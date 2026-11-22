package eu.eurise.core.insieme

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
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.core.suite.TranslateContract

/**
 * Traduzione per colleghi stranieri (tavola SpaTraduci), in Chat e in Email: il testo va a Eurise Leggi sul telefono (servizio
 * con il permesso della suite) e non esce. Per ogni persona si sceglie se leggere i suoi messaggi in italiano e in che lingua
 * mandargli i tuoi (con l'italiano sotto).
 */
object Traduci {
    sealed interface Result {
        data class Done(val text: String) : Result
        data object Missing : Result
        data object NoApp : Result
        data object Failed : Result
    }

    fun readPackage(context: Context) = "eu.eurise.read" + if (SuiteAccess.isDebug(context)) ".debug" else ""

    fun installed(context: Context) = SuiteAccess.isSuitePackage(context, readPackage(context))

    /** In Leggi la scelta delle lingue da scaricare (quando una manca). */
    fun openLanguages(context: Context) = runCatching {
        context.startActivity(Intent(TranslateContract.ACTION_LANGUAGES).setPackage(readPackage(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Traduce [text] da [from] a [to] (codici «de», «it»…); la risposta arriva sul thread principale. */
    fun translate(context: Context, text: String, from: String, to: String, done: (Result) -> Unit) {
        val app = context.applicationContext
        if (!installed(app)) return done(Result.NoApp)
        val main = Handler(Looper.getMainLooper())
        var connection: ServiceConnection? = null
        var finished = false
        fun finish(r: Result) {
            if (finished) return
            finished = true
            connection?.let { runCatching { app.unbindService(it) } }
            connection = null
            done(r)
        }
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            val data = msg.data
            finish(when {
                data.getString(TranslateContract.KEY_ERROR) == TranslateContract.ERROR_NO_MODEL -> Result.Missing
                data.getString(TranslateContract.KEY_RESULT) != null -> Result.Done(data.getString(TranslateContract.KEY_RESULT).orEmpty())
                else -> Result.Failed
            })
            true
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                val msg = Message.obtain(null, TranslateContract.MSG_TRANSLATE).apply {
                    data = Bundle().apply {
                        putString(TranslateContract.KEY_TEXT, text.take(TranslateContract.MAX_CHARS))
                        putString(TranslateContract.KEY_FROM, from)
                        putString(TranslateContract.KEY_TO, to)
                    }
                    replyTo = reply
                }
                runCatching { Messenger(service).send(msg) }.onFailure { finish(Result.Failed) }
            }
            override fun onServiceDisconnected(name: ComponentName?) = finish(Result.Failed)
        }
        connection = conn
        val ok = runCatching { app.bindService(Intent(TranslateContract.ACTION_TRANSLATE).setPackage(readPackage(app)), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!ok) { connection = null; finish(Result.NoApp); return }
        main.postDelayed({ finish(Result.Failed) }, 60_000)
    }

    /** Le lingue proposte per «mandagli i tuoi in…». */
    val LANGUAGES = listOf("en", "de", "fr", "es", "pt", "nl", "ro", "uk", "pl")

    fun name(code: String): String = java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ITALIAN).ifBlank { code }
}
