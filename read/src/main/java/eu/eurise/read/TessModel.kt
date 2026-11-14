package eu.eurise.read

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Le lingue di Tesseract (tessdata_fast): l'italiano, e a scelta l'inglese, scaricate una volta a richiesta e tenute nei
 * file dell'app. Si scaricano da GitHub (progetto tesseract-ocr); nulla del telefono viene inviato.
 */
internal object TessModel {
    private fun source(lang: String) = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$lang.traineddata"

    /** Cartella dei modelli, da dare a [eu.eurise.read.ocr.Ocr.open]. */
    fun dataDir(context: Context) = File(context.filesDir, "tesseract/tessdata")
    fun file(context: Context, lang: String = "ita") = File(dataDir(context), "$lang.traineddata")
    fun ready(context: Context) = file(context).exists()

    /** Le lingue da usare insieme («ita» o «ita+eng»), o null se manca l'italiano. */
    fun languages(context: Context): String? =
        if (!ready(context)) null else listOf("ita", "eng").filter { file(context, it).exists() }.joinToString("+")

    /** Scarica la lingua; nulla del telefono viene inviato. */
    fun download(target: File) {
        target.parentFile?.mkdirs()
        var url = URL(source(target.name.removeSuffix(".traineddata")))
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "EuriseLeggi")
            when (c.responseCode) {
                in 300..399 -> { url = URL(url, c.getHeaderField("Location")); c.disconnect() }
                200 -> {
                    val tmp = File(target.parentFile, target.name + ".part")
                    c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    check(tmp.renameTo(target))
                    return
                }
                else -> error("HTTP ${c.responseCode}")
            }
        }
        error("troppi rinvii")
    }
}
