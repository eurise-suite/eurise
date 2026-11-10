package eu.eurise.core.data

import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.FreeSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FreeSlotsTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val earlier = LocalDateTime.of(2026, 11, 1, 0, 0)

    private fun event(from: String, to: String, allDay: Boolean = false) = CalendarEvent(
        eventId = 1, calendarId = 1, title = "x", location = "", description = "", allDay = allDay,
        start = day.atTime(LocalTime.parse(from)), end = day.atTime(LocalTime.parse(to)),
        color = 0, recurring = false, calendarName = "", writable = true, beginMillis = 0,
    )

    @Test fun emptyDayOffersEveryHalfHour() {
        val slots = FreeSlots.find(day, emptyList(), Duration.ofHours(1), earlier)
        assertEquals(LocalTime.of(8, 0), slots.first())
        assertEquals(LocalTime.of(19, 0), slots.last())
        assertEquals(23, slots.size)
    }

    @Test fun meetingBlocksOverlappingStarts() {
        val slots = FreeSlots.find(day, listOf(event("10:00", "12:30")), Duration.ofHours(1), earlier)
        assertTrue(LocalTime.of(9, 0) in slots)
        assertFalse(LocalTime.of(9, 30) in slots)
        assertFalse(LocalTime.of(12, 0) in slots)
        assertTrue(LocalTime.of(12, 30) in slots)
    }

    @Test fun allDayEventsDoNotBlock() {
        val slots = FreeSlots.find(day, listOf(event("00:00", "23:59", allDay = true)), Duration.ofHours(1), earlier)
        assertEquals(23, slots.size)
    }

    @Test fun todayStartsAfterNow() {
        val slots = FreeSlots.find(day, emptyList(), Duration.ofMinutes(30), day.atTime(15, 10))
        assertEquals(LocalTime.of(15, 30), slots.first())
    }
}
