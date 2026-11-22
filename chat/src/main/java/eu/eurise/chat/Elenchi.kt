package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Elenchi condivisi (tavola InsElenchi), senza Android: si prova con i test.
 *
 * Spesa, casa, ufficio: uno stato della stanza ([TYPE]), lo stesso per tutti e aggiornato mentre lo si cambia (chi è al
 * supermercato vede la lista che cambia). Ogni voce dice chi l'ha aggiunta, chi la prende («Lo prendo io») e chi l'ha fatta,
 * con l'ora. Le voci fatte scendono in fondo e dopo una settimana se ne vanno.
 */
object Elenchi {
    const val TYPE = "eu.eurise.lists"
    const val KEEP_DONE_MS = 7L * 86_400_000L

    data class Item(val id: String, val text: String, val by: String, val at: Long, val taker: String = "", val doneBy: String = "", val doneAt: Long = 0, val from: String = "")
    data class Lista(val id: String, val name: String, val items: List<Item> = emptyList())

    fun defaults(names: List<String>): List<Lista> = names.mapIndexed { i, n -> Lista("l$i", n) }

    fun add(lists: List<Lista>, list: String, text: String, by: String, at: Long, from: String = ""): List<Lista> = lists.map { l ->
        if (l.id != list || text.isBlank()) l
        else if (l.items.any { it.doneAt == 0L && it.text.equals(text.trim(), ignoreCase = true) }) l
        else l.copy(items = l.items + Item("i$at" + l.items.size, text.trim(), by, at, from = from))
    }

    private fun change(lists: List<Lista>, list: String, item: String, f: (Item) -> Item): List<Lista> =
        lists.map { l -> if (l.id != list) l else l.copy(items = l.items.map { if (it.id == item) f(it) else it }) }

    /** «Lo prendo io» (o lo lascia, se l'aveva preso lui). */
    fun take(lists: List<Lista>, list: String, item: String, who: String) = change(lists, list, item) { it.copy(taker = if (it.taker == who) "" else who) }

    /** Fatta (o di nuovo da fare). */
    fun done(lists: List<Lista>, list: String, item: String, who: String, at: Long) =
        change(lists, list, item) { if (it.doneAt > 0) it.copy(doneBy = "", doneAt = 0) else it.copy(doneBy = who, doneAt = at) }

    fun remove(lists: List<Lista>, list: String, item: String) = lists.map { l -> if (l.id != list) l else l.copy(items = l.items.filterNot { it.id == item }) }

    fun addList(lists: List<Lista>, name: String, at: Long) = if (name.isBlank()) lists else lists + Lista("l$at", name.trim())

    /** Da fare in cima (prima quelle prese da qualcuno), poi le fatte dalla più recente; via le fatte da più di una settimana. */
    fun shown(l: Lista, now: Long): List<Item> = l.items.filter { it.doneAt == 0L || now - it.doneAt < KEEP_DONE_MS }
        .sortedWith(compareBy<Item>({ it.doneAt > 0 }, { if (it.doneAt > 0) -it.doneAt else 0L }, { it.taker.isEmpty() }, { it.at }))

    fun open(l: Lista) = l.items.count { it.doneAt == 0L }

    /** Il testo per chi non usa la Chat (email o Element): le voci da fare, una per riga. */
    fun text(l: Lista): String = l.name + "\n" + l.items.filter { it.doneAt == 0L }.joinToString("\n") { "☐ " + it.text }

    fun encode(lists: List<Lista>, now: Long): JSONObject = JSONObject().put("lists", JSONArray().apply {
        lists.forEach { l ->
            put(JSONObject().put("id", l.id).put("name", l.name).put("items", JSONArray().apply {
                l.items.filter { it.doneAt == 0L || now - it.doneAt < KEEP_DONE_MS }.forEach { i ->
                    put(JSONObject().put("id", i.id).put("text", i.text).put("by", i.by).put("at", i.at).put("taker", i.taker)
                        .put("doneBy", i.doneBy).put("doneAt", i.doneAt).put("from", i.from))
                }
            }))
        }
    })

    fun decode(o: JSONObject?): List<Lista> = runCatching {
        val a = o?.optJSONArray("lists") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val l = a.optJSONObject(i) ?: return@mapNotNull null
            val items = l.optJSONArray("items")?.let { x ->
                (0 until x.length()).mapNotNull { j -> x.optJSONObject(j)?.let { t ->
                    Item(t.optString("id"), t.optString("text"), t.optString("by"), t.optLong("at"), t.optString("taker"), t.optString("doneBy"), t.optLong("doneAt"), t.optString("from"))
                        .takeIf { it.id.isNotEmpty() && it.text.isNotBlank() } } }
            }.orEmpty()
            Lista(l.optString("id"), l.optString("name"), items).takeIf { it.id.isNotEmpty() && it.name.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}
