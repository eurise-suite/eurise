package eu.eurise.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class CaptureTest {
    // Lunedì 9 novembre 2026, sera.
    private val now = LocalDateTime.of(2026, 11, 9, 21, 4)

    @Test fun `giorno e ora diventano un evento con la persona nel titolo`() {
        val r = Capture.parse("Cena da Marco venerdì alle 20 con @Anna", now)
        assertEquals(Capture.Target.Event, r.suggested)
        assertEquals("Cena da Marco con Anna", r.title)
        assertEquals(listOf("Anna"), r.mentions.map { it.name })
    }

    @Test fun `un giorno senza ora è un'attività`() =
        assertEquals(Capture.Target.Task, Capture.parse("Pagare il bollo domani", now).suggested)

    @Test fun `una scadenza con ora resta un'attività`() =
        assertEquals(Capture.Target.Task, Capture.parse("Consegnare i documenti entro venerdì alle 18", now).suggested)

    @Test fun `una ripetizione è un'attività anche con l'ora`() =
        assertEquals(Capture.Target.Task, Capture.parse("Annaffiare ogni lunedì alle 8", now).suggested)

    @Test fun `una frase lunga senza date è una nota`() =
        assertEquals(Capture.Target.Note, Capture.parse("Il libro che mi ha consigliato Luca parla di viaggi in treno", now).suggested)

    @Test fun `una frase breve senza date è un'attività`() =
        assertEquals(Capture.Target.Task, Capture.parse("Comprare il latte", now).suggested)

    @Test fun `un indirizzo email non è una persona`() =
        assertEquals(emptyList<Capture.Mention>(), Capture.parse("Scrivere a anna@esempio.it", now).mentions)

    @Test fun `la nota conserva giorno e ora`() =
        assertEquals("Vedere Anna venerdì alle 20", Capture.parse("Vedere @Anna venerdì alle 20", now).noteText)

    @Test fun `dal nome si ricava la parte da citare`() {
        assertEquals("Anna", Capture.mentionToken("Anna Maria Rossi"))
        assertEquals("", Capture.mentionToken("3B Idraulica"))
    }
}
