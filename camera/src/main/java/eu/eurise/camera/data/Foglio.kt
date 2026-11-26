package eu.eurise.camera.data

import kotlin.math.abs
import kotlin.math.hypot

/**
 * C'è un foglio nel mirino? (26 novembre 2026, richiesta di Sun: «riconoscimento automatico del documento e tastino che
 * compare per aprire lo scanner»). Un controllo leggero su un'immagine piccola (circa 160 pixel di lato): la zona chiara
 * più grande, se ha la forma di un quadrilatero pieno e non è il muro intero. Il ritaglio preciso lo fa poi lo Scanner.
 */
object Foglio {
    /** I quattro angoli in frazioni dell'immagine (0..1): alto sinistra, alto destra, basso destra, basso sinistra. */
    data class Quad(val x: FloatArray, val y: FloatArray) {
        fun distance(o: Quad): Float = (0 until 4).maxOf { hypot(x[it] - o.x[it], y[it] - o.y[it]) }
        override fun equals(other: Any?) = other is Quad && x.contentEquals(other.x) && y.contentEquals(other.y)
        override fun hashCode() = x.contentHashCode() * 31 + y.contentHashCode()
    }

    private const val MIN_AREA = 0.12f
    private const val MAX_AREA = 0.9f
    private const val MIN_FILL = 0.86f
    private const val MIN_CONTRAST = 45

    /** [luma] riga per riga, [w]×[h] valori 0-255. Null se non sembra un foglio. */
    fun find(luma: IntArray, w: Int, h: Int): Quad? {
        if (w < 16 || h < 16) return null
        val t = otsu(luma)
        // Il foglio deve staccarsi dal fondo: chiaro contro scuro, non due grigi simili.
        var lo = 0L; var nlo = 0; var hi = 0L; var nhi = 0
        for (v in luma) if (v > t) { hi += v; nhi++ } else { lo += v; nlo++ }
        if (nlo == 0 || nhi == 0 || hi / nhi - lo / nlo < MIN_CONTRAST) return null
        // La zona chiara più grande (4 vicini).
        val label = IntArray(w * h)
        val stack = IntArray(w * h)
        var best = 0; var bestId = 0; var id = 0
        for (start in luma.indices) {
            if (luma[start] <= t || label[start] != 0) continue
            id++
            var top = 0; var n = 0
            stack[top++] = start; label[start] = id
            while (top > 0) {
                val p = stack[--top]; n++
                val x = p % w; val y = p / w
                if (x > 0 && luma[p - 1] > t && label[p - 1] == 0) { label[p - 1] = id; stack[top++] = p - 1 }
                if (x < w - 1 && luma[p + 1] > t && label[p + 1] == 0) { label[p + 1] = id; stack[top++] = p + 1 }
                if (y > 0 && luma[p - w] > t && label[p - w] == 0) { label[p - w] = id; stack[top++] = p - w }
                if (y < h - 1 && luma[p + w] > t && label[p + w] == 0) { label[p + w] = id; stack[top++] = p + w }
            }
            if (n > best) { best = n; bestId = id }
        }
        val area = best.toFloat() / (w * h)
        if (area < MIN_AREA || area > MAX_AREA) return null
        // Angoli dalle proiezioni estreme. Il foglio deve stare tutto nel mirino: una zona che tocca un bordo è un muro,
        // un'anta, il cielo (provato sulle foto di Sun: senza questa regola 3 falsi su 8), e lo Scanner vuole il foglio intero.
        var tl = -1; var tr = -1; var br = -1; var bl = -1
        var sMin = Int.MAX_VALUE; var sMax = Int.MIN_VALUE; var dMin = Int.MAX_VALUE; var dMax = Int.MIN_VALUE
        var left = false; var right = false; var up = false; var down = false
        for (p in label.indices) {
            if (label[p] != bestId) continue
            val x = p % w; val y = p / w
            if (x + y < sMin) { sMin = x + y; tl = p }
            if (x + y > sMax) { sMax = x + y; br = p }
            if (x - y > dMax) { dMax = x - y; tr = p }
            if (x - y < dMin) { dMin = x - y; bl = p }
            if (x == 0) left = true; if (x == w - 1) right = true
            if (y == 0) up = true; if (y == h - 1) down = true
        }
        if (left || right || up || down) return null
        val xs = intArrayOf(tl % w, tr % w, br % w, bl % w)
        val ys = intArrayOf(tl / w, tr / w, br / w, bl / w)
        var quad = 0.0
        for (i in 0 until 4) { val j = (i + 1) % 4; quad += xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i] }
        quad = abs(quad) / 2
        if (quad <= 0) return null
        val fill = best / quad
        if (fill < MIN_FILL || fill > 1.15) return null
        // Lati non troppo corti (niente triangoli schiacciati).
        val short = minOf(w, h) * 0.2
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            if (hypot((xs[i] - xs[j]).toDouble(), (ys[i] - ys[j]).toDouble()) < short) return null
        }
        return Quad(
            FloatArray(4) { (xs[it] + 0.5f) / w },
            FloatArray(4) { (ys[it] + 0.5f) / h },
        )
    }

    internal fun otsu(luma: IntArray): Int {
        val hist = IntArray(256)
        for (v in luma) hist[v.coerceIn(0, 255)]++
        val total = luma.size.toDouble()
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var t = 127
        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i }
        }
        return t
    }
}
