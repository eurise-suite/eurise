package eu.eurise.feature.launcher.sounds

import kotlin.math.abs

/**
 * «Trova il ritornello»: cerca il pezzo lungo [seconds] che torna più volte nella canzone ed è tra i più pieni.
 * Lavora sui riassunti per fotogramma che [AudioCut.analyze] ricava dal brano (volume e «ruvidità» del suono), senza
 * modelli e senza rete: è una stima, l'utente sposta poi il pezzo dove vuole. Logica pura, provata dai test.
 */
object Chorus {
    /** [loud] e [rough] hanno un valore per fotogramma, [fps] fotogrammi al secondo. Ritorna il fotogramma d'inizio. */
    fun find(loud: FloatArray, rough: FloatArray, fps: Int, seconds: Int): Int {
        val n = loud.size
        val len = seconds * fps
        if (n <= len + fps) return 0
        val l = normalize(loud)
        val r = normalize(rough)
        val step = maxOf(1, fps / 2)
        val stride = 2
        val starts = (0..n - len step step).toList()
        val mean = FloatArray(starts.size) { k -> avg(l, starts[k], len) }
        val maxMean = mean.maxOrNull()?.takeIf { it > 0f } ?: 1f
        var best = 0
        var bestScore = -1f
        for ((a, i) in starts.withIndex()) {
            var rep = 0f
            for ((b, j) in starts.withIndex()) {
                if (abs(i - j) < len || b == a) continue
                var diff = 0f
                var count = 0
                var t = 0
                while (t < len) {
                    diff += abs(l[i + t] - l[j + t]) + abs(r[i + t] - r[j + t])
                    count++
                    t += stride
                }
                val sim = 1f - diff / (2f * count)
                if (sim > rep) rep = sim
            }
            // L'inizio della canzone (introduzione) conta un po' meno.
            val intro = if (i < n / 10) 0.9f else 1f
            val score = (0.55f * rep + 0.45f * mean[a] / maxMean) * intro
            if (score > bestScore) { bestScore = score; best = i }
        }
        return best
    }

    private fun avg(x: FloatArray, from: Int, len: Int): Float {
        var s = 0f
        for (t in from until from + len) s += x[t]
        return s / len
    }

    private fun normalize(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        val min = x.minOrNull() ?: 0f
        val span = (max - min).takeIf { it > 1e-6f } ?: return FloatArray(x.size)
        return FloatArray(x.size) { (x[it] - min) / span }
    }
}
