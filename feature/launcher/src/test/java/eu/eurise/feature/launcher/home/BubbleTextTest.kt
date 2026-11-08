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
}
