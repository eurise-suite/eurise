package eu.eurise.core.ponte

import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.util.Base64
import java.util.UUID

/**
 * Una chiave della cassetta: coppia EC P-256 che vive al massimo [MailboxKeys.KEEP_MS] e poi si distrugge. La parte
 * pubblica va all'altro lato sul canale autenticato (rete di casa), con [MailboxKeys.announce].
 */
class MailboxKey(val id: String, val keys: Identity, val created: Long)

/**
 * Le chiavi della cassetta di questo dispositivo: una nuova ogni [ROTATE_MS], le vecchie tenute per i messaggi ancora in
 * viaggio e distrutte dopo [KEEP_MS]. Chi conserva queste chiavi le sigilla come la chiave fissa (Keystore sul telefono,
 * portachiavi sul computer): [encode] dà anche le private.
 */
class MailboxKeys(keys: List<MailboxKey> = emptyList()) {
    private val keys = keys.sortedBy { it.created }.toMutableList()

    val all: List<MailboxKey> get() = keys.toList()

    /** La chiave da annunciare adesso; ne crea una nuova se l'ultima ha più di [ROTATE_MS]. */
    @Synchronized
    fun current(now: Long = System.currentTimeMillis()): MailboxKey {
        val last = keys.lastOrNull()
        if (last != null && now - last.created < ROTATE_MS && last.created <= now) return last
        return MailboxKey(UUID.randomUUID().toString().replace("-", "").take(16), Identity.generate(), now).also { keys += it }
    }

    @Synchronized
    fun find(id: String): MailboxKey? = keys.firstOrNull { it.id == id }

    /**
     * Distrugge le chiavi più vecchie di [KEEP_MS] (mai l'ultima). Da qui in poi i messaggi chiusi con loro non si aprono
     * più, nemmeno con le chiavi fisse e il segreto del QR: è la segretezza in avanti della cassetta. Vero se ha tolto qualcosa.
     */
    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()): Boolean {
        val keep = keys.lastOrNull()
        return keys.removeAll { it !== keep && now - it.created > KEEP_MS }
    }

    /** I campi da mettere in un messaggio sul canale autenticato (per esempio [Kind.Hello]) per dare all'altro la chiave. */
    fun announce(now: Long = System.currentTimeMillis()): Map<String, String> {
        val k = current(now)
        return mapOf(FIELD_ID to k.id, FIELD_KEY to B64.encodeToString(k.keys.publicBytes), FIELD_TIME to k.created.toString())
    }

    fun encode(): String = keys.joinToString(";") {
        listOf(it.id, B64.encodeToString(it.keys.publicBytes), B64.encodeToString(it.keys.privateBytes()), it.created.toString()).joinToString(",")
    }

    companion object {
        const val ROTATE_MS = 7L * 24 * 3600_000
        /** Quanto vive una chiave: quattro settimane. È il tetto di ciò che si legge rubando tutto in un dato giorno. */
        const val KEEP_MS = 28L * 24 * 3600_000
        /** Chi manda usa una chiave dell'altro solo se ha meno di tre settimane: resta una settimana di margine. */
        const val USABLE_MS = 21L * 24 * 3600_000

        const val FIELD_ID = "mk"
        const val FIELD_KEY = "mp"
        const val FIELD_TIME = "mt"

        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val D64 = Base64.getUrlDecoder()

        fun decode(text: String): MailboxKeys = MailboxKeys(text.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            runCatching {
                val (id, pub, priv, created) = row.split(',')
                MailboxKey(id, Identity.restore(D64.decode(pub), D64.decode(priv)), created.toLong())
            }.getOrNull()
        })
    }
}

/** La chiave della cassetta dell'altro lato, come l'ha annunciata ([MailboxKeys.announce]). */
class PeerMailboxKey(val id: String, val key: PublicKey, val created: Long) {
    fun usable(now: Long = System.currentTimeMillis()) = now - created in 0..MailboxKeys.USABLE_MS

