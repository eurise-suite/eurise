package eu.eurise.desktop.app

import eu.eurise.core.sync.CalItem
import eu.eurise.core.sync.CalTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Gli appuntamenti in un intervallo di giorni, con le ripetizioni svolte (RRULE: giornaliera, settimanale con i giorni,
 * mensile, annuale; INTERVAL, COUNT, UNTIL) e le eccezioni del file (EXDATE e le occorrenze cambiate, RECURRENCE-ID).
 * Le regole più rare si mostrano solo alla prima data, mai inventate.
 */
object Occurrences {
    /** Un appuntamento in un giorno preciso: [day] il giorno, [start]/[end] null se dura tutto il giorno. */
    data class Occurrence(val uid: String, val item: CalItem, val day: LocalDate, val start: LocalDateTime?, val end: LocalDateTime?, val calendar: String) {
        val allDay: Boolean get() = start == null
    }

    private val basic = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /**
     * Le occorrenze di [items] (tutti gli elementi di un file: il principale e le sue eccezioni) fra [from] e [to]
     * compresi. [exdates] sono le date tolte dalla ripetizione.
     */
    fun of(items: List<CalItem>, calendar: String, from: LocalDate, to: LocalDate, exdates: Set<LocalDate> = emptySet(),
           overridden: Set<LocalDate> = emptySet(), zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        for (item in items) {
            if (item.todo) continue
            val start = item.start ?: continue
            val (firstDay, firstStart, length) = when (start) {
                is CalTime.Day -> Triple(start.date, null, Duration.ofDays(daysBetween(start.date, (item.end as? CalTime.Day)?.date)))
                is CalTime.At -> {
                    val s = start.time.withZoneSameInstant(zone).toLocalDateTime()
                    val e = (item.end as? CalTime.At)?.time?.withZoneSameInstant(zone)?.toLocalDateTime() ?: s.plusHours(1)
                    Triple(s.toLocalDate(), s, Duration.between(s, e).coerceAtLeast(Duration.ZERO))
                }
            }
            val dates = if (item.rrule == null) sequenceOf(firstDay) else expand(item.rrule!!, firstDay, to)
            for (d in dates) {
                if (d.isAfter(to)) break
                if (item.rrule != null && (d in exdates || (d != firstDay && d in overridden))) continue
                // Gli appuntamenti di più giorni compaiono in ogni giorno che toccano.
                val startAt = firstStart?.let { LocalDateTime.of(d, it.toLocalTime()) }
                val endAt = startAt?.plus(length)
                val lastDay = endAt?.let { if (it.toLocalTime().toSecondOfDay() == 0 && it.toLocalDate().isAfter(d)) it.toLocalDate().minusDays(1) else it.toLocalDate() }
                    ?: d.plusDays((length.toDays() - 1).coerceAtLeast(0))
                var day = d
                while (!day.isAfter(lastDay)) {
                    if (!day.isBefore(from) && !day.isAfter(to)) out += Occurrence(item.uid, item, day, startAt, endAt, calendar)
                    day = day.plusDays(1)
                }
            }
        }
        return out.sortedWith(compareBy<Occurrence>({ it.day }, { it.start != null }, { it.start }))
    }

    /** Le date di una regola di ripetizione a partire da [first], fino a [until] al massimo. */
    fun expand(rrule: String, first: LocalDate, until: LocalDate): Sequence<LocalDate> {
        val parts = rrule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
        val freq = parts["FREQ"]?.uppercase() ?: return sequenceOf(first)
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val end = parts["UNTIL"]?.let { u -> runCatching { if (u.length == 8) LocalDate.parse(u, DateTimeFormatter.BASIC_ISO_DATE) else LocalDateTime.parse(u.removeSuffix("Z"), basic).toLocalDate() }.getOrNull() }
        val last = listOfNotNull(until, end).minOrNull()!!
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { day(it.takeLast(2)) }?.toSet().orEmpty()
        // Regole che non svolgiamo (BYSETPOS, BYMONTHDAY multipli, BYDAY con numero): solo la prima data.
        if (parts.keys.any { it in setOf("BYSETPOS", "BYWEEKNO", "BYYEARDAY", "BYHOUR", "BYMINUTE") } ||
            parts["BYDAY"]?.split(',')?.any { it.length > 2 } == true && freq != "WEEKLY") return sequenceOf(first)
        return sequence {
            var n = 0
            when (freq) {
                "DAILY" -> { var d = first; while (!d.isAfter(last) && (count == null || n < count)) { yield(d); n++; d = d.plusDays(interval.toLong()) } }
                "WEEKLY" -> {
                    val days = byDay.ifEmpty { setOf(first.dayOfWeek) }.sortedBy { it.value }
                    var weekStart = first.with(DayOfWeek.MONDAY)
                    while (!weekStart.isAfter(last) && (count == null || n < count)) {
                        for (dw in days) {
                            val d = weekStart.plusDays((dw.value - 1).toLong())
                            if (d.isBefore(first) || d.isAfter(last) || (count != null && n >= count)) continue
                            yield(d); n++
                        }
                        weekStart = weekStart.plusWeeks(interval.toLong())
                    }
                }
                "MONTHLY" -> { var i = 0L; while (count == null || n < count) {
                    val month = first.withDayOfMonth(1).plusMonths(i * interval)
                    if (month.isAfter(last)) break
                    if (first.dayOfMonth <= month.lengthOfMonth()) { val d = month.withDayOfMonth(first.dayOfMonth); if (!d.isAfter(last)) { yield(d); n++ } }
                    i++
                } }
                "YEARLY" -> { var i = 0L; while (count == null || n < count) {
                    val y = first.year + i * interval
                    if (y > last.year) break
                    val d = runCatching { first.withYear(y.toInt()) }.getOrNull()
                    if (d != null && d.dayOfMonth == first.dayOfMonth && !d.isAfter(last)) { yield(d); n++ }
                    i++
                } }
                else -> yield(first)
            }
        }
    }

    private fun day(code: String): DayOfWeek? = when (code.uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY; "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun daysBetween(a: LocalDate, b: LocalDate?): Long = if (b == null || !b.isAfter(a)) 1 else java.time.temporal.ChronoUnit.DAYS.between(a, b)

    /** Le date tolte (EXDATE) e quelle cambiate (RECURRENCE-ID) di un file iCalendar, nel fuso [zone]. */
    fun exceptions(ics: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Set<LocalDate>, Set<LocalDate>> {
        val root = eu.eurise.core.sync.ICal.parse(ics)
        val ex = HashSet<LocalDate>(); val over = HashSet<LocalDate>()
        for (c in root.children) {
            for (p in c.props) {
                if (p.name == "EXDATE") p.value.split(',').forEach { v ->
                    eu.eurise.core.sync.ICal.time(p.copy(value = v), zone)?.let { t -> ex += date(t, zone) }
                }
                if (p.name == "RECURRENCE-ID") eu.eurise.core.sync.ICal.time(p, zone)?.let { over += date(it, zone) }
            }
        }
        return ex to over
    }

    private fun date(t: CalTime, zone: ZoneId): LocalDate = when (t) {
        is CalTime.Day -> t.date
        is CalTime.At -> t.time.withZoneSameInstant(zone).toLocalDate()
    }

    @Suppress("unused")
    private fun ZonedDateTime.local() = toLocalDateTime()
}
