package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BriefingTest {
    private val morning = LocalDateTime.of(2026, 11, 13, 7, 42)

    private fun facts(
        now: LocalDateTime = morning,
        today: List<BriefEvent> = emptyList(),
        tomorrow: List<BriefEvent> = emptyList(),
        markets: List<BriefQuote> = emptyList(),
    ) = BriefFacts(now, ", Giulia", today, tomorrow, "Tempo tranquillo", "Sereno con 14°.", markets, listOf("Prima notizia."), listOf("World news"))

    private fun at(h: Int, m: Int = 0, day: Int = 13) = LocalDateTime.of(2026, 11, day, h, m)

    @Test
    fun morningGreetingCountsTheDay() {
        val two = listOf(BriefEvent("Dal commercialista", at(10), false, "Studio Ferri"), BriefEvent("Cena da Anna", at(20, 30), false, "via Garibaldi"))
        assertEquals("Buongiorno, Giulia. Una giornata ben disposta.", BriefingWriter.greeting(facts(today = two), evening = false))
        assertTrue(BriefingWriter.greeting(facts(), evening = false).contains("giornata libera"))
    }

    @Test
    fun eventsAreTold() {
        val list = listOf(BriefEvent("Dal commercialista", at(10), false, "Studio Ferri, Milano"), BriefEvent("Cena da Anna", at(20, 30), false, ""))
        val text = BriefingWriter.events(facts(today = list))
        assertEquals("Alle 10 Dal commercialista, a Studio Ferri, alle 20:30 Cena da Anna. Tra l'uno e l'altro, se posso permettermi, un pranzo vero.", text)
    }

    @Test
    fun eveningLooksAtTomorrow() {
        val f = facts(now = at(21), tomorrow = listOf(BriefEvent("Treno per Roma", at(7, 15, 14), false, "Milano Centrale"), BriefEvent("Pranzo", at(13, 0, 14), false, "")))
        val b = BriefingWriter.write(f)
        assertTrue(b.evening)
        assertTrue(b.greeting.startsWith("Buonasera, Giulia."))
        val events = b.sections.first().text
        assertTrue(events, events.contains("Domani si comincia alle 7:15 con Treno per Roma, a Milano Centrale, poi un altro impegno."))
        assertTrue(events.contains("non fare tardi"))
        assertFalse(b.sections.any { it.kind == BriefKind.Art })
    }

    @Test
    fun percentAndMarkets() {
        assertEquals("+0,8%", BriefingWriter.percent(0.83))
        assertEquals("−1,2%", BriefingWriter.percent(-1.249))
        assertEquals("invariato", BriefingWriter.percent(0.01))
        assertTrue(BriefingWriter.markets(listOf(BriefQuote("FTSE MIB", 0.5))).startsWith("FTSE MIB +0,5%."))
    }

    @Test
    fun catalogIsStableForADay() {
        val d = LocalDate.of(2026, 11, 13)
        assertEquals(AlfredCatalog.pearl(d, false), AlfredCatalog.pearl(d, false))
        assertTrue(AlfredCatalog.pearl(d, true).let { it.evening || it.genre == "classica" || it.genre == "jazz" })
        val b = BriefingWriter.write(facts())
        assertEquals(b.sections.size, b.spoken().size)
        assertTrue(b.spoken().first().startsWith(b.greeting))
    }
}
