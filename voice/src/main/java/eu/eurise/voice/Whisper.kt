package eu.eurise.voice

import java.io.File

/** Le funzioni del motore nativo (eurise_whisper.c). */
internal object WhisperNative {
    init { System.loadLibrary("eurise_whisper") }
    @JvmStatic external fun init(path: String): Long
    @JvmStatic external fun free(ptr: Long)
    @JvmStatic external fun transcribe(ptr: Long, threads: Int, audio: FloatArray, language: String, prompt: String, audioCtx: Int): String
}

/**
 * Un modello di Whisper caricato in memoria. Si usa da un thread alla volta; [close] libera la memoria (centinaia di MB),
 * e si chiama appena finita la dettatura.
 */
class Whisper private constructor(private var ptr: Long) : AutoCloseable {

    /**
     * Trascrive l'audio (16 kHz, mono, valori tra -1 e 1) nella lingua indicata ("it", "en" o "auto"). [prompt] è il
     * testo detto prima (fa da contesto); [threads] di base quelli misurati come più veloci su questo telefono.
     */
    @Synchronized
    fun transcribe(audio: FloatArray, language: String, prompt: String = "", threads: Int = Whisper.threads(), fullContext: Boolean = false): String {
        check(ptr != 0L)
        val ctx = if (fullContext) 0 else Chunker.audioCtx(audio.size)
        return WhisperNative.transcribe(ptr, threads, audio, language, prompt, ctx).trim()
    }

    @Synchronized
    override fun close() {
        if (ptr != 0L) WhisperNative.free(ptr)
        ptr = 0
    }

    companion object {
        fun load(file: File): Whisper? = WhisperNative.init(file.absolutePath).takeIf { it != 0L }?.let { Whisper(it) }

        /** Scelti dalla misura (vedi [Speed]); senza misura, 4 sui telefoni a 8 core (i core grandi), altrimenti meno. */
        @Volatile var measured = 0

        fun threads(): Int = measured.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors().let { if (it >= 8) 4 else (it - 1).coerceIn(2, 4) }
    }
}
