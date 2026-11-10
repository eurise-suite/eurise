package eu.eurise.keyboard

import java.text.Normalizer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Riconoscitore libero della scrittura scorrendo il dito, scritto per Eurise (nessuna libreria esterna).
 *
 * Confronta la traccia del dito con il percorso ideale di ogni parola del dizionario, sui centri dei tasti:
 * 1. si scartano le parole che non cominciano e non finiscono vicino ai punti di inizio e fine della traccia;
 * 2. per le rimaste si misurano la distanza dei punti (dove passa il dito), la forma (come si muove, indipendentemente
 *    da dove) e la copertura (il dito deve passare, in ordine, vicino a ogni lettera);
 * 3. si aggiunge quanto la parola è comune. Vince il costo più basso.
 * È l'idea pubblicata da Kristensson e Zhai (SHARK²), semplificata.
 */
class GlideDecoder {

    private var centers: Map<Char, Pair<Float, Float>> = emptyMap()
    private var keyWidth = 1f

    class Entry(val word: String, val letters: String, val frequency: Int)

    private val byFirst = HashMap<Char, MutableList<Entry>>()

    /** Carica le parole (con la loro frequenza, 0–255 come nei dizionari della tastiera). */
    @Synchronized
    fun setLexicon(words: Sequence<Pair<String, Int>>) {
        byFirst.clear()
        words.forEach { (word, freq) ->
            val letters = baseLetters(word)
            if (letters.length < 2) return@forEach
            byFirst.getOrPut(letters[0]) { mutableListOf() }.add(Entry(word, letters, freq))
        }
    }

    val size: Int get() = byFirst.values.sumOf { it.size }

    data class Candidate(val word: String, val cost: Float)

    /**
     * Le parole più probabili per la traccia ([xs], [ys], [count] punti), al massimo [limit].
     * [keys] sono i centri dei tasti delle lettere sulla tastiera di adesso, [keyWidth] la larghezza di un tasto.
     */
    @Synchronized
    fun decode(keys: Map<Char, Pair<Float, Float>>, keyWidth: Float, xs: IntArray, ys: IntArray, count: Int, limit: Int = 8): List<Candidate> {
        centers = keys.mapKeys { it.key.lowercaseChar() }
        this.keyWidth = keyWidth
        if (count < 2 || centers.isEmpty()) return emptyList()
        val trace = resample(FloatArray(count) { xs[it].toFloat() }, FloatArray(count) { ys[it].toFloat() }, N)
        val traceLength = length(trace.first, trace.second)
        if (traceLength < keyWidth * 0.6f) return emptyList()

        val starts = near(trace.first[0], trace.second[0])
        val ends = near(trace.first[N - 1], trace.second[N - 1]).toSet()
        val traceShape = normalized(trace.first, trace.second)
        val out = ArrayList<Candidate>()
        for (s in starts) {
            val list = byFirst[s] ?: continue
            for (e in list) {
                if (e.letters.last() !in ends || e.letters.any { it !in centers }) continue
                val tx = FloatArray(e.letters.length); val ty = FloatArray(e.letters.length)
                var n = 0
                var prev = ' '
                for (c in e.letters) {
                    if (c == prev) continue
                    val p = centers.getValue(c)
                    tx[n] = p.first; ty[n] = p.second; n++; prev = c
                }
                if (n < 2) continue
                val templateLength = length(tx, ty, n)
                val ratio = templateLength / traceLength
                if (ratio < 0.45f || ratio > 2.2f) continue
                val cover = coverage(tx, ty, n, trace.first, trace.second) / n
                if (cover > MAX_COVER) continue
                val template = resample(tx.copyOf(n), ty.copyOf(n), N)
                val location = meanDistance(template.first, template.second, trace.first, trace.second) / keyWidth
                val shape = normalized(template.first, template.second).let { meanDistance(it.first, it.second, traceShape.first, traceShape.second) }
                val extra = unexplained(tx, ty, n, trace.first, trace.second)
                val common = ln(1f + e.frequency) / ln(256f)
                val cost = location * W_LOCATION + shape * W_SHAPE + cover * W_COVER + extra * W_EXTRA - common * W_FREQUENCY
                out += Candidate(e.word, cost)
            }
        }
        out.sortBy { it.cost }
        return out.distinctBy { it.word.lowercase() }.take(limit)
    }

