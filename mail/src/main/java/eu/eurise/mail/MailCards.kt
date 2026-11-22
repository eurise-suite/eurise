package eu.eurise.mail

import android.content.Context
import eu.eurise.core.insieme.Risposte
import eu.eurise.core.insieme.Scheda
import eu.eurise.core.insieme.SchedaMail
import org.json.JSONArray
import org.json.JSONObject

/**
 * Le schede mandate per email (tavola InsStessaScheda), senza rete: si prova con i test.
 *
 * Ogni scheda va a una persona per email (un'email a testa: nessuno vede gli indirizzi degli altri). Le risposte sullo stesso
 * oggetto, da uno degli indirizzi, si aprono una volta e si leggono con [Risposte]: lo stato di ognuno resta qui, sul telefono,
 * e la Chat lo chiede quando la scheda viene da un suo gruppo ([SchedeProvider]).
 */
object MailCards {
    const val QUIET_MS = 86_400_000L

    data class Got(val answer: Scheda.Answer, val at: Long)

    data class Sent(
        val card: Scheda.Card, val to: List<String>, val subject: String, val sentAt: Long,
        val answers: Map<String, Got> = emptyMap(), val read: Set<Long> = emptySet(), val fixed: Int = -1, val closed: Boolean = false,
    )

    /** Le risposte da aprire: email da uno degli indirizzi, dopo l'invio, sullo stesso oggetto, non ancora lette per questo. */
    fun candidates(list: List<Sent>, inbox: List<Summary>): List<Pair<Sent, Summary>> = list.filter { !it.closed }.flatMap { s ->
        val core = SchedaMail.core(s.subject)
        inbox.filter { m -> m.date > s.sentAt && m.uid !in s.read && s.to.any { it.equals(m.fromAddress, ignoreCase = true) } && SchedaMail.core(m.subject) == core }
            .map { s to it }
    }

    /** La risposta letta (o non capita: allora si segna solo come letta, per non riaprirla). */
    fun apply(list: List<Sent>, card: String, address: String, uid: Long, answer: Scheda.Answer?, at: Long): List<Sent> = list.map { s ->
        if (s.card.id != card) return@map s
        val key = address.lowercase()
        val prev = s.answers[key]
        val answers = if (answer != null && (prev == null || prev.at <= at)) s.answers + (key to Got(answer, at)) else s.answers
        s.copy(answers = answers, read = s.read + uid)
    }

    /** Chi non ha ancora risposto dopo un giorno (per «Sollecita»). */
    fun silent(s: Sent, now: Long): List<String> = if (now - s.sentAt < QUIET_MS) emptyList() else s.to.filter { it.lowercase() !in s.answers }

    fun encode(list: List<Sent>): String = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().put("card", Scheda.toJson(s.card)).put("to", JSONArray(s.to)).put("subject", s.subject).put("sent", s.sentAt)
                .put("answers", JSONObject().apply { s.answers.forEach { (k, g) -> put(k, Scheda.answerToJson(g.answer).put("at", g.at)) } })
                .put("read", JSONArray(s.read.toList().takeLast(200))).put("fixed", s.fixed).put("closed", s.closed))
        }
    }.toString()

    fun decode(str: String?): List<Sent> = runCatching {
        val a = JSONArray(str ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val card = Scheda.fromJson(o.optJSONObject("card")) ?: return@mapNotNull null
            val to = o.optJSONArray("to")?.let { t -> (0 until t.length()).map { t.optString(it) }.filter { it.contains('@') } }.orEmpty()
            val ans = o.optJSONObject("answers")?.let { x ->
                x.keys().asSequence().mapNotNull { k -> x.optJSONObject(k)?.let { g -> Scheda.answerFromJson(g)?.let { k to Got(it, g.optLong("at")) } } }.toMap()
            }.orEmpty()
            val read = o.optJSONArray("read")?.let { r -> (0 until r.length()).map { r.optLong(it) }.toSet() }.orEmpty()
            Sent(card, to, o.optString("subject"), o.optLong("sent"), ans, read, o.optInt("fixed", -1), o.optBoolean("closed"))
        }
    }.getOrDefault(emptyList())
}

/** Dove stanno le schede mandate: uno solo per l'app, il fornitore per la Chat e il lavoro in background. */
object CardStore {
    private fun prefs(context: Context) = context.getSharedPreferences("mail_cards", Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): List<MailCards.Sent> = MailCards.decode(prefs(context).getString("list", null))

    @Synchronized fun save(context: Context, list: List<MailCards.Sent>) {
        // Si tengono le ultime 200; quelle chiuse da più di un mese se ne vanno.
        val month = System.currentTimeMillis() - 30L * 86_400_000L
        prefs(context).edit().putString("list", MailCards.encode(list.filterNot { it.closed && it.sentAt < month }.takeLast(200))).apply()
    }

