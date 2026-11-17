package eu.eurise.core.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IcsTest {
    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun readsUtcTzidAndAllDay() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VTIMEZONE
            TZID:Europe/Rome
            BEGIN:STANDARD
            DTSTART:19701025T030000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY:Treno Milano\, Torino
            DTSTART:20261120T143000Z
            DTEND:20261120T160000Z
            LOCATION:Milano Centrale
            DESCRIPTION:Carrozza 5\nPosto 12A
            BEGIN:VALARM
            TRIGGER:-PT15M
            DESCRIPTION:avviso
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Riunione
            DTSTART;TZID="Europe/Rome":20261121T093000
            DURATION:PT1H30M
            RRULE:FREQ=WEEKLY
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Ferie con un titolo lungo che va a capo
              e continua qui
            DTSTART;VALUE=DATE:20261224
            DTEND;VALUE=DATE:20261227
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val e = Ics.parse(ics, rome)
        assertEquals(3, e.size)
        assertEquals("Treno Milano, Torino", e[0].title)
        assertEquals(LocalDateTime.of(2026, 11, 20, 15, 30), e[0].start)
        assertEquals(LocalDateTime.of(2026, 11, 20, 17, 0), e[0].end)
        assertEquals("Carrozza 5\nPosto 12A", e[0].description)
        assertEquals(LocalDateTime.of(2026, 11, 21, 9, 30), e[1].start)
        assertEquals(LocalDateTime.of(2026, 11, 21, 11, 0), e[1].end)
        assertTrue(e[1].repeats)
        assertTrue(e[2].allDay)
        assertEquals("Ferie con un titolo lungo che va a capo e continua qui", e[2].title)
        assertEquals(LocalDateTime.of(2026, 12, 27, 0, 0), e[2].end)
    }

    @Test
    fun missingEndAndBrokenEvents() {
        val e = Ics.parse("BEGIN:VEVENT\nSUMMARY:Senza fine\nDTSTART:20261201T100000\nEND:VEVENT\nBEGIN:VEVENT\nSUMMARY:Senza inizio\nEND:VEVENT", rome)
        assertEquals(1, e.size)
        assertEquals(LocalDateTime.of(2026, 12, 1, 11, 0), e[0].end)
    }

    @Test
    fun proposalFieldsAndCancel() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc-1\r\nSUMMARY:Studio Bianchi\r\nDTSTART:20261119T090000Z\r\n" +
            "ORGANIZER;CN=\"Laura Neri\":mailto:laura@murena.io\r\nSTATUS:TENTA\r\n TIVE\r\nBEGIN:VALARM\r\nSTATUS:X\r\n" +
            "END:VALARM\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val e = Ics.parse(ics, rome).single()
        assertEquals("abc-1", e.uid)
        assertEquals("TENTATIVE", e.status)
        assertEquals("Laura Neri", e.organizer)
        val cancelled = Ics.withStatus(ics, "CANCELLED")
        assertEquals("CANCELLED", Ics.parse(cancelled, rome).single().status)
        assertTrue("STATUS:X" in cancelled)
        assertTrue("TIVE" !in cancelled)
        // Senza STATUS: si aggiunge.
        val plain = "BEGIN:VEVENT\nUID:x\nDTSTART:20261119T090000Z\nORGANIZER:mailto:luca@example.org\nEND:VEVENT"
        assertEquals("luca@example.org", Ics.parse(plain, rome).single().organizer)
        assertEquals("CANCELLED", Ics.parse(Ics.withStatus(plain, "CANCELLED"), rome).single().status)
    }
}
