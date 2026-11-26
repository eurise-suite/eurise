package eu.eurise.core.suite

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Foto private (26 novembre 2026): la Fotocamera cifra la foto per la cartella protetta della Galleria **senza aprirla**.
 * La Galleria ha una coppia di chiavi: la pubblica la dà a chi la chiede tra le app della suite, la privata è chiusa con la
 * chiave della cartella (quindi si usa solo a cartella aperta). La Fotocamera fa una «busta» per ogni foto: chiave di un
 * solo uso (ECDH P-256, che c'è da Android 11; X25519 solo da Android 13), HKDF-SHA256, AES-256-GCM. Nella busta stanno
 * il nome, la data e la foto. Chi ha la busta senza la cartella aperta non legge niente, nemmeno la Fotocamera dopo.
 *
 * Il passaggio: la Fotocamera scrive la busta in `content://<galleria>.hub/inbox/<uuid>` ([openFile] della Galleria,
 * solo app della suite) e poi chiama [METHOD_COMMIT] con l'uuid. All'apertura della cartella la Galleria apre le buste,
 * ricifra ogni foto con la chiave della cartella, verifica e cancella la busta.
 */
object PrivateInbox {
    /** Chiede la chiave pubblica: risponde con [KEY_PUBLIC] (X.509, Base64) o senza, se la cartella non è pronta. */
    const val METHOD_KEY = "private_key"
    /** Conferma la busta scritta in inbox/<arg>. */
    const val METHOD_COMMIT = "private_commit"
    const val KEY_PUBLIC = "public"
    const val KEY_OK = "ok"
    const val PATH = "inbox"

    private const val MAGIC = 0x45 // «E»
    private const val VERSION = 1
    private val INFO = "eurise-private-v1".toByteArray()

    /** Nome, data e tipo della foto, dentro la busta. */
    data class Item(val name: String, val takenAt: Long, val mime: String, val bytes: ByteArray)

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun publicKey(x509: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(x509))
    fun privateKey(pkcs8: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    /** Un uuid e basta: niente barre né punti, così il nome non può uscire dalla cartella delle buste. */
    fun validId(id: String?): Boolean = id != null && Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(id)

    /** La busta per la chiave pubblica [recipient]. */
    fun seal(recipient: PublicKey, item: Item): ByteArray {
        val ephemeral = newKeyPair()
        val epk = ephemeral.public.encoded
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = ByteArrayOutputStream().also { o ->
            DataOutputStream(o).apply { writeByte(MAGIC); writeByte(VERSION); writeShort(epk.size); write(epk); write(iv) }
        }.toByteArray()
        val key = derive(ephemeral.private, recipient, epk)
        val body = ByteArrayOutputStream().also { o ->
            DataOutputStream(o).apply { writeUTF(item.name.take(200)); writeLong(item.takenAt); writeUTF(item.mime.take(100)); write(item.bytes) }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(header)
        }
        key.fill(0)
        return header + cipher.doFinal(body)
    }

    /** Apre la busta con la chiave privata; un errore se è stata toccata o non è per questa chiave. */
    fun open(own: PrivateKey, sealed: ByteArray): Item {
        val input = DataInputStream(sealed.inputStream())
        require(input.readUnsignedByte() == MAGIC && input.readUnsignedByte() == VERSION) { "busta sconosciuta" }
        val n = input.readUnsignedShort()
        require(n in 1..512) { "busta rovinata" }
        val epk = ByteArray(n).also { input.readFully(it) }
        val iv = ByteArray(12).also { input.readFully(it) }
        val headerSize = 4 + n + 12
        val key = derive(own, publicKey(epk), epk)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(sealed, 0, headerSize)
        }
        key.fill(0)
        val body = cipher.doFinal(sealed, headerSize, sealed.size - headerSize)
        val stream = body.inputStream()
        val data = DataInputStream(stream)
        val name = data.readUTF(); val taken = data.readLong(); val mime = data.readUTF()
        val bytes = stream.readBytes()
        body.fill(0)
        return Item(name, taken, mime, bytes)
    }

    private fun derive(own: PrivateKey, other: PublicKey, salt: ByteArray): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").apply { init(own); doPhase(other, true) }.generateSecret()
        // HKDF-SHA256 (RFC 5869): estrai e espandi un blocco, 32 byte per AES-256.
        val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(shared)
        shared.fill(0)
        val okm = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }.doFinal(INFO + byteArrayOf(1))
        prk.fill(0)
        return okm
    }
}
