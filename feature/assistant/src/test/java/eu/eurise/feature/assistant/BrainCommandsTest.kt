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
}
