package eu.eurise.keyboard

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ln

/**
 * Quanto è probabile una lettera (o la fine della parola, cioè lo spazio) dopo le due lettere precedenti, imparato dal
 * dizionario della lingua (17 novembre: precisione alta fin dall'inizio, senza aspettare che la tastiera impari).
 * Serve a [KeyModel.chooseWith]: quando un tocco cade tra due tasti, decide la lettera che ha senso lì.
 * Logica pura, senza Android: si prova in `CharModelTest`.
 */
class CharModel private constructor(private val logp: FloatArray) {

    /** Logaritmo della probabilità di [c] dopo [p2] e [p1] (caratteri; [BOUNDARY] per inizio o fine parola). */
    fun logProb(p2: Char, p1: Char, c: Char): Float = logp[(index(p2) * N + index(p1)) * N + index(c)]

    fun write(out: OutputStream) {
        val d = DataOutputStream(out)
        d.writeInt(MAGIC)
        logp.forEach(d::writeFloat)
        d.flush()
    }

    /** Raccoglie le parole del dizionario e alla fine calcola le probabilità ([build]). */
    class Builder {
        private val tri = FloatArray(N * N * N)

        /** [weight]: quanto conta la parola (le più usate di più). */
        fun add(word: String, weight: Float) {
            if (word.isEmpty() || weight <= 0f) return
            var a = B
            var b = B
            for (ch in word) {
                val c = index(ch.lowercaseChar())
                tri[(a * N + b) * N + c] += weight
                a = b; b = c
            }
            tri[(a * N + b) * N + B] += weight
        }

        /**
         * Probabilità con interpolazione: tre lettere, due, una. Così anche una combinazione mai vista ha una
         * probabilità piccola ma non nulla, e una lettera rara non è mai impossibile.
         */
        fun build(): CharModel {
            val bi = FloatArray(N * N)
            val uni = FloatArray(N)
            for (a in 0 until N) for (b in 0 until N) for (c in 0 until N) {
                val v = tri[(a * N + b) * N + c]
                bi[b * N + c] += v
                uni[c] += v
            }
            val uniTot = uni.sum() + N
            val pUni = FloatArray(N) { (uni[it] + 1f) / uniTot }
            val logp = FloatArray(N * N * N)
            for (b in 0 until N) {
                val biTot = (0 until N).sumOf { bi[b * N + it].toDouble() }.toFloat()
                val pBi = FloatArray(N) { c -> (bi[b * N + c] + K_BI * pUni[c]) / (biTot + K_BI) }
                for (a in 0 until N) {
                    val base = (a * N + b) * N
                    var triTot = 0f
                    for (c in 0 until N) triTot += tri[base + c]
                    for (c in 0 until N) {
                        val p = (tri[base + c] + K_TRI * pBi[c]) / (triTot + K_TRI)
                        logp[base + c] = ln(p.toDouble()).toFloat().coerceAtLeast(FLOOR)
                    }
                }
            }
            return CharModel(logp)
        }
    }

    companion object {
        const val BOUNDARY = ' '
        private const val MAGIC = 0x45434D31 // «ECM1»
        private const val LETTERS = "abcdefghijklmnopqrstuvwxyzàèéìòù"
        private val OTHER = LETTERS.length
        private val B = LETTERS.length + 1
        val N = LETTERS.length + 2
        /** Peso delle probabilità più semplici quando quelle più lunghe hanno pochi esempi. */
        private const val K_TRI = 4f
        private const val K_BI = 2f
        /** Nessuna lettera è più improbabile di così: il tocco deve poter sempre vincere. */
        const val FLOOR = -9f

        private fun index(ch: Char): Int = when {
            ch == BOUNDARY -> B
            else -> LETTERS.indexOf(ch).let { if (it >= 0) it else OTHER }
        }

        fun read(input: InputStream): CharModel? = runCatching {
            val d = DataInputStream(input.buffered())
            if (d.readInt() != MAGIC) return null
            CharModel(FloatArray(N * N * N) { d.readFloat() })
        }.getOrNull()

        /** Dal punteggio del dizionario di HeliBoard (0–255, scala logaritmica) al peso di una parola. */
        fun weightOf(probability: Int): Float = Math.pow(2.0, (probability.coerceIn(0, 255) - 255) / 24.0).toFloat()
    }
}
