package eu.eurise.core.sync

import com.sun.mail.imap.IMAPFolder
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Sicurezza della connessione: SSL dall'inizio (993/465) o STARTTLS (143/587). Mai in chiaro. */
enum class MailSecurity { Ssl, StartTls }

data class MailServer(val host: String, val port: Int, val security: MailSecurity)

/** Una casella: indirizzo, nome che compare, server e credenziali (la password arriva dal portachiavi del sistema). */
data class MailAccount(
    val address: String,
    val name: String,
    val imap: MailServer,
    val smtp: MailServer,
    val user: String = address,
)

/** Il tipo di messaggio per l'elenco, come sul telefono: persone, avvisi automatici, newsletter. */
enum class MailKind { Person, Notice, Newsletter }

/** Un messaggio nell'elenco: solo le intestazioni; il testo si scarica quando lo si apre. */
data class MailSummary(
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val date: Long,
    val seen: Boolean,
    val answered: Boolean,
    val kind: MailKind,
)

/** Un messaggio aperto: [text] per citare e cercare, [html] se il messaggio ne ha uno. */
data class MailOpened(val summary: MailSummary, val to: String, val cc: String, val text: String, val html: String?, val attachments: List<String>, val messageId: String?)

/**
 * IMAP per leggere e SMTP per inviare, direttamente dal computer alla casella dell'utente (nessun server nostro in
 * mezzo). Ogni chiamata apre e chiude la sua connessione; da chiamare fuori dal thread dell'interfaccia.
 */
class MailClient(private val account: MailAccount, private val password: () -> String) {

    private fun props(): Properties = Properties().apply {
        val i = account.imap
        val s = account.smtp
        val ip = if (i.security == MailSecurity.Ssl) "imaps" else "imap"
        put("mail.store.protocol", ip)
        put("mail.$ip.host", i.host)
        put("mail.$ip.port", i.port.toString())
        put("mail.$ip.connectiontimeout", "20000")
        put("mail.$ip.timeout", "60000")
        put("mail.$ip.ssl.checkserveridentity", "true")
        if (i.security == MailSecurity.StartTls) put("mail.$ip.starttls.required", "true")
        val sp = if (s.security == MailSecurity.Ssl) "smtps" else "smtp"
        put("mail.transport.protocol", sp)
        put("mail.$sp.host", s.host)
        put("mail.$sp.port", s.port.toString())
        put("mail.$sp.auth", "true")
        put("mail.$sp.connectiontimeout", "20000")
        put("mail.$sp.timeout", "60000")
        put("mail.$sp.ssl.checkserveridentity", "true")
        if (s.security == MailSecurity.StartTls) {
            put("mail.$sp.starttls.enable", "true")
            put("mail.$sp.starttls.required", "true")
        }
    }

    private fun <T> withStore(block: (Store) -> T): T {
        val session = Session.getInstance(props())
        val store = session.store
        store.connect(account.imap.host, account.imap.port, account.user, password())
        try { return block(store) } finally { runCatching { store.close() } }
    }

    /** Prova l'accesso: true se IMAP risponde con queste credenziali. */
    fun check(): Boolean = runCatching { withStore { it.isConnected } }.getOrDefault(false)