    @Synchronized fun update(context: Context, change: (List<MailCards.Sent>) -> List<MailCards.Sent>): List<MailCards.Sent> {
        val next = change(load(context))
        save(context, next)
        return next
    }

    /**
     * Manda [card] a ognuno di [to] dalla casella [from] (testo e HTML con i pulsanti) e la tiene tra le schede mandate.
     * Va chiamata fuori dal thread principale. Vero se almeno un'email è partita.
     */
    fun send(context: Context, from: Account, card: Scheda.Card, to: List<String>): Boolean {
        val w = SchedaMail.words(context)
        val c = card.copy(fromAddress = from.email, from = card.from.ifBlank { from.name.ifBlank { from.email.substringBefore('@') } })
        val subject = SchedaMail.subject(c, w)
        val text = SchedaMail.text(c, w)
        val html = SchedaMail.html(c, w, from.email)
        // Un appuntamento (condiviso o proposto) porta con sé l'invito: si apre con qualunque calendario.
        val withIcs = (c.kind == Scheda.Kind.Condividi || c.kind == Scheda.Kind.Proposta) && c.dueAt > 0
        val ok = to.map { it.trim() }.filter { Providers.isValidEmail(it) }.distinct().filter { addr ->
            val files = if (withIcs) listOf(icsFile(context, c.id, c.title, c.dueAt, c.endAt.takeIf { it > c.dueAt } ?: (c.dueAt + 3_600_000L), c.note, from.email, addr)) else emptyList()
            runCatching { MailClient(from).send(addr, "", subject, text, null, files, html) }.isSuccess
        }
        if (ok.isEmpty()) return false
        update(context) { list -> list.filterNot { it.card.id == c.id } + MailCards.Sent(c, ok, subject, System.currentTimeMillis()) }
        return true
    }

    /** L'invito di calendario da allegare, nella cache dell'app. */
    fun icsFile(context: Context, id: String, title: String, start: Long, end: Long, place: String, organizer: String, attendee: String): java.io.File {
        val dir = java.io.File(context.cacheDir, "inviti").apply { mkdirs() }
        return java.io.File(dir, "invito.ics").also {
            it.writeText(eu.eurise.core.insieme.Ics.event(id, title, start, end, place, "", organizer, attendee))
        }
    }

    /**
     * Disponibilità per email (tavola InsDisponibilitaMail): quando l'altro sceglie un orario, l'appuntamento è fissato. Gli arriva
     * l'invito (.ics) e, se c'è il permesso, entra nel calendario di questo telefono. Una volta sola per scheda.
     */
    private fun confirmSlot(context: Context, accounts: List<Account>, card: String, address: String) {
        val s = load(context).firstOrNull { it.card.id == card } ?: return
        if (s.card.kind != Scheda.Kind.Orari || s.fixed >= 0 || !s.card.origin.startsWith("mail")) return
        val i = s.answers[address.lowercase()]?.answer?.choices?.minOrNull() ?: return
        val start = s.card.times.getOrNull(i) ?: return
        val end = start + s.card.durationMin * 60_000L
        val from = accounts.firstOrNull { it.email.equals(s.card.fromAddress, true) } ?: accounts.firstOrNull() ?: return
        val label = s.card.options.getOrNull(i).orEmpty()
        val title = s.card.title.substringBefore(" · ")
        update(context) { l -> l.map { if (it.card.id == card) it.copy(fixed = i) else it } }
        runCatching {
            MailClient(from).send(address, "", MailText.replySubject(s.subject), context.getString(R.string.ml_cd_slot_fixed_body, label), null,
                listOf(icsFile(context, s.card.id, title, start, end, "", from.email, address)))
        }
        eu.eurise.core.insieme.Agenda.insert(context, title, start, end, "", address)
    }

    /** Apre le risposte arrivate e le legge. Fuori dal thread principale. Vero se qualcosa è cambiato. */
    fun collect(context: Context, accounts: List<Account>, inbox: List<Summary>): Boolean {
        val todo = MailCards.candidates(load(context), inbox)
        if (todo.isEmpty()) return false
        todo.forEach { (s, m) ->
            val a = accounts.firstOrNull { it.id == m.accountId } ?: return@forEach
            val text = runCatching { MailClient(a).open(m.uid)?.text }.getOrNull() ?: return@forEach
            val answer = Risposte.read(s.card.kind, text, s.card.options.size)
            update(context) { MailCards.apply(it, s.card.id, m.fromAddress, m.uid, answer, m.date) }
            if (answer?.choices?.isNotEmpty() == true) runCatching { confirmSlot(context, accounts, s.card.id, m.fromAddress) }
        }
        return true
    }
}
