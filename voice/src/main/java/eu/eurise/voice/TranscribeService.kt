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
                out.putBoolean(VoiceContract.KEY_SPEAKING, Voices.installed(this) != null)
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
                            whisper?.transcribe(audio, language)
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
            FloatArray(b.size / 2) { i -> ((b[2 * i].toInt() and 0xFF) or (b[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
        }
    }.getOrNull()

    private companion object {
        /** Whisper a volte scrive tra parentesi i rumori («[musica]», «(risate)»): non sono parole dette. */
        fun clean(text: String) = text.replace(Regex("""\[[^\]]*]|\([^)]*\)"""), " ").replace(Regex("\\s+"), " ").trim()
    }
}

@Suppress("DEPRECATION")
private fun Bundle.getParcelableCompat(key: String): ParcelFileDescriptor? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, ParcelFileDescriptor::class.java) else getParcelable(key)
