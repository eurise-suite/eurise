package eu.eurise.core.insieme

import org.json.JSONObject
import java.time.LocalDate

/**
 * Stato e assenza (tavola LavStato), senza Android: si prova con i test. Il tuo stato adesso (in cantiere, in riunione, in
 * viaggio, libero) e l'assenza (in ferie dal… al…, chi sostituisce, il messaggio): la stessa risposta automatica in Chat e in
 * Email, una volta per persona e per giorno. Le ferie della squadra si vedono nel gruppo di lavoro.
 */
object Assenza {
    enum class Now { Libero, Cantiere, Riunione, Viaggio }

    data class State(
        val now: Now = Now.Libero, val until: String = "", val from: Long = 0, val to: Long = 0, val substitute: String = "",
        val message: String = "", val chat: Boolean = true, val email: Boolean = true,
    ) {
        fun away(day: LocalDate) = from > 0 && to >= from && day.toEpochDay() in from..to
    }

    fun encode(s: State): JSONObject = JSONObject().put("now", s.now.name).put("until", s.until).put("from", s.from).put("to", s.to)
        .put("sub", s.substitute).put("msg", s.message).put("chat", s.chat).put("email", s.email)

    fun decode(o: JSONObject?): State? = runCatching {
        o ?: return null
        if (!o.has("now")) return null
        State(runCatching { Now.valueOf(o.optString("now")) }.getOrDefault(Now.Libero), o.optString("until"), o.optLong("from"), o.optLong("to"),
            o.optString("sub"), o.optString("msg"), o.optBoolean("chat", true), o.optBoolean("email", true))
    }.getOrNull()

    /** Il testo della risposta automatica: quello scritto, o «Sono in ferie fino al 27 dicembre. Per urgenze: Paolo». */
    fun reply(s: State, untilLabel: String, defaultText: (String, String) -> String): String = s.message.ifBlank { defaultText(untilLabel, s.substitute) }

    /** Quanti della squadra sono fuori quel giorno (per «Il 23 siete fuori in due»). */
    fun outOn(states: Collection<State>, day: LocalDate) = states.count { it.away(day) }
}
