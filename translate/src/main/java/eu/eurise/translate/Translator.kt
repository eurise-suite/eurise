package eu.eurise.translate

import java.io.Closeable
import java.io.File

/** Un modello di traduzione aperto (una direzione, es. italiano → inglese). Da usare da un solo thread alla volta. */
class Translator private constructor(private var handle: Long) : Closeable {

    fun translate(text: String): String {
        check(handle != 0L) { "motore chiuso" }
        return nativeTranslate(handle, text.toByteArray(Charsets.UTF_8))?.toString(Charsets.UTF_8) ?: error("traduzione non riuscita")
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0
    }

    companion object {
        init {
            System.loadLibrary("eurise_translate")
        }

        fun open(files: Models.Files): Translator? =
            nativeOpen(files.model.path, files.vocab.path, files.targetVocab?.path ?: "", files.shortlist.path).takeIf { it != 0L }?.let(::Translator)

        @JvmStatic private external fun nativeOpen(model: String, vocab: String, tgtVocab: String, shortlist: String): Long
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeTranslate(handle: Long, text: ByteArray): ByteArray?
    }
}

