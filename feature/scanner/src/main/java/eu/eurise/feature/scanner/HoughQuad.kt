package eu.eurise.feature.scanner

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Il foglio dai suoi bordi: sfocatura leggera, gradiente di Sobel, punti di bordo più forti, rette con la trasformata di
 * Hough (votano solo nella direzione del loro gradiente: meno rumore, meno calcoli), poi la scelta di due rette quasi
 * orizzontali e due quasi verticali. Il punteggio premia i lati davvero coperti da bordi e i quadrilateri grandi.
 * Pochi millisecondi su un'immagine di 200 colonne.
 */
internal object HoughQuad {
    private const val ANGLES = 90 // passi di 2 gradi su 180
    private const val NEAR = 9 // gradi di tolleranza tra gradiente e retta
    private const val MIN_SIDE_SUPPORT = 0.45f

    private data class Line(val theta: Int, val rho: Int, val votes: Int) {
        val angle get() = theta * Math.PI / ANGLES
        /** Retta quasi verticale: la sua normale è quasi orizzontale. */
        val vertical get() = theta < ANGLES / 4 || theta >= ANGLES * 3 / 4
    }

    fun find(luma: IntArray, w: Int, h: Int): Quad? {
        if (w < 16 || h < 16) return null
        val blurred = blur(blur(luma, w, h), w, h)
        val mag = IntArray(w * h)
        val dir = IntArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (blurred[i - w + 1] + 2 * blurred[i + 1] + blurred[i + w + 1]) - (blurred[i - w - 1] + 2 * blurred[i - 1] + blurred[i + w - 1])
            val gy = (blurred[i + w - 1] + 2 * blurred[i + w] + blurred[i + w + 1]) - (blurred[i - w - 1] + 2 * blurred[i - w] + blurred[i - w + 1])
            mag[i] = abs(gx) + abs(gy)
            // Direzione della normale in passi della tabella (0..ANGLES-1, retta e verso opposto coincidono).
            var a = atan2(gy.toDouble(), gx.toDouble())
            if (a < 0) a += Math.PI
            dir[i] = ((a / Math.PI * ANGLES).roundToInt()) % ANGLES
        }
        val threshold = edgeThreshold(mag)
        if (threshold <= 0) return null
        val edge = BooleanArray(w * h) { mag[it] >= threshold }

