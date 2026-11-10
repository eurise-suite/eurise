package eu.eurise.core.data.modes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ModeRulesTest {
    private val weekdays = DayOfWeek.entries.take(5).toSet()
    private val work = ModeConfig(Mode.Work, schedule = Schedule(true, weekdays, LocalTime.of(9, 0), LocalTime.of(18, 0), skipHolidays = true))
    private val night = ModeConfig(Mode.Night, schedule = Schedule(true, DayOfWeek.entries.toSet(), LocalTime.of(23, 0), LocalTime.of(7, 0)))
    private val travel = ModeConfig(Mode.Travel, calendarTrigger = 5)
    private val configs = listOf(work, night, travel, ModeConfig(Mode.Home))
    private val holidays = { d: LocalDate -> ItalianHolidays.isHoliday(d) }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test fun `martedì alle dieci si lavora`() =
        assertEquals(Mode.Work, ModeRules.resolve(at(2026, 11, 10, 10), configs, null, emptyList(), holidays))

    @Test fun `l'8 dicembre è festa e il lavoro resta spento`() =
        assertNull(ModeRules.resolve(at(2026, 12, 8, 10), configs, null, emptyList(), holidays))

    @Test fun `la notte passa la mezzanotte`() {
        assertEquals(Mode.Night, ModeRules.resolve(at(2026, 11, 10, 23, 30), configs, null, emptyList(), holidays))
        assertEquals(Mode.Night, ModeRules.resolve(at(2026, 11, 11, 6, 59), configs, null, emptyList(), holidays))
        assertNull(ModeRules.resolve(at(2026, 11, 11, 7, 0), configs, null, emptyList(), holidays))
    }

    @Test fun `un evento del calendario scelto accende il viaggio e vince sul lavoro`() {
        val trip = Busy(5, at(2026, 11, 10, 0), at(2026, 11, 13, 0))
        assertEquals(Mode.Travel, ModeRules.resolve(at(2026, 11, 10, 10), configs, null, listOf(trip), holidays))
        assertEquals(Mode.Work, ModeRules.resolve(at(2026, 11, 10, 10), configs, null, listOf(trip.copy(calendarId = 6)), holidays))
    }

    @Test fun `un viaggio preparato da un biglietto accende il viaggio`() {
        val trip = Busy(Busy.TRIP, at(2026, 11, 20, 4, 35), at(2026, 11, 23, 22, 0))
        val noTrigger = configs.map { if (it.mode == Mode.Travel) it.copy(calendarTrigger = null) else it }
        assertEquals(Mode.Travel, ModeRules.resolve(at(2026, 11, 20, 10), noTrigger, null, listOf(trip), holidays))
        assertEquals(at(2026, 11, 23, 22, 0), ModeRules.nextChange(at(2026, 11, 23, 20), noTrigger, null, listOf(trip)))
    }

    @Test fun `la scelta a mano vale fino alla scadenza`() {
        val manual = ManualChoice(Mode.Home, at(2026, 11, 10, 12))
        assertEquals(Mode.Home, ModeRules.resolve(at(2026, 11, 10, 10), configs, manual, emptyList(), holidays))
        assertEquals(Mode.Work, ModeRules.resolve(at(2026, 11, 10, 12), configs, manual, emptyList(), holidays))
        assertNull(ModeRules.resolve(at(2026, 11, 10, 10), configs, ManualChoice(null, at(2026, 11, 10, 12)), emptyList(), holidays))
    }

    @Test fun `il prossimo cambio è la fine del lavoro`() =
        assertEquals(at(2026, 11, 10, 18), ModeRules.nextChange(at(2026, 11, 10, 10), configs, null, emptyList()))

    @Test fun `orari spenti non accendono nulla`() =
        assertFalse(ModeRules.inSchedule(at(2026, 11, 10, 10), Schedule(enabled = false), holidays))

    @Test fun `pasqua e pasquetta`() {
        assertEquals(LocalDate.of(2026, 4, 5), ItalianHolidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), ItalianHolidays.easter(2027))
        assertTrue(ItalianHolidays.isHoliday(LocalDate.of(2026, 4, 6)))
        assertTrue(ItalianHolidays.isTimeOffTitle("Ferie al mare"))
    }
}
