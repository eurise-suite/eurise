package eu.eurise.mail

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Fuori orario per la posta di lavoro (tavola SpaFuoriOrario), senza Android: si prova con i test.
 *
 * Le caselle segnate «di lavoro» fuori dall'orario non suonano: le email aspettano in silenzio e all'inizio dell'orario
 * arriva il riepilogo («Ti aspettano 3 email di lavoro»), tranne quelle che passano lo stesso (mittenti scelti, una parola
 * nell'oggetto come «urgente»).
 */
object MailOffHours {
    data class Rules(
        val work: Set<String> = emptySet(),
        val days: Set<Int> = setOf(1, 2, 3, 4, 5),
        val from: LocalTime = LocalTime.of(8, 30),
        val to: LocalTime = LocalTime.of(18, 30),
        val people: Set<String> = emptySet(),
        val words: Set<String> = setOf("urgente"),
    )

    fun quiet(r: Rules, now: LocalDateTime): Boolean =
        now.dayOfWeek.value !in r.days || now.toLocalTime().isBefore(r.from) || !now.toLocalTime().isBefore(r.to)

    /** Il prossimo inizio dell'orario (null se si è già in orario). */
    fun nextStart(r: Rules, now: LocalDateTime): LocalDateTime? {
        if (!quiet(r, now)) return null
        var d = if (now.dayOfWeek.value in r.days && now.toLocalTime().isBefore(r.from)) now.toLocalDate() else now.toLocalDate().plusDays(1)
        repeat(8) { if (d.dayOfWeek.value !in r.days) d = d.plusDays(1) }
        return d.atTime(r.from)
    }

    fun passes(r: Rules, fromAddress: String, subject: String): Boolean {
        if (r.people.any { it.equals(fromAddress, ignoreCase = true) }) return true
        val s = subject.lowercase()
        return r.words.any { w -> w.isNotBlank() && Regex("(^|\\W)" + Regex.escape(w.lowercase()) + "($|\\W)").containsMatchIn(s) }
    }

    fun encode(r: Rules): String = JSONObject().put("work", JSONArray(r.work.sorted())).put("days", JSONArray(r.days.sorted()))
        .put("from", r.from.toString()).put("to", r.to.toString()).put("people", JSONArray(r.people.sorted())).put("words", JSONArray(r.words.sorted())).toString()

    fun decode(s: String?): Rules = runCatching {
        val o = JSONObject(s ?: return Rules())
        fun set(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() }
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet() }?.ifEmpty { null }
        val from = runCatching { LocalTime.parse(o.optString("from")) }.getOrDefault(LocalTime.of(8, 30))
        val to = runCatching { LocalTime.parse(o.optString("to")) }.getOrDefault(LocalTime.of(18, 30))
        Rules(set("work").orEmpty(), days ?: setOf(1, 2, 3, 4, 5), from, if (to.isAfter(from)) to else LocalTime.of(18, 30), set("people").orEmpty(), set("words") ?: setOf("urgente"))
    }.getOrDefault(Rules())
}
