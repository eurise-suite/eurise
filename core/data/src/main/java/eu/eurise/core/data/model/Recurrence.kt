package eu.eurise.core.data.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Ripetizione di un'attività nella forma RRULE di iCalendar, la stessa che CalDAV porta a Murena.
 * Si leggono FREQ (DAILY, WEEKLY, MONTHLY, YEARLY), INTERVAL e, per le settimane, BYDAY; il resto della regola
 * si conserva così com'è ma non cambia il calcolo.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    enum class Frequency { Daily, Weekly, Monthly, Yearly }

    /** Il giorno della prossima volta dopo [from]. */
    fun next(from: LocalDate): LocalDate = when (frequency) {
        Frequency.Daily -> from.plusDays(interval.toLong())
        Frequency.Monthly -> from.plusMonths(interval.toLong())
        Frequency.Yearly -> from.plusYears(interval.toLong())
        Frequency.Weekly -> if (days.isEmpty()) from.plusWeeks(interval.toLong()) else nextWeekday(from)
    }

    /** Con BYDAY: il primo giorno indicato dopo [from]; passata la settimana si salta di [interval] settimane. */
    private fun nextWeekday(from: LocalDate): LocalDate {
        var day = from.plusDays(1)
        repeat(7) {
            if (day.dayOfWeek in days) {
                // Si è passati alla settimana successiva (lunedì compreso): con intervallo > 1 si saltano le altre.
                val crossed = day.dayOfWeek.value <= from.dayOfWeek.value
                return if (crossed && interval > 1) day.plusWeeks((interval - 1).toLong()) else day
            }
            day = day.plusDays(1)
        }
        return from.plusWeeks(interval.toLong())
    }

    fun toRule(): String = buildString {
        append("FREQ=").append(
            when (frequency) {
                Frequency.Daily -> "DAILY"
                Frequency.Weekly -> "WEEKLY"
                Frequency.Monthly -> "MONTHLY"
                Frequency.Yearly -> "YEARLY"
            }
        )
        if (interval > 1) append(";INTERVAL=").append(interval)
        if (days.isNotEmpty()) append(";BYDAY=").append(days.sorted().joinToString(",") { CODES.getValue(it) })
    }

    companion object {
        private val CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** Legge una regola RRULE; nulla se manca o se la frequenza non è tra quelle note. */
        fun parse(rule: String?): Recurrence? {
            if (rule.isNullOrBlank()) return null
            val parts = rule.removePrefix("RRULE:").split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).trim().uppercase() to it.substring(i + 1).trim().uppercase()
            }.toMap()
            val frequency = when (parts["FREQ"]) {
                "DAILY" -> Frequency.Daily
                "WEEKLY" -> Frequency.Weekly
                "MONTHLY" -> Frequency.Monthly
                "YEARLY" -> Frequency.Yearly
                else -> return null
            }
            val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            // BYDAY può avere un numero davanti (es. 1MO): conta solo il giorno.
            val days = parts["BYDAY"]?.split(',')?.mapNotNull { code ->
                val key = code.takeLast(2)
                CODES.entries.firstOrNull { it.value == key }?.key
            }?.toSet().orEmpty()
            return Recurrence(frequency, interval, if (frequency == Frequency.Weekly) days else emptySet())
        }
    }
}
