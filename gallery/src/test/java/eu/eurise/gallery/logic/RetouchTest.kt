package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetouchTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun lum(c: Int) = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3

    @Test fun ritoccoVuotoNonCambiaNulla() {
        val px = IntArray(20 * 20) { rgb(it % 255, 100, 50) }
        val copy = px.copyOf()
        applyLocal(px, 20, 20, 0, 20, 20, Retouch(), null)
        assertTrue(px.contentEquals(copy))
        assertTrue(Retouch().isEmpty)
    }

    @Test fun ilSelettivoCambiaVicinoEIColoriSimili() {
        val w = 100; val h = 100
        // Metà sinistra cielo azzurro, metà destra muro rosso.
        val px = IntArray(w * h) { i -> if (i % w < 50) rgb(120, 160, 220) else rgb(180, 60, 40) }
        val point = SelectivePoint(1, 0.25f, 0.5f, radius = 0.4f, ref = rgb(120, 160, 220), light = 0.8f)
        applyLocal(px, w, h, 0, w, h, Retouch(points = listOf(point)), null)
        assertTrue("il cielo vicino si schiarisce", lum(px[50 * w + 25]) > 170)
        assertTrue("il muro accanto quasi non cambia", kotlin.math.abs(red(px[50 * w + 60]) - 180) < 12)
        // Lontano dal punto anche il cielo resta com'era.
        val far = IntArray(w * h) { rgb(120, 160, 220) }
        applyLocal(far, w, h, 0, w, h, Retouch(points = listOf(point.copy(x = 0f, y = 0f, radius = 0.1f))), null)
        assertEquals(rgb(120, 160, 220), far[99 * w + 99])
    }

    @Test fun ilPennelloSegueLaPassataELaGommaLaToglie() {
        val stroke = BrushStroke(BrushKind.Lighten, 0.05f, 1f, listOf(0.1f, 0.5f, 0.9f, 0.5f))
        val m = brushMasks(listOf(stroke), 1f, 100)
        assertTrue(m.sample(m.exposure, 0.5f, 0.5f) > 0.8f)
        assertEquals(0f, m.sample(m.exposure, 0.5f, 0.1f), 0.001f)
        val erased = brushMasks(listOf(stroke, BrushStroke(BrushKind.Erase, 0.08f, 1f, listOf(0.5f, 0.3f, 0.5f, 0.7f))), 1f, 100)
        assertTrue(erased.sample(erased.exposure, 0.5f, 0.5f) < 0.1f)
        assertTrue(erased.sample(erased.exposure, 0.2f, 0.5f) > 0.8f)
        // Applicato: dove è passato schiarisce.
        val px = IntArray(50 * 50) { rgb(100, 100, 100) }
        applyLocal(px, 50, 50, 0, 50, 50, Retouch(strokes = listOf(stroke)), m)
        assertTrue(lum(px[25 * 50 + 25]) > 160)
        assertEquals(100, lum(px[2 * 50 + 25]))
    }

    @Test fun lePasateSonoTondeAncheInUnaFotoLarga() {
        val m = brushMasks(listOf(BrushStroke(BrushKind.Warm, 0.1f, 1f, listOf(0.5f, 0.5f))), 2f, 200)
        assertEquals(200, m.w); assertEquals(100, m.h)
        assertTrue(m.sample(m.warmth, 0.5f, 0.5f) > 0.9f)
    }

    @Test fun ilCorrettivoTogleLaMacchia() {
        val w = 120; val h = 120
        // Trama a righe leggere e una macchia scura al centro.
        val px = IntArray(w * h) { i -> val v = if ((i / w) % 6 < 3) 150 else 160; rgb(v, v, v) }
        for (y in 55..65) for (x in 55..65) px[y * w + x] = rgb(20, 20, 20)
        heal(px, w, h, 60, 60, 9)
        for (y in 56..64) for (x in 56..64) assertTrue("ancora scuro in $x,$y", lum(px[y * w + x]) > 120)
        // Lontano dalla macchia nulla cambia.
        assertEquals(rgb(150, 150, 150), px[0])
    }

    @Test fun distanzaInQuoteDelLatoCorto() {
        assertEquals(0.5f, shortDistance(0f, 0f, 0.25f, 0f, 200, 100), 0.001f)
    }
}
