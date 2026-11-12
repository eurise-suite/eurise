package eu.eurise.mail

/** Sicurezza della connessione: SSL dall'inizio (993, 465) o STARTTLS (143, 587). Mai in chiaro. */
enum class Security { Ssl, StartTls }

data class Server(val host: String, val port: Int, val security: Security)

/** I server di una casella: IMAP per leggere, SMTP per inviare. [user] null = l'indirizzo intero. */
data class ServerSettings(val imap: Server, val smtp: Server, val user: String? = null)

/**
 * Un fornitore: nome, paese, valutazione di privacy (3 = Europa e attento, 2 = Europa, 1 = servizio chiuso fuori
 * dall'UE: si può usare, ma si propone per ultimo e con l'avviso). [appPasswordUrl]: per i servizi che non accettano la
 * password normale da un'app, la pagina dove crearne una «per le app».
 */
data class Provider(
    val name: String,
    val where: String,
    val level: Int,
    val domains: List<String>,
    val settings: ServerSettings,
    val appPasswordUrl: String? = null,
)

/**
 * Dove stanno i server delle caselle, senza chiedere nulla alla persona (tavola MailCaselle: «server e porte li trova
 * Eurise»). Prima la nostra tabella, poi le impostazioni pubbliche di Thunderbird, poi i nomi più comuni sul dominio.
 * Senza Android: si prova con i test.
 */
object Providers {
    private fun ssl(host: String, port: Int = 993) = Server(host, port, Security.Ssl)
    private fun tls(host: String, port: Int = 587) = Server(host, port, Security.StartTls)

    val KNOWN = listOf(
        Provider("Murena", "Francia, UE", 3, listOf("murena.io", "e.email"), ServerSettings(ssl("mail.ecloud.global"), tls("mail.ecloud.global"))),
        Provider("Posteo", "Germania, UE", 3, listOf("posteo.de", "posteo.net", "posteo.eu", "posteo.it", "posteo.org"), ServerSettings(ssl("posteo.de"), tls("posteo.de"))),
        Provider("mailbox.org", "Germania, UE", 3, listOf("mailbox.org"), ServerSettings(ssl("imap.mailbox.org"), ssl("smtp.mailbox.org", 465))),
        Provider("Disroot", "Paesi Bassi, UE", 3, listOf("disroot.org"), ServerSettings(ssl("disroot.org"), tls("disroot.org"))),
        Provider("Infomaniak", "Svizzera", 3, listOf("ik.me", "ikmail.com", "etik.com"), ServerSettings(ssl("mail.infomaniak.com"), tls("mail.infomaniak.com"))),
        Provider("GMX", "Germania, UE", 2, listOf("gmx.net", "gmx.de", "gmx.at", "gmx.ch", "gmx.com", "gmx.eu", "gmx.it"), ServerSettings(ssl("imap.gmx.net"), tls("mail.gmx.net"))),
        Provider("WEB.DE", "Germania, UE", 2, listOf("web.de"), ServerSettings(ssl("imap.web.de"), tls("smtp.web.de"))),
        Provider("Libero", "Italia, UE", 2, listOf("libero.it", "inwind.it", "iol.it", "blu.it", "giallo.it"), ServerSettings(ssl("imapmail.libero.it"), ssl("smtp.libero.it", 465))),
        Provider("Virgilio", "Italia, UE", 2, listOf("virgilio.it"), ServerSettings(ssl("in.virgilio.it"), ssl("out.virgilio.it", 465))),
        Provider("Aruba", "Italia, UE", 2, listOf("aruba.it"), ServerSettings(ssl("imaps.aruba.it"), ssl("smtps.aruba.it", 465))),
        Provider("Tiscali", "Italia, UE", 2, listOf("tiscali.it", "tiscalinet.it"), ServerSettings(ssl("imap.tiscali.it"), ssl("smtp.tiscali.it", 465))),
        // Servizi chiusi, USA: in fondo, valutazione bassa, con l'avviso (regola dell'11-12 novembre: decide la persona).
        Provider("Gmail", "Google · USA", 1, listOf("gmail.com", "googlemail.com"),
            ServerSettings(ssl("imap.gmail.com"), ssl("smtp.gmail.com", 465)), "https://myaccount.google.com/apppasswords"),
        Provider("iCloud", "Apple · USA", 1, listOf("icloud.com", "me.com", "mac.com"),
            ServerSettings(ssl("imap.mail.me.com"), tls("smtp.mail.me.com")), "https://account.apple.com/account/manage"),
        Provider("Yahoo", "Yahoo · USA", 1, listOf("yahoo.com", "yahoo.it", "ymail.com"),
            ServerSettings(ssl("imap.mail.yahoo.com"), ssl("smtp.mail.yahoo.com", 465)), "https://login.yahoo.com/account/security"),
    )

