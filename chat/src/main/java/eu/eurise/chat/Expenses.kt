package eu.eurise.chat

import java.text.NumberFormat
import java.util.Locale
import org.json.JSONObject

/**
 * Spese di gruppo nella Chat (tavole SpeGruppo, SpeAggiungi, SpeSaldi, approvate il 17 novembre).
 *
 * Ogni spesa e ogni pagamento tra due persone è un messaggio della stanza (cifrato come gli altri) con `msgtype`
 * [EXPENSE] o [SETTLEMENT] e i dati sotto la chiave omonima; `body` porta una frase leggibile, così chi usa Element X o
 * FluffyChat vede comunque «Spesa: Cena 186,00 € …». Il conto si fa sul telefono di ognuno, dai messaggi: nessun
 * servizio esterno. Una spesa corretta o tolta è un messaggio nuovo con lo stesso [Expense.id]: vale l'ultimo.
 *
 * Gli importi sono in centesimi (Long) per non perdere centesimi negli arrotondamenti.
 */
object Expenses {
    const val EXPENSE = "eu.eurise.expense"
    const val SETTLEMENT = "eu.eurise.settlement"

    enum class Mode { Equal, Shares, Amounts, Percent }

    data class Expense(
        val id: String,
        val title: String,
        val cents: Long,
        val payer: String,
        /** Quanto tocca a ognuno, in centesimi; la somma è [cents]. */
        val owed: Map<String, Long>,
        val mode: Mode = Mode.Equal,
        val time: Long = 0L,
        val deleted: Boolean = false,
        val currency: String = "EUR",
    )

    data class Settlement(val from: String, val to: String, val cents: Long, val time: Long = 0L)

    data class Transfer(val from: String, val to: String, val cents: Long)

    /** Per persona: quanto ha pagato, quanto le tocca, il saldo (positivo: deve ricevere). */
    data class Person(val id: String, val paid: Long, val share: Long, val balance: Long)

    // --- Divisioni ---

    /**
     * In parti uguali tra [people]; i centesimi che avanzano vanno uno a testa ai primi in ordine alfabetico, così
     * ogni telefono fa lo stesso conto.
     */
    fun equal(cents: Long, people: Collection<String>): Map<String, Long> {
        val sorted = people.distinct().sorted()
        if (sorted.isEmpty()) return emptyMap()
        val base = cents / sorted.size
        val rest = (cents - base * sorted.size).toInt()
        return sorted.mapIndexed { i, p -> p to base + if (i < rest) 1 else 0 }.toMap()
    }

    /**
     * In proporzione a [weights] (quote: 2 = il doppio; percentuali: la somma è 100). Il resto dei centesimi va a chi ha
     * la parte decimale più grande (metodo del resto più grande), a pari merito in ordine alfabetico.
     */
    fun proportional(cents: Long, weights: Map<String, Double>): Map<String, Long> {
        val w = weights.filterValues { it > 0 }
        val total = w.values.sum()
        if (w.isEmpty() || total <= 0) return emptyMap()
        val exact = w.mapValues { (_, v) -> cents * v / total }
        val floor = exact.mapValues { (_, v) -> kotlin.math.floor(v).toLong() }
        var rest = cents - floor.values.sum()
        val order = exact.keys.sortedWith(compareByDescending<String> { exact.getValue(it) - floor.getValue(it) }.thenBy { it })
        val out = floor.toMutableMap()
        for (p in order) { if (rest <= 0) break; out[p] = out.getValue(p) + 1; rest-- }
        return out.toSortedMap()
    }

    /** Importi scritti a mano: valgono solo se la somma è il totale. */
    fun amounts(cents: Long, given: Map<String, Long>): Map<String, Long>? =
        given.filterValues { it > 0 }.takeIf { it.values.sum() == cents && it.isNotEmpty() }

    fun percentOk(p: Map<String, Double>) = kotlin.math.abs(p.values.sum() - 100.0) < 0.01

    // --- Conti ---

    /** Le spese valide: per ogni id l'ultima versione, senza quelle tolte. */
    fun current(all: List<Expense>): List<Expense> =
        all.groupBy { it.id }.values.map { v -> v.maxBy { it.time } }.filter { !it.deleted }.sortedByDescending { it.time }