    companion object {
        /** Dai campi di un messaggio ricevuto sul canale autenticato; null se mancano o non sono validi. */
        fun from(m: Message): PeerMailboxKey? = runCatching {
            val id = m[MailboxKeys.FIELD_ID]?.takeIf { it.length in 1..64 && it.all(Char::isLetterOrDigit) } ?: return null
            val key = Identity.publicKey(Base64.getUrlDecoder().decode(m[MailboxKeys.FIELD_KEY] ?: return null))
            PeerMailboxKey(id, key, (m[MailboxKeys.FIELD_TIME] ?: return null).toLong())
        }.getOrNull()
    }
}

/**
 * La cassetta nel cloud (file asincroni, senza sessione) con segretezza in avanti. Ogni file è chiuso con una chiave che
 * mescola:
 * - ECDH tra una chiave effimera di chi manda (buttata subito) e la chiave della cassetta di chi riceve ([MailboxKeys],
 *   distrutta dopo quattro settimane): chi un giorno ruba le chiavi fisse non apre i file più vecchi;
 * - ECDH tra le chiavi fisse dei due lati, con il segreto del QR come sale: solo il dispositivo abbinato può scrivere.
 * L'intestazione (versione, chiave della cassetta usata, chiave effimera) e il mittente sono nei dati autenticati.
 * Non esiste un ripiego sulla chiave fissa: senza una chiave della cassetta dell'altro, valida, non si manda.
 */
object Mailbox {
    private const val VERSION: Byte = 2
    private val INFO = "eurise-ponte cassetta v2".toByteArray()

    /** Il file per [recipientMailbox]; null se quella chiave è troppo vecchia per essere ancora aperta dall'altro. */
    fun seal(
        me: Identity,
        meId: String,
        recipient: PublicKey,
        secret: ByteArray,
        recipientMailbox: PeerMailboxKey,
        m: Message,
        now: Long = System.currentTimeMillis(),
    ): ByteArray? {
        if (!recipientMailbox.usable(now)) return null
        val ephemeral = Identity.generate()
        val header = header(recipientMailbox.id, ephemeral.publicBytes)
        val ikm = Link.dh(ephemeral.keys.private, recipientMailbox.key) + Link.dh(me.keys.private, recipient)
        val link = Link.derive(ikm, secret, INFO + header)
        return header + link.seal(m.encode().toByteArray(Charsets.UTF_8), meId.toByteArray() + header)
    }

    /**
     * Il messaggio di [fromId] dentro [file], se è integro e la chiave della cassetta esiste ancora. Le ripetizioni le
     * scarta chi legge (il file si cancella dopo la lettura, e un [ReplayGuard] lungo quanto [MailboxKeys.KEEP_MS]).
     */
    fun open(me: Identity, mine: MailboxKeys, from: PublicKey, fromId: String, secret: ByteArray, file: ByteArray): Message? = runCatching {
        if (file.size < 4 || file[0] != VERSION) return null
        val idLen = file[1].toInt() and 0xff
        val keyLen = ((file[2 + idLen].toInt() and 0xff) shl 8) or (file[3 + idLen].toInt() and 0xff)
        val headerLen = 4 + idLen + keyLen
        if (idLen == 0 || keyLen == 0 || file.size <= headerLen) return null
        val header = file.copyOfRange(0, headerLen)
        val mailbox = mine.find(String(file, 2, idLen, Charsets.US_ASCII)) ?: return null
        val ephemeral = Identity.publicKey(file.copyOfRange(4 + idLen, headerLen))
        val ikm = Link.dh(mailbox.keys.keys.private, ephemeral) + Link.dh(me.keys.private, from)
        val plain = Link.derive(ikm, secret, INFO + header).open(file.copyOfRange(headerLen, file.size), fromId.toByteArray() + header) ?: return null
        Message.decode(String(plain, Charsets.UTF_8))
    }.getOrNull()

    private fun header(mailboxId: String, ephemeral: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val id = mailboxId.toByteArray(Charsets.US_ASCII)
        require(id.size in 1..255 && ephemeral.size in 1..0xffff)
        write(VERSION.toInt()); write(id.size); write(id)
        write(ephemeral.size shr 8); write(ephemeral.size and 0xff); write(ephemeral)
    }.toByteArray()
}
