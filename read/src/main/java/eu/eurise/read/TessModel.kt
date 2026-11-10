package eu.eurise.read

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** La lingua italiana di Tesseract (tessdata_fast), scaricata una volta a richiesta e tenuta nei file dell'app. */
internal object TessModel {
    private const val URL_ITA = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ita.traineddata"

    fun dir(context: Context) = File(context.filesDir, "tesseract")
    fun file(context: Context) = File(dir(context), "tessdata/ita.traineddata")
    fun ready(context: Context) = file(context).exists()

    /** Scarica la lingua; nulla del telefono viene inviato. */
    fun download(target: File) {
        target.parentFile?.mkdirs()
        var url = URL(URL_ITA)
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
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
