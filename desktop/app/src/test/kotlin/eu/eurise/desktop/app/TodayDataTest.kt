package eu.eurise.desktop.app

import eu.eurise.core.ponte.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TodayDataTest {
    private val day = LocalDate.of(2026, 11, 15)
    private val morning = day.atTime(9, 12)

    /** Come lo scrive il telefono (`PonteToday.fields`). */
    private val line = Json.write(mapOf(
        "events" to "11:00\t12:00\tRiunione con Studio Villa\tJitsi\t2026-11-15\n13:00\t14:00\tPranzo con Anna\tTrattoria da Gino\t2026-11-15\n" +
            "\t\tCompleanno di Luca\t\t2026-11-15\n08:30\t09:30\tTreno\tCentrale\t2026-11-16",
        "tasks" to "Pagare F24\t2026-11-14\nPreventivo Rossi\t2026-11-15\nComprare il toner\t",
        "tasksOpen" to "7",
        "day" to "2026-11-15",
        "age" to "3",
    ))

    @Test fun readsThePhoneSummary() {
        val s = TodayData.parseSummary(line)!!
        assertEquals(4, s.events.size)
        assertEquals(LocalTime.of(11, 0), s.events[0].start)
        assertNull(s.events[2].start)
        assertEquals(LocalDate.of(2026, 11, 16), s.events[3].day)
        assertEquals(3, s.tasks.size)
        assertNull(s.tasks[2].due)
        assertEquals(7, s.tasksOpen)
        assertTrue(TodayData.parseSummary(Json.write(mapOf("off" to "1", "age" to "1")))!!.off)
        assertNull(TodayData.parseSummary(""))
    }

    @Test fun headlineCountsOnlyToday() {
        val s = TodayData.parseSummary(line)!!
        assertEquals("Oggi hai 3 impegni, il primo alle 11.", TodayData.headline(s.events, morning))
        assertEquals("Oggi hai ancora 3 impegni, il prossimo alle 13.", TodayData.headline(s.events, day.atTime(11, 30)))
        assertEquals("Oggi non hai altri impegni.", TodayData.headline(emptyList(), morning))
        val one = listOf(TodayData.Event(LocalTime.of(15, 30), null, "Dentista", "", day))
        assertEquals("Oggi hai un impegno, alle 15:30.", TodayData.headline(one, morning))
    }

    @Test fun greetingAndDates() {
        assertEquals("Buongiorno, Marco.", TodayData.greeting(morning, "Marco"))
        assertEquals("Buonasera.", TodayData.greeting(day.atTime(20, 0), null))
        assertEquals("Domenica 15 novembre", TodayData.dateLine(day))
    }

    @Test fun rowsAndCountdown() {
        val s = TodayData.parseSummary(line)!!
        assertEquals("11:00 Riunione con Studio Villa", TodayData.eventTitle(s.events[0], day))
        assertEquals("domani 08:30 Treno", TodayData.eventTitle(s.events[3], day))
        assertEquals("Jitsi · fino alle 12:00", TodayData.eventSub(s.events[0]))
        assertEquals("tutto il giorno", TodayData.eventSub(s.events[2]))
        assertEquals("tra 1 h 48", TodayData.countdown(s.events[0], morning))
        assertEquals("adesso", TodayData.countdown(s.events[0], day.atTime(11, 10)))
        assertNull(TodayData.countdown(s.events[1], day.atTime(9, 0)))
        assertEquals("tra 20 min", TodayData.countdown(s.events[1], day.atTime(12, 40)))
    }

    @Test fun tasks() {
        val s = TodayData.parseSummary(line)!!
        assertEquals(listOf("Pagare F24", "Preventivo Rossi"), TodayData.dueToday(s, day).map { it.title })
        assertEquals("scaduta ieri", TodayData.dueLine(s.tasks[0], day))
        assertEquals("oggi", TodayData.dueLine(s.tasks[1], day))
        assertEquals("senza data", TodayData.dueLine(s.tasks[2], day))
        assertEquals("scaduta il 10 nov", TodayData.dueLine(TodayData.Todo("x", LocalDate.of(2026, 11, 10)), day))
        assertTrue(TodayData.overdue(s.tasks[0], day))
    }

    @Test fun writeAsYouSpeak() {
        val now = LocalDateTime.of(2026, 11, 16, 9, 0) // lunedì
        val f = TodayData.eventFields("dentista giovedì alle 15", now)!!
        assertEquals("2026-11-19T15:00", f["start"])
        assertEquals("2026-11-19T16:00", f["end"])
        assertEquals("dentista", f["title"]!!.lowercase())
        val all = TodayData.eventFields("cena da Anna domani", now)!!
        assertEquals("2026-11-17", all["start"])
        assertNull(all["end"])
        assertEquals("giovedì 19 novembre alle 15 · dentista", TodayData.eventPreview("dentista giovedì alle 15", now)!!.lowercase())
        assertNull(TodayData.eventFields("   ", now))
    }

    @Test fun recentFromThePonte() {
        val r = TodayData.parseRecent(listOf(
            Json.write(mapOf("k" to "handoff", "title" to "Orari treni", "sub" to "aperto dal telefono", "at" to "1000", "url" to "https://x.it/")),
            "non json",
            Json.write(mapOf("k" to "notify", "title" to "Anna ti ha scritto", "sub" to "Ci vediamo alle 13?", "at" to "900")),
        ))
        assertEquals(2, r.size)
        assertEquals("https://x.it/", r[0].url)
        assertNull(r[1].url)
    }
}
