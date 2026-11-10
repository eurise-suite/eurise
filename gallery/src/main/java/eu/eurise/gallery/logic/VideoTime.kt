package eu.eurise.gallery.logic

/** Tempo del lettore: 0:12, 4:05, 1:02:33. */
fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Le velocità del lettore, a giro: 1×, 1,5×, 2×, 0,5×. */
val Speeds = listOf(1f, 1.5f, 2f, 0.5f)

fun nextSpeed(current: Float): Float = Speeds[(Speeds.indexOf(current).coerceAtLeast(0) + 1) % Speeds.size]

fun speedLabel(speed: Float): String = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"

/** Il punto in cui saltare: [deltaMs] avanti o indietro, sempre dentro il video. */
fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long =
    (positionMs + deltaMs).coerceIn(0, durationMs.coerceAtLeast(0))

/**
 * Il tratto da tenere tagliando, in frazioni del video: le due maniglie non si scavalcano e restano lontane
 * almeno [minMs] (un secondo), così il taglio non è mai vuoto.
 */
data class Trim(val start: Float = 0f, val end: Float = 1f) {
    val isFull: Boolean get() = start <= 0f && end >= 1f
    fun startMs(durationMs: Long) = (start * durationMs).toLong()
    fun endMs(durationMs: Long) = (end * durationMs).toLong()
}

fun moveTrim(trim: Trim, handleStart: Boolean, to: Float, durationMs: Long, minMs: Long = 1000): Trim {
    val gap = if (durationMs > 0) (minMs.toFloat() / durationMs).coerceAtMost(1f) else 1f
    return if (handleStart) trim.copy(start = to.coerceIn(0f, (trim.end - gap).coerceAtLeast(0f)))
    else trim.copy(end = to.coerceIn((trim.start + gap).coerceAtMost(1f), 1f))
}
