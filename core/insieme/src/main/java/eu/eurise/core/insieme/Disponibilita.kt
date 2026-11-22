package eu.eurise.core.insieme

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Disponibilità in un link (tavole InsDisponibilita, InsDisponibilitaMail), senza Android: si prova con i test.
 *
 * Dal calendario si prendono solo gli intervalli occupati; da qui escono gli orari liberi della settimana scelta, uno la
 * mattina e uno il pomeriggio per giorno (lunedì–venerdì), della durata chiesta. All'altro arrivano solo quegli orari,
 * nient'altro del calendario. Scelto uno, l'appuntamento nasce nei due calendari (con l'invito [Ics] per chi non usa Eurise).
 */
object Disponibilita {
    data class Busy(val start: Long, val end: Long, val title: String = "")
    data class Slot(val start: Long, val end: Long)

    val MORNING: Pair<LocalTime, LocalTime> = LocalTime.of(9, 0) to LocalTime.of(12, 30)
    val AFTERNOON: Pair<LocalTime, LocalTime> = LocalTime.of(14, 30) to LocalTime.of(18, 0)

    /** Il lunedì della settimana prossima (o di questa, se oggi è sabato o domenica: allora la prossima comincia domani o dopodomani). */
    fun nextWeek(today: LocalDate): LocalDate {
        var d = today.plusDays(1)
        while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
        return d
    }

    fun overlaps(start: Long, end: Long, busy: List<Busy>) = busy.any { it.start < end && it.end > start }

    /**
     * Gli orari liberi: per ogni giorno feriale da [from] per [days] giorni, il primo inizio libero (a passi di 30 minuti) nella
     * mattina e nel pomeriggio, dopo [now]; al massimo [max].
     */
    fun free(busy: List<Busy>, from: LocalDate, days: Int, durationMin: Int, zone: ZoneId, now: Long, max: Int = 8,
             halves: List<Pair<LocalTime, LocalTime>> = listOf(MORNING, AFTERNOON)): List<Slot> {
        val out = mutableListOf<Slot>()
        val len = durationMin * 60_000L
        for (i in 0 until days) {
            val day = from.plusDays(i.toLong())
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) continue
            for ((a, b) in halves) {
                var t = day.atTime(a).atZone(zone).toInstant().toEpochMilli()
                val stop = day.atTime(b).atZone(zone).toInstant().toEpochMilli()
                while (t + len <= stop) {
                    if (t > now && !overlaps(t, t + len, busy)) { out += Slot(t, t + len); break }
                    t += 30 * 60_000L
                }
                if (out.size >= max) return out
            }
        }
        return out
    }

    /** «Lun 9:00». */
    fun label(start: Long, zone: ZoneId, locale: Locale = Locale.ITALIAN): String {
        val t = Instant.ofEpochMilli(start).atZone(zone)
        val day = t.format(DateTimeFormatter.ofPattern("EEE d", locale)).replaceFirstChar { it.uppercase() }
        return day + " · " + t.format(DateTimeFormatter.ofPattern("H:mm", locale))
    }

    /** «Gio 21 · 9:00–10:30». */
    fun range(start: Long, end: Long, zone: ZoneId, locale: Locale = Locale.ITALIAN): String =
        label(start, zone, locale) + "–" + Instant.ofEpochMilli(end).atZone(zone).format(DateTimeFormatter.ofPattern("H:mm", locale))
}

/** Un invito di calendario (iCalendar, RFC 5545) per chi non usa Eurise: si apre con qualunque calendario. */
object Ics {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n")

    /** Le righe più lunghe di 75 caratteri si piegano (una riga che continua comincia con uno spazio). */
    private fun fold(line: String): String {
        if (line.length <= 75) return line
        val parts = mutableListOf(line.substring(0, 75))
        var i = 75
        while (i < line.length) { parts += " " + line.substring(i, minOf(line.length, i + 74)); i += 74 }
        return parts.joinToString("\r\n")
    }

    fun event(uid: String, title: String, start: Long, end: Long, place: String = "", note: String = "", organizer: String = "",
              attendee: String = "", now: Long = System.currentTimeMillis(), method: String = "REQUEST"): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Eurise//Insieme//IT", "CALSCALE:GREGORIAN", "METHOD:$method",
            "BEGIN:VEVENT", "UID:$uid@eurise", "DTSTAMP:" + stamp.format(Instant.ofEpochMilli(now)),
            "DTSTART:" + stamp.format(Instant.ofEpochMilli(start)), "DTEND:" + stamp.format(Instant.ofEpochMilli(end)), "SUMMARY:" + esc(title))
        if (place.isNotBlank()) lines += "LOCATION:" + esc(place)
        if (note.isNotBlank()) lines += "DESCRIPTION:" + esc(note)
        if (organizer.isNotBlank()) lines += "ORGANIZER:mailto:$organizer"
        if (attendee.isNotBlank()) lines += "ATTENDEE;RSVP=FALSE;PARTSTAT=ACCEPTED:mailto:$attendee"
        lines += listOf("END:VEVENT", "END:VCALENDAR")
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }
}
