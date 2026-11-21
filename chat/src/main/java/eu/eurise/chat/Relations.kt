package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Le basi della chat (tavola ChatBasi): reazioni, modifiche, cancellazioni, risposte e sondaggi, come li scrive Matrix
 * (https://spec.matrix.org, sondaggi MSC3381 con i nomi che usano Element e gli altri). Logica pura con [RelationsTest]:
 * gli eventi che «toccano» un altro messaggio si leggono con [read] e si raccolgono in uno [Store].
 */
object Relations {
    /** Un evento che si riferisce a un altro. */
    sealed interface Rel { val id: String; val sender: String; val time: Long }
    data class Reaction(override val id: String, override val sender: String, override val time: Long, val target: String, val key: String) : Rel
    data class Edit(override val id: String, override val sender: String, override val time: Long, val target: String, val body: String) : Rel
    data class Redaction(override val id: String, override val sender: String, override val time: Long, val target: String) : Rel
    data class Vote(override val id: String, override val sender: String, override val time: Long, val poll: String, val answers: List<String>) : Rel
    data class PollEnd(override val id: String, override val sender: String, override val time: Long, val poll: String) : Rel

    const val REACTION = "m.reaction"
    val POLL_START = setOf("m.poll.start", "org.matrix.msc3381.poll.start")
    val POLL_RESPONSE = setOf("m.poll.response", "org.matrix.msc3381.poll.response")
    val POLL_END = setOf("m.poll.end", "org.matrix.msc3381.poll.end")
    const val PIN_STATE = "m.room.pinned_events"

    private const val U_START = "org.matrix.msc3381.poll.start"
    private const val U_RESPONSE = "org.matrix.msc3381.poll.response"
    private const val U_TEXT = "org.matrix.msc1767.text"
    private const val U_DISCLOSED = "org.matrix.msc3381.poll.disclosed"
    private const val U_UNDISCLOSED = "org.matrix.msc3381.poll.undisclosed"
    /** Nostra: quando il sondaggio si chiude da solo (gli altri client la ignorano; chi l'ha aperto manda la fine). */
    const val CLOSES = "eu.eurise.closes"

    /** Il riferimento di un evento qualsiasi, o null se non tocca un altro messaggio. */
    fun read(o: JSONObject): Rel? {
        val id = o.optString("event_id").ifEmpty { return null }
        val sender = o.optString("sender")
        val time = o.optLong("origin_server_ts")
        val content = o.optJSONObject("content") ?: JSONObject()
        val rel = content.optJSONObject("m.relates_to")
        val target = rel?.optString("event_id").orEmpty()
        val type = o.optString("type")
        return when {
            type == "m.room.redaction" -> {
                // Dalla versione 11 delle stanze «redacts» sta nel contenuto; prima, fuori.
                val t = content.optString("redacts").ifEmpty { o.optString("redacts") }
                if (t.isEmpty()) null else Redaction(id, sender, time, t)
            }
            type == REACTION && rel?.optString("rel_type") == "m.annotation" && target.isNotEmpty() ->
                rel.optString("key").takeIf { it.isNotEmpty() }?.let { Reaction(id, sender, time, target, it) }
            type == "m.room.message" && rel?.optString("rel_type") == "m.replace" && target.isNotEmpty() -> {
                val nc = content.optJSONObject("m.new_content") ?: return null
                Edit(id, sender, time, target, nc.optString("body"))
            }
            type in POLL_RESPONSE && target.isNotEmpty() -> {
                val r = content.optJSONObject(U_RESPONSE) ?: content.optJSONObject("m.poll.response") ?: content.optJSONObject("m.selections")?.let { JSONObject().put("answers", it) }
                val a = r?.optJSONArray("answers") ?: JSONArray()
                Vote(id, sender, time, target, (0 until a.length()).map { a.getString(it) })
            }
            type in POLL_END && target.isNotEmpty() -> PollEnd(id, sender, time, target)
            else -> null
        }
    }

    /** Il messaggio a cui questo risponde, o null. */
    fun replyTo(content: JSONObject): String? =
        content.optJSONObject("m.relates_to")?.optJSONObject("m.in_reply_to")?.optString("event_id")?.takeIf { it.isNotEmpty() }

    /**
     * Il testo senza la vecchia citazione («> <@anna:x> …» e riga vuota) che alcuni client mettono ancora in testa alle
     * risposte: la citazione la mostra già il fumetto.
     */
    fun stripFallback(body: String): String {
        if (!body.startsWith("> ")) return body
        val lines = body.lines()
        val i = lines.indexOfFirst { !it.startsWith(">") }
        if (i < 0) return body
        return lines.drop(i).dropWhile { it.isBlank() }.joinToString("\n")
    }

    /** Un sondaggio: domanda, risposte (id e testo), quante se ne possono scegliere, se i risultati si vedono subito. */
    data class Poll(val question: String, val answers: List<Pair<String, String>>, val max: Int, val disclosed: Boolean, val closes: Long?)

    private fun text(o: JSONObject?): String {
        o ?: return ""
        o.optString(U_TEXT).takeIf { it.isNotEmpty() }?.let { return it }
        o.optJSONArray("m.text")?.let { a -> if (a.length() > 0) return a.getJSONObject(0).optString("body") }
        return o.optString("body")
    }

    fun poll(content: JSONObject): Poll? {
        val p = content.optJSONObject(U_START) ?: content.optJSONObject("m.poll") ?: return null
        val a = p.optJSONArray("answers") ?: return null
        val answers = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            o.optString("id").ifEmpty { o.optString("m.id") } to text(o)
        }.filter { it.first.isNotEmpty() }
        val kind = p.optString("kind")
        return Poll(
            text(p.optJSONObject("question")), answers, p.optInt("max_selections", 1).coerceAtLeast(1),
            disclosed = kind != U_UNDISCLOSED && kind != "m.undisclosed", closes = content.optLong(CLOSES).takeIf { it > 0 },
        )
    }

    /** Il contenuto di un sondaggio nuovo (voto palese, una risposta), con il testo per chi non sa leggere i sondaggi. */
    fun newPoll(question: String, answers: List<String>, closes: Long?): JSONObject {
        val list = JSONArray()
        answers.forEachIndexed { i, a -> list.put(JSONObject().put("id", "a$i").put(U_TEXT, a)) }
        val fallback = question + "\n" + answers.mapIndexed { i, a -> "${i + 1}. $a" }.joinToString("\n")
        return JSONObject()
            .put(U_START, JSONObject().put("question", JSONObject().put(U_TEXT, question)).put("kind", U_DISCLOSED).put("max_selections", 1).put("answers", list))
            .put(U_TEXT, fallback).put("body", fallback)
            .apply { if (closes != null) put(CLOSES, closes) }
    }

    fun vote(poll: String, answers: List<String>): JSONObject = JSONObject()
        .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", poll))
        .put(U_RESPONSE, JSONObject().put("answers", JSONArray(answers)))

    fun endPoll(poll: String, text: String): JSONObject = JSONObject()
        .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", poll)).put(U_TEXT, text).put("body", text)

    fun reaction(target: String, key: String): JSONObject =
        JSONObject().put("m.relates_to", JSONObject().put("rel_type", "m.annotation").put("event_id", target).put("key", key))

    fun reply(text: String, target: String): JSONObject = JSONObject().put("msgtype", "m.text").put("body", text)
        .put("m.relates_to", JSONObject().put("m.in_reply_to", JSONObject().put("event_id", target)))

    fun edit(target: String, text: String): JSONObject = JSONObject().put("msgtype", "m.text").put("body", "* $text")
        .put("m.new_content", JSONObject().put("msgtype", "m.text").put("body", text))
        .put("m.relates_to", JSONObject().put("rel_type", "m.replace").put("event_id", target))

    /** I messaggi fissati di una stanza (stato «m.room.pinned_events»). */
    fun pinned(state: JSONObject?): List<String> =
        state?.optJSONArray("pinned")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

    fun pinnedState(ids: List<String>): JSONObject = JSONObject().put("pinned", JSONArray(ids))

    /** Una reazione come la mostra il fumetto: chiave, quante, e la mia (per toglierla), se c'è. */
    data class Chip(val key: String, val count: Int, val mine: String?)

    /** I risultati di un sondaggio: voti per risposta, le mie risposte, chi ha votato, se è chiuso. */
    data class Tally(val counts: Map<String, Int>, val mine: List<String>, val voters: Int, val ended: Boolean)

    /** Tutto ciò che tocca altri messaggi, raccolto per messaggio. Non è thread-safe: si usa dal thread principale. */
    class Store {
        private val reactions = HashMap<String, LinkedHashMap<String, Reaction>>()
        private val edits = HashMap<String, HashMap<String, Edit>>()
        private val votes = HashMap<String, HashMap<String, Vote>>()
        private val ends = HashMap<String, PollEnd>()
        private val seen = HashSet<String>()
        private val gone = HashSet<String>()

        /** Aggiunge un riferimento; vero se cambia qualcosa. */
        fun add(r: Rel): Boolean {
            if (!seen.add(r.id)) return false
            when (r) {
                is Reaction -> {
                    if (r.id in gone) return false
                    val m = reactions.getOrPut(r.target) { LinkedHashMap() }
                    // Una persona, una reazione per chiave.
                    if (m.values.any { it.sender == r.sender && it.key == r.key }) return false
                    m[r.id] = r
                }
                is Edit -> {
                    // Per autore: la modifica di un altro non cancella quella di chi ha scritto (e non si mostra).
                    val m = edits.getOrPut(r.target) { HashMap() }
                    val old = m[r.sender]
                    if (old == null || r.time >= old.time) m[r.sender] = r else return false
                }
                is Redaction -> {
                    gone += r.target
                    reactions.values.forEach { it.remove(r.target) }
                    votes.values.forEach { m -> m.entries.removeAll { it.value.id == r.target } }
                    edits.values.forEach { m -> m.entries.removeAll { it.value.id == r.target } }
                }
                is Vote -> {
                    val m = votes.getOrPut(r.poll) { HashMap() }
                    val old = m[r.sender]
                    if (old == null || r.time >= old.time) m[r.sender] = r else return false
                }
                is PollEnd -> ends[r.poll] = r
            }
            return true
        }

        fun redacted(id: String) = id in gone

        /** Chi ha messo la reazione [key] (la presa visione: ✅). */
        fun reactors(target: String, key: String): Set<String> =
            reactions[target]?.values?.filter { it.key == key }?.map { it.sender }?.toSet() ?: emptySet()

        fun chips(target: String, me: String): List<Chip> {
            val m = reactions[target] ?: return emptyList()
            return m.values.groupBy { it.key }.map { (k, list) -> Chip(k, list.size, list.firstOrNull { it.sender == me }?.id) }
        }

        /** Il testo modificato, se l'ha modificato chi l'ha scritto (gli altri non possono). */
        fun edited(target: String, originalSender: String): String? = edits[target]?.get(originalSender)?.body

        /**
         * Valgono l'ultimo voto di ognuno, dato prima della chiusura (fine mandata o ora di chiusura passata); le risposte
         * che non ci sono nel sondaggio non contano.
         */
        fun tally(pollId: String, poll: Poll, me: String, now: Long): Tally {
            val end = ends[pollId]?.time ?: poll.closes?.takeIf { it <= now }
            val valid = votes[pollId].orEmpty().values.filter { end == null || it.time <= end }
                .map { v -> v.sender to v.answers.filter { a -> poll.answers.any { it.first == a } }.take(poll.max) }
                .filter { it.second.isNotEmpty() }
            val counts = poll.answers.associate { (id, _) -> id to valid.count { id in it.second } }
            return Tally(counts, valid.firstOrNull { it.first == me }?.second.orEmpty(), valid.size, end != null)
        }

        /** Vero se il sondaggio ha già la fine (per non mandarla due volte). */
        fun endSent(pollId: String) = pollId in ends
    }
}
