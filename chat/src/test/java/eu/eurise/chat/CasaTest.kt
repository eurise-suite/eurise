package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CasaTest {
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun deadlines() {
        val luce = Casa.Deadline("a", "Bolletta luce", Casa.Kind.Bolletta, LocalDate.of(2026, 12, 5).toEpochDay(), 8430, "@anna:x", remindDays = 3)
        val rata = Casa.Deadline("b", "Rata mutuo", Casa.Kind.Rata, LocalDate.of(2026, 12, 1).toEpochDay(), 61200, monthly = true)
        val carta = Casa.Deadline("c", "Carta d'identità · Luca", Casa.Kind.Documento, LocalDate.of(2027, 1, 15).toEpochDay(), notice = 60)
        assertFalse(Casa.warning(luce, today))
        assertTrue(Casa.warning(luce, LocalDate.of(2026, 12, 2)))
        assertTrue(Casa.warning(carta, today)) // 60 giorni prima del 15 gennaio
        assertEquals(listOf("c", "b", "a"), Casa.sorted(listOf(luce, rata, carta), today).map { it.id })
        val next = Casa.paid(listOf(rata, luce), "b", 5)
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), next[0].due)
        assertEquals(0L, next[0].paidAt)
        assertEquals(5L, Casa.paid(listOf(luce), "a", 5)[0].paidAt)
        val all = listOf(luce, rata, carta)
        assertEquals(all, Casa.decodeDeadlines(Casa.encodeDeadlines(all)))
    }

    @Test fun menu() {
        val menu = listOfNotNull(Casa.parseDish(1, "pasta al pesto: basilico, pinoli"), Casa.parseDish(2, "Pollo al forno: pollo, patate, Basilico"), Casa.parseDish(3, ""))
        assertEquals(2, menu.size)
        assertEquals("Pasta al pesto", menu[0].name)
        val spesa = Elenchi.add(Elenchi.defaults(listOf("Spesa")), "l0", "Pinoli", "@me", 1)[0]
        assertEquals(listOf("basilico", "pollo", "patate"), Casa.missing(menu, spesa))
        assertEquals(menu, Casa.decodeMenu(Casa.encodeMenu(menu)))
    }

    @Test fun meds() {
        var m = Casa.Meds("Rosa", phone = "+39 333 1234567", meds = listOf(Casa.Med("m1", "Cardioaspirina", "08:00"), Casa.Med("m2", "Eutirox 50", "20:00")))
        m = Casa.take(m, "m1", today, 1000)
        assertEquals(listOf(1000L, null), Casa.today(m, today).map { it.second })
        assertEquals(m, Casa.decodeMeds(Casa.encodeMeds(m)))
        assertTrue(Casa.confirms("1"))
        assertTrue(Casa.confirms("Presa, grazie"))
        assertFalse(Casa.confirms("10 minuti"))
        assertFalse(Casa.confirms("non ancora"))
    }
}
