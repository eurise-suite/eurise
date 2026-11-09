package eu.eurise.feature.launcher.home

/**
 * Letture dei testi per le bolle: codici di accesso e consegne dalle notifiche, link delle videochiamate dagli
 * appuntamenti. Tutto avviene sul telefono, su testi che il telefono ha già.
 */
object BubbleText {

    private val CodeWords = Regex(
        """(?iu)\b(codice|code|otp|verifica|verification|conferma|accesso|login|pin|password monouso|one[- ]time|token|2fa)\b""",
    )

    /** Gruppi di 4–8 cifre, anche divisi da uno spazio o un trattino ("482 913", "48-29-13"). */
    private val CodeDigits = Regex("""(?<!\d|\d[.,/:])(\d{2,8}(?:[ -]\d{2,4}){0,3})(?!\d|[.,/:]\d)""")

    /** Il codice di accesso nel testo, se il testo parla di un codice; solo cifre, senza spazi. */
    fun findCode(text: String): String? {
        if (!CodeWords.containsMatchIn(text)) return null
        return CodeDigits.findAll(text)
            .map { it.groupValues[1].replace(" ", "").replace("-", "") }
            .firstOrNull { it.length in 4..8 && !looksLikeYear(it) }
    }

    private fun looksLikeYear(s: String) = s.length == 4 && s.toInt() in 1990..2099

    private val DeliveryWords = Regex(
        """(?iu)\b(pacco|pacchi|spedizione|spedito|consegna|consegnato|corriere|in transito|ritiro|locker|tracking|tracciamento|delivery|delivered|shipment|shipped|out for delivery|parcel)\b""",
    )

    private val DeliveryPackages = listOf(
        "com.amazon", "it.poste", "posteitaliane", "com.dhl", "de.dhl", "com.ups", "com.gls", "it.brt", "com.sda",
        "pl.inpost", "com.inpost", "com.fedex", "nl.postnl", "com.parcelapp", "de.orrs.deliveries", "com.vinted",
    )

    /** Vero se la notifica parla di un pacco in arrivo o consegnato. */
    fun isDelivery(packageName: String, text: String): Boolean =
        DeliveryPackages.any { packageName.startsWith(it) } || DeliveryWords.containsMatchIn(text)

    private val MeetingLink = Regex(
        """(?i)https?://[^\s<>"']*(meet\.jit\.si|jitsi|zoom\.us|meet\.google\.com|teams\.microsoft\.com|teams\.live\.com|whereby\.com|webex\.com|bigbluebutton|meet\.[a-z0-9-]+\.[a-z]+|talk|bbb\.)[^\s<>"']*""",
    )

    /** Il primo link di videochiamata trovato nel testo (luogo o descrizione di un appuntamento). */
    fun meetingLink(text: String): String? = MeetingLink.find(text)?.value?.trimEnd('.', ',', ')', ';')

    /** App dell'orologio di sistema e delle più diffuse alternative libere. */
    /** App di posta certificata: Aruba, Legalmail e le altre che hanno "pec" come parola del nome del pacchetto. */
    fun isPec(packageName: String): Boolean =
        packageName.lowercase().split('.', '_').any { it == "pec" || it == "legalmail" || it == "pecmail" }

    private val PlaceBracket = Regex("""#\\[([^\\]]+)]""")
    private val PlaceWord = Regex("""#([\\p{L}\\p{N}_-]+)""")

    /** Il luogo scritto in una cosa da fare: #[Via Roma 12], oppure #Stazione (i trattini valgono come spazi). */
    fun taskPlace(text: String): String? =
        PlaceBracket.find(text)?.groupValues?.get(1)?.trim()
            ?: PlaceWord.find(text)?.groupValues?.get(1)?.replace('_', ' ')?.replace('-', ' ')

    /** Il testo senza il #luogo, per mostrarlo. */
    fun withoutPlace(text: String): String =
        text.replace(PlaceBracket, "").replace(PlaceWord, "").replace(Regex(" {2,}"), " ").trim()

    private val PhoneText = Regex("""(?<![\\d+])(\\+?[03]\\d[\\d .-]{6,}\\d)""")
    private val EmailText = Regex("""[\\w.+-]+@[\\w-]+\\.[\\w.-]+""")
    private val WithName = Regex("""(?i)\\bcon\\s+(\\p{Lu}\\p{L}+(?:\\s+\\p{Lu}\\p{L}+)?)""")

    /** Un numero di telefono in un testo: da 9 a 13 cifre, che inizia come un numero italiano o internazionale. */
    fun phoneIn(text: String): String? = PhoneText.findAll(text).map { it.value.filterNot { c -> c == ' ' || c == '.' || c == '-' } }
        .firstOrNull { n -> n.count(Char::isDigit) in 9..13 }

    fun emailIn(text: String): String? = EmailText.find(text)?.value?.trimEnd('.')

    /** Il nome dopo "con": "Pranzo con Anna Rossi" dà "Anna Rossi". */
    fun personIn(title: String): String? = WithName.find(title)?.groupValues?.get(1)

    /** Il numero nel formato di wa.me: solo cifre, con il prefisso italiano se manca. */
    fun whatsappNumber(phone: String): String {
        val digits = phone.filter(Char::isDigit).removePrefix("00")
        return if (!phone.startsWith("+") && !phone.startsWith("00") && digits.length == 10 && digits.startsWith("3")) "39$digits" else digits
    }

    fun isClock(packageName: String): Boolean =
        "deskclock" in packageName || packageName.endsWith(".clock") || ".clock." in packageName || "alarmclock" in packageName
}
