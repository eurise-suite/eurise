package eu.eurise.read.translate

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import eu.eurise.core.suite.TranslateContract

/**
 * Servizio di traduzione per le app della suite ([TranslateContract]): traduce sul telefono su un thread suo, con i modelli
 * scaricati in Leggi. Protetto dal permesso della suite (solo app con la stessa firma). Il testo non si salva.
 */
class TranslateService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var engine: TranslateEngine? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("eurise-translate").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        val e = engine
        engine = null
        Handler(thread.looper).post { e?.close() }
        thread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val out = Bundle()
        when (msg.what) {
            TranslateContract.MSG_LANGUAGES ->
                out.putStringArrayList(TranslateContract.KEY_LANGUAGES, ArrayList(Models.languages(this)))
            TranslateContract.MSG_TRANSLATE -> {
                val text = msg.data.getString(TranslateContract.KEY_TEXT).orEmpty().take(TranslateContract.MAX_CHARS)
                val from = msg.data.getString(TranslateContract.KEY_FROM).orEmpty()
                val to = msg.data.getString(TranslateContract.KEY_TO).orEmpty()
                try {
                    val e = engine ?: TranslateEngine(this).also { engine = it }
                    out.putString(TranslateContract.KEY_RESULT, e.translate(text, from, to))
                } catch (_: TranslateEngine.MissingModel) {
                    out.putString(TranslateContract.KEY_ERROR, TranslateContract.ERROR_NO_MODEL)
                } catch (_: Throwable) {
                    out.putString(TranslateContract.KEY_ERROR, TranslateContract.ERROR_FAILED)
                }
            }
            else -> return
        }
        try {
            replyTo.send(Message.obtain(null, TranslateContract.MSG_RESULT).apply { data = out })
        } catch (_: RemoteException) {
            // L'app che ha chiesto non c'è più.
        }
    }
}
