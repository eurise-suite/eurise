package eu.eurise.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeDetectorTest {
    private val w = 120
    private val h = 160

    private fun image(inside: (Int, Int) -> Boolean) = IntArray(w * h) { i -> if (inside(i % w, i / w)) 230 else 60 }

    @Test fun findsAStraightSheet() {
        val q = EdgeDetector.find(image { x, y -> x in 20..99 && y in 30..129 }, w, h)
        assertNotNull(q)
        assertEquals(20f / w, q!!.tl.x, 0.02f)
        assertEquals(30f / h, q.tl.y, 0.02f)
        assertEquals(100f / w, q.br.x, 0.02f)
        assertEquals(130f / h, q.br.y, 0.02f)
    }

    @Test fun findsATiltedSheet() {
        // Rombo leggero: gli angoli stanno sugli assi delle diagonali del rettangolo inclinato.
        val q = EdgeDetector.find(image { x, y -> y >= 20 + x / 10 && y <= 140 + x / 10 - 12 && x >= 15 + y / 10 && x <= 105 + y / 10 - 16 }, w, h)
        assertNotNull(q)
        assertTrue(q!!.tl.x < q.tr.x && q.tl.y < q.bl.y && q.br.x > q.bl.x)
    }

    @Test fun refusesSmallSpotsAndBlankImages() {
        assertNull(EdgeDetector.find(image { x, y -> x in 50..60 && y in 50..60 }, w, h))
        assertNull(EdgeDetector.find(IntArray(w * h) { 200 }, w, h))
    }

    @Test fun stableFramesAreClose() {
        val a = Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))
        assertEquals(0f, a.distance(a), 0.0001f)
        assertEquals(0.5f, EdgeDetector.area(Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(1f, 1f))), 0.0001f)
    }
}
