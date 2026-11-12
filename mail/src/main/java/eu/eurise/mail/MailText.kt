package eu.eurise.mail

/** Dove va un messaggio nella posta (tavola MailPosta): deciso sul telefono, dalle intestazioni. */
enum class Kind { People, Alerts, Newsletters }

/** Logica dei testi della posta, senza Android: si prova con i test. */
object MailText {
    private val ROBOT = Regex(
        "^(no[-_.]?reply|do[-_.]?not[-_.]?reply|noreply|notifiche|notifica|notification|notifications|alert|alerts|avvisi|" +
            "info|account|accounts|security|sicurezza|billing|fatture|fatturazione|support|assistenza|service|servizio|clienti|" +
            "mailer[-_.]?daemon|postmaster|bounce|news|newsletter|marketing|offerte|promo)([-_.+].*)?$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Newsletter se ha le intestazioni delle liste (List-Unsubscribe, List-Id, Precedence bulk/list); avviso se scrive
     * un indirizzo «robot» (noreply, notifiche, info…); altrimenti una persona.
     */
    fun classify(fromAddress: String, listUnsubscribe: Boolean, listId: Boolean, precedence: String?): Kind {
        val p = precedence?.trim()?.lowercase()
        if (listUnsubscribe || listId || p == "bulk" || p == "list" || p == "junk") return Kind.Newsletters
        val local = fromAddress.substringBefore('@')
        if (ROBOT.matches(local)) return Kind.Alerts
        return Kind.People
    }

    /** Le iniziali per l'avatar: «Anna Rossi» → AR, «studio.tecnico@…» → ST. */
    fun initials(name: String, address: String): String {
        val source = name.ifBlank { address.substringBefore('@') }
        val words = source.split(Regex("[\\s._\\-]+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}"
            words.size == 1 -> words[0].take(2)
            else -> "?"
        }.uppercase()
    }

    /** «Re: » una volta sola, anche se l'oggetto ha già «R:», «Re:», «RE:», «Fwd:»… */
    fun replySubject(subject: String): String {
        val clean = subject.replace(Regex("^((re|r|fwd|fw|i|inoltra)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "").trim()
        return "Re: $clean"
    }

    /** L'oggetto di un inoltro: «I: …» senza ripetere i prefissi. */
    fun forwardSubject(subject: String): String {
        val clean = subject.replace(Regex("^((re|r|fwd|fw|i|inoltra)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "").trim()
        return "I: $clean"
    }

    /** Il testo citato nella risposta: «Il <data>, <chi> ha scritto:» e le righe con «> ». */
    fun quote(text: String, header: String): String =
        "\n\n$header\n" + text.trimEnd().lineSequence().joinToString("\n") { "> $it" }

    /** Da HTML a testo leggibile (anteprime, ricerca, citazioni). */
    fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), "")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace(Regex("[ \\t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()

    /** Quante immagini remote ha l'HTML (quelle che possono dire al mittente che hai aperto: «pixel spia»). */
    fun remoteImages(html: String): Int =
        Regex("(?i)<img[^>]+src\\s*=\\s*[\"']?https?://").findAll(html).count() +
            Regex("(?i)url\\(\\s*[\"']?https?://").findAll(html).count()

    /** L'HTML della mail dentro una pagina con i colori della suite e il testo che si adatta allo schermo. */
    fun page(html: String, dark: Boolean): String {
        val bg = if (dark) "#14171C" else "#FFFFFF"
        val fg = if (dark) "#ECE9E3" else "#1E1D1B"
        val link = if (dark) "#A9C1F2" else "#1F2A44"
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>body{margin:0;padding:0 2px;font-family:sans-serif;font-size:16px;line-height:1.5;background:$bg;color:$fg;word-wrap:break-word}" +
            "a{color:$link}img{max-width:100%;height:auto}table{max-width:100%}pre{white-space:pre-wrap}blockquote{margin:0;padding-left:10px;border-left:3px solid #8888}</style>" +
            "</head><body>$html</body></html>"
    }

    /** Testo semplice come HTML (con i link cliccabili). */
    fun plainToHtml(text: String): String {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val linked = escaped.replace(Regex("(https?://[^\\s<]+)"), "<a href=\"$1\">$1</a>")
        return "<div style=\"white-space:pre-wrap\">$linked</div>"
    }
}
