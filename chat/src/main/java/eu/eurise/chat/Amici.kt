package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Viaggio di gruppo e regalo collettivo (tavole AmiViaggio, AmiRegalo), senza Android: si prova con i test. Sono stati della
 * stanza, gli stessi per tutti e cifrati: l'itinerario (giorno, ora, cosa, dove), e il regalo (per chi, idee votate, quota, chi ha
 * versato, chi compra, il biglietto con una riga di ognuno).
 */
object Amici {
    const val TRIP = "eu.eurise.trip"
    const val GIFT = "eu.eurise.gift"

    data class Stop(val id: String, val day: Long, val time: String, val title: String, val note: String = "", val place: String = "", val by: String = "")

    fun sortedStops(list: List<Stop>) = list.sortedWith(compareBy({ it.day }, { it.time.ifBlank { "99" } }))

    /** La prossima tappa da oggi (o quella in corso oggi). */
    fun next(list: List<Stop>, today: Long, nowTime: String): Stop? =
        sortedStops(list).firstOrNull { it.day > today || (it.day == today && (it.time.isBlank() || it.time >= nowTime)) }

    fun encodeTrip(list: List<Stop>): JSONObject = JSONObject().put("stops", JSONArray().apply {
        list.forEach { s -> put(JSONObject().put("id", s.id).put("day", s.day).put("time", s.time).put("title", s.title).put("note", s.note).put("place", s.place).put("by", s.by)) }
    })

    fun decodeTrip(o: JSONObject?): List<Stop> = runCatching {
        val a = o?.optJSONArray("stops") ?: return emptyList()
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { x ->
            Stop(x.optString("id"), x.optLong("day"), x.optString("time"), x.optString("title"), x.optString("note"), x.optString("place"), x.optString("by"))
                .takeIf { it.id.isNotEmpty() && it.title.isNotBlank() } } }
    }.getOrDefault(emptyList())

    data class Idea(val id: String, val text: String, val cents: Long, val by: String, val votes: Set<String> = emptySet())
    data class Gift(
        val forName: String, val date: Long, val buyBy: Long, val buyer: String, val quota: Long, val ideas: List<Idea> = emptyList(),
        val paid: Set<String> = emptySet(), val lines: Map<String, String> = emptyMap(), val iban: String = "", val people: List<String> = emptyList(),
    )

    /** Un voto per persona: votare un'idea toglie il voto dalle altre; votare di nuovo la stessa lo toglie. */
    fun vote(g: Gift, idea: String, who: String): Gift {
        val had = g.ideas.firstOrNull { it.id == idea }?.votes?.contains(who) == true
        return g.copy(ideas = g.ideas.map { i -> i.copy(votes = if (i.id == idea && !had) i.votes + who else i.votes - who) })
    }

    fun winner(g: Gift): Idea? = g.ideas.maxWithOrNull(compareBy<Idea> { it.votes.size }.thenByDescending { -it.cents })?.takeIf { it.votes.isNotEmpty() }

    /** La quota: il prezzo dell'idea più votata diviso fra tutti (arrotondato ai 50 centesimi in su), se non è scritta a mano. */
    fun quota(g: Gift, people: Int): Long {
        if (g.quota > 0) return g.quota
        val price = winner(g)?.cents ?: return 0
        if (people <= 0) return 0
        val q = (price + people - 1) / people
        return ((q + 49) / 50) * 50
    }

    fun togglePaid(g: Gift, who: String) = g.copy(paid = if (who in g.paid) g.paid - who else g.paid + who)

    fun encodeGift(g: Gift): JSONObject = JSONObject().put("for", g.forName).put("date", g.date).put("buyBy", g.buyBy).put("buyer", g.buyer).put("quota", g.quota)
        .put("iban", g.iban).put("people", JSONArray(g.people)).put("paid", JSONArray(g.paid.sorted()))
        .put("lines", JSONObject().apply { g.lines.forEach { (k, v) -> put(k, v) } })
        .put("ideas", JSONArray().apply { g.ideas.forEach { i -> put(JSONObject().put("id", i.id).put("text", i.text).put("cents", i.cents).put("by", i.by).put("votes", JSONArray(i.votes.sorted()))) } })

    fun decodeGift(o: JSONObject?): Gift? = runCatching {
        o ?: return null
        if (o.optString("for").isBlank()) return null
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }.orEmpty()
        val ideas = o.optJSONArray("ideas")?.let { a -> (0 until a.length()).mapNotNull { j -> a.optJSONObject(j)?.let { x ->
            Idea(x.optString("id"), x.optString("text"), x.optLong("cents"), x.optString("by"),
                x.optJSONArray("votes")?.let { v -> (0 until v.length()).map { v.optString(it) }.toSet() }.orEmpty()) } } }.orEmpty()
        val lines = o.optJSONObject("lines")?.let { l -> l.keys().asSequence().associateWith { l.optString(it) } }.orEmpty()
        Gift(o.optString("for"), o.optLong("date"), o.optLong("buyBy"), o.optString("buyer"), o.optLong("quota"), ideas, strings("paid").toSet(), lines,
            o.optString("iban"), strings("people"))
    }.getOrNull()
}
