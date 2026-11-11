package eu.eurise.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SideRefinerTest {
    private val w = 600
    private val h = 800
    // Foglio leggermente ruotato e in prospettiva (angoli in pixel).
    private val truth = listOf(118.0 to 96.0, 482.0 to 120.0, 470.0 to 700.0, 102.0 to 672.0)

    /** Foglio chiaro (225) su tavolo scuro (55), con un po' di testo scuro dentro. */
    private fun image(): IntArray = IntArray(w * h) { i ->
        val x = i % w + 0.5
        val y = i / w + 0.5
        if (inside(x, y)) { if ((y.toInt() / 18) % 2 == 0 && x in 160.0..440.0 && y in 200.0..600.0 && x.toInt() % 37 < 20) 70 else 225 } else 55
    }

    private fun inside(x: Double, y: Double): Boolean {
        for (k in 0 until 4) {
            val (ax, ay) = truth[k]
            val (bx, by) = truth[(k + 1) % 4]
            if ((bx - ax) * (y - ay) - (by - ay) * (x - ax) < 0) return false
        }
        return true
    }

    private fun quad(points: List<Pair<Double, Double>>) =
        points.map { Pt((it.first / w).toFloat(), (it.second / h).toFloat()) }.let { Quad(it[0], it[1], it[2], it[3]) }

    @Test fun cornersComeWithinTwoPixels() {
        // Partenza come dal rilevatore a bassa risoluzione: angoli sbagliati di 6–9 pixel.
        val coarse = quad(listOf(124.0 to 90.0, 475.0 to 128.0, 478.0 to 694.0, 96.0 to 679.0))
        val refined = SideRefiner.refine(image(), w, h, coarse)
        refined.points.zip(truth).forEach { (p, t) ->
            assertEquals(t.first, p.x * w.toDouble(), 2.0)
            assertEquals(t.second, p.y * h.toDouble(), 2.0)
        }
    }

    @Test fun flatImageKeepsTheStart() {
        val coarse = quad(truth)
        val refined = SideRefiner.refine(IntArray(w * h) { 128 }, w, h, coarse)
        assertEquals(coarse, refined)
    }

    private val sheet = Quad(Pt(0.1f, 0.1f), Pt(0.9f, 0.1f), Pt(0.9f, 0.9f), Pt(0.1f, 0.9f))

    @Test fun shootsOnceThenWaitsForANewSheet() {
        val auto = AutoShot(stillFrames = 5)
        val shots = (0 until 40).count { auto.onFrame(sheet, 1f) }
        assertEquals(1, shots)
        assertFalse(auto.armed)
        // Il foglio si toglie (mano davanti), poi ne arriva un altro nello stesso posto: un solo scatto in più.
        repeat(4) { auto.onFrame(null, 20f) }
        assertTrue(auto.armed)
        assertEquals(1, (0 until 40).count { auto.onFrame(sheet, 1f) })
    }

    @Test fun movingPhoneDoesNotShoot() {
        val auto = AutoShot(stillFrames = 5)
        assertEquals(0, (0 until 30).count { auto.onFrame(sheet, 9f) })
    }

    @Test fun redoShootsAgainOnTheSameSheet() {
        val auto = AutoShot(stillFrames = 5)
        assertEquals(1, (0 until 20).count { auto.onFrame(sheet, 1f) })
        auto.rearm()
        assertEquals(1, (0 until 20).count { auto.onFrame(sheet, 1f) })
    }
}
