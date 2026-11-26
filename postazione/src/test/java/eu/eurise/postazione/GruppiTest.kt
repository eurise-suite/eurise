package eu.eurise.postazione

import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Scheda
import org.junit.Assert.assertEquals
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

    @Test fun comunicaSempreAccantoAllaLente() =
        Scheda.entries.forEach { assertEquals(Gruppo.Comunica, Gruppi.barra(it).last()) }

    @Test fun preferitaAggiungiTogli() {
        val l = listOf("a/1", "b/2", "c/3")
        assertEquals(listOf("c/3", "a/1", "b/2"), Gruppi.preferita(l, "c/3"))
        assertEquals(l, Gruppi.aggiungi(l, "b/2"))
        assertEquals(listOf("a/1", "c/3"), Gruppi.togli(l, "b/2"))
    }

    @Test fun salvataggioTieneSoloLeInstallate() =
        assertEquals(listOf("a/1"), Gruppi.leggi(Gruppi.scrivi(listOf("a/1", "x/9")), setOf("a/1")))
}