    /** Domini che accettano solo l'accesso dal loro sito (OAuth): arrivano quando Eurise sarà registrata presso Microsoft. */
    val NEEDS_OAUTH = setOf("outlook.com", "outlook.it", "hotmail.com", "hotmail.it", "live.com", "live.it", "msn.com")

    /** Proton e Tuta cifrano tutto e non aprono IMAP sul telefono. */
    val CLOSED = setOf("proton.me", "protonmail.com", "protonmail.ch", "pm.me", "tuta.com", "tutanota.com", "tutanota.de", "tutamail.com", "tuta.io", "keemail.me")

    fun domainOf(email: String): String = email.substringAfterLast('@', "").trim().lowercase()

    fun isValidEmail(email: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(email.trim())

    fun known(email: String): Provider? {
        val d = domainOf(email)
        return KNOWN.firstOrNull { d in it.domains }
    }

    /** Tentativi sul dominio, dal più probabile: imap./smtp., mail., il dominio stesso. */
    fun guesses(email: String): List<ServerSettings> {
        val d = domainOf(email)
        if (d.isEmpty()) return emptyList()
        return listOf(
            ServerSettings(ssl("imap.$d"), tls("smtp.$d")),
            ServerSettings(ssl("mail.$d"), tls("mail.$d")),
            ServerSettings(ssl("imap.$d"), ssl("smtp.$d", 465)),
            ServerSettings(ssl(d), tls(d)),
        )
    }

    /**
     * Le impostazioni dal formato «clientConfig» di Thunderbird (autoconfig.thunderbird.net e autoconfig.<dominio>).
     * Prende il primo IMAP e il primo SMTP non in chiaro; %EMAILADDRESS% o %EMAILLOCALPART% come nome utente.
     */
    fun parseAutoconfig(xml: String, email: String): ServerSettings? {
        fun servers(tag: String): List<Triple<String, Server?, String>> =
            Regex("<$tag\\s+type=\"(\\w+)\"[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
                val body = m.groupValues[2]
                fun v(name: String) = Regex("<$name>\\s*([^<]*?)\\s*</$name>").find(body)?.groupValues?.get(1).orEmpty()
                val host = v("hostname")
                val port = v("port").toIntOrNull()
                val security = when (v("socketType").uppercase()) {
                    "SSL" -> Security.Ssl
                    "STARTTLS" -> Security.StartTls
                    else -> null
                }
                val server = if (host.isNotEmpty() && port != null && security != null) Server(host, port, security) else null
                Triple(m.groupValues[1].lowercase(), server, v("username"))
            }.toList()
        val imap = servers("incomingServer").firstOrNull { it.first == "imap" && it.second != null } ?: return null
        val smtp = servers("outgoingServer").firstOrNull { it.first == "smtp" && it.second != null } ?: return null
        val local = email.substringBefore('@')
        val user = imap.third.replace("%EMAILADDRESS%", email).replace("%EMAILLOCALPART%", local)
            .replace("%EMAILDOMAIN%", domainOf(email)).ifBlank { null }
        return ServerSettings(imap.second!!, smtp.second!!, user?.takeIf { it != email })
    }
}
