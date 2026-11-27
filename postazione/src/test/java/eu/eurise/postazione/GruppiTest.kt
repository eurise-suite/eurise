package eu.eurise.postazione

import eu.eurise.postazione.data.Ai
import eu.eurise.postazione.data.Categoria
import eu.eurise.postazione.data.Contesto
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Siti
import eu.eurise.postazione.data.Usi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GruppiTest {
    private val installate = listOf(
        "eu.eurise.hub.debug/eu.eurise.hub.MainActivity",
        "eu.eurise.hub.debug/eu.eurise.hub.NotesApp",
        "eu.eurise.hub.debug/eu.eurise.hub.FileApp",
        "eu.eurise.office.debug/eu.eurise.office.OfficeActivity",
        "com.android.dialer/com.android.dialer.Main",
        "eu.eurise.messages/eu.eurise.messages.Main",
    )

    @Test fun suiteInDebugEAttivitaEsatte() = assertEquals(
        listOf("eu.eurise.office.debug/eu.eurise.office.OfficeActivity", "eu.eurise.hub.debug/eu.eurise.hub.NotesApp", "eu.eurise.hub.debug/eu.eurise.hub.FileApp"),
        Gruppi.risolvi(Gruppo.Lavoro.predefinite, installate, emptyMap()),
    )

    @Test fun senzaLaSuitePrendeLAppDelTelefono() = assertEquals(
        listOf("com.android.dialer/com.android.dialer.Main", "eu.eurise.messages/eu.eurise.messages.Main"),
        Gruppi.risolvi(Gruppo.Comunica.predefinite, installate, mapOf("dialer" to "com.android.dialer")),
    )

    @Test fun dockDiSeiConComunicaAccantoAllaLente() {
        assertEquals(Gruppi.POSTI, Gruppi.dockIniziale.size)
        assertEquals(Gruppo.Comunica.name, Gruppi.dockIniziale.last().id)
    }

    @Test fun dockSalvataERiletta() {
        val dock = Gruppi.sostituisci(Gruppi.dockIniziale, "Musica", Categoria("c1", "Cantiere\tA", "tools"))
        val riletta = Gruppi.leggiDock(Gruppi.scriviDock(dock))
        assertEquals(listOf("Lavoro", "Foto", "Mappe", "WebAI", "c1", "Comunica"), riletta.map { it.id })
        assertEquals("Cantiere A", riletta[4].nome)
        assertEquals("tools", riletta[4].icona)
        // Una dock rovinata o incompleta torna quella di partenza.
        assertEquals(Gruppi.dockIniziale, Gruppi.leggiDock("x\ty"))
    }

    @Test fun unaCategoriaProntaGiaNellaDockNonSiDoppia() {
        val dock = Gruppi.sostituisci(Gruppi.dockIniziale, "Musica", Categoria("Foto", "", "camera"))
        assertEquals(6, dock.map { it.id }.distinct().size)
    }

    @Test fun spostaNellaDock() {
        val dock = Gruppi.sposta(Gruppi.dockIniziale, "Foto", -1)
        assertEquals(listOf("Foto", "Lavoro"), dock.take(2).map { it.id })
        assertEquals(Gruppi.dockIniziale, Gruppi.sposta(Gruppi.dockIniziale, "Lavoro", -1))
    }

    @Test fun appDellaSchedaFissatePoiPiuUsate() {
        val inst = listOf("a/1", "b/2", "c/3", "d/4")
        val usi = mapOf("d/4" to 9, "c/3" to 4, "a/1" to 2)
        assertEquals(listOf("b/2", "d/4", "a/1"), Contesto.mostra(listOf("b/2", "x/9"), usi, setOf("c/3"), inst))
    }

    @Test fun domandaNellIndirizzoDelleAi() {
        assertEquals("https://claude.ai/new?q=due%20parole", Ai.Claude.pagina("due parole"))
        assertEquals("https://lumo.proton.me/", Ai.Lumo.pagina("x"))
        assertNull(Ai.Alfred.pagina("x"))
        assertEquals(Ai.Alfred, Ai.da("sconosciuta"))
        // Prima le libere ed europee, poi i servizi chiusi con una o due tacchette.
        val primoChiuso = Ai.entries.indexOfFirst { it.chiuso }
        assert(Ai.entries.drop(primoChiuso).all { it.chiuso && it.tacche <= 2 })
    }

    @Test fun preferitaAggiungiTogli() {
        val l = listOf("a/1", "b/2", "c/3")
        assertEquals(listOf("c/3", "a/1", "b/2"), Gruppi.preferita(l, "c/3"))
        assertEquals(l, Gruppi.aggiungi(l, "b/2"))
        assertEquals(listOf("a/1", "c/3"), Gruppi.togli(l, "b/2"))
    }

    @Test fun salvataggioTieneSoloLeInstallate() =
        assertEquals(listOf("a/1"), Gruppi.leggi(Gruppi.scrivi(listOf("a/1", "x/9")), setOf("a/1")))

    @Test fun iSitiRestanoNelGruppo() =
        assertEquals(listOf("a/1", "web:https://esempio.it"), Gruppi.leggi(Gruppi.scrivi(listOf("a/1", "web:https://esempio.it", "x/9")), setOf("a/1")))

    @Test fun indirizziDeiSiti() {
        assertEquals("https://esempio.it", Siti.normalizza(" esempio.it "))
        assertEquals("http://esempio.it/a?b=1", Siti.normalizza("http://esempio.it/a?b=1"))
        assertEquals("https://banca.esempio.it:8443/home", Siti.normalizza("https://banca.esempio.it:8443/home"))
        assertNull(Siti.normalizza("javascript:alert(1)"))
        assertNull(Siti.normalizza("intent://esempio.it#Intent;end"))
        assertNull(Siti.normalizza("file:///sdcard/a.html"))
        assertNull(Siti.normalizza("https://utente@esempio.it"))
        assertNull(Siti.normalizza("due parole.it"))
        assertNull(Siti.normalizza("senzapunto"))
        assertEquals("esempio.it", Siti.nomeDa("https://www.esempio.it/pagina"))
    }

    @Test fun piuUsate() {
        val usi = Usi.leggi(Usi.scrivi(mapOf("b/2" to 3, "a/1" to 7, "z/9" to 9)))
        assertEquals(listOf("a/1", "b/2"), Usi.piuUsate(usi, listOf("a/1", "b/2", "c/3"), 5))
    }
}
