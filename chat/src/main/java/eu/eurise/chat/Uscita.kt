package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject

/**
 * Organizza un'uscita (tavola AmiUscita), senza Android: si prova con i test.
 *
 * La proposta è un messaggio [CARD] (titolo, due o tre date, il luogo, quando si chiude). Ogni risposta è un messaggio
 * [ANSWER] riferito alla proposta: le date che vanno bene (con, se la persona lo vuole, quelle libere anche secondo il suo
 * calendario: lo legge solo il suo telefono, al gruppo arriva solo «libero»), i passaggi in macchina (offro N posti,
 * salgo con…, cerco un passaggio da…, la prendo io) e, per chi l'ha proposta, «Fissa». Lo stato si ricava mettendo in fila
 * le risposte: per ogni persona vale l'ultima.
 */
object Uscita {
    const val CARD = "eu.eurise.outing"
    const val ANSWER = "eu.eurise.outing.answer"

    data class Option(val label: String, val at: Long)
    data class Card(val id: String, val from: String, val title: String, val options: List<Option>, val place: String, val address: String, val closes: Long)

    fun content(id: String, from: String, title: String, options: List<Option>, place: String, address: String, closes: Long, fallback: String): JSONObject {
        val body = fallback + "\n" + title.trim() + "\n" + options.joinToString("\n") { "· " + it.label } +
            (if (place.isNotBlank()) "\n📍 " + place.trim() + (if (address.isNotBlank()) ", " + address.trim() else "") else "")
        return JSONObject().put("msgtype", CARD).put("body", body).put(CARD, JSONObject().put("id", id).put("from", from).put("title", title.trim())
            .put("options", JSONArray().apply { options.forEach { put(JSONObject().put("label", it.label).put("at", it.at)) } })
            .put("place", place.trim()).put("address", address.trim()).put("closes", closes))
    }

