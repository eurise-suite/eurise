package eu.eurise.feature.notes.meeting

import eu.eurise.feature.notes.doc.NoteHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingDocTest {
    private var next = 0L
    private val ids = { ++next }
    private val labels = MeetingLabels("Presenti", "Assenti", "Ordine del giorno", "Appunti", "Da fare")

    private val content = MeetingContent(
        intro = "Martedì 10 novembre · 15:00–16:00 · Sala Verde",
        people = listOf(MeetingPerson("Anna Rossi", true), MeetingPerson("Marco", true), MeetingPerson("Luca", false)),
        agenda = listOf(AgendaItem("Budget 2027", true), AgendaItem("Fornitori", false)),
        notes = "Il cliente chiede più tempo\n@Anna manda il preventivo entro venerdì",
        actions = listOf(MeetingAction("Anna manda il preventivo", 42, false)),
    )

    @Test fun `scritta e riletta la riunione non cambia`() =
        assertEquals(content, MeetingDoc.read(MeetingDoc.write(content, labels, ids), labels))

    @Test fun `passa anche dall'HTML delle note`() {
        val html = NoteHtml.write(MeetingDoc.write(content, labels, ids))
        assertEquals(content, MeetingDoc.read(NoteHtml.read(html, ids), labels))
    }

    @Test fun `una nota vuota dà una riunione vuota`() =
        assertEquals(MeetingContent(), MeetingDoc.read(emptyList(), labels))

    @Test fun `il riassunto non mostra gli assenti`() {
        val text = MeetingDoc.summary("Avanzamento lavori", content, labels)
        assertTrue(text.startsWith("Avanzamento lavori"))
        assertTrue("Presenti: Anna Rossi, Marco" in text)
        assertTrue("Luca" !in text)
        assertTrue("✓ Budget 2027" in text)
    }
}
