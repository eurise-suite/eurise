package eu.eurise.gallery.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopTest {
    private fun gradient(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val r = (x * 255 / (w - 1)); val g = (y * 255 / (h - 1)); val b = ((x + y) * 255 / (w + h - 2))
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test fun curvaDirittaLasciaTuttoComE() {
        assertArrayEquals(IntArray(256) { it }, curveLut(emptyList()))
        assertArrayEquals(IntArray(256) { it }, curveLut(listOf(CurvePoint(0.5f, 0.5f))))
    }

    @Test fun curvaSempreCrescenteEFedeleAiPunti() {
        val lut = curveLut(CurvePreset.StrongS.points)
        for (i in 1..255) assertTrue("scende a $i", lut[i] >= lut[i - 1])
        assertEquals((0.15f * 255).toInt().toFloat(), lut[(0.25f * 255).toInt()].toFloat(), 3f)
        assertEquals(0, lut[0]); assertEquals(255, lut[255])
        // Un punto che scende rispetto al precedente non inverte i toni.
        val odd = curveLut(listOf(CurvePoint(0.3f, 0.6f), CurvePoint(0.6f, 0.3f)))
        for (i in 1..255) assertTrue(odd[i] >= odd[i - 1])
        // Curva opaca: il nero diventa grigio scuro.
        assertTrue(curveLut(CurvePreset.Matte.points)[0] > 10)
    }

    @Test fun regolazioniNeutreNonCambianoNulla() {
        val px = gradient(40, 30)
        val copy = px.copyOf()
        develop(px, 40, 30, Adjust())
        assertArrayEquals(copy, px)
        develop(px, 40, 30, Adjust(), adjustMatrix(Adjust()))
        assertArrayEquals(copy, px)
    }

    @Test fun vividezzaRispettaIColoriGiaPieni() {
        val dull = (0xFF shl 24) or (140 shl 16) or (120 shl 8) or 110
        val full = (0xFF shl 24) or (250 shl 16) or (20 shl 8) or 20
        val px = intArrayOf(dull, full)
        develop(px, 2, 1, Adjust(vibrance = 1f))
        val dullSpread = ((px[0] shr 16) and 0xFF) - (px[0] and 0xFF)
        assertTrue("il colore spento si ravviva", dullSpread > 30)
        assertTrue("quello pieno quasi non cambia", kotlin.math.abs(((px[1] shr 16) and 0xFF) - 250) <= 6)
    }

    @Test fun strutturaENitidezzaAumentanoIlContrastoLocale() {
        val w = 60; val h = 60
        val px = IntArray(w * h) { i -> val v = if ((i % w) / 3 % 2 == 0) 110 else 140; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val before = px.copyOf()
        develop(px, w, h, Adjust(structure = 1f, sharpen = 1f))
        fun spread(a: IntArray) = a.maxOf { it and 0xFF } - a.minOf { it and 0xFF }
        assertTrue(spread(px) > spread(before))
        // Un'immagine piatta resta piatta.
        val flat = IntArray(w * h) { (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128 }
        develop(flat, w, h, Adjust(structure = 1f, sharpen = 1f))
        assertTrue(flat.all { it and 0xFF == 128 })
    }

    @Test fun biancoGiustoRendeGrigioIlPunto() {
        // Un grigio troppo blu e un po' verde.
        val a = neutralize(110, 135, 150, Adjust())
        val px = intArrayOf((0xFF shl 24) or (110 shl 16) or (135 shl 8) or 150)
        applyMatrix(px, adjustMatrix(a))
        val r = (px[0] shr 16) and 0xFF; val g = (px[0] shr 8) and 0xFF; val b = px[0] and 0xFF
        assertTrue("r=$r g=$g b=$b", kotlin.math.abs(r - b) <= 3 && kotlin.math.abs(g - (r + b) / 2) <= 3)
    }

    @Test fun leModalitaTueSiSalvanoESiRileggono() {
        val looks = listOf(
            SavedLook(1, "Mare d'estate", Adjust(light = 0.1f, warmth = 0.3f, structure = 0.2f, vignette = 0.25f,
                curves = Curves(luma = CurvePreset.SoftS.points, blue = listOf(CurvePoint(0.5f, 0.45f))))),
            SavedLook(2, "Sera\tcon\ttab", Adjust(tint = -0.2f, vibrance = 0.4f)),
        )
        val back = LookCodec.decode(LookCodec.encode(looks))
        assertEquals(2, back.size)
        assertEquals(looks[0], back[0])
        assertEquals("Sera con tab", back[1].name)
        assertEquals(looks[1].adjust, back[1].adjust)
        assertTrue(LookCodec.decode("rotto\nanche questo").isEmpty())
    }

    @Test fun ilFiltroDosatoAvvicinaLaCurvaAllaDiagonale() {
        val half = Adjust(curves = Curves(luma = listOf(CurvePoint(0.5f, 0.7f)))) * 0.5f
        assertEquals(0.6f, half.curves.luma[0].y, 0.001f)
        assertTrue((Adjust() * 0f).isNeutral)
    }

    @Test fun unaModalitaSiCondivideComeTesto() {
        val look = SavedLook(7, "Mare d'estate", Adjust(warmth = 0.3f, vignette = 0.25f, curves = Curves(luma = CurvePreset.SoftS.points)))
        val text = LookShare.encode(look)
        assertTrue(text.startsWith(LookShare.PREFIX))
        val back = LookShare.decode("Prova questa: $text ciao", 99)!!
        assertEquals(99L, back.id)
        assertEquals(look.name, back.name)
        assertEquals(look.adjust, back.adjust)
        assertEquals(null, LookShare.decode("niente qui", 1))
        assertEquals(null, LookShare.decode(LookShare.PREFIX + "%%%", 1))
    }
}
