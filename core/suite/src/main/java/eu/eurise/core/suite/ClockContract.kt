package eu.eurise.core.suite

import android.app.AlarmManager
import android.content.Context
import android.content.Intent

/**
 * Accordo tra la Scrivania e l'Orologio, che stanno nello stesso APK ma in moduli che non si conoscono
 * (feature diverse). I messaggi restano dentro l'app: il ricevitore dell'Orologio non è esportato.
 *
 * - [ACTION_SKIP_NEXT]: l'Orologio salta solo la prossima volta della sveglia che suona per prima; il sistema
 *   annuncia la nuova prossima sveglia e la home si aggiorna da sola.
 * - [ACTION_UNDO_SKIP]: annulla l'ultimo salto.
 */
object ClockContract {
    const val ACTION_SKIP_NEXT = "eu.eurise.clock.action.SKIP_NEXT_ALARM"
    const val ACTION_UNDO_SKIP = "eu.eurise.clock.action.UNDO_SKIP_ALARM"

    /** Vero se la prossima sveglia del telefono l'ha messa l'Orologio di Eurise (e quindi si può saltare da qui). */
    fun nextAlarmIsOurs(context: Context): Boolean = runCatching {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.showIntent?.creatorPackage == context.packageName
    }.getOrDefault(false)

    /** Chiede all'Orologio di saltare la prossima sveglia. */
    fun skipNext(context: Context) = send(context, ACTION_SKIP_NEXT)

    /** Chiede all'Orologio di rimettere la sveglia appena saltata. */
    fun undoSkip(context: Context) = send(context, ACTION_UNDO_SKIP)

    private fun send(context: Context, action: String) {
        runCatching { context.sendBroadcast(Intent(action).setPackage(context.packageName)) }
    }

    /** Timer rapido nell'Orologio di Eurise, senza aprirlo. */
    fun timerIntent(context: Context, seconds: Int, label: String = ""): Intent =
        Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
            .setPackage(context.packageName)
            .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
