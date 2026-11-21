package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProgrammaTest {
    // Sabato 21 novembre 2026, 22:48.
    private val sat = LocalDateTime.of(2026, 11, 21, 22, 48)
    // Martedì 24 novembre 2026, 10:15.
    private val tue = LocalDateTime.of(2026, 11, 24, 10, 15)

    @Test fun proposedTimes() {
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 30), Programma.tomorrowAt(sat, 8, 30))
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 0), Programma.nextMonday(sat))
        // Di lunedì il «lunedì» è quello dopo.
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), Programma.nextMonday(LocalDateTime.of(2026, 11, 23, 7, 0)))
    }

    @Test fun workStart() {
        assertNull(Programma.workStart(tue))
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 0), Programma.workStart(sat))
        assertEquals(LocalDateTime.of(2026, 11, 24, 8, 0), Programma.workStart(LocalDateTime.of(2026, 11, 24, 6, 30)))
        assertEquals(LocalDateTime.of(2026, 11, 25, 8, 0), Programma.workStart(LocalDateTime.of(2026, 11, 24, 20, 0)))
        // Venerdì sera: lunedì.
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 0), Programma.workStart(LocalDateTime.of(2026, 11, 27, 19, 30)))
    }

    @Test fun remindChoices() {
        val morning = Programma.remindChoices(tue).map { it.first }
        assertEquals(listOf(Programma.Remind.Hour, Programma.Remind.Evening, Programma.Remind.Tomorrow), morning)
        assertEquals(listOf(Programma.Remind.Hour, Programma.Remind.Tomorrow), Programma.remindChoices(sat).map { it.first })
        assertEquals(LocalDateTime.of(2026, 11, 24, 11, 15), Programma.remindChoices(tue)[0].second)
    }

    @Test fun dayLabels() {
        assertEquals(Programma.Day.Today, Programma.day(tue.plusHours(2), tue))
        assertEquals(Programma.Day.Tomorrow, Programma.day(Programma.tomorrowAt(tue, 9), tue))
        assertEquals(Programma.Day.Week, Programma.day(tue.plusDays(4), tue))
        assertEquals(Programma.Day.Date, Programma.day(tue.plusDays(9), tue))
        assertEquals("9:05", Programma.time(java.time.LocalTime.of(9, 5)))
    }

    @Test fun plannedRoundTripAndDue() {
        val a = Programma.Planned("1", "Work", "!r", "Ciao «tu»", 2000, true, Programma.FADE_MS, work = true)
        val b = Programma.Planned("2", "Work", "!r", "Dopo", 1000, false)
        val c = Programma.Planned("3", "Personal", "!p", "Altro", 500, false)
        val list = Programma.decodePlanned(Programma.encodePlanned(listOf(a, b, c)))
        assertEquals(listOf(a, b, c), list)
        assertEquals(listOf(b, a), Programma.due(list, "Work", 5000))
        assertEquals(emptyList<Programma.Planned>(), Programma.due(list, "Work", 900))
        assertEquals(500L, Programma.next(list))
        assertEquals(emptyList<Programma.Planned>(), Programma.decodePlanned("non json"))
    }

    @Test fun fadeContent() {
        val c = Programma.content("ciao", Programma.FADE_MS)
        assertEquals(Programma.FADE_MS, Programma.fadeOf(JSONObject(c.toString())))
        assertEquals(0L, Programma.fadeOf(Programma.content("ciao", 0)))
        assertEquals(0L, Programma.fadeOf(null))
    }

    @Test fun receiptsAndFading() {
        val sync = JSONObject("""{"rooms":{"join":{"!r":{"ephemeral":{"events":[{"type":"m.receipt","content":{
            "${'$'}e1":{"m.read":{"@marco:x.it":{"ts":5000},"@me:x.it":{"ts":9000}}},
            "${'$'}e2":{"m.read.private":{"@anna:x.it":{"ts":7000}}}}}]}},"!q":{}}}}""")
        val read = Programma.receipts(sync, "@me:x.it")
        assertEquals(mapOf("!r" to 7000L), read)
        val f1 = Programma.Fading("Work", "!r", "\$a", 6000, 1000)
        val f2 = Programma.Fading("Work", "!r", "\$b", 8000, 1000)
        val marked = Programma.markRead(listOf(f1, f2), "Work", read)
        assertEquals(7000L, marked[0].readAt)
        assertEquals(0L, marked[1].readAt)
        assertEquals(listOf(marked[0]), Programma.gone(marked, "Work", 8000))
        assertTrue(Programma.gone(marked, "Work", 7999).isEmpty())
        assertEquals(marked, Programma.decodeFading(Programma.encodeFading(marked)))
    }
}
