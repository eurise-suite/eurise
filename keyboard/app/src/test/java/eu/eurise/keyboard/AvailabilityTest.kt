package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AvailabilityTest {
    private val now = LocalDateTime.of(2026, 11, 10, 21, 0) // martedì sera
    private val thu = LocalDate.of(2026, 11, 12)

    private fun span(d: LocalDate, h1: Int, m1: Int, h2: Int, m2: Int) = Span(d.atTime(h1, m1), d.atTime(h2, m2))

    @Test fun freeDayGivesConsecutiveBlocks() {
        val s = Availability.slots(thu, emptyList(), 120, now)
        assertEquals(LocalTime.of(9, 0), s.first().from)
        assertEquals(LocalTime.of(11, 0), s.first().to)
        assertEquals(5, s.size) // 9-11, 11-13, 13-15, 15-17, 17-19
    }

    @Test fun busyBlocksAreSkippedAndRoundedToHalfHour() {
        val busy = listOf(span(thu, 9, 0, 12, 10), span(thu, 15, 0, 16, 0))
        val s = Availability.slots(thu, busy, 60, now)
        assertEquals(LocalTime.of(12, 30), s[0].from)
        assertTrue(s.none { it.from < LocalTime.of(16, 0) && it.to > LocalTime.of(15, 0) })
    }

    @Test fun pastDaysAndHoursAreNeverProposed() {
        assertTrue(Availability.slots(LocalDate.of(2026, 11, 9), emptyList(), 30, now).isEmpty())
        assertTrue(Availability.slots(now.toLocalDate(), emptyList(), 30, now).isEmpty()) // alle 21 la giornata è finita
        val morning = LocalDateTime.of(2026, 11, 12, 10, 10)
        assertEquals(LocalTime.of(10, 30), Availability.slots(thu, emptyList(), 30, morning).first().from)
    }

    @Test fun sentenceForTwoSlots() {
        val a = Slot(thu, LocalTime.of(15, 0), LocalTime.of(17, 0))
        val b = Slot(LocalDate.of(2026, 11, 13), LocalTime.of(10, 0), LocalTime.of(12, 0))
        assertEquals(
            "giovedì 12 dalle 15 alle 17 o venerdì 13 dalle 10 alle 12. Ti va bene uno dei due?",
            Availability.sentence(listOf(b, a), now.toLocalDate()),
        )
    }

    @Test fun sentenceUsesTomorrowAndMinutes() {
        val a = Slot(LocalDate.of(2026, 11, 11), LocalTime.of(9, 30), LocalTime.of(10, 0))
        assertEquals("domani dalle 9:30 alle 10. Ti va bene?", Availability.sentence(listOf(a), now.toLocalDate()))
    }
}
