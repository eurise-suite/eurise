package eu.eurise.voice

/**
 * Quando si è finito di parlare, per l'ascolto senza tasti (Alfred e le altre app della suite): guarda l'energia dei
 * blocchi da 100 ms come [Chunker] e dice quando la voce comincia e quando una pausa vuol dire «ho finito».
 * Senza voce entro [waitMs] si arrende; oltre [maxMs] chiude comunque.
 */
class Endpoint(
    private val rate: Int = 16_000,
    private val endPauseMs: Int = 850,
    private val minSpeechMs: Int = 300,
    private val waitMs: Int = 6_000,
    private val maxMs: Int = 20_000,
) {
    enum class Event { None, Started, Finished, NoSpeech }

    private var floor = -1f
    private var voiced = 0
    private var run = 0
    private var started = false
    private var silentSince = -1
    private var last = 0

    /** Un blocco: [rms] la sua energia, [end] la posizione in campioni dove finisce. */
    fun feed(rms: Float, end: Int): Event {
        val block = end - last
        last = end
        floor = if (floor < 0) minOf(rms, 0.006f) else if (rms < floor) floor * 0.7f + rms * 0.3f else floor * 0.995f + rms * 0.005f
        val speech = rms > maxOf(0.012f, floor * 2.8f)
        if (speech) {
            run += block
            voiced += block
            silentSince = -1
        } else {
            run = 0
            if (silentSince < 0) silentSince = end - block
        }
        if (!started && run >= ms(200)) { started = true; return Event.Started }
        return when {
            !started && end >= ms(waitMs) -> Event.NoSpeech
            end >= ms(maxMs) -> if (started) Event.Finished else Event.NoSpeech
            started && voiced >= ms(minSpeechMs) && silentSince >= 0 && end - silentSince >= ms(endPauseMs) -> Event.Finished
            else -> Event.None
        }
    }

    private fun ms(v: Int) = rate / 1000 * v
}
