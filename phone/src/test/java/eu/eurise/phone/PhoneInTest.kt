package eu.eurise.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneInTest {
    @Test
    fun trovaIlNumeroNelTesto() {
        assertEquals("3331234567", phoneIn("Chiamami al 333 123 4567 dopo le 5"))
        assertEquals("+393331234567", phoneIn("+39 333-123-4567"))
        assertEquals("0612345678", phoneIn("06.1234.5678"))
    }

    @Test
    fun nienteNumeroNienteIncolla() {
        assertNull(phoneIn("ci vediamo alle 18"))
        assertNull(phoneIn("12"))
    }
}
