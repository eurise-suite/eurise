package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * La casa nella Chat della famiglia (tavole FamScadenze, FamDocumenti, FamCena, FamGenitori), senza Android: si prova con i
 * test. Ogni cosa è uno stato della stanza, lo stesso per tutti e cifrato: le scadenze (bollette, bollo, revisione, contratti,
 * garanzie, documenti), il menu della settimana, i farmaci di un genitore con chi li ha presi.
 */
object Casa {
    const val DEADLINES = "eu.eurise.deadlines"
    const val MENU = "eu.eurise.menu"
    const val MEDS = "eu.eurise.meds"
    const val DINNER = "eu.eurise.dinner"

    // --- Scadenze e documenti ---

    enum class Kind { Bolletta, Bollo, Revisione, Assicurazione, Rata, Garanzia, Contratto, Documento, Altro }

    /**
     * [due]: il giorno (epoch day); [monthly]: si ripete ogni mese (rate); [notice]: per i contratti, i giorni di disdetta; per i
     * documenti l'anticipo con cui avvisare (le carte d'identità vanno prenotate settimane prima). [payer]: chi paga; [split]:
     * quando è pagata va nelle spese del gruppo divisa fra tutti. [file]: il documento conservato (un messaggio cifrato del gruppo).
     */
    data class Deadline(
        val id: String, val title: String, val kind: Kind, val due: Long, val cents: Long = 0, val payer: String = "", val remindDays: Int = 3,
        val split: Boolean = false, val monthly: Boolean = false, val notice: Int = 0, val source: String = "", val file: String = "",
        val paidAt: Long = 0, val by: String = "",
    )

    /** Quando avvisare: i giorni prima della scadenza (per contratti e documenti l'anticipo è più lungo). */
    fun warnDay(d: Deadline): LocalDate {
        val day = LocalDate.ofEpochDay(d.due)
        return when (d.kind) {
            Kind.Contratto -> day.minusDays((if (d.notice > 0) d.notice else 60).toLong() + d.remindDays)
            Kind.Documento -> day.minusDays((if (d.notice > 0) d.notice else 60).toLong())
            Kind.Garanzia -> day.minusDays(30)
            else -> day.minusDays(d.remindDays.toLong())
        }
    }

    /** Da guardare adesso: non pagata e il giorno dell'avviso è arrivato. */
    fun warning(d: Deadline, today: LocalDate): Boolean = d.paidAt == 0L && !today.isBefore(warnDay(d))

    /** Le prossime in alto (prima quelle da guardare), poi le pagate di recente; le rate tornano il mese dopo. */
    fun sorted(list: List<Deadline>, today: LocalDate): List<Deadline> =
        list.sortedWith(compareBy<Deadline>({ it.paidAt > 0 }, { !warning(it, today) }, { it.due }))

    /** «Pagata»: le rate passano al mese dopo; le altre restano segnate come pagate. */
    fun paid(list: List<Deadline>, id: String, at: Long): List<Deadline> = list.map { d ->
        if (d.id != id) d else if (d.monthly) d.copy(due = LocalDate.ofEpochDay(d.due).plusMonths(1).toEpochDay()) else d.copy(paidAt = at)
    }

