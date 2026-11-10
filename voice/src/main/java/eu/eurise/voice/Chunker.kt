package eu.eurise.voice

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Dettatura a pezzi mentre si parla: guarda l'energia dell'audio a blocchi di 100 ms e propone dove tagliare, cioè
 * nelle pause di almeno [pauseMs] dopo almeno [minSpeechMs] di voce. Così ogni pezzo si trascrive mentre si continua a
 * parlare, e alla fine resta da fare solo l'ultimo. Il rumore di fondo si stima dai primi blocchi e segue l'ambiente.
 */
class Chunker(
    private val rate: Int = 16_000,
    private val minSpeechMs: Int = 2500,
    private val pauseMs: Int = 600,
    private val maxMs: Int = 25_000,
) {
    private var floor = -1f
    private var segmentStart = 0
    private var silentSince = -1
    private var heard = false
    private var voiced = 0

    /**
     * Un blocco di audio: [rms] è la sua energia, [end] la posizione (in campioni) dove finisce. Restituisce la
     * posizione dove tagliare, se è il momento, altrimenti null.
     */
    fun feed(rms: Float, end: Int): Int? {
        // Si parte da un fondo basso (si può cominciare a parlare subito); scende in fretta, sale piano.
        floor = if (floor < 0) minOf(rms, 0.006f) else if (rms < floor) floor * 0.7f + rms * 0.3f else floor * 0.995f + rms * 0.005f
        val speech = rms > maxOf(0.012f, floor * 2.8f)
        if (speech) { heard = true; voiced += block(end); silentSince = -1 } else if (silentSince < 0) silentSince = end
        val length = end - segmentStart
        val pause = if (silentSince >= 0) end - silentSince else 0
        val cut = when {
            !heard -> null
            length >= ms(maxMs) -> end - pause / 2 // pezzo troppo lungo: si taglia comunque, nel punto più quieto noto
            voiced >= ms(minSpeechMs) && pause >= ms(pauseMs) -> silentSince + pause / 2
            else -> null
        }
        if (cut != null) { segmentStart = cut; heard = false; voiced = 0; silentSince = if (speech) -1 else cut }
        last = end
        return cut
    }

    private var last = 0
    private fun block(end: Int) = end - last

    private fun ms(v: Int) = rate / 1000 * v

    companion object {
        fun rms(samples: ShortArray, n: Int): Float {
            if (n <= 0) return 0f
            var sum = 0.0
            for (i in 0 until n) { val v = samples[i] / 32768.0; sum += v * v }
            return sqrt(sum / n).toFloat()
        }

        /**
         * Il contesto audio per un pezzo di [samples] campioni: Whisper lavora su 30 s (1500); per un pezzo più corto
         * basta la sua durata più un margine di 2 s, a multipli di 64. 0 vuol dire "intero".
         */
        fun audioCtx(samples: Int, rate: Int = 16_000): Int {
            val seconds = samples.toDouble() / rate + 2.0
            val ctx = (ceil(seconds * 50 / 64) * 64).toInt().coerceAtLeast(384)
            return if (ctx >= 1500) 0 else ctx
        }
    }
}
