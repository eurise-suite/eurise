package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Approvazioni (tavola LavApprova), senza Android: si prova con i test.
 *
 * Una richiesta (preventivo, spesa, ferie) è un messaggio della chat con il campo [REQUEST]: titolo, importo, file citato.
 * Chi la riceve risponde con un tocco: Approva, Chiedi modifica (con una riga), Rifiuta; la risposta è un messaggio
 * [ANSWER] che si riferisce alla richiesta (chi usa Element legge il testo). Chi chiede tiene sul telefono «Le tue
 * richieste», con lo stato e «Sollecita» quando aspetta da più di un giorno.
 */
object Approva {
    const val REQUEST = "eu.eurise.approval"
    const val ANSWER = "eu.eurise.approval.answer"
    const val QUIET_MS = 86_400_000L

    enum class Verdict { Approved, Change, Rejected }

    data class Card(val id: String, val from: String, val title: String, val amount: String, val file: String, val note: String)

    data class Request(
        val id: String, val account: String, val room: String, val roomName: String, val title: String, val amount: String,
        val sentAt: Long, val eventId: String = "", val verdict: Verdict? = null, val by: String = "", val note: String = "", val at: Long = 0,
    )

    fun content(id: String, from: String, title: String, amount: String, file: String, note: String, fallback: String): JSONObject {
        val body = fallback + "\n" + title.trim() + (if (amount.isNotBlank()) " · " + amount.trim() else "") +
            (if (file.isNotBlank()) "\n📎 " + file.trim() else "") + (if (note.isNotBlank()) "\n" + note.trim() else "")
        return JSONObject().put("msgtype", "m.text").put("body", body)
            .put(REQUEST, JSONObject().put("id", id).put("from", from).put("title", title.trim()).put("amount", amount.trim())
                .put("file", file.trim()).put("note", note.trim()))
    }

    fun card(content: JSONObject?): Card? {
        val r = content?.optJSONObject(REQUEST) ?: return null
        val id = r.optString("id").ifEmpty { return null }
        return Card(id, r.optString("from"), r.optString("title"), r.optString("amount"), r.optString("file"), r.optString("note"))
    }

    fun answerContent(requestEvent: String, id: String, verdict: Verdict, note: String, body: String): JSONObject = JSONObject()
        .put("msgtype", "m.text").put("body", body + (if (note.isNotBlank()) ": «" + note.trim() + "»" else ""))
        .put(ANSWER, JSONObject().put("id", id).put("verdict", verdict.name).put("note", note.trim()))
        .put("m.relates_to", JSONObject().put("m.in_reply_to", JSONObject().put("event_id", requestEvent)))

    fun answer(content: JSONObject?): Triple<String, Verdict, String>? {
        val a = content?.optJSONObject(ANSWER) ?: return null
        val v = runCatching { Verdict.valueOf(a.optString("verdict")) }.getOrNull() ?: return null
        return Triple(a.optString("id").ifEmpty { return null }, v, a.optString("note"))
    }

    /** In attesa da più di un giorno: si può sollecitare. */
    fun waiting(r: Request, now: Long) = r.verdict == null && now - r.sentAt >= QUIET_MS

    /** Applica le risposte arrivate (id → verdetto, chi, nota, ora); la più recente vince (una modifica chiesta e poi l'ok). */
    fun update(list: List<Request>, answers: Map<String, Answer>): List<Request> = list.map { r ->
        val a = answers[r.id] ?: return@map r
        if (a.at >= r.at) r.copy(verdict = a.verdict, by = a.by, note = a.note, at = a.at) else r
    }

    data class Answer(val verdict: Verdict, val by: String, val note: String, val at: Long)

    fun encode(list: List<Request>): String = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().put("id", r.id).put("account", r.account).put("room", r.room).put("roomName", r.roomName).put("title", r.title)
                .put("amount", r.amount).put("sent", r.sentAt).put("event", r.eventId).put("verdict", r.verdict?.name ?: "")
                .put("by", r.by).put("note", r.note).put("at", r.at))
        }
    }.toString()

    fun decode(s: String?): List<Request> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Request(o.optString("id"), o.optString("account"), o.optString("room"), o.optString("roomName"), o.optString("title"),
                o.optString("amount"), o.optLong("sent"), o.optString("event"),
                runCatching { Verdict.valueOf(o.optString("verdict")) }.getOrNull(), o.optString("by"), o.optString("note"), o.optLong("at"))
                .takeIf { it.id.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}
