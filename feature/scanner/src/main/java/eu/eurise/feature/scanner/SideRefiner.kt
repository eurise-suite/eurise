package eu.eurise.feature.scanner

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rifinisce i lati del foglio su un'immagine più grande di quella usata per trovarlo. Per ogni lato si prendono molti
 * punti lungo il lato grezzo; da ciascuno si cerca, lungo la perpendicolare, il salto di luminosità più forte (il bordo
 * vero); con quei punti si calcola la retta migliore (minimi quadrati ortogonali), scartando due volte i punti lontani
 * (ombre, dita, testo vicino al bordo). Gli angoli nuovi sono gli incroci delle quattro rette. Se un lato non trova
 * abbastanza punti, o se un angolo si sposterebbe troppo, si tiene il quadrilatero di partenza.
 */
object SideRefiner {
    private const val SAMPLES = 48
    private const val MIN_POINTS = 14

    /** [luma]: [w]×[h] valori 0..255; [quad] in frazioni dell'immagine. */
    fun refine(luma: IntArray, w: Int, h: Int, quad: Quad): Quad {
        if (w < 32 || h < 32) return quad
        val corners = quad.points.map { it.x.toDouble() * w to it.y.toDouble() * h }
        val reach = maxOf(4.0, hypot(w.toDouble(), h.toDouble()) * 0.025)
        val lines = (0 until 4).map { i ->
            fit(luma, w, h, corners[i], corners[(i + 1) % 4], reach) ?: return quad
        }
        val out = (0 until 4).map { i -> cross(lines[(i + 3) % 4], lines[i]) ?: return quad }
        val limit = reach * 1.6
        for (i in 0 until 4) if (hypot(out[i].first - corners[i].first, out[i].second - corners[i].second) > limit) return quad
        val pts = out.map { Pt((it.first / w).toFloat().coerceIn(0f, 1f), (it.second / h).toFloat().coerceIn(0f, 1f)) }
        return Quad(pts[0], pts[1], pts[2], pts[3])
    }

    /** Retta in forma normale: n·p = c, con n unitario. */
    private data class Line(val nx: Double, val ny: Double, val c: Double)

    private fun fit(luma: IntArray, w: Int, h: Int, a: Pair<Double, Double>, b: Pair<Double, Double>, reach: Double): Line? {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len < 8) return null
        val nx = -dy / len
        val ny = dx / len
        val points = ArrayList<Pair<Double, Double>>()
        for (k in 0 until SAMPLES) {
            // Si lasciano fuori le estremità: vicino agli angoli i bordi dei due lati si confondono.
            val t = 0.1 + 0.8 * k / (SAMPLES - 1)
            val px = a.first + dx * t
            val py = a.second + dy * t
            var bestStep = 0.0
            var bestOff = 0.0
            var off = -reach
            while (off <= reach) {
                val g = abs(sample(luma, w, h, px + nx * (off + 1), py + ny * (off + 1)) - sample(luma, w, h, px + nx * (off - 1), py + ny * (off - 1)))
                if (g > bestStep) { bestStep = g; bestOff = off }
                off += 0.5
            }
            if (bestStep >= 14) points += (px + nx * bestOff) to (py + ny * bestOff)
        }
        if (points.size < MIN_POINTS) return null
        var line = tls(points) ?: return null
        repeat(2) {
            val dists = points.map { abs(line.nx * it.first + line.ny * it.second - line.c) }
            val sorted = dists.sorted()
            val keep = maxOf(1.5, sorted[sorted.size / 2] * 2.5)
            val inliers = points.filterIndexed { i, _ -> dists[i] <= keep }
            if (inliers.size < MIN_POINTS) return line
            line = tls(inliers) ?: return line
        }
        return line
    }

    /** Minimi quadrati ortogonali: la retta che passa per il baricentro lungo l'asse principale dei punti. */
    private fun tls(p: List<Pair<Double, Double>>): Line? {
        val mx = p.sumOf { it.first } / p.size
        val my = p.sumOf { it.second } / p.size
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for ((x, y) in p) { val u = x - mx; val v = y - my; sxx += u * u; syy += v * v; sxy += u * v }
        // Autovettore dell'autovalore minore della covarianza = normale della retta.
        val tr = sxx + syy
        val det = sxx * syy - sxy * sxy
        val lMin = tr / 2 - sqrt(maxOf(0.0, tr * tr / 4 - det))
        var nx = sxy
        var ny = lMin - sxx
        if (abs(nx) < 1e-9 && abs(ny) < 1e-9) { nx = lMin - syy; ny = sxy }
        val n = hypot(nx, ny)
        if (n < 1e-9) return null
        nx /= n; ny /= n
        return Line(nx, ny, nx * mx + ny * my)
    }

    private fun cross(a: Line, b: Line): Pair<Double, Double>? {
        val det = a.nx * b.ny - a.ny * b.nx
        if (abs(det) < 0.2) return null
        return (a.c * b.ny - a.ny * b.c) / det to (a.nx * b.c - a.c * b.nx) / det
    }

    /** Luminosità con interpolazione bilineare; fuori dall'immagine vale il bordo più vicino. */
    private fun sample(luma: IntArray, w: Int, h: Int, x: Double, y: Double): Double {
        val cx = x.coerceIn(0.0, w - 1.001)
        val cy = y.coerceIn(0.0, h - 1.001)
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val fx = cx - x0; val fy = cy - y0
        val i = y0 * w + x0
        val top = luma[i] * (1 - fx) + luma[i + 1] * fx
        val bottom = luma[i + w] * (1 - fx) + luma[i + w + 1] * fx
        return top * (1 - fy) + bottom * fy
    }
}

/**
 * Quando scattare da soli (come kDrive): il foglio è trovato, gli angoli non si muovono e il telefono è fermo per
 * [stillFrames] fotogrammi di fila. Dopo uno scatto non si scatta più finché la scena non cambia (foglio tolto o
 * spostato, mano davanti): così non partono foto a raffica dello stesso foglio. [rearm] riarma subito (Rifai).
 * Logica pura, provata dai test.
 */
class AutoShot(
    private val stillFrames: Int = 10,
    private val quadTolerance: Float = 0.018f,
    private val motionTolerance: Float = 5f,
) {
    private var last: Quad? = null
    private var shotQuad: Quad? = null
    private var disturbed = 0
    var still = 0
        private set
    var armed = true
        private set

    /** Un fotogramma analizzato: [quad] trovato (o null), [motion] differenza media di luminosità col precedente. Vero = scatta. */
    @Synchronized
    fun onFrame(quad: Quad?, motion: Float): Boolean {
        val previous = last
        last = quad
        if (!armed) {
            val shot = shotQuad
            val changed = quad == null || motion > motionTolerance * 3 || (shot != null && quad.distance(shot) > 0.06f)
            disturbed = if (changed) disturbed + 1 else 0
            if (disturbed >= 3) { armed = true; still = 0; disturbed = 0 }
            return false
        }
        still = if (quad != null && previous != null && quad.distance(previous) < quadTolerance && motion < motionTolerance) still + 1 else 0
        if (still >= stillFrames) {
            still = 0
            armed = false
            shotQuad = quad
            disturbed = 0
            return true
        }
        return false
    }

    /** Dopo uno scatto a mano: come uno automatico, si aspetta che cambi il foglio. */
    @Synchronized
    fun shotByHand(quad: Quad?) { armed = false; shotQuad = quad; still = 0; disturbed = 0 }

    /** Rifai: si torna a scattare sullo stesso foglio. */
    @Synchronized
    fun rearm() { armed = true; still = 0; disturbed = 0 }
}
