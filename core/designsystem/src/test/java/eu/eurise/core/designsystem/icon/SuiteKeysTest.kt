package eu.eurise.core.designsystem.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SuiteKeysTest {
    @Test
    fun leAppDellaSuiteHannoIlLoroDisegno() {
        val casi = listOf(
            "eu.eurise.phone.debug" to ".MainActivity", "eu.eurise.phone" to "eu.eurise.phone.ContactsAlias",
            "eu.eurise.browser" to "x", "eu.eurise.mail" to "x", "eu.eurise.chat" to "x", "eu.eurise.messages" to "x",
            "eu.eurise.gallery" to "x", "eu.eurise.camera.debug" to "x", "eu.eurise.password" to "x", "eu.eurise.keyboard" to "x",
            "eu.eurise.maps" to "x", "eu.eurise.office.debug" to "x", "eu.eurise.voice" to "x", "eu.eurise.read" to "x",
            "eu.eurise.hub.debug" to "eu.eurise.hub.FileApp", "eu.eurise.hub" to "eu.eurise.hub.ScannerApp",
            "eu.eurise.hub" to "eu.eurise.hub.AgendaApp", "eu.eurise.hub" to "eu.eurise.hub.CalendarApp",
            "eu.eurise.hub" to "eu.eurise.hub.TasksApp", "eu.eurise.hub" to "eu.eurise.hub.NotesApp",
            "eu.eurise.hub" to "eu.eurise.hub.BackupApp",
        )
        casi.forEach { (pkg, cls) -> assertNotNull("$pkg/$cls", SuiteKeys.drawing(pkg, cls)) }
    }

    @Test
    fun contattiETelefonoSonoDueIcone() {
        assertEquals("Contatti", SuiteKeys.forComponent("eu.eurise.phone", "eu.eurise.phone.ContactsAlias"))
        assertEquals("Telefono", SuiteKeys.forComponent("eu.eurise.phone", "eu.eurise.phone.MainActivity"))
    }

    @Test
    fun leAltreAppNo() {
        assertNull(SuiteKeys.forComponent("org.mozilla.firefox", "org.mozilla.firefox.App"))
        assertNull(SuiteKeys.forComponent("eu.eurise.hub", "eu.eurise.hub.MainActivity"))
    }
}
