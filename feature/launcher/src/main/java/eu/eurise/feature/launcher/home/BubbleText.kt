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
    fun isClock(packageName: String): Boolean =
        "deskclock" in packageName || packageName.endsWith(".clock") || ".clock." in packageName || "alarmclock" in packageName
}
