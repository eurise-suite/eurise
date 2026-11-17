package eu.eurise.feature.launcher.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class DeskCarryTest {
    private val a = appSlot("a")
    private val b = appSlot("b")
    private val c = appSlot("c")
    private val agenda = HomeSlot(HomeKind.Agenda, 1f)

    @Test
    fun placingInsertsAtIndexAndAtEnd() {
        assertEquals(listOf(a, c, b), listOf(a, b).placing(c, 1))
        assertEquals(listOf(a, b, c), listOf(a, b).placing(c, -1))
        assertEquals(listOf(c, a, b), listOf(a, b).placing(c, 0))
        assertEquals(listOf(a, b, c), listOf(a, b).placing(c, 99))
    }

    @Test
    fun placingMovesInsteadOfRepeating() {
        assertEquals(listOf(b, a, c), listOf(a, b, c).placing(a, 1))
        assertEquals(listOf(b, c, a), listOf(a, b, c).placing(a, -1))
        assertEquals(1, listOf(agenda, a).placing(HomeSlot(HomeKind.Agenda, 0.5f), 1).count { it.kind == HomeKind.Agenda })
    }

    // Tre app in fila: a [0,100], b [100,200], c [200,300], alte 100.
    private val rects = mapOf(
        a.key to Rect(0f, 0f, 100f, 100f),
        b.key to Rect(100f, 0f, 200f, 100f),
        c.key to Rect(200f, 0f, 300f, 100f),
    )
    private val slots = listOf(a, b, c)

    @Test
    fun indexFollowsTheHalfOfTheSlotUnderTheFinger() {
        assertEquals(1, carryIndex(slots, rects, Offset(120f, 50f), -1, null))
        assertEquals(2, carryIndex(slots, rects, Offset(180f, 50f), -1, null))
        assertEquals(0, carryIndex(slots, rects, Offset(10f, 50f), -1, null))
    }

    @Test
    fun belowEverythingGoesAtTheEndElsewhereKeepsThePlace() {
        assertEquals(-1, carryIndex(slots, rects, Offset(50f, 400f), 1, null))
        assertEquals(1, carryIndex(slots, rects, Offset(350f, 50f), 1, null))
        assertEquals(2, carryIndex(slots, rects, Offset(150f, 50f), 2, Rect(140f, 0f, 160f, 100f)))
    }

    @Test
    fun emptyPageTakesItAtTheEnd() {
        assertEquals(-1, carryIndex(emptyList(), emptyMap(), Offset(10f, 10f), 0, null))
    }

    @Test
    fun previewHidesTheCarriedSlotAndShowsTheHole() {
        val carry = DeskCarry()
        carry.start(CarryItem(b, from = "p1"), lifted = true, index = 1)
        val shown = carry.preview("p1", slots)
        assertEquals(listOf(a.key, DeskCarry.hole(b).key, c.key), shown.map { it.key })
        assertEquals(listOf(a, c), carry.preview("p2", slots))
        carry.chip = "p2"
        assertEquals(CarryTarget("p2", -1), carry.target("p1"))
        carry.chip = DeskCarry.NEW
        assertEquals(CarryTarget("p1", -1, newPage = true), carry.target("p1"))
    }
}
