package eu.eurise.voice

import java.io.File

/** Le funzioni del motore nativo (eurise_whisper.c). */
internal object WhisperNative {
    init { System.loadLibrary("eurise_whisper") }
    @JvmStatic external fun init(path: String): Long
    @JvmStatic external fun free(ptr: Long)
    @JvmStatic external fun transcribe(ptr: Long, threads: Int, audio: FloatArray, language: String): String
}

/**
 * Un modello di Whisper caricato in memoria. Si usa da un thread alla volta; [close] libera la memoria (centinaia di MB),
 * e si chiama appena finita la dettatura.
 */
class Whisper private constructor(private var ptr: Long) : AutoCloseable {

    /** Trascrive l'audio (16 kHz, mono, valori tra -1 e 1) nella lingua indicata ("it", "en" o "auto"). */
    @Synchronized
    fun transcribe(audio: FloatArray, language: String): String {
        check(ptr != 0L)
        return WhisperNative.transcribe(ptr, threads(), audio, language).trim()
    }

    @Synchronized
    override fun close() {
        if (ptr != 0L) WhisperNative.free(ptr)
        ptr = 0
    }

    companion object {
        fun load(file: File): Whisper? = WhisperNative.init(file.absolutePath).takeIf { it != 0L }?.let { Whisper(it) }

        /** Tutti i core grandi tranne uno lasciato al resto del telefono; almeno due. */
        fun threads() = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
    }
}
