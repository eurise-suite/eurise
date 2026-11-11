package eu.eurise.feature.launcher.apps

import android.graphics.Bitmap
import android.graphics.Color

/**
 * La sagoma del logo di un'icona a tutto riquadro, staccata dal colore di fondo. La logica sta in [maskOf] (pixel ARGB),
 * provata senza telefono in LogoMaskTest.
 */
internal object LogoMask {

    fun of(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val alpha = maskOf(pixels, w, h) ?: return null
        val colors = IntArray(w * h) { Color.argb(alpha[it].toInt() and 0xFF, 0, 0, 0) }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Alfa della sagoma (0–255 per pixel) oppure null. Il fondo è la media dei pixel del bordo (il 6% esterno); se il
     * bordo è trasparente si usa l'alfa del disegno. Un pixel è del logo quando si stacca dal fondo di almeno
     * [THRESHOLD] (somma delle differenze RGB), con un passaggio morbido per i bordi del logo.
     */
    fun maskOf(pixels: IntArray, w: Int, h: Int): ByteArray? {
        val ring = maxOf(1, minOf(w, h) * 6 / 100)
        var r = 0L; var g = 0L; var b = 0L; var a = 0L; var n = 0
        val border = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            if (x >= ring && y >= ring && x < w - ring && y < h - ring) continue
            val p = pixels[y * w + x]
            border += p
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; a += (p ushr 24); n++
        }
        if (n == 0) return null
        val transparent = a / n < 64
        val br = (r / n).toInt(); val bg = (g / n).toInt(); val bb = (b / n).toInt()
        if (!transparent) {
            // Bordo non uniforme: non è un logo su un fondo pieno.
            val far = border.count { distance(it, br, bg, bb) > THRESHOLD }
            if (far > border.size / 8) return null
        }
        val out = ByteArray(w * h)
        var covered = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val pa = p ushr 24
            val v = if (transparent) pa else {
                val d = distance(p, br, bg, bb)
                if (pa < 128) 0 else ((d - THRESHOLD / 2) * 255 / THRESHOLD).coerceIn(0, 255)
            }
            out[i] = v.toByte()
            if (v > 128) covered++
        }
        val coverage = covered.toFloat() / pixels.size
        return if (coverage in 0.03f..0.6f) out else null
    }

    private fun distance(p: Int, r: Int, g: Int, b: Int) =
        Math.abs(((p shr 16) and 0xFF) - r) + Math.abs(((p shr 8) and 0xFF) - g) + Math.abs((p and 0xFF) - b)

    private const val THRESHOLD = 90
}
