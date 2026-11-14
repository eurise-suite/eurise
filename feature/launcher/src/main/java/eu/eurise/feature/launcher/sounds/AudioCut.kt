package eu.eurise.feature.launcher.sounds

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Il pezzo di una canzone che l'utente ha sul telefono: decodifica con i codec del sistema (nessuna libreria), riassunto
 * per la forma d'onda e per «Trova il ritornello», taglio con dissolvenze e salvataggio in AAC nella cartella dei suoni.
 * Il brano originale non si tocca; nulla esce dal telefono.
 */
object AudioCut {
    const val FPS = 20

    /** [loud] volume e [rough] «ruvidità» (passaggi per lo zero) per fotogramma di 1/[FPS] di secondo. */
    class Info(val name: String, val durationMs: Long, val loud: FloatArray, val rough: FloatArray)

    suspend fun analyze(context: Context, uri: Uri, progress: (Float) -> Unit = {}): Info? = withContext(Dispatchers.Default) {
        runCatching {
            val loud = ArrayList<Float>()
            val rough = ArrayList<Float>()
            var frameLen = 0
            var acc = 0.0
            var zc = 0
            var count = 0
            var prev = 0
            var duration = 0L
            decode(context, uri, 0, Long.MAX_VALUE, { d -> duration = d }, progress) { sample, rate ->
                if (frameLen == 0) frameLen = rate / FPS
                acc += sample.toDouble() * sample
                if ((sample >= 0) != (prev >= 0)) zc++
                prev = sample
                if (++count == frameLen) {
                    loud += sqrt(acc / count).toFloat() / 32768f
                    rough += zc.toFloat() / count
                    acc = 0.0; zc = 0; count = 0
                }
            }
            if (loud.isEmpty()) return@runCatching null
            Info(displayName(context, uri), duration / 1000, loud.toFloatArray(), rough.toFloatArray())
        }.getOrNull()
    }

    /**
     * Salva il pezzo [startMs]–[endMs] come «nome.m4a» tra i suoni dell'utente. [fadeIn]: il volume sale nei primi due
     * secondi; [seamless]: fine e inizio sfumano, così la suoneria ricomincia senza stacco.
     */
    suspend fun export(context: Context, uri: Uri, startMs: Long, endMs: Long, fadeIn: Boolean, seamless: Boolean, name: String): File? =
        withContext(Dispatchers.Default) {
            runCatching {
                var rate = 44_100
                val pcm = ShortArrayBuilder()
                decode(context, uri, startMs * 1000, endMs * 1000, {}, {}) { s, r -> rate = r; pcm.add(s) }
                val data = pcm.build()
                if (data.isEmpty()) return@runCatching null
                shape(data, rate, fadeIn, seamless)
                val out = File(SoundLibrary.mineDir(context), safe(name) + ".m4a")
                encodeAac(data, rate, out)
                out
            }.getOrNull()
        }

    /** Dissolvenze: entrata lunga (2 s) o breve (30 ms, senza clic), uscita di 1 s o di 30 ms. */
    internal fun shape(data: ShortArray, rate: Int, fadeIn: Boolean, seamless: Boolean) {
        val inLen = minOf(data.size / 3, if (fadeIn) 2 * rate else rate * 3 / 100)
        val outLen = minOf(data.size / 3, if (seamless) rate else rate * 3 / 100)
        for (i in 0 until inLen) data[i] = (data[i] * (i.toFloat() / inLen)).toInt().toShort()
        for (i in 0 until outLen) {
            val k = data.size - 1 - i
            data[k] = (data[k] * (i.toFloat() / outLen)).toInt().toShort()
        }
    }

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.') ?: "Brano"

    private fun safe(name: String) = name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(40).ifEmpty { "Pezzo" }

    /** Campioni mono a 16 bit tra [fromUs] e [toUs], in ordine, con la loro frequenza. */
    private suspend fun decode(
        context: Context, uri: Uri, fromUs: Long, toUs: Long,
        onDuration: (Long) -> Unit, progress: (Float) -> Unit, sink: (Int, Int) -> Unit,
    ) {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(track)
        val format = ex.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        onDuration(durationUs)
        if (fromUs > 0) ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var float = false
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        try {
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > toUs) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val frames = info.size / (channels * if (float) 4 else 2)
                    val frameUs = 1_000_000.0 / rate
                    for (k in 0 until frames) {
                        var sum = 0
                        for (ch in 0 until channels) sum += if (float) (buf.getFloat() * 32767f).toInt() else buf.getShort().toInt()
                        val t = info.presentationTimeUs + (k * frameUs).toLong()
                        if (t in fromUs..toUs) sink((sum / channels).coerceIn(-32768, 32767), rate)
                    }
                    if (durationUs > 0) progress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
    }

    private fun encodeAac(data: ShortArray, rate: Int, out: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var pos = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buf.clear()
                        val n = minOf(data.size - pos, buf.capacity() / 2)
                        val timeUs = pos * 1_000_000L / rate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            for (k in 0 until n) buf.putShort(data[pos + k])
                            pos += n
                            codec.queueInputBuffer(i, 0, n * 2, timeUs, 0)
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && track >= 0) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
        }
    }

    private class ShortArrayBuilder {
        private var data = ShortArray(1 shl 16)
        private var size = 0
        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v.toShort()
        }
        fun build(): ShortArray = data.copyOf(size)
    }

    /** Volume medio di un tratto, per disegnare la forma d'onda con poche barre. */
    fun bars(loud: FloatArray, count: Int): FloatArray {
        if (loud.isEmpty()) return FloatArray(count)
        val max = loud.maxOrNull()?.takeIf { it > 0f } ?: 1f
        return FloatArray(count) { b ->
            val from = b * loud.size / count
            val to = maxOf(from + 1, (b + 1) * loud.size / count)
            var s = 0f
            for (i in from until minOf(to, loud.size)) s += abs(loud[i])
            (s / (to - from) / max).coerceIn(0.05f, 1f)
        }
    }
}