    /**
     * I messaggi di [folder] più nuovi di [sinceUid] (tutti gli ultimi [limit] la prima volta), con le intestazioni
     * che servono all'elenco. Restituisce anche l'UIDVALIDITY: se cambia, la cartella va riletta da capo.
     */
    fun fetch(folder: String = "INBOX", sinceUid: Long = 0, limit: Int = 200): Pair<Long, List<MailSummary>> = withStore { store ->
        val f = store.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_ONLY)
        try {
            val validity = f.uidValidity
            val messages: Array<Message> = if (sinceUid > 0) {
                f.getMessagesByUID(sinceUid + 1, UIDFolder.LASTUID)
            } else {
                val n = f.messageCount
                if (n == 0) emptyArray() else f.getMessages(maxOf(1, n - limit + 1), n)
            }
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(UIDFolder.FetchProfileItem.UID)
                add("List-Unsubscribe")
                add("Auto-Submitted")
                add("Precedence")
            }
            f.fetch(messages, fp)
            validity to messages.mapNotNull { m ->
                val uid = f.getUID(m)
                if (uid <= sinceUid) return@mapNotNull null
                summary(uid, m)
            }.sortedByDescending { it.date }
        } finally { runCatching { f.close(false) } }
    }

    /** Apre un messaggio (e lo segna come letto). */
    fun open(folder: String, uid: Long): MailOpened? = withStore { store ->
        val f = store.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_WRITE)
        try {
            val m = f.getMessageByUID(uid) ?: return@withStore null
            val parts = Parts()
            parts.walk(m)
            m.setFlag(Flags.Flag.SEEN, true)
            MailOpened(
                summary = summary(uid, m),
                to = m.getRecipients(Message.RecipientType.TO)?.joinToString(", ", transform = ::show).orEmpty(),
                cc = m.getRecipients(Message.RecipientType.CC)?.joinToString(", ", transform = ::show).orEmpty(),
                text = parts.text.toString().ifBlank { MailText.plain(parts.html.toString()) },
                html = parts.html.toString().ifBlank { null },
                attachments = parts.attachments,
                messageId = (m as? MimeMessage)?.messageID,
            )
        } finally { runCatching { f.close(false) } }
    }

    /** Invia un messaggio di solo testo; [inReplyTo] tiene insieme la conversazione. */
    fun send(to: List<String>, subject: String, text: String, cc: List<String> = emptyList(), inReplyTo: String? = null) {
        val session = Session.getInstance(props())
        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(account.address, account.name, "UTF-8"))
            setRecipients(Message.RecipientType.TO, to.joinToString(","))
            if (cc.isNotEmpty()) setRecipients(Message.RecipientType.CC, cc.joinToString(","))
            setSubject(subject, "UTF-8")
            setText(text, "UTF-8")
            sentDate = Date()
            inReplyTo?.let { setHeader("In-Reply-To", it); setHeader("References", it) }
            setHeader("User-Agent", "Eurise")
        }
        val transport = session.transport
        transport.connect(account.smtp.host, account.smtp.port, account.user, password())
        try { transport.sendMessage(msg, msg.allRecipients) } finally { runCatching { transport.close() } }
    }

    private fun summary(uid: Long, m: Message): MailSummary {
        val from = m.from?.firstOrNull() as? InternetAddress
        return MailSummary(
            uid = uid,
            fromName = from?.personal?.takeIf { it.isNotBlank() } ?: from?.address.orEmpty(),
            fromAddress = from?.address.orEmpty(),
            subject = m.subject.orEmpty(),
            date = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
            seen = m.isSet(Flags.Flag.SEEN),
            answered = m.isSet(Flags.Flag.ANSWERED),
            kind = MailText.kind(
                from?.address.orEmpty(),
                m.getHeader("List-Unsubscribe")?.isNotEmpty() == true,
                m.getHeader("Auto-Submitted")?.firstOrNull(),
                m.getHeader("Precedence")?.firstOrNull(),
            ),
        )
    }

    private fun show(a: Address): String = (a as? InternetAddress)?.let { it.personal ?: it.address } ?: a.toString()

    /** Raccoglie testo, HTML e nomi degli allegati da un messaggio MIME. */
    private class Parts {
        val text = StringBuilder()
        val html = StringBuilder()
        val attachments = ArrayList<String>()

        fun walk(p: Part) {
            val disposition = p.disposition
            when {
                disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true) -> attachments += p.fileName ?: "allegato"
                p.isMimeType("text/plain") && text.isEmpty() -> text.append(p.content as? String ?: "")
                p.isMimeType("text/html") && html.isEmpty() -> html.append(p.content as? String ?: "")
                p.isMimeType("multipart/*") -> {
                    val mp = p.content as Multipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
                p.fileName != null -> attachments += p.fileName
            }
        }
    }
}

/** Le regole sul testo delle email, senza rete: le prova `MailTextTest`. */
object MailText {
    private val noReply = Regex("""^(no-?reply|do-?not-?reply|notifiche|notifications?|alert|avvisi|info|newsletter|news|mailer-daemon)[@+._-]""", RegexOption.IGNORE_CASE)

    /** Persona, avviso automatico o newsletter, dalle intestazioni (mai dal contenuto). */
    fun kind(from: String, unsubscribe: Boolean, autoSubmitted: String?, precedence: String?): MailKind = when {
        unsubscribe || precedence.equals("bulk", true) || precedence.equals("list", true) -> MailKind.Newsletter
        autoSubmitted != null && !autoSubmitted.equals("no", true) -> MailKind.Notice
        noReply.containsMatchIn(from) -> MailKind.Notice
        else -> MailKind.Person
    }

    /** Il testo leggibile di un HTML: niente stili, script e tag; righe vuote ridotte. */
    fun plain(html: String): String = html
        .replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), "")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>|</h[1-6]>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Indirizzi conosciuti: server IMAP e SMTP dei provider più usati, con quelli liberi ed europei per primi. */
    val providers: Map<String, Pair<MailServer, MailServer>> = mapOf(
        "murena.io" to (MailServer("mail.ecloud.global", 993, MailSecurity.Ssl) to MailServer("mail.ecloud.global", 587, MailSecurity.StartTls)),
        "e.email" to (MailServer("mail.ecloud.global", 993, MailSecurity.Ssl) to MailServer("mail.ecloud.global", 587, MailSecurity.StartTls)),
        "posteo.de" to (MailServer("posteo.de", 993, MailSecurity.Ssl) to MailServer("posteo.de", 465, MailSecurity.Ssl)),
        "mailbox.org" to (MailServer("imap.mailbox.org", 993, MailSecurity.Ssl) to MailServer("smtp.mailbox.org", 465, MailSecurity.Ssl)),
        "libero.it" to (MailServer("imapmail.libero.it", 993, MailSecurity.Ssl) to MailServer("smtp.libero.it", 465, MailSecurity.Ssl)),
        "virgilio.it" to (MailServer("in.virgilio.it", 993, MailSecurity.Ssl) to MailServer("out.virgilio.it", 465, MailSecurity.Ssl)),
        "tiscali.it" to (MailServer("imap.tiscali.it", 993, MailSecurity.Ssl) to MailServer("smtp.tiscali.it", 465, MailSecurity.Ssl)),
        "gmail.com" to (MailServer("imap.gmail.com", 993, MailSecurity.Ssl) to MailServer("smtp.gmail.com", 465, MailSecurity.Ssl)),
        "outlook.com" to (MailServer("outlook.office365.com", 993, MailSecurity.Ssl) to MailServer("smtp-mail.outlook.com", 587, MailSecurity.StartTls)),
    )
}
