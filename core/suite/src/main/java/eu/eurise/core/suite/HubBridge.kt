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

    /**
     * Per un'attività con data e ora già decise (Ricordamelo della Chat): giorno `2026-11-22`, ora `09:00` e note. Con
     * questi il testo non si interpreta («domani alle 9» dentro un messaggio citato non cambia la data).
     */
    const val EXTRA_DUE = "eu.eurise.extra.DUE"
    const val EXTRA_TIME = "eu.eurise.extra.TIME"
    const val EXTRA_NOTES = "eu.eurise.extra.NOTES"

    /** [hubPackage] e [permission] cambiano tra la versione di prova (.debug) e quella ufficiale. */
    fun send(
        context: Context, hubPackage: String, permission: String, action: String, text: String,
        due: java.time.LocalDate? = null, time: java.time.LocalTime? = null, notes: String? = null,
    ): Boolean {
        val installed = runCatching { context.packageManager.getPackageInfo(hubPackage, 0); true }.getOrDefault(false)
        if (!installed || text.isBlank()) return false
        val i = Intent(action).setPackage(hubPackage).putExtra(Intent.EXTRA_TEXT, text.trim())
        due?.let { i.putExtra(EXTRA_DUE, it.toString()) }
        time?.let { i.putExtra(EXTRA_TIME, "%02d:%02d".format(it.hour, it.minute)) }
        notes?.let { i.putExtra(EXTRA_NOTES, it) }
        context.sendBroadcast(i, permission)
        return true
    }
}
