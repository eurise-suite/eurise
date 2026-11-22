package eu.eurise.chat

import eu.eurise.core.insieme.Disponibilita
import org.json.JSONArray
import org.json.JSONObject

/**
 * L'agenda con l'assistente (tavola InsAssistente), senza Android: si prova con i test.
 *
 * Nella chat a due con la segreteria si mette uno stato della stanza ([TYPE], con la chiave del proprio id) con gli orari
 * occupati delle prossime due settimane: per ogni calendario si sceglie se non va mai, va come «occupato» o con i titoli. La
 * segreteria vede i liberi e propone (una scheda «Appuntamento proposto»); tu confermi con un tocco e l'appuntamento entra nel
 * tuo calendario. Il personale e la famiglia di base non escono mai.
 */
object Assistente {
    const val TYPE = "eu.eurise.agenda"
    const val DAYS = 14
    enum class Mode { Never, Busy, Titles }

    data class Block(val start: Long, val end: Long, val title: String = "")
    data class Shared(val updated: Long, val from: Long, val to: Long, val blocks: List<Block>)

    /** Dai calendari agli occupati che escono: niente per «mai», senza titolo per «occupato». */
    fun blocks(items: List<Pair<Long, Disponibilita.Busy>>, modes: Map<Long, Mode>): List<Block> = items.mapNotNull { (cal, b) ->
        when (modes[cal] ?: Mode.Never) {
            Mode.Never -> null
            Mode.Busy -> Block(b.start, b.end)
            Mode.Titles -> Block(b.start, b.end, b.title.take(80))
        }
    }.sortedBy { it.start }

    fun encode(s: Shared): JSONObject = JSONObject().put("updated", s.updated).put("from", s.from).put("to", s.to)
        .put("blocks", JSONArray().apply { s.blocks.forEach { b -> put(JSONObject().put("s", b.start).put("e", b.end).apply { if (b.title.isNotBlank()) put("t", b.title) }) } })

    fun decode(o: JSONObject?): Shared? = runCatching {
        o ?: return null
        if (!o.has("blocks")) return null
        val a = o.getJSONArray("blocks")
        Shared(o.optLong("updated"), o.optLong("from"), o.optLong("to"),
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Block(it.optLong("s"), it.optLong("e"), it.optString("t")) }.takeIf { it != null && it.end > it.start } })
    }.getOrNull()

    fun encodeModes(m: Map<Long, Mode>): String = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v.name) } }.toString()

    fun decodeModes(s: String?): Map<Long, Mode> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { id -> runCatching { Mode.valueOf(o.getString(k)) }.getOrNull()?.let { id to it } } }.toMap()
    }.getOrDefault(emptyMap())
}
