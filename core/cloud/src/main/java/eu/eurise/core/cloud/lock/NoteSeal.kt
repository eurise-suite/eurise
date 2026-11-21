package eu.eurise.core.cloud.lock

import eu.eurise.core.cloud.backup.BackupCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * La copia della chiave delle note per il backup, che il telefono può scrivere ma non leggere (21 novembre 2026).
 *
 * Sul telefono la chiave delle note si apre solo con l'impronta o il PIN ([LockKeys]); il backup automatico però parte da
 * solo, senza nessuno davanti. Per questo la copia si chiude verso una chiave pubblica EC P-256 ([Pair]): la parte privata
 * esiste sul telefono solo chiusa con la frase segreta del backup e con il codice di recupero (PBKDF2, come
 * [BackupCrypto]). Chi forza il telefono trova la copia e la chiave pubblica, non la frase: non apre le note.
 *
 * La copia è autosufficiente ([seal]): porta con sé la chiave pubblica e le due chiusure della privata, così si apre con
 * la frase o il codice validi quando è stata scritta, anche su un telefono nuovo («Riapri le note bloccate», ripristino).
 */
object NoteSeal {
    private const val MAGIC = "EURISEN1"
    private const val VERSION = 1
    private val INFO = "eurise note seal v1".toByteArray()

    /** La coppia della copia: chiave pubblica, e la privata chiusa con la frase e con il codice di recupero. */
    class Pair(
        val publicKey: ByteArray,
        val iterations: Int,
        val saltPass: ByteArray,
        val wrapPass: ByteArray,
        val saltCode: ByteArray,
        val wrapCode: ByteArray,
    )

    /** Una coppia nuova: alla prima attivazione del backup, quando ci sono sia la frase sia il codice. Lento di proposito. */
    fun newPair(passphrase: CharArray, recoveryCode: String, iterations: Int = BackupCrypto.ITERATIONS): Pair {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val priv = kp.private.encoded
        val saltPass = BackupCrypto.bytes(16)
        val saltCode = BackupCrypto.bytes(16)
        return Pair(
            kp.public.encoded, iterations,
            saltPass, BackupCrypto.wrap(BackupCrypto.derive(passphrase, saltPass, iterations), priv),
            saltCode, BackupCrypto.wrap(BackupCrypto.derive(BackupCrypto.normalizeCode(recoveryCode).toCharArray(), saltCode, iterations), priv),
        )
    }

    /** La stessa coppia con la frase nuova; [current] è la frase attuale o il codice. Null se [current] non la apre. */
    fun rewrapPassphrase(pair: Pair, current: String, next: CharArray): Pair? {
        val priv = privateBytes(pair, current) ?: return null
        val salt = BackupCrypto.bytes(16)
        return Pair(pair.publicKey, pair.iterations, salt, BackupCrypto.wrap(BackupCrypto.derive(next, salt, pair.iterations), priv), pair.saltCode, pair.wrapCode)
    }

    /** Vero se [secret] (frase o codice) apre la coppia. */
    fun opens(pair: Pair, secret: String): Boolean = privateBytes(pair, secret) != null

    /** Chiude [noteKey] verso la coppia: lo può fare il backup automatico, senza frase. */
    fun seal(pair: Pair, noteKey: ByteArray): ByteArray {
        val eph = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pair.publicKey))
        val shared = KeyAgreement.getInstance("ECDH").apply { init(eph.private); doPhase(pub, true) }.generateSecret()
        val ephPub = eph.public.encoded
        val key = hkdf(shared, ephPub + pair.publicKey)
        val nonce = BackupCrypto.bytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
        val ct = nonce + c.doFinal(noteKey)
        return encode(pair, ephPub, ct)
    }

    /** La chiave delle note dentro [entry], con la frase o il codice; null se il segreto è sbagliato o il file rovinato. */
    fun open(entry: ByteArray, secret: String): ByteArray? = runCatching {
        val (pair, ephPub, ct) = decode(entry)
        val priv = privateBytes(pair, secret) ?: return null
        val privKey: PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(priv))
        val eph = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(ephPub))
        val shared = KeyAgreement.getInstance("ECDH").apply { init(privKey); doPhase(eph, true) }.generateSecret()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(hkdf(shared, ephPub + pair.publicKey), "AES"), GCMParameterSpec(128, ct.copyOfRange(0, 12)))
        c.updateAAD(MAGIC.toByteArray(Charsets.US_ASCII))
        c.doFinal(ct, 12, ct.size - 12)
    }.getOrNull()

    /**
     * La stessa copia con le chiusure di [pair] (frase nuova): la parte cifrata dipende solo dalla chiave pubblica, che
     * non cambia, quindi non serve la chiave delle note. Null se [pair] ha un'altra chiave pubblica o la copia è rovinata.
     */
    fun rewrapCopy(entry: ByteArray, pair: Pair): ByteArray? = runCatching {
        val (old, ephPub, ct) = decode(entry)
        if (!old.publicKey.contentEquals(pair.publicKey)) return null
        encode(pair, ephPub, ct)
    }.getOrNull()

    /** La coppia con cui è stata chiusa [entry] (per tenerla sul telefono dopo un ripristino); null se rovinata. */
    fun pairOf(entry: ByteArray): Pair? = runCatching { decode(entry).first }.getOrNull()

    fun encodePair(pair: Pair): ByteArray = encode(pair, ByteArray(0), ByteArray(0))

    fun decodePair(bytes: ByteArray): Pair? = pairOf(bytes)

    private fun privateBytes(pair: Pair, secret: String): ByteArray? {
        runCatching { return BackupCrypto.unwrap(BackupCrypto.derive(secret.toCharArray(), pair.saltPass, pair.iterations), pair.wrapPass) }
        val code = BackupCrypto.normalizeCode(secret)
        if (code.length == 24) runCatching { return BackupCrypto.unwrap(BackupCrypto.derive(code.toCharArray(), pair.saltCode, pair.iterations), pair.wrapCode) }
        return null
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal(INFO + byteArrayOf(1)).copyOf(32)
    }

    private fun encode(pair: Pair, ephPub: ByteArray, ct: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            writeByte(VERSION)
            writeInt(pair.iterations)
            listOf(pair.publicKey, pair.saltPass, pair.wrapPass, pair.saltCode, pair.wrapCode, ephPub, ct).forEach { writeShort(it.size); write(it) }
            flush()
        }
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Triple<Pair, ByteArray, ByteArray> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(8).also { input.readFully(it) }
        require(String(magic, Charsets.US_ASCII) == MAGIC && input.readByte().toInt() == VERSION)
        val iterations = input.readInt()
        require(iterations in 1_000..5_000_000)
        fun next(): ByteArray { val n = input.readUnsignedShort(); require(n <= 2048); return ByteArray(n).also { input.readFully(it) } }
        val pair = Pair(next(), iterations, next(), next(), next(), next())
        return Triple(pair, next(), next())
    }
}
