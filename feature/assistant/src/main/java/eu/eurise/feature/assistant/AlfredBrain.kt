package eu.eurise.feature.assistant

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
import eu.eurise.core.contacts.Reach
import eu.eurise.core.suite.BrainContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate

/**
 * Il collegamento di Alfred al suo cervello, Eurise AI (vedi [BrainContract]). Si collega quando il pannello si apre
 * (il modello comincia a caricarsi subito) e si scollega con [close]. Tutto resta sul telefono.
 */
class AlfredBrain(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var service: Messenger? = null
    private val waiting = ArrayList<(Messenger?) -> Unit>()

    /** Lo stato dell'ultima richiesta: c'è un modello pronto e quale. */
    var ready = false
        private set
    var model: String? = null
        private set
    var models: List<String> = emptyList()
        private set

    val installed get() = pkg() != null

    private fun pkg() = BrainContract.PACKAGES.firstOrNull { Reach.installed(context, it) }

    fun connect(then: (Messenger?) -> Unit = {}) {
        service?.let { return then(it) }
        waiting += then
        if (connection != null) return
        val p = pkg() ?: return flush(null)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                service = Messenger(binder)
                flush(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                connection = null
            }
        }
        connection = conn
        val ok = eu.eurise.core.suite.SuiteAccess.isSuitePackage(context, p) && runCatching { context.bindService(Intent(BrainContract.ACTION_BRAIN).setPackage(p), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!ok) { connection = null; flush(null) }
    }

    private fun flush(m: Messenger?) = waiting.toList().also { waiting.clear() }.forEach { it(m) }

    fun close() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        service = null
    }

    /** Manda un messaggio e aspetta la prima risposta del tipo [answer] (o null dopo [timeoutMs]). */
    private suspend fun request(what: Int, answer: Int, data: Bundle, timeoutMs: Long): Bundle? {
        val result = CompletableDeferred<Bundle?>()
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == answer) result.complete(Bundle(msg.data))
            true
        })
        main.post {
            connect { m ->
                if (m == null) { result.complete(null); return@connect }
                val sent = runCatching { m.send(Message.obtain(null, what).apply { this.data = data; replyTo = reply }) }.isSuccess
                if (!sent) result.complete(null)
            }
        }
        return withTimeoutOrNull(timeoutMs) { result.await() }
    }

    private fun readStatus(b: Bundle?) {
        if (b == null) return
        ready = b.getBoolean(BrainContract.KEY_READY)
        model = b.getString(BrainContract.KEY_MODEL)
        models = b.getStringArrayList(BrainContract.KEY_MODELS).orEmpty()
    }

    suspend fun status(): Boolean {
        if (!installed) { ready = false; return false }
        readStatus(request(BrainContract.MSG_STATUS, BrainContract.MSG_STATUS, Bundle(), 4_000))
        return ready
    }

    /** Le azioni capite dalla frase (righe «tipo|chi|testo|data|ora|canale»), null se il cervello non c'è o non risponde. */
    suspend fun command(sentence: String, hints: String, today: LocalDate): String? {
        if (!ready) return null
        val b = request(BrainContract.MSG_COMMAND, BrainContract.MSG_COMMAND, Bundle().apply {
            putString(BrainContract.KEY_TEXT, sentence)
            putString(BrainContract.KEY_HINTS, hints)
            putString(BrainContract.KEY_DATE, today.toString())
        }, 25_000) ?: return null
        if (b.getString(BrainContract.KEY_ERROR) != null) return null
        return b.getString(BrainContract.KEY_TEXT)?.takeIf { it.isNotBlank() }
    }

    /** Risposta libera a pezzi: [onPiece] riceve il testo man mano, [onDone] la fine (false se qualcosa è andato storto). */
    fun ask(question: String, onPiece: (String) -> Unit, onDone: (Boolean) -> Unit) {
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                BrainContract.MSG_PIECE -> onPiece(msg.data.getString(BrainContract.KEY_TEXT).orEmpty())
                BrainContract.MSG_DONE -> onDone(msg.data.getString(BrainContract.KEY_ERROR) == null)
            }
            true
        })
        connect { m ->
            if (m == null) return@connect onDone(false)
            val sent = runCatching {
                m.send(Message.obtain(null, BrainContract.MSG_ASK).apply {
                    data = Bundle().apply { putString(BrainContract.KEY_TEXT, question) }
                    replyTo = reply
                })
            }.isSuccess
            if (!sent) onDone(false)
        }
    }

    fun stop() {
        service?.let { runCatching { it.send(Message.obtain(null, BrainContract.MSG_STOP)) } }
    }

    /** Passa a Eurise AI il modello scaricato da Eurise; Eurise AI lo copia e lo usa. */
    suspend fun install(file: File, name: String): Boolean {
        val pfd = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return false
        val b = request(BrainContract.MSG_INSTALL, BrainContract.MSG_STATUS, Bundle().apply {
            putParcelable(BrainContract.KEY_FILE, pfd)
            putString(BrainContract.KEY_NAME, name)
        }, 10 * 60_000)
        runCatching { pfd.close() }
        readStatus(b)
        return b != null && b.getString(BrainContract.KEY_ERROR) == null && name in models
    }

    suspend fun use(name: String) = readStatus(request(BrainContract.MSG_USE, BrainContract.MSG_STATUS, Bundle().apply { putString(BrainContract.KEY_NAME, name) }, 4_000))

    suspend fun remove(name: String) = readStatus(request(BrainContract.MSG_REMOVE, BrainContract.MSG_STATUS, Bundle().apply { putString(BrainContract.KEY_NAME, name) }, 4_000))
}
