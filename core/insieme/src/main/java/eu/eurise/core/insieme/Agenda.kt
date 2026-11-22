package eu.eurise.core.insieme

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Il calendario del telefono per Disponibilità, Condividi e l'agenda con l'assistente (tavole InsDisponibilita,
 * InsCondividi, InsAssistente). Si legge solo quando serve e solo sul telefono: da qui escono gli orari occupati (e i titoli
 * solo se l'hai scelto); si scrive l'appuntamento confermato nel calendario principale. Fuori dal thread principale.
 */
object Agenda {
    data class Cal(val id: Long, val name: String, val account: String, val writable: Boolean, val primary: Boolean)
    data class Item(val start: Long, val end: Long, val title: String, val place: String, val calendar: Long, val allDay: Boolean, val busy: Boolean)

    fun canRead(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite(context: Context) = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<Cal> {
        if (!canRead(context)) return emptyList()
        return runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(
                CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.VISIBLE,
            ), null, null, null)?.use { c ->
                val out = mutableListOf<Cal>()
                while (c.moveToNext()) {
                    if (c.getInt(5) == 0) continue
                    out += Cal(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                        c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR, c.getInt(4) == 1)
                }
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Gli impegni tra [from] e [to] (millisecondi), dei calendari visibili (o solo di [only]). */
    fun items(context: Context, from: Long, to: Long, only: Set<Long>? = null): List<Item> {
        if (!canRead(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY,
                CalendarContract.Instances.VISIBLE,
            ), null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                val out = mutableListOf<Item>()
                while (c.moveToNext()) {
                    if (c.getInt(7) == 0) continue
                    val cal = c.getLong(4)
                    if (only != null && cal !in only) continue
                    out += Item(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), cal, c.getInt(5) == 1,
                        c.getInt(6) != CalendarContract.Events.AVAILABILITY_FREE)
                }
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Gli occupati per gli orari liberi: gli impegni non «liberi» e non di tutto il giorno. */
    fun busy(context: Context, from: Long, to: Long, only: Set<Long>? = null): List<Disponibilita.Busy> =
        items(context, from, to, only).filter { it.busy && !it.allDay }.map { Disponibilita.Busy(it.start, it.end, it.title) }

    /** Scrive l'appuntamento nel calendario principale (o nel primo in cui si può scrivere). Vero se c'è riuscito. */
    fun insert(context: Context, title: String, start: Long, end: Long, place: String = "", note: String = ""): Boolean {
        if (!canWrite(context) || !canRead(context)) return false
        val cal = calendars(context).filter { it.writable }.let { l -> l.firstOrNull { it.primary } ?: l.firstOrNull() } ?: return false
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal.id)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (place.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, place)
                if (note.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, note)
            }) != null
        }.getOrDefault(false)
    }

    /** Senza il permesso: il Calendario si apre con l'appuntamento già scritto, basta salvarlo. */
    fun insertIntent(title: String, start: Long, end: Long, place: String = "", note: String = ""): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.Events.TITLE, title).putExtra(CalendarContract.Events.EVENT_LOCATION, place)
            .putExtra(CalendarContract.Events.DESCRIPTION, note)
}
