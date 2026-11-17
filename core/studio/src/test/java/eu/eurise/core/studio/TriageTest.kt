package eu.eurise.core.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class TriageTest {
    @Test fun urgenze() {
        assertEquals(Need.Urgent, Triage.need("Dottore ho un dolore al petto da stamattina"))
        assertEquals(Need.Urgent, Triage.need("mio padre è svenuto in cucina"))
        assertEquals(Need.Urgent, Triage.need("Non riesco a respirare bene"))
        assertEquals(Need.Urgent, Triage.need("Ha la BOCCA STORTA e parla male"))
    }

    @Test fun nonUrgenze() {
        assertEquals(Need.Report, Triage.need("Sono pronti gli esami del sangue?"))
        assertEquals(Need.Question, Triage.need("Mi serve la tachipirina, va bene?"))
        assertEquals(Need.Question, Triage.need("Posso prendere l'ibuprofene a stomaco vuoto?"))
    }

    @Test fun ricette() {
        assertEquals(Need.Prescription, Triage.need("Buongiorno, mi può fare la ricetta del solito?"))
        assertEquals(Need.Prescription, Triage.need("ho finito le compresse per la pressione"))
        assertEquals(Need.Prescription, Triage.need("Mi servirebbe il Ramipril", listOf("Ramipril 5 mg")))
    }

    @Test fun appuntamenti() {
        assertEquals(Need.Appointment, Triage.need("Posso venire giovedì per una visita?"))
        assertEquals(Need.Appointment, Triage.need("Devo spostare la seduta di venerdì"))
    }

    @Test fun refertiEGrazie() {
        assertEquals(Need.Report, Triage.need("Le mando il referto dell'ECG"))
        assertEquals(Need.Thanks, Triage.need("Grazie mille dottore!"))
        assertEquals(Need.Question, Triage.need("Grazie, ma devo continuare anche domenica?"))
    }

    @Test fun parteDelloStudio() {
        val both = setOf(Practice.General, Practice.Acupuncture)
        assertEquals(Practice.Acupuncture, Triage.practice("Spostiamo la seduta di agopuntura?", both))
        assertEquals(Practice.General, Triage.practice("La ricetta per favore", both))
        assertEquals(Practice.Acupuncture, Triage.practice("La ricetta per favore", setOf(Practice.Acupuncture)))
        assertEquals(Practice.General, Triage.practice("Ho dolore al braccio dopo il vaccino", both))
    }
}
