package eu.eurise.mail

import com.sun.mail.imap.IMAPFolder
import java.io.File
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
import javax.mail.internet.MimeUtility

/** Un messaggio nell'elenco (solo intestazioni: il testo si scarica quando lo apri). */
data class Summary(
    val accountId: String,
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val date: Long,
    val seen: Boolean,
    val kind: Kind,
    val answered: Boolean = false,
)

data class Attachment(val name: String, val size: Int, val index: Int)

/** Un messaggio aperto. [html] già pronto da mostrare; [text] per citare e cercare. */
data class Opened(
    val summary: Summary,
    val to: String,
    val cc: String,
    val html: String,
    val text: String,
    val attachments: List<Attachment>,
    val messageId: String?,
    val remoteImages: Int,
)

/**
 * IMAP per leggere e SMTP per inviare, con Jakarta Mail (android-mail). Ogni chiamata apre e chiude la sua
 * connessione: niente servizi sempre accesi (regola della leggerezza). Da chiamare fuori dal thread principale.
 */
class MailClient(private val account: Account) {
    private val password by lazy { Vault.open(account.secret) }

    private fun props(): Properties = Properties().apply {
        val i = account.settings.imap
        val s = account.settings.smtp
        val ip = if (i.security == Security.Ssl) "imaps" else "imap"
        put("mail.store.protocol", ip)
        put("mail.$ip.host", i.host)
        put("mail.$ip.port", i.port.toString())
        put("mail.$ip.connectiontimeout", "20000")
        put("mail.$ip.timeout", "30000")
        put("mail.$ip.ssl.checkserveridentity", "true")
        if (i.security == Security.StartTls) {
            put("mail.imap.starttls.enable", "true")
            put("mail.imap.starttls.required", "true")
        }
        put("mail.smtp.host", s.host)
        put("mail.smtp.port", s.port.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", "20000")
        put("mail.smtp.timeout", "30000")
        put("mail.smtp.ssl.checkserveridentity", "true")
        if (s.security == Security.Ssl) put("mail.smtp.ssl.enable", "true")
        else {
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
        }
    }

    private val session by lazy { Session.getInstance(props()) }

    private fun <T> store(block: (Store) -> T): T {
        val i = account.settings.imap
        val store = session.getStore(if (i.security == Security.Ssl) "imaps" else "imap")
        store.connect(i.host, i.port, account.user, password)
        try { return block(store) } finally { runCatching { store.close() } }
    }

    private fun <T> inbox(mode: Int, block: (IMAPFolder) -> T): T = store { s ->
        val f = s.getFolder("INBOX") as IMAPFolder
        f.open(mode)
        try { block(f) } finally { runCatching { f.close(false) } }
    }

    /** Prova l'accesso (collegamento di una casella): IMAP e SMTP. Lancia l'errore se non va. */
    fun check() {
        store { }
        val t = session.getTransport("smtp")
        t.connect(account.settings.smtp.host, account.settings.smtp.port, account.user, password)
        t.close()
    }

    /** Le ultime [count] della posta in arrivo, dalla più recente. */
    fun latest(count: Int = 60): List<Summary> = inbox(Folder.READ_ONLY) { f ->
        val total = f.messageCount
        if (total == 0) return@inbox emptyList()
        val msgs = f.getMessages(maxOf(1, total - count + 1), total)
        val fp = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
            add("List-Unsubscribe")
            add("List-Id")
            add("Precedence")
        }
        f.fetch(msgs, fp)
        msgs.map { m -> summary(f, m) }.sortedByDescending { it.date }
    }

    /** Il numero più alto tra quelli dei messaggi (per sapere se ne sono arrivati di nuovi). */
    fun lastUid(): Long = inbox(Folder.READ_ONLY) { f ->
        val total = f.messageCount
        if (total == 0) 0L else f.getUID(f.getMessage(total))
    }

