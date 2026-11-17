package eu.eurise.gallery.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prospettiva ed Espandi (tavola GalProspettiva, approvata il 17 novembre). Logica pura: si prova in `FrameTest`.
 */

/**
 * La prospettiva: [vertical] e [horizontal] da -1 a 1 raddrizzano le linee che convergono (una facciata vista dal
 * basso, un foglio fotografato di sbieco); [free], se c'è, sono i quattro angoli spostati a mano (x, y da 0 a 1 in
 * ordine: in alto a sinistra, in alto a destra, in basso a destra, in basso a sinistra) e vincono sui cursori.
 */
data class Perspective(val vertical: Float = 0f, val horizontal: Float = 0f, val free: List<Float>? = null) {
    val isIdentity: Boolean get() = free?.let { f -> f.indices.all { abs(f[it] - UNIT[it]) < 0.0005f } } ?: (vertical == 0f && horizontal == 0f)

    /**
     * I quattro punti della foto che diventano i quattro angoli del risultato (8 numeri da 0 a 1). Verticale positivo:
     * la parte alta si allarga (le linee che salendo si stringevano tornano dritte); negativo: la parte bassa.
     */
    fun quad(): FloatArray {
        free?.let { if (it.size == 8) return it.toFloatArray() }
        val v = vertical.coerceIn(-1f, 1f) * MAX
        val h = horizontal.coerceIn(-1f, 1f) * MAX
        val top = max(0f, v); val bottom = max(0f, -v)
        val left = max(0f, -h); val right = max(0f, h)
        return floatArrayOf(
            top, left,           // in alto a sinistra
            1f - top, right,     // in alto a destra
            1f - bottom, 1f - right, // in basso a destra
            bottom, 1f - left,   // in basso a sinistra
        )
    }

    /** Sposta l'angolo [corner] (0..3) di (dx, dy) in quote della foto, senza farlo uscire né incrociare gli altri. */
    fun movedCorner(corner: Int, dx: Float, dy: Float): Perspective {
        val q = quad().toMutableList()
        val x = (q[corner * 2] + dx).coerceIn(0f, 1f)
        val y = (q[corner * 2 + 1] + dy).coerceIn(0f, 1f)
        // Ogni angolo resta nella sua metà, così il quadrilatero non si rovescia.
        q[corner * 2] = if (corner == 0 || corner == 3) min(x, 0.45f) else max(x, 0.55f)
        q[corner * 2 + 1] = if (corner < 2) min(y, 0.45f) else max(y, 0.55f)
        return copy(free = q)
    }

    companion object {
        /** Quanto può stringere un lato al massimo (quota della larghezza o dell'altezza). */
        const val MAX = 0.3f
        val UNIT = listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    }
}

/** Come si riempiono i bordi aggiunti con Espandi. */
enum class ExpandFill { Continue, Blur, White, Black }

/** Espandi: [amount] da 0 a 1 aggiunge su ogni lato fino a un terzo del lato corto della foto. */
data class Expand(val amount: Float = 0f, val fill: ExpandFill = ExpandFill.Continue) {
    fun margin(w: Int, h: Int): Int = (amount.coerceIn(0f, 1f) * min(w, h) / 3f).roundToInt()
}

/**
 * La foto con i bordi aggiunti. «Continua la foto»: i bordi riprendono la foto come in uno specchio e, allontanandosi,
 * si sfumano nei colori medi del bordo, così non si vedono ripetizioni nette. «Sfocato»: lo stesso, tutto sfocato.
 */
fun expandPixels(px: IntArray, w: Int, h: Int, m: Int, fill: ExpandFill): IntArray {
    if (m <= 0) return px.copyOf()
    val nw = w + 2 * m
    val nh = h + 2 * m
    val out = IntArray(nw * nh)
    when (fill) {
        ExpandFill.White -> out.fill(-0x1)
        ExpandFill.Black -> out.fill(-0x1000000)
        ExpandFill.Continue, ExpandFill.Blur -> {
            fun mirror(v: Int, n: Int): Int {
                var x = v
                while (x < 0 || x >= n) x = if (x < 0) -x - 1 else 2 * n - x - 1
                return x
            }
            for (y in 0 until nh) {
                val sy = mirror(y - m, h)
                for (x in 0 until nw) out[y * nw + x] = px[sy * w + mirror(x - m, w)]
            }
            // Versione sfocata per canale, con raggio legato al bordo aggiunto.
            val r = max(2, m / 3)
            val red = FloatArray(out.size) { ((out[it] shr 16) and 0xFF).toFloat() }
            val green = FloatArray(out.size) { ((out[it] shr 8) and 0xFF).toFloat() }
            val blue = FloatArray(out.size) { (out[it] and 0xFF).toFloat() }
            val br = boxBlur(boxBlur(red, nw, nh, r), nw, nh, r)
            val bg = boxBlur(boxBlur(green, nw, nh, r), nw, nh, r)
            val bb = boxBlur(boxBlur(blue, nw, nh, r), nw, nh, r)
            for (y in 0 until nh) for (x in 0 until nw) {
                val inside = x in m until m + w && y in m until m + h
                if (inside) continue
                val i = y * nw + x
                // Distanza dal bordo della foto, da 0 (appena fuori) a 1 (al bordo nuovo).
                val dx = when { x < m -> (m - x).toFloat(); x >= m + w -> (x - m - w + 1).toFloat(); else -> 0f }
                val dy = when { y < m -> (m - y).toFloat(); y >= m + h -> (y - m - h + 1).toFloat(); else -> 0f }
                val t = if (fill == ExpandFill.Blur) 1f else (max(dx, dy) / m).coerceIn(0f, 1f).let { it * (2 - it) }
                val c = out[i]
                val rr = ((c shr 16) and 0xFF) * (1 - t) + br[i] * t
                val gg = ((c shr 8) and 0xFF) * (1 - t) + bg[i] * t
                val bbv = (c and 0xFF) * (1 - t) + bb[i] * t
                out[i] = (0xFF shl 24) or (rr.roundToInt().coerceIn(0, 255) shl 16) or (gg.roundToInt().coerceIn(0, 255) shl 8) or bbv.roundToInt().coerceIn(0, 255)
            }
        }
    }
    for (y in 0 until h) System.arraycopy(px, y * w, out, (y + m) * nw + m, w)
    return out
}
