package eu.eurise.feature.launcher.apps

/** Pacchetti noti: l'ordine conta (Outlook sta in Comunicazione prima che "com.microsoft.office" dica Lavoro). */
private val PACKAGES: List<Pair<Category, List<String>>> = listOf(
    Category.Ai to listOf("com.openai", "com.anthropic", "com.google.android.apps.bard", "com.microsoft.copilot", "ai.perplexity", "ai.mistral", "com.deepseek"),
    Category.Browser to listOf(
        "foundation.e.browser", "org.mozilla", "org.lineageos.jelly", "com.android.chrome", "com.brave", "com.vivaldi",
        "com.opera", "com.duckduckgo", "org.torproject.torbrowser",
    ),
    Category.Communication to listOf(
        "com.android.dialer", "com.google.android.dialer", "org.fossify.phone", "com.android.messaging", "com.google.android.apps.messaging",
        "org.fossify.messages", "foundation.e.message", "com.android.contacts", "org.fossify.contacts", "foundation.e.mail", "com.fsck.k9",
        "net.thunderbird", "ch.protonmail", "com.infomaniak.mail", "com.whatsapp", "org.telegram", "org.thoughtcrime.securesms",
        "im.vector", "io.element", "com.google.android.gm", "com.microsoft.office.outlook",
    ),
    Category.Money to listOf("com.paypal", "com.revolut", "de.number26", "posteitaliane", "it.poste", "com.amazon.mshop", "com.ebay", "com.klarna"),
    Category.Tools to listOf(
        "org.fossify.clock", "com.android.deskclock", "com.kunzisoft.keepass", "com.x8bit.bitwarden", "com.beemdevelopment.aegis",
        "me.proton.android.pass", "com.google.android.apps.authenticator2", "app.organicmaps", "app.comaps", "foundation.e.maps",
        "com.google.android.apps.maps", "com.google.android.projection.gearhead", "foundation.e.privacycentral", "foundation.e.advancedprivacy",
        "com.android.settings", "foundation.e.apps", "org.fdroid", "de.markusfisch.android.binaryeye", "net.mullvad", "org.torproject.android",
    ),
    Category.Media to listOf(
        "eu.eurise.gallery", "foundation.e.gallery", "org.fossify.gallery", "org.lineageos.glimpse", "com.android.gallery3d", "com.android.camera", "foundation.e.camera",
        "org.lineageos.aperture", "org.lineageos.eleven", "foundation.e.music", "com.spotify", "com.google.android.youtube", "org.videolan.vlc",
        "org.schabi.newpipe", "com.netflix",
    ),
    Category.Work to listOf(
        "foundation.e.calendar", "org.fossify.calendar", "org.lineageos.etar", "foundation.e.notes", "foundation.e.tasks", "org.tasks", "com.nextcloud",
        "com.infomaniak.drive", "foundation.e.drive", "me.proton.android.drive", "com.android.documentsui", "org.fossify.filemanager", "com.android.calculator2",
        "org.lineageos.calculator", "com.google.android.apps.docs", "com.microsoft.office", "org.libreoffice", "com.adobe.reader",
    ),
)

/** Parole intere del pacchetto o del nome, per le app che nessun elenco può conoscere (banche, negozi…). */
private val WORDS: List<Pair<Category, Set<String>>> = listOf(
    Category.Ai to setOf("chatgpt", "openai", "claude", "gemini", "copilot", "perplexity", "deepseek", "grok", "mistral"),
    Category.Browser to setOf("browser", "chrome", "firefox", "brave", "vivaldi", "opera", "duckduckgo"),
    Category.Money to setOf(
        "bank", "banca", "banco", "bancoposta", "paypal", "satispay", "revolut", "n26", "poste", "postepay", "fineco", "unicredit", "intesa", "bnl",
        "klarna", "amazon", "ebay", "zalando", "temu", "shein", "vinted", "aliexpress", "esselunga", "coop", "conad", "carrefour", "lidl", "eurospin",
        "shop", "store", "wallet", "pagamenti",
    ),
    Category.Communication to setOf("sms", "pec", "mail", "email", "messaggi", "messages", "telefono", "phone", "whatsapp", "telegram", "contatti"),
    Category.Tools to setOf("vpn", "authenticator", "otp", "password", "maps", "mappe", "orologio", "clock", "auto", "bussola", "compass"),
    Category.Media to setOf("music", "musica", "video", "foto", "photos", "gallery", "galleria", "camera", "fotocamera", "podcast"),
    Category.Work to setOf("office", "pdf", "calendar", "calendario", "agenda", "notes", "note", "drive", "calculator", "calcolatrice", "docs", "documenti", "excel"),
)

private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

/** La categoria di un'app dal pacchetto o dal nome; nulla se non si riconosce. */
internal fun classifyApp(packageName: String, label: String): Category? {
    val pkg = packageName.lowercase()
    PACKAGES.firstOrNull { (_, prefixes) -> prefixes.any { pkg.startsWith(it) } }?.let { return it.first }
    val words = (SEPARATORS.split(pkg) + SEPARATORS.split(label.lowercase())).toSet()
    return WORDS.firstOrNull { (_, known) -> words.any { it in known } }?.first
}
