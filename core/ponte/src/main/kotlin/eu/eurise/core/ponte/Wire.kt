package eu.eurise.core.ponte

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Le due strade del Ponte (docs/PIATTAFORME.md). Sulla rete di casa: cornici su TCP, ognuna lunga 4 byte e poi il
 * messaggio cifrato. Fuori casa: un file cifrato per messaggio in una cartella del cloud dell'utente (WebDAV), che
 * l'altro lato legge e cancella.
 */
object Wire {
    const val MAX_FRAME = 8 * 1024 * 1024
    /** Nome del servizio mDNS sulla rete di casa. */
    const val SERVICE = "_eurise-ponte._tcp"
    const val DEFAULT_PORT = 47_610

    fun writeFrame(out: OutputStream, sealed: ByteArray) {
        require(sealed.size <= MAX_FRAME) { "cornice troppo grande" }
        val d = DataOutputStream(out)
        d.writeInt(sealed.size)
        d.write(sealed)
        d.flush()
    }

    /** La prossima cornice; null a fine flusso. Una lunghezza fuori misura chiude il collegamento (IllegalStateException). */
    fun readFrame(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val size = try { d.readInt() } catch (e: EOFException) { return null }
        check(size in 1..MAX_FRAME) { "cornice non valida" }
        return ByteArray(size).also { d.readFully(it) }
    }

    /** Cartella e nome del file per un messaggio verso [to] nel cloud: ordinati per tempo, mai uguali. */
    fun mailboxPath(root: String, to: String, m: Message): String =
        root.trimEnd('/') + "/" + safe(to) + "/" + "%013d".format(m.time) + "-" + safe(m.id) + ".msg"

    /** Solo lettere, cifre, - e _: niente percorsi strani nei nomi dei file. */
    fun safe(s: String) = s.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64).ifEmpty { "x" }
}

/** Mette insieme cifratura e messaggi: ciò che serve a una delle due strade per mandare e ricevere. */
class Channel(private val link: Link, private val me: String, private val guard: ReplayGuard = ReplayGuard()) {
    /** Il messaggio cifrato; mittente nei dati autenticati, così non si può far finta di essere l'altro lato. */
    fun seal(m: Message): ByteArray = link.seal(m.encode().toByteArray(Charsets.UTF_8), me.toByteArray())

    /** Il messaggio di [from], se è integro, recente e non già visto. */
    fun open(sealed: ByteArray, from: String, now: Long = System.currentTimeMillis()): Message? {
        val plain = link.open(sealed, from.toByteArray()) ?: return null
        val m = Message.decode(String(plain, Charsets.UTF_8)) ?: return null
        return if (guard.accept(m, now)) m else null
    }
}
