package eu.eurise.feature.clock.logic

import java.time.LocalDate
import java.time.Month

/** Festività nazionali italiane, calcolate sul telefono (Pasquetta compresa). */
object ItalianHolidays {

    private val Fixed = listOf(
        Month.JANUARY to 1, Month.JANUARY to 6, Month.APRIL to 25, Month.MAY to 1, Month.JUNE to 2,
        Month.AUGUST to 15, Month.NOVEMBER to 1, Month.DECEMBER to 8, Month.DECEMBER to 25, Month.DECEMBER to 26,
    )

    /** Nome breve della festa, se [date] è festivo. */
    fun name(date: LocalDate): String? {
        val easter = easter(date.year)
        return when {
            date == easter -> "Pasqua"
            date == easter.plusDays(1) -> "Pasquetta"
            else -> Names[date.month to date.dayOfMonth]
        }
    }

    fun isHoliday(date: LocalDate): Boolean = name(date) != null

    /** Il primo giorno festivo da [from] in poi (entro un anno). */
    fun next(from: LocalDate): LocalDate? = generateSequence(from) { it.plusDays(1) }.take(370).firstOrNull { isHoliday(it) }

    private val Names = mapOf(
        Fixed[0] to "Capodanno", Fixed[1] to "Epifania", Fixed[2] to "Liberazione", Fixed[3] to "Festa del lavoro",
        Fixed[4] to "Festa della Repubblica", Fixed[5] to "Ferragosto", Fixed[6] to "Ognissanti", Fixed[7] to "Immacolata",
        Fixed[8] to "Natale", Fixed[9] to "Santo Stefano",
    )

    /** Domenica di Pasqua (calendario gregoriano, algoritmo anonimo di Meeus). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
