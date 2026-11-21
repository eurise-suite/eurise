package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class FaccendeTest {
    private val toMs: (LocalDateTime) -> Long = { it.toInstant(ZoneOffset.UTC).toEpochMilli() }
    // Lunedì 16 novembre 2026.
    private val monday = LocalDate.of(2026, 11, 16)
    private val trash = Faccende.Chore("c1", "Spazzatura", setOf(1, 3, 5), 0, 0, "21:00", listOf("@luca", "@sofia", "@anna"))

    @Test fun rotationAndStatus() {
        assertEquals("@luca", Faccende.whose(trash))
        assertEquals("@sofia", Faccende.next(trash))
        val now = monday.atTime(18, 0)
        assertEquals(Faccende.Status.Todo, Faccende.status(trash, now, toMs))
        val d = Faccende.done(trash, "@luca", toMs(monday.atTime(20, 50)))
        assertEquals("@sofia", Faccende.whose(d))
        assertEquals(Faccende.Status.Done, Faccende.status(d, monday.atTime(21, 0), toMs))
        // Martedì non tocca: libera; ma lunedì non fatta → in ritardo.
        assertEquals(Faccende.Status.Late, Faccende.status(trash, monday.plusDays(1).atTime(9, 0), toMs))
        assertEquals(Faccende.Status.Done, Faccende.status(d, monday.plusDays(1).atTime(9, 0), toMs))
        // Tre giri tornano al primo.
        assertEquals("@luca", Faccende.whose(Faccende.done(Faccende.done(d, "x", 0), "x", 0)))
    }

    @Test fun everyNDays() {
        val dish = Faccende.Chore("c2", "Lavastoviglie", emptySet(), 2, monday.toEpochDay(), "20:00", listOf("@sofia", "@luca"))
        assertEquals(true, Faccende.dueOn(dish, monday))
        assertEquals(false, Faccende.dueOn(dish, monday.plusDays(1)))
        assertEquals(true, Faccende.dueOn(dish, monday.plusDays(2)))
        assertEquals(monday, Faccende.lastDue(dish, monday.plusDays(1)))
    }

    @Test fun reminder() {
        val r = Faccende.nextReminder(listOf(trash), "@luca", monday.atTime(18, 0), toMs)!!
        assertEquals(monday.atTime(20, 30), r.second)
        // Dopo le 20:30 di lunedì: il prossimo è mercoledì.
        assertEquals(monday.plusDays(2).atTime(20, 30), Faccende.nextReminder(listOf(trash), "@luca", monday.atTime(20, 45), toMs)!!.second)
        assertNull(Faccende.nextReminder(listOf(trash), "@anna", monday.atTime(18, 0), toMs))
    }

    @Test fun roundTrip() {
        val list = listOf(trash, trash.copy(id = "c3", days = emptySet(), every = 2))
        assertEquals(list, Faccende.decode(Faccende.encode(list)))
    }
}
