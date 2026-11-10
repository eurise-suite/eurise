package eu.eurise.feature.calendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.isMeeting
import eu.eurise.core.data.dates.ImportantDate
import eu.eurise.core.data.model.TaskList
import eu.eurise.feature.calendar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Promemoria della suite: regali per le date importanti e farmaci da prendere. Entrambi diventano voci tra
 * le cose da fare (create e tolte da qui) e, quando serve, un avviso. Tutto avviene sul telefono.
 */
object Reminders {
    private const val CHANNEL = "eurise_reminders"
    private const val GIFT_DAYS = 10L
    private const val MEETING_CHANNEL = "eurise_meetings"
    /** L'avviso arriva un minuto prima dell'inizio e vale fino a dieci minuti dopo (telefono in pausa, riavvio). */
    private const val MEETING_EARLY = 60_000L
    private const val MEETING_LATE = 10 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Da chiamare all'apertura dell'app e della Scrivania, e quando cambiano date o farmaci. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { sync(app) }
            schedule(app)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("eurise_reminders", Context.MODE_PRIVATE)

    /** Voci automatiche: chiave (regalo o dose) → id dell'attività creata. */
    private fun loadMap(context: Context): MutableMap<String, Long> =
        prefs(context).getString("tasks", "").orEmpty().lines().filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=').toLong() }.toMutableMap()

    private fun saveMap(context: Context, map: Map<String, Long>) =
        prefs(context).edit().putString("tasks", map.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()

    private fun notified(context: Context): MutableSet<String> = prefs(context).getStringSet("notified", emptySet())!!.toMutableSet()

    suspend fun sync(context: Context) {
        val data = EuriseData.create(context)
        val tasks = data.tasks
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val map = loadMap(context)
        val seen = notified(context)

        // Regali: da dieci giorni prima fino al giorno stesso.
        val dates = data.dates.dates.value
        val wantedGifts = mutableMapOf<String, Pair<String, LocalDate>>()
        dates.filter { it.giftReminder && !it.mine }.forEach { d ->
            val next = d.nextOn(today)
            val days = ChronoUnit.DAYS.between(today, next)
            if (days in 0..GIFT_DAYS) wantedGifts["gift:${d.id}:$next"] = giftTitle(context, d) to next
        }
        // Dosi di oggi già arrivate all'orario.
        val wantedDoses = mutableMapOf<String, Pair<String, String>>()
        data.medicines.medicines.value.filter { it.dueOn(today) }.forEach { m ->
            m.times.filter { !it.isAfter(now.toLocalTime()) }.forEach { t ->
                val title = listOf(m.name, m.dose).filter { it.isNotBlank() }.joinToString(" · ")
                wantedDoses["med:${m.id}:$today:$t"] = title to context.getString(R.string.reminder_med_note, t.toString())
            }
        }

        // Crea le voci mancanti.
        wantedGifts.forEach { (key, value) ->
            if (key !in map) map[key] = tasks.add(value.first, due = value.second, notes = context.getString(R.string.reminder_gift_note), listId = TaskList.DATES)
        }
        wantedDoses.forEach { (key, value) ->
            if (key !in map) {
                map[key] = tasks.add(value.first, due = today, notes = value.second, listId = TaskList.MEDICINES)
                if (key !in seen) { notify(context, key.hashCode(), context.getString(R.string.reminder_med_title), value.first); seen += key }
            }
        }
        // Toglie quelle non più valide e non spuntate: regali passati, dosi di giorni precedenti a ieri, farmaci tolti.
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val (key, id) = iterator.next()
            val task = tasks.get(id)
            val stale = when {
                task == null -> true
                key.startsWith("gift:") -> key !in wantedGifts
                key.startsWith("med:") -> {
                    val date = LocalDate.parse(key.split(':')[2])
                    date.isBefore(today.minusDays(1)) || data.medicines.medicines.value.none { it.id.toString() == key.split(':')[1] }
                }
                else -> false
            }
            if (stale) {
                if (task != null && !task.done) tasks.delete(id)
                iterator.remove()
            } else if (task != null) {
                // Voci nate prima degli elenchi automatici: si spostano nel loro elenco.
                val list = if (key.startsWith("med:")) TaskList.MEDICINES else TaskList.DATES
                if (task.listId != list) tasks.moveToList(id, list)
            }
        }
        saveMap(context, map)

        // Avvisi delle attività: quelli arrivati nelle ultime 12 ore e non ancora mostrati.
        tasks.remindersBetween(now.minusHours(12), now).forEach { task ->
            val key = "task:${task.id}:${task.reminderAt()}"
            if (key !in seen) {
                val text = task.time?.let { context.getString(R.string.reminder_task_at, it.toString()) }
                    ?: context.getString(R.string.reminder_task_today)
                notify(context, key.hashCode(), task.title, text)
                seen += key
            }
        }

        // Avvisi delle date importanti: il giorno prima e il giorno stesso.
        dates.filter { it.notify }.forEach { d ->
            val next = d.nextOn(today)
            val days = ChronoUnit.DAYS.between(today, next)
            val key = "date:${d.id}:$next:$days"
            if (days in 0..1 && key !in seen && now.hour >= 8) {
                val text = if (days == 0L) context.getString(R.string.reminder_today, dateLabel(context, d))
                else context.getString(R.string.reminder_tomorrow, dateLabel(context, d))
                notify(context, key.hashCode(), context.getString(R.string.reminder_dates_title), text)
                seen += key
            }
        }
        // Inizio delle riunioni: l'avviso apre la nota della riunione (solo eventi con altre persone invitate).
        val nowMillis = System.currentTimeMillis()
        data.calendar.startingBetween(nowMillis - MEETING_LATE, nowMillis + MEETING_EARLY).forEach { e ->
            val key = "meet:${e.eventId}:${e.beginMillis}"
            if (key !in seen && data.calendar.attendees(e.eventId).isMeeting()) {
                notifyMeeting(context, key.hashCode(), e.title, e.eventId, e.beginMillis)
                seen += key
            }
        }
        prefs(context).edit().putStringSet("notified", seen.filter { !it.contains("${today.minusDays(3)}") }.takeLast(400).toSet()).apply()
    }

