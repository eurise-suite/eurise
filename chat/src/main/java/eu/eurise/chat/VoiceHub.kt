package eu.eurise.chat

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * I vocali della Chat (tavola ChatVocali): ascolto, trascrizione sul telefono con Eurise Voce e lettura ad alta voce dei
 * messaggi con la sintesi del telefono. Le trascrizioni restano in memoria finché la Chat è aperta: l'audio non esce e
 * il testo non si salva su disco.
 */
class VoiceHub(private val context: Context, private val scope: CoroutineScope) {

    sealed interface Transcript {
        data object Working : Transcript
        data class Text(val text: String) : Transcript
        /** Nessuna parola riconosciuta. */
        data object Empty : Transcript
        /** Voce c'è, il modello di Whisper no. */
        data object NoModel : Transcript
        /** Eurise Voce non è installata. */
        data object NoVoice : Transcript
        data object Failed : Transcript
    }

    /** Chiamato quando una trascrizione è pronta (l'anteprima nell'elenco delle chat la mostra). */
    var onTranscribed: (() -> Unit)? = null

    /** Per messaggio (indirizzo del file, o id dell'eco locale). */
    val transcripts = mutableStateMapOf<String, Transcript>()
    private val transcriber by lazy { VoiceTranscriber(context) }
    private val queue = ArrayDeque<Pair<String, suspend () -> ShortArray?>>()
    private var busy = false

    fun key(e: Event): String = e.mediaUrl ?: e.id

    /** Trascrive il vocale [e] se non è già fatto: il file lo scarica (e decifra) [fetch]. */
    fun want(e: Event, fetch: (Event, (File?) -> Unit) -> Unit) {
        val k = key(e)
        if (k in transcripts || e.mediaUrl == null) return
        transcripts[k] = Transcript.Working
        enqueue(k) {
            // Senza sessione il file non arriva mai: dopo due minuti si lascia perdere.
            val f = kotlinx.coroutines.withTimeoutOrNull(120_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<File?> { cont -> fetch(e) { if (cont.isActive) cont.resumeWith(Result.success(it)) } }
            }
            f?.let { withContext(Dispatchers.IO) { VoiceCodec.decode(it) } }
        }
    }

    /** Il vocale appena registrato: si trascrive dall'audio già in memoria. */
    fun transcribeOwn(key: String, pcm: ShortArray) {
        if (key in transcripts) return
        transcripts[key] = Transcript.Working
        enqueue(key) { pcm }
    }

    /** L'eco locale ha preso il suo indirizzo: la trascrizione passa con lui. */
    fun move(from: String, to: String) { transcripts.remove(from)?.let { transcripts[to] = it } }

    /** Riprova (dopo aver scaricato il modello in Voce). */
    fun retry(e: Event, fetch: (Event, (File?) -> Unit) -> Unit) { transcripts.remove(key(e)); want(e, fetch) }

    private fun enqueue(key: String, audio: suspend () -> ShortArray?) {
        queue.addLast(key to audio)
        next()
    }

    private fun next() {
        if (busy) return
        val (key, audio) = queue.removeFirstOrNull() ?: return
        busy = true
        scope.launch {
            val pcm = runCatching { audio() }.getOrNull()
            if (pcm == null) { transcripts[key] = Transcript.Failed; busy = false; next(); return@launch }
            transcriber.transcribe(pcm) { r ->
                transcripts[key] = when (r) {
                    is VoiceTranscriber.Result.Text -> Transcript.Text(r.text).also { scope.launch { onTranscribed?.invoke() } }
                    VoiceTranscriber.Result.Empty -> Transcript.Empty
                    VoiceTranscriber.Result.NoModel -> Transcript.NoModel
                    VoiceTranscriber.Result.NoVoice -> Transcript.NoVoice
                    VoiceTranscriber.Result.Failed -> Transcript.Failed
                }
                busy = false
                next()
            }
        }
    }

    fun modelsIntent() = transcriber.modelsIntent()

    // --- Ascolto ---

    /** Il vocale che suona (id del messaggio) e a che punto è, da 0 a 1. */
    var playing by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    /** Il vocale che si sta scaricando per ascoltarlo. */
    var loading by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Tocco sul tasto: parte, si ferma o riprende. */
    fun toggle(e: Event, fetch: (Event, (File?) -> Unit) -> Unit, failed: () -> Unit) {
        val p = player
        if (playing == e.id && p != null) {
            if (p.isPlaying) { p.pause(); paused = true } else { p.start(); paused = false }
            return
        }
        stopPlaying()
        stopSpeaking()
        loading = e.id
        fetch(e) { f ->
            if (loading != e.id) return@fetch
            loading = null
            if (f == null) return@fetch failed()
            val mp = runCatching { MediaPlayer().apply { setDataSource(f.absolutePath); prepare() } }.getOrNull() ?: return@fetch failed()
            player = mp
            playing = e.id
            paused = false
            progress = 0f
            mp.setOnCompletionListener { stopPlaying() }
            mp.start()
            ticker = scope.launch {
                while (isActive && player === mp) {
                    runCatching { if (mp.duration > 0) progress = mp.currentPosition / mp.duration.toFloat() }
                    delay(100)
                }
            }
        }
    }

    /** Spostarsi nel vocale che suona toccando la forma d'onda. */
    fun seek(e: Event, fraction: Float) {
        val p = player ?: return
        if (playing != e.id) return
        runCatching { p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt()); progress = fraction }
    }

    fun stopPlaying() {
        ticker?.cancel(); ticker = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing = null
        paused = false
        progress = 0f
    }

    // --- Lettura ad alta voce ---

    /** Il messaggio che si sta leggendo ad alta voce. */
    var speakingId by mutableStateOf<String?>(null)
        private set
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val waiting = ArrayList<Pair<String, String>>()

    /**
     * Legge [text] con la voce del telefono (il motore scelto nelle impostazioni di Android: anche Eurise Voce). [queue]
     * vero: dopo quello che sta già leggendo (lettura automatica dei messaggi che arrivano).
     */
    fun speak(id: String, text: String, queue: Boolean = false) {
        if (text.isBlank()) return
        if (!queue) stopPlaying()
        val t = tts
        if (t == null || !ttsReady) {
            if (!queue) waiting.clear()
            waiting += id to text
            if (t == null) tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.setLanguage(Locale.getDefault())
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { scope.launch { speakingId = utteranceId } }
                        override fun onDone(utteranceId: String?) { scope.launch { if (speakingId == utteranceId) speakingId = null } }
                        @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { scope.launch { if (speakingId == utteranceId) speakingId = null } }
                    })
                    val list = waiting.toList(); waiting.clear()
                    list.forEachIndexed { i, (u, s) -> tts?.speak(s, if (i == 0 && !queue) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, u) }
                } else waiting.clear()
            }
            return
        }
        t.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stopSpeaking() {
        tts?.stop()
        waiting.clear()
        speakingId = null
    }

    // --- Per chi vede poco: tutti i messaggi letti da soli ---

    private val prefs = context.getSharedPreferences("chat_ui", Context.MODE_PRIVATE)
    var autoRead by mutableStateOf(prefs.getBoolean("auto_read", false))
        private set

    fun changeAutoRead(on: Boolean) {
        autoRead = on
        prefs.edit().putBoolean("auto_read", on).apply()
        if (!on) stopSpeaking()
    }

    /** Alla chiusura della Chat: si ferma tutto e Voce torna libera. */
    fun close() {
        stopPlaying()
        tts?.shutdown(); tts = null; ttsReady = false
        transcriber.close()
    }
}
