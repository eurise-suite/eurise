package eu.eurise.core.insieme

import org.json.JSONArray
import org.json.JSONObject

/**
 * La stessa scheda in Chat e in Email (tavola InsStessaScheda), senza Android: si prova con i test.
 *
 * Una scheda è una cosa da fare insieme: affidata, una faccenda, un'uscita da fissare, una spesa, gli orari liberi, un
 * elenco… Chi usa Eurise la riceve viva nella stanza Matrix cifrata (un messaggio con il campo [FIELD], con il testo per
 * Element nel `body`) e risponde con i tasti: ogni risposta è un messaggio [ANSWER] riferito alla scheda (`m.reference`).
 * Chi non usa Eurise la riceve per email ([SchedaMail]) con i pulsanti, che preparano la risposta, o basta rispondere
 * «fatto»: [Risposte] la capisce e lo stato si aggiorna lo stesso. Un modulo solo, cambia solo la strada.
 */
object Scheda {
    const val FIELD = "eu.eurise.card"
    const val ANSWER = "eu.eurise.card.answer"
    /** Le persone che hanno solo l'email, dentro gli elenchi di persone della Chat (faccende, uscite): «mailto:indirizzo#Nome». */
    const val MAIL_PREFIX = "mailto:"

    enum class Kind {
        /** Affida a… (tavola InsAffida): una cosa da fare con scadenza; torna a te se non risponde. */
        Affida,
        /** Faccende a turno (tavola FamFaccende): il promemoria a chi tocca, «fatto» passa il turno. */
        Faccenda,
        /** Organizza un'uscita (tavola AmiUscita): si vota una delle date. */
        Uscita,
        /** Disponibilità in un link (tavole InsDisponibilita, InsDisponibilitaMail): si sceglie un orario. */
        Orari,
        /** Spese di gruppo dall'email (tavola SpeEmail): la quota e «Ho pagato». */
        Spesa,
        /** Condividi con… (tavola InsCondividi): vede o modifica. */
        Condividi,
        /** Chi c'è a cena? (tavola FamCena): sì, tardi, no. */
        Cena,
        /** Regalo collettivo (tavola AmiRegalo): la quota e «Ho versato». */
        Regalo,
        /** Elenchi condivisi (tavola InsElenchi). */
        Elenco,
        /** Il registro delle decisioni (tavola LavDecisioni). */
        Decisione,
    }

    /** Che cosa ha detto chi ha ricevuto la scheda. */
    enum class State { Seen, Working, Done, Declined, Back, Yes, Late, No, Paid }

    data class Card(
        val id: String,
        val kind: Kind,
        val title: String,
        val note: String = "",
        /** La scadenza o la data da mostrare («ven 22, 12:00»); [dueAt] in millisecondi, 0 se non c'è. */
        val due: String = "",
        val dueAt: Long = 0,
        val from: String = "",
        val fromAddress: String = "",
        /** Le scelte (date di un'uscita, orari liberi, idee del regalo), numerate da 1 nelle email. */
        val options: List<String> = emptyList(),
        val amount: String = "",
        val link: String = "",
        /** Da dove viene: «mail», «chat:!stanza». Le risposte per email tornano lì. */
        val origin: String = "",
        /** Vede o modifica (per Condividi). */
        val edit: Boolean = false,
    )

    /** Una risposta: lo stato, le scelte (numeri da 0), una riga; [forWho] quando la manda qualcuno per conto di altri. */
    data class Answer(val state: State? = null, val choices: Set<Int>? = null, val note: String = "", val forWho: String = "")

    /** Le risposte possibili per ogni tipo di scheda, nell'ordine dei tasti (la principale per ultima, sotto il pollice). */
    fun actions(kind: Kind): List<State> = when (kind) {
        Kind.Affida -> listOf(State.Declined, State.Seen, State.Done)
        Kind.Faccenda -> listOf(State.Declined, State.Done)
        Kind.Cena -> listOf(State.No, State.Late, State.Yes)
        Kind.Spesa, Kind.Regalo -> listOf(State.Paid)
        Kind.Condividi, Kind.Elenco, Kind.Decisione -> listOf(State.Seen)
        Kind.Uscita, Kind.Orari -> emptyList()
    }

    /** Le schede con le scelte numerate (si vota, si sceglie un orario). */
    fun choosing(kind: Kind) = kind == Kind.Uscita || kind == Kind.Orari

    /** Per gli orari se ne sceglie uno solo; per le uscite tutte le date che vanno bene. */
    fun single(kind: Kind) = kind == Kind.Orari

    fun toJson(c: Card): JSONObject = JSONObject().put("id", c.id).put("kind", c.kind.name).put("title", c.title.trim())
        .put("note", c.note.trim()).put("due", c.due).put("dueAt", c.dueAt).put("from", c.from).put("fromAddress", c.fromAddress)
        .put("options", JSONArray(c.options)).put("amount", c.amount).put("link", c.link).put("origin", c.origin).put("edit", c.edit)

