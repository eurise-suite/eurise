package eu.eurise.feature.launcher.feeds

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Una voce di un feed con testo e immagine, per le schede Oroscopo e Ricetta. */
data class FeedEntry(
    val title: String,
    val link: String,
    val text: String,
    val image: String?,
    val publishedAt: Long,
    val source: String,
)

/** Una fonte proposta o aggiunta dall'utente. */
data class FeedSource(val id: String, val name: String, val url: String, val custom: Boolean = false)

/** Legge feed RSS e Atom: titolo, collegamento, testo (senza HTML), immagine e data. */
object FeedReader {

    fun read(url: String, source: String, max: Int = 20): List<FeedEntry> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Eurise/1.0 (lettore RSS)")
        }
        return try {
            connection.inputStream.use { parse(it, source, max) }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(stream: java.io.InputStream, source: String, max: Int): List<FeedEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        val out = mutableListOf<FeedEntry>()
        var inItem = false
        var title = ""; var link = ""; var text = ""; var image: String? = null; var date = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < max) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    when {
                        name == "item" || name == "entry" -> { inItem = true; title = ""; link = ""; text = ""; image = null; date = "" }
                        !inItem -> Unit
                        name == "title" -> title = parser.nextText().trim()
                        name == "link" -> link = parser.getAttributeValue(null, "href") ?: parser.nextText().trim()
                        name == "description" || name == "summary" || name == "content:encoded" || name == "content" -> {
                            val raw = parser.nextText()
                            if (image == null) image = Regex("""<img[^>]+src=["']([^"']+)""").find(raw)?.groupValues?.get(1)
                            val clean = stripHtml(raw)
                            if (clean.length > text.length) text = clean
                        }
                        name == "enclosure" || name == "media:content" || name == "media:thumbnail" -> {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            val u = parser.getAttributeValue(null, "url")
                            if (u != null && (type.isEmpty() || type.startsWith("image")) && image == null) image = u
                        }
                        name == "pubdate" || name == "updated" || name == "published" || name == "dc:date" ->
                            if (date.isEmpty()) date = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> if (inItem && parser.name.lowercase() in setOf("item", "entry")) {
                    inItem = false
                    if (title.isNotBlank() && link.isNotBlank()) out += FeedEntry(stripHtml(title), link, text, image, parseDate(date), source)
                }
            }
            event = parser.next()
        }
        return out
    }

    fun stripHtml(html: String): String = html
        .replace(Regex("""(?s)<script.*?</script>"""), " ")
        .replace(Regex("""<br\s*/?>|</p>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
        .replace("&rsquo;", "’").replace("&lsquo;", "‘").replace("&egrave;", "è").replace("&agrave;", "à")
        .replace("&ograve;", "ò").replace("&igrave;", "ì").replace("&ugrave;", "ù").replace("&eacute;", "é").replace("&amp;", "&")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n\s*\n+"""), "\n")
        .trim()

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0
        return runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrDefault(0)
    }

    /** Scarica e riduce un'immagine (per la foto della ricetta). */
    suspend fun image(url: String, maxSide: Int = 360): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", "Eurise/1.0")
            }
            val bytes = try { connection.inputStream.readBytes() } finally { connection.disconnect() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * Fonti scelte per una scheda (oroscopo, ricette): proposte attivabili e feed aggiunti a mano, salvati sul
 * telefono. Il risultato resta in memoria un'ora.
 */
class FeedSources(context: Context, private val key: String, val builtIn: List<FeedSource>, defaults: Set<String>) {
    private val prefs = context.getSharedPreferences("eurise_feeds_$key", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getStringSet("enabled", null)?.toSet() ?: defaults)
    val enabled: StateFlow<Set<String>> = _enabled.asStateFlow()
    private val _custom = MutableStateFlow(
        prefs.getString("custom", "").orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { url ->
            runCatching { FeedSource("custom:${url.hashCode()}", URL(url).host.removePrefix("www."), url, custom = true) }.getOrNull()
        },
    )
    val custom: StateFlow<List<FeedSource>> = _custom.asStateFlow()
    private var cache: Pair<Long, List<FeedEntry>>? = null

    fun setEnabled(id: String, on: Boolean) {
        val next = if (on) _enabled.value + id else _enabled.value - id
        _enabled.value = next
        prefs.edit().putStringSet("enabled", next).apply()
        cache = null
    }

    fun addCustom(raw: String) {
        val url = raw.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val source = runCatching { FeedSource("custom:${url.hashCode()}", URL(url).host.removePrefix("www."), url, custom = true) }.getOrNull() ?: return
        if (_custom.value.any { it.url == url }) return
        _custom.value = _custom.value + source
        prefs.edit().putString("custom", _custom.value.joinToString("\n") { it.url }).apply()
        setEnabled(source.id, true)
    }

    fun removeCustom(source: FeedSource) {
        _custom.value = _custom.value - source
        prefs.edit().putString("custom", _custom.value.joinToString("\n") { it.url }).apply()
        setEnabled(source.id, false)
    }

    suspend fun entries(force: Boolean = false): List<FeedEntry> {
        val now = System.currentTimeMillis()
        cache?.let { (at, list) -> if (!force && now - at < 60 * 60_000) return list }
        val sources = (builtIn + _custom.value).filter { it.id in _enabled.value }
        val list = withContext(Dispatchers.IO) {
            coroutineScope { sources.map { s -> async { runCatching { FeedReader.read(s.url, s.name) }.getOrDefault(emptyList()) } }.awaitAll() }
        }
        val merged = list.flatten().sortedByDescending { it.publishedAt }
        if (merged.isNotEmpty()) cache = now to merged
        return merged
    }
}