    private fun summary(f: IMAPFolder, m: Message): Summary {
        val from = m.from?.firstOrNull() as? InternetAddress
        val address = from?.address.orEmpty()
        return Summary(
            accountId = account.id,
            uid = f.getUID(m),
            fromName = from?.personal.orEmpty().ifBlank { address },
            fromAddress = address,
            subject = m.subject.orEmpty(),
            date = (m.receivedDate ?: m.sentDate ?: Date()).time,
            seen = m.isSet(Flags.Flag.SEEN),
            kind = MailText.classify(
                address,
                listUnsubscribe = m.getHeader("List-Unsubscribe") != null,
                listId = m.getHeader("List-Id") != null,
                precedence = m.getHeader("Precedence")?.firstOrNull(),
            ),
            answered = m.isSet(Flags.Flag.ANSWERED),
        )
    }

    /** Apre un messaggio (lo segna come letto) con testo, HTML e allegati. */
    fun open(uid: Long): Opened? = inbox(Folder.READ_WRITE) { f ->
        val m = f.getMessageByUID(uid) ?: return@inbox null
        val parts = Parts()
        parts.walk(m)
        val html = parts.html ?: parts.text?.let(MailText::plainToHtml) ?: ""
        val text = parts.text ?: parts.html?.let(MailText::htmlToText) ?: ""
        m.setFlag(Flags.Flag.SEEN, true)
        Opened(
            summary = summary(f, m).copy(seen = true),
            to = addresses(m.getRecipients(Message.RecipientType.TO)),
            cc = addresses(m.getRecipients(Message.RecipientType.CC)),
            html = html, text = text,
            attachments = parts.attachments,
            messageId = (m as? MimeMessage)?.messageID,
            remoteImages = MailText.remoteImages(html),
        )
    }

