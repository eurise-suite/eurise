package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun lum(c: Int) = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3

    @Test fun laPelleSiRiconosce() {
        assertTrue(skinLikelihood(224, 172, 140) > 0.8f)
        assertTrue(skinLikelihood(141, 85, 60) > 0.5f)
        assertEquals(0f, skinLikelihood(60, 120, 220), 0.001f)
        assertEquals(0f, skinLikelihood(80, 160, 70), 0.001f)
    }

    @Test fun lOvaleDelVisoSfuma() {
        assertEquals(1f, faceWeight(50f, 55f, 50f, 50f, 20f), 0.001f)
        assertEquals(0f, faceWeight(100f, 50f, 50f, 50f, 20f), 0.001f)
        val edge = faceWeight(72f, 57f, 50f, 50f, 20f)
        assertTrue(edge > 0f && edge < 1f)
    }

    @Test fun lisciaLaPelleMaNonIBordi() {
        val w = 80; val h = 80
        // Pelle a granelli, con un bordo netto scuro (un sopracciglio) a metà.
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w
            if (y in 30..32) rgb(40, 30, 25) else if ((x + y) % 2 == 0) rgb(230, 175, 145) else rgb(206, 155, 125) }
        val before = px.copyOf()
        portraitFace(px, w, h, 40f, 35f, 22f, smoothing = 1f, eyes = 0f)
        val grain = { a: IntArray -> kotlin.math.abs(lum(a[45 * w + 40]) - lum(a[45 * w + 41])) }
        assertTrue("granelli più lisci", grain(px) < grain(before) / 2)
        assertTrue("il bordo resta scuro", lum(px[31 * w + 40]) < 60)
    }

    @Test fun laLuceSulVisoSchiarisceDentroEScurisceFuori() {
        val w = 100; val h = 100
        val px = IntArray(w * h) { rgb(128, 128, 128) }
        spotlight(px, w, h, 0, w, h, Portrait(listOf(FaceSpot(0.5f, 0.4f, 0.15f)), spotlight = 1f))
        assertTrue(lum(px[45 * w + 50]) > 140)
        assertTrue(lum(px[2 * w + 2]) < 100)
    }
}