    fun people(expenses: List<Expense>, settlements: List<Settlement>, members: Collection<String> = emptyList()): List<Person> {
        val paid = HashMap<String, Long>()
        val share = HashMap<String, Long>()
        val bal = HashMap<String, Long>()
        members.forEach { bal[it] = 0 }
        for (e in expenses) {
            paid.merge(e.payer, e.cents, Long::plus)
            bal.merge(e.payer, e.cents, Long::plus)
            e.owed.forEach { (p, c) -> share.merge(p, c, Long::plus); bal.merge(p, -c, Long::plus) }
        }
        // Chi paga un debito sale (ha dato), chi lo riceve scende (ha avuto).
        for (s in settlements) { bal.merge(s.from, s.cents, Long::plus); bal.merge(s.to, -s.cents, Long::plus) }
        return bal.keys.map { Person(it, paid[it] ?: 0, share[it] ?: 0, bal.getValue(it)) }
            .sortedWith(compareByDescending<Person> { it.balance }.thenBy { it.id })
    }

    /**
     * Chi deve a chi con pochi pagamenti: chi deve di più paga chi deve ricevere di più, finché i conti tornano.
     * Con n persone bastano al massimo n − 1 pagamenti.
     */
    fun settle(people: List<Person>): List<Transfer> {
        val credit = people.filter { it.balance > 0 }.map { it.id to it.balance }.sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).toMutableList()
        val debt = people.filter { it.balance < 0 }.map { it.id to -it.balance }.sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }).toMutableList()
        val out = mutableListOf<Transfer>()
        while (credit.isNotEmpty() && debt.isNotEmpty()) {
            val (c, cAmt) = credit[0]
            val (d, dAmt) = debt[0]
            val m = minOf(cAmt, dAmt)
            out += Transfer(d, c, m)
            if (cAmt == m) credit.removeAt(0) else credit[0] = c to cAmt - m
            if (dAmt == m) debt.removeAt(0) else debt[0] = d to dAmt - m
            credit.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
            debt.sortWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
        }
        return out
    }

    // --- Messaggi ---

    fun content(e: Expense, body: String): JSONObject = JSONObject()
        .put("msgtype", EXPENSE).put("body", body)
        .put(EXPENSE, JSONObject()
            .put("id", e.id).put("title", e.title).put("amount", e.cents).put("currency", e.currency)
            .put("payer", e.payer).put("mode", e.mode.name.lowercase())
            .put("owed", JSONObject().apply { e.owed.forEach { (p, c) -> put(p, c) } })
            .apply { if (e.deleted) put("deleted", true) })

    fun content(s: Settlement, body: String): JSONObject = JSONObject()
        .put("msgtype", SETTLEMENT).put("body", body)
        .put(SETTLEMENT, JSONObject().put("from", s.from).put("to", s.to).put("amount", s.cents))

    /** Una spesa dal contenuto di un messaggio; null se manca qualcosa o i conti non tornano. */
    fun expense(content: JSONObject, time: Long): Expense? = runCatching {
        val o = content.getJSONObject(EXPENSE)
        val owedJ = o.getJSONObject("owed")
        val owed = owedJ.keys().asSequence().associateWith { owedJ.getLong(it) }
        val cents = o.getLong("amount")
        val deleted = o.optBoolean("deleted")
        if (!deleted && (cents <= 0 || owed.values.sum() != cents || owed.values.any { it < 0 })) return null
        Expense(
            id = o.getString("id"), title = o.optString("title"), cents = cents, payer = o.getString("payer"), owed = owed,
            mode = Mode.entries.firstOrNull { it.name.equals(o.optString("mode"), true) } ?: Mode.Equal,
            time = time, deleted = deleted, currency = o.optString("currency", "EUR"),
        )
    }.getOrNull()

    fun settlement(content: JSONObject, time: Long): Settlement? = runCatching {
        val o = content.getJSONObject(SETTLEMENT)
        Settlement(o.getString("from"), o.getString("to"), o.getLong("amount").takeIf { it > 0 } ?: return null, time)
    }.getOrNull()

    // --- Importi ---

    private val ITALIAN = Locale.ITALY

    /** «1.284,00 €». */
    fun money(cents: Long): String = NumberFormat.getCurrencyInstance(ITALIAN).apply { currency = java.util.Currency.getInstance("EUR") }
        .format(cents / 100.0).replace(' ', ' ')

    /** «186», «186,5», «186,50», «1.284,00» → centesimi; null se non è un importo. */
    fun parse(text: String): Long? {
        var t = text.trim().replace("€", "").replace(" ", "")
        if (t.isEmpty()) return null
        // Con virgola: i punti sono le migliaia. Senza: un punto seguito da 1–2 cifre è il decimale.
        t = if (',' in t) t.replace(".", "").replace(',', '.')
        else if (Regex("\\.\\d{3}(\\.|$)").containsMatchIn(t) && !Regex("\\.\\d{1,2}$").containsMatchIn(t)) t.replace(".", "") else t
        val v = t.toBigDecimalOrNull() ?: return null
        if (v.signum() < 0 || v.scale() > 2) return null
        return v.movePointRight(2).toLong()
    }
}
