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

    @Test fun findsAWhiteSheetOnALightTable() {
        // Foglio bianco su un tavolo chiaro: il vecchio metodo per zone non lo vedeva.
        val img = IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x in 18..101 && y in 22..137) 240 else 196 }
        val q = HoughQuad.find(img, w, h)
        assertNotNull(q)
        assertEquals(18f / w, q!!.tl.x, 0.03f)
        assertEquals(22f / h, q.tl.y, 0.03f)
        assertEquals(102f / w, q.br.x, 0.03f)
        assertEquals(138f / h, q.br.y, 0.03f)
    }

    @Test fun findsARotatedSheetFromEdges() {
        // Foglio ruotato di circa 8 gradi su fondo medio.
        val c = kotlin.math.cos(Math.toRadians(8.0)); val s = kotlin.math.sin(Math.toRadians(8.0))
        val img = IntArray(w * h) { i ->
            val x = i % w - w / 2.0; val y = i / w - h / 2.0
            val u = x * c + y * s; val v = -x * s + y * c
            if (kotlin.math.abs(u) < 38 && kotlin.math.abs(v) < 55) 235 else 120
        }
        val q = HoughQuad.find(img, w, h)
        assertNotNull(q)
        // Il lato in alto scende verso destra come il foglio.
        assertTrue(q!!.tr.y > q.tl.y)
        assertTrue(EdgeDetector.area(q) in 0.32f..0.48f)
    }

    @Test fun stableFramesAreClose() {
        val a = Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))
        assertEquals(0f, a.distance(a), 0.0001f)
        assertEquals(0.5f, EdgeDetector.area(Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(1f, 1f))), 0.0001f)
    }
}