    private fun giftTitle(context: Context, d: ImportantDate): String = when (d.kind) {
        ImportantDate.Kind.Birthday -> context.getString(R.string.gift_birthday, d.name)
        ImportantDate.Kind.Wedding -> context.getString(R.string.gift_wedding, d.name)
        ImportantDate.Kind.Engagement -> context.getString(R.string.gift_engagement, d.name)
        ImportantDate.Kind.Anniversary -> context.getString(R.string.gift_anniversary, d.name)
    }

    private fun dateLabel(context: Context, d: ImportantDate): String {
        val years = d.yearsOn(d.nextOn(LocalDate.now()))
        val base = when (d.kind) {
            ImportantDate.Kind.Birthday -> context.getString(R.string.label_birthday, d.name)
            else -> context.getString(R.string.label_anniversary, d.name)
        }
        return if (years != null) "$base ($years)" else base
    }

    /**
     * Prossima sveglia: il primo orario di un farmaco o di un avviso di un'attività ancora da arrivare,
     * o il controllo del mattino.
     */
    suspend fun schedule(context: Context) {
        val data = EuriseData.create(context)
        val now = LocalDateTime.now()
        val candidates = mutableListOf<LocalDateTime>()
        for (offset in 0L..1L) {
            val day = now.toLocalDate().plusDays(offset)
            data.medicines.medicines.value.filter { it.dueOn(day) }.forEach { m -> m.times.forEach { candidates += day.atTime(it) } }
            candidates += day.atTime(LocalTime.of(8, 30))
        }
        candidates += data.tasks.remindersBetween(now, now.plusDays(2)).mapNotNull { it.reminderAt() }
        val nowMillis = System.currentTimeMillis()
        data.calendar.startingBetween(nowMillis + MEETING_EARLY + 1, nowMillis + 2 * 86_400_000L)
            .firstOrNull { data.calendar.attendees(it.eventId).isMeeting() }
            ?.let { candidates += it.start.minusSeconds(MEETING_EARLY / 1000) }
        val next = candidates.filter { it.isAfter(now) }.minOrNull() ?: return
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 7, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Avviso d'inizio di una riunione, con il pulsante che apre la nota. */
    private fun notifyMeeting(context: Context, id: Int, title: String, eventId: Long, beginMillis: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(MEETING_CHANNEL, context.getString(R.string.meeting_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity")
            .putExtra("eu.eurise.extra.SECTION", "meeting:$eventId:$beginMillis")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, MEETING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(context.getString(R.string.meeting_starting, title))
            .setContentText(context.getString(R.string.meeting_starting_text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.meeting_open), open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Svegliato all'orario dei farmaci e al mattino; anche dopo il riavvio del telefono. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { Reminders.sync(context.applicationContext) }
                Reminders.schedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
