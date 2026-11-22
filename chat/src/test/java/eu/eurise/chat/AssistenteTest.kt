package eu.eurise.chat

import eu.eurise.core.insieme.Disponibilita
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistenteTest {
    @Test fun blocksAndRoundTrip() {
        val items = listOf(1L to Disponibilita.Busy(10, 20, "Sopralluogo"), 2L to Disponibilita.Busy(30, 40, "Dentista"), 3L to Disponibilita.Busy(5, 8, "Cena"))
        val b = Assistente.blocks(items, mapOf(1L to Assistente.Mode.Titles, 2L to Assistente.Mode.Busy))
        assertEquals(listOf(Assistente.Block(10, 20, "Sopralluogo"), Assistente.Block(30, 40)), b)
        val s = Assistente.Shared(1, 0, 100, b)
        assertEquals(s, Assistente.decode(Assistente.encode(s)))
        val m = mapOf(1L to Assistente.Mode.Titles, 2L to Assistente.Mode.Never)
        assertEquals(m, Assistente.decodeModes(Assistente.encodeModes(m)))
    }
}
