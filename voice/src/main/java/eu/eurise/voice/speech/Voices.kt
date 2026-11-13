package eu.eurise.voice.speech

import android.content.Context
import eu.eurise.voice.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Le voci neurali di Alfred (Piper, licenza MIT; motore sherpa-onnx), scaricate a richiesta. Ogni voce è un archivio
 * preparato dalla suite (`.github/workflows/voci.yml`) con il modello, i simboli e i dati di pronuncia italiani di
 * espeak-ng. Si scarica solo toccando «Scarica»: escono la richiesta del file e l'indirizzo di rete, nient'altro.
 */
enum class Voice(val id: String, val onnx: String, val megabytes: Int, val label: Int, val detail: Int) {
    /** Maschile, pacata: la voce di Alfred. */
    Riccardo("alfred-riccardo", "it_IT-riccardo-x_low.onnx", 19, R.string.voice_tts_riccardo, R.string.voice_tts_riccardo_d),
    /** Femminile, più ricca: per chi la preferisce. */
    Paola("alfred-paola", "it_IT-paola-medium.onnx", 60, R.string.voice_tts_paola, R.string.voice_tts_paola_d);

    val url get() = "https://github.com/eurise-suite/eurise/releases/download/voci/$id.zip"

    fun dir(context: Context) = File(File(context.filesDir, "voices"), id)
    fun model(context: Context) = File(dir(context), onnx)
    fun tokens(context: Context) = File(dir(context), "tokens.txt")
    fun data(context: Context) = File(dir(context), "espeak-ng-data")
    fun installed(context: Context) = model(context).isFile && tokens(context).isFile && data(context).isDirectory
}

object Voices {
    const val HOST = "github.com"
    private const val PREFS = "voices"
    private const val KEY_CHOSEN = "chosen"

    /** La voce in uso: quella scelta se c'è, altrimenti la prima scaricata. */
    fun installed(context: Context): Voice? {
        val chosen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOSEN, null)
        return Voice.entries.firstOrNull { it.id == chosen && it.installed(context) } ?: Voice.entries.firstOrNull { it.installed(context) }
    }

    fun choose(context: Context, voice: Voice) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHOSEN, voice.id).apply()

    fun delete(context: Context, voice: Voice) = voice.dir(context).deleteRecursively().also { clearStopped(context) }

    /*
     * La spia della voce. La sintesi gira in un processo a parte (":voce"): se il motore nativo si ferma di colpo, chiude
     * solo quel processo. Il file «parla» si scrive prima di ogni frase e si toglie dopo; se al riavvio c'è ancora, la
     * voce si è fermata parlando e diventa «ferma»: Alfred passa alla voce del telefono invece di restare muto, finché
     * «Ascolta una prova» non la rimette in uso. File e non preferenze, perché li leggono due processi.
     */
    private fun flag(context: Context, name: String) = File(File(context.filesDir, "voices").apply { mkdirs() }, name)
    fun speakingStart(context: Context) = runCatching { flag(context, "parla").writeText("1") }
    fun speakingEnd(context: Context) = runCatching { flag(context, "parla").delete() }
    /** Al riavvio del motore: se l'ultima frase non è finita, la voce è ferma. */
    fun checkCrash(context: Context) = runCatching {
        if (flag(context, "parla").exists()) { flag(context, "parla").delete(); flag(context, "ferma").writeText("1") }
    }
    fun stopped(context: Context) = flag(context, "ferma").exists()
    fun clearStopped(context: Context) = runCatching { flag(context, "ferma").delete() }

    /** Scarica l'archivio in una copia temporanea, lo apre in una cartella nuova e la mette al suo posto solo se è intera. */
    fun download(context: Context, voice: Voice, progress: (Int) -> Unit): Boolean {
        val root = File(context.filesDir, "voices").apply { mkdirs() }
        val part = File(root, voice.id + ".zip.part")
        val unpack = File(root, voice.id + ".new")
        return runCatching {
            var url = URL(voice.url)
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "EuriseVoce")
                val code = connection.responseCode
                if (code in 300..399 && redirects < 6) {
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
                        val pct = if (total > 0) (done * 95 / total).toInt() else 0
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
            if (total > 0 && part.length() != total) error("download incompleto")
            unpack.deleteRecursively()
            unzip(part, unpack)
            val target = voice.dir(context)
            target.deleteRecursively()
            check(unpack.renameTo(target))
            clearStopped(context)
            progress(100)
            voice.installed(context)
        }.getOrElse { false }.also {
            part.delete()
            unpack.deleteRecursively()
        }
    }

    /** Apre l'archivio dentro [into], rifiutando i nomi che uscirebbero dalla cartella. */
    private fun unzip(zip: File, into: File) {
        into.mkdirs()
        val base = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                check(out.canonicalPath.startsWith(base)) { "nome non valido" }
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }
}
