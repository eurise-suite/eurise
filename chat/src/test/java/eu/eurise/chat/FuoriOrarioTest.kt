package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FuoriOrarioTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val rules = FuoriOrario.Rules(Orari.Hours(setOf(1, 2, 3, 4, 5), LocalTime.of(8, 30), LocalTime.of(18, 30), "Europe/Rome"),
        people = setOf("@ferri:studio.it"), rooms = setOf("!cantiere"))

    @Test fun quietHours() {
        // Martedì 17 novembre 2026.
        assertFalse(FuoriOrario.quiet(rules, ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, rome)))
        assertTrue(FuoriOrario.quiet(rules, ZonedDateTime.of(2026, 11, 17, 22, 10, 0, 0, rome)))
        assertTrue(FuoriOrario.quiet(rules, ZonedDateTime.of(2026, 11, 17, 8, 15, 0, 0, rome)))
        assertTrue(FuoriOrario.quiet(rules, ZonedDateTime.of(2026, 11, 21, 11, 0, 0, 0, rome)))
    }

    @Test fun exceptions() {
        assertTrue(FuoriOrario.passes(rules, "@ferri:studio.it", "!x", "domani"))
        assertTrue(FuoriOrario.passes(rules, "@a:b", "!cantiere", "ciao"))
        assertTrue(FuoriOrario.passes(rules, "@a:b", "!x", "È URGENTE, chiamami"))
        assertFalse(FuoriOrario.passes(rules, "@a:b", "!x", "non urgentissimo"))
        assertFalse(FuoriOrario.passes(rules, "@a:b", "!x", "a domani"))
    }

    @Test fun roundTrip() {
        assertEquals(rules, FuoriOrario.decode(FuoriOrario.encode(rules)))
        assertEquals(setOf("urgente"), FuoriOrario.decode(null).words)
    }
}
