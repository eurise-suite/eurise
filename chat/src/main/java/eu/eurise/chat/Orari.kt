package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * L'orario di lavoro di chi ti scrive (tavola ChatQuandoLavora), senza Android: si prova con i test.
 *
 * Chi lo vuole (interruttore nel foglio dell'account di lavoro, spento di base) mette nelle chat a due uno stato
 * [TYPE] con la chiave del suo id: giorni, inizio, fine e fuso. Chi gli scrive fuori orario vede «fuori orario fino alle
 * 9» e il messaggio parte quando l'altro comincia, a meno di «Manda subito».
 */
object Orari {
    const val TYPE = "eu.eurise.hours"

    /** [days]: 1 = lunedì … 7 = domenica. */
    data class Hours(val days: Set<Int>, val from: LocalTime, val to: LocalTime, val zone: String)

    fun encode(h: Hours): JSONObject = JSONObject().put("days", JSONArray(h.days.sorted())).put("from", Programma.time(h.from).padStart(5, '0'))
        .put("to", Programma.time(h.to).padStart(5, '0')).put("tz", h.zone)

    fun decode(o: JSONObject?): Hours? = runCatching {
        o ?: return null
        val d = o.optJSONArray("days") ?: return null
        val days = (0 until d.length()).map { d.optInt(it) }.filter { it in 1..7 }.toSet()
        if (days.isEmpty()) return null
        val from = LocalTime.parse(o.optString("from").padStart(5, '0'))
        val to = LocalTime.parse(o.optString("to").padStart(5, '0'))
        if (!to.isAfter(from)) return null
        Hours(days, from, to, o.optString("tz").ifEmpty { "Europe/Rome" }.also { ZoneId.of(it) })
    }.getOrNull()

    /** L'orario di [Quiet] (feriali 8–19), quello che pubblica l'account di lavoro. */
    fun work(zone: String = ZoneId.systemDefault().id) = Hours(setOf(1, 2, 3, 4, 5), LocalTime.of(Quiet.TO_HOUR, 0), LocalTime.of(Quiet.FROM_HOUR, 0), zone)

    /** Quando comincia a lavorare: null se è già in orario adesso ([now] nel suo fuso). */
    fun nextStart(h: Hours, now: ZonedDateTime): ZonedDateTime? {
        val t = now.withZoneSameInstant(ZoneId.of(h.zone))
        val inDay = t.dayOfWeek.value in h.days
        if (inDay && !t.toLocalTime().isBefore(h.from) && t.toLocalTime().isBefore(h.to)) return null
        var d = if (inDay && t.toLocalTime().isBefore(h.from)) t.toLocalDate() else t.toLocalDate().plusDays(1)
        repeat(8) { if (d.dayOfWeek.value !in h.days) d = d.plusDays(1) }
        return d.atTime(h.from).atZone(ZoneId.of(h.zone))
    }

    /** «9–18», «8:30–17». */
    fun label(h: Hours): String {
        fun t(x: LocalTime) = if (x.minute == 0) "${x.hour}" else Programma.time(x)
        return t(h.from) + "–" + t(h.to)
    }
}
