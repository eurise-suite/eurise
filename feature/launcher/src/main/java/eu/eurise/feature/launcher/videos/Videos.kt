package eu.eurise.feature.launcher.videos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

/** Un canale seguito: l'identificativo YouTube (UC…) e il nome. */
data class VideoChannel(val id: String, val name: String, val enabled: Boolean = true)

/** Un video recente di un canale seguito. */
data class Video(val id: String, val title: String, val channel: String, val publishedAt: Long) {
    val link: String get() = "https://www.youtube.com/watch?v=$id"
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

/**
 * Video dai canali preferiti. I canali si importano dal file che NewPipe esporta (Iscrizioni → Esporta) o
 * si aggiungono con l'indirizzo; i video si leggono dal feed pubblico di ogni canale, senza account e
 * senza servizi Google sul telefono. Si aprono in NewPipe se c'è, altrimenti nel browser.
 */
class VideosRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("eurise_videos", Context.MODE_PRIVATE)
    private val _channels = MutableStateFlow(load())
    val channels: StateFlow<List<VideoChannel>> = _channels.asStateFlow()
    private var cache: Pair<Long, List<Video>>? = null

    private fun load(): List<VideoChannel> = prefs.getString("channels", "").orEmpty().lines().mapNotNull { line ->
        val bits = line.split('\t')
        if (bits.size < 2 || bits[0].isBlank()) null else VideoChannel(bits[0], bits[1], bits.getOrNull(2) != "0")
    }

    private fun save(list: List<VideoChannel>) {
        _channels.value = list
        prefs.edit().putString("channels", list.joinToString("\n") { "${it.id}\t${it.name.replace('\t', ' ')}\t${if (it.enabled) 1 else 0}" }).apply()
        cache = null
    }

    fun setEnabled(id: String, on: Boolean) = save(_channels.value.map { if (it.id == id) it.copy(enabled = on) else it })

    fun remove(id: String) = save(_channels.value.filterNot { it.id == id })

    /** Importa le iscrizioni dal file JSON esportato da NewPipe; restituisce quanti canali nuovi ha aggiunto. */
    suspend fun importNewPipe(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@withContext 0
        val subs = JSONObject(text).optJSONArray("subscriptions") ?: return@withContext 0
        val found = (0 until subs.length()).mapNotNull { i ->
            val o = subs.optJSONObject(i) ?: return@mapNotNull null
            val id = channelIdFromUrl(o.optString("url")) ?: return@mapNotNull null
            VideoChannel(id, o.optString("name").ifBlank { id })
        }
        val known = _channels.value.map { it.id }.toSet()
        val added = found.filter { it.id !in known }.distinctBy { it.id }
        withContext(Dispatchers.Main) { save(_channels.value + added) }
        added.size
    }

    /** Aggiunge un canale dall'indirizzo (…/channel/UC…, …/@nome) o dall'identificativo. */
    suspend fun addChannel(raw: String): Boolean = withContext(Dispatchers.IO) {
        val input = raw.trim()
        val id = channelIdFromUrl(input) ?: Regex("^UC[\\w-]{22}$").find(input)?.value ?: resolveHandle(input) ?: return@withContext false
        if (_channels.value.any { it.id == id }) return@withContext true
        val name = runCatching { read(id).firstOrNull()?.channel }.getOrNull() ?: input.substringAfterLast('/').removePrefix("@")
        withContext(Dispatchers.Main) { save(_channels.value + VideoChannel(id, name)) }
        true
    }

    private fun channelIdFromUrl(url: String): String? = Regex("channel/(UC[\\w-]{22})").find(url)?.groupValues?.get(1)

    /** Per gli indirizzi con il nome (@canale) l'identificativo si legge dalla pagina del canale. */
    private fun resolveHandle(input: String): String? = runCatching {
        val url = when {
            input.startsWith("http") -> input
            input.startsWith("@") -> "https://www.youtube.com/$input"
            else -> return null
        }
        val html = get(url) ?: return null
        Regex("\"(?:channelId|externalId)\":\"(UC[\\w-]{22})\"").find(html)?.groupValues?.get(1)
            ?: Regex("channel/(UC[\\w-]{22})").find(html)?.groupValues?.get(1)
    }.getOrNull()

    private fun get(url: String): String? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
        }
        return try {
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Gli ultimi video dei canali attivi, dal più recente; restano in memoria un'ora. */
    suspend fun latest(force: Boolean = false, max: Int = 20): List<Video> {
        val now = System.currentTimeMillis()
        cache?.let { (at, list) -> if (!force && now - at < 60 * 60_000) return list }
        val active = _channels.value.filter { it.enabled }
        val all = withContext(Dispatchers.IO) {
            coroutineScope { active.map { ch -> async { runCatching { read(ch.id) }.getOrDefault(emptyList()) } }.awaitAll() }
        }.flatten().sortedByDescending { it.publishedAt }.take(max)
        if (all.isNotEmpty()) cache = now to all
        return all
    }

    private fun read(channelId: String): List<Video> {
        val c = (URL("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 8_000
            setRequestProperty("User-Agent", "Eurise/1.0 (lettore RSS)")
        }
        return try {
            c.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                val out = mutableListOf<Video>()
                var channel = ""
                var inEntry = false
                var id = ""; var title = ""; var date = ""
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && out.size < 6) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "entry" -> { inEntry = true; id = ""; title = ""; date = "" }
                            "yt:videoId" -> id = parser.nextText().trim()
                            "title" -> if (inEntry) title = parser.nextText().trim() else if (channel.isEmpty()) channel = parser.nextText().trim()
                            "name" -> if (!inEntry && channel.isEmpty()) channel = parser.nextText().trim()
                            "published" -> if (inEntry) date = parser.nextText().trim()
                        }
                        XmlPullParser.END_TAG -> if (parser.name == "entry") {
                            inEntry = false
                            // Gli "short" verticali restano fuori: il feed non li distingue, ma il titolo spesso sì.
                            if (id.isNotBlank() && !title.contains("#shorts", ignoreCase = true)) {
                                out += Video(id, title, channel, runCatching { java.time.OffsetDateTime.parse(date).toInstant().toEpochMilli() }.getOrDefault(0L))
                            }
                        }
                    }
                    event = parser.next()
                }
                out
            }
        } finally {
            c.disconnect()
        }
    }

    /** Apre il video in NewPipe se è installato, altrimenti con l'app scelta dal sistema. */
    fun open(video: Video) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val newPipe = listOf("org.schabi.newpipe", "org.polymorphicshade.tubular", "org.schabi.newpipe.debug")
            .firstOrNull { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
        runCatching { context.startActivity(if (newPipe != null) Intent(intent).setPackage(newPipe) else intent) }
            .onFailure { runCatching { context.startActivity(intent) } }
    }
}
