package eu.eurise.feature.assistant.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun namesFromContactsBecomePlaceholders() {
        val r = Redactor.redact("Scrivi ad Anna Rossi che la riunione con Marco slitta", listOf("Anna Rossi", "Marco Bianchi"))
        assertEquals("Scrivi ad [PERSONA 1] che la riunione con [PERSONA 2] slitta", r.text)
        assertEquals("Scrivi ad Anna Rossi, ciao Marco", r.restore("Scrivi ad [PERSONA 1], ciao PERSONA 2"))
    }

    @Test
    fun sameNameSamePlaceholder() {
        val r = Redactor.redact("Anna dice che Anna arriva", listOf("Anna Rossi"))
        assertEquals("[PERSONA 1] dice che [PERSONA 1] arriva", r.text)
    }

    @Test
    fun emailPhoneIbanFiscalCode() {
        val r = Redactor.redact(
            "mandala a mario.rossi@example.it o al 333 123 4567, IBAN IT60X0542811101000000123456, CF RSSMRA80A01F205X",
        )
        assertFalse(r.text.contains("example.it"))
        assertFalse(r.text.contains("4567"))
        assertFalse(r.text.contains("IT60X"))
        assertFalse(r.text.contains("RSSMRA"))
        assertTrue(r.text.contains("[EMAIL 1]"))
        assertTrue(r.text.contains("[TELEFONO 1]"))
        assertTrue(r.text.contains("[IBAN 1]"))
        assertTrue(r.text.contains("[CODICE 1]"))
    }

    @Test
    fun addressesAreRemovedButNotOrdinaryWords() {
        val r = Redactor.redact("Quanto costa parcheggiare in via Garibaldi 4 a Monza?")
        assertEquals("Quanto costa parcheggiare in [INDIRIZZO 1] a Monza?", r.text)
        val plain = Redactor.redact("Cosa cambia per il bonus mobili nel 2027?")
        assertEquals("Cosa cambia per il bonus mobili nel 2027?", plain.text)
        assertFalse(plain.changed)
    }

    @Test
    fun datesAndAmountsStay() {
        val r = Redactor.redact("Il 12.03.2026 ho speso 1.250 euro")
        assertEquals("Il 12.03.2026 ho speso 1.250 euro", r.text)
    }
}
