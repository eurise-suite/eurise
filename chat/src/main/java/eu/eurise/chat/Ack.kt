package eu.eurise.chat

import org.json.JSONObject

/**
 * Presa visione (tavola ChatPresaVisione), senza Android: si prova con i test.
 *
 * Un messaggio porta il campo [ACK]; chi l'ha letto conferma con la reazione ✅ (anche da Element: in fondo al testo c'è
 * l'invito a farlo). Chi l'ha mandato vede «Ricevuto da 4 su 6» e chi manca, distinguendo chi l'ha letto (conferma di
 * lettura del server) da chi no.
 */
object Ack {
    const val ACK = "eu.eurise.ack"
    const val KEY = "✅"

    /** Il contenuto: il testo, l'invito per chi usa un'altra app ([fallback]) e il campo. */
    fun content(text: String, fallback: String): JSONObject =
        JSONObject().put("msgtype", "m.text").put("body", text.trim() + "\n\n" + fallback).put(ACK, JSONObject().put("text", text.trim()))

    fun isRequest(content: JSONObject?): Boolean = content?.has(ACK) == true

    /** Il testo senza l'invito in fondo (che serve solo alle altre app). */
    fun text(content: JSONObject?, body: String): String = content?.optJSONObject(ACK)?.optString("text")?.ifEmpty { null } ?: body

    data class Status(val confirmed: List<String>, val readOnly: List<String>, val unread: List<String>) {
        val total get() = confirmed.size + readOnly.size + unread.size
    }

    /**
     * A che punto è la presa visione: tra i [members] (tranne chi l'ha chiesta, [sender]) chi ha confermato, chi l'ha
     * letto senza confermare (conferma di lettura dopo [time]) e chi non l'ha ancora letto.
     */
    fun status(members: List<String>, sender: String, confirmed: Set<String>, read: Map<String, Long>, time: Long): Status {
        val others = members.filter { it != sender }.distinct()
        val yes = others.filter { it in confirmed }
        val rest = others - yes.toSet()
        return Status(yes, rest.filter { (read[it] ?: 0L) >= time }, rest.filter { (read[it] ?: 0L) < time })
    }

    /** Le conferme di lettura di una tornata di /sync, per stanza e persona (l'ora più recente). */
    fun readers(sync: JSONObject): Map<String, Map<String, Long>> {
        val out = HashMap<String, HashMap<String, Long>>()
        val join = sync.optJSONObject("rooms")?.optJSONObject("join") ?: return out
        join.keys().forEach { roomId ->
            val events = join.optJSONObject(roomId)?.optJSONObject("ephemeral")?.optJSONArray("events") ?: return@forEach
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                if (e.optString("type") != "m.receipt") continue
                val c = e.optJSONObject("content") ?: continue
                c.keys().forEach { eventId ->
                    val byType = c.optJSONObject(eventId) ?: return@forEach
                    listOf("m.read", "m.read.private").forEach { t ->
                        byType.optJSONObject(t)?.let { users ->
                            users.keys().forEach { u ->
                                val ts = users.optJSONObject(u)?.optLong("ts") ?: 0L
                                val m = out.getOrPut(roomId) { HashMap() }
                                if (ts > (m[u] ?: 0L)) m[u] = ts
                            }
                        }
                    }
                }
            }
        }
        return out
    }
}
