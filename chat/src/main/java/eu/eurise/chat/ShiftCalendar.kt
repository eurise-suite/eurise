package eu.eurise.chat

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Il calendario «Turni» sul telefono (tavola ChatTurniCrea): i propri turni di ogni gruppo, con il promemoria la sera
 * prima. È un calendario locale del sistema, quello che legge anche il Calendario di Eurise: non esce dal telefono.
 * Si accende con l'interruttore nella scheda Turni del gruppo (permesso del calendario chiesto solo allora);
 * spento, i turni di quel gruppo si tolgono dal calendario.
 *
 * Ogni evento porta in [Events.SYNC_DATA1] la stanza e la casella, così a ogni aggiornamento si riscrivono solo i turni
 * di quel gruppo (dalla settimana scorsa in poi).
 */
object ShiftCalendar {
    private const val ACCOUNT = "Eurise Chat"
    private const val PREFS = "shifts"
    /** Ora del promemoria la sera prima. */
    private const val EVENING = 20

    fun allowed(context: Context) = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun enabled(context: Context, roomId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("cal:$roomId", false) && allowed(context)

    fun setEnabled(context: Context, roomId: String, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("cal:$roomId", on).apply()
    }

    private fun Uri.asSync(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    private fun calendarId(context: Context, name: String): Long? {
        val r = context.contentResolver
        r.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?", arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null)
            ?.use { if (it.moveToFirst()) return it.getLong(0) }
        val v = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, 0xFF3E5C8A.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
        }
        return r.insert(Calendars.CONTENT_URI.asSync(), v)?.lastPathSegment?.toLongOrNull()
    }

    /** Toglie i turni del gruppo dalla settimana scorsa in poi. */
    fun clear(context: Context, roomId: String) {
        if (!allowed(context)) return
        runCatching {
            context.contentResolver.delete(Events.CONTENT_URI.asSync(), "${Events.SYNC_DATA1} LIKE ?", arrayOf("$roomId|%"))
        }
    }

    /**
     * Riscrive i turni di [me] del gruppo [roomId]: titolo «M · Mattina · Reparto B», con i colleghi nella descrizione e il
     * promemoria alle 20 della sera prima se [reminder]. Da chiamare fuori dal thread principale.
     */
    fun sync(context: Context, roomId: String, group: String, plan: Shifts.Plan, entries: List<Shifts.Entry>, me: String,
             names: (String) -> String, calendarName: String, reminder: Boolean) {
        if (!enabled(context, roomId)) return
        runCatching {
            val cal = calendarId(context, calendarName) ?: return
            val r = context.contentResolver
            val from = LocalDate.now().minusDays(7)
            val zone = ZoneId.systemDefault()
            // I turni passati da più di una settimana restano com'erano: si riscrive solo da lì in poi.
            r.delete(Events.CONTENT_URI.asSync(), "${Events.SYNC_DATA1} LIKE ? AND ${Events.CALENDAR_ID} = ? AND ${Events.DTSTART} >= ?",
                arrayOf("$roomId|%", cal.toString(), from.atStartOfDay(zone).toInstant().toEpochMilli().toString()))
            for (e in entries) {
                if (e.date.isBefore(from)) continue
                val t = plan.type(e.type) ?: continue
                val mine = Shifts.slots(plan, e).filter { it.person == me }
                for (s in mine) {
                    val others = e.people.filter { it != me }.joinToString(", ") { names(it) }
                    val v = ContentValues().apply {
                        put(Events.CALENDAR_ID, cal)
                        put(Events.TITLE, "${t.code} · ${t.name} · $group")
                        put(Events.DESCRIPTION, others)
                        put(Events.DTSTART, s.start.atZone(zone).toInstant().toEpochMilli())
                        put(Events.DTEND, s.end.atZone(zone).toInstant().toEpochMilli())
                        put(Events.EVENT_TIMEZONE, zone.id)
                        put(Events.HAS_ALARM, if (reminder) 1 else 0)
                        put(Events.SYNC_DATA1, "$roomId|${e.key}")
                    }
                    val id = r.insert(Events.CONTENT_URI.asSync(), v)?.lastPathSegment?.toLongOrNull() ?: continue
                    if (reminder) {
                        val evening = s.start.toLocalDate().minusDays(1).atTime(EVENING, 0)
                        val minutes = Duration.between(evening, s.start).toMinutes().coerceAtLeast(30)
                        r.insert(Reminders.CONTENT_URI.asSync(), ContentValues().apply {
                            put(Reminders.EVENT_ID, id)
                            put(Reminders.MINUTES, minutes)
                            put(Reminders.METHOD, Reminders.METHOD_ALERT)
                        })
                    }
                }
            }
        }
    }
}
