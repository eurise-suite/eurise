package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class OrariTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val h = Orari.Hours(setOf(1, 2, 3, 4, 5), LocalTime.of(9, 0), LocalTime.of(18, 0), "Europe/Rome")

    @Test fun roundTrip() {
        assertEquals(h, Orari.decode(JSONObject(Orari.encode(h).toString())))
        assertEquals("09:00", Orari.encode(h).getString("from"))
        assertNull(Orari.decode(JSONObject("""{"days":[],"from":"09:00","to":"18:00"}""")))
        assertNull(Orari.decode(JSONObject("""{"days":[1],"from":"18:00","to":"09:00"}""")))
        assertNull(Orari.decode(JSONObject()))
        assertEquals("9–18", Orari.label(h))
    }

    @Test fun nextStart() {
        // Martedì 24 alle 10: in orario.
        assertNull(Orari.nextStart(h, ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, rome)))
        // Martedì sera: mercoledì alle 9.
        assertEquals(ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, rome), Orari.nextStart(h, ZonedDateTime.of(2026, 11, 24, 22, 48, 0, 0, rome)))
        // Martedì mattina presto: stesso giorno.
        assertEquals(ZonedDateTime.of(2026, 11, 24, 9, 0, 0, 0, rome), Orari.nextStart(h, ZonedDateTime.of(2026, 11, 24, 7, 0, 0, 0, rome)))
        // Sabato: lunedì.
        assertEquals(ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, rome), Orari.nextStart(h, ZonedDateTime.of(2026, 11, 21, 12, 0, 0, 0, rome)))
        // Da un altro fuso: le 8 a Londra sono le 9 a Roma, in orario.
        assertNull(Orari.nextStart(h, ZonedDateTime.of(2026, 11, 24, 8, 30, 0, 0, ZoneId.of("Europe/London"))))
    }
}
