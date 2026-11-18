package eu.eurise.chat

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import eu.eurise.core.suite.VoiceContract
import java.io.File
import java.nio.ByteOrder
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Registrazione di un vocale (tavola ChatVocali): il microfono a 16 kHz, mono, in memoria. Lo stesso audio serve a
 * mandare il file (AAC, [encode]) e a trascriverlo con Voce senza decodificarlo di nuovo. [level] va da 0 a 1 per la
 * sbarretta che si muove mentre si parla.
 */
class VoiceRecorder {
    @Volatile private var running = false
    private var worker: Thread? = null
    private val samples = java.io.ByteArrayOutputStream()
    @Volatile var level = 0f
        private set
    val startedAt = System.currentTimeMillis()

    /** Vero se il microfono è partito (serve il permesso RECORD_AUDIO, chiesto prima). */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(Voice.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, Voice.RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, Voice.RATE))
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        runCatching { rec.startRecording() }.onFailure { rec.release(); return false }
        running = true
        worker = thread(name = "chat-vocale") {
            val buf = ShortArray(Voice.RATE / 10)
            val bytes = ByteArray(buf.size * 2)
            val max = Voice.RATE * 2L * (Voice.MAX_MS / 1000)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
                    bytes[2 * i] = v.toByte(); bytes[2 * i + 1] = (v shr 8).toByte()
                }
                level = (peak / 12_000f).coerceIn(0f, 1f)
                synchronized(samples) { if (samples.size() < max) samples.write(bytes, 0, n * 2) else running = false }
            }
            runCatching { rec.stop() }
            rec.release()
        }
        return true
    }

    val elapsedMs: Long get() = System.currentTimeMillis() - startedAt

    /** Ferma e restituisce l'audio registrato (vuoto se annullato). */
    fun stop(): ShortArray {
        running = false
        worker?.join(1500)
        val b = synchronized(samples) { samples.toByteArray() }
        val out = ShortArray(b.size / 2)
        java.nio.ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
        return out
    }
}

/** Codifica e decodifica dei vocali con i codec del telefono (nessuna libreria). */
object VoiceCodec {
    const val MIME = "audio/mp4"

    /**
     * Da PCM a 16 kHz a un file .m4a (AAC-LC, 32 kbit/s): si apre in Element, nel browser e in ogni lettore. Restituisce
     * false se il telefono non ha il codificatore.
     */
    fun encode(pcm: ShortArray, out: File): Boolean = runCatching {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Voice.RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bytes = Voice.bytes(pcm)
        var offset = 0
        var inputDone = false
        var track = -1
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf(buf.remaining(), bytes.size - offset)
                        buf.put(bytes, offset, n)
                        val timeUs = offset / 2L * 1_000_000L / Voice.RATE
                        offset += n
                        inputDone = offset >= bytes.size
                        codec.queueInputBuffer(i, 0, n, timeUs, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) {
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
            runCatching { if (track >= 0) muxer.stop() }; runCatching { muxer.release() }
        }
        track >= 0 && out.length() > 0
    }.getOrDefault(false)

