package eu.eurise.feature.notes.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Registra una nota vocale dal microfono: PCM a 16 kHz mono, salvato come AAC in un file .m4a (circa 4 KB al secondo).
 *
 * Mentre registra passa a [onLevel] il volume (0..1) e a [onChunk] i pezzi di parlato, tagliati a una pausa o ogni 12
 * secondi, perché Eurise Voce li trascriva mentre si parla. Tutto resta sul telefono.
 */
class VoiceRecorder(
    private val file: File,
    private val onLevel: (Float) -> Unit,
    private val onChunk: (ByteArray) -> Unit,
) {
    @Volatile var paused = false
    @Volatile private var running = false
    private var worker: Thread? = null

    /** Durata registrata finora, in millisecondi (le pause non contano). */
    @Volatile var durationMs = 0L
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        file.parentFile?.mkdirs()
        running = true
        worker = thread(name = "nota-vocale") {
            val writer = runCatching { AacWriter(file) }.getOrNull()
            val buf = ByteArray(BYTES_PER_MS * 100)
            val chunk = ByteArrayOutputStream()
            var voiced = 0
            var silent = 0
            var written = 0L
            runCatching { rec.startRecording() }
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0 || paused) {
                        if (paused) onLevel(0f)
                        continue
                    }
                    writer?.write(buf, n)
                    written += n
                    durationMs = written / BYTES_PER_MS
                    val level = level(buf, n)
                    onLevel(level)
                    chunk.write(buf, 0, n)
                    if (level > SPEECH) { voiced += 100; silent = 0 } else silent += 100
                    val chunkMs = chunk.size() / BYTES_PER_MS
                    if (voiced >= 1_500 && silent >= 700 || chunkMs >= 12_000) {
                        if (voiced > 0) onChunk(chunk.toByteArray())
                        chunk.reset()
                        voiced = 0
                        silent = 0
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                if (voiced > 0 && chunk.size() > 0) onChunk(chunk.toByteArray())
                runCatching { writer?.finish() }
            }
        }
        return true
    }

    /** Ferma la registrazione e chiude il file. */
    fun stop() {
        running = false
        runCatching { worker?.join(4_000) }
    }

    private fun level(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < n) {
            val s = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
            sum += s.toDouble() * s
            count++
            i += 2
        }
        if (count == 0) return 0f
        return (sqrt(sum / count) / 6_000.0).toFloat().coerceIn(0f, 1f)
    }

    /** Scrive PCM come AAC-LC in un contenitore MP4 (.m4a), con le API di sistema. */
    private class AacWriter(file: File) {
        private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var track = -1
        private var started = false
        private var samples = 0L
        private val info = MediaCodec.BufferInfo()

        init {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }

        fun write(pcm: ByteArray, len: Int) {
            var off = 0
            while (off < len) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i < 0) { drain(false); continue }
                val input = codec.getInputBuffer(i) ?: continue
                input.clear()
                val n = minOf(input.remaining(), len - off)
                input.put(pcm, off, n)
                codec.queueInputBuffer(i, 0, n, samples * 1_000_000L / RATE, 0)
                samples += n / 2
                off += n
                drain(false)
            }
        }

        fun finish() {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) codec.queueInputBuffer(i, 0, 0, samples * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
            codec.stop()
            codec.release()
            if (started) muxer.stop()
            muxer.release()
        }

        private fun drain(end: Boolean) {
            var idle = 0
            while (true) {
                val o = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end || ++idle > 50) return
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    o >= 0 -> {
                        val out = codec.getOutputBuffer(o)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (started && out != null && info.size > 0) muxer.writeSampleData(track, out, info)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    companion object {
        const val RATE = 16_000
        private const val BYTES_PER_MS = RATE * 2 / 1000
        private const val SPEECH = 0.06f
    }
}
