package eu.eurise.chat

import eu.eurise.core.insieme.Scheda
import org.json.JSONArray
import org.json.JSONObject

/**
 * Affida a… (tavola InsAffida), senza Android: si prova con i test.
 *
 * Una cosa da fare passa a un collaboratore con la scadenza e una riga: arriva come scheda ([Scheda], tipo Affida) nella chat a
 * due con lui, o per email se non usa Eurise. Lui risponde Non posso, Vista o Fatta; se non risponde entro il tempo scelto la
 * cosa torna a te. L'elenco «Le cose affidate» resta sul telefono di chi affida.
 */
object Affida {
    data class Given(
        val id: String, val account: String, val room: String, val toUser: String, val toName: String, val title: String,
        val due: String, val sentAt: Long, val eventId: String = "", val backAfter: Long = 0,
        val state: Scheda.State? = null, val stateAt: Long = 0, val note: String = "", val email: String = "", val retaken: Boolean = false,
    ) { val byMail get() = email.isNotBlank() }

    /** Lo stato da mostrare: tornata a te (ripresa o senza risposta), la risposta, vista (letta), affidata. */
    fun shown(g: Given, readAt: Long, now: Long): Scheda.State? = when {
        g.retaken -> Scheda.State.Back
        Scheda.back(g.state, g.sentAt, g.backAfter, now) -> Scheda.State.Back
        else -> Scheda.shown(g.state, g.sentAt, readAt)
    }

    /** Le risposte arrivate (id → stato, ora, riga): vale la più recente. */
    fun update(list: List<Given>, updates: Map<String, Triple<Scheda.State, Long, String>>): List<Given> = list.map { g ->
        val u = updates[g.id] ?: return@map g
        if (u.second >= g.stateAt && !g.retaken) g.copy(state = u.first, stateAt = u.second, note = u.third.ifBlank { g.note }) else g
    }

    fun encode(list: List<Given>): String = JSONArray().apply {
        list.forEach { g ->
            put(JSONObject().put("id", g.id).put("account", g.account).put("room", g.room).put("user", g.toUser).put("name", g.toName).put("title", g.title)
                .put("due", g.due).put("sent", g.sentAt).put("event", g.eventId).put("back", g.backAfter).put("state", g.state?.name ?: "")
                .put("stateAt", g.stateAt).put("note", g.note).put("email", g.email).put("retaken", g.retaken))
        }
    }.toString()

    fun decode(s: String?): List<Given> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Given(o.optString("id"), o.optString("account"), o.optString("room"), o.optString("user"), o.optString("name"), o.optString("title"),
                o.optString("due"), o.optLong("sent"), o.optString("event"), o.optLong("back"),
                o.optString("state").takeIf { it.isNotEmpty() }?.let { st -> runCatching { Scheda.State.valueOf(st) }.getOrNull() },
                o.optLong("stateAt"), o.optString("note"), o.optString("email"), o.optBoolean("retaken")).takeIf { it.id.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}
