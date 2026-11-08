package eu.eurise.feature.clock.system

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import eu.eurise.feature.clock.ClockActivity
import eu.eurise.feature.clock.R
import eu.eurise.feature.clock.data.ClockStore
import eu.eurise.feature.clock.logic.Alarm
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Canali e notifiche dell'Orologio: suoneria, timer in corso, suggerimenti della sera. */
object Notifications {
    const val CH_RING = "ring"
    const val CH_TIMERS = "timers"
    const val CH_HINTS = "hints"
    const val ID_RING = 1
    const val ID_EARLIER = 2
    const val ID_BEDTIME = 3
    private const val ID_TIMER_BASE = 100
    private val Time = DateTimeFormatter.ofPattern("HH:mm")

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_RING, context.getString(R.string.ch_ring), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false); setBypassDnd(true)
                },
                NotificationChannel(CH_TIMERS, context.getString(R.string.ch_timers), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_HINTS, context.getString(R.string.ch_hints), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun builder(context: Context, channel: String) = Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_clock)

    private fun broadcast(context: Context, action: String, id: Int, request: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, request, Intent(context, ClockReceiver::class.java).setAction(action).putExtra(ClockReceiver.EXTRA_ID, id).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun openApp(context: Context, tab: Int = 0): PendingIntent = PendingIntent.getActivity(
        context, 50 + tab, Intent(context, ClockActivity::class.java).putExtra(ClockActivity.EXTRA_TAB, tab).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Un avviso per ogni timer in corso, con il conto alla rovescia del sistema e i comandi +1 min e Ferma
     * (gli stessi che la bolla Timer e sveglie della Scrivania mostra).
     */
    fun updateTimers(context: Context) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val timers = ClockStore.get(context).timers.value
        val now = System.currentTimeMillis()
        nm.activeNotifications.filter { it.id >= ID_TIMER_BASE && timers.none { t -> t.id + ID_TIMER_BASE == it.id && (t.running || t.remainingMs > 0) } }
            .forEach { nm.cancel(it.id) }
        timers.filter { it.running || it.remainingMs > 0 }.forEach { t ->
            val title = t.label.ifBlank { context.getString(R.string.timer_default) }
            val b = builder(context, CH_TIMERS)
                .setContentTitle(if (t.pomodoro != null) context.getString(if (t.isWork) R.string.pomodoro_work else R.string.pomodoro_rest, title) else title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(context, 1))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.timer_plus1), broadcast(context, ClockReceiver.TIMER_PLUS, t.id, 30_000 + t.id)).build())
                .addAction(Notification.Action.Builder(null, context.getString(if (t.running) R.string.timer_pause else R.string.timer_resume), broadcast(context, ClockReceiver.TIMER_PAUSE, t.id, 31_000 + t.id)).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.timer_stop), broadcast(context, ClockReceiver.TIMER_STOP, t.id, 32_000 + t.id)).build())
            val end = t.endsAt
            if (end != null) {
                b.setUsesChronometer(true).setWhen(end).setShowWhen(true)
                if (android.os.Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true)
                b.setContentText(context.getString(R.string.timer_rings_at, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), ZoneId.systemDefault()).format(Time)))
            } else {
                b.setContentText(context.getString(R.string.timer_paused, eu.eurise.feature.clock.logic.formatDuration(t.remaining(now))))
            }
            nm.notify(ID_TIMER_BASE + t.id, b.build())
        }
    }

    /** Notifica della suoneria, con lo schermo intero sopra il blocco. */
    fun ring(context: Context, title: String, text: String, snooze: Boolean, dismiss: Boolean, timerId: Int?): Notification {
        channels(context)
        val full = PendingIntent.getActivity(
            context, 60, Intent(context, RingActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = builder(context, CH_RING)
            .setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (timerId != null) {
            b.addAction(Notification.Action.Builder(null, context.getString(R.string.timer_plus1), broadcast(context, ClockReceiver.TIMER_PLUS, timerId, 33_000)).build())
            b.addAction(Notification.Action.Builder(null, context.getString(R.string.timer_stop), broadcast(context, ClockReceiver.TIMER_STOP, timerId, 34_000)).build())
        } else {
            if (snooze) b.addAction(Notification.Action.Builder(null, context.getString(R.string.ring_snooze), broadcast(context, ClockReceiver.SNOOZE, 0, 35_000)).build())
            if (dismiss) b.addAction(Notification.Action.Builder(null, context.getString(R.string.ring_dismiss), broadcast(context, ClockReceiver.DISMISS, 0, 36_000)).build())
        }
        return b.build()
    }

    /** Programma l'avviso "è ora di dormire". */
    fun scheduleBedtime(context: Context, at: LocalDateTime, wake: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = broadcast(context, ClockReceiver.BEDTIME, 0, 37_000) {
            putExtra(ClockReceiver.EXTRA_TEXT, context.getString(R.string.bedtime_text, wake.format(Time)))
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    fun bedtime(context: Context, text: String) {
        channels(context)
        context.getSystemService(NotificationManager::class.java)?.notify(
            ID_BEDTIME, builder(context, CH_HINTS).setContentTitle(context.getString(R.string.bedtime_title)).setContentText(text).setAutoCancel(true).setContentIntent(openApp(context)).build(),
        )
    }

    /** Proposta (mai automatica) di anticipare la sveglia per un impegno presto. */
    fun earlier(context: Context, alarm: Alarm, event: String, eventAt: LocalDateTime, proposal: LocalDateTime) {
        channels(context)
        val text = context.getString(R.string.earlier_text, eventAt.format(Time), event, proposal.format(Time))
        val accept = broadcast(context, ClockReceiver.EARLIER, alarm.id, 38_000) {
            putExtra(ClockReceiver.EXTRA_MINUTE, proposal.hour * 60 + proposal.minute)
            putExtra(ClockReceiver.EXTRA_TEXT, context.getString(R.string.earlier_label, event))
        }
        context.getSystemService(NotificationManager::class.java)?.notify(
            ID_EARLIER,
            builder(context, CH_HINTS)
                .setContentTitle(context.getString(R.string.earlier_title))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.earlier_accept, proposal.format(Time)), accept).build())
                .build(),
        )
    }
}
