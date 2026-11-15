package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GrammarTest {
    /** Giovedì 12 novembre 2026, 10:15, come nelle tavole. */
    private val now = LocalDateTime.of(2026, 11, 12, 10, 15)
    private fun p(s: String) = Grammar.parse(s, now)

    @Test fun appuntamentoDellaTavola() {
        val c = p("Appuntamento giovedì alle 10 con Marco dal commercialista") as Command.Event
        assertEquals("Dal commercialista", c.title)
        assertEquals("Marco", c.who)
        assertEquals(LocalDate.of(2026, 11, 19), c.day)
        assertEquals(LocalTime.of(10, 0), c.time)
    }

    @Test fun segnalaInGuida() {
        assertEquals(ReportKind.CAMERA, (p("Alfred, autovelox") as Command.Report).kind)
        assertEquals(ReportKind.QUEUE, (p("c'è una coda") as Command.Report).kind)
        assertEquals(ReportKind.LIMIT, (p("segnala limite sbagliato") as Command.Report).kind)
        assertEquals(ReportKind.CLOSED, (p("strada chiusa") as Command.Report).kind)
        assertEquals(ReportKind.WORKS, (p("lavori in corso") as Command.Report).kind)
        assertEquals(ReportKind.DANGER, (p("Alfred, pericolo!") as Command.Report).kind)
        assertTrue(p("coda alla posta alle 9") !is Command.Report)
    }

    @Test fun cenaTieneLaParola() {
        val c = p("cena venerdì alle 20 da Anna") as Command.Event
        assertEquals("Cena da Anna", c.title)
    }

    @Test fun senzaVerboConGiornoEOra() {
        val c = p("dentista martedì alle 9") as Command.Event
        assertEquals("Dentista", c.title)
        assertEquals(LocalTime.of(9, 0), c.time)
    }

    @Test fun messaggioWhatsapp() {
        val c = p("Manda a Marco su WhatsApp che arrivo tra dieci minuti") as Command.Message
        assertEquals("Marco", c.who)
        assertEquals("Arrivo tra dieci minuti", c.text)
        assertEquals(Channel.WhatsApp, c.channel)
    }

    @Test fun messaggioSenzaCanale() {
        val c = p("manda a Marco Bianchi che arrivo") as Command.Message
        assertEquals("Marco Bianchi", c.who)
        assertEquals(null, c.channel)
    }

    @Test fun chiamaConCortesia() {
        assertEquals(Command.Call("Anna"), p("Alfred, chiama Anna per favore"))
    }

    @Test fun nota() {
        assertEquals(Command.Note("Comprare il latte"), p("nota: comprare il latte"))
    }

    @Test fun portami() {
        assertEquals(Command.Navigate("Apice"), p("Portami ad Apice"))
        assertEquals(Command.Navigate("casa"), p("portami a casa"))
    }

    @Test fun sveglia() {
        assertEquals(Command.Alarm(LocalTime.of(7, 0), null), p("sveglia alle 7"))
        assertEquals(Command.Alarm(LocalTime.of(6, 30), null), p("svegliami 6.30"))
        val d = p("sveglia domani alle 7") as Command.Alarm
        assertEquals(LocalDate.of(2026, 11, 13), d.day)
    }

    @Test fun timer() {
        assertEquals(Command.Timer(10), p("timer di 10 minuti"))
        assertEquals(Command.Timer(30), p("metti un timer di mezz'ora"))
    }

    @Test fun ricordami() {
        val c = p("ricordami di pagare il bollo domani") as Command.Task
        assertEquals("Pagare il bollo", c.title)
        assertEquals(LocalDate.of(2026, 11, 13), c.due)
    }

    @Test fun cercaEApri() {
        assertEquals(Command.Search("contratto affitto", Scope.Suite), p("cerca nei documenti contratto affitto"))
        assertEquals(Command.Search("orari farmacia", Scope.Web), p("cerca orari farmacia"))
        assertEquals(Command.Open("Meteo"), p("apri Meteo"))
    }

    @Test fun letteraERichiestaLibera() {
        assertTrue(p("Scrivi una lettera all'amministratore per la perdita d'acqua dal piano di sopra") is Command.Free)
    }

    @Test fun rispostaAllaScheda() {
        assertEquals(Grammar.Answer.Yes, Grammar.answer("Sì, procedi."))
        assertEquals(Grammar.Answer.Yes, Grammar.answer("Alfred, va bene"))
        assertEquals(Grammar.Answer.Yes, Grammar.answer("Ok"))
        assertEquals(Grammar.Answer.No, Grammar.answer("No, lascia stare."))
        assertEquals(Grammar.Answer.No, Grammar.answer("annulla"))
        assertEquals(Grammar.Answer.Other, Grammar.answer("alle dieci invece"))
        assertEquals(Grammar.Answer.Other, Grammar.answer("Sicuramente Marco"))
    }

    // --- Frasi come le scrive Whisper (prova del proprietario, 15 novembre: «capisce una cosa per un'altra») ---

    @Test fun aggiungiAlCalendario() {
        val c = p("Aggiungi al calendario dentista martedì alle 9.") as Command.Event
        assertEquals("Dentista", c.title)
        assertEquals(LocalTime.of(9, 0), c.time)
    }

    @Test fun creaUnEvento() {
        val c = p("Alfred, crea un evento domani alle 15 riunione con Luca") as Command.Event
        assertEquals(LocalDate.of(2026, 11, 13), c.day)
        assertEquals(LocalTime.of(15, 0), c.time)
    }

    @Test fun mettiNelCalendario() {
        assertTrue(p("Metti nel calendario cena da Anna venerdì alle 20") is Command.Event)
    }

    @Test fun notaSenzaTesto() {
        assertEquals(Command.Note(""), p("Scrivi una nota."))
        assertEquals(Command.Note("Comprare il latte"), p("Nuova nota: comprare il latte"))
    }

    @Test fun chiamataScrittaMale() {
        assertEquals(Command.Call("Marco"), p("Chiamo Marco."))
        assertEquals(Command.Call("Laura"), p("Fai una telefonata a Laura"))
    }

    @Test fun messaggioWhatsappDetto() {
        val c = p("Manda un WhatsApp a Luca che arrivo tra poco") as Command.Message
        assertEquals("Luca", c.who)
        assertEquals("Arrivo tra poco", c.text)
        assertEquals(Channel.WhatsApp, c.channel)
    }

    @Test fun promemoriaConDomanda() {
        val c = p("Mi ricordi di comprare il pane domani?") as Command.Task
        assertEquals(LocalDate.of(2026, 11, 13), c.due)
    }

    @Test fun leDomandeRestanoLibere() {
        assertTrue(p("Quanto deve cuocere un uovo sodo?") is Command.Free)
        assertTrue(p("Quando ho l'appuntamento dal dentista?") is Command.Free)
    }

    @Test fun ricordaChe() {
        assertEquals(Command.Remember("Dopo pranzo sono le 13"), p("Alfred, ricorda che dopo pranzo sono le 13"))
        assertTrue(p("ricordami di comprare il pane") is Command.Task)
    }
}
