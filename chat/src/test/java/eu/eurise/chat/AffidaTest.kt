package eu.eurise.chat

import eu.eurise.core.insieme.Scheda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffidaTest {
    private val g = Affida.Given("c1", "Work", "!r", "@paolo:x", "Paolo", "Preventivo Rossi", "ven 22", 1000, "\$e", backAfter = 86_400_000L)

    @Test fun states() {
        assertNull(Affida.shown(g, 0, 2000))
        assertEquals(Scheda.State.Seen, Affida.shown(g, 1500, 2000))
        assertEquals(Scheda.State.Back, Affida.shown(g, 1500, 1000 + 86_400_000L))
        val done = Affida.update(listOf(g), mapOf("c1" to Triple(Scheda.State.Done, 3000L, "")))
        assertEquals(Scheda.State.Done, Affida.shown(done[0], 0, 1000 + 86_400_000L))
        assertEquals(Scheda.State.Back, Affida.shown(g.copy(retaken = true), 0, 2000))
    }

    @Test fun roundTrip() {
        val list = listOf(g, g.copy(id = "c2", email = "geom@x.it", state = Scheda.State.Declined, stateAt = 5, note = "sono fuori"))
        assertEquals(list, Affida.decode(Affida.encode(list)))
    }
}
