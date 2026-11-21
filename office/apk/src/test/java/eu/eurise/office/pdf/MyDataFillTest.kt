package eu.eurise.office.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyDataFillTest {
    private val studio = listOf(
        "Nome" to "Anna", "Cognome" to "Bianchi", "Codice fiscale" to "BNCNNA80A41H501U", "CAP" to "00184",
        "Città" to "Roma", "Provincia" to "RM", "IBAN" to "IT60 X054 2811 1010 0000 0123 456", "Email" to "anna@studio.it",
        "Partita IVA" to "01234567890", "Albo" to "Ordine degli architetti 1234",
    )

    @Test fun abbreviazioniComuni() {
        assertEquals("BNCNNA80A41H501U", MyDataFill.valueFor("Cod. Fisc.", "modulo.cf", studio))
        assertEquals("00184", MyDataFill.valueFor("", "residenza.CAP", studio))
        assertEquals("RM", MyDataFill.valueFor("Prov.", "prov_1", studio))
        assertEquals("anna@studio.it", MyDataFill.valueFor("E-mail", "email", studio))
        assertEquals("01234567890", MyDataFill.valueFor("P. IVA", "piva", studio))
        assertEquals("Roma", MyDataFill.valueFor("Comune di residenza", "x", studio))
    }

    @Test fun nomeECognomeInsieme() {
        assertEquals("Anna Bianchi", MyDataFill.valueFor("Nome e cognome", "nominativo", studio))
        assertEquals("Bianchi Anna", MyDataFill.valueFor("Cognome e nome", "c", studio))
    }

    @Test fun ibanSenzaSpazi() {
        assertEquals("IT60X0542811101000000123456", MyDataFill.valueFor("IBAN", "iban", studio))
    }

    @Test fun datiAggiuntiDallaPersona() {
        assertEquals("Ordine degli architetti 1234", MyDataFill.valueFor("Albo", "albo", studio))
    }

    @Test fun nessunAbbinamentoIncerto() {
        assertNull(MyDataFill.valueFor("Firma", "firma", studio))
        assertNull(MyDataFill.valueFor("Stato civile", "stato_civile", studio))
    }

    @Test fun soloCampiDiTestoVuoti() {
        val fields = listOf(
            FormField("nome", "Nome", FormField.Kind.Text, "", emptyList()),
            FormField("cognome", "Cognome", FormField.Kind.Text, "Rossi", emptyList()),
            FormField("privacy", "Acconsento", FormField.Kind.Check, "", emptyList()),
        )
        assertEquals(mapOf("nome" to "Anna"), MyDataFill.fill(fields, studio))
    }
}
