package eu.eurise.password.autofill

/** Che cosa contiene un campo di un modulo. */
enum class FieldKind { Username, Password, NewPassword, CardNumber, CardExpiry, CardMonth, CardYear, CardCvc, CardHolder }

/** Le decisioni della compilazione automatica, senza Android: si provano nei test. */
object Matching {
    /**
     * Antifrode: il sito deve essere quello salvato o un suo sottodominio. "murena.io.truffa.com" e "falsomurena.io"
     * non passano, e una voce di un sottodominio non vale per il dominio superiore.
     */
    fun domainMatches(entryUrl: String, webDomain: String): Boolean {
        val saved = host(entryUrl)
        val page = host(webDomain)
        if (saved.isEmpty() || page.isEmpty()) return false
        return page == saved || page.endsWith(".$saved")
    }

    fun host(url: String): String = url.trim().lowercase()
        .substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfter('@').substringBefore(':').removePrefix("www.")

    /** Riconosce il campo, nell'ordine dal dato più esplicito a quello più incerto; null se non interessa. */
    fun classify(hints: List<String>, html: Map<String, String>, idEntry: String?, inputType: Int): FieldKind? {
        hints.firstNotNullOfOrNull { byHint(it.lowercase()) }?.let { return it }
        html["autocomplete"]?.lowercase()?.split(' ')?.firstNotNullOfOrNull { byAutocomplete(it) }?.let { return it }
        val type = html["type"]?.lowercase()
        if (type == "password") return byWords(listOfNotNull(html["name"], html["id"], idEntry)) ?: FieldKind.Password
        if (type == "email") return FieldKind.Username
        val cls = inputType and 0xF
        val variation = inputType and 0xFF0
        if (cls == 1 && variation in setOf(0x80, 0x90, 0xE0)) return FieldKind.Password
        if (cls == 2 && variation == 0x10) return byWords(listOfNotNull(idEntry)) ?: FieldKind.Password
        if (cls == 1 && variation in setOf(0x20, 0xD0)) return FieldKind.Username
        return byWords(listOfNotNull(html["name"], html["id"], html["placeholder"], html["aria-label"], idEntry))
    }

    /** Se c'è una password ma nessun utente, l'utente è il campo di testo subito prima della password. */
    fun fillUsername(kinds: List<FieldKind?>): List<FieldKind?> {
        if (kinds.any { it == FieldKind.Username }) return kinds
        val first = kinds.indexOfFirst { it == FieldKind.Password || it == FieldKind.NewPassword }
        if (first <= 0 || kinds[first - 1] != null) return kinds
        return kinds.toMutableList().also { it[first - 1] = FieldKind.Username }
    }

    private fun byHint(h: String): FieldKind? = when (h) {
        "username", "emailaddress", "email" -> FieldKind.Username
        "password", "current-password" -> FieldKind.Password
        "new-password", "newpassword" -> FieldKind.NewPassword
        "creditcardnumber" -> FieldKind.CardNumber
        "creditcardexpirationdate" -> FieldKind.CardExpiry
        "creditcardexpirationmonth" -> FieldKind.CardMonth
        "creditcardexpirationyear" -> FieldKind.CardYear
        "creditcardsecuritycode" -> FieldKind.CardCvc
        else -> byAutocomplete(h)
    }

    private fun byAutocomplete(a: String): FieldKind? = when (a) {
        "username", "email" -> FieldKind.Username
        "current-password" -> FieldKind.Password
        "new-password" -> FieldKind.NewPassword
        "cc-number" -> FieldKind.CardNumber
        "cc-exp" -> FieldKind.CardExpiry
        "cc-exp-month" -> FieldKind.CardMonth
        "cc-exp-year" -> FieldKind.CardYear
        "cc-csc" -> FieldKind.CardCvc
        "cc-name" -> FieldKind.CardHolder
        else -> null
    }

    private fun byWords(values: List<String>): FieldKind? {
        val text = values.joinToString(" ").lowercase()
        if (text.isBlank()) return null
        fun has(vararg words: String) = words.any { text.contains(it) }
        return when {
            has("cvv", "cvc", "csc", "securitycode", "security_code", "codice_sicurezza") -> FieldKind.CardCvc
            has("cardnumber", "card_number", "card-number", "ccnumber", "cc-number", "numero_carta", "numerocarta") -> FieldKind.CardNumber
            has("expiry", "cc-exp", "scadenza") -> FieldKind.CardExpiry
            has("cardholder", "card_holder", "intestatario") -> FieldKind.CardHolder
            has("new_password", "newpassword", "new-password", "nuova_password") -> FieldKind.NewPassword
            has("password", "passwd", "pwd", "passphrase") -> FieldKind.Password
            has("username", "user_name", "userid", "login", "e-mail", "email", "utente") -> FieldKind.Username
            else -> null
        }
    }
}