    /** Salva l'allegato numero [index] in [dir]; restituisce il file. */
    fun attachment(uid: Long, index: Int, dir: File): File? = inbox(Folder.READ_ONLY) { f ->
        val m = f.getMessageByUID(uid) ?: return@inbox null
        val parts = Parts()
        parts.walk(m)
        val p = parts.attachmentParts.getOrNull(index) ?: return@inbox null
        val name = (decode(p.fileName) ?: "allegato").replace('/', '-')
        val out = File(dir.apply { mkdirs() }, name)
        p.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    /** Dove spostare un messaggio: le cartelle speciali si trovano dal loro ruolo, poi dal nome. */
    enum class Target(val attribute: String) { Archive("\\Archive"), Trash("\\Trash"), Junk("\\Junk") }

    /** Sposta nell'archivio, nel cestino o nella posta indesiderata. */
    fun move(uid: Long, target: Target): Boolean = store { s ->
        val names = when (target) { Target.Archive -> ARCHIVE_NAMES; Target.Trash -> TRASH_NAMES; Target.Junk -> JUNK_NAMES }
        val folder = special(s, target.attribute, names) ?: return@store false
        moveTo(s, uid, folder)
    }

    /** Sposta nella cartella [fullName] scelta dall'utente («Sposta in…»). */
    fun moveTo(uid: Long, fullName: String): Boolean = store { s -> moveTo(s, uid, s.getFolder(fullName)) }

    private fun moveTo(s: Store, uid: Long, target: Folder): Boolean {
        val inbox = s.getFolder("INBOX") as IMAPFolder
        inbox.open(Folder.READ_WRITE)
        try {
            val m = inbox.getMessageByUID(uid) ?: return false
            inbox.moveMessages(arrayOf(m), target)
            return true
        } finally { runCatching { inbox.close(true) } }
    }

    /** Le cartelle in cui si può spostare un messaggio (non la Posta in arrivo né quelle senza messaggi). */
    fun folders(): List<String> = store { s ->
        s.defaultFolder.list("*").filterIsInstance<IMAPFolder>()
            .filter { f -> (f.type and Folder.HOLDS_MESSAGES) != 0 && !f.fullName.equals("INBOX", ignoreCase = true) && f.attributes.none { it.equals("\\Noselect", true) } }
            .map { it.fullName }
            .sortedBy { it.lowercase() }
    }

    private fun special(s: Store, attribute: String, names: List<String>): Folder? {
        val all = s.defaultFolder.list("*").filterIsInstance<IMAPFolder>()
        return all.firstOrNull { f -> f.attributes.any { it.equals(attribute, ignoreCase = true) } }
            ?: all.firstOrNull { f -> names.any { n -> f.fullName.substringAfterLast('/').substringAfterLast('.').equals(n, ignoreCase = true) } }
    }

    /** Invia e mette una copia nella cartella degli inviati (se il server non lo fa da solo). */
    fun send(to: String, cc: String, subject: String, body: String, inReplyTo: String?, files: List<File> = emptyList()) {
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(account.email, account.name.ifBlank { null }, "UTF-8"))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false))
        if (cc.isNotBlank()) msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc, false))
        msg.setSubject(subject, "UTF-8")
        if (files.isEmpty()) msg.setText(body, "UTF-8")
        else {
            val mixed = javax.mail.internet.MimeMultipart()
            mixed.addBodyPart(javax.mail.internet.MimeBodyPart().apply { setText(body, "UTF-8") })
            files.forEach { f ->
                mixed.addBodyPart(javax.mail.internet.MimeBodyPart().apply {
                    attachFile(f)
                    fileName = MimeUtility.encodeText(f.name, "UTF-8", null)
                })
            }
            msg.setContent(mixed)
        }
        msg.sentDate = Date()
        if (!inReplyTo.isNullOrBlank()) {
            msg.setHeader("In-Reply-To", inReplyTo)
            msg.setHeader("References", inReplyTo)
        }
        msg.saveChanges()
        Transport.send(msg, account.user, password)
        runCatching {
            store { s ->
                val sent = special(s, "\\Sent", SENT_NAMES) ?: return@store
                sent.appendMessages(arrayOf(msg))
            }
        }
    }

    private fun addresses(list: Array<Address>?): String =
        list.orEmpty().joinToString(", ") { a -> (a as? InternetAddress)?.let { it.personal ?: it.address } ?: a.toString() }

    /** Le parti di un messaggio: il testo migliore e gli allegati. */
    private class Parts {
        var html: String? = null
        var text: String? = null
        val attachments = mutableListOf<Attachment>()
        val attachmentParts = mutableListOf<Part>()

        fun walk(p: Part) {
            val disposition = runCatching { p.disposition }.getOrNull()
            val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true) || (p.fileName != null && !p.isMimeType("multipart/*"))
            when {
                isAttachment -> {
                    attachments += Attachment(decode(p.fileName) ?: "allegato", p.size, attachmentParts.size)
                    attachmentParts += p
                }
                p.isMimeType("multipart/*") -> {
                    val mp = p.content as Multipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
                p.isMimeType("message/rfc822") -> (p.content as? Part)?.let { walk(it) }
                p.isMimeType("text/html") -> if (html == null) html = p.content as? String
                p.isMimeType("text/plain") -> if (text == null) text = p.content as? String
            }
        }
    }

    companion object {
        private val TRASH_NAMES = listOf("Trash", "Cestino", "Deleted", "Deleted Items", "Deleted Messages", "Posta eliminata", "Bin")
        private val ARCHIVE_NAMES = listOf("Archive", "Archivio", "Archives", "All Mail")
        private val JUNK_NAMES = listOf("Junk", "Spam", "Posta indesiderata", "Junk E-mail", "Junk Email", "Bulk Mail", "Indesiderata")
        private val SENT_NAMES = listOf("Sent", "Inviata", "Inviati", "Posta inviata", "Sent Items", "Sent Messages", "Elementi inviati")

        private fun decode(name: String?): String? = name?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
    }
}
