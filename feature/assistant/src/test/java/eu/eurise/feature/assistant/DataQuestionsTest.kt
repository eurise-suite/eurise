package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Le domande sui dati (tavola AlfDati): la grammatica e le frasi di Alfred. */
class DataQuestionsTest {
    /** Domenica 15 novembre 2026, 18:42, come nella tavola. */
    private val now = LocalDateTime.of(2026, 11, 15, 18, 42)
    private fun p(s: String) = Grammar.parse(s, now)
    private fun ask(s: String) = p(s) as Command.Ask

    @Test fun cosaHoDomani() {
        val a = ask("Cosa ho domani?")
        assertEquals(AskKind.Agenda, a.kind)
        assertEquals(LocalDate.of(2026, 11, 16), a.day)
        assertEquals(LocalDate.of(2026, 11, 15), ask("Alfred, che impegni ho oggi").day)
        assertEquals(LocalDate.of(2026, 11, 15), ask("cosa ho").day)
        assertEquals(LocalDate.of(2026, 11, 20), ask("ho qualcosa venerdì?").day)
        assertEquals(AskKind.Agenda, ask("cos'ho dopodomani").kind)
        assertEquals(AskKind.Agenda, ask("com'è la mia giornata domani").kind)
    }

    @Test fun settimana() {
        val a = ask("cosa ho questa settimana")
        assertEquals(LocalDate.of(2026, 11, 15), a.day)
        assertEquals(1, a.days) // domenica: resta solo oggi
        val b = ask("che impegni ho la settimana prossima")
        assertEquals(LocalDate.of(2026, 11, 16), b.day)
        assertEquals(7, b.days)
    }

    @Test fun quando() {
        val a = ask("quando ho il dentista?")
        assertEquals(AskKind.When, a.kind)
        assertEquals("Dentista", a.about)
        assertEquals("Pranzo con Anna", ask("a che ora ho il pranzo con Anna").about)
        assertEquals("Riunione", ask("quando è la riunione").about)
    }

    @Test fun chiamate() {
        assertEquals(AskKind.Calls, ask("chi mi ha chiamato?").kind)
        assertEquals(null, ask("chi mi ha chiamato").day)
        assertEquals(LocalDate.of(2026, 11, 15), ask("chi mi ha chiamato oggi").day)
        assertEquals(AskKind.Calls, ask("ho chiamate perse").kind)
    }

    @Test fun note() {
        val a = ask("cosa avevo scritto sul cancello?")
        assertEquals(AskKind.Notes, a.kind)
        assertEquals("cancello", a.about)
        assertEquals("codice del cancello", ask("qual è il codice del cancello").about)
    }

    @Test fun nonSonoDomandeSuiDati() {
        assertFalse(p("cosa ho fatto di male") is Command.Ask)
        assertFalse(p("cosa c'è da vedere a Roma") is Command.Ask)
        assertTrue(p("chiama Marco") is Command.Call)
        assertTrue(p("scrivi una nota il codice è 4512") is Command.Note)
    }

    // --- Le frasi ---

    private fun ev(h: Int, title: String, place: String = "", day: Int = 16) =
        DataEvent(title, LocalDateTime.of(2026, 11, day, h, 0), false, place, "Casa", 0xFF3E5C8A.toInt())

    @Test fun rispostaDellaTavola() {
        val a = DataWriter.agenda(
            ask("cosa ho domani"), now,
            listOf(ev(9, "Dentista", "Studio Bianchi, via Roma 12"), ev(13, "Pranzo con Anna", "Trattoria da Gino"), ev(18, "Riunione di condominio")),
            listOf(DataTask("Comprare il latte", LocalDate.of(2026, 11, 16), "Spesa")),
        )
        assertEquals("Domani ha tre impegni e una cosa da fare.", a.lead)
        assertEquals("9:00 · Dentista", a.rows[0].title)
        assertEquals("in calendario Casa", a.rows[2].detail)
        assertEquals("Da fare · Comprare il latte", a.rows[3].title)
        assertEquals("nell'elenco Spesa", a.rows[3].detail)
        assertTrue(a.spoken.contains("Alle 9 Dentista, alle 13 Pranzo con Anna"))
        assertEquals(listOf("Strada per Studio Bianchi", "Avvisa Anna"), a.chips.map { it.label })
    }

    @Test fun oggiRestano() {
        val a = DataWriter.agenda(ask("cosa ho oggi"), now, listOf(ev(9, "Palestra", day = 15), ev(20, "Cena", day = 15)), emptyList())
        assertEquals("Oggi le resta un impegno.", a.lead)
        assertEquals(1, a.rows.size)
        assertEquals("Domani l'agenda è libera.", DataWriter.agenda(ask("cosa ho domani"), now, emptyList(), emptyList()).lead)
    }

    @Test fun quandoRisposta() {
        val a = DataWriter.whenIs(ask("quando ho il dentista"), now, listOf(ev(9, "Dentista", "Studio Bianchi, via Roma 12", day = 18)), emptyList())
        assertEquals("Il dentista è mercoledì 18 alle 9.", a.lead)
        assertEquals(listOf("dentista"), DataWriter.keywords("Appuntamento dal dentista"))
        assertEquals("Non trovo «Pasqua» né in calendario né tra le cose da fare.",
            DataWriter.whenIs(ask("quando è Pasqua"), now, emptyList(), emptyList()).lead)
        assertEquals("la riunione", DataWriter.article("riunione"))
        assertEquals("l'aperitivo", DataWriter.article("aperitivo"))
        assertEquals("lo psicologo", DataWriter.article("psicologo"))
    }

    @Test fun chiamateRisposta() {
        val calls = listOf(
            DataCall("Marco Ferri", "3331234567", LocalDateTime.of(2026, 11, 15, 15, 20), missed = true),
            DataCall("", "0612345678", LocalDateTime.of(2026, 11, 15, 11, 0), missed = false),
        )
        val a = DataWriter.calls(ask("chi mi ha chiamato"), now, calls)
        assertEquals("Nelle ultime ventiquattro ore l'hanno chiamata in due, una chiamata persa.", a.lead)
        assertEquals("Richiama Marco", a.chips.first().label)
        assertEquals("Oggi non l'ha chiamata nessuno.", DataWriter.calls(ask("chi mi ha chiamato oggi"), now, emptyList()).lead)
    }

    @Test fun noteRisposta() {
        val body = "Cose di casa. Il codice del cancello è 4512, quello del garage 1290. Chiamare l'idraulico."
        assertEquals("Il codice del cancello è 4512, quello del garage 1290.", DataWriter.snippet(body, listOf("cancello")))
        val a = DataWriter.notes(ask("cosa avevo scritto sul cancello"), listOf(DataNote(3, "Casa", "Il codice del cancello è 4512.")), emptyList())
        assertEquals("Ho trovato una nota.", a.lead)
        assertEquals("note:3", a.open)
        assertEquals(listOf("codice", "cancello"), DataWriter.keywords("codice del cancello"))
    }
}