    fun fromJson(o: JSONObject?): Card? {
        o ?: return null
        val id = o.optString("id").ifEmpty { return null }
        val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
        val opts = o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        return Card(id, kind, o.optString("title"), o.optString("note"), o.optString("due"), o.optLong("dueAt"), o.optString("from"),
            o.optString("fromAddress"), opts, o.optString("amount"), o.optString("link"), o.optString("origin"), o.optBoolean("edit"))
            .takeIf { it.title.isNotBlank() }
    }

    /** Il messaggio della stanza: la scheda nel campo [FIELD], e per Element il testo [body]. */
    fun content(c: Card, body: String): JSONObject = JSONObject().put("msgtype", "m.text").put("body", body).put(FIELD, toJson(c))

    fun card(content: JSONObject?): Card? = fromJson(content?.optJSONObject(FIELD))

    fun answerToJson(a: Answer): JSONObject = JSONObject().apply {
        a.state?.let { put("state", it.name) }
        a.choices?.let { put("choices", JSONArray(it.sorted())) }
        if (a.note.isNotBlank()) put("note", a.note.trim())
        if (a.forWho.isNotBlank()) put("for", a.forWho)
    }

    fun answerFromJson(o: JSONObject?): Answer? {
        o ?: return null
        val state = o.optString("state").takeIf { it.isNotEmpty() }?.let { s -> runCatching { State.valueOf(s) }.getOrNull() }
        val choices = o.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it >= 0 }.toSet() }
        if (state == null && choices == null) return null
        return Answer(state, choices, o.optString("note"), o.optString("for"))
    }

    /** La risposta nella stanza: si riferisce alla scheda, con una riga di testo per Element. */
    fun answerContent(cardEvent: String, id: String, a: Answer, body: String): JSONObject = JSONObject()
        .put("msgtype", "m.notice").put("body", body)
        .put(ANSWER, answerToJson(a).put("id", id))
        .put("m.relates_to", JSONObject().put("rel_type", "m.reference").put("event_id", cardEvent))

    fun answer(content: JSONObject?): Pair<String, Answer>? {
        val o = content?.optJSONObject(ANSWER) ?: return null
        val id = o.optString("id").ifEmpty { return null }
        return answerFromJson(o)?.let { id to it }
    }

    /**
     * Lo stato di una scheda dalle risposte in ordine (chi, cosa): per ogni persona vale l'ultima; una risposta «per conto
     * di» ([Answer.forWho]) vale solo se la manda chi ha fatto la scheda (il suo telefono legge le email).
     */
    fun fold(owner: String, answers: List<Pair<String, Answer>>): Map<String, Answer> {
        val out = LinkedHashMap<String, Answer>()
        answers.forEach { (who, a) ->
            val key = if (a.forWho.isNotBlank()) { if (who != owner) return@forEach; a.forWho } else who
            out.remove(key)
            out[key] = a
        }
        return out
    }

    /** Quante persone hanno scelto ogni opzione. */
    fun counts(c: Card, answers: Map<String, Answer>): List<Int> = c.options.indices.map { i -> answers.values.count { i in it.choices.orEmpty() } }

    // --- Persone con la sola email ---

    fun mailPerson(address: String, name: String = ""): String = MAIL_PREFIX + address.trim().lowercase() + (if (name.isNotBlank()) "#" + name.trim() else "")

    fun isMailPerson(id: String) = id.startsWith(MAIL_PREFIX)

    fun mailAddress(id: String): String? = if (isMailPerson(id)) id.removePrefix(MAIL_PREFIX).substringBefore('#').takeIf { it.contains('@') } else null

    /** Il nome scritto, o la parte prima della @ con la maiuscola. */
    fun mailName(id: String): String {
        val rest = id.removePrefix(MAIL_PREFIX)
        rest.substringAfter('#', "").takeIf { it.isNotBlank() }?.let { return it }
        return rest.substringBefore('@').substringBefore('.').replaceFirstChar { it.uppercase() }
    }

    /** Lo stato da mostrare a chi l'ha mandata: la risposta, se no «vista» se l'altro l'ha letta dopo l'invio. */
    fun shown(answer: State?, sentAt: Long, readAt: Long): State? = answer ?: if (readAt >= sentAt && sentAt > 0) State.Seen else null

    /** Torna a te: nessuna risposta data con i tasti (la sola lettura non conta) dopo [backAfterMs] dall'invio. */
    fun back(answer: State?, sentAt: Long, backAfterMs: Long, now: Long): Boolean =
        backAfterMs > 0 && answer == null && now - sentAt >= backAfterMs
}
