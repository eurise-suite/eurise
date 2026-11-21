package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TempoTest {
    private val b = Tempo.Box(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 24))

    @Test fun roundTrip() {
        assertEquals(b, Tempo.decode(JSONObject(Tempo.encode(b).toString())))
        assertNull(Tempo.decode(JSONObject("""{"from":"2026-11-30","to":"2026-11-18"}""")))
        assertNull(Tempo.decode(JSONObject()))
        // Una fine fuori dal periodo non vale.
        assertNull(Tempo.decode(JSONObject("""{"from":"2026-11-18","to":"2026-11-30","end":"2026-12-05"}"""))!!.end)
    }

    @Test fun phases() {
        assertFalse(Tempo.showSummary(b, LocalDate.of(2026, 11, 24)))
        assertTrue(Tempo.showSummary(b, LocalDate.of(2026, 11, 25)))
        assertTrue(Tempo.showSummary(b, LocalDate.of(2026, 11, 30)))
        assertFalse(Tempo.archived(b, LocalDate.of(2026, 11, 30)))
        assertTrue(Tempo.archived(b, LocalDate.of(2026, 12, 1)))
        val noEnd = b.copy(end = null)
        assertEquals(LocalDate.of(2026, 11, 28), noEnd.summaryFrom)
    }

    @Test fun recap() {
        val events = listOf(
            Event("1", "a", "x", 0, Event.Kind.Image), Event("2", "a", "x", 0, Event.Kind.Image),
            Event("3", "a", "x", 0, Event.Kind.Expense), Event("4", "a", "x", 0, Event.Kind.File), Event("5", "a", "x", 0, Event.Kind.Text),
        )
        assertEquals(Tempo.Recap(2, 1, 1), Tempo.recap(events))
    }
}
