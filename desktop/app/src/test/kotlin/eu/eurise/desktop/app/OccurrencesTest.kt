package eu.eurise.desktop.app

import eu.eurise.core.sync.ICal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Le ripetizioni del Calendario sul computer (Occurrences). */
class OccurrencesTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun weeklyWithDays() {
        val dates = Occurrences.expand("FREQ=WEEKLY;BYDAY=MO,TH", d("2026-11-16"), d("2026-11-30")).toList()
        assertEquals(listOf(d("2026-11-16"), d("2026-11-19"), d("2026-11-23"), d("2026-11-26"), d("2026-11-30")), dates)
    }

    @Test fun countAndInterval() {
        assertEquals(listOf(d("2026-11-01"), d("2026-11-03"), d("2026-11-05")),
            Occurrences.expand("FREQ=DAILY;INTERVAL=2;COUNT=3", d("2026-11-01"), d("2026-12-31")).toList())
    }

    @Test fun monthlySkipsShortMonths() {
        assertEquals(listOf(d("2027-01-31"), d("2027-03-31")), Occurrences.expand("FREQ=MONTHLY;COUNT=2", d("2027-01-31"), d("2027-12-31")).toList())
    }

    @Test fun untilStops() {
        assertEquals(3, Occurrences.expand("FREQ=WEEKLY;UNTIL=20261130T000000Z", d("2026-11-16"), d("2027-01-31")).count())
    }

    @Test fun exdateAndMovedOccurrence() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:riunione
            DTSTART;TZID=Europe/Rome:20261116T090000
            DTEND;TZID=Europe/Rome:20261116T100000
            RRULE:FREQ=WEEKLY
            EXDATE;TZID=Europe/Rome:20261123T090000
            SUMMARY:Riunione settimanale
            END:VEVENT
            BEGIN:VEVENT
            UID:riunione
            RECURRENCE-ID;TZID=Europe/Rome:20261130T090000
            DTSTART;TZID=Europe/Rome:20261201T110000
            DTEND;TZID=Europe/Rome:20261201T120000
            SUMMARY:Riunione spostata
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val (ex, over) = Occurrences.exceptions(ics, rome)
        val occ = Occurrences.of(ICal.items(ics, rome), "cal", d("2026-11-16"), d("2026-12-07"), ex, over, rome)
        assertEquals(listOf(d("2026-11-16"), d("2026-12-01"), d("2026-12-07")), occ.map { it.day })
        assertEquals("Riunione spostata", occ[1].item.summary)
        assertEquals(9, occ[0].start!!.hour)
    }

    @Test fun allDaySpansDays() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:ferie\r\nDTSTART;VALUE=DATE:20261224\r\nDTEND;VALUE=DATE:20261227\r\nSUMMARY:Ferie\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val occ = Occurrences.of(ICal.items(ics, rome), "cal", d("2026-12-20"), d("2026-12-31"), zone = rome)
        assertEquals(listOf(d("2026-12-24"), d("2026-12-25"), d("2026-12-26")), occ.map { it.day })
        assertEquals(true, occ.all { it.allDay })
    }
}
