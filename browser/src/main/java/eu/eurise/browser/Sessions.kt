package eu.eurise.browser

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Una scheda ricordata (sessione salvata, chiusa di recente): indirizzo, titolo, quando. */
data class SavedTab(val url: String, val title: String, val time: Long = 0L)

/** Un gruppo di schede con il suo colore (indice nelle otto tinte della suite). */
data class TabGroup(val name: String, val tint: Int)

/** Una sessione salvata: un nome e le sue schede, da riaprire quando vuoi (tavola BrwSessioni). */
data class Session(val name: String, val tabs: List<SavedTab>, val time: Long)

/**
 * Gruppi, sessioni salvate e schede chiuse di recente (tavola BrwSessioni), in un file sul telefono.
 * Le schede private non arrivano mai qui.
 */
class SessionStore(private val file: File) {
    data class Data(
        val groups: List<TabGroup> = emptyList(),
        val sessions: List<Session> = emptyList(),
        val closed: List<SavedTab> = emptyList(),
    )

    fun load(): Data = runCatching { decode(file.readText()) }.getOrDefault(Data())

    fun save(data: Data) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(encode(data))
            tmp.renameTo(file)
        }
    }

    companion object {
        /** Quante schede chiuse si ricordano. */
        const val MAX_CLOSED = 40
        const val MAX_SESSIONS = 30

        /** Aggiunge in testa le schede chiuse (le più recenti prima), senza doppioni, fino a [MAX_CLOSED]. */
        fun remember(closed: List<SavedTab>, add: List<SavedTab>): List<SavedTab> {
            val fresh = add.filter { it.url.startsWith("http") }
            if (fresh.isEmpty()) return closed
            val urls = fresh.map { it.url }.toSet()
            return (fresh + closed.filter { it.url !in urls }).take(MAX_CLOSED)
        }

        /** Il primo colore non ancora usato dai gruppi (poi si ricomincia). */
        fun nextTint(groups: List<TabGroup>, count: Int = 8): Int {
            val used = groups.map { it.tint }.toSet()
            return (0 until count).firstOrNull { it !in used } ?: (groups.size % count)
        }

        /** Salva o sostituisce la sessione con lo stesso nome, la più recente in testa. */
        fun upsert(sessions: List<Session>, s: Session): List<Session> =
            (listOf(s) + sessions.filter { !it.name.equals(s.name, ignoreCase = true) }).take(MAX_SESSIONS)

        fun encode(d: Data): String = JSONObject()
            .put("groups", JSONArray().apply { d.groups.forEach { put(JSONObject().put("name", it.name).put("tint", it.tint)) } })
            .put("sessions", JSONArray().apply {
                d.sessions.forEach { s -> put(JSONObject().put("name", s.name).put("time", s.time).put("tabs", tabs(s.tabs))) }
            })
            .put("closed", tabs(d.closed))
            .toString()

        fun decode(text: String): Data {
            val o = JSONObject(text)
            val groups = o.optJSONArray("groups").objects().map { TabGroup(it.optString("name"), it.optInt("tint")) }.filter { it.name.isNotBlank() }
            val sessions = o.optJSONArray("sessions").objects().map { Session(it.optString("name"), tabs(it.optJSONArray("tabs")), it.optLong("time")) }
            return Data(groups, sessions, tabs(o.optJSONArray("closed")))
        }

        private fun tabs(list: List<SavedTab>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("url", it.url).put("title", it.title).put("time", it.time)) }
        }

        private fun tabs(a: JSONArray?) = a.objects().map { SavedTab(it.optString("url"), it.optString("title"), it.optLong("time")) }
            .filter { it.url.isNotBlank() }

        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
