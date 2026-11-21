package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Le frasi della tavola AlfAzioni (passo 3), più quelle vicine che devono restare com'erano. */
class ActionsTest {
    /** Giovedì 12 novembre 2026, 10:15, come nelle tavole. */
    private val now = LocalDateTime.of(2026, 11, 12, 10, 15)
    private fun p(s: String) = Grammar.parse(s, now)
    private fun dev(s: String) = (p(s) as? Command.Device)?.action

    @Test fun impegniPerNome() {
        val move = p("sposta la riunione a giovedì") as Command.Change
        assertEquals(false, move.delete)
        assertEquals("riunione", move.about)
        assertNotNull(move.day)
        val at = p("Alfred, sposta il dentista alle 11") as Command.Change
        assertEquals("dentista", at.about)
        assertEquals(LocalTime.of(11, 0), at.time)
        val del = p("cancella il dentista") as Command.Change
        assertEquals(true, del.delete)
        assertEquals("dentista", del.about)
        val withDay = p("cancella l'appuntamento con Anna di domani") as Command.Change
        assertEquals("Anna", withDay.about)
        assertEquals(LocalDate.of(2026, 11, 13), withDay.day)
    }

    @Test fun coseFatte() {
        assertEquals(Command.Complete("latte"), p("ho comprato il latte"))
        assertEquals(Command.Complete("bollo dell'auto"), p("ho pagato il bollo dell'auto"))
        assertEquals(Command.Complete("latte"), p("spunta il latte"))
        // «cosa devo fare oggi?» resta la domanda sui dati.
        assertTrue(p("cosa devo fare oggi?") is Command.Ask)
    }

    @Test fun sveglieETimer() {
        assertEquals(Command.Clock(ClockAction.StopTimer), p("ferma il timer"))
        assertEquals(Command.Clock(ClockAction.StopTimer), p("Alfred, spegni il timer"))
        assertEquals(Command.Clock(ClockAction.TimerLeft), p("quanto manca al timer?"))
        val off = p("spegni la sveglia di domani") as Command.Clock
        assertEquals(ClockAction.AlarmOff, off.action)
        assertEquals(LocalDate.of(2026, 11, 13), off.day)
        assertEquals(Command.Clock(ClockAction.AlarmOff), p("salta la sveglia"))
        // Mettere sveglie e timer resta com'era.
        assertEquals(Command.Timer(10), p("timer 10 minuti"))
        assertTrue(p("sveglia alle 7") is Command.Alarm)
        // «quanto manca?» da solo non è il timer (è la domanda in guida).
        assertTrue(p("quanto manca?") is Command.Guide)
    }

    @Test fun telefono() {
        assertEquals(DeviceAction.TorchOn, dev("accendi la torcia"))
        assertEquals(DeviceAction.TorchOn, dev("torcia"))
        assertEquals(DeviceAction.TorchOff, dev("spegni la torcia"))
        assertEquals(DeviceAction.QuietOn, dev("non disturbare"))
        assertEquals(LocalTime.of(8, 0), (p("non disturbare fino alle 8") as Command.Device).until)
        assertEquals(60, (p("metti il non disturbare per un'ora") as Command.Device).minutes)
        assertEquals(DeviceAction.QuietOff, dev("togli il non disturbare"))
        assertEquals(DeviceAction.VolumeMin, dev("volume al minimo"))
        assertEquals(DeviceAction.VolumeMax, dev("metti il volume al massimo"))
        assertEquals(DeviceAction.VolumeUp, dev("alza il volume"))
        assertEquals(DeviceAction.VolumeDown, dev("abbassa un po' il volume"))
        assertEquals(DeviceAction.Silent, dev("metti il telefono in silenzioso"))
        assertEquals(DeviceAction.Vibrate, dev("vibrazione"))
        assertEquals(DeviceAction.Sound, dev("togli il silenzioso"))
        val mode = p("modalità Notte") as Command.Device
        assertEquals(DeviceAction.ModeOn, mode.action)
        assertEquals("notte", mode.mode)
        assertEquals(DeviceAction.ModeOff, dev("spegni la modalità lavoro"))
    }

