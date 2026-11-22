package eu.eurise.core.insieme

import android.content.Context
import eu.eurise.core.insieme.Scheda.Card
import eu.eurise.core.insieme.Scheda.Kind
import eu.eurise.core.insieme.Scheda.State
import java.net.URLEncoder

/**
 * La strada dell'email per una scheda (tavola InsStessaScheda), senza rete: si prova con i test.
 *
 * L'oggetto porta il tipo tra parentesi quadre («[Da fare] Preparare il preventivo Rossi · entro ven 22»), il testo dice chi
 * la manda, cosa c'è da fare e come rispondere; in HTML ci sono i pulsanti, che aprono una risposta già scritta («fatto»,
 * «non posso», il numero della data) verso chi l'ha mandata: funziona con ogni programma di posta, senza pagine né server.
 * In fondo il riferimento «eurise-<id>», che resta nella citazione della risposta.
 */
object SchedaMail {
    const val REF = "eurise-"

    /** I testi dell'email, dalle risorse (italiano) o scritti nei test. */
    data class Words(
        val tag: (Kind) -> String,
        val intro: (Kind, String) -> String,
        val howTo: (Kind) -> String,
        val button: (State) -> String,
        val reply: (State) -> String,
        val due: (String) -> String,
        val option: (Int, String) -> String,
        val choose: (Kind) -> String,
        val amount: (String) -> String,
        val footer: String,
    )

    fun words(context: Context): Words {
        val r = context.resources
        fun s(id: Int) = r.getString(id)
        return Words(
            tag = { k -> s(when (k) {
                Kind.Affida -> R.string.ins_tag_affida; Kind.Faccenda -> R.string.ins_tag_faccenda; Kind.Uscita -> R.string.ins_tag_uscita
                Kind.Orari -> R.string.ins_tag_orari; Kind.Spesa -> R.string.ins_tag_spesa; Kind.Condividi -> R.string.ins_tag_condividi
                Kind.Cena -> R.string.ins_tag_cena; Kind.Regalo -> R.string.ins_tag_regalo; Kind.Elenco -> R.string.ins_tag_elenco
                Kind.Decisione -> R.string.ins_tag_decisione
            }) },
            intro = { k, from -> r.getString(when (k) {
                Kind.Affida -> R.string.ins_intro_affida; Kind.Faccenda -> R.string.ins_intro_faccenda; Kind.Uscita -> R.string.ins_intro_uscita
                Kind.Orari -> R.string.ins_intro_orari; Kind.Spesa -> R.string.ins_intro_spesa; Kind.Condividi -> R.string.ins_intro_condividi
                Kind.Cena -> R.string.ins_intro_cena; Kind.Regalo -> R.string.ins_intro_regalo; Kind.Elenco -> R.string.ins_intro_elenco
                Kind.Decisione -> R.string.ins_intro_decisione
            }, from) },
            howTo = { k -> s(when (k) {
                Kind.Affida -> R.string.ins_how_affida; Kind.Faccenda -> R.string.ins_how_faccenda; Kind.Uscita -> R.string.ins_how_uscita
                Kind.Orari -> R.string.ins_how_orari; Kind.Spesa, Kind.Regalo -> R.string.ins_how_spesa; Kind.Cena -> R.string.ins_how_cena
                Kind.Condividi, Kind.Elenco, Kind.Decisione -> R.string.ins_how_vista
            }) },
            button = { st -> s(label(st)) },
            reply = { st -> s(replyWord(st)) },
            due = { d -> r.getString(R.string.ins_due, d) },
            option = { i, o -> r.getString(R.string.ins_option, i + 1, o) },
            choose = { k -> s(if (k == Kind.Orari) R.string.ins_choose_one else R.string.ins_choose_many) },
            amount = { a -> r.getString(R.string.ins_amount, a) },
            footer = s(R.string.ins_footer),
        )
    }

    /** L'etichetta del tasto, la stessa in Chat e nei pulsanti dell'email. */
    fun label(st: State): Int = when (st) {
        State.Seen -> R.string.ins_st_seen; State.Working -> R.string.ins_st_working; State.Done -> R.string.ins_st_done
        State.Declined -> R.string.ins_st_declined; State.Back -> R.string.ins_st_back; State.Yes -> R.string.ins_st_yes
        State.Late -> R.string.ins_st_late; State.No -> R.string.ins_st_no; State.Paid -> R.string.ins_st_paid
    }

    /** La parola che il pulsante scrive nella risposta (e che [Risposte] riconosce). */
    fun replyWord(st: State): Int = when (st) {
        State.Seen -> R.string.ins_w_seen; State.Working -> R.string.ins_w_working; State.Done -> R.string.ins_w_done
        State.Declined -> R.string.ins_w_declined; State.Back -> R.string.ins_w_seen; State.Yes -> R.string.ins_w_yes
        State.Late -> R.string.ins_w_late; State.No -> R.string.ins_w_no; State.Paid -> R.string.ins_w_paid
    }

