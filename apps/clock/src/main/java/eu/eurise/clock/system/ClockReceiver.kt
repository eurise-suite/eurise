package eu.eurise.clock.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import eu.eurise.clock.data.ClockStore
import eu.eurise.clock.logic.Alarm
import eu.eurise.clock.logic.Schedule
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Riceve gli scatti dell'AlarmManager e i pulsanti delle notifiche. */
class ClockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = ClockStore.get(context)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (intent.action) {
            RING -> {
                val alarm = store.alarm(id) ?: return
                val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
                // Una sveglia senza giorni, suonata, si spegne; le altre passano alla volta successiva.
                if (!alarm.repeating && !snoozed) store.saveAlarm(alarm.copy(enabled = false))
                RingService.startAlarm(context, id, intent.getIntExtra(EXTRA_SNOOZES, 0))
                Scheduler.rescheduleAll(context)
            }
            TIMER_DONE -> {
                val t = store.timer(id) ?: return
                val next = t.nextPhase(System.currentTimeMillis())
                if (next != null) {
                    // Pomodoro: la fase seguente parte da sola, con un breve suono.
                    store.saveTimer(next)
                    Quiet.apply(context, next.pomodoro?.quietDuringWork == true && next.isWork)
                    RingService.chime(context, id)
                } else {
                    store.saveTimer(t.copy(endsAt = null, remainingMs = 0))
                    if (t.pomodoro != null) Quiet.apply(context, false)
                    RingService.startTimer(context, id)
                }
                Scheduler.rescheduleAll(context)
            }
            TIMER_PLUS -> {
                store.timer(id)?.let { store.saveTimer(it.plus(60_000, System.currentTimeMillis()).start(System.currentTimeMillis())) }
                RingService.stop(context)
                Scheduler.rescheduleAll(context)
            }
            TIMER_STOP -> {
                store.timer(id)?.let { if (it.pomodoro != null) Quiet.apply(context, false) }
                store.deleteTimer(id)
                RingService.stop(context)
                Scheduler.rescheduleAll(context)
            }
            TIMER_PAUSE -> {
                val now = System.currentTimeMillis()
                store.timer(id)?.let { store.saveTimer(if (it.running) it.pause(now) else it.start(now)) }
                Scheduler.rescheduleAll(context)
            }
            SNOOZE -> RingService.snooze(context)
            DISMISS -> RingService.dismiss(context)
            EVENING -> evening(context, store)
            BEDTIME -> Notifications.bedtime(context, intent.getStringExtra(EXTRA_TEXT).orEmpty())
            EARLIER -> {
                val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
                val alarm = store.alarm(id)
                if (minute >= 0 && alarm != null) {
                    val now = LocalDateTime.now()
                    store.saveAlarm(Schedule.skipNext(alarm, now) { it in store.vacationDays })
                    store.saveAlarm(
                        alarm.copy(
                            id = store.nextId(), time = LocalTime.of(minute / 60, minute % 60), days = emptySet(), skipped = null,
                            label = intent.getStringExtra(EXTRA_TEXT).orEmpty(), earlier = false, enabled = true,
                        ),
                    )
                    Scheduler.rescheduleAll(context)
                }
                context.getSystemService(NotificationManager::class.java)?.cancel(Notifications.ID_EARLIER)
            }
        }
    }

    /** La sera: ora di dormire e, se serve, proposta di anticipare la sveglia di domani. */
    private fun evening(context: Context, store: ClockStore) {
        val now = LocalDateTime.now()
        val vacation = store.vacationDays
        val first = Schedule.first(store.alarms.value, now) { it in vacation }
        Scheduler.rescheduleAll(context)
        val (alarm, wake) = first ?: return
        if (Duration.between(now, wake).toHours() > 16) return
        val settings = store.settings.value
        if (settings.bedtimeReminder) {
            val bed = Schedule.bedtime(wake, Duration.ofMinutes(settings.sleepMinutes.toLong()))
            if (bed.isAfter(now)) Notifications.scheduleBedtime(context, bed, wake)
        }
        if (settings.useCalendar && alarm.earlier) {
            val event = CalendarReader.firstEvent(context, wake.toLocalDate()) ?: return
            val proposal = Schedule.earlierFor(wake, event.second, Duration.ofMinutes(90)) ?: return
            Notifications.earlier(context, alarm, event.first, event.second, proposal)
        }
    }

    companion object {
        const val RING = "eu.eurise.clock.RING"
        const val TIMER_DONE = "eu.eurise.clock.TIMER_DONE"
        const val TIMER_PLUS = "eu.eurise.clock.TIMER_PLUS"
        const val TIMER_STOP = "eu.eurise.clock.TIMER_STOP"
        const val TIMER_PAUSE = "eu.eurise.clock.TIMER_PAUSE"
        const val SNOOZE = "eu.eurise.clock.SNOOZE"
        const val DISMISS = "eu.eurise.clock.DISMISS"
        const val EVENING = "eu.eurise.clock.EVENING"
        const val BEDTIME = "eu.eurise.clock.BEDTIME"
        const val EARLIER = "eu.eurise.clock.EARLIER"
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZED = "snoozed"
        const val EXTRA_SNOOZES = "snoozes"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_TEXT = "text"
    }
}

/** Riprogramma tutto dopo un riavvio, un aggiornamento o un cambio d'ora. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Scheduler.rescheduleAll(context)
}

/** Messaggi delle altre app della suite (stessa firma): saltare la prossima sveglia o annullare il salto. */
class SuiteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ClockStore.get(context)
        val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
        when (intent.action) {
            eu.eurise.core.suite.ClockContract.ACTION_SKIP_NEXT -> {
                val now = LocalDateTime.now()
                val (alarm, _) = Schedule.first(store.alarms.value, now) { it in store.vacationDays } ?: return
                store.saveAlarm(Schedule.skipNext(alarm, now) { it in store.vacationDays })
                prefs.edit().putInt("last_skipped", alarm.id).apply()
            }
            eu.eurise.core.suite.ClockContract.ACTION_UNDO_SKIP -> {
                val alarm: Alarm = store.alarm(prefs.getInt("last_skipped", -1)) ?: return
                store.saveAlarm(alarm.copy(skipped = null))
            }
        }
        Scheduler.rescheduleAll(context)
    }
}

/** Silenzio durante il lavoro del Pomodoro, se l'utente ha dato l'accesso a "Non disturbare". */
object Quiet {
    fun apply(context: Context, on: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT < 23 || !nm.isNotificationPolicyAccessGranted) return
        runCatching { nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL) }
    }
}
