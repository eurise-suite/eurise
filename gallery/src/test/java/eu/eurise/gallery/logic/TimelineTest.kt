package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineTest {
    private fun at(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun item(id: Long, date: String, hour: Int = 12) = MediaItem(id, "u$id", false, at(date, hour), 0, "Camera", false)

    @Test
    fun daysAreNewestFirstAndKeepTheirPhotos() {
        val groups = groupByDay(listOf(item(1, "2026-11-01"), item(2, "2026-11-08", 9), item(3, "2026-11-08", 18)), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.parse("2026-11-08"), LocalDate.parse("2026-11-01")), groups.map { it.date })
        assertEquals(listOf(3L, 2L), groups[0].items.map { it.id })
    }

    @Test
    fun railHasOneMarkPerMonthWithItsYear() {
        val groups = groupByDay(listOf(item(1, "2026-11-08"), item(2, "2026-11-01"), item(3, "2025-12-24")), ZoneOffset.UTC)
        assertEquals(listOf(RailMark(2026, 11, 0), RailMark(2025, 12, 2)), railMarks(groups))
    }

    @Test
    fun longRailStaysWithinTheLimitAndKeepsTheNewestMonth() {
        val dates = (0 until 48).map { LocalDate.of(2026, 12, 1).minusMonths(it.toLong()).toString() }
        val marks = railMarks(groupByDay(dates.mapIndexed { i, d -> item(i.toLong(), d) }, ZoneOffset.UTC), max = 10)
        assertEquals(10, marks.size)
        assertEquals(RailMark(2026, 12, 0), marks.first())
        assertEquals(marks.sortedBy { it.groupIndex }, marks)
    }

    @Test
    fun fingerOnALabelIsInThatMonthAndBetweenLabelsInBetween() {
        val marks = listOf(RailMark(2026, 11, 0), RailMark(2026, 10, 10))
        assertEquals(0, groupAt(marks, 20, 0.25f))
        assertEquals(10, groupAt(marks, 20, 0.75f))
        assertEquals(5, groupAt(marks, 20, 0.5f))
        assertEquals(19, groupAt(marks, 20, 1f))
    }

    @Test
    fun memoriesAreSameDayOfPastYears() {
        val groups = groupByDay(listOf(item(1, "2026-11-08"), item(2, "2023-11-08"), item(3, "2023-11-09")), ZoneOffset.UTC)
        val memories = onThisDay(groups, LocalDate.parse("2026-11-08"))
        assertEquals(listOf(3), memories.map { it.yearsAgo })
        assertEquals(listOf(2L), memories.single().items.map { it.id })
    }
}
