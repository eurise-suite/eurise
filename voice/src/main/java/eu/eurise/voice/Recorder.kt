package eu.eurise.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

/**
 * Registra dal microfono a 16 kHz mono (il formato di Whisper), al massimo [maxSeconds] secondi. L'audio resta in memoria
 * e si butta subito dopo la trascrizione: non si salva mai su disco.
 */
class Recorder(private val maxSeconds: Int = 90) {
    @Volatile private var running = false
    @Volatile var level = 0f
        private set

    /** Registra finché non si chiama [stop] (dal thread che chiama questa funzione, bloccante). */
    @SuppressLint("MissingPermission")
    fun record(): FloatArray {
        val rate = SAMPLE_RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        val chunk = ShortArray(rate / 10)
        val out = FloatArray(rate * maxSeconds)
        var size = 0
        running = true
        try {
            record.startRecording()
            while (running && size < out.size) {
                val n = record.read(chunk, 0, chunk.size)
                if (n <= 0) break
                var peak = 0
                for (i in 0 until n) {
                    if (size >= out.size) break
                    out[size++] = chunk[i] / 32768f
                    peak = maxOf(peak, abs(chunk[i].toInt()))
                }
                level = peak / 32768f
            }
        } finally {
            running = false
            runCatching { record.stop() }
            record.release()
            level = 0f
        }
        return out.copyOf(size)
    }

    fun stop() { running = false }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
