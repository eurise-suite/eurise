package eu.eurise.core.suite

/**
 * Difesa dalle truffe nei messaggi (tavola ChatTruffa), per Messaggi e Chat: tutto sul telefono, il testo non esce.
 *
 * Riconosce i copioni più usati in Italia quando scrive qualcuno che non è in rubrica: il familiare con il «numero nuovo»
 * che chiede soldi, la richiesta di soldi urgente, il conto o la carta bloccati con un link, il pacco in giacenza, chi
 * chiede il codice arrivato per SMS. Meglio un avviso in più che uno in meno, ma solo con almeno due segni insieme.
 */
object ScamCheck {
    enum class Kind { Family, Money, Bank, Parcel, Code }

    /** Il copione riconosciuto e, se il messaggio lo dice («sono Luca»), il nome con cui si presenta. */
    data class Verdict(val kind: Kind, val name: String?)

    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    private val FAMILY = rx("""\b(ciao\s+)?(mamma|mamm[iì]|pap[aà]|babbo|nonna|nonno|zia|zio|amore|tesoro)\b""")
    private val NEW_NUMBER = rx("""numero\s+nuovo|nuovo\s+numero|cambiato\s+(il\s+)?numero|(rotto|perso|caduto|rubato)\s+(il\s+)?(telefono|cellulare)|telefono\s+(rotto|perso|in\s+riparazione)|salva\s+(questo|il\s+mio)\s+numero""")
    private val MONEY = rx("""bonifico|ricarica|soldi|pagare|pagamento|prestito|\d+[.,]?\d*\s*(euro|€)|€\s*\d+|iban|postepay|paypal""")
    private val URGENT = rx("""urgent[ei]|subito|entro\s+oggi|oggi\s+stesso|al\s+pi[uù]\s+presto|ti\s+spiego\s+dopo|non\s+posso\s+(parlare|chiamare)|scrivimi\s+(qui|su\s+whatsapp)""")
    private val BANK_WORDS = rx("""conto|carta|bancomat|banca|poste|intesa|unicredit|bper|bnl|fineco|mps|inps|agenzia\s+delle\s+entrate|rimborso|spid""")
    private val BANK_ALARM = rx("""bloccat[oa]|sospes[oa]|limitat[oa]|accesso\s+anomalo|accesso\s+non\s+autorizzato|verifica\s+(i\s+tuoi\s+dati|la\s+tua\s+identit[aà])|aggiorna\s+i\s+(tuoi\s+)?dati|operazione\s+sospetta|annullare\s+(il\s+)?pagamento""")
    private val PARCEL = rx("""pacco|spedizione|consegna|corriere|giacenza""")
    private val PARCEL_ALARM = rx("""in\s+giacenza|spese\s+di\s+(spedizione|sdoganamento)|indirizzo\s+(incompleto|errato)|conferma\s+(il\s+tuo\s+)?indirizzo|paga\s+\d""")
    private val LINK = rx("""https?://|www\.|bit\.ly|\b[a-z0-9-]+\.(com|it|info|xyz|top|net|link|live|online)/""")
    private val CODE_ASK = rx("""(dimmi|mandami|inviami|girami|passami|leggimi)\s+(il\s+)?codice|codice\s+(che\s+ti\s+(è\s+)?arrivat[oa]|ricevuto)|ti\s+(è\s+)?arrivato\s+un\s+codice|codice\s+(di\s+)?(verifica|otp)\s+per\s+errore""")
    private val NAME = Regex("""\bsono\s+(?:io\s*,?\s*)?([A-ZÀ-Ý][a-zà-ÿ]{2,})""")

    /**
     * Il copione che il messaggio [text] somiglia di più, o null. [known]: chi scrive è in rubrica (o verificato): allora
     * non si avvisa, tranne per chi chiede il codice (anche un amico con l'account rubato lo fa).
     */
    fun check(text: String, known: Boolean = false): Verdict? {
        val t = text.trim()
        if (t.length < 12) return null
        val name = NAME.find(t)?.groupValues?.get(1)?.takeUnless { it.lowercase() in NOT_NAMES }
        if (CODE_ASK.containsMatchIn(t)) return Verdict(Kind.Code, name)
        if (known) return null
        val money = MONEY.containsMatchIn(t)
        if (FAMILY.containsMatchIn(t) && NEW_NUMBER.containsMatchIn(t) && (money || URGENT.containsMatchIn(t))) return Verdict(Kind.Family, name)
        if (NEW_NUMBER.containsMatchIn(t) && money) return Verdict(Kind.Family, name)
        if (BANK_WORDS.containsMatchIn(t) && BANK_ALARM.containsMatchIn(t) && (LINK.containsMatchIn(t) || URGENT.containsMatchIn(t) || PHONE.containsMatchIn(t))) return Verdict(Kind.Bank, null)
        if (PARCEL.containsMatchIn(t) && PARCEL_ALARM.containsMatchIn(t) && LINK.containsMatchIn(t)) return Verdict(Kind.Parcel, null)
        if (money && URGENT.containsMatchIn(t) && (FAMILY.containsMatchIn(t) || LINK.containsMatchIn(t))) return Verdict(Kind.Money, name)
        return null
    }

    private val PHONE = Regex("""(\+39\s?)?\b(800|803|0\d{1,3}|3\d{2})[\s.]?\d{3}[\s.]?\d{3,4}\b""")
    private val NOT_NAMES = setOf("qui", "io", "tua", "tuo", "nuovo", "nuova", "sempre", "stato", "stata", "solo", "sola", "senza", "fuori", "rimasto", "rimasta")
}
