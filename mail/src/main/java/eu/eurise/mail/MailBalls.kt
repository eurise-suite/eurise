package eu.eurise.mail

import org.json.JSONArray
import org.json.JSONObject

/**
 * Passa la palla da un'email (tavola InsPalla), la stessa scheda della Chat con il trasporto email: l'email va inoltrata
 * al collega con la riga sul da farsi in cima, e resta sul telefono l'elenco «Le palle che hai passato». Fatta quando dal
 * collega arriva un'email sullo stesso oggetto (la risposta al cliente in copia, o «fatto»); ferma da un giorno se no.
 */
object MailBalls {
    const val QUIET_MS = 86_400_000L

    data class Ball(val id: String, val to: String, val subject: String, val task: String, val sentAt: Long, val done: Boolean = false, val taken: Boolean = false)

    /** L'oggetto senza «Re:», «I:», «Fwd:», per riconoscere le risposte. */
    fun core(subject: String): String = subject.replace(Regex("^((re|r|fwd|fw|i|inoltra|aw|wg)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "").trim().lowercase()

    /** Il testo dell'inoltro: la riga in cima, poi l'intestazione e l'email. */
    fun body(task: String, copyMe: String?, header: String, text: String): String =
        "🏐 " + task.trim() + (copyMe?.let { "\n$it" } ?: "") + "\n\n" + header + "\n\n" + text.trim()

    /** Segna fatte le palle a cui il collega ha risposto (un'email da lui sullo stesso oggetto, dopo l'invio). */
    fun update(list: List<Ball>, inbox: List<Summary>): List<Ball> = list.map { b ->
        if (b.done || b.taken) b
        else if (inbox.any { it.fromAddress.equals(b.to, ignoreCase = true) && it.date > b.sentAt && core(it.subject) == core(b.subject) }) b.copy(done = true)
        else b
    }

    fun stale(b: Ball, now: Long) = !b.done && !b.taken && now - b.sentAt >= QUIET_MS

    fun encode(list: List<Ball>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("to", it.to).put("subject", it.subject).put("task", it.task).put("sent", it.sentAt).put("done", it.done).put("taken", it.taken)) }
    }.toString()

    fun decode(s: String?): List<Ball> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Ball(o.optString("id"), o.optString("to"), o.optString("subject"), o.optString("task"), o.optLong("sent"), o.optBoolean("done"), o.optBoolean("taken"))
                .takeIf { it.id.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    /** Le persone a cui di solito scrivi o che ti scrivono, per sceglierle con un tocco (escluse le notifiche). */
    fun people(inbox: List<Summary>, mine: Set<String>, max: Int = 6): List<Pair<String, String>> =
        inbox.filter { it.kind == Kind.People && it.fromAddress.lowercase() !in mine }
            .groupBy { it.fromAddress.lowercase() }.entries.sortedByDescending { e -> e.value.size * 10 + (e.value.maxOf { it.date } / 86_400_000L) }
            .take(max).map { (addr, list) -> addr to list.first().fromName.ifBlank { addr } }
}