    fun encodeDeadlines(list: List<Deadline>): JSONObject = JSONObject().put("items", JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().put("id", d.id).put("title", d.title).put("kind", d.kind.name).put("due", d.due).put("cents", d.cents).put("payer", d.payer)
                .put("remind", d.remindDays).put("split", d.split).put("monthly", d.monthly).put("notice", d.notice).put("source", d.source)
                .put("file", d.file).put("paidAt", d.paidAt).put("by", d.by))
        }
    })

    fun decodeDeadlines(o: JSONObject?): List<Deadline> = runCatching {
        val a = o?.optJSONArray("items") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val x = a.optJSONObject(i) ?: return@mapNotNull null
            Deadline(x.optString("id"), x.optString("title"), runCatching { Kind.valueOf(x.optString("kind")) }.getOrDefault(Kind.Altro), x.optLong("due"),
                x.optLong("cents"), x.optString("payer"), x.optInt("remind", 3), x.optBoolean("split"), x.optBoolean("monthly"), x.optInt("notice"),
                x.optString("source"), x.optString("file"), x.optLong("paidAt"), x.optString("by")).takeIf { it.id.isNotEmpty() && it.title.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    // --- Chi c'è a cena e il menu della settimana ---

    /** Un piatto per giorno (1 = lunedì … 7) con gli ingredienti da comprare. */
    data class Dish(val day: Int, val name: String, val ingredients: List<String>)

    /** Gli ingredienti del menu che non sono già nell'elenco (voci da fare), senza doppioni. */
    fun missing(menu: List<Dish>, list: Elenchi.Lista?): List<String> {
        val have = list?.items?.filter { it.doneAt == 0L }?.map { it.text.lowercase().trim() }.orEmpty().toSet()
        return menu.flatMap { it.ingredients }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.filter { it.lowercase() !in have }
    }

    /** «pasta al pesto: basilico, pinoli» → il piatto e gli ingredienti. */
    fun parseDish(day: Int, text: String): Dish? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val name = t.substringBefore(':').trim()
        val ing = if (t.contains(':')) t.substringAfter(':').split(',', ';').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        return Dish(day, name.replaceFirstChar { it.uppercase() }, ing)
    }

    fun encodeMenu(menu: List<Dish>): JSONObject = JSONObject().put("days", JSONArray().apply {
        menu.forEach { d -> put(JSONObject().put("day", d.day).put("name", d.name).put("ing", JSONArray(d.ingredients))) }
    })

    fun decodeMenu(o: JSONObject?): List<Dish> = runCatching {
        val a = o?.optJSONArray("days") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { x ->
                Dish(x.optInt("day"), x.optString("name"), x.optJSONArray("ing")?.let { g -> (0 until g.length()).map { g.optString(it) } }.orEmpty())
                    .takeIf { it.day in 1..7 && it.name.isNotBlank() }
            }
        }.sortedBy { it.day }
    }.getOrDefault(emptyList())

    // --- I farmaci di un genitore ---

    /** [phone]: il numero di chi li prende, se ha un telefono semplice (conferma rispondendo «1» all'SMS). */
    data class Med(val id: String, val name: String, val time: String)
    data class Meds(val person: String, val phone: String = "", val user: String = "", val meds: List<Med> = emptyList(),
                    val taken: Map<String, Long> = emptyMap(), val by: String = "", val escalateMin: Int = 30)

    /** La chiave di una dose: farmaco e giorno. */
    fun dose(medId: String, day: LocalDate) = medId + "@" + day.toEpochDay()

    fun take(m: Meds, medId: String, day: LocalDate, at: Long): Meds = m.copy(taken = m.taken + (dose(medId, day) to at))

    /** Le dosi di un giorno: (farmaco, presa alle… o null). */
    fun today(m: Meds, day: LocalDate): List<Pair<Med, Long?>> = m.meds.sortedBy { it.time }.map { it to m.taken[dose(it.id, day)] }

    /** Per la settimana: per ogni giorno quante prese su quante, e se una è stata presa tardi (più di un'ora dopo). */
    fun week(m: Meds, end: LocalDate, toMs: (LocalDate, String) -> Long): List<Triple<LocalDate, Int, Boolean>> = (6 downTo 0).map { back ->
        val d = end.minusDays(back.toLong())
        val taken = m.meds.count { m.taken.containsKey(dose(it.id, d)) }
        val late = m.meds.any { med -> m.taken[dose(med.id, d)]?.let { it - toMs(d, med.time) > 3_600_000L } == true }
        Triple(d, taken, late)
    }

    fun encodeMeds(m: Meds): JSONObject = JSONObject().put("person", m.person).put("phone", m.phone).put("user", m.user).put("by", m.by).put("escalate", m.escalateMin)
        .put("meds", JSONArray().apply { m.meds.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("time", it.time)) } })
        .put("taken", JSONObject().apply { m.taken.entries.sortedBy { it.value }.takeLast(200).forEach { (k, v) -> put(k, v) } })

    fun decodeMeds(o: JSONObject?): Meds? = runCatching {
        o ?: return null
        if (o.optString("person").isBlank()) return null
        val meds = o.optJSONArray("meds")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Med(it.optString("id"), it.optString("name"), it.optString("time")) } } }.orEmpty()
        val taken = o.optJSONObject("taken")?.let { t -> t.keys().asSequence().associateWith { t.optLong(it) } }.orEmpty()
        Meds(o.optString("person"), o.optString("phone"), o.optString("user"), meds, taken, o.optString("by"), o.optInt("escalate", 30))
    }.getOrNull()

    /** Un SMS di conferma: «1», «presa», «fatto», «ok» (anche con altro dopo). */
    fun confirms(sms: String): Boolean {
        val t = sms.trim().lowercase()
        return t == "1" || Regex("^(1\\b|presa|preso|presi|prese|fatto|fatta|ok\\b|sì\\b|si\\b)").containsMatchIn(t)
    }
}
