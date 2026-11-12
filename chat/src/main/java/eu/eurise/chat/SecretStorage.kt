package eu.eurise.chat

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * L'archivio dei segreti di Matrix («secret storage», m.secret_storage.v1.aes-hmac-sha2) e la chiave di recupero nel
 * formato di Element (tavola ChatRecupero). Con la chiave si cifrano, sul telefono, le chiavi di firma e la chiave della
 * copia delle chiavi sul server; il server conserva solo testo cifrato. La stessa chiave apre l'account in Element.
 * Solo Java standard: si prova con i test.
 */
object SecretStorage {
    const val ALGORITHM = "m.secret_storage.v1.aes-hmac-sha2"
    const val DEFAULT_KEY = "m.secret_storage.default_key"
    const val MASTER = "m.cross_signing.master"
    const val SELF_SIGNING = "m.cross_signing.self_signing"
    const val USER_SIGNING = "m.cross_signing.user_signing"
    const val BACKUP = "m.megolm_backup.v1"

    private val PREFIX = byteArrayOf(0x8B.toByte(), 0x01)
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    /** Un identificativo per la chiave nell'archivio (come fa Element: 32 lettere e cifre). */
    fun newKeyId(): String = (1..32).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** La chiave di recupero da mostrare: base58 di prefisso, chiave e parità, a gruppi di quattro. */
    fun encodeRecoveryKey(key: ByteArray): String {
        require(key.size == 32)
        val bytes = PREFIX + key
        var parity = 0
        bytes.forEach { parity = parity xor (it.toInt() and 0xFF) }
        return base58(bytes + parity.toByte()).chunked(4).joinToString(" ")
    }

    /** Dalla chiave scritta (spazi e a capo ammessi) ai 32 byte, o null se non è una chiave di recupero valida. */
    fun decodeRecoveryKey(text: String): ByteArray? {
        val clean = text.filterNot { it.isWhitespace() }
        if (clean.isEmpty() || clean.any { it !in ALPHABET }) return null
        val bytes = unbase58(clean) ?: return null
        if (bytes.size != PREFIX.size + 32 + 1) return null
        if (bytes[0] != PREFIX[0] || bytes[1] != PREFIX[1]) return null
        var parity = 0
        bytes.forEach { parity = parity xor (it.toInt() and 0xFF) }
        if (parity != 0) return null
        return bytes.copyOfRange(2, 34)
    }

    /** La descrizione della chiave per l'archivio: algoritmo e la prova (iv e mac di 32 zeri cifrati con nome vuoto). */
    fun keyDescription(key: ByteArray, name: String): JSONObject {
        val check = encrypt(key, "", ByteArray(32))
        return JSONObject().put("algorithm", ALGORITHM).put("name", name).put("iv", check.getString("iv")).put("mac", check.getString("mac"))
    }

    /** Vero se [key] è la chiave descritta da [description] (la prova torna). */
    fun checkKey(key: ByteArray, description: JSONObject): Boolean {
        if (description.optString("algorithm") != ALGORITHM) return false
        val iv = unb64(description.optString("iv")) ?: return false
        val mac = unb64(description.optString("mac")) ?: return false
        val check = encrypt(key, "", ByteArray(32), iv)
        return MessageDigest.isEqual(unb64(check.getString("mac")), mac)
    }

    /** Cifra un segreto (testo) con il suo nome: {iv, ciphertext, mac}. */
    fun encrypt(key: ByteArray, name: String, secret: String): JSONObject = encrypt(key, name, secret.toByteArray(Charsets.UTF_8))

    /** Il segreto in chiaro, o null se il mac non torna (chiave sbagliata o testo alterato). */
    fun decrypt(key: ByteArray, name: String, data: JSONObject): String? {
        val iv = unb64(data.optString("iv")) ?: return null
        val cipher = unb64(data.optString("ciphertext")) ?: return null
        val mac = unb64(data.optString("mac")) ?: return null
        val (aes, hmac) = derive(key, name)
        if (!MessageDigest.isEqual(hmacSha256(hmac, cipher), mac)) return null
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(iv))
        return String(c.doFinal(cipher), Charsets.UTF_8)
    }

    private fun encrypt(key: ByteArray, name: String, plain: ByteArray, givenIv: ByteArray? = null): JSONObject {
        val iv = givenIv ?: ByteArray(16).also(random::nextBytes).also { it[8] = (it[8].toInt() and 0x7F).toByte() }
        val (aes, hmac) = derive(key, name)
        val c = Cipher.getInstance("AES/CTR/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(iv))
        val cipher = c.doFinal(plain)
        return JSONObject().put("iv", b64(iv)).put("ciphertext", b64(cipher)).put("mac", b64(hmacSha256(hmac, cipher)))
    }

    /** HKDF-SHA256 con sale di 32 zeri e il nome del segreto come info: 32 byte per AES, 32 per HMAC. */
    private fun derive(key: ByteArray, name: String): Pair<ByteArray, ByteArray> {
        val prk = hmacSha256(ByteArray(32), key)
        val info = name.toByteArray(Charsets.UTF_8)
        val t1 = hmacSha256(prk, info + 1.toByte())
        val t2 = hmacSha256(prk, t1 + info + 2.toByte())
        return t1 to t2
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** Base64 standard come lo scrive Element (con il riempimento); in lettura si accetta anche senza. */
    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    fun unb64(s: String): ByteArray? = runCatching {
        val t = s.trim().replace('-', '+').replace('_', '/')
        Base64.getDecoder().decode(t + "=".repeat((4 - t.length % 4) % 4))
    }.getOrNull()

    private fun base58(input: ByteArray): String {
        var n = BigInteger(1, input)
        val base = BigInteger.valueOf(58)
        val sb = StringBuilder()
        while (n > BigInteger.ZERO) {
            val (q, r) = n.divideAndRemainder(base)
            sb.append(ALPHABET[r.toInt()])
            n = q
        }
        input.takeWhile { it.toInt() == 0 }.forEach { _ -> sb.append(ALPHABET[0]) }
        return sb.reverse().toString()
    }

    private fun unbase58(s: String): ByteArray? {
        var n = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (ch in s) {
            val d = ALPHABET.indexOf(ch)
            if (d < 0) return null
            n = n.multiply(base).add(BigInteger.valueOf(d.toLong()))
        }
        val raw = n.toByteArray().let { if (it.size > 1 && it[0].toInt() == 0) it.copyOfRange(1, it.size) else it }
        val zeros = s.takeWhile { it == ALPHABET[0] }.length
        return ByteArray(zeros) + raw
    }
}
