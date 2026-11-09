package eu.eurise.feature.scanner

import kotlin.math.abs
import kotlin.math.hypot

/** Un punto in frazioni dell'immagine (0..1). */
data class Pt(val x: Float, val y: Float)

/** I quattro angoli del foglio: in alto a sinistra, in alto a destra, in basso a destra, in basso a sinistra. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points get() = listOf(tl, tr, br, bl)

    /** Quanto si è spostato l'angolo che si è mosso di più: serve a capire se l'inquadratura è ferma. */
    fun distance(other: Quad): Float = points.zip(other.points).maxOf { (a, b) -> hypot(a.x - b.x, a.y - b.y) }
}

/**
 * Trova un foglio chiaro su un fondo più scuro, senza librerie: soglia automatica (Otsu) sulla luminosità ridotta,
 * zona chiara più grande, angoli dagli estremi di x+y e x−y. Funziona per fogli su tavoli; su fondi chiari non trova
 * nulla e resta lo scatto manuale.
 */
object EdgeDetector {
    private const val MIN_AREA = 0.20f
    private const val MAX_AREA = 0.97f

    /** [luma]: una riga dopo l'altra, [width]×[height] valori da 0 a 255. */
    fun find(luma: IntArray, width: Int, height: Int): Quad? {
        val threshold = otsu(luma)
        val seen = BooleanArray(width * height)
        val stack = IntArray(width * height)
        var best: IntArray? = null
        var bestSize = 0
        for (start in luma.indices) {
            if (seen[start] || luma[start] <= threshold) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var size = 0
            // Estremi: minimo e massimo di x+y e di x−y danno i quattro angoli anche se il foglio è inclinato.
            var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE; var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
            var tl = 0; var br = 0; var bl = 0; var tr = 0
            while (top > 0) {
                val at = stack[--top]
                size++
                val x = at % width
                val y = at / width
                val sum = x + y
                val diff = x - y
                if (sum < minSum) { minSum = sum; tl = at }
                if (sum > maxSum) { maxSum = sum; br = at }
                if (diff < minDiff) { minDiff = diff; bl = at }
                if (diff > maxDiff) { maxDiff = diff; tr = at }
                if (x > 0) push(at - 1, luma, threshold, seen, stack, top).also { top = it }
                if (x < width - 1) push(at + 1, luma, threshold, seen, stack, top).also { top = it }
                if (y > 0) push(at - width, luma, threshold, seen, stack, top).also { top = it }
                if (y < height - 1) push(at + width, luma, threshold, seen, stack, top).also { top = it }
            }
            if (size > bestSize) { bestSize = size; best = intArrayOf(tl, tr, br, bl) }
        }
        val corners = best ?: return null
        val quad = corners.map { Pt((it % width + 0.5f) / width, (it / width + 0.5f) / height) }.let { Quad(it[0], it[1], it[2], it[3]) }
        val total = area(quad)
        if (total < MIN_AREA || total > MAX_AREA) return null
        // Un foglio riempie quasi tutta la figura definita dagli angoli: una macchia irregolare no.
        if (bestSize.toFloat() / (width * height) < total * 0.8f) return null
        return quad
    }

    private fun push(next: Int, luma: IntArray, threshold: Int, seen: BooleanArray, stack: IntArray, top: Int): Int {
        if (seen[next] || luma[next] <= threshold) return top
        seen[next] = true
        stack[top] = next
        return top + 1
    }

    /** Formula dell'area di Gauss. */
    fun area(q: Quad): Float {
        val p = q.points
        var sum = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }

    internal fun otsu(luma: IntArray): Int {
        val histogram = IntArray(256)
        luma.forEach { histogram[it.coerceIn(0, 255)]++ }
        val total = luma.size
        val weighted = (0..255).sumOf { it.toLong() * histogram[it] }
        var sumBack = 0L
        var weightBack = 0
        var bestVar = -1.0
        var best = 127
        for (t in 0..255) {
            weightBack += histogram[t]
            if (weightBack == 0) continue
            val weightFore = total - weightBack
            if (weightFore == 0) break
            sumBack += t.toLong() * histogram[t]
            val meanBack = sumBack.toDouble() / weightBack
            val meanFore = (weighted - sumBack).toDouble() / weightFore
            val between = weightBack.toDouble() * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
            if (between > bestVar) { bestVar = between; best = t }
        }
        return best
    }
}
