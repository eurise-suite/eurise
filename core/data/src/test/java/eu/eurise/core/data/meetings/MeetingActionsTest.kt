package eu.eurise.core.data.meetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MeetingActionsTest {
    // Martedì 10 novembre 2026, pomeriggio.
    private val now = LocalDateTime.of(2026, 11, 10, 15, 30)

    @Test fun `una riga con persona e data diventa un'azione`() {
        val a = MeetingActions.extract("- @Anna manda il preventivo entro venerdì", now).single()
        assertEquals(listOf("Anna"), a.people)
        assertEquals(LocalDate.of(2026, 11, 13), a.due)
        assertTrue(a.title.startsWith("Anna manda il preventivo"))
        assertEquals(0, a.line)
    }

    @Test fun `senza data o senza persona resta un appunto`() {
        val text = "Il cliente vuole più tempo\n@Marco ne parla con il fornitore\nRivedere il budget domani"
        assertEquals(emptyList<MeetingActions.Action>(), MeetingActions.extract(text, now))
    }

    @Test fun `la riga giusta viene ricordata`() {
        val text = "Punto aperto sui costi\n\n2) @Luca chiama la banca domani alle 10"
        val a = MeetingActions.extract(text, now).single()
        assertEquals(2, a.line)
        assertEquals(LocalDate.of(2026, 11, 11), a.due)
        assertEquals(10, a.time?.hour)
    }

    @Test fun `un indirizzo email non è una persona`() =
        assertEquals(emptyList<MeetingActions.Action>(), MeetingActions.extract("Scrivere a anna@esempio.it domani", now))
}
