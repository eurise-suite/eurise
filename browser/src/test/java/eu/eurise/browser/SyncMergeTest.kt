package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun i(n: Int, title: String = "Sito $n", folder: String = "", pinned: Boolean = false) =
        SyncItem("https://sito$n.example/", title, folder, pinned)

    @Test fun primaVoltaUnioneSenzaPerdite() {
        val out = SyncMerge.merge(emptyList(), listOf(i(1), i(2)), listOf(i(2, "Dal computer"), i(3)))
        assertEquals(listOf(i(1), i(2), i(3)), out)
    }

    @Test fun primaVoltaFissatoDaUnaParteResta() {
        val out = SyncMerge.merge(emptyList(), listOf(i(1)), listOf(i(1, pinned = true)))
        assertTrue(out.single().pinned)
    }

    @Test fun aggiuntaDaUnaParteEntra() {
        val base = listOf(i(1))
        assertEquals(listOf(i(1), i(2)), SyncMerge.merge(base, listOf(i(1)), listOf(i(1), i(2))))
        assertEquals(listOf(i(1), i(2)), SyncMerge.merge(base, listOf(i(1), i(2)), listOf(i(1))))
    }

    @Test fun toltaDaUnaParteNonTorna() {
        val base = listOf(i(1), i(2))
        assertEquals(listOf(i(1)), SyncMerge.merge(base, listOf(i(1)), listOf(i(1), i(2))))
        assertEquals(listOf(i(1)), SyncMerge.merge(base, listOf(i(1), i(2)), listOf(i(1))))
    }

    @Test fun toltaDaUnaParteMaCambiataDallAltraResta() {
        val base = listOf(i(1))
        assertEquals(listOf(i(1, "Nuovo nome")), SyncMerge.merge(base, emptyList(), listOf(i(1, "Nuovo nome"))))
        assertEquals(listOf(i(1, folder = "Lavoro")), SyncMerge.merge(base, listOf(i(1, folder = "Lavoro")), emptyList()))
    }

    @Test fun toltaDaTutteEDueEsce() {
        assertEquals(emptyList<SyncItem>(), SyncMerge.merge(listOf(i(1)), emptyList(), emptyList()))
    }

    @Test fun campoPerCampoVinceChiHaCambiato() {
        val base = listOf(i(1))
        val out = SyncMerge.merge(base, listOf(i(1, "Titolo dal telefono")), listOf(i(1, folder = "Dal computer", pinned = true)))
        assertEquals(listOf(SyncItem(i(1).url, "Titolo dal telefono", "Dal computer", true)), out)
    }

    @Test fun cambiatoDaTutteEDueVinceIlTelefono() {
        val out = SyncMerge.merge(listOf(i(1)), listOf(i(1, "Telefono")), listOf(i(1, "Computer")))
        assertEquals("Telefono", out.single().title)
    }

    @Test fun unaSecondaSincronizzazioneNonCambiaNulla() {
        val first = SyncMerge.merge(emptyList(), listOf(i(1), i(2)), listOf(i(3)))
        assertEquals(first, SyncMerge.merge(first, first, first))
    }

    @Test fun formatoAndataERitorno() {
        val items = listOf(i(1, folder = "Lavoro › Progetti", pinned = true), i(2))
        assertEquals(items, SyncMerge.decode(SyncMerge.encode(items)))
    }

    @Test fun soloIndirizziWebEInSeguito() {
        val text = """[{"u":"javascript:alert(1)","t":"x"},{"u":"file:///etc/passwd"},{"u":"https://a.example/","t":""},{"u":"https://a.example/","t":"doppio"},"rotto"]"""
        val out = SyncMerge.decode(text)
        assertEquals(1, out.size)
        assertEquals("a.example", out.single().title)
        assertEquals(emptyList<SyncItem>(), SyncMerge.decode("non è json"))
    }

    @Test fun tetto() {
        val many = (1..SyncMerge.MAX + 50).map { i(it) }
        assertEquals(SyncMerge.MAX, SyncMerge.merge(emptyList(), many, emptyList()).size)
    }
}
