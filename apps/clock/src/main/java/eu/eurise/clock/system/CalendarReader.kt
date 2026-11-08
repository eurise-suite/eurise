package eu.eurise.clock.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Letture del calendario sul telefono, solo se l'utente ha acceso "Usa il calendario" e dato il permesso.
 * Nulla esce dal telefono.
 */
object CalendarReader {

    private val VacationWords = Regex("""(?iu)\b(ferie|vacanz[ae]|vacation|holiday|permesso|malattia)\b""")

    fun granted(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** I giorni dei prossimi quattro mesi coperti da un evento "Ferie", "Vacanza"… */
    fun vacationDays(context: Context): Set<LocalDate> {
        if (!granted(context)) return emptySet()
        val days = mutableSetOf<LocalDate>()
        instances(context, LocalDate.now(), LocalDate.now().plusDays(120)) { title, begin, end, allDay ->
            if (VacationWords.containsMatchIn(title)) {
                val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
                var d = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                val last = Instant.ofEpochMilli((end - 1).coerceAtLeast(begin)).atZone(zone).toLocalDate()
                while (!d.isAfter(last)) { days += d; d = d.plusDays(1) }
            }
        }
        return days
    }

    /** Il primo impegno con orario del giorno [day], con il titolo. */
    fun firstEvent(context: Context, day: LocalDate): Pair<String, LocalDateTime>? {
        if (!granted(context)) return null
        var best: Pair<String, LocalDateTime>? = null
        instances(context, day, day) { title, begin, _, allDay ->
            if (!allDay) {
                val at = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDateTime()
                if (at.toLocalDate() == day && (best == null || at.isBefore(best!!.second))) best = title to at
            }
        }
        return best
    }

    private fun instances(context: Context, from: LocalDate, to: LocalDate, each: (String, Long, Long, Boolean) -> Unit) {
        val zone = ZoneId.systemDefault()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, from.atStartOfDay(zone).toInstant().toEpochMilli())
        ContentUris.appendId(builder, to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        runCatching {
            context.contentResolver.query(
                builder.build(),
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) each(c.getString(0).orEmpty(), c.getLong(1), c.getLong(2), c.getInt(3) == 1)
            }
        }
    }
}
