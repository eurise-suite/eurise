package eu.eurise.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MapsDataTest {
    private val day = LocalDate.of(2026, 11, 19)
    private fun ev(h: Int?, title: String, place: String, d: LocalDate = day) =
        TodayData.Event(h?.let { LocalTime.of(it, 0) }, h?.let { LocalTime.of(it + 1, 0) }, title, place, d)

    @Test fun geoLinkIsEncodedAndChecked() {
        val g = MapsData.geo("  Via Lecco 12,   Monza ")!!
        assertEquals("geo:0,0?q=Via%20Lecco%2012%2C%20Monza", g)
        assertTrue(MapsData.isGeo(g))
        assertNull(MapsData.geo("   "))
        assertFalse(MapsData.isGeo("https://example.org"))
        assertFalse(MapsData.isGeo("geo:0,0?q=a b"))
    }

    @Test fun onlyEventsWithAPlaceNotYetEnded() {
        val s = TodayData.Summary(listOf(
            ev(13, "Pranzo con Anna", "Da Gino"),
            ev(8, "Colazione", "Bar Roma"),
            ev(15, "Chiamata", ""),
            ev(9, "Sopralluogo Villa", "Via Lecco 12, Monza", day.plusDays(1)),
        ), emptyList(), 0)
        val list = MapsData.withPlace(s, day.atTime(10, 0))
        assertEquals(listOf("Pranzo con Anna", "Sopralluogo Villa"), list.map { it.title })
        assertEquals("Oggi 13:00", MapsData.whenText(list[0], day))
        assertEquals("Domani 9:00", MapsData.whenText(list[1], day))
        assertEquals("tra 3 h", MapsData.startsIn(list[0], day.atTime(10, 0)))
        assertEquals("tra 20 min", MapsData.startsIn(list[0], day.atTime(12, 40)))
    }
}
