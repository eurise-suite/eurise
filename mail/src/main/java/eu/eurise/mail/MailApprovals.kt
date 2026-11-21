package eu.eurise.mail

import org.json.JSONArray
import org.json.JSONObject

/**
 * Approvazioni per email (tavola LavApprova), senza Android: si prova con i test.
 *
 * La richiesta parte come email con l'oggetto «Richiesta di approvazione: …» e due righe in fondo: rispondere «ok» per
 * approvare, scrivere cosa cambiare per una modifica, «no» per rifiutare. Funziona con qualunque programma di posta. Quando
 * dal destinatario arriva una risposta sullo stesso oggetto, Eurise la legge e propone il verdetto; chi ha chiesto lo
 * conferma con un tocco (un «ok» vale come approvazione, con la conferma). Chi usa Eurise e riceve la richiesta ha i tasti
 * Approva e Chiedi modifica, che rispondono con le stesse parole.
 */
object MailApprovals {
    const val PREFIX = "Richiesta di approvazione: "
    const val QUIET_MS = 86_400_000L

    enum class Verdict { Approved, Change, Rejected }

    data class Request(
        val id: String, val to: String, val title: String, val sentAt: Long,
        val verdict: Verdict? = null, val note: String = "", val confirmed: Boolean = false, val answerUid: Long = 0,
    )

    fun subject(title: String) = PREFIX + title.trim()

    fun isRequest(subject: String) = MailBalls.core(subject).startsWith(PREFIX.lowercase().trim())

    /** Il titolo dall'oggetto (anche con «Re:» davanti). */
    fun title(subject: String): String {
        val s = subject.replace(Regex("^((re|r|fwd|fw|i|aw)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "").trim()
        return if (s.startsWith(PREFIX.trim(), ignoreCase = true)) s.substring(PREFIX.trim().length).trim() else s
    }

    fun body(text: String, howTo: String): String = text.trim() + "\n\n—\n" + howTo

    /**
     * Il verdetto dalla risposta: le prime parole scritte (le righe citate con «>» e quello che segue «Il … ha scritto:» non
     * contano). «ok», «approvo», «va bene», «sì» → approvata; «no», «rifiuto» → rifiutata; altro testo → modifica chiesta.
     */
    fun verdict(reply: String): Pair<Verdict, String>? {
        val own = reply.lines().takeWhile { l ->
            val t = l.trim()
            !t.startsWith(">") && !Regex("^(il|on|le|am|el) .*(scritto|wrote|écrit|schrieb|escribió)\\s*:?$", RegexOption.IGNORE_CASE).matches(t) &&
                !t.startsWith("-----") && t != "—"
        }.joinToString("\n").trim()
        if (own.isEmpty()) return null
        val first = own.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().split(Regex("\\s+"))
        val head = first.take(3).joinToString(" ")
        val yes = listOf("ok", "okay", "approvo", "approvato", "approvata", "va bene", "sì", "si", "confermo", "procedi", "nessun problema", "approved", "yes")
        val no = listOf("no", "rifiuto", "rifiutato", "rifiutata", "non approvo", "respingo")
        val rest = own.lines().drop(1).joinToString("\n").trim()
        return when {
            no.any { head == it || head.startsWith("$it ") } && !head.startsWith("no problem") -> Verdict.Rejected to rest
            yes.any { head == it || head.startsWith("$it ") } -> Verdict.Approved to rest
            else -> Verdict.Change to own.take(300)
        }
    }

    fun waiting(r: Request, now: Long) = r.verdict == null && now - r.sentAt >= QUIET_MS

    /** Le risposte da guardare: email del destinatario sulla stessa richiesta, arrivate dopo, non ancora lette per questo. */
    fun candidates(list: List<Request>, inbox: List<Summary>): List<Pair<Request, Summary>> = list.filter { !it.confirmed }.mapNotNull { r ->
        inbox.filter { it.fromAddress.equals(r.to, ignoreCase = true) && it.date > r.sentAt && isRequest(it.subject) &&
            title(it.subject).equals(r.title, ignoreCase = true) && it.uid != r.answerUid }
            .maxByOrNull { it.date }?.let { r to it }
    }

    fun encode(list: List<Request>): String = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().put("id", r.id).put("to", r.to).put("title", r.title).put("sent", r.sentAt).put("verdict", r.verdict?.name ?: "")
                .put("note", r.note).put("confirmed", r.confirmed).put("uid", r.answerUid))
        }
    }.toString()

    fun decode(s: String?): List<Request> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Request(o.optString("id"), o.optString("to"), o.optString("title"), o.optLong("sent"),
                runCatching { Verdict.valueOf(o.optString("verdict")) }.getOrNull(), o.optString("note"), o.optBoolean("confirmed"), o.optLong("uid"))
                .takeIf { it.id.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}
