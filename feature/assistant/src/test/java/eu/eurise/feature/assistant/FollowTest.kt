package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Seguiti (tavola AlfSeguito), scelte (tavola AlfQuale) e correzioni a mano. */
class FollowTest {
    private val now = LocalDateTime.of(2026, 11, 15, 18, 42)
    private fun f(s: String) = Grammar.follow(s, now)

    @Test fun sposta() {
        assertEquals(Command.Follow(FollowAction.Move, null, LocalTime.of(10, 0)), f("spostalo alle 10"))
        assertEquals(Command.Follow(FollowAction.Move, null, LocalTime.of(11, 0)), f("no, alle 11"))
        assertEquals(Command.Follow(FollowAction.Move, LocalDate.of(2026, 11, 16), null), f("rimandalo a domani"))
        assertEquals(LocalTime.of(9, 30), f("anzi alle 9 e mezza")?.time)
        assertEquals(LocalDate.of(2026, 11, 20), f("spostalo a venerdì alle 15")?.day)
    }

    @Test fun annullaEAncheA() {
        assertEquals(FollowAction.Undo, f("cancellalo")?.action)
        assertEquals(FollowAction.Undo, f("annulla")?.action)
        assertEquals(FollowAction.Undo, f("Alfred, lascia perdere")?.action)
        assertEquals(Command.Follow(FollowAction.AlsoTo, who = "Luca"), f("mandalo anche a Luca"))
        assertEquals("Luca", f("avvisa anche Luca")?.who ?: f("avvisa anche a Luca")?.who)
        assertEquals("Anna", f("e anche ad Anna")?.who)
    }

    @Test fun nonSonoSeguiti() {
        assertNull(f("domani alle 9 dentista"))
        assertNull(f("chiama Marco"))
        assertNull(f("cosa ho domani"))
        assertNull(f("metti la sveglia alle 7"))
    }

    private val marchi = listOf("Marco Ferri" to "lavoro · l'ultima volta 3 giorni fa", "Marco Rossi" to "calcetto", "Marco Bianchi" to "")

    @Test fun qualeMarco() {
        assertEquals(1, Grammar.pick("Rossi", marchi))
        assertEquals(0, Grammar.pick("il primo", marchi))
        assertEquals(2, Grammar.pick("il terzo", marchi))
        assertEquals(0, Grammar.pick("quello del lavoro", marchi))
        assertEquals(1, Grammar.pick("Marco del calcetto", marchi))
        assertEquals(2, Grammar.pick("l'ultimo", marchi))
        assertNull(Grammar.pick("Marco", marchi))
        assertNull(Grammar.pick("boh", marchi))
    }

    @Test fun correzioni() {
        assertEquals(listOf("Dentisti" to "Dentista"), Corrections.diff("Dentisti martedì", "Dentista martedì"))
        assertEquals(emptyList<Pair<String, String>>(), Corrections.diff("Cena", "Cena da Anna"))
        assertEquals(emptyList<Pair<String, String>>(), Corrections.diff("alle 9", "alle 10"))
        assertEquals("Chiama Ferri e poi il dentista", Corrections.apply("Chiama Ferry e poi il dentisti", mapOf("ferry" to "Ferri", "dentisti" to "dentista")))
    }
}
