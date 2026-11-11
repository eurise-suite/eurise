package eu.eurise.core.ponte

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
 * L'identità di un dispositivo del Ponte: una coppia di chiavi EC P-256 che resta sul dispositivo (il telefono la tiene
 * nella memoria dell'app, il computer nella sua cartella dei dati). Si condivide solo la chiave pubblica, nel QR.
 */
class Identity(val keys: KeyPair) {
    val publicBytes: ByteArray get() = keys.public.encoded

    fun privateBytes(): ByteArray = keys.private.encoded

    companion object {
        fun generate(random: SecureRandom = SecureRandom()): Identity {
            val g = KeyPairGenerator.getInstance("EC")
            g.initialize(ECGenParameterSpec("secp256r1"), random)
            return Identity(g.generateKeyPair())
        }

        fun restore(publicKey: ByteArray, privateKey: ByteArray): Identity {
            val f = KeyFactory.getInstance("EC")
            return Identity(KeyPair(f.generatePublic(X509EncodedKeySpec(publicKey)), f.generatePrivate(PKCS8EncodedKeySpec(privateKey))))
        }

        fun publicKey(bytes: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }
}

/**
 * La chiave comune di due dispositivi abbinati: ECDH tra le due coppie, poi HKDF-SHA256 con il segreto dell'abbinamento
 * (16 byte casuali nel QR) come sale. Uguale sui due lati senza che passi mai in rete.
 */
class Link private constructor(private val key: SecretKeySpec) {

    /** Cifra [plain] con AES-256-GCM: 12 byte di nonce casuale, poi il testo cifrato con l'etichetta. [aad] si autentica. */
    fun seal(plain: ByteArray, aad: ByteArray = ByteArray(0), random: SecureRandom = SecureRandom()): ByteArray {
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    /** Il contrario di [seal]; null se il messaggio è stato toccato o non è per questo collegamento. */
    fun open(sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        if (sealed.size < NONCE + TAG_BITS / 8) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, NONCE)))
            if (aad.isNotEmpty()) c.updateAAD(aad)
            c.doFinal(sealed, NONCE, sealed.size - NONCE)
        }.getOrNull()
    }

    companion object {
        private const val NONCE = 12
        private const val TAG_BITS = 128
        private val INFO = "eurise-ponte v1".toByteArray()

        fun between(mine: PrivateKey, theirs: PublicKey, pairingSecret: ByteArray): Link {
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(mine)
            ka.doPhase(theirs, true)
            return Link(SecretKeySpec(hkdf(ka.generateSecret(), pairingSecret, INFO, 32), "AES"))
        }

        /** HKDF (RFC 5869) con HMAC-SHA256. */
        fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = java.io.ByteArrayOutputStream()
            var t = ByteArray(0)
            var i = 1
            while (out.size() < length) {
                mac.update(t); mac.update(info); mac.update(i.toByte())
                t = mac.doFinal()
                out.write(t)
                i++
            }
            return out.toByteArray().copyOf(length)
        }
    }
}
