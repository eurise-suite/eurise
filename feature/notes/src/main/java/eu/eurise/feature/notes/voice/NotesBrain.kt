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
import eu.eurise.core.suite.BrainContract
import eu.eurise.core.suite.SuiteAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Il cervello di Alfred (Eurise AI, sul telefono) visto dalle Note: serve a rimettere in ordine e riassumere le note
 * vocali. È un collegamento minimo a [BrainContract] (stato e domande libere); senza Eurise AI o senza modello
 * [ready] risponde falso e le Note fanno da sé. Niente esce dal telefono.
 */
class NotesBrain(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var service: Messenger? = null
    private val waiting = ArrayList<(Messenger?) -> Unit>()

    private fun pkg() = BrainContract.PACKAGES.firstOrNull { runCatching { app.packageManager.getPackageInfo(it, 0) }.isSuccess }

    val installed: Boolean get() = pkg() != null

    private fun connect(then: (Messenger?) -> Unit) {
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
        val ok = SuiteAccess.isSuitePackage(app, p) &&
            runCatching { app.bindService(Intent(BrainContract.ACTION_BRAIN).setPackage(p), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!ok) { connection = null; flush(null) }
    }

    private fun flush(m: Messenger?) = waiting.toList().also { waiting.clear() }.forEach { it(m) }

    fun close() = main.post {
        connection?.let { runCatching { app.unbindService(it) } }
        connection = null
        service = null
    }

    /** Vero se c'è un modello pronto. */
    suspend fun ready(): Boolean {
        if (!installed) return false
        val done = CompletableDeferred<Boolean>()
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == BrainContract.MSG_STATUS) done.complete(msg.data.getBoolean(BrainContract.KEY_READY))
            true
        })
        main.post {
            connect { m ->
                if (m == null) { done.complete(false); return@connect }
                val sent = runCatching { m.send(Message.obtain(null, BrainContract.MSG_STATUS).apply { replyTo = reply }) }.isSuccess
                if (!sent) done.complete(false)
            }
        }
        return withTimeoutOrNull(5_000) { done.await() } ?: false
    }

    /** La risposta intera a una richiesta libera; null se il cervello non c'è, sbaglia o ci mette troppo. */
    suspend fun ask(text: String, timeoutMs: Long = 120_000): String? {
        val done = CompletableDeferred<String?>()
        val pieces = StringBuilder()
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                BrainContract.MSG_PIECE -> pieces.append(msg.data.getString(BrainContract.KEY_TEXT).orEmpty())
                BrainContract.MSG_DONE -> done.complete(
                    if (msg.data.getString(BrainContract.KEY_ERROR) != null) null
                    else msg.data.getString(BrainContract.KEY_TEXT)?.takeIf { it.isNotBlank() } ?: pieces.toString(),
                )
            }
            true
        })
        main.post {
            connect { m ->
                if (m == null) { done.complete(null); return@connect }
                val sent = runCatching {
                    m.send(Message.obtain(null, BrainContract.MSG_ASK).apply {
                        data = Bundle().apply { putString(BrainContract.KEY_TEXT, text) }
                        replyTo = reply
                    })
                }.isSuccess
                if (!sent) done.complete(null)
            }
        }
        val answer = withTimeoutOrNull(timeoutMs) { done.await() }
        if (answer == null) service?.let { runCatching { it.send(Message.obtain(null, BrainContract.MSG_STOP)) } }
        return answer?.trim()?.takeIf { it.isNotEmpty() }
    }
}