    fun card(content: JSONObject?): Card? {
        val c = content?.optJSONObject(CARD) ?: return null
        val id = c.optString("id").ifEmpty { return null }
        val a = c.optJSONArray("options") ?: JSONArray()
        val opts = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Option(it.optString("label"), it.optLong("at")) } }
        return Card(id, c.optString("from"), c.optString("title"), opts, c.optString("place"), c.optString("address"), c.optLong("closes"))
    }

    /** Le cose che si possono dire su un'uscita. */
    sealed class Say {
        data class Dates(val yes: Set<Int>, val calFree: Set<Int>?) : Say()
        data class Offer(val seats: Int, val from: String) : Say()
        data class Need(val from: String) : Say()
        data class Join(val driver: String) : Say()
        data class Pick(val user: String) : Say()
        object NoRide : Say()
        data class Fix(val option: Int) : Say()
        data class Place(val place: String, val address: String) : Say()
    }

    /**
     * [forWho]: chi ha risposto per email (una persona «mailto:», [eu.eurise.core.insieme.Scheda.mailPerson]); la risposta la
     * manda il telefono di chi ha proposto l'uscita e vale solo se viene da lui.
     */
    fun answer(cardEvent: String, id: String, say: Say, body: String, forWho: String = ""): JSONObject {
        val a = JSONObject().put("id", id)
        if (forWho.isNotBlank()) a.put("for", forWho)
        when (say) {
            is Say.Dates -> { a.put("kind", "dates").put("yes", JSONArray(say.yes.sorted())); say.calFree?.let { a.put("cal", JSONArray(it.sorted())) } }
            is Say.Offer -> a.put("kind", "offer").put("seats", say.seats).put("from", say.from)
            is Say.Need -> a.put("kind", "need").put("from", say.from)
            is Say.Join -> a.put("kind", "join").put("driver", say.driver)
            is Say.Pick -> a.put("kind", "pick").put("user", say.user)
            Say.NoRide -> a.put("kind", "noride")
            is Say.Fix -> a.put("kind", "fix").put("option", say.option)
            is Say.Place -> a.put("kind", "place").put("place", say.place).put("address", say.address)
        }
        return JSONObject().put("msgtype", ANSWER).put("body", body).put(ANSWER, a)
            .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", cardEvent))
    }

    /** Per conto di chi è la risposta (solo quelle arrivate per email), o null. */
    fun forWho(content: JSONObject?): String? = content?.optJSONObject(ANSWER)?.optString("for")?.takeIf { it.isNotBlank() }

    fun parse(content: JSONObject?): Pair<String, Say>? {
        val a = content?.optJSONObject(ANSWER) ?: return null
        val id = a.optString("id").ifEmpty { return null }
        fun ints(k: String) = a.optJSONArray(k)?.let { x -> (0 until x.length()).map { x.optInt(it) }.toSet() }
        val say = when (a.optString("kind")) {
            "dates" -> Say.Dates(ints("yes").orEmpty(), ints("cal"))
            "offer" -> Say.Offer(a.optInt("seats").coerceIn(1, 8), a.optString("from"))
            "need" -> Say.Need(a.optString("from"))
            "join" -> Say.Join(a.optString("driver"))
            "pick" -> Say.Pick(a.optString("user"))
            "noride" -> Say.NoRide
            "fix" -> Say.Fix(a.optInt("option", -1))
            "place" -> Say.Place(a.optString("place"), a.optString("address"))
            else -> return null
        }
        return id to say
    }

    data class Car(val driver: String, val seats: Int, val from: String, val riders: List<String>) { val free get() = (seats - riders.size).coerceAtLeast(0) }

    data class State(
        val votes: Map<String, Set<Int>>, val calFree: Map<String, Set<Int>>, val cars: List<Car>, val needs: Map<String, String>,
        val fixed: Int?, val place: String, val address: String,
    ) {
        fun count(option: Int) = votes.values.count { option in it }
        /** Chi ha detto che quella data va bene ed è libero anche secondo il calendario (solo chi lo condivide). */
        fun calendarFree(option: Int) = calFree.count { (u, s) -> option in s && option in votes[u].orEmpty() }
        fun sharing() = calFree.size
    }

    /** Lo stato dalle risposte in ordine (chi, cosa): l'ultima di ognuno vale; solo chi ha proposto fissa e cambia il luogo. */
    fun state(card: Card, organizer: String, answers: List<Pair<String, Say>>): State {
        val votes = LinkedHashMap<String, Set<Int>>()
        val cal = LinkedHashMap<String, Set<Int>>()
        val offers = LinkedHashMap<String, Say.Offer>()
        val rideOf = HashMap<String, String>()
        val needs = LinkedHashMap<String, String>()
        var fixed: Int? = null
        var place = card.place
        var address = card.address
        answers.forEach { (who, say) ->
            when (say) {
                is Say.Dates -> { votes[who] = say.yes.filter { it in card.options.indices }.toSet(); if (say.calFree != null) cal[who] = say.calFree else cal.remove(who) }
                is Say.Offer -> { offers[who] = say; needs.remove(who); rideOf.remove(who) }
                is Say.Need -> { needs[who] = say.from; rideOf.remove(who) }
                is Say.Join -> if (say.driver in offers && say.driver != who) {
                    val taken = rideOf.count { it.value == say.driver && it.key != who }
                    if (taken < offers.getValue(say.driver).seats) { rideOf[who] = say.driver; needs.remove(who) }
                }
                // «La prendo io»: chi guida prende chi cerca un passaggio.
                is Say.Pick -> if (who in offers && say.user in needs) {
                    val taken = rideOf.count { it.value == who }
                    if (taken < offers.getValue(who).seats) { rideOf[say.user] = who; needs.remove(say.user) }
                }
                Say.NoRide -> { offers.remove(who); needs.remove(who); rideOf.remove(who); rideOf.entries.removeAll { it.value == who } }
                is Say.Fix -> if (who == organizer) fixed = say.option.takeIf { it in card.options.indices }
                is Say.Place -> if (who == organizer) { place = say.place; address = say.address }
            }
        }
        val cars = offers.values.zip(offers.keys).map { (o, d) -> Car(d, o.seats, o.from, rideOf.filterValues { it == d }.keys.toList()) }
        return State(votes, cal, cars, needs, fixed, place, address)
    }

    /** La data migliore: più sì, a pari voti la prima. */
    fun best(card: Card, s: State): Int? = card.options.indices.maxWithOrNull(compareBy<Int> { s.count(it) }.thenByDescending { it })
        ?.takeIf { s.count(it) > 0 }
}
