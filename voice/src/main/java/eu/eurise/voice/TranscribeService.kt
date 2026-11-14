package eu.eurise.voice

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import eu.eurise.core.suite.VoiceContract
import eu.eurise.voice.speech.Voices

/**
 * Trascrizione per le app della suite ([VoiceContract]): l'audio arriva già registrato da chi ascolta (Alfred, in primo
 * piano e con il suo permesso del microfono), così Voce non deve registrare dallo sfondo, cosa che Android limita.
 * Whisper resta caricato finché il servizio è vivo: la seconda frase è più rapida. L'audio non si salva.
 */
class TranscribeService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var whisper: Whisper? = null
    private var loaded: Model? = null

    override fun onCreate() {
        super.onCreate()
        Speed.load(this)
        thread = HandlerThread("eurise-transcribe").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        val w = whisper
        whisper = null
        Handler(thread.looper).post { w?.close() }
        thread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val out = Bundle()
        when (msg.what) {
            VoiceContract.MSG_STATUS -> {
                out.putBoolean(VoiceContract.KEY_HEARING, Model.installed(this) != null)
                out.putBoolean(VoiceContract.KEY_SPEAKING, Voices.current(this) != null)
            }
            VoiceContract.MSG_TRANSCRIBE -> {
                val pipe = msg.data.getParcelableCompat(VoiceContract.KEY_AUDIO)
                val language = msg.data.getString(VoiceContract.KEY_LANGUAGE)?.takeIf { it.length in 2..4 } ?: "auto"
                val audio = pipe?.let { readPcm(it) }
                val model = Model.installed(this)
                when {
                    model == null -> out.putString(VoiceContract.KEY_ERROR, VoiceContract.ERROR_NO_MODEL)
                    audio == null || audio.size < VoiceContract.SAMPLE_RATE / 4 -> out.putString(VoiceContract.KEY_ERROR, VoiceContract.ERROR_NOTHING)
                    else -> {
                        val text = runCatching {
                            if (whisper == null || loaded != model) {
                                whisper?.close()
                                whisper = Whisper.load(model.file(this))
                                loaded = model
                            }
                            // Con le frasi d'esempio (Alfred) il comando è breve: contesto pieno, più preciso; il
                            // contesto ridotto, buono per la dettatura lunga, sbagliava le parole dei comandi.
                            val prompt = msg.data.getString(VoiceContract.KEY_PROMPT).orEmpty().take(400)
                            whisper?.transcribe(audio, language, prompt, fullContext = prompt.isNotEmpty())
                        }.getOrNull()?.let(::clean)
                        when {
                            text == null -> out.putString(VoiceContract.KEY_ERROR, VoiceContract.ERROR_FAILED)
                            text.isBlank() -> out.putString(VoiceContract.KEY_ERROR, VoiceContract.ERROR_NOTHING)
                            else -> out.putString(VoiceContract.KEY_TEXT, text)
                        }
                    }
                }
            }
            else -> return
        }
        try {
            replyTo.send(Message.obtain(null, VoiceContract.MSG_RESULT).apply { data = out })
        } catch (_: RemoteException) {
            // Chi ha chiesto non c'è più.
        }
    }

    /** Legge il tubo fino in fondo (al massimo 30 secondi di audio) e lo porta ai valori tra -1 e 1 che vuole Whisper. */
    private fun readPcm(pfd: ParcelFileDescriptor): FloatArray? = runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val max = VoiceContract.SAMPLE_RATE * 2 * 30
            val bytes = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (bytes.size() < max) {
                val n = input.read(buf)
                if (n < 0) break
                bytes.write(buf, 0, n)
            }
            val b = bytes.toByteArray()
            normalize(FloatArray(b.size / 2) { i -> ((b[2 * i].toInt() and 0xFF) or (b[2 * i + 1].toInt() shl 8)).toShort() / 32768f })
        }
    }.getOrNull()

    internal companion object {
        /**
         * Whisper a volte scrive tra parentesi i rumori («[musica]», «(risate)») e, su audio quasi vuoto, inventa frasi
         * fatte («Mi scusi?», «Grazie.», «Sottotitoli a cura di…»): non sono parole dette, e Alfred deve chiedere di
         * ripetere invece di «non capire» (prova del 13 novembre).
         */
        fun clean(text: String): String {
            val t = text.replace(Regex("""\[[^\]]*]|\([^)]*\)"""), " ").replace(Regex("\\s+"), " ").trim()
            val bare = t.lowercase().trim(' ', '.', '!', '?', ',', '…')
            return if (bare in HALLUCINATIONS || HALLUCINATION_PREFIXES.any { bare.startsWith(it) }) "" else t
        }

        private val HALLUCINATIONS = setOf("mi scusi", "scusi", "grazie", "grazie a tutti", "grazie mille",
            "grazie per la visione", "grazie per l'attenzione", "thank you", "thanks for watching")
        private val HALLUCINATION_PREFIXES = listOf("sottotitoli", "sottotitolato", "a cura di", "subtitles by")

        /**
         * Il microfono in modalità riconoscimento non alza il volume da sé: una voce bassa arrivava a Whisper quasi
         * come silenzio. Si porta il picco a 0,9 (al massimo 8 volte più forte, per non gonfiare il rumore).
         */
        fun normalize(audio: FloatArray): FloatArray {
            var peak = 0f
            for (v in audio) { val a = kotlin.math.abs(v); if (a > peak) peak = a }
            if (peak <= 0f || peak >= 0.5f) return audio
            val gain = (0.9f / peak).coerceAtMost(8f)
            for (i in audio.indices) audio[i] = audio[i] * gain
            return audio
        }
    }
}

@Suppress("DEPRECATION")
private fun Bundle.getParcelableCompat(key: String): ParcelFileDescriptor? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, ParcelFileDescriptor::class.java) else getParcelable(key)
