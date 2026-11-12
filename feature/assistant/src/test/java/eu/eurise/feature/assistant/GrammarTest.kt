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
}
