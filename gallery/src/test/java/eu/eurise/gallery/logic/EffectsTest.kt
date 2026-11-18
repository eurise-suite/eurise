package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun lum(c: Int) = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
    private val w = 120
    private val h = 80
    /** Una scena di prova: cielo sfumato in alto, terra a quadretti in basso. */
    private fun scene() = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        if (y < h / 2) rgb(90 + y * 2, 140 + y, 210) else if ((x / 6 + y / 6) % 2 == 0) rgb(170, 120, 70) else rgb(70, 90, 40)
    }
    private fun full() = EffectSpace.of(w, h, Crop())

    @Test fun lePosizioniSonoQuelleDelRiquadroFinale() {
        val s = full()
        assertEquals(0.25f, s.u(30f, 0f), 1e-4f)
        assertEquals(0.5f, s.v(0f, 40f), 1e-4f)
        val cropped = EffectSpace.of(w, h, Crop(left = 0.5f))
        assertEquals(0.5f, cropped.u(90f, 10f), 1e-4f)
        assertEquals(2f, EffectSpace.of(w, h, Crop(top = 0.25f)).aspect, 1e-4f)
        // Ruotata, il centro resta al centro.
        val rot = EffectSpace.of(w, h, Crop(), angle = 30f, scale = 1.4f)
        assertEquals(0.5f, rot.u(w / 2f, h / 2f), 1e-4f)
        assertEquals(0.5f, rot.v(w / 2f, h / 2f), 1e-4f)
    }

    @Test fun ogniEffettoCambiaLaFotoERestaNeiLimiti() {
        val second = Plane.of(IntArray(w * h) { rgb(250, 30, 30) }, w, h)
        for (kind in EffectKind.entries) for (style in 0 until maxOf(1, kind.styles)) {
            val px = scene()
            val e = newEffect(kind, 1, second = "x").copy(style = style)
            applyEffects(px, w, h, listOf(e), full(), mapOf("x" to second))
            assertTrue("$kind $style cambia qualcosa", !px.contentEquals(scene()))
            assertTrue(px.all { (it ushr 24) == 0xFF })
        }
    }

    @Test fun laVignettaturaScurisceIBordiENonIlCentro() {
        val px = IntArray(w * h) { rgb(150, 150, 150) }
        applyEffects(px, w, h, listOf(newEffect(EffectKind.Vignette, 1)), full())
        assertTrue(lum(px[2 * w + 2]) < 110)
        assertEquals(150, lum(px[40 * w + 60]))
        // Spostando il centro, la zona chiara si sposta.
        val moved = IntArray(w * h) { rgb(150, 150, 150) }
        applyEffects(moved, w, h, listOf(newEffect(EffectKind.Vignette, 1).copy(x = 0.1f, y = 0.1f)), full())
        assertTrue(lum(moved[6 * w + 10]) > lum(moved[74 * w + 110]))
    }

    @Test fun laCorniceStaSulBordo() {
        val px = IntArray(w * h) { rgb(100, 100, 100) }
        applyEffects(px, w, h, listOf(newEffect(EffectKind.Frame, 1)), full())
        assertEquals(rgb(248, 248, 248), px[0])
        assertEquals(rgb(100, 100, 100), px[40 * w + 60])
        val black = IntArray(w * h) { rgb(100, 100, 100) }
        applyEffects(black, w, h, listOf(newEffect(EffectKind.Frame, 1).copy(style = 1)), full())
        assertEquals(rgb(12, 12, 12), black[w * h - 1])
    }

    @Test fun ilBiancoENeroEGrigio() {
        val px = scene()
        applyEffects(px, w, h, listOf(newEffect(EffectKind.BlackWhite, 1)), full())
        assertTrue(px.all { ((it shr 16) and 0xFF) == (it and 0xFF) && ((it shr 8) and 0xFF) == (it and 0xFF) })
    }

    @Test fun laSfocaturaLasciaNitidoIlCentro() {
        val checker = IntArray(w * h) { i -> if ((i % w + i / w) % 2 == 0) rgb(240, 240, 240) else rgb(20, 20, 20) }
        val px = checker.copyOf()
        applyEffects(px, w, h, listOf(newEffect(EffectKind.Blur, 1).copy(a = 1f, c = 0.2f)), full())
        assertEquals(checker[40 * w + 60], px[40 * w + 60])
        assertTrue("lontano si confonde", kotlin.math.abs(lum(px[2 * w + 2]) - lum(px[2 * w + 3])) < 60)
    }

    @Test fun ilPennelloLimitaLEffetto() {
        val px = IntArray(w * h) { rgb(150, 150, 150) }
        val mask = StepMask(listOf(MaskStroke(0.1f, 1f, listOf(0.1f, 0.5f, 0.3f, 0.5f))))
        applyEffects(px, w, h, listOf(newEffect(EffectKind.BlackWhite, 1).copy(a = 1f, mask = mask)), full())
        assertTrue(lum(px[40 * w + 24]) > 200)
        assertEquals(rgb(150, 150, 150), px[40 * w + 110])
    }

    @Test fun laCopiaPiccolaFaLaMedia() {
        val px = IntArray(4 * 2) { i -> if (i % 2 == 0) rgb(0, 0, 0) else rgb(200, 100, 50) }
        val p = Plane.of(px, 4, 2, maxSide = 2)
        assertEquals(2, p.w); assertEquals(1, p.h)
        assertEquals(100f, p.r[0], 0.01f)
    }
}
