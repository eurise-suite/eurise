package eu.eurise.chat

import org.json.JSONObject
import java.time.LocalDate

/**
 * Gruppi a tempo (tavola ChatGruppoTempo), senza Android: si prova con i test.
 *
 * Uno stato della stanza [TYPE] dice da quando a quando vale il gruppo (un viaggio, una festa, un trasloco) e, se c'è,
 * quando finisce la cosa per cui è nato ([end]). Finita, in cima compare il riepilogo; passato [to], il gruppo va
 * nell'archivio (sul telefono di ognuno, che può tenerlo aperto con un tocco).
 */
object Tempo {
    const val TYPE = "eu.eurise.timebox"

    data class Box(val from: LocalDate, val to: LocalDate, val end: LocalDate? = null) {
        /** Da quando si mostra il riepilogo: il giorno dopo la fine della cosa, o due giorni prima della scadenza. */
        val summaryFrom: LocalDate get() = end?.plusDays(1) ?: to.minusDays(2)
    }

    fun encode(b: Box): JSONObject = JSONObject().put("from", b.from.toString()).put("to", b.to.toString()).also { o -> b.end?.let { o.put("end", it.toString()) } }

    fun decode(o: JSONObject?): Box? = runCatching {
        o ?: return null
        if (!o.has("to")) return null
        val from = LocalDate.parse(o.optString("from"))
        val to = LocalDate.parse(o.optString("to"))
        if (to.isBefore(from)) return null
        val end = o.optString("end").ifEmpty { null }?.let(LocalDate::parse)?.takeIf { !it.isBefore(from) && !it.isAfter(to) }
        Box(from, to, end)
    }.getOrNull()

    fun showSummary(b: Box, today: LocalDate) = !today.isBefore(b.summaryFrom) && !today.isAfter(b.to)
    fun archived(b: Box, today: LocalDate) = today.isAfter(b.to)

    /** Quello che il gruppo ha raccolto: foto, spese, documenti. */
    data class Recap(val photos: Int, val expenses: Int, val files: Int)

    fun recap(events: List<Event>) = Recap(
        events.count { it.kind == Event.Kind.Image },
        events.count { it.kind == Event.Kind.Expense },
        events.count { it.kind == Event.Kind.File },
    )
}
