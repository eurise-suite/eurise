package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AmiciTest {
    @Test fun trip() {
        val stops = listOf(Amici.Stop("b", 10, "", "Casa a Cala Gonone"), Amici.Stop("a", 9, "21:00", "Traghetto"), Amici.Stop("c", 12, "09:00", "Gita in barca"))
        assertEquals(listOf("a", "b", "c"), Amici.sortedStops(stops).map { it.id })
        assertEquals("a", Amici.next(stops, 9, "18:00")?.id)
        assertEquals("b", Amici.next(stops, 9, "22:00")?.id)
        assertEquals(stops, Amici.decodeTrip(Amici.encodeTrip(stops)))
    }

    @Test fun gift() {
        var g = Amici.Gift("Giulia", 100, 98, "@sara:x", 0, listOf(Amici.Idea("i1", "Corso di ceramica", 18000, "@sara:x"), Amici.Idea("i2", "Cuffie", 16000, "@marco:x")))
        g = Amici.vote(g, "i1", "@a:x"); g = Amici.vote(g, "i1", "@b:x"); g = Amici.vote(g, "i2", "@c:x")
        g = Amici.vote(g, "i2", "@a:x") // cambia idea: il voto passa
        assertEquals(1, g.ideas[0].votes.size)
        assertEquals(2, g.ideas[1].votes.size)
        assertEquals("i2", Amici.winner(g)?.id)
        assertEquals(2000L, Amici.quota(g, 8))
        assertEquals(1500L, Amici.quota(g.copy(quota = 1500), 8))
        g = Amici.togglePaid(g, "@a:x")
        g = g.copy(lines = mapOf("@a:x" to "Auguri!"), iban = "IT00X", people = listOf("mailto:p@x.it"))
        assertEquals(g, Amici.decodeGift(Amici.encodeGift(g)))
    }
}
