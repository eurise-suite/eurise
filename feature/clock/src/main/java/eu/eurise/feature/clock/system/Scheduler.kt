package eu.eurise.feature.clock.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import eu.eurise.feature.clock.ClockActivity
import eu.eurise.feature.clock.data.ClockStore
import eu.eurise.feature.clock.logic.Schedule
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Mette ogni sveglia e ogni timer nell'AlarmManager. Le sveglie usano setAlarmClock: il sistema le mostra come
 * "prossima sveglia" (e la Scrivania la legge, con chi l'ha messa), e le fa suonare anche in risparmio energetico.
 */
object Scheduler {

    fun rescheduleAll(context: Context) {
        val store = ClockStore.get(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (store.settings.value.useCalendar) store.vacationDays = CalendarReader.vacationDays(context)
        val vacation = store.vacationDays
        val now = LocalDateTime.now()
        // Le sveglie una tantum già suonate si spengono; un salto già passato si dimentica.
        store.alarms.value.forEach { a ->
            val skippedPast = a.skipped != null && a.skipped.isBefore(now.toLocalDate())
            if (skippedPast) store.saveAlarm(a.copy(skipped = null))
        }
        store.alarms.value.forEach { a ->
            val pi = ringIntent(context, a.id)
            val at = Schedule.next(a, now) { it in vacation }
            if (at == null) {
                am.cancel(pi)
            } else {
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent(context)), pi) }
            }
        }
        val nowMs = System.currentTimeMillis()
        store.timers.value.forEach { t ->
            val pi = timerIntent(context, t.id)
            val end = t.endsAt
            if (end == null) am.cancel(pi) else exact(am, end.coerceAtLeast(nowMs + 500), pi)
        }
        scheduleEvening(context, am)
        Notifications.updateTimers(context)
    }

    /** Ogni sera alle 20:30: ora di dormire e proposta di anticipare la sveglia se domani c'è un impegno presto. */
    private fun scheduleEvening(context: Context, am: AlarmManager) {
        var at = LocalDateTime.now().with(LocalTime.of(20, 30))
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val pi = PendingIntent.getBroadcast(
            context, 9_000, Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.EVENING), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    /** Rimanda la sveglia [id] di [minutes] minuti, come se fosse una sveglia a sé. */
    fun snooze(context: Context, id: Int, minutes: Int, count: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val millis = System.currentTimeMillis() + minutes * 60_000L
        val pi = PendingIntent.getBroadcast(
            context, 20_000 + id,
            Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.RING).putExtra(ClockReceiver.EXTRA_ID, id).putExtra(ClockReceiver.EXTRA_SNOOZED, true)
                .putExtra(ClockReceiver.EXTRA_SNOOZES, count),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent(context)), pi) }
    }

    private fun exact(am: AlarmManager, at: Long, pi: PendingIntent) {
        val can = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (can) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun ringIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.RING).putExtra(ClockReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun timerIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, 10_000 + id,
        Intent(context, ClockReceiver::class.java).setAction(ClockReceiver.TIMER_DONE).putExtra(ClockReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Aperto toccando la prossima sveglia nella tendina o nella Scrivania: identifica l'Orologio di Eurise. */
    fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1, Intent(context, ClockActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
    )
}
