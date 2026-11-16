package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class KeyModelTest {
    // Fila «a s d f» con tasti larghi 100 e alti 150.
    private val a = KeyBox('a'.code, 0, 0, 100, 150)
    private val s = KeyBox('s'.code, 100, 0, 100, 150)
    private val d = KeyBox('d'.code, 200, 0, 100, 150)

    private fun gauss(r: Random) = (r.nextDouble() + r.nextDouble() + r.nextDouble() - 1.5).toFloat() * 2f

    /** Tocchi di [box] spostati di ([ox], [oy]) quote del tasto, con un po' di dispersione. */
    private fun train(m: KeyModel, box: KeyBox, ox: Float, oy: Float, n: Int, seed: Int) {
        val r = Random(seed)
        repeat(n) { m.record(box, box.cx + (ox + 0.08f * gauss(r)) * box.w, box.cy + (oy + 0.08f * gauss(r)) * box.h) }
    }

    @Test fun prudenteFinoAQuandoNonEPronto() {
        val m = KeyModel()
        assertFalse(m.ready)
        // Senza esempi: centro del tasto e raggio di AOSP (0,15 della diagonale).
        val spot = m.sweetSpot(s)
        assertEquals(s.cx, spot[0], 0.01f)
        assertEquals(s.cy, spot[1], 0.01f)
        assertEquals(0.15f * hypot(100f, 150f), spot[2], 0.02f * hypot(100f, 150f))
        // Il tasto sotto il dito non cambia mai prima che il modello sia pronto.
        assertEquals(s.code, m.choose(s, listOf(a, d), s.x + 2f, s.cy))
    }

    @Test fun imparaIlPuntoDiOgniTasto() {
        val m = KeyModel()
        train(m, s, 0.1f, 0.15f, 300, 1)
        assertTrue(m.ready)
        val spot = m.sweetSpot(s)
        assertTrue("x spostato a destra", spot[0] > s.cx + 5f)
        assertTrue("y spostato in basso", spot[1] > s.cy + 10f)
        // Il punto preferito non va mai oltre il limite.
        train(m, s, 0.9f, 0.9f, 400, 2)
        val far = m.sweetSpot(s)
        assertTrue(far[0] <= s.cx + KeyModel.MAX_MX * s.w + 0.01f)
        assertTrue(far[1] <= s.cy + KeyModel.MAX_MY * s.h + 0.01f)
    }

    @Test fun ilBordoVaAlTastoCheLaPersonaVoleva() {
        val m = KeyModel()
        // La persona, quando vuole «s», tocca spesso a sinistra, verso «a»; «a» la tocca al centro.
        train(m, s, -0.4f, 0f, 200, 3)
        train(m, a, 0f, 0f, 200, 4)
        // Un tocco appena dentro «a», vicino al bordo: con questo modello voleva «s».
        assertEquals(s.code, m.choose(a, listOf(s), a.x + 92f, a.cy))
        // Al centro di «a» resta «a».
        assertEquals(a.code, m.choose(a, listOf(s), a.cx, a.cy))
        // Un tasto lontano non ruba mai il tocco.
        assertEquals(a.code, m.choose(a, listOf(d), a.x + 92f, a.cy))
    }

    @Test fun pochiEsempiDelTastoAccantoNonBastano() {
        val m = KeyModel()
        train(m, a, 0f, 0f, 300, 5)
        train(m, s, -0.4f, 0f, 10, 6)
        assertEquals(a.code, m.choose(a, listOf(s), a.x + 92f, a.cy))
    }

    @Test fun iTocchiLontaniNonInsegnano() {
        val m = KeyModel()
        assertFalse(m.record(s, s.cx + 300f, s.cy))
        assertEquals(0, m.learned)
    }

    @Test fun salvaERilegge() {
        val m = KeyModel()
        train(m, s, 0.1f, 0.1f, 200, 7)
        train(m, a, -0.05f, 0.12f, 100, 8)
        val copy = KeyModel().apply { load(m.save()) }
        assertEquals(m.learned, copy.learned)
        val p = m.sweetSpot(s)
        val q = copy.sweetSpot(s)
        for (i in 0..2) assertEquals(p[i], q[i], 0.01f)
        // Testo rovinato: si riparte da zero, senza errori.
        val broken = KeyModel().apply { load("2|xx|qualcosa") }
        assertEquals(0, broken.learned)
    }
}
