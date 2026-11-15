package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BrainCommandsTest {
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0)

    @Test fun piuAzioni() {
        val c = BrainCommands.parse("evento||Cena da Giulia|2026-11-13|20:00|\nmessaggio|Anna|Venerdì alle 20 ceno da Giulia|||telegram", now)
        assertEquals(Command.Event("Cena da Giulia", null, LocalDate.of(2026, 11, 13), LocalTime.of(20, 0)), c[0])
        assertEquals(Command.Message("Anna", "Venerdì alle 20 ceno da Giulia", Channel.Telegram), c[1])
    }

    @Test fun chiamataTimerDomanda() {
        assertEquals(listOf(Command.Call("Marco")), BrainCommands.parse("chiama|Marco||||", now))
        assertEquals(listOf(Command.Timer(12)), BrainCommands.parse("timer||12|||", now))
        assertEquals(listOf(Command.Free("Quanto è alto il Monte Bianco?")), BrainCommands.parse("domanda||Quanto è alto il Monte Bianco?|||", now))
    }

    @Test fun promemoriaConOraSenzaData() {
        assertEquals(listOf(Command.Task("Chiamare il commercialista", LocalDate.of(2026, 11, 11), LocalTime.of(14, 0))),
            BrainCommands.parse("promemoria||chiamare il commercialista||14:00|", now))
    }

    @Test fun righeIncomplete() {
        assertEquals(emptyList<Command>(), BrainCommands.parse("chiama|||||\nsveglia||||\nqualcosa", now))
    }

    // --- Controlli dopo il modello (errori veri delle misure del 15 novembre) ---

    @Test fun notaInventataSiSvuota() {
        assertEquals(listOf(Command.Note("")),
            BrainCommands.parse("nota||Ricordo di controllare le chiavi del garage prima di uscire per la serata.|||", now, "scrivi una nota"))
    }

    @Test fun dataEOraDallaFrase() {
        // Granite: «martedì» → 23 invece di 17; l'ora inventata su una chiamata sparisce.
        val c = BrainCommands.parse("evento||Dentista|2026-11-23|09:00|", now, "aggiungi al calendario dentista martedì alle 9")
        assertEquals(Command.Event("Dentista", null, LocalDate.of(2026, 11, 17), LocalTime.of(9, 0)), c[0])
        assertEquals(listOf(Command.Call("Marco")), BrainCommands.parse("chiama|Marco|||11:00|telegram", now, "chiama Marco"))
    }

    @Test fun azioneInPiuSenzaCongiunzione() {
        val c = BrainCommands.parse("evento||Dentista|2026-11-17|09:00|\nmessaggio|Eurise|Mi aggiungi al calendario|||", now, "dentista martedì alle 9")
        assertEquals(1, c.size)
    }

    @Test fun nomeInventatoSiToglie() {
        assertEquals(emptyList<Command>(), BrainCommands.parse("chiama|Eurise||||", now, "chiama"))
    }

    @Test fun domandaComeDetta() {
        assertEquals(listOf(Command.Free("")), BrainCommands.parse("domanda||Quanto deve cuocere un uovo sodo per essere perfetto?|||", now, "quanto deve cuocere un uovo sodo"))
    }
}
