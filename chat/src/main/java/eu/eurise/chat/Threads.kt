package eu.eurise.chat

import org.json.JSONObject

/**
 * Discussioni a filo (tavola ChatThread), senza Android: si prova con i test.
 *
 * Sono i thread di Matrix (`m.thread`, come in Element): una risposta porta `m.relates_to` con `rel_type: m.thread`,
 * l'id del messaggio di partenza e, per i client che non conoscono i thread, `m.in_reply_to` verso l'ultimo messaggio
 * con `is_falling_back: true`. Nella conversazione del gruppo resta solo il messaggio di partenza con «6 risposte».
 */
object Threads {
    const val REL = "m.thread"
    const val TITLE_MAX = 40

    /** Il messaggio di partenza della discussione di cui [content] fa parte, o null. */
    fun root(content: JSONObject?): String? {
        val r = content?.optJSONObject("m.relates_to") ?: return null
        if (r.optString("rel_type") != REL) return null
        return r.optString("event_id").takeIf { it.isNotEmpty() }
    }

    /** Vero se il riferimento a «risponde a» serve solo ai client senza thread (non è una vera risposta). */
    fun fallingBack(content: JSONObject?): Boolean =
        content?.optJSONObject("m.relates_to")?.optBoolean("is_falling_back", false) == true

    /** Quante risposte conta il server (riepilogo dentro `unsigned`), 0 se non lo dice. */
    fun serverCount(event: JSONObject): Int =
        event.optJSONObject("unsigned")?.optJSONObject("m.relations")?.optJSONObject(REL)?.optInt("count") ?: 0

    /**
     * Una risposta nella discussione [root]. [replyTo] = il messaggio a cui si risponde davvero dentro la discussione;
     * se null si mette l'ultimo ([last]) come riferimento di ripiego.
     */
    fun content(text: String, root: String, last: String, replyTo: String? = null): JSONObject {
        val rel = JSONObject().put("rel_type", REL).put("event_id", root)
            .put("is_falling_back", replyTo == null)
            .put("m.in_reply_to", JSONObject().put("event_id", replyTo ?: last))
        return JSONObject().put("msgtype", "m.text").put("body", text).put("m.relates_to", rel)
    }

    /** Una discussione vista da fuori: messaggio di partenza, quante risposte, l'ultima quando. */
    data class Info(val root: String, val title: String, val count: Int, val last: Long)

    /** Il titolo: la prima riga del messaggio di partenza, accorciata. */
    fun title(text: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length > TITLE_MAX) line.take(TITLE_MAX - 1).trimEnd() + "…" else line
    }

    /** Le discussioni di una conversazione (con almeno una risposta), dalla più recente. */
    fun summary(events: List<Event>): List<Info> {
        val byId = events.associateBy { it.id }
        val replies = events.filter { it.thread != null }.groupBy { it.thread!! }
        val roots = (replies.keys + events.filter { it.threadCount > 0 }.map { it.id }).toSet()
        return roots.mapNotNull { id ->
            val r = byId[id]
            val list = replies[id].orEmpty()
            val count = maxOf(list.size, r?.threadCount ?: 0)
            if (count == 0) return@mapNotNull null
            Info(id, title(r?.text.orEmpty()), count, maxOf(list.maxOfOrNull { it.time } ?: 0L, r?.time ?: 0L))
        }.sortedByDescending { it.last }
    }

    /** L'ultimo messaggio della discussione (o il messaggio di partenza), per il riferimento di ripiego. */
    fun last(events: List<Event>, root: String): String =
        events.lastOrNull { it.thread == root && !it.id.startsWith("local:") }?.id ?: root
}
