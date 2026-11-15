package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ICalTest {
    private val rome = ZoneId.of("Europe/Rome")

    private val event = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Altro//EN
        BEGIN:VEVENT
        UID:abc-1
        DTSTAMP:20261110T080000Z
        DTSTART;TZID=Europe/Rome:20261119T090000
        DTEND;TZID=Europe/Rome:20261119T120000
        SUMMARY:Sopralluogo Villa\, Monza
        LOCATION:Via Lecco 12
        DESCRIPTION:Portare la planimetria\nE il metro laser
        X-ALTRA-APP:da tenere
        LAST-MODIFIED:20261112T101500Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test fun leggeUnAppuntamento() {
        val item = ICal.items(event, rome).single()
        assertEquals("abc-1", item.uid)
        assertEquals("Sopralluogo Villa, Monza", item.summary)
        assertEquals("Portare la planimetria\nE il metro laser", item.description)
        val start = item.start as CalTime.At
        assertEquals(9, start.time.hour)
        assertEquals(Instant.parse("2026-11-12T10:15:00Z"), item.lastModified)
    }

    @Test fun modificaTieneLeProprietaSconosciute() {
        val cal = ICal.parse(event)
        val ev = cal.children.single()
        ICal.update(ev, "Sopralluogo Villa", ICal.time(ev.first("DTSTART")!!, rome), ICal.time(ev.first("DTEND")!!, rome), "Via Lecco 12", "", null,
            Instant.parse("2026-11-15T09:00:00Z"))
        val text = ICal.write(cal)
        assertTrue(text.contains("X-ALTRA-APP:da tenere"))
        assertTrue(text.contains("SEQUENCE:0"))
        assertTrue(text.contains("LAST-MODIFIED:20261115T090000Z"))
        assertTrue(text.contains("DTSTART:20261119T080000Z"))
        assertEquals("Sopralluogo Villa", ICal.items(text, rome).single().summary)
    }

    @Test fun attivitaNuovaFattaEGiornoIntero() {
        val text = ICal.create("t-1", todo = true, summary = "Pagare F24", start = null, end = CalTime.Day(LocalDate.of(2026, 11, 16)))
        val c = ICal.parse(text).children.single()
        ICal.update(c, "Pagare F24", null, CalTime.Day(LocalDate.of(2026, 11, 16)), "", "", completed = true)
        val item = ICal.item(c, rome)!!
        assertTrue(item.todo)
        assertTrue(item.completed)
        assertEquals(CalTime.Day(LocalDate.of(2026, 11, 16)), item.end)
        assertTrue(text.contains("DUE;VALUE=DATE:20261116"))
    }

    @Test fun righeLungheRipiegateEriunite() {
        val long = "Una descrizione molto lunga con le lettere accentate àèìòù che supera di parecchio i settantacinque byte"
        val text = ICal.create("x", false, "Titolo", CalTime.Day(LocalDate.of(2026, 1, 1)), null, description = long)
        assertTrue(text.lines().all { it.toByteArray(Charsets.UTF_8).size <= 76 })
        assertEquals(long, ICal.items(text, rome).single().description)
    }
}
