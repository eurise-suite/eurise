package eu.eurise.core.data.messages

import eu.eurise.core.data.model.QuickAdd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Che cosa si capisce da un messaggio (SMS, email, chat), tutto sul telefono: un appuntamento, un numero da chiamare o
 * salvare, un indirizzo, un codice di verifica, una consegna, una cosa da fare, un appunto, un'altra lingua. Ogni cosa
 * diventa una proposta nella conversazione (Crea l'evento, Chiama, Traduci…). Niente rete, niente modelli: regole.
 */
object MessageInsights {

    sealed interface Insight {
        /** Un appuntamento: giorno e (se c'è) ora. [text] è il pezzo di frase da cui nasce. */
        data class Event(val date: LocalDate, val time: LocalTime?, val text: String) : Insight
        data class Phone(val number: String) : Insight
        data class Email(val address: String) : Insight
        data class Link(val url: String) : Insight
        data class Address(val text: String) : Insight
        /** Codice di verifica (OTP): si copia e si cancella da solo dopo poco. */
        data class Code(val code: String) : Insight
        data object Delivery : Insight
        data class Task(val text: String) : Insight
        data class Note(val text: String) : Insight
        /** Il messaggio è in un'altra lingua ([language], codice di due lettere). */
        data class Foreign(val language: String) : Insight
    }

    enum class Category { People, Codes, Alerts }

    private const val B = """(?<![\p{L}\p{N}])"""
    private const val E = """(?![\p{L}\p{N}])"""

    private val CODE_WORDS = Regex("""(?iu)${B}(codice|code|otp|pin|password|passcode|verifica|verification|conferma|token|chiave)$E""")
    private val CODE = Regex("""(?<![\d€.,:/-])(\d{4,8}|\d{3}[ -]\d{3})(?![\d€%]|[.,]\d)""")
    private val DELIVERY = Regex("""(?iu)${B}(consegna|consegnato|spedizione|spedito|pacco|corriere|tracking|ritiro|locker|in giacenza|parcel|delivery|shipment)""")
    private val PHONE = Regex("""(?<![\p{L}\d+])(\+\d{2}[\s.]?)?(3\d{2}|0\d{1,4})[\s./-]?\d{2,4}[\s./-]?\d{2,4}(?:[\s./-]?\d{1,4})?(?![\d])""")
    private val EMAIL = Regex("""[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.[\p{L}]{2,}""")
    private val LINK = Regex("""(?iu)\b(?:https?://|www\.)[^\s<>"]+""")
    private val ADDRESS = Regex("""(?u)\b(?:[Vv]ia|[Vv]iale|[Pp]iazza|[Pp]iazzale|[Cc]orso|[Ll]argo|[Vv]icolo|[Ll]ungomare|[Ss]trada)\s+(?:[\p{Lu}0-9][\p{L}'’.]*\s?){1,5}(?:,?\s*\d{1,4}[\p{L}]?)?""")
    private val TASK = Regex("""(?iu)${B}(ricordati di|ricorda di|non dimenticare di|non dimenticarti di|puoi|potresti|dovresti|devi|bisogna|serve che tu|mi mandi|mi porti|da fare)$E""")
    private val NOTE = Regex("""(?iu)${B}(segna|segnati|annota|annotati|prendi nota|appunto|nota bene|codice cliente|numero di pratica|iban)$E""")
    private val CONFIRM = Regex("""(?iu)^\s*(ok(ay|ei)?|va bene|vabbene|perfetto|d'accordo|daccordo|confermo|confermato|sì|si|certo|ci sono|ci vediamo|a posto|benissimo|ottimo|fatto|deal|yes|sure|sounds good|perfect)\b""")
    private val SUSPICIOUS = Regex("""(?iu)(conto|carta).{0,40}(bloccat|sospes|limitat)|verifica (i tuoi dati|l'account|il conto)|pacco in giacenza|rimborso.{0,30}(inps|agenzia|entrate)|clicca (qui|sul link)|aggiorna i (tuoi )?dati""")

    /** Le proposte per un messaggio, in ordine di utilità. [now] serve a capire «giovedì», «domani»… */
    fun analyze(text: String, now: LocalDateTime, deviceLanguage: String = "it"): List<Insight> {
        val out = mutableListOf<Insight>()
        code(text)?.let { out += Insight.Code(it) }
        if (DELIVERY.containsMatchIn(text)) out += Insight.Delivery
        val quick = QuickAdd.parse(text, now)
        if (quick.due != null && (quick.time != null || quick.pieces.any { it.kind == QuickAdd.Kind.Date })) {
            val piece = quick.pieces.filter { it.kind == QuickAdd.Kind.Date || it.kind == QuickAdd.Kind.Time }
            val span = if (piece.isEmpty()) text else text.substring(piece.minOf { it.start }, piece.maxOf { it.end }).trim()
            if (out.none { it is Insight.Code }) out += Insight.Event(quick.due, quick.time, span)
        }
        val codes = out.filterIsInstance<Insight.Code>().map { it.code.filter(Char::isDigit) }
        PHONE.findAll(text).map { it.value.trim() }.filter { n ->
            val digits = n.filter(Char::isDigit)
            digits.length in 8..13 && digits !in codes
        }.distinct().forEach { out += Insight.Phone(it) }
        EMAIL.findAll(text).map { it.value.trimEnd('.') }.distinct().forEach { out += Insight.Email(it) }
        LINK.findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().forEach { out += Insight.Link(it) }
        ADDRESS.findAll(text).map { it.value.trim().trimEnd(',', '.') }.filter { it.split(' ').size >= 2 }.distinct().forEach { out += Insight.Address(it) }
        TASK.find(text)?.let { out += Insight.Task(sentenceAround(text, it.range.first)) }
        NOTE.find(text)?.let { out += Insight.Note(text.trim()) }
        language(text)?.takeIf { it != deviceLanguage }?.let { out += Insight.Foreign(it) }
        return out
    }

    /** Il codice di verifica, se il messaggio ne ha uno vicino a parole come «codice», «OTP», «PIN». */
    fun code(text: String): String? {
        if (!CODE_WORDS.containsMatchIn(text)) return null
        return CODE.findAll(text).map { it.groupValues[1] }.firstOrNull { c ->
            val digits = c.filter(Char::isDigit)
            // Non un anno, non un orario scritto come «1530» dopo «alle».
            !(digits.length == 4 && digits.toInt() in 1900..2100)
        }
    }

    /** Vero se il messaggio è una conferma breve («ok», «va bene», «perfetto, ci vediamo lì»). */
    fun isConfirmation(text: String): Boolean = text.length <= 120 && CONFIRM.containsMatchIn(text)

    /** Messaggio che somiglia a una truffa nota (conto bloccato, pacco in giacenza, rimborso con link). */
    fun isSuspicious(text: String): Boolean = SUSPICIOUS.containsMatchIn(text) && (LINK.containsMatchIn(text) || PHONE.containsMatchIn(text))

    /**
     * Dove mettere una conversazione: Codici e consegne (codici, corrieri), Avvisi (mittenti con un nome invece del
     * numero, messaggi sospetti), Persone (il resto). [sender] è il mittente come arriva («+39…», «INFO-BANCA»).
     */
    fun category(sender: String, lastText: String): Category {
        val isNumber = sender.count(Char::isDigit) >= 6 && sender.none { it.isLetter() }
        return when {
            code(lastText) != null || DELIVERY.containsMatchIn(lastText) -> Category.Codes
            !isNumber || isSuspicious(lastText) -> Category.Alerts
            else -> Category.People
        }
    }

    /** La lingua del testo tra quelle più comuni, se si capisce con sicurezza (almeno tre parole tipiche). */
    fun language(text: String): String? {
        val words = text.lowercase().split(Regex("""[^\p{L}']+""")).filter { it.isNotEmpty() }
        if (words.size < 3) return null
        val scores = STOPWORDS.mapValues { (_, set) -> words.count { it in set } }
        val best = scores.maxBy { it.value }
        val second = scores.filterKeys { it != best.key }.maxOf { it.value }
        return best.key.takeIf { best.value >= 3 && best.value >= second * 2 }
    }

    private fun sentenceAround(text: String, at: Int): String {
        val start = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), at).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), at).let { if (it < 0) text.length else it }
        return text.substring(start, end).trim()
    }

    private val STOPWORDS = mapOf(
        "it" to setOf("il", "lo", "la", "gli", "le", "di", "che", "e", "è", "per", "con", "non", "una", "un", "sono", "ci", "ti", "mi", "del", "della", "alle", "domani", "oggi", "grazie", "ciao", "anche", "come", "però", "quando", "dove", "va", "bene", "sei", "hai", "ho"),
        "en" to setOf("the", "and", "is", "are", "you", "to", "of", "for", "with", "this", "that", "it", "on", "be", "at", "we", "your", "will", "have", "can", "please", "thanks", "tomorrow", "today", "hi", "hello", "see", "i'm", "it's"),
        "fr" to setOf("le", "la", "les", "et", "est", "vous", "je", "tu", "nous", "pour", "avec", "pas", "une", "des", "du", "que", "qui", "dans", "sur", "merci", "bonjour", "demain", "aujourd'hui", "c'est", "suis", "êtes"),
        "de" to setOf("der", "die", "das", "und", "ist", "ich", "du", "sie", "wir", "nicht", "mit", "für", "ein", "eine", "auf", "zu", "den", "dem", "danke", "morgen", "heute", "bitte", "hallo", "bin", "sind"),
        "es" to setOf("el", "los", "las", "y", "es", "que", "de", "en", "por", "para", "con", "no", "una", "un", "gracias", "hola", "mañana", "hoy", "estoy", "eres", "está", "nos", "vemos", "muy"),
        "pt" to setOf("o", "os", "as", "e", "é", "que", "de", "em", "por", "para", "com", "não", "uma", "um", "obrigado", "obrigada", "olá", "amanhã", "hoje", "estou", "você", "está", "nós", "muito"),
    )
}
