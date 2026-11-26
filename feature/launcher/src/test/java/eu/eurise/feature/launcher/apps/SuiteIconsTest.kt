package eu.eurise.feature.launcher.apps

import eu.eurise.core.designsystem.icon.SuiteDrawings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiteIconsTest {
    private fun app(pkg: String, section: String? = null) =
        AppEntry("k", "x", pkg, null, Category.Other, section)

    @Test
    fun sezioniDiEurisePerNome() {
        assertEquals("Calendario", SuiteIcons.keyFor(app("eu.eurise.hub", "calendar")))
        assertEquals("Drive", SuiteIcons.keyFor(app("eu.eurise.hub", "drive")))
        assertEquals("Alfred", SuiteIcons.keyFor(app("eu.eurise.hub", "alfred")))
        assertNull(SuiteIcons.keyFor(app("eu.eurise.hub", "privacy")))
    }

    @Test
    fun appSeparatePerPacchetto() {
        assertEquals("Browser", SuiteIcons.keyFor(app("eu.eurise.browser.debug")))
        assertEquals("Office", SuiteIcons.keyFor(app("eu.eurise.office")))
        assertEquals("Telefono", SuiteIcons.keyFor(app("eu.eurise.phone")))
        assertEquals("Fotocamera", SuiteIcons.keyFor(app("eu.eurise.camera.debug")))
        assertNull(SuiteIcons.keyFor(app("org.mozilla.firefox")))
    }

    @Test
    fun ogniChiaveHaIlSuoDisegno() {
        val keys = listOf("Calendario", "Note", "Attività", "Orologio", "Drive", "Scanner", "Calcolatrice", "Meteo", "Alfred",
            "Contatti", "Telefono", "Browser", "Email", "Chat", "Messaggi", "Galleria", "Password", "Tastiera", "Mappe", "Office",
            "Leggi", "Traduttore", "Agenda", "Backup", "Fotocamera")
        keys.forEach { assertTrue(it, SuiteDrawings.all.containsKey(it)) }
    }
}
