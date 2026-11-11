package eu.eurise.browser

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private fun store() = HistoryStore(File.createTempFile("cronologia", ".txt").apply { delete() })

    @Test fun unaVisitaRipetutaSubitoNonRaddoppia() {
        val h = store()
        h.add("https://a.example/", "A", now = 1_000_000)
        h.add("https://a.example/", "", now = 1_000_000 + 60_000)
        h.add("https://b.example/", "B", now = 1_000_000 + 120_000)
        assertEquals(listOf("https://b.example/", "https://a.example/"), h.list().map { it.url })
        assertEquals("A", h.list().last().title)
    }

    @Test fun cercaECancella() {
        val h = store()
        h.add("https://comune.monza.it/", "Comune di Monza", now = 10)
        h.add("https://eurise.eu/", "Eurise", now = 20_000_000)
        assertEquals(1, h.list("monza").size)
        h.clear(since = 1_000)
        assertEquals(listOf("https://comune.monza.it/"), h.list().map { it.url })
    }

    @Test fun formatoERilettura() {
        val visits = listOf(Visit("https://a.example/", "Titolo\tcon tab", 5), Visit("https://b.example/", "", 6))
        val back = HistoryStore.parse(HistoryStore.format(visits))
        assertEquals("Titolo con tab", back[0].title)
        assertEquals(2, back.size)
    }

    @Test fun niente_piu_vecchio_di_90_giorni() {
        val list = mutableListOf(Visit("https://old/", "", 0), Visit("https://new/", "", 100L * 86_400_000L))
        HistoryStore.trim(list, now = 100L * 86_400_000L)
        assertEquals(listOf("https://new/"), list.map { it.url })
    }

    @Test fun lePagineNonWebNonEntrano() {
        val h = store()
        h.add("about:blank", "", now = 1)
        assertEquals(0, h.list().size)
    }
}
