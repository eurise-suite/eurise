package eu.eurise.core.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MillewinTest {
    // Dati finti: il codice fiscale d'esempio di «Mario Rossi» e uno inventato per il medico.
    private val patientCf = "RSSMRA85T10A562S"
    private val doctorCf = "BNCGPP52A01A783M"

    /** Il testo come lo estrae core/pdf da un promemoria (righe e spazi come vengono). */
    private val memoText = """
        SERVIZIO SANITARIO NAZIONALE   RICETTA ELETTRONICA - PROMEMORIA PER L'ASSISTITO
        REGIONE CAMPANIA
        1500A 40123 45678
        CODICE FISCALE $patientCf
        COGNOME E NOME / INIZIALI DELL'ASSISTITO: ROSSI MARIO
        PRESCRIZIONE
        RAMIPRIL 5MG 28 CPR   QTA 1
        CARDIOASPIRIN 100MG 30 CPR GASTRORESISTENTI   QTA 1
        CODICE FISCALE DEL MEDICO: $doctorCf   DATA 17/11/2026
    """.trimIndent()

    @Test fun codice_fiscale() {
        assertTrue(Millewin.validFiscalCode(patientCf))
        assertTrue(Millewin.validFiscalCode(doctorCf))
        assertFalse(Millewin.validFiscalCode("RSSMRA85T10A562T"))
        assertEquals(patientCf, Millewin.fiscalCode(" rssmra85t10a562s "))
        assertNull(Millewin.fiscalCode("RSSMRA85T10A562"))
        assertNull(Millewin.fiscalCode("RSSMRA85T10A562X"))
    }

    @Test fun legge_il_promemoria() {
        val m = Millewin.parse(memoText)!!
        assertEquals("1500A4012345678", m.nre)
        assertEquals(listOf(patientCf, doctorCf), m.fiscalCodes)
        assertNull(Millewin.parse("Un PDF qualsiasi senza codici"))
    }

    @Test fun nre_intero() {
        assertEquals("1500A4012345678", Millewin.parse("NRE: 1500A4012345678 data 17/11/2026")!!.nre)
    }

    @Test fun abbina_paziente_e_farmaci() {
        val m = Millewin.parse(memoText)!!
        assertTrue(Millewin.forPatient(m, patientCf, "qualsiasi"))
        assertFalse(Millewin.forPatient(m, "VRDLGU60M41F839J", "Rossi Mario"))
        // Senza codice fiscale nella scheda: il nome intero, in qualsiasi ordine.
        assertTrue(Millewin.forPatient(m, null, "Mario Rossi"))
        assertFalse(Millewin.forPatient(m, null, "Mario Bianchi"))
        assertFalse(Millewin.forPatient(m, null, "Rossi"))
        assertEquals(2, Millewin.drugsFound(m, listOf("Ramipril 5 mg", "Cardioaspirin", "Metformina 500")))
    }

    @Test fun sceglie_il_promemoria_giusto() {
        val a = Millewin.parse(memoText)!!
        val b = Millewin.parse(memoText.replace("1500A 40123 45678", "1500A 49999 00000").replace("RAMIPRIL", "METFORMINA"))!!
        assertEquals(b.nre, Millewin.best(listOf(a, b), patientCf, "", listOf("Metformina 500"))!!.nre)
        assertEquals(a.nre, Millewin.best(listOf(a, b), patientCf, "", listOf("Ramipril"))!!.nre)
        assertNull(Millewin.best(listOf(a, b), "VRDLGU60M41F839J", "", listOf("Ramipril")))
    }
}
