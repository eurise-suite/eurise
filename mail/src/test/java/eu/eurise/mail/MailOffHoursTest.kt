package eu.eurise.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MailOffHoursTest {
    private val r = MailOffHours.Rules(work = setOf("w"), people = setOf("ferri@studio.it"))

    @Test fun hours() {
        // Martedì 17 novembre 2026.
        assertFalse(MailOffHours.quiet(r, LocalDateTime.of(2026, 11, 17, 9, 0)))
        assertTrue(MailOffHours.quiet(r, LocalDateTime.of(2026, 11, 17, 22, 10)))
        assertEquals(LocalDateTime.of(2026, 11, 18, 8, 30), MailOffHours.nextStart(r, LocalDateTime.of(2026, 11, 17, 22, 10)))
        // Venerdì sera → lunedì.
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 30), MailOffHours.nextStart(r, LocalDateTime.of(2026, 11, 20, 19, 0)))
        assertNull(MailOffHours.nextStart(r, LocalDateTime.of(2026, 11, 17, 10, 0)))
    }

    @Test fun passes() {
        assertTrue(MailOffHours.passes(r, "Ferri@Studio.it", "domani"))
        assertTrue(MailOffHours.passes(r, "a@b.it", "URGENTE: perdita in cantiere"))
        assertFalse(MailOffHours.passes(r, "a@b.it", "Riunione"))
        assertEquals(r, MailOffHours.decode(MailOffHours.encode(r)))
    }
}