        val diag = sqrt((w * w + h * h).toDouble()).toInt() + 1
        val acc = IntArray(ANGLES * (2 * diag + 1))
        val cosT = DoubleArray(ANGLES) { cos(it * Math.PI / ANGLES) }
        val sinT = DoubleArray(ANGLES) { sin(it * Math.PI / ANGLES) }
        val near = NEAR * ANGLES / 180
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (!edge[i]) continue
            for (d in -near..near) {
                val t = Math.floorMod(dir[i] + d, ANGLES)
                val rho = (x * cosT[t] + y * sinT[t]).roundToInt()
                acc[t * (2 * diag + 1) + rho + diag]++
            }
        }
        val lines = peaks(acc, diag, minOf(w, h) / 5)
        val vertical = lines.filter { it.vertical }.take(8)
        val horizontal = lines.filterNot { it.vertical }.take(8)

        var best: Quad? = null
        var bestScore = 0.0
        for (a in vertical.indices) for (b in a + 1 until vertical.size) for (c in horizontal.indices) for (d in c + 1 until horizontal.size) {
            val (l, r) = listOf(vertical[a], vertical[b]).sortedBy { xAt(it, h / 2.0) }
            val (t, btm) = listOf(horizontal[c], horizontal[d]).sortedBy { yAt(it, w / 2.0) }
            val tl = cross(l, t) ?: continue
            val tr = cross(r, t) ?: continue
            val br = cross(r, btm) ?: continue
            val bl = cross(l, btm) ?: continue
            val pts = listOf(tl, tr, br, bl)
            // Gli angoli possono uscire appena dall'inquadratura (foglio che la riempie), non di più.
            if (pts.any { it.first < -0.04 * w || it.first > 1.04 * w || it.second < -0.04 * h || it.second > 1.04 * h }) continue
            val quad = Quad(
                Pt((tl.first / w).toFloat().coerceIn(0f, 1f), (tl.second / h).toFloat().coerceIn(0f, 1f)),
                Pt((tr.first / w).toFloat().coerceIn(0f, 1f), (tr.second / h).toFloat().coerceIn(0f, 1f)),
                Pt((br.first / w).toFloat().coerceIn(0f, 1f), (br.second / h).toFloat().coerceIn(0f, 1f)),
                Pt((bl.first / w).toFloat().coerceIn(0f, 1f), (bl.second / h).toFloat().coerceIn(0f, 1f)),
            )
            val area = EdgeDetector.area(quad)
            if (area < 0.15f || area > 0.985f || !convex(pts)) continue
            val support = listOf(tl to tr, tr to br, br to bl, bl to tl).map { (p, q) -> support(edge, w, h, p, q) }
            if (support.minOrNull()!! < MIN_SIDE_SUPPORT * 0.6f || support.average() < MIN_SIDE_SUPPORT) continue
            val score = support.average() * (0.4 + area)
            if (score > bestScore) { bestScore = score; best = quad }
        }
        return best
    }

    /** Soglia dei bordi: i più forti (circa il 7% dei punti), mai sotto un minimo che separa dal rumore. */
    private fun edgeThreshold(mag: IntArray): Int {
        val max = mag.maxOrNull() ?: 0
        if (max < 60) return 0
        val hist = IntArray(max + 1)
        mag.forEach { hist[it]++ }
        var count = 0
        val target = (mag.size * 0.07).toInt()
        for (v in max downTo 0) {
            count += hist[v]
            if (count >= target) return maxOf(v, 60)
        }
        return 60
    }

    private fun peaks(acc: IntArray, diag: Int, minVotes: Int): List<Line> {
        val span = 2 * diag + 1
        val out = mutableListOf<Line>()
        for (t in 0 until ANGLES) for (r in 0 until span) {
            val v = acc[t * span + r]
            if (v < minVotes) continue
            var isMax = true
            loop@ for (dt in -2..2) for (dr in -6..6) {
                if (dt == 0 && dr == 0) continue
                val tt = Math.floorMod(t + dt, ANGLES)
                val rr = r + dr
                if (rr !in 0 until span) continue
                val o = acc[tt * span + rr]
                if (o > v || (o == v && (dt < 0 || (dt == 0 && dr < 0)))) { isMax = false; break@loop }
            }
            if (isMax) out += Line(t, r - diag, v)
        }
        return out.sortedByDescending { it.votes }.take(24)
    }

    private fun xAt(l: Line, y: Double): Double {
        val c = cos(l.angle); val s = sin(l.angle)
        return if (abs(c) < 1e-6) Double.MAX_VALUE else (l.rho - y * s) / c
    }

    private fun yAt(l: Line, x: Double): Double {
        val c = cos(l.angle); val s = sin(l.angle)
        return if (abs(s) < 1e-6) Double.MAX_VALUE else (l.rho - x * c) / s
    }

    private fun cross(a: Line, b: Line): Pair<Double, Double>? {
        val c1 = cos(a.angle); val s1 = sin(a.angle)
        val c2 = cos(b.angle); val s2 = sin(b.angle)
        val det = c1 * s2 - s1 * c2
        if (abs(det) < 0.2) return null // rette quasi parallele
        return (a.rho * s2 - b.rho * s1) / det to (c1 * b.rho - c2 * a.rho) / det
    }

    private fun convex(p: List<Pair<Double, Double>>): Boolean {
        var sign = 0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            val z = (b.first - a.first) * (c.second - b.second) - (b.second - a.second) * (c.first - b.first)
            val s = if (z > 0) 1 else if (z < 0) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Quanta parte del lato da [a] a [b] passa su un bordo (con un punto di tolleranza). */
    private fun support(edge: BooleanArray, w: Int, h: Int, a: Pair<Double, Double>, b: Pair<Double, Double>): Float {
        val length = hypot(b.first - a.first, b.second - a.second)
        val steps = maxOf(8, length.toInt())
        var on = 0
        var total = 0
        for (k in 1 until steps) {
            val x = (a.first + (b.first - a.first) * k / steps).roundToInt()
            val y = (a.second + (b.second - a.second) * k / steps).roundToInt()
            if (x !in 1 until w - 1 || y !in 1 until h - 1) continue // fuori dall'immagine: non conta
            total++
            var hit = false
            for (dy in -1..1) for (dx in -1..1) if (edge[(y + dy) * w + x + dx]) hit = true
            if (hit) on++
        }
        return if (total < steps / 3) 0f else on.toFloat() / total
    }

    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) { sum += src[yy * w + xx]; n++ }
            }
            out[y * w + x] = sum / n
        }
        return out
    }
}
