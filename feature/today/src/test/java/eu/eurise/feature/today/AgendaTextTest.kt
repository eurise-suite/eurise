package eu.eurise.feature.today

import eu.eurise.core.data.weather.Day
import eu.eurise.core.data.weather.Forecast
import eu.eurise.core.data.weather.Hour
import eu.eurise.core.data.weather.Now
import eu.eurise.core.data.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AgendaTextTest {
    private val day = LocalDate.of(2026, 11, 17)

    private fun forecast(rainAt: Int? = 18): Forecast {
        val hours = (0..47).map { h ->
            val t = day.atStartOfDay().plusHours(h.toLong())
            val wet = rainAt != null && t.toLocalDate() == day && t.hour >= rainAt
            Hour(t, 10, if (wet) 80 else 5, if (wet) Sky.Rain else Sky.Clear)
        }
        val days = listOf(
            Day(day, Sky.Clear, 14, 6, 60, 20, null, null),
            Day(day.plusDays(1), Sky.Cloudy, 12, 5, 10, 15, null, null),
        )
        return Forecast(Now(12, 11, Sky.Clear, 5, 10, 180, true), hours, days, ZoneId.of("Europe/Rome"), hours)
    }

    @Test
    fun meteoDiOggi() {
        val m = AgendaText.meteo(forecast(), day, day.atTime(9, 0))!!
        assertEquals("12° · sereno", m.head)
        assertEquals("min 6° max 14° · pioggia dalle 18", m.tail)
    }

    @Test
    fun meteoDiDomani() {
        val m = AgendaText.meteo(forecast(), day.plusDays(1), day.atTime(9, 0))!!
        assertEquals("Nuvoloso · 5°/12°", m.head)
        assertEquals("", m.tail)
    }

    @Test
    fun meteoOltreLePrevisioni() {
        assertNull(AgendaText.meteo(forecast(), day.plusDays(9), day.atTime(9, 0)))
        assertNull(AgendaText.meteo(null, day, day.atTime(9, 0)))
    }

    @Test
    fun partenzaConMargine() {
        assertEquals(LocalTime.of(9, 7), AgendaText.leaveAt(LocalDateTime.of(day, LocalTime.of(9, 30)), 18))
    }

    @Test
    fun personeFuoriDalTitolo() {
        val (title, people) = AgendaText.splitPeople("Chiamare l'idraulico @Luca per la cantina")
        assertEquals("Chiamare l'idraulico per la cantina", title)
        assertEquals(listOf("Luca"), people)
        assertEquals("Scrivere a anna@posteo.de" to emptyList<String>(), AgendaText.splitPeople("Scrivere a anna@posteo.de"))
    }

    @Test
    fun settimanaEMassima() {
        assertEquals(47, AgendaText.week(day))
        assertTrue(Maxims.forDay(day).text.isNotBlank())
        assertTrue(Maxims.forDay(day) != Maxims.forDay(day.plusDays(1)))
    }

    @Test
    fun dayKindFollowsSundaysSaturdaysAndHolidays() {
        assertEquals(AgendaText.DayKind.Weekday, AgendaText.dayKind(java.time.LocalDate.of(2026, 11, 17)))
        assertEquals(AgendaText.DayKind.PreHoliday, AgendaText.dayKind(java.time.LocalDate.of(2026, 11, 21)))
        assertEquals(AgendaText.DayKind.Holiday, AgendaText.dayKind(java.time.LocalDate.of(2026, 11, 22)))
        assertEquals(AgendaText.DayKind.PreHoliday, AgendaText.dayKind(java.time.LocalDate.of(2026, 12, 24)))
        assertEquals(AgendaText.DayKind.Holiday, AgendaText.dayKind(java.time.LocalDate.of(2026, 12, 25)))
        assertEquals(AgendaText.DayKind.Holiday, AgendaText.dayKind(java.time.LocalDate.of(2026, 12, 8)))
        assertEquals(321, AgendaText.dayOfYear(java.time.LocalDate.of(2026, 11, 17)))
    }
}
