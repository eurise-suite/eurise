package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTest {
    @Test fun prospettivaNeutraLasciaGliAngoli() {
        val p = Perspective()
        assertTrue(p.isIdentity)
        assertEquals(Perspective.UNIT, p.quad().toList())
    }

    @Test fun verticaleAllargaLaParteAlta() {
        val q = Perspective(vertical = 1f).quad()
        assertEquals(Perspective.MAX, q[0], 0.0001f)       // in alto a sinistra rientra
        assertEquals(1f - Perspective.MAX, q[2], 0.0001f)  // in alto a destra rientra
        assertEquals(1f, q[4], 0.0001f)                    // in basso resta
        val down = Perspective(vertical = -1f).quad()
        assertEquals(Perspective.MAX, down[6], 0.0001f)
        assertFalse(Perspective(horizontal = 0.4f).isIdentity)
    }

    @Test fun gliAngoliSpostatiNonSiRovesciano() {
        var p = Perspective()
        p = p.movedCorner(0, 0.9f, 0.9f)
        val q = p.quad()
        assertTrue(q[0] <= 0.45f && q[1] <= 0.45f)
        p = p.movedCorner(2, -5f, -5f)
        val r = p.quad()
        assertTrue(r[4] >= 0.55f && r[5] >= 0.55f)
        assertFalse(p.isIdentity)
    }

    private fun photo(w: Int, h: Int) = IntArray(w * h) { i -> (0xFF shl 24) or ((i % w) * 10 shl 16) or ((i / w) * 10 shl 8) or 77 }

    @Test fun espandiTieneLaFotoAlCentro() {
        val w = 20; val h = 12
        val px = photo(w, h)
        for (fill in ExpandFill.entries) {
            val m = 4
            val out = expandPixels(px, w, h, m, fill)
            val nw = w + 2 * m
            assertEquals(nw * (h + 2 * m), out.size)
            for (y in 0 until h) for (x in 0 until w) assertEquals(px[y * w + x], out[(y + m) * nw + x + m])
        }
        val white = expandPixels(px, w, h, 3, ExpandFill.White)
        assertEquals(-0x1, white[0])
        val black = expandPixels(px, w, h, 3, ExpandFill.Black)
        assertEquals(-0x1000000, black[0])
    }

    @Test fun continuaRiprendeIColoriDelBordo() {
        val w = 30; val h = 20
        val px = IntArray(w * h) { (0xFF shl 24) or (200 shl 16) or (60 shl 8) or 30 }
        val out = expandPixels(px, w, h, 6, ExpandFill.Continue)
        // Una foto tutta di un colore resta di quel colore anche nei bordi aggiunti.
        assertTrue(out.all { ((it shr 16) and 0xFF) in 198..202 && ((it shr 8) and 0xFF) in 58..62 })
        assertEquals(0, Expand(0f).margin(1000, 800))
        assertEquals(267, Expand(1f).margin(1000, 800))
    }
}
