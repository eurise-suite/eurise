package eu.eurise.core.suite

import android.app.AlarmManager
import android.content.Context
import android.content.Intent

/**
 * Accordo tra l'APK principale e l'APK di Eurise Orologio. Le due app si parlano solo con il permesso di firma
 * [permission]: lo hanno soltanto le app firmate con la stessa chiave, nessun'altra app può inviare o ricevere
 * questi messaggi. Le versioni di prova (".debug") usano un permesso a parte, così convivono con le ufficiali.
 *
 * - [ACTION_SKIP_NEXT]: l'Orologio salta solo la prossima volta della sveglia che suona per prima; il sistema
 *   annuncia la nuova prossima sveglia e la home si aggiorna da sola.
 * - [ACTION_UNDO_SKIP]: annulla l'ultimo salto.
 */
object ClockContract {
    const val PACKAGE = "eu.eurise.clock"
    const val ACTION_SKIP_NEXT = "eu.eurise.clock.action.SKIP_NEXT_ALARM"
    const val ACTION_UNDO_SKIP = "eu.eurise.clock.action.UNDO_SKIP_ALARM"

    /** Il permesso di firma della suite, per la versione (ufficiale o di prova) di chi chiede. */
    fun permission(context: Context): String =
        if (context.packageName.endsWith(".debug")) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    /** Il pacchetto dell'Orologio di Eurise che ha messo la prossima sveglia, se l'ha messa lui. */
    fun nextAlarmOwner(context: Context): String? = runCatching {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent?.creatorPackage
            ?.takeIf { it == PACKAGE || it == "$PACKAGE.debug" }
    }.getOrNull()

    /** Vero se la prossima sveglia del telefono l'ha messa Eurise Orologio (e quindi si può saltare da qui). */
    fun nextAlarmIsOurs(context: Context): Boolean = nextAlarmOwner(context) != null

    /** Chiede all'Orologio di saltare la prossima sveglia. */
    fun skipNext(context: Context) = send(context, ACTION_SKIP_NEXT)

    /** Chiede all'Orologio di rimettere la sveglia appena saltata. */
    fun undoSkip(context: Context) = send(context, ACTION_UNDO_SKIP)

    private fun send(context: Context, action: String) {
        val owner = nextAlarmOwner(context) ?: return
        runCatching { context.sendBroadcast(Intent(action).setPackage(owner), permission(context)) }
    }
}