    /** Lettere vicine a un punto: la più vicina sempre, le altre entro un tasto. */
    private fun near(x: Float, y: Float): List<Char> {
        val sorted = centers.entries.sortedBy { hypot(it.value.first - x, it.value.second - y) }
        return sorted.filterIndexed { i, en -> i == 0 || hypot(en.value.first - x, en.value.second - y) <= keyWidth * 1.5f }.map { it.key }
    }

    /**
     * Quanto della traccia la parola non spiega (in tasti): il dito è andato dove la parola non passa.
     * Senza questo una parola corta, con lettere che stanno sul percorso, batterebbe quella lunga giusta.
     */
    private fun unexplained(tx: FloatArray, ty: FloatArray, n: Int, xs: FloatArray, ys: FloatArray): Float {
        var sum = 0f
        for (j in xs.indices) {
            var best = Float.MAX_VALUE
            for (i in 1 until n) best = min(best, segmentDistance(xs[j], ys[j], tx[i - 1], ty[i - 1], tx[i], ty[i]))
            sum += max(0f, best / keyWidth - 0.35f)
        }
        return sum / xs.size
    }

    /** Quanto il dito "manca" le lettere, in ordine (in tasti): 0 se passa vicino a tutte. */
    private fun coverage(tx: FloatArray, ty: FloatArray, n: Int, xs: FloatArray, ys: FloatArray): Float {
        var from = 0
        var missed = 0f
        for (i in 0 until n) {
            var best = Float.MAX_VALUE
            var bestAt = from
            // La lettera dopo deve venire dopo nella traccia. Vale il primo passaggio vicino alla lettera:
            // il dito può ripassarci più avanti per un'altra lettera (in "questo" la t→o passa sopra la u).
            for (j in from until xs.size) {
                val d = hypot(xs[j] - tx[i], ys[j] - ty[i])
                if (d < best) { best = d; bestAt = j }
                else if (best < keyWidth * 1.3f && d > best + keyWidth * 0.6f) break
            }
            from = bestAt
            missed += max(0f, best / keyWidth - 0.55f)
        }
        return missed
    }

    companion object {
        const val N = 48
        private const val MAX_COVER = 0.6f
        private const val W_LOCATION = 1.0f
        private const val W_SHAPE = 1.6f
        private const val W_COVER = 4.0f
        private const val W_EXTRA = 3.0f
        private const val W_FREQUENCY = 0.6f

        fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }

        /** "Perché" → "perche", "l'acqua" → "lacqua": solo lettere, senza accenti, minuscole. */
        fun baseLetters(word: String): String =
            Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD).filter { it in 'a'..'z' }

        fun length(xs: FloatArray, ys: FloatArray, n: Int = xs.size): Float {
            var l = 0f
            for (i in 1 until n) l += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return l
        }

        /** Ricampiona un percorso in [n] punti alla stessa distanza tra loro. */
        fun resample(xs: FloatArray, ys: FloatArray, n: Int): Pair<FloatArray, FloatArray> {
            val ox = FloatArray(n); val oy = FloatArray(n)
            val total = length(xs, ys)
            if (total == 0f || xs.size < 2) { ox.fill(xs[0]); oy.fill(ys[0]); return ox to oy }
            val step = total / (n - 1)
            var seg = 1
            var segStart = 0f
            var segLen = hypot(xs[1] - xs[0], ys[1] - ys[0])
            for (k in 0 until n) {
                val target = min(step * k, total)
                while (segStart + segLen < target && seg < xs.size - 1) {
                    segStart += segLen; seg++
                    segLen = hypot(xs[seg] - xs[seg - 1], ys[seg] - ys[seg - 1])
                }
                val t = if (segLen == 0f) 0f else ((target - segStart) / segLen).coerceIn(0f, 1f)
                ox[k] = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * t
                oy[k] = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * t
            }
            return ox to oy
        }

        /** Sposta il percorso al centro e lo porta a misura 1: resta solo la forma. */
        fun normalized(xs: FloatArray, ys: FloatArray): Pair<FloatArray, FloatArray> {
            val cx = xs.average().toFloat(); val cy = ys.average().toFloat()
            val w = (xs.max() - xs.min()); val h = (ys.max() - ys.min())
            val scale = max(max(w, h), 1e-3f)
            return FloatArray(xs.size) { (xs[it] - cx) / scale } to FloatArray(ys.size) { (ys[it] - cy) / scale }
        }

        fun meanDistance(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
            var s = 0f
            for (i in ax.indices) s += sqrt((ax[i] - bx[i]) * (ax[i] - bx[i]) + (ay[i] - by[i]) * (ay[i] - by[i]))
            return s / ax.size
        }
    }
}
