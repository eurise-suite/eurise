package eu.eurise.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

class ScheduleTest {

    /** Domenica 8 novembre 2026, ore 21. */
    private val now = LocalDateTime.of(2026, 11, 8, 21, 0)
    private val work = Alarm(1, LocalTime.of(6, 45), "Lavoro", Alarm.Workdays)

    @Test
    fun workdaysRingOnMonday() {
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 45), Schedule.next(work, now))
    }

    @Test
    fun oneShotRingsTomorrowIfTheTimeHasPassed() {
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), Schedule.next(Alarm(2, LocalTime.of(7, 0)), now))
        assertEquals(LocalDateTime.of(2026, 11, 8, 22, 30), Schedule.next(Alarm(3, LocalTime.of(22, 30)), now))
    }

    @Test
    fun disabledNeverRings() {
        assertNull(Schedule.next(work.copy(enabled = false), now))
    }

    @Test
    fun holidaysAndVacationsAreSkipped() {
        val a = work.copy(skipHolidays = true)
        // Martedì 8 dicembre 2026 è l'Immacolata: dal lunedì 7 alle 7 la prossima è mercoledì 9.
        assertEquals(LocalDateTime.of(2026, 12, 9, 6, 45), Schedule.next(a, LocalDateTime.of(2026, 12, 7, 7, 0)))
        assertEquals(LocalDate.of(2026, 12, 8) to Silence.Holiday, Schedule.nextSilence(a, LocalDate.of(2026, 11, 9)))
        val ferie = setOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10))
        val b = work.copy(skipVacation = true)
        assertEquals(LocalDateTime.of(2026, 11, 11, 6, 45), Schedule.next(b, now) { it in ferie })
    }

    @Test
    fun skipOnlyTheNextTime() {
        val skipped = Schedule.skipNext(work, now)
        assertEquals(LocalDate.of(2026, 11, 9), skipped.skipped)
        assertEquals(LocalDateTime.of(2026, 11, 10, 6, 45), Schedule.next(skipped, now))
    }

    @Test
    fun firstOfMany() {
        val sat = Alarm(4, LocalTime.of(9, 0), days = setOf(DayOfWeek.SATURDAY))
        assertEquals(work, Schedule.first(listOf(sat, work), now)?.first)
    }

    @Test
    fun bedtimeAndEarlier() {
        val wake = LocalDateTime.of(2026, 11, 9, 6, 45)
        assertEquals(LocalDateTime.of(2026, 11, 8, 23, 0), Schedule.bedtime(wake, Duration.ofMinutes(450)))
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 0), Schedule.earlierFor(wake, LocalDateTime.of(2026, 11, 9, 7, 30), Duration.ofMinutes(90)))
        assertNull(Schedule.earlierFor(wake, LocalDateTime.of(2026, 11, 9, 9, 0), Duration.ofMinutes(90)))
    }

    @Test
    fun holidays() {
        assertEquals(LocalDate.of(2026, 4, 5), ItalianHolidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), ItalianHolidays.easter(2027))
        assertEquals("Pasquetta", ItalianHolidays.name(LocalDate.of(2026, 4, 6)))
        assertEquals("Immacolata", ItalianHolidays.name(LocalDate.of(2026, 12, 8)))
        assertNull(ItalianHolidays.name(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun challengeIsSmall() {
        repeat(50) {
            val c = Challenge.make(Random(it))
            assertTrue(c.answer in -20..90)
        }
    }
}
