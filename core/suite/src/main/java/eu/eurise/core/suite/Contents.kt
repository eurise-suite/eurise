package eu.eurise.core.suite

import java.io.File
import java.security.MessageDigest

/**
 * I contenuti pesanti che le app scaricano da fuori (docs/SICUREZZA.md, «Contenuti scaricati»), fissati a una versione
 * precisa e con l'impronta SHA-256 attesa. Finiscono in lettori scritti in C (whisper.cpp, llama.cpp, Tesseract): un file
 * cambiato a monte o per strada potrebbe far eseguire codice nell'app, quindi si usa solo il file con l'impronta giusta.
 *
 * Aggiornare un modello è una scelta: si lancia `impronte-contenuti.yml` (Hugging Face) o si calcola l'impronta del file
 * di GitHub, e si cambiano qui commit e impronta insieme.
 */
object Contents {
    /** Un file a una versione precisa: dove si scarica, quanto pesa, che impronta deve avere. */
    data class Pinned(val url: String, val sha256: String, val size: Long)

    private const val WHISPER = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/"

    /** Modelli di Whisper per Voce, per nome del file. */
    val whisper: Map<String, Pinned> = mapOf(
        "ggml-base-q5_1.bin" to Pinned(WHISPER + "ggml-base-q5_1.bin",
            "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", 59_707_625L),
        "ggml-small-q5_1.bin" to Pinned(WHISPER + "ggml-small-q5_1.bin",
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", 190_085_487L),
    )

    /** I cervelli di Alfred (GGUF Q4_K_M), per repository di Hugging Face. */
    val brain: Map<String, Pinned> = mapOf(
        "ibm-granite/granite-4.0-1b-GGUF" to Pinned(
            "https://huggingface.co/ibm-granite/granite-4.0-1b-GGUF/resolve/b27c2fe3f211b7f44e80fa620177aea371099aaa/granite-4.0-1b-Q4_K_M.gguf",
            "22ec0f9cc99a90185312de3c882c84e7bd6789bdd050389844380a01a831d7f1", 1_023_645_440L),
        "mistralai/Ministral-3-3B-Instruct-2512-GGUF" to Pinned(
            "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/eb599d408350ea2bb60452cb86be7c7b2fc28227/Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            "9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8", 2_147_023_008L),
    )

    private const val TESS = "https://github.com/tesseract-ocr/tessdata_fast/raw/87416418657359cb625c412a48b6e1d6d41c29bd/"

    /** Lingue di Tesseract (tessdata_fast) per Leggi e lo Scanner, per codice di lingua. */
    val tesseract: Map<String, Pinned> = mapOf(
        "ita" to Pinned(TESS + "ita.traineddata", "b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827", 2_701_314L),
        "eng" to Pinned(TESS + "eng.traineddata", "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2", 4_113_088L),
    )

    /** L'impronta SHA-256 di [file], in esadecimale minuscolo (lettura a pezzi, anche per file di qualche GB). */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Vero se [file] è proprio [expected]: stessa lunghezza (controllo rapido) e stessa impronta. */
    fun matches(file: File, expected: Pinned): Boolean =
        file.isFile && file.length() == expected.size && sha256(file).equals(expected.sha256, ignoreCase = true)
}
