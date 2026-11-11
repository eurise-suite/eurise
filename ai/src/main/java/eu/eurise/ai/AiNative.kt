package eu.eurise.ai

import org.json.JSONObject

/** Le funzioni del motore nativo (eurise_ai_jni.cpp). Si chiamano da un solo thread alla volta. */
internal object AiNative {
    init { System.loadLibrary("eurise_ai") }
    @JvmStatic external fun backends(libDir: String): String
    @JvmStatic external fun load(path: String, threads: Int, nCtx: Int): Long
    @JvmStatic external fun free(ptr: Long)
    @JvmStatic external fun info(ptr: Long): String
    @JvmStatic external fun threads(ptr: Long, threads: Int)
    @JvmStatic external fun capture(ptr: Long, sentence: String, y: Int, m: Int, d: Int): String
    @JvmStatic external fun ask(ptr: Long, question: String, maxTokens: Int): String
    @JvmStatic external fun bench(ptr: Long, nPrompt: Int, nGen: Int): String
}

/** Una risposta del motore con i suoi tempi. */
data class Answer(val ok: Boolean, val text: String, val nPrompt: Int, val msPrompt: Double, val nGen: Int, val msGen: Double) {
    val promptSpeed get() = if (msPrompt > 0) nPrompt * 1000 / msPrompt else 0.0
    val genSpeed get() = if (msGen > 0) nGen * 1000 / msGen else 0.0

    companion object {
        fun parse(json: String): Answer = JSONObject(json).run {
            Answer(getBoolean("ok"), getString("text"), getInt("n_prompt"), getDouble("ms_prompt"), getInt("n_gen"), getDouble("ms_gen"))
        }
    }
}

/** Il modello caricato. [close] libera la memoria (circa quanto il file del modello). */
class Ai private constructor(private var ptr: Long) : AutoCloseable {
    val description: String
    val bytes: Long
    val loadMs: Long

    init {
        val parts = AiNative.info(ptr).split('|')
        description = parts[0]
        bytes = parts.getOrNull(2)?.toLongOrNull() ?: 0
        loadMs = parts.getOrNull(3)?.toLongOrNull() ?: 0
    }

    @Synchronized fun setThreads(n: Int) = AiNative.threads(ptr, n)

    @Synchronized fun capture(sentence: String, y: Int, m: Int, d: Int) = Answer.parse(AiNative.capture(ptr, sentence, y, m, d))

    @Synchronized fun ask(question: String, maxTokens: Int = 200) = Answer.parse(AiNative.ask(ptr, question, maxTokens))

    @Synchronized fun bench(nPrompt: Int = 256, nGen: Int = 64) = Answer.parse(AiNative.bench(ptr, nPrompt, nGen))

    @Synchronized
    override fun close() {
        if (ptr != 0L) AiNative.free(ptr)
        ptr = 0
    }

    companion object {
        fun load(path: String, threads: Int): Ai? = AiNative.load(path, threads, 2048).takeIf { it != 0L }?.let { Ai(it) }

        /** Come per Whisper: 4 sui telefoni a 8 core (i core grandi), altrimenti uno in meno dei core. */
        fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().let { if (it >= 8) 4 else (it - 1).coerceIn(2, 4) }
    }
}
