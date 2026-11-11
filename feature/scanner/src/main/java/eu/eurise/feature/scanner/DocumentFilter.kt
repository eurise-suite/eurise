package eu.eurise.feature.scanner

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Filtri delle pagine su pixel ARGB, senza grafica di sistema (si provano senza telefono).
 *
 * [document] è il «Documento»: la fotocamera vede la carta tinta dalla luce (azzurrina con le lampade fredde, gialla con
 * quelle calde) e più scura dove c'è ombra. Il filtro stima il colore del foglio zona per zona, divide ogni punto per
 * quel colore (la carta torna bianca e neutra, le ombre spariscono), sistema nero e bianco e porta a grigio puro ciò
 * che è quasi grigio, così il testo è nero e non bluastro. I colori veri (timbri, firme a penna, evidenziatori) restano.
 */
object DocumentFilter {

    /** Colore della carta zona per zona (griglia [gw] × [gh] di celle da [cell] punti), smussato. */
    internal class Paper(val grid: Array<FloatArray>, val gw: Int, val gh: Int, val cell: Int) {
        /** Il colore della carta nel punto (x, y), interpolato tra le zone vicine: niente piani grandi in memoria. */
        fun at(ch: Int, x: Int, y: Int): Float {
            val g = grid[ch]
            val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, gh - 1); val ty = fy - y0
            val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
            val x0 = fx.toInt(); val x1 = min(x0 + 1, gw - 1); val tx = fx - x0
            val a = g[y0 * gw + x0] + (g[y0 * gw + x1] - g[y0 * gw + x0]) * tx
            val b = g[y1 * gw + x0] + (g[y1 * gw + x1] - g[y1 * gw + x0]) * tx
            return a + (b - a) * ty
        }
    }

    internal fun paper(pixels: IntArray, w: Int, h: Int): Paper {
        val cell = max(8, max(w, h) / 24)
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val grid = Array(3) { FloatArray(gw * gh) }
        val hist = Array(3) { IntArray(256) }
        val step = max(1, cell / 12)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            hist.forEach { it.fill(0) }
            var n = 0
            var y = gy * cell
            while (y < min(h, (gy + 1) * cell)) {
                var x = gx * cell
                while (x < min(w, (gx + 1) * cell)) {
                    val c = pixels[y * w + x]
                    hist[0][(c shr 16) and 0xFF]++; hist[1][(c shr 8) and 0xFF]++; hist[2][c and 0xFF]++
                    n++
                    x += step
                }
                y += step
            }
            // Il 90° percentile: la carta, non il testo (che è poco) né i riflessi (pochissimi).
            for (ch in 0..2) grid[ch][gy * gw + gx] = percentile(hist[ch], n, 0.90f).toFloat()
        }
        // Le zone piene di testo o di foto sembrano carta scura: il massimo dei vicini le riporta al foglio, poi si smussa.
        for (ch in 0..2) {
            var g = grid[ch]
            g = neighbours(g, gw, gh, max = true)
            repeat(2) { g = neighbours(g, gw, gh, max = false) }
            grid[ch] = g
        }
        return Paper(grid, gw, gh, cell)
    }

    fun document(pixels: IntArray, w: Int, h: Int): IntArray {
        val bg = paper(pixels, w, h)
        val out = IntArray(pixels.size)
        val black = 0.18f // sotto questo (rispetto alla carta) è inchiostro pieno
        val white = 0.88f // sopra è carta
        for (i in pixels.indices) {
            val c = pixels[i]
            val x = i % w
            val y = i / w
            var r = norm(((c shr 16) and 0xFF) / max(bg.at(0, x, y), 24f), black, white)
            var g = norm(((c shr 8) and 0xFF) / max(bg.at(1, x, y), 24f), black, white)
            var b = norm((c and 0xFF) / max(bg.at(2, x, y), 24f), black, white)
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            // Quasi grigio → grigio puro; colori veri tenuti, un poco più vivi perché non escano spenti.
            val chroma = hi - lo
            val keep = ((chroma - 0.10f) / 0.12f).coerceIn(0f, 1f)
            r = luma + (r - luma) * keep * 1.15f
            g = luma + (g - luma) * keep * 1.15f
            b = luma + (b - luma) * keep * 1.15f
            out[i] = (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
        }
        return out
    }

    /** Scala di grigi dalla pagina pulita: stessa carta bianca, niente colori. */
    fun gray(pixels: IntArray, w: Int, h: Int): IntArray = document(pixels, w, h).also { p ->
        for (i in p.indices) {
            val c = p[i]
            val v = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            p[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    /**
     * Bianco e nero dalla pagina pulita: la carta è già uniforme, quindi basta una soglia locale leggera contro i
     * caratteri sottili (punto più scuro del 12 % della media vicina, o comunque scuro).
     */
    fun blackWhite(pixels: IntArray, w: Int, h: Int): IntArray {
        val clean = gray(pixels, w, h)
        val v = IntArray(clean.size) { clean[it] and 0xFF }
        val sum = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += v[y * w + x]
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row
            }
        }
        val radius = max(w, h) / 50 + 1
        val out = IntArray(clean.size)
        for (y in 0 until h) {
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius).coerceAtMost(h - 1) + 1
            for (x in 0 until w) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius).coerceAtMost(w - 1) + 1
                val count = (right - left) * (bottom - top)
                val total = sum[bottom * (w + 1) + right] - sum[top * (w + 1) + right] - sum[bottom * (w + 1) + left] + sum[top * (w + 1) + left]
                val p = v[y * w + x]
                val dark = p < 110 || (p * count * 100L < total * 88L && p < 200)
                out[y * w + x] = if (dark) 0xFF000000.toInt() else -1
            }
        }
        return out
    }

    private fun percentile(hist: IntArray, n: Int, q: Float): Int {
        if (n == 0) return 255
        val target = (n * q).roundToInt()
        var acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc >= target) return v }
        return 255
    }

    private fun neighbours(g: FloatArray, gw: Int, gh: Int, max: Boolean): FloatArray = FloatArray(g.size) { i ->
        val x = i % gw
        val y = i / gw
        var best = 0f
        var sum = 0f
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx
            val yy = y + dy
            if (xx !in 0 until gw || yy !in 0 until gh) continue
            val v = g[yy * gw + xx]
            if (max) best = max(best, v) else { sum += v; n++ }
        }
        if (max) best else sum / n
    }

    /** Porta il rapporto con la carta tra nero e bianco, con una curva morbida verso il bianco. */
    private fun norm(ratio: Float, black: Float, white: Float): Float {
        val t = ((ratio - black) / (white - black)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t) * 0.35f + t * 0.65f
    }

    private fun byte(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
}
