package eu.eurise.messages.system

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
import eu.eurise.core.suite.TranslateContract
import eu.eurise.messages.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Traduzione con Eurise Leggi (servizio con il permesso della suite): il testo va a Leggi sul telefono, non esce.
 * [Result.Missing] quando manca la lingua (si scarica in Leggi), [Result.NoApp] se Leggi non è installata.
 */
object TranslateClient {
    sealed interface Result {
        data class Done(val text: String) : Result
        data object Missing : Result
        data object NoApp : Result
        data object Failed : Result
    }

    fun installed(context: Context) = runCatching { context.packageManager.getPackageInfo(BuildConfig.READ_PACKAGE, 0); true }.getOrDefault(false)

    /** Apre in Leggi la scelta delle lingue (quando una manca). */
    fun openLanguages(context: Context) = runCatching {
        context.startActivity(Intent(TranslateContract.ACTION_LANGUAGES).setPackage(BuildConfig.READ_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    suspend fun translate(context: Context, text: String, from: String, to: String): Result {
        if (!installed(context)) return Result.NoApp
        return withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                var connection: ServiceConnection? = null
                fun finish(r: Result) {
                    connection?.let { runCatching { context.unbindService(it) } }
                    connection = null
                    if (cont.isActive) cont.resume(r)
                }
                val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
                    val data = msg.data
                    finish(
                        when {
                            data.getString(TranslateContract.KEY_ERROR) == TranslateContract.ERROR_NO_MODEL -> Result.Missing
                            data.getString(TranslateContract.KEY_RESULT) != null -> Result.Done(data.getString(TranslateContract.KEY_RESULT).orEmpty())
                            else -> Result.Failed
                        },
                    )
                    true
                })
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                        val msg = Message.obtain(null, TranslateContract.MSG_TRANSLATE).apply {
                            data = Bundle().apply {
                                putString(TranslateContract.KEY_TEXT, text)
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
                val ok = runCatching {
                    context.bindService(Intent(TranslateContract.ACTION_TRANSLATE).setPackage(BuildConfig.READ_PACKAGE), conn, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
                if (!ok) finish(Result.NoApp)
                cont.invokeOnCancellation { connection?.let { runCatching { context.unbindService(it) } } }
            }
        } ?: Result.Failed
    }
}
