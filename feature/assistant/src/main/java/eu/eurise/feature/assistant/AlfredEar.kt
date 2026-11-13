package eu.eurise.feature.assistant

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import eu.eurise.core.suite.Endpoint
import eu.eurise.core.suite.VoiceContract
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Alfred che ascolta senza altri tasti. Registra lui stesso il microfono (è in primo piano e ha il permesso: Android non
 * lascia registrare a un'app in secondo piano), capisce da solo quando si è finito ([Endpoint]) e manda l'audio a
 * Eurise Voce, che lo trascrive con Whisper sul telefono. Senza Voce o senza il suo modello usa il riconoscimento del
 * telefono, se c'è. Ogni esito arriva sul thread principale; nessuno apre altre app da solo.
 */
class Ear(private val context: Context) {
    enum class Problem {
        /** Silenzio: nessuna parola entro qualche secondo. */
        Nothing,
        /** Eurise Voce c'è ma manca il modello per ascoltare: si scarica dalla sua pagina. */
        NoModel,
        /** Né Voce né il riconoscimento del telefono. */
        NoEngine,
        /** Il microfono non si apre (permesso o già in uso). */
        Mic,
        Failed,
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var recording = false
    @Volatile private var cancelled = false
    private var system: SpeechRecognizer? = null
    private var connection: ServiceConnection? = null

    private val voicePackage get() = AlfredTts.voicePackage(context)

    val available: Boolean get() = voicePackage != null || SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Ascolta una frase. [onLevel] (0..1) anima il microfono, [onSpeech] dice che si è cominciato a parlare,
     * [onWorking] che la frase è finita e si sta trascrivendo, [onText] porta la frase, [onFail] il problema.
     */
    fun listen(
        onLevel: (Float) -> Unit,
        onSpeech: () -> Unit,
        onWorking: () -> Unit,
        onText: (String) -> Unit,
        onFail: (Problem) -> Unit,
    ) {
        cancel()
        cancelled = false
        val voice = voicePackage
        if (voice != null) record(voice, onLevel, onSpeech, onWorking, onText, onFail)
        else if (SpeechRecognizer.isRecognitionAvailable(context)) phone(onLevel, onSpeech, onWorking, onText, onFail)
        else onFail(Problem.NoEngine)
    }

    /** «Ho finito» toccando di nuovo il microfono: si trascrive quel che c'è. */
    fun finish() {
        recording = false
        runCatching { system?.stopListening() }
    }

    fun cancel() {
        cancelled = true
        recording = false
        system?.let { runCatching { it.cancel(); it.destroy() } }
        system = null
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
    }

    @SuppressLint("MissingPermission")
    private fun record(
        voice: String, onLevel: (Float) -> Unit, onSpeech: () -> Unit, onWorking: () -> Unit,
        onText: (String) -> Unit, onFail: (Problem) -> Unit,
    ) {
        recording = true
        thread(name = "alfred-ear") {
            val rate = VoiceContract.SAMPLE_RATE
            val record = runCatching {
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
                    .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            }.getOrNull()
            if (record == null) { main.post { if (!cancelled) onFail(Problem.Mic) }; recording = false; return@thread }
            val pcm = java.io.ByteArrayOutputStream(rate * 2 * 8)
            val block = ShortArray(rate / 10)
            val endpoint = Endpoint(rate = rate)
            var heard = false
            var silent = false
            var size = 0
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) error("microfono occupato")
                while (recording && !cancelled) {
                    val n = record.read(block, 0, block.size)
                    if (n <= 0) break
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = block[i].toInt()
                        sum += (v * v).toDouble()
                        pcm.write(v and 0xFF)
                        pcm.write((v shr 8) and 0xFF)
                    }
                    size += n
                    val rms = (sqrt(sum / n) / 32768.0).toFloat()
                    main.post { onLevel((rms * 12f).coerceIn(0f, 1f)) }
                    when (endpoint.feed(rms, size)) {
                        Endpoint.Event.Started -> { heard = true; main.post { onSpeech() } }
                        Endpoint.Event.Finished -> recording = false
                        Endpoint.Event.NoSpeech -> { silent = true; recording = false }
                        Endpoint.Event.None -> Unit
                    }
                }
            } catch (_: Throwable) {
                runCatching { record.release() }
                main.post { if (!cancelled) onFail(Problem.Mic) }
                return@thread
            }
            runCatching { record.stop() }
            record.release()
            recording = false
            if (cancelled) return@thread
            // Toccare «ho finito» prima che la voce sia riconosciuta vale comunque: si prova a trascrivere.
            if (silent || (!heard && size < rate / 2)) { main.post { onFail(Problem.Nothing) }; return@thread }
            main.post {
                if (cancelled) return@post
                onWorking()
                transcribe(voice, pcm.toByteArray(), onText, onFail)
            }
        }
    }

    /** L'audio va a Voce in un tubo (può superare il limite dei messaggi tra app); la risposta torna con [Messenger]. */
    private fun transcribe(voice: String, audio: ByteArray, onText: (String) -> Unit, onFail: (Problem) -> Unit) {
        var finished = false
        fun end(block: () -> Unit) {
            if (finished) return
            finished = true
            connection?.let { runCatching { context.unbindService(it) } }
            connection = null
            if (!cancelled) block()
        }
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            val data = msg.data
            val text = data.getString(VoiceContract.KEY_TEXT)
            end {
                when {
                    !text.isNullOrBlank() -> onText(text)
                    data.getString(VoiceContract.KEY_ERROR) == VoiceContract.ERROR_NO_MODEL -> onFail(Problem.NoModel)
                    data.getString(VoiceContract.KEY_ERROR) == VoiceContract.ERROR_NOTHING -> onFail(Problem.Nothing)
                    else -> onFail(Problem.Failed)
                }
            }
            true
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                val (read, write) = ParcelFileDescriptor.createPipe()
                val msg = Message.obtain(null, VoiceContract.MSG_TRANSCRIBE).apply {
                    data = Bundle().apply {
                        putParcelable(VoiceContract.KEY_AUDIO, read)
                        putString(VoiceContract.KEY_LANGUAGE, Locale.getDefault().language)
                    }
                    replyTo = reply
                }
                val sent = runCatching { Messenger(service).send(msg) }.isSuccess
                runCatching { read.close() }
                if (!sent) { runCatching { write.close() }; end { onFail(Problem.Failed) }; return }
                thread(name = "alfred-ear-pipe") {
                    runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(audio) } }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) = end { onFail(Problem.Failed) }
        }
        connection = conn
        val bound = runCatching {
            context.bindService(Intent(VoiceContract.ACTION_TRANSCRIBE).setPackage(voice), conn, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) { connection = null; end { onFail(Problem.Failed) } }
        // Whisper sul telefono impiega qualche secondo; oltre il minuto qualcosa non va.
        main.postDelayed({ end { onFail(Problem.Failed) } }, 60_000)
    }

    /** Senza Eurise Voce: il riconoscimento del telefono (chiede di restare sul telefono, se può). */
    private fun phone(onLevel: (Float) -> Unit, onSpeech: () -> Unit, onWorking: () -> Unit, onText: (String) -> Unit, onFail: (Problem) -> Unit) {
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: return onFail(Problem.NoEngine)
        system = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = onSpeech()
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onWorking()
            override fun onError(error: Int) {
                if (cancelled) return
                onFail(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Problem.Nothing
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognizer.ERROR_AUDIO -> Problem.Mic
                    else -> Problem.Failed
                })
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                if (text.isNullOrBlank()) onFail(Problem.Nothing) else onText(text)
            }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (runCatching { r.startListening(intent) }.isFailure) onFail(Problem.Failed)
    }
}
