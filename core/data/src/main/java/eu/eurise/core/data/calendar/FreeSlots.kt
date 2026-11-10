package eu.eurise.core.data.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Orari liberi di un giorno, per proporre quando fissare un appuntamento. Si considera la giornata tra [dayStart] e
 * [dayEnd]; un orario è libero se l'intero appuntamento di [length] non tocca nessun impegno. Gli eventi di tutto il
 * giorno non occupano (ferie, compleanni). Gli inizi cadono ogni [step] e, per oggi, mai prima di [now].
 */
object FreeSlots {

    fun find(
        day: LocalDate,
        events: List<CalendarEvent>,
        length: Duration,
        now: LocalDateTime,
        dayStart: LocalTime = LocalTime.of(8, 0),
        dayEnd: LocalTime = LocalTime.of(20, 0),
        step: Duration = Duration.ofMinutes(30),
    ): List<LocalTime> {
        val busy = events.filter { !it.allDay && it.occursOn(day) }.map { it.start to it.end }
        val out = mutableListOf<LocalTime>()
        var start = day.atTime(dayStart)
        val last = day.atTime(dayEnd)
        while (!start.plus(length).isAfter(last)) {
            val end = start.plus(length)
            val future = start.isAfter(now)
            val clear = busy.none { (b, e) -> start.isBefore(e) && end.isAfter(b) }
            if (future && clear) out += start.toLocalTime()
            start = start.plus(step)
        }
        return out
    }

    /** Gli impegni del giorno, come fasce "9:00–10:30", per dire perché un orario manca. */
    fun busyRanges(day: LocalDate, events: List<CalendarEvent>): List<Pair<LocalTime, LocalTime>> =
        events.filter { !it.allDay && it.occursOn(day) }
            .map { maxOf(it.start, day.atStartOfDay()).toLocalTime() to minOf(it.end, day.plusDays(1).atStartOfDay().minusMinutes(1)).toLocalTime() }
            .sortedBy { it.first }
}
