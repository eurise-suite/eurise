package eu.eurise.chat

import eu.eurise.chat.Expenses.Expense
import eu.eurise.chat.Expenses.Settlement
import eu.eurise.chat.Expenses.Transfer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpensesTest {
    private val io = "@io:x.it"; private val anna = "@anna:x.it"; private val luca = "@luca:x.it"; private val sara = "@sara:x.it"
    private val four = listOf(io, anna, luca, sara)

    @Test fun partiUgualiSenzaPerdereCentesimi() {
        val s = Expenses.equal(10_000, listOf(io, anna, luca))
        assertEquals(10_000L, s.values.sum())
        assertEquals(3334L, s[anna]) // i primi in ordine alfabetico prendono il centesimo in più
        assertEquals(3333L, s[luca])
        assertEquals(emptyMap<String, Long>(), Expenses.equal(100, emptyList()))
    }

    @Test fun quoteEPercentuali() {
        val q = Expenses.proportional(9_000, mapOf(io to 2.0, anna to 1.0))
        assertEquals(mapOf(anna to 3_000L, io to 6_000L), q)
        val p = Expenses.proportional(1_000, mapOf(io to 33.33, anna to 33.33, luca to 33.34))
        assertEquals(1_000L, p.values.sum())
        assertEquals(true, Expenses.percentOk(mapOf(io to 50.0, anna to 50.0)))
        assertEquals(false, Expenses.percentOk(mapOf(io to 50.0, anna to 40.0)))
        assertEquals(mapOf(io to 600L, anna to 400L), Expenses.amounts(1_000, mapOf(io to 600L, anna to 400L)))
        assertNull(Expenses.amounts(1_000, mapOf(io to 600L, anna to 300L)))
    }

    /** L'esempio della tavola SpeSaldi: 1.284 € in quattro, bastano tre pagamenti. */
    @Test fun chiDeveAChi() {
        val e = listOf(
            Expense("1", "Traghetto", 51_200, io, Expenses.equal(51_200, four), time = 1),
            Expense("2", "Casa", 41_000, anna, Expenses.equal(41_000, four), time = 2),
            Expense("3", "Spesa e benzina", 24_000, luca, Expenses.equal(24_000, four), time = 3),
            Expense("4", "Gita", 12_200, sara, Expenses.equal(12_200, four), time = 4),
        )
        val p = Expenses.people(e, emptyList())
        assertEquals(19_100L, p.first { it.id == io }.balance)
        assertEquals(8_900L, p.first { it.id == anna }.balance)
        assertEquals(-8_100L, p.first { it.id == luca }.balance)
        assertEquals(-19_900L, p.first { it.id == sara }.balance)
        assertEquals(0L, p.sumOf { it.balance })
        assertEquals(listOf(Transfer(sara, io, 19_100), Transfer(luca, anna, 8_100), Transfer(sara, anna, 800)), Expenses.settle(p))
        // Sara paga: i conti si aggiornano.
        val after = Expenses.people(e, listOf(Settlement(sara, io, 19_100)))
        assertEquals(0L, after.first { it.id == io }.balance)
        assertEquals(-800L, after.first { it.id == sara }.balance)
    }

    @Test fun vaLUltimaVersioneETolteNo() {
        val a = Expense("1", "Cena", 18_600, anna, Expenses.equal(18_600, four), time = 1)
        val b = a.copy(cents = 18_000, owed = Expenses.equal(18_000, four), time = 5)
        val c = Expense("2", "Pane", 400, io, Expenses.equal(400, four), time = 3)
        val d = c.copy(deleted = true, time = 9)
        assertEquals(listOf(b), Expenses.current(listOf(a, c, b, d)))
    }

    @Test fun messaggioAndataERitorno() {
        val e = Expense("x1", "Cena da Gesuino", 18_600, anna, Expenses.equal(18_600, listOf(io, anna, sara)), Expenses.Mode.Equal)
        val back = Expenses.expense(Expenses.content(e, "Spesa"), 42)
        assertEquals(e.copy(time = 42), back)
        // I conti che non tornano si scartano.
        val bad = JSONObject(Expenses.content(e, "Spesa").toString())
        bad.getJSONObject(Expenses.EXPENSE).put("amount", 99)
        assertNull(Expenses.expense(bad, 1))
        val s = Expenses.settlement(Expenses.content(Settlement(sara, io, 19_100), "Pagato"), 7)
        assertEquals(Settlement(sara, io, 19_100, 7), s)
    }

    @Test fun importi() {
        assertEquals(18_600L, Expenses.parse("186"))
        assertEquals(18_650L, Expenses.parse("186,5"))
        assertEquals(18_650L, Expenses.parse("186.50"))
        assertEquals(128_400L, Expenses.parse("1.284"))
        assertEquals(128_400L, Expenses.parse("1.284,00 €"))
        assertNull(Expenses.parse("abc"))
        assertNull(Expenses.parse("1,234"))
        assertEquals("1.284,00 €", Expenses.money(128_400))
    }
}
