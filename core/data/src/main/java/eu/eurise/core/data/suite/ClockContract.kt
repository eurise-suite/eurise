package eu.eurise.core.data.suite

import android.app.AlarmManager
import android.content.Context
import android.content.Intent

/**
 * Accordo tra l'APK principale e l'APK di Eurise Orologio (in arrivo). Le due app si parlano solo con il permesso
 * di firma [PERMISSION]: lo hanno soltanto le app firmate con la stessa chiave, nessun'altra app può inviare o
 * ricevere questi messaggi.
 *
 * - [ACTION_SKIP_NEXT]: l'Orologio salta solo la prossima volta della sveglia che suona per prima e la rimette
 *   per la volta successiva; il sistema annuncia la nuova prossima sveglia e la home si aggiorna da sola.
 * - [ACTION_UNDO_SKIP]: annulla l'ultimo salto.
 */
object ClockContract {
    const val PACKAGE = "eu.eurise.clock"
    const val PERMISSION = "eu.eurise.permission.SUITE"
    const val ACTION_SKIP_NEXT = "eu.eurise.clock.action.SKIP_NEXT_ALARM"
    const val ACTION_UNDO_SKIP = "eu.eurise.clock.action.UNDO_SKIP_ALARM"

    /** Vero se la prossima sveglia del telefono l'ha messa Eurise Orologio (e quindi si può saltare da qui). */
    fun nextAlarmIsOurs(context: Context): Boolean = runCatching {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent?.creatorPackage == PACKAGE
    }.getOrDefault(false)

    /** Chiede all'Orologio di saltare la prossima sveglia. */
    fun skipNext(context: Context) = send(context, ACTION_SKIP_NEXT)

    /** Chiede all'Orologio di rimettere la sveglia appena saltata. */
    fun undoSkip(context: Context) = send(context, ACTION_UNDO_SKIP)

    private fun send(context: Context, action: String) {
        runCatching { context.sendBroadcast(Intent(action).setPackage(PACKAGE), PERMISSION) }
    }
}
