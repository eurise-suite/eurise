package eu.eurise.feature.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleTextTest {

    @Test
    fun codesWithAWordAround() {
        assertEquals("482913", BubbleText.findCode("Il tuo codice di accesso è 482913. Non condividerlo."))
        assertEquals("482913", BubbleText.findCode("Codice: 482 913"))
        assertEquals("7731", BubbleText.findCode("Your verification code is 7731"))
        assertEquals("55120", BubbleText.findCode("OTP 55120 per il pagamento di 12,50 €"))
    }

    @Test
    fun numbersWithoutACodeAreIgnored() {
        assertNull(BubbleText.findCode("Ci vediamo alle 18 in via Roma 1234"))
        assertNull(BubbleText.findCode("Codice sconto ESTATE"))
        assertNull(BubbleText.findCode("Il codice è valido fino al 2026"))
        assertNull(BubbleText.findCode("Codice 12"))
    }

    @Test
    fun deliveries() {
        assertTrue(BubbleText.isDelivery("it.poste.app", "Novità"))
        assertTrue(BubbleText.isDelivery("ch.protonmail.android", "Il tuo pacco è in consegna oggi"))
        assertTrue(BubbleText.isDelivery("x", "Out for delivery today"))
        assertFalse(BubbleText.isDelivery("org.thoughtcrime.securesms", "Ci vediamo stasera?"))
    }

    @Test
    fun meetingLinks() {
        assertEquals("https://meet.jit.si/Cucina-Ferri", BubbleText.meetingLink("Collegati: https://meet.jit.si/Cucina-Ferri."))
        assertEquals("https://zoom.us/j/123456", BubbleText.meetingLink("Zoom https://zoom.us/j/123456 password 1"))
        assertNull(BubbleText.meetingLink("Via Roma 14, Milano"))
        assertNull(BubbleText.meetingLink("Menu: https://ristorante.it/menu"))
    }

    @Test
    fun clocks() {
        assertTrue(BubbleText.isClock("com.android.deskclock"))
        assertTrue(BubbleText.isClock("org.fossify.clock"))
        assertFalse(BubbleText.isClock("com.whatsapp"))
    }

    @org.junit.Test
    fun pecAppsAreRecognised() {
        org.junit.Assert.assertTrue(BubbleText.isPec("it.aruba.pec.mobile"))
        org.junit.Assert.assertTrue(BubbleText.isPec("it.infocert.mobile.legalmail"))
        org.junit.Assert.assertFalse(BubbleText.isPec("com.example.spectrum"))
    }

    @org.junit.Test
    fun taskPlacesAreReadAndHidden() {
        org.junit.Assert.assertEquals("Via Roma 12", BubbleText.taskPlace("Ritirare pacco #[Via Roma 12]"))
        org.junit.Assert.assertEquals("Piazza Duomo", BubbleText.taskPlace("Ritirare pacco #Piazza-Duomo"))
        org.junit.Assert.assertEquals("Ritirare pacco", BubbleText.withoutPlace("Ritirare pacco #[Via Roma 12]"))
        org.junit.Assert.assertNull(BubbleText.taskPlace("Ritirare pacco"))
    }

    @org.junit.Test
    fun contactsAreFoundInEventText() {
        org.junit.Assert.assertEquals("3331234567", BubbleText.phoneIn("Chiamare 333 123 4567 prima"))
        org.junit.Assert.assertEquals("+390612345678", BubbleText.phoneIn("Tel +39 06 12345678"))
        org.junit.Assert.assertNull(BubbleText.phoneIn("Via Roma 12 20100"))
        org.junit.Assert.assertEquals("anna@example.it", BubbleText.emailIn("Scrivere a anna@example.it."))
        org.junit.Assert.assertEquals("Anna Rossi", BubbleText.personIn("Pranzo con Anna Rossi"))
        org.junit.Assert.assertEquals("393331234567", BubbleText.whatsappNumber("3331234567"))
        org.junit.Assert.assertEquals("390612345678", BubbleText.whatsappNumber("+390612345678"))
    }
}
