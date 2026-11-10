package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditToolsTest {
    @Test fun curveKeepsEndsAndRises() {
        for (s in listOf(-1f, 0f, 1f)) for (h in listOf(-1f, 0f, 1f)) {
            val lut = toneLut(s, h)
            assertEquals(0, lut[0]); assertEquals(255, lut[255])
            for (i in 1..255) assertTrue("s=$s h=$h i=$i", lut[i] >= lut[i - 1])
        }
        assertTrue(toneLut(1f, 0f)[60] > 60)
        assertTrue(toneLut(0f, -1f)[200] < 200)
        assertEquals(128, toneLut(0f, 0f)[128])
    }

    @Test fun autoBrightensDarkPhotos() {
        val dark = IntArray(256).also { for (i in 10..80) it[i] = 100 }
        val a = autoAdjust(dark)
        assertTrue(a.light > 0.2f)
        assertTrue(a.contrast > 0f)
        val balanced = IntArray(256) { 10 }
        val b = autoAdjust(balanced)
        assertTrue(kotlin.math.abs(b.light) < 0.05f)
        assertEquals(Adjust(), autoAdjust(IntArray(256)))
    }

    @Test fun lookIsDosed() {
        val s = EditState(adjust = Adjust(light = 0.1f), look = Look.Caldo, lookStrength = 0.5f)
        assertEquals(0.3f, s.effective.warmth, 1e-4f)
        assertEquals(0.1f, s.effective.light, 1e-4f)
        assertEquals(Adjust(light = 0.1f), s.copy(look = Look.None).effective)
    }

    @Test fun eraserRemovesTouchedStrokes() {
        val a = InkStroke(listOf(0.1f to 0.1f, 0.2f to 0.2f), 0.01f, 0)
        val b = InkStroke(listOf(0.8f to 0.8f), 0.01f, 0)
        assertEquals(listOf(b), erase(listOf(a, b), 0.21f, 0.2f, 0.03f))
    }
}
