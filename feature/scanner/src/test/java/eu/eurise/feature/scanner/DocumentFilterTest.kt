package eu.eurise.feature.scanner

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Il caso segnalato l'11 novembre: foto normale di un foglio con luce fredda, il documento veniva bluastro. */
class DocumentFilterTest {
    private val w = 400
    private val h = 560

    private fun argb(r: Float, g: Float, b: Float) =
        (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** Carta azzurrina (190, 203, 232), ombra a sinistra, righe di testo scuro tinto, un timbro rosso ad anello. */
    private val photo = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val light = 0.65f + 0.35f * x / w
        val text = (y / 14) % 2 == 0 && x in 40..360 && (x / 3) % 3 != 0 && y in 40..420
        val d = (x - 300) * (x - 300) + (y - 480) * (y - 480)
        val stamp = d in 30 * 30 + 1 until 40 * 40
        val f = when {
            text -> floatArrayOf(0.17f, 0.18f, 0.26f)
            stamp -> floatArrayOf(0.95f, 0.25f, 0.28f)
            else -> floatArrayOf(1f, 1f, 1f)
        }
        argb(190f * f[0] * light, 203f * f[1] * light, 232f * f[2] * light)
    }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun tint(c: Int) = max(abs(r(c) - g(c)), max(abs(g(c) - b(c)), abs(r(c) - b(c))))

    @Test fun paperBecomesNeutralWhiteEvenInShadow() {
        val out = DocumentFilter.document(photo, w, h)
        for ((x, y) in listOf(20 to 500, 390 to 500, 200 to 30, 10 to 10)) {
            val c = out[y * w + x]
            assertTrue("carta in ($x,$y): ${r(c)},${g(c)},${b(c)}", r(c) > 240 && g(c) > 240 && b(c) > 240 && tint(c) < 6)
        }
    }

    @Test fun textBecomesNeutralBlack() {
        val out = DocumentFilter.document(photo, w, h)
        val c = out[56 * w + 40]
        assertTrue("testo: ${r(c)},${g(c)},${b(c)}", r(c) < 40 && tint(c) < 6)
    }

    @Test fun realColoursStay() {
        val c = DocumentFilter.document(photo, w, h)[480 * w + 335]
        assertTrue("timbro: ${r(c)},${g(c)},${b(c)}", r(c) > 200 && g(c) < 90 && b(c) < 90)
    }

    @Test fun grayAndBlackWhiteHaveNoTint() {
        val gray = DocumentFilter.gray(photo, w, h)
        assertTrue(gray.all { tint(it) == 0 })
        val bw = DocumentFilter.blackWhite(photo, w, h)
        assertTrue(bw[500 * w + 20] == -1 && bw[56 * w + 40] == 0xFF000000.toInt())
    }
}
