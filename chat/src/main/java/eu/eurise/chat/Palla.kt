package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Passa la palla (tavole InsPalla e InsPallaChat), senza Android: si prova con i test.
 *
 * Da un messaggio si passa a un collega «la palla»: una riga sul da farsi, una scadenza, e il messaggio citato. Arriva
 * nella chat a due con lui come messaggio con il campo [BALL] (chi usa Element legge il testo); lui risponde «In corso» o
 * «Fatto» con un messaggio [STATUS] che si riferisce alla palla. Chi l'ha passata tiene sul telefono l'elenco «Le palle
 * che hai passato», con lo stato: passata, vista (conferma di lettura), in corso, fatta, o ferma da un giorno.
 */
object Palla {
    const val BALL = "eu.eurise.ball"
    const val STATUS = "eu.eurise.ball.status"
    const val QUIET_MS = 86_400_000L

    enum class State { Passed, Seen, Working, Done, Taken }

    data class Ball(
        val id: String, val account: String, val toRoom: String, val toUser: String, val toName: String,
        val quote: String, val task: String, val due: Long, val sentAt: Long, val eventId: String = "",
        val state: State = State.Passed, val stateAt: Long = 0,
    )

    fun content(id: String, fromName: String, quote: String, task: String, dueLabel: String?, fallback: String): JSONObject {
        val body = fallback + (if (quote.isNotBlank()) "\n«" + quote.trim().take(300) + "»" else "") +
            (if (task.isNotBlank()) "\n→ " + task.trim() else "") + (dueLabel?.let { "\n⏱ $it" } ?: "")
        return JSONObject().put("msgtype", "m.text").put("body", body)
            .put(BALL, JSONObject().put("id", id).put("from", fromName).put("quote", quote.trim().take(500)).put("task", task.trim()).put("due", dueLabel ?: ""))
    }

    data class Card(val id: String, val from: String, val quote: String, val task: String, val due: String)

    fun card(content: JSONObject?): Card? {
        val b = content?.optJSONObject(BALL) ?: return null
        val id = b.optString("id").ifEmpty { return null }
        return Card(id, b.optString("from"), b.optString("quote"), b.optString("task"), b.optString("due"))
    }

    /** La risposta di chi ha preso la palla: «In corso» o «Fatto» (testo per Element in [body]). */
    fun statusContent(ballEvent: String, id: String, state: State, body: String): JSONObject = JSONObject()
        .put("msgtype", "m.notice").put("body", body)
        .put(STATUS, JSONObject().put("id", id).put("state", state.name))
        .put("m.relates_to", JSONObject().put("m.in_reply_to", JSONObject().put("event_id", ballEvent)))

    fun status(content: JSONObject?): Pair<String, State>? {
        val s = content?.optJSONObject(STATUS) ?: return null
        val st = runCatching { State.valueOf(s.optString("state")) }.getOrNull() ?: return null
        return s.optString("id").ifEmpty { return null } to st
    }

    /** Lo stato da mostrare: quello ricevuto, se no «vista» se l'ha letta dopo l'invio. */
    fun shown(b: Ball, readAt: Long): State = when {
        b.state != State.Passed -> b.state
        readAt >= b.sentAt && b.sentAt > 0 -> State.Seen
        else -> State.Passed
    }

    /** Ferma da un giorno: nessuna risposta (né «in corso» né «fatto»). */
    fun stale(b: Ball, readAt: Long, now: Long): Boolean = shown(b, readAt) in setOf(State.Passed, State.Seen) && now - b.sentAt >= QUIET_MS

    /** Applica le risposte arrivate (id → stato, ora). */
    fun update(list: List<Ball>, updates: Map<String, Pair<State, Long>>): List<Ball> = list.map { b ->
        val u = updates[b.id] ?: return@map b
        if (u.second >= b.stateAt && b.state != State.Taken) b.copy(state = u.first, stateAt = u.second) else b
    }

    fun encode(list: List<Ball>): String = JSONArray().apply {
        list.forEach { b ->
            put(JSONObject().put("id", b.id).put("account", b.account).put("room", b.toRoom).put("user", b.toUser).put("name", b.toName)
                .put("quote", b.quote).put("task", b.task).put("due", b.due).put("sent", b.sentAt).put("event", b.eventId)
                .put("state", b.state.name).put("stateAt", b.stateAt))
        }
    }.toString()

    fun decode(s: String?): List<Ball> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Ball(o.optString("id"), o.optString("account"), o.optString("room"), o.optString("user"), o.optString("name"),
                o.optString("quote"), o.optString("task"), o.optLong("due"), o.optLong("sent"), o.optString("event"),
                runCatching { State.valueOf(o.optString("state")) }.getOrDefault(State.Passed), o.optLong("stateAt"))
                .takeIf { it.id.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}
