package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PallaTest {
    @Test fun cardRoundTrip() {
        val c = JSONObject(Palla.content("b1", "Luca", "I Rossi chiedono…", "Senti i Rossi", "stasera alle 18:00", "🏐 Luca ti passa la palla").toString())
        assertTrue(c.getString("body").startsWith("🏐 Luca ti passa la palla"))
        assertTrue(c.getString("body").contains("→ Senti i Rossi"))
        val card = Palla.card(c)!!
        assertEquals("b1", card.id)
        assertEquals("Senti i Rossi", card.task)
        val s = Palla.statusContent("\$ev", "b1", Palla.State.Done, "Fatto ✓")
        assertEquals("b1" to Palla.State.Done, Palla.status(s))
        assertEquals("\$ev", Relations.replyTo(s))
    }

    @Test fun states() {
        val b = Palla.Ball("b1", "Work", "!r", "@m", "Marta", "q", "t", 0, 1000)
        assertEquals(Palla.State.Passed, Palla.shown(b, 0))
        assertEquals(Palla.State.Seen, Palla.shown(b, 2000))
        assertTrue(Palla.stale(b, 2000, 1000 + Palla.QUIET_MS))
        val u = Palla.update(listOf(b), mapOf("b1" to (Palla.State.Working to 3000L))).single()
        assertEquals(Palla.State.Working, Palla.shown(u, 0))
        assertFalse(Palla.stale(u, 0, 1000 + 2 * Palla.QUIET_MS))
        // Una risposta più vecchia non torna indietro.
        val d = Palla.update(listOf(u.copy(state = Palla.State.Done, stateAt = 5000)), mapOf("b1" to (Palla.State.Working to 4000L))).single()
        assertEquals(Palla.State.Done, d.state)
        assertEquals(listOf(d), Palla.decode(Palla.encode(listOf(d))))
    }
}
