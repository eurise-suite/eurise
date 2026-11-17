package eu.eurise.chat

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Allegati cifrati di Matrix (specifica client-server, «Sending encrypted attachments», versione v2; come Element):
 * il file si cifra sul telefono con AES-256-CTR e una chiave nuova per ogni file, va sul server già cifrato, e chiave,
 * vettore iniziale e impronta SHA-256 viaggiano dentro il messaggio, che a sua volta è cifrato end-to-end. Il server
 * vede solo byte illeggibili. Senza Android: si prova con i test.
 */
object Attachments {
    private val random = SecureRandom()

    /** Il file cifrato e il blocco «file» del messaggio (manca solo «url», che arriva dopo il caricamento). */
    class Sealed(val bytes: ByteArray, val info: JSONObject)

    fun encrypt(plain: ByteArray): Sealed {
        val key = ByteArray(32).also(random::nextBytes)
        // 64 bit casuali e 64 a zero: il contatore non trabocca mai (lo chiede la specifica).
        val iv = ByteArray(16).also { random.nextBytes(it); for (i in 8 until 16) it[i] = 0 }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val out = cipher.doFinal(plain)
        val info = JSONObject()
            .put("v", "v2")
            .put("key", JSONObject().put("kty", "oct").put("key_ops", JSONArray().put("encrypt").put("decrypt"))
                .put("alg", "A256CTR").put("k", b64url(key)).put("ext", true))
            .put("iv", b64(iv))
            .put("hashes", JSONObject().put("sha256", b64(sha256(out))))
        return Sealed(out, info)
    }

    /**
     * Il file in chiaro da quello scaricato. Controlla prima l'impronta: se il server (o chiunque in mezzo) ha cambiato
     * anche un solo byte, non si apre. Errore con [IllegalArgumentException].
     */
    fun decrypt(cipherBytes: ByteArray, info: JSONObject): ByteArray {
        require(info.optString("v", "v2") == "v2") { "version" }
        val jwk = info.getJSONObject("key")
        require(jwk.optString("alg") == "A256CTR") { "alg" }
        val key = unb64(jwk.getString("k"))
        val iv = unb64(info.getString("iv"))
        require(key.size == 32 && iv.size == 16) { "key" }
        val expected = info.optJSONObject("hashes")?.optString("sha256").orEmpty()
        require(expected.isNotEmpty() && MessageDigest.isEqual(unb64(expected), sha256(cipherBytes))) { "hash" }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(cipherBytes)
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    /** Base64 senza «=» finali (come vuole Matrix); [b64url] con l'alfabeto per gli indirizzi (la chiave JWK). */
    fun b64(b: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(b)
    fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    /** Legge sia la forma normale sia quella per gli indirizzi, con o senza «=» (i client non sono tutti uguali). */
    fun unb64(s: String): ByteArray {
        val t = s.trim().trimEnd('=').replace('-', '+').replace('_', '/')
        return Base64.getDecoder().decode(t + "=".repeat((4 - t.length % 4) % 4))
    }
}
