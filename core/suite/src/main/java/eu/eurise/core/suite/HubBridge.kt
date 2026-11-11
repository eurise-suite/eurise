package eu.eurise.core.suite

import android.content.Context
import android.content.Intent

/**
 * Manda all'app Eurise (la Scrivania con Attività e Note) una cosa da fare o una nota, da un altro APK della suite
 * (Telefono, Messaggi, Tastiera). Passa solo tra app firmate con la stessa chiave: il ricevitore dell'app Eurise chiede
 * il permesso della suite a chi manda, e il messaggio arriva solo a chi ha il permesso.
 */
object HubBridge {
    const val ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK"
    const val ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE"

    /** [hubPackage] e [permission] cambiano tra la versione di prova (.debug) e quella ufficiale. */
    fun send(context: Context, hubPackage: String, permission: String, action: String, text: String): Boolean {
        val installed = runCatching { context.packageManager.getPackageInfo(hubPackage, 0); true }.getOrDefault(false)
        if (!installed || text.isBlank()) return false
        context.sendBroadcast(Intent(action).setPackage(hubPackage).putExtra(Intent.EXTRA_TEXT, text.trim()), permission)
        return true
    }
}
