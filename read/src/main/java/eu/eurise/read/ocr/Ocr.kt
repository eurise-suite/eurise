package eu.eurise.read.ocr

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/**
 * Motore di lettura del testo: Tesseract compilato dai sorgenti di `third_party` (ponte in `src/main/cpp/eurise_ocr.cpp`).
 * Solo motore LSTM (i modelli tessdata_fast). Un'istanza si usa da un solo thread alla volta; [close] la libera.
 */
class Ocr private constructor(private var handle: Long) : Closeable {

    /** Modo di segmentazione della pagina, con i numeri di Tesseract. */
    enum class PageSeg(val code: Int) { AUTO(3), SINGLE_BLOCK(6), SPARSE_TEXT(11) }

    var pageSeg: PageSeg = PageSeg.AUTO
        set(value) {
            field = value
            nativeSetPageSegMode(live(), value.code)
        }

    /** Limita i caratteri riconosciuti (es. solo cifre e maiuscole per le carte). */
    fun allowOnly(chars: String) {
        nativeSetVariable(live(), "tessedit_char_whitelist", chars)
    }

    /** Legge il testo di [bitmap] (convertita in ARGB_8888 se serve). */
    fun read(bitmap: Bitmap): String {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val text = nativeRead(live(), argb) ?: error("immagine non leggibile")
        if (argb !== bitmap) argb.recycle()
        return text
    }

    /** Fiducia media dell'ultima lettura, da 0 a 100. */
    fun confidence(): Int = nativeConfidence(live())

    /** Libera l'immagine dell'ultima lettura (il modello resta caricato). */
    fun clear() = nativeClear(live())

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0
    }

    private fun live(): Long = handle.also { check(it != 0L) { "motore chiuso" } }

    companion object {
        init {
            System.loadLibrary("eurise_ocr")
        }

        /** Apre il motore con la lingua [lang] (file `<lang>.traineddata` dentro [dataDir]); null se il modello non va. */
        fun open(dataDir: File, lang: String = "ita"): Ocr? =
            nativeOpen(dataDir.absolutePath, lang).takeIf { it != 0L }?.let(::Ocr)

        @JvmStatic private external fun nativeOpen(dataPath: String, lang: String): Long
        @JvmStatic private external fun nativeClose(handle: Long)
        @JvmStatic private external fun nativeSetVariable(handle: Long, name: String, value: String): Boolean
        @JvmStatic private external fun nativeSetPageSegMode(handle: Long, mode: Int)
        @JvmStatic private external fun nativeRead(handle: Long, bitmap: Bitmap): String?
        @JvmStatic private external fun nativeConfidence(handle: Long): Int
        @JvmStatic private external fun nativeClear(handle: Long)
    }
}
