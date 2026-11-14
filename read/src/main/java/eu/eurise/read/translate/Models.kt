package eu.eurise.read.translate

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * I modelli di traduzione di Firefox (Mozilla, licenza MPL 2.0), presi dall'elenco pubblico dei modelli rilasciati e tenuti
 * nei file dell'app. Si scaricano solo a richiesta; dal telefono parte soltanto la richiesta dei file.
 * Ogni lingua ha due direzioni con l'inglese («fr-en» ed «en-fr»): tra due lingue qualsiasi si passa dall'inglese.
 */
object Models {
    /** Lingue offerte in Leggi, l'italiano per primo. L'inglese non si scarica da solo: arriva con ogni altra lingua. */
    val LANGUAGES = listOf("it", "fr", "de", "es", "pt", "nl", "pl", "ro", "cs", "el", "sv", "da", "uk", "ru")

    /** Le direzioni da usare per tradurre da [from] a [to] (una o due, passando dall'inglese). */
    fun route(from: String, to: String): List<String> = when {
        from == to -> emptyList()
        from == "en" || to == "en" -> listOf("$from-$to")
        else -> listOf("$from-en", "en-$to")
    }

    /** Lingue pronte: quelle con entrambe le direzioni, più l'inglese se ce n'è almeno una. */
    fun languages(context: Context): List<String> {
        val ready = LANGUAGES.filter { installed(context, "$it-en") != null && installed(context, "en-$it") != null }
        return if (ready.isEmpty()) ready else ready + "en"
    }

    fun languageSize(context: Context, lang: String): Long = size(context, "$lang-en") + size(context, "en-$lang")

    fun downloadLanguage(context: Context, lang: String) {
        for (pair in listOf("$lang-en", "en-$lang")) if (installed(context, pair) == null) download(context, pair)
    }

    fun removeLanguage(context: Context, lang: String) {
        remove(context, "$lang-en")
        remove(context, "en-$lang")
    }

    private const val MANIFEST = "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json"
    private val RELEASE = mapOf("Release Android" to 4, "Release" to 3, "Release Desktop" to 2, "Nightly" to 1)
    private val SIZE = mapOf("tiny" to 1, "base" to 2, "base-memory" to 3)

    class Files(val model: File, val vocab: File, val targetVocab: File?, val shortlist: File, val architecture: String)

    private fun dir(context: Context, pair: String) = File(context.filesDir, "models/$pair")

    /** I file della direzione [pair] («it-en»), se scaricati. */
    fun installed(context: Context, pair: String): Files? {
        val d = dir(context, pair)
        val info = File(d, "info.txt").takeIf { it.exists() }?.readLines() ?: return null
        val files = info.drop(1).associate { it.substringBefore('=') to File(d, it.substringAfter('=')) }
        val model = files["model"] ?: return null
        val shortlist = files["lex"] ?: return null
        val vocab = files["vocab"] ?: files["srcVocab"] ?: return null
        if (!model.exists() || !shortlist.exists() || !vocab.exists()) return null
        return Files(model, vocab, files["trgVocab"], shortlist, info.first())
    }

    fun size(context: Context, pair: String): Long = dir(context, pair).walk().filter { it.isFile }.sumOf { it.length() }

    fun remove(context: Context, pair: String) {
        dir(context, pair).deleteRecursively()
    }

    /** Scarica la direzione [pair] (es. «it-en»): la versione rilasciata migliore, decompressa. */
    fun download(context: Context, pair: String) {
        val manifest = JSONObject(fetch(MANIFEST).toString(Charsets.UTF_8))
        val base = manifest.getString("baseUrl").trimEnd('/')
        val entries = manifest.getJSONObject("models").optJSONArray(pair) ?: error("coppia non disponibile")
        val best = (0 until entries.length()).map { entries.getJSONObject(it) }
            .maxBy { (RELEASE[it.optString("releaseStatus")] ?: 0) * 10 + (SIZE[it.optString("architecture")] ?: 0) }
        val files = best.getJSONObject("files")
        val d = dir(context, "$pair.part").apply { deleteRecursively(); mkdirs() }
        val info = StringBuilder(best.optString("architecture")).append('\n')
        for (key in listOf("model", "lexicalShortlist", "vocab", "srcVocab", "trgVocab")) {
            val path = files.optJSONObject(key)?.optString("path")?.takeIf { it.isNotEmpty() } ?: continue
            val name = path.substringAfterLast('/').removeSuffix(".gz")
            val bytes = fetch("$base/$path")
            File(d, name).outputStream().use { out ->
                if (path.endsWith(".gz")) GZIPInputStream(bytes.inputStream()).use { it.copyTo(out) } else out.write(bytes)
            }
            info.append(if (key == "lexicalShortlist") "lex" else key).append('=').append(name).append('\n')
        }
        File(d, "info.txt").writeText(info.toString())
        val target = dir(context, pair)
        target.deleteRecursively()
        check(d.renameTo(target))
    }

    private fun fetch(address: String): ByteArray {
        var url = URL(address)
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            // Senza questo Android manda «Dalvik/… (Linux; Android 14; modello del telefono)»: un'impronta del telefono.
            c.setRequestProperty("User-Agent", "EuriseLeggi")
            when (c.responseCode) {
                in 300..399 -> { url = URL(url, c.getHeaderField("Location")); c.disconnect() }
                200 -> return c.inputStream.use { it.readBytes() }
                else -> error("HTTP ${c.responseCode}")
            }
        }
        error("troppi rinvii")
    }
}