    fun subject(c: Card, w: Words): String = "[" + w.tag(c.kind) + "] " + c.title.trim() + (if (c.due.isNotBlank()) " · " + c.due else "")

    /** L'oggetto senza «Re:», «R:», «I:», «Fwd:», in minuscolo: così si riconosce la risposta. */
    fun core(subject: String): String = subject.replace(Regex("^((re|r|fwd|fw|i|inoltra|aw|wg|sv|antw)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "").trim().lowercase()

    /** Il tipo dalla parentesi quadra dell'oggetto, se è una scheda. */
    fun kindOf(subject: String, w: Words): Kind? {
        val s = core(subject)
        return Kind.entries.firstOrNull { s.startsWith("[" + w.tag(it).lowercase() + "]") }
    }

    /** Il riferimento della scheda dentro il testo (anche citato in una risposta). */
    fun ref(text: String): String? = Regex(Regex.escape(REF) + "([A-Za-z0-9]{4,40})").find(text)?.groupValues?.get(1)

    /** Il testo semplice: chi la manda, cosa, entro quando, la riga, le scelte numerate, come rispondere. */
    fun text(c: Card, w: Words): String = buildString {
        append(w.intro(c.kind, c.from.ifBlank { "—" })).append("\n\n")
        append(c.title.trim()).append('\n')
        if (c.due.isNotBlank()) append(w.due(c.due)).append('\n')
        if (c.amount.isNotBlank()) append(w.amount(c.amount)).append('\n')
        if (c.note.isNotBlank()) append("«").append(c.note.trim()).append("»\n")
        if (c.options.isNotEmpty()) {
            append('\n').append(w.choose(c.kind)).append('\n')
            c.options.forEachIndexed { i, o -> append(w.option(i, o)).append('\n') }
        }
        if (c.link.isNotBlank()) append('\n').append(c.link).append('\n')
        append('\n').append(w.howTo(c.kind)).append("\n\n—\n").append(w.footer).append(" · ").append(REF).append(c.id)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Un pulsante: una risposta già scritta verso chi ha mandato la scheda. */
    fun mailto(to: String, subject: String, body: String): String = "mailto:" + to.trim() + "?subject=" + enc("Re: $subject") + "&body=" + enc(body)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** La versione HTML con i pulsanti (stili in linea, niente immagini né script: si legge ovunque). */
    fun html(c: Card, w: Words, replyTo: String): String {
        val subject = subject(c, w)
        fun button(label: String, body: String, strong: Boolean) =
            "<a href=\"" + esc(mailto(replyTo, subject, body)) + "\" style=\"display:inline-block;margin:4px 6px 4px 0;padding:10px 16px;border-radius:20px;" +
                (if (strong) "background:#3E5C8A;color:#ffffff;" else "background:#EEF1F5;color:#1F2A37;border:1px solid #C9D2DE;") +
                "font-weight:bold;text-decoration:none;font-family:sans-serif;font-size:14px\">" + esc(label) + "</a>"
        val buttons = if (Scheda.choosing(c.kind)) c.options.mapIndexed { i, o -> button(w.option(i, o), (i + 1).toString(), false) }
        else Scheda.actions(c.kind).let { list -> list.mapIndexed { i, st -> button(w.button(st), w.reply(st), i == list.lastIndex) } }
        return buildString {
            append("<div style=\"font-family:sans-serif;font-size:15px;line-height:1.5;color:#1F2A37\">")
            append("<p>").append(esc(w.intro(c.kind, c.from.ifBlank { "—" }))).append("</p>")
            append("<div style=\"border:1px solid #C9D2DE;border-radius:16px;padding:14px 16px;margin:12px 0\">")
            append("<div style=\"font-size:17px;font-weight:bold\">").append(esc(c.title.trim())).append("</div>")
            if (c.due.isNotBlank()) append("<div style=\"color:#55657A\">").append(esc(w.due(c.due))).append("</div>")
            if (c.amount.isNotBlank()) append("<div style=\"color:#55657A\">").append(esc(w.amount(c.amount))).append("</div>")
            if (c.note.isNotBlank()) append("<div style=\"margin-top:6px;font-style:italic\">«").append(esc(c.note.trim())).append("»</div>")
            if (c.options.isNotEmpty()) append("<div style=\"margin-top:10px;color:#55657A\">").append(esc(w.choose(c.kind))).append("</div>")
            append("<div style=\"margin-top:8px\">").append(buttons.joinToString("")).append("</div>")
            if (c.link.isNotBlank()) append("<div style=\"margin-top:8px\"><a href=\"").append(esc(c.link)).append("\">").append(esc(c.link)).append("</a></div>")
            append("</div>")
            append("<p style=\"color:#55657A;font-size:13px\">").append(esc(w.howTo(c.kind))).append("</p>")
            append("<p style=\"color:#8A97A8;font-size:11px\">").append(esc(w.footer)).append(" · ").append(REF).append(esc(c.id)).append("</p>")
            append("</div>")
        }
    }
}