    @Test fun musica() {
        assertEquals(DeviceAction.Play, dev("metti musica"))
        assertEquals(DeviceAction.Play, dev("metti un po' di musica"))
        assertEquals(DeviceAction.Pause, dev("pausa"))
        assertEquals(DeviceAction.Next, dev("avanti"))
        assertEquals(DeviceAction.Next, dev("prossima canzone"))
        assertEquals(DeviceAction.Previous, dev("canzone precedente"))
        assertEquals(DeviceAction.VolumeUp, dev("più forte"))
    }

    @Test fun messaggi() {
        assertEquals(Command.Ask(AskKind.Messages), p("leggimi i messaggi"))
        assertEquals(Command.Ask(AskKind.Messages), p("chi mi ha scritto?"))
        assertEquals(Command.Ask(AskKind.Messages), p("ho messaggi nuovi?"))
        assertEquals("Anna", (p("cosa mi ha scritto Anna?") as Command.Ask).about)
        assertEquals(Command.Reply("Arrivo"), p("rispondi che arrivo"))
        val to = p("rispondi a Marco che arrivo tra dieci minuti") as Command.Message
        assertEquals("Marco", to.who)
        assertEquals("Arrivo tra dieci minuti", to.text)
        // Le domande vicine restano com'erano.
        assertEquals(AskKind.Calls, (p("chi mi ha chiamato?") as Command.Ask).kind)
        assertTrue(p("scrivi ad Anna che arrivo") is Command.Message)
    }

    @Test fun letturaDeiMessaggi() {
        val now = LocalDateTime.of(2026, 11, 12, 10, 15)
        val a = DataWriter.messages(Command.Ask(AskKind.Messages), now,
            listOf(DataMessage("Anna Rossi", "+39333", now.minusMinutes(5), listOf("Arrivo alle 8"), 1)), available = true, unreadTotal = 1)
        assertEquals("Un messaggio nuovo da Anna.", a.lead)
        assertEquals("Anna ha scritto: Arrivo alle 8.", a.spoken)
        assertEquals(listOf("Rispondi ad Anna"), a.chips.map { it.label })
        assertEquals("Per leggere i messaggi mi serve Eurise Messaggi.",
            DataWriter.messages(Command.Ask(AskKind.Messages), now, emptyList(), available = false).lead)
    }

    @Test fun inGuida() {
        assertEquals(Command.Guide(), p("quanto manca?"))
        assertEquals(Command.Guide(), p("a che ora arrivo"))
        assertEquals(Command.Guide("Anna"), p("avvisa Anna che arrivo"))
        assertEquals(Command.Navigate("benzinaio"), p("dov'è il benzinaio più vicino?"))
        assertEquals(Command.Navigate("farmacia"), p("c'è una farmacia qui vicino?"))
        assertEquals(Command.Clock(ClockAction.TimerLeft), p("quanto manca al timer"))
    }

    @Test fun cosaSaiFare() {
        assertEquals(Command.Help, p("Cosa sai fare?"))
        assertEquals(Command.Help, p("Alfred, che cosa sai fare"))
    }

    @Test fun restanoComErano() {
        // Seguiti: «cancellalo», «annulla» restano dei seguiti (li prende Grammar.follow), non nomi di impegni.
        assertTrue(p("annulla") !is Command.Change)
        assertTrue(p("cancella la sveglia") is Command.Clock)
        assertTrue(p("cancella il timer") is Command.Clock)
        assertTrue(p("metti la sveglia alle 7") is Command.Alarm)
        assertTrue(p("chiama Marco") is Command.Call)
        assertTrue(p("Metti nel calendario cena da Anna venerdì alle 20") is Command.Event)
        assertTrue(p("portami a casa") is Command.Navigate)
    }
}

class AlfredLogTest {
    @Test fun testoDaMandare() {
        val e = listOf(
            AlfredLog.Entry(2, "chiamo Marco", AlfredLog.Result.Done, "Chiamata · Marco Ferri"),
            AlfredLog.Entry(1, "scrivi alla mamma che arrivo", AlfredLog.Result.Cancelled, "Messaggio · Mamma"),
        )
        assertEquals("«chiamo Marco» → fatta · Chiamata · Marco Ferri\n«scrivi alla mamma che arrivo» → annullata · Messaggio · Mamma", AlfredLog.text(e))
    }
}
