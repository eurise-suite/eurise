package eu.eurise.feature.launcher.news

import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import android.content.Context
import android.util.Xml
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
import java.util.Locale

enum class FeedGroup { Agencies, National, Foreign, Local, Custom }

/** Una fonte di notizie: un feed RSS o Atom pubblico. */
data class Feed(val id: String, val name: String, val url: String, val group: FeedGroup)

/** Una notizia da mostrare nella scheda. */
data class NewsItem(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
    val province: String? = null,
    /** Foto della notizia, se il feed ne indica una e l'utente ha scelto di vederle. */
    val image: String? = null,
)

/**
 * Fonti proposte: agenzie, testate nazionali e locali (Campania). Si leggono direttamente dai siti, senza
 * passare da servizi intermedi; l'utente sceglie quali tenere e può aggiungere qualsiasi altro feed.
 */
val BuiltInFeeds = listOf(
    Feed("ansa", "ANSA · Ultima ora", "https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml", FeedGroup.Agencies),
    Feed("adnkronos", "Adnkronos · Ultim'ora", "https://www.adnkronos.com/RSS_Ultimora.xml", FeedGroup.Agencies),
    Feed("agi", "AGI · Estero", "https://www.agi.it/estero/rss", FeedGroup.Agencies),
    Feed("rainews", "RaiNews · Ultim'ora", "https://www.rainews.it/rss/ultimora", FeedGroup.National),
    Feed("skytg24", "Sky TG24", "https://tg24.sky.it/rss/tg24_flipboard.ultimenews.xml", FeedGroup.National),
    Feed("corriere", "Corriere della Sera", "https://xml2.corriereobjects.it/rss/homepage.xml", FeedGroup.National),
    Feed("repubblica", "la Repubblica", "https://www.repubblica.it/rss/homepage/rss2.0.xml", FeedGroup.National),
    Feed("sole24ore", "Il Sole 24 Ore", "https://www.ilsole24ore.com/rss/italia.xml", FeedGroup.National),
    Feed("ilpost", "Il Post", "https://www.ilpost.it/feed/", FeedGroup.National),
    Feed("ilfatto", "Il Fatto Quotidiano", "https://www.ilfattoquotidiano.it/feed/", FeedGroup.National),
    Feed("ilmattino", "Il Mattino", "https://www.ilmattino.it/rss/home.xml", FeedGroup.National),
    Feed("bbc", "BBC News · World", "https://feeds.bbci.co.uk/news/world/rss.xml", FeedGroup.Foreign),
    Feed("guardian", "The Guardian · World", "https://www.theguardian.com/world/rss", FeedGroup.Foreign),
    Feed("lemonde", "Le Monde", "https://www.lemonde.fr/rss/une.xml", FeedGroup.Foreign),
    Feed("dw", "Deutsche Welle", "https://rss.dw.com/rdf/rss-en-all", FeedGroup.Foreign),
    Feed("elpais", "El País", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", FeedGroup.Foreign),
    Feed("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", FeedGroup.Foreign),
    Feed("npr", "NPR News", "https://feeds.npr.org/1001/rss.xml", FeedGroup.Foreign),
)

private val DefaultEnabled = setOf("ansa", "adnkronos", "rainews")

/** Legge e unisce i feed scelti; tiene in memoria il risultato per venti minuti. */
class NewsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_news", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getStringSet("enabled", null)?.toSet() ?: DefaultEnabled)
    val enabled: StateFlow<Set<String>> = _enabled.asStateFlow()

    private val _custom = MutableStateFlow(loadCustom())
    val custom: StateFlow<List<Feed>> = _custom.asStateFlow()

    /** Province seguite: il feed della loro regione è attivo e le notizie che le nominano vanno in cima. */
    private val _provinces = MutableStateFlow(prefs.getStringSet("provinces", null)?.toSet() ?: emptySet())
    val provinces: StateFlow<Set<String>> = _provinces.asStateFlow()

    private var cache: Pair<Long, List<NewsItem>>? = null

    private val _photos = MutableStateFlow(prefs.getBoolean("photos", false))

    /** Piccola foto accanto a ogni notizia (scaricata dal sito della testata). */
    val photos: StateFlow<Boolean> = _photos

    fun setPhotos(on: Boolean) {
        _photos.value = on
        prefs.edit().putBoolean("photos", on).apply()
        cache = null
    }

    fun setProvince(province: String, on: Boolean) {
        val next = if (on) _provinces.value + province else _provinces.value - province
        _provinces.value = next
        prefs.edit().putStringSet("provinces", next).apply()
        Regions.firstOrNull { province in it.provinces }?.let { region -> if (on) setEnabled(region.feed.id, true) }
        cache = null
    }

    fun setEnabled(id: String, on: Boolean) {
        val next = if (on) _enabled.value + id else _enabled.value - id
        _enabled.value = next
        prefs.edit().putStringSet("enabled", next).apply()
        cache = null
    }

    /** Aggiunge un feed scritto dall'utente; il nome è l'indirizzo del sito finché non si legge il titolo. */
    fun addCustom(url: String) {
        val clean = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        if (_custom.value.any { it.url == clean }) return
        val feed = Feed("custom:${clean.hashCode()}", URL(clean).host.removePrefix("www."), clean, FeedGroup.Custom)
        _custom.value = _custom.value + feed
        saveCustom()
        setEnabled(feed.id, true)
    }

    fun removeCustom(feed: Feed) {
        _custom.value = _custom.value - feed
        saveCustom()
        setEnabled(feed.id, false)
    }

    suspend fun latest(force: Boolean = false): List<NewsItem> {
        val now = System.currentTimeMillis()
        cache?.let { (at, items) -> if (!force && now - at < 20 * 60_000) return items }
        val feeds = (BuiltInFeeds + Regions.map { it.feed } + _custom.value).filter { it.id in _enabled.value }
        val items = withContext(Dispatchers.IO) {
            coroutineScope { feeds.map { f -> async { runCatching { read(f) }.getOrDefault(emptyList()) } }.awaitAll() }
        }.flatten()
            .distinctBy { it.title.lowercase() }
            .map { item -> item.copy(province = _provinces.value.firstOrNull { p -> provinceKeywords(p).any { k -> item.title.contains(k, ignoreCase = true) } }) }
            // Le notizie della propria provincia delle ultime 24 ore vanno in cima, le altre per data.
            .sortedWith(compareByDescending<NewsItem> { it.province != null && now - it.publishedAt < 24 * 3_600_000L }.thenByDescending { it.publishedAt })
            .take(40)
            .map { if (_photos.value) it else it.copy(image = null) }
        if (items.isNotEmpty()) cache = now to items
        return items
    }

    private fun read(feed: Feed): List<NewsItem> {
        if (!PrivacyCenter.allowed(PrivacyService.News)) return emptyList()
        PrivacyCenter.log(PrivacyService.News)
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Eurise/1.0 (lettore RSS)")
        }
        return try {
            connection.inputStream.use { stream -> parse(stream, feed) }
        } finally {
            connection.disconnect()
        }
    }

    /** RSS 2.0 (item/title/link/pubDate) e Atom (entry/title/link href/updated). */
    private fun parse(stream: java.io.InputStream, feed: Feed): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        val out = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""; var link = ""; var date = ""; var image: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 15) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item", "entry" -> { inItem = true; title = ""; link = ""; date = ""; image = null }
                    "title" -> if (inItem) title = parser.nextText().trim()
                    "link" -> if (inItem) {
                        val href = parser.getAttributeValue(null, "href")
                        link = href ?: parser.nextText().trim()
                    }
                    "pubdate", "updated", "published", "dc:date" -> if (inItem && date.isEmpty()) date = parser.nextText().trim()
                    "enclosure", "media:content", "media:thumbnail" -> if (inItem && image == null) {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val u = parser.getAttributeValue(null, "url")
                        if (u != null && (type.isEmpty() || type.startsWith("image"))) image = u
                    }
                    "description", "content:encoded" -> if (inItem && image == null) {
                        image = Regex("""<img[^>]+src=["']([^"']+)""").find(parser.nextText())?.groupValues?.get(1)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.lowercase() in setOf("item", "entry") && inItem) {
                    inItem = false
                    if (title.isNotBlank() && link.isNotBlank()) out += NewsItem(clean(title), feed.name, link, parseDate(date), image = image)
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun clean(text: String) = text
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&amp;", "&")
        .replace("&rsquo;", "’").replace("&lsquo;", "‘").replace("&egrave;", "è").replace("&agrave;", "à")
        .trim()

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return 0
        return runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { ZonedDateTime.parse(raw, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrDefault(0)
    }

    private fun loadCustom(): List<Feed> = prefs.getString("custom", "").orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val url = line.trim()
        runCatching { Feed("custom:${url.hashCode()}", URL(url).host.removePrefix("www."), url, FeedGroup.Custom) }.getOrNull()
    }

    private fun saveCustom() {
        prefs.edit().putString("custom", _custom.value.joinToString("\n") { it.url }).apply()
    }
}
