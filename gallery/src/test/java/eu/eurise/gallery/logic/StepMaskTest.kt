package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepMaskTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun dipintoValeSoloDovePassa() {
        val g = maskGrid(StepMask(listOf(MaskStroke(0.05f, 1f, listOf(0.1f, 0.5f, 0.9f, 0.5f)))), 1f, 100)
        assertTrue(g.sample(0.5f, 0.5f) > 0.9f)
        assertEquals(0f, g.sample(0.5f, 0.1f), 0.001f)
        val half = maskGrid(StepMask(listOf(MaskStroke(0.05f, 0.5f, listOf(0.1f, 0.5f, 0.9f, 0.5f)))), 1f, 100)
        assertEquals(0.5f, half.sample(0.5f, 0.5f), 0.05f)
    }

    @Test fun gommaEInverti() {
        val paint = MaskStroke(0.05f, 1f, listOf(0.1f, 0.5f, 0.9f, 0.5f))
        val erased = maskGrid(StepMask(listOf(paint, MaskStroke(0.08f, 0f, listOf(0.5f, 0.3f, 0.5f, 0.7f)))), 1f, 100)
        assertTrue(erased.sample(0.5f, 0.5f) < 0.05f)
        assertTrue(erased.sample(0.2f, 0.5f) > 0.9f)
        val inv = maskGrid(StepMask(listOf(paint), inverted = true), 1f, 100)
        assertTrue(inv.sample(0.5f, 0.5f) < 0.1f)
        assertEquals(1f, inv.sample(0.5f, 0.1f), 0.001f)
    }

    @Test fun mescolaSecondoLaMaschera() {
        val w = 50; val h = 50
        val grid = maskGrid(StepMask(listOf(MaskStroke(0.1f, 1f, listOf(0.2f, 0.5f, 0.3f, 0.5f)))), 1f, 50)
        val dev = IntArray(w * h) { rgb(250, 250, 250) }
        val orig = IntArray(w * h) { rgb(10, 10, 10) }
        blendMasked(dev, orig, w, h, 0, w, h, grid)
        assertEquals(rgb(250, 250, 250), dev[25 * w + 12])
        assertEquals(rgb(10, 10, 10), dev[25 * w + 45])
        // Con la foto raddrizzata di 90°, la zona dipinta a sinistra finisce in alto.
        val rot = IntArray(w * h) { rgb(250, 250, 250) }
        blendMasked(rot, orig, w, h, 0, w, h, grid, angle = 90f)
        assertEquals(rgb(250, 250, 250), rot[12 * w + 25])
        assertEquals(rgb(10, 10, 10), rot[25 * w + 12])
    }
}