    /**
     * Da un file audio qualsiasi (Ogg Opus di Element, AAC nostro, MP3…) al PCM mono a 16 kHz che vuole Whisper; null se
     * il telefono non sa leggerlo. Al massimo [maxMs] di audio.
     */
    fun decode(file: File, maxMs: Long = Voice.MAX_MS): ShortArray? = runCatching {
        val ex = MediaExtractor()
        ex.setDataSource(file.absolutePath)
        val t = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: run { ex.release(); return null }
        ex.selectTrack(t)
        val inFormat = ex.getTrackFormat(t)
        var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(inFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(inFormat, null, null, 0)
        codec.start()
        val out = java.io.ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        val limitUs = maxMs * 1000
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0 || ex.sampleTime > limitUs) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    codec.outputFormat.let { rate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                } else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    if (info.size > 0) {
                        val chunk = ByteArray(info.size)
                        buf.position(info.offset); buf.get(chunk)
                        out.write(chunk)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
        val b = out.toByteArray()
        val pcm = ShortArray(b.size / 2)
        java.nio.ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        Voice.toMono16k(pcm, channels, rate)
    }.getOrNull()
}

/**
 * Trascrive un vocale con Eurise Voce (Whisper sul telefono, [VoiceContract]): pezzi di al massimo 28 secondi, uno alla
 * volta, uniti alla fine. L'audio passa da un tubo e non si salva; il testo resta in memoria.
 */
class VoiceTranscriber(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null

    /** Il pacchetto di Voce installato, o null. */
    val voice: String? get() = VoiceContract.PACKAGES.firstOrNull { pkg ->
        runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    sealed interface Result {
        data class Text(val text: String) : Result
        data object Empty : Result
        data object NoModel : Result
        data object NoVoice : Result
        data object Failed : Result
    }

    /** Trascrive [pcm] (mono, 16 kHz); [done] arriva sul thread principale. */
    fun transcribe(pcm: ShortArray, done: (Result) -> Unit) {
        val parts = Voice.pieces(pcm).filter { it.count() >= Voice.RATE / 4 }
        if (parts.isEmpty()) return done(Result.Empty)
        val texts = ArrayList<String>()
        fun step(i: Int) {
            if (i >= parts.size) {
                done(if (texts.isEmpty()) Result.Empty else Result.Text(texts.joinToString(" ")))
                return
            }
            one(Voice.bytes(pcm, parts[i])) { r ->
                when (r) {
                    is Result.Text -> { texts += r.text; step(i + 1) }
                    Result.Empty -> step(i + 1)
                    else -> done(r)
                }
            }
        }
        main.post { step(0) }
    }

    private fun one(bytes: ByteArray, done: (Result) -> Unit) {
        connect { m ->
            if (m == null) return@connect done(if (voice == null) Result.NoVoice else Result.Failed)
            val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
                val text = msg.data.getString(VoiceContract.KEY_TEXT)
                done(when {
                    !text.isNullOrBlank() -> Result.Text(text.trim())
                    msg.data.getString(VoiceContract.KEY_ERROR) == VoiceContract.ERROR_NO_MODEL -> Result.NoModel
                    msg.data.getString(VoiceContract.KEY_ERROR) == VoiceContract.ERROR_FAILED -> Result.Failed
                    else -> Result.Empty
                })
                true
            })
            val (read, write) = ParcelFileDescriptor.createPipe()
            val msg = Message.obtain(null, VoiceContract.MSG_TRANSCRIBE).apply {
                data = Bundle().apply {
                    putParcelable(VoiceContract.KEY_AUDIO, read)
                    putString(VoiceContract.KEY_LANGUAGE, Locale.getDefault().language)
                }
                replyTo = reply
            }
            val sent = runCatching { m.send(msg) }.isSuccess
            runCatching { read.close() }
            if (!sent) { runCatching { write.close() }; service = null; return@connect done(Result.Failed) }
            thread(name = "chat-vocale-voce") {
                runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) } }
            }
        }
    }

    private fun connect(then: (Messenger?) -> Unit) {
        service?.let { return then(it) }
        val pkg = voice ?: return then(null)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val m = Messenger(binder)
                service = m
                then(m)
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
        connection = conn
        val bound = runCatching { app.bindService(Intent(VoiceContract.ACTION_TRANSCRIBE).setPackage(pkg), conn, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
        if (!bound) { connection = null; then(null) }
    }

    /** Lascia andare Voce (e la memoria del modello) quando la Chat non serve più. */
    fun close() {
        connection?.let { runCatching { app.unbindService(it) } }
        connection = null
        service = null
    }

    /** Apre in Voce la pagina dei modelli. */
    fun modelsIntent(): Intent? = voice?.let { Intent(VoiceContract.ACTION_MODELS).setPackage(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
