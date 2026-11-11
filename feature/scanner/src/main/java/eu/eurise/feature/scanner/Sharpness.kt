package eu.eurise.feature.scanner

import kotlin.math.abs

/**
 * La pagina è mossa? (tavola ScanPagine, «Pag. 1 un po' mossa: Rifai»). Si misura quanto cambiano i punti vicini sui
 * bordi del testo: in una foto nitida le lettere passano da carta a inchiostro in un punto, in una mossa in tanti.
 *
 * [score] è la media dei salti più forti (il 2 % più alto) tra punti vicini, su un'immagine di luminosità larga circa
 * 600 punti: basso = mossa. Una pagina quasi vuota (scarto della luminosità sotto 8) non viene segnalata.
 */
object Sharpness {
    /** Sotto questo il testo piccolo si legge male. Tarato sulle prove: nitida sopra 60, mossa sotto 30. */
    const val BLURRY = 34

    fun score(luma: IntArray, w: Int, h: Int): Int? {
        if (w < 8 || h < 8) return null
        val hist = IntArray(256)
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val d = maxOf(abs(luma[i + 1] - luma[i - 1]), abs(luma[i + w] - luma[i - w])).coerceAtMost(255)
            hist[d]++
            n++
        }
        // Pagina quasi vuota (luminosità quasi uguale ovunque): niente da dire.
        var sum1 = 0.0
        var sum2 = 0.0
        for (v in luma) { sum1 += v; sum2 += v.toDouble() * v }
        val mean = sum1 / luma.size
        if (sum2 / luma.size - mean * mean < 64.0) return null
        val top = maxOf(1, n / 50)
        var taken = 0
        var sum = 0L
        for (v in 255 downTo 0) {
            val k = minOf(hist[v], top - taken)
            sum += k.toLong() * v
            taken += k
            if (taken >= top) break
        }
        return (sum / top).toInt()
    }

    fun isBlurry(luma: IntArray, w: Int, h: Int): Boolean = score(luma, w, h)?.let { it < BLURRY } ?: false
}
