package eu.eurise.voice

import android.content.Context
import kotlin.random.Random

/**
 * Quanti thread usare su questo telefono: si misura una volta (a richiesta) trascrivendo 6 secondi di audio con 2, 4
 * e 6 thread, e si tiene il più veloce. I telefoni hanno core grandi e piccoli: usarli tutti spesso rallenta.
 */
object Speed {
    private fun prefs(context: Context) = context.getSharedPreferences("voice_speed", Context.MODE_PRIVATE)

    fun load(context: Context) { Whisper.measured = prefs(context).getInt("threads", 0) }

    /** I tempi in millisecondi per numero di thread; il più veloce diventa la scelta. */
    fun measure(context: Context, whisper: Whisper): List<Pair<Int, Long>> {
        val cores = Runtime.getRuntime().availableProcessors()
        val audio = FloatArray(16_000 * 6) { (Random.nextFloat() - 0.5f) * 0.01f }
        whisper.transcribe(audio.copyOf(16_000), "it", threads = 4) // riscaldamento: memoria e cache
        val times = listOf(2, 4, 6).filter { it <= cores }.map { n ->
            val start = System.nanoTime()
            whisper.transcribe(audio, "it", threads = n)
            n to (System.nanoTime() - start) / 1_000_000
        }
        times.minByOrNull { it.second }?.let { (best, _) ->
            prefs(context).edit().putInt("threads", best).apply()
            Whisper.measured = best
        }
        return times
    }
}
