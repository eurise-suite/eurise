package eu.eurise.postazione

import eu.eurise.postazione.data.Azioni
import eu.eurise.postazione.data.Bolla
import eu.eurise.postazione.data.Fase
import eu.eurise.postazione.data.Gesto
import eu.eurise.postazione.data.Linguetta
import eu.eurise.postazione.data.Misura
import eu.eurise.postazione.data.Pagina
import eu.eurise.postazione.data.Pagine
import eu.eurise.postazione.data.Sole
import eu.eurise.postazione.data.Tempo
import eu.eurise.postazione.data.Tipo
import eu.eurise.postazione.data.TipoBolla
import eu.eurise.postazione.data.Ai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class PagineTest {
    @Test fun linguetteSalvateERilette() {
        val l = Pagine.linguetteIniziali + Linguetta("c9", "Cantiere\tnord", Tipo.Libera)
        val r = Pagine.leggiLinguette(Pagine.scriviLinguette(l))
        assertEquals(4, r.size)
        assertEquals("Cantiere nord", r[3].nome)
        assertEquals(Pagine.linguetteIniziali, Pagine.leggiLinguette(""))
    }

    @Test fun bolleSalvateRiletteESenzaDoppioni() {
        val b = listOf(Bolla(TipoBolla.Meteo, Misura.Piccola), Bolla(TipoBolla.Meteo, Misura.Grande), Bolla(TipoBolla.DaFare, Misura.Media))
        assertEquals(listOf(Bolla(TipoBolla.Meteo, Misura.Piccola), Bolla(TipoBolla.DaFare, Misura.Media)), Pagine.leggiBolle(Pagine.scriviBolle(b), Tipo.Libera))
        // Mai salvate: quelle di partenza del tipo; salvate vuote: nessuna.
        assertEquals(Pagine.bolleIniziali(Tipo.Lavoro), Pagine.leggiBolle(null, Tipo.Lavoro))
        assertTrue(Pagine.leggiBolle("", Tipo.Lavoro).isEmpty())
    }

    @Test fun lerigheAffiancanoLeMezzeBolle() {
        val p = Bolla(TipoBolla.Meteo, Misura.Piccola)
        val m = Bolla(TipoBolla.Ascolto, Misura.Media)
        val l = Bolla(TipoBolla.DaFare, Misura.Larga)
        val q = Bolla(TipoBolla.Farmaci, Misura.Piccola)
        assertEquals(listOf(listOf(p, m), listOf(l), listOf(q)), Pagine.righe(listOf(p, m, l, q)))
        assertEquals(Misura.Piccola, Pagine.prossimaMisura(Misura.Grande))
    }

    @Test fun pagineEGesti() {
        val p = listOf(Pagina("p1", "Cantiere", Ai.LeChat), Pagina("p2", "Famiglia", Ai.Claude))
        assertEquals(p, Pagine.leggiPagine(Pagine.scriviPagine(p)))
        assertEquals(listOf("p2", "p1"), Pagine.sposta(p, 1, -1).map { it.id })
        assertEquals(Azioni.TELEFONO, Azioni.iniziale(Gesto.DueSu))
        assertTrue(Gesto.entries.all { Azioni.valida(Azioni.iniziale(it)) })
        assertTrue(Azioni.valida(Azioni.app("a.b/a.b.Main")))
        assertTrue(!Azioni.valida("app:niente") && !Azioni.valida("rm -rf"))
    }

    @Test fun albaETramontoARoma() {
        // 27 novembre a Roma: alba verso le 7:14, tramonto verso le 16:40.
        val (alba, tramonto) = Sole.orari(LocalDate.of(2026, 11, 27), 41.9, 12.5, ZoneId.of("Europe/Rome"))!!
        assertTrue(abs(alba.toSecondOfDay() / 60 - (7 * 60 + 14)) <= 10)
        assertTrue(abs(tramonto.toSecondOfDay() / 60 - (16 * 60 + 40)) <= 10)
    }

    @Test fun fasiEPosizioneDelSole() {
        val a = LocalTime.of(7, 14); val t = LocalTime.of(16, 40)
        assertEquals(Fase.Notte, Sole.fase(LocalTime.of(3, 0), a, t))
        assertEquals(Fase.Alba, Sole.fase(LocalTime.of(7, 20), a, t))
        assertEquals(Fase.Giorno, Sole.fase(LocalTime.of(12, 30), a, t))
        assertEquals(Fase.Tramonto, Sole.fase(LocalTime.of(16, 30), a, t))
        assertEquals(Fase.Sera, Sole.fase(LocalTime.of(18, 0), a, t))
        assertNull(Sole.posizione(LocalTime.of(23, 0), a, t))
        val mezzogiorno = Sole.posizione(LocalTime.of(11, 57), a, t)!!
        val mattina = Sole.posizione(LocalTime.of(8, 0), a, t)!!
        // Sempre nella metà destra, più alto a mezzogiorno.
        assertTrue(mezzogiorno.first >= 0.5f && mattina.first >= 0.5f && mezzogiorno.second < mattina.second)
        assertEquals(Tempo.Temporale, Sole.tempo("Storm"))
        assertEquals(Tempo.Coperto, Sole.tempo("Fog"))
    }
}
