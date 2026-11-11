package eu.eurise.feature.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpnessTest {
    private val w = 300
    private val h = 400

    /** Righe di «testo»: barre nere su carta chiara, con i bordi netti. */
    private fun page(): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val line = (y / 12) % 2 == 0 && y > 20 && y < h - 20
        val letter = (x / 5) % 3 != 0 && x > 20 && x < w - 20
        if (line && letter && (y % 12) in 3..9) 30 else 230
    }

    /** Sfocatura a scatola, come una foto mossa. */
    private fun blur(src: IntArray, r: Int): IntArray {
        var a = src
        repeat(2) {
            val b = IntArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0; var n = 0
                for (dx in -r..r) { val xx = x + dx; if (xx in 0 until w) { s += a[y * w + xx]; n++ } }
                b[y * w + x] = s / n
            }
            val c = IntArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0; var n = 0
                for (dy in -r..r) { val yy = y + dy; if (yy in 0 until h) { s += b[yy * w + x]; n++ } }
                c[y * w + x] = s / n
            }
            a = c
        }
        return a
    }

    @Test
    fun sharpPageIsNotBlurry() = assertFalse(Sharpness.isBlurry(page(), w, h))

    @Test
    fun movedPageIsBlurry() = assertTrue(Sharpness.isBlurry(blur(page(), 4), w, h))

    @Test
    fun emptyPageSaysNothing() {
        assertNull(Sharpness.score(IntArray(w * h) { 228 }, w, h))
        assertFalse(Sharpness.isBlurry(IntArray(w * h) { 228 }, w, h))
    }
}
