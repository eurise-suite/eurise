package eu.eurise.browser

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un segnalibro; [pinned] lo mette tra i siti fissati della nuova scheda. */
data class Bookmark(val title: String, val url: String, val folder: String = "", val pinned: Boolean = false)

/**
 * Lettura del file dei segnalibri esportato da Chrome, Firefox, Brave, Edge, Safari (formato «Netscape bookmark»):
 * cartelle <H3> e link <A HREF>. Senza Android, si prova con i test (tavola BrwImporta).
 */
object BookmarksHtml {
    private val TAG = Regex("<(/?)(H3|A|DL)\\b([^>]*)>(.*?)(?=<|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HREF = Regex("HREF\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(html: String): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        val folders = ArrayDeque<String>()
        var pendingFolder: String? = null
        for (m in TAG.findAll(html)) {
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].uppercase()
            val attrs = m.groupValues[3]
            val text = unescape(m.groupValues[4].trim())
            when {
                tag == "H3" && !closing -> pendingFolder = text
                tag == "DL" && !closing -> { folders.addLast(pendingFolder ?: ""); pendingFolder = null }
                tag == "DL" && closing -> if (folders.isNotEmpty()) folders.removeLast()
                tag == "A" && !closing -> {
                    val href = HREF.find(attrs)?.groupValues?.get(1)?.let(::unescape) ?: continue
                    if (!href.startsWith("http")) continue
                    val folder = folders.filter { it.isNotEmpty() }.joinToString(" › ")
                    out += Bookmark(text.ifBlank { Links.host(href) }, href, folder)
                }
            }
        }
        return out.distinctBy { it.url }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
}

/** I segnalibri sul telefono, in un file JSON nella memoria dell'app (non esce nulla). */
class BookmarkStore(private val file: File) {
    fun load(): List<Bookmark> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Bookmark(o.optString("title"), o.getString("url"), o.optString("folder"), o.optBoolean("pinned"))
        }
    }.getOrDefault(emptyList())

    fun save(items: List<Bookmark>) {
        val a = JSONArray()
        items.forEach { b -> a.put(JSONObject().put("title", b.title).put("url", b.url).put("folder", b.folder).put("pinned", b.pinned)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }

    /** Aggiunge senza doppioni (stesso indirizzo): resta il segnalibro che c'era, con il «fissato» più forte. */
    fun merge(old: List<Bookmark>, new: List<Bookmark>): Pair<List<Bookmark>, Int> {
        val known = old.associateBy { it.url }
        val added = new.filter { it.url !in known }
        return (old + added) to added.size
    }
}
