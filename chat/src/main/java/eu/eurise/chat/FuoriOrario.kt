package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fuori orario (tavola SpaFuoriOrario), senza Android: si prova con i test.
 *
 * Il tuo orario di lavoro (giorni, inizio, fine): fuori da lì i messaggi dell'account di lavoro aspettano la mattina, in
 * silenzio, tranne quelli che passano lo stesso: persone scelte, una parola («urgente»), una stanza (il cantiere). Al
 * primo minuto di lavoro arriva un riepilogo («Ti aspettano 7 messaggi»). Lo stesso orario è quello che, se vuoi, gli
 * altri vedono quando ti scrivono (tavola ChatQuandoLavora).
 */
object FuoriOrario {
    data class Rules(
        val hours: Orari.Hours = Orari.work(),
        val people: Set<String> = emptySet(),
        val rooms: Set<String> = emptySet(),
        val words: Set<String> = setOf("urgente"),
    )

    fun quiet(r: Rules, now: ZonedDateTime): Boolean = Orari.nextStart(r.hours, now) != null

    /** Passa lo stesso: la persona, la stanza o una parola (anche dentro una frase, senza badare alle maiuscole). */
    fun passes(r: Rules, sender: String, roomId: String, text: String): Boolean {
        if (sender in r.people || roomId in r.rooms) return true
        val t = text.lowercase()
        return r.words.any { w -> w.isNotBlank() && Regex("(^|\\W)" + Regex.escape(w.lowercase()) + "($|\\W)").containsMatchIn(t) }
    }

    /** Le ore da proporre: inizio e fine con un tocco. */
    val STARTS = listOf(LocalTime.of(7, 30), LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30))
    val ENDS = listOf(LocalTime.of(16, 0), LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(18, 30), LocalTime.of(19, 0), LocalTime.of(20, 0))

    fun encode(r: Rules): String = JSONObject().put("hours", Orari.encode(r.hours)).put("people", JSONArray(r.people.sorted()))
        .put("rooms", JSONArray(r.rooms.sorted())).put("words", JSONArray(r.words.sorted())).toString()

    fun decode(s: String?, zone: String = ZoneId.systemDefault().id): Rules = runCatching {
        val o = JSONObject(s ?: return Rules(Orari.work(zone)))
        fun set(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() }
        Rules(Orari.decode(o.optJSONObject("hours")) ?: Orari.work(zone), set("people").orEmpty(), set("rooms").orEmpty(), set("words") ?: setOf("urgente"))
    }.getOrDefault(Rules(Orari.work(zone)))
}
