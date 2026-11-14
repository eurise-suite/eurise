package eu.eurise.voice

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * I modelli di Whisper che si possono scaricare (dalla pagina pubblica di whisper.cpp su Hugging Face).
 * Si scarica solo quello scelto, solo toccando "Scarica": nessun audio e nessun dato personale esce dal telefono.
 */
enum class Model(val id: String, val megabytes: Int, val label: Int, val detail: Int) {
    Base("ggml-base-q5_1.bin", 57, R.string.voice_model_base, R.string.voice_model_base_d),
    Small("ggml-small-q5_1.bin", 181, R.string.voice_model_small, R.string.voice_model_small_d);

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$id"

    fun file(context: Context) = File(File(context.filesDir, "models"), id)
    fun installed(context: Context) = file(context).let { it.isFile && it.length() > megabytes * 900_000L }

    companion object {
        const val HOST = "huggingface.co"

        fun installed(context: Context): Model? = entries.lastOrNull { it.installed(context) }
    }
}

object Downloader {
    /** Scarica [model] in una copia temporanea e la rinomina solo se arriva intera. [progress] riceve 0–100. */
    fun download(context: Context, model: Model, progress: (Int) -> Unit): Boolean {
        val target = model.file(context)
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        return runCatching {
            var url = URL(model.url)
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "EuriseVoce")
                val code = connection.responseCode
                if (code in 300..399 && redirects < 5) {
                    url = URL(url, connection.getHeaderField("Location")); redirects++; connection.disconnect(); continue
                }
                if (code != 200) error("HTTP $code")
                break
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
            if (total > 0 && part.length() != total) error("download incompleto")
            target.delete()
            part.renameTo(target)
        }.getOrElse { part.delete(); false }
    }

    fun delete(context: Context, model: Model) = model.file(context).delete()
}
