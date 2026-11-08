package eu.eurise.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {

    private val sunday = LocalDate.of(2026, 11, 8)

    @Test
    fun dailyAndInterval() {
        assertEquals(sunday.plusDays(1), Recurrence.parse("FREQ=DAILY")!!.next(sunday))
        assertEquals(sunday.plusDays(3), Recurrence.parse("FREQ=DAILY;INTERVAL=3")!!.next(sunday))
    }

    @Test
    fun weeklyWithoutDaysKeepsTheWeekday() {
        assertEquals(LocalDate.of(2026, 11, 15), Recurrence.parse("FREQ=WEEKLY")!!.next(sunday))
    }

    @Test
    fun workdaysSkipTheWeekend() {
        val rule = Recurrence(Recurrence.Frequency.Weekly, days = Recurrence.WORKDAYS)
        val friday = LocalDate.of(2026, 11, 13)
        assertEquals(LocalDate.of(2026, 11, 16), rule.next(friday))
        assertEquals(LocalDate.of(2026, 11, 9), rule.next(sunday))
        assertEquals(LocalDate.of(2026, 11, 11), rule.next(LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun everyOtherMondayJumpsOneWeek() {
        val rule = Recurrence.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO")!!
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(LocalDate.of(2026, 11, 23), rule.next(monday))
    }

    @Test
    fun monthEndIsClamped() {
        assertEquals(LocalDate.of(2027, 2, 28), Recurrence.parse("FREQ=MONTHLY")!!.next(LocalDate.of(2027, 1, 31)))
        assertEquals(LocalDate.of(2027, 11, 8), Recurrence.parse("RRULE:FREQ=YEARLY")!!.next(sunday))
    }

    @Test
    fun ruleRoundTrip() {
        val rule = Recurrence(Recurrence.Frequency.Weekly, 2, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR", rule.toRule())
        assertEquals(rule, Recurrence.parse(rule.toRule()))
        assertEquals(setOf(DayOfWeek.MONDAY), Recurrence.parse("FREQ=WEEKLY;BYDAY=1MO")!!.days)
    }

    @Test
    fun unknownRulesAreIgnored() {
        assertNull(Recurrence.parse("FREQ=HOURLY"))
        assertNull(Recurrence.parse(""))
        assertNull(Recurrence.parse(null))
    }
}
