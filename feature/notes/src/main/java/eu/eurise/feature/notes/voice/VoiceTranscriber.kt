package eu.eurise.feature.notes.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import eu.eurise.core.suite.VoiceContract
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Trascrive i pezzi di una nota vocale con Eurise Voce (Whisper sul telefono), uno alla volta e in ordine: il testo
 * arriva a [onText] pezzo per pezzo, mentre si parla. Senza Voce ([available] falso) la nota resta solo audio.
 */
class VoiceTranscriber(context: Context, private val onText: (String) -> Unit, private val onNoModel: () -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val voice: String? = VoiceContract.PACKAGES.firstOrNull { pkg ->
        runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null
    private val queue = ArrayDeque<ByteArray>()
    private var busy = false

    val available: Boolean get() = voice != null

    /** Mette in fila un pezzo di audio (PCM 16 bit, 16 kHz, mono). Si può chiamare da qualsiasi thread. */
    fun add(pcm: ByteArray) {
        if (voice == null) return
        main.post {
            queue.addLast(pcm)
            next()
        }
    }

    /** Vero se resta qualcosa da trascrivere. */
    fun pending(): Boolean = busy || queue.isNotEmpty()

    fun close() {
        main.post {
            queue.clear()
            connection?.let { runCatching { app.unbindService(it) } }
            connection = null
            service = null
        }
    }

    private fun next() {
        if (busy) return
        val pcm = queue.removeFirstOrNull() ?: return
        busy = true
        connect { m ->
            if (m == null) { busy = false; queue.clear(); return@connect }
            val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
                val text = msg.data.getString(VoiceContract.KEY_TEXT)
                if (!text.isNullOrBlank()) onText(text.trim())
                else if (msg.data.getString(VoiceContract.KEY_ERROR) == VoiceContract.ERROR_NO_MODEL) { queue.clear(); onNoModel() }
                busy = false
                next()
                true
            })
            val (read, write) = ParcelFileDescriptor.createPipe()
            val msg = Message.obtain(null, VoiceContract.MSG_TRANSCRIBE).apply {
                data = Bundle().apply {
                    putParcelable(VoiceContract.KEY_AUDIO, read)
                    putString(VoiceContract.KEY_LANGUAGE, Locale.getDefault().language)
                }
                replyTo = reply
            }
            val sent = runCatching { m.send(msg) }.isSuccess
            runCatching { read.close() }
            if (!sent) {
                runCatching { write.close() }
                busy = false
                next()
                return@connect
            }
            thread(name = "nota-vocale-voce") {
                runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(pcm) } }
            }
        }
    }

    private fun connect(then: (Messenger?) -> Unit) {
        service?.let { return then(it) }
        val pkg = voice ?: return then(null)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val m = Messenger(binder)
                service = m
                then(m)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                busy = false
            }
        }
        connection = conn
        val bound = eu.eurise.core.suite.SuiteAccess.isSuitePackage(app, pkg) && runCatching { app.bindService(Intent(VoiceContract.ACTION_TRANSCRIBE).setPackage(pkg), conn, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
        if (!bound) { connection = null; then(null) }
    }
}
