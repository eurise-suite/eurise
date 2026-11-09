package eu.eurise.password.passkey

import eu.eurise.password.autofill.Matching
import eu.eurise.password.kdbx.PasskeyData
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * La parte crittografica delle passkey (WebAuthn, ES256 con attestazione "none"). Le chiavi stanno nel file cifrato,
 * e non nel chip del telefono, perché devono viaggiare con la cassaforte come in KeePassXC.
 */
object WebAuthn {
    private const val FLAG_UP = 0x01
    private const val FLAG_UV = 0x04
    private const val FLAG_BE = 0x08
    private const val FLAG_BS = 0x10
    private const val FLAG_AT = 0x40
    private const val ES256 = -7L
    private val random = SecureRandom()

    fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Accetta anche il base64 normale, che alcuni programmi scrivono al posto di quello per gli indirizzi. */
    fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text.trim().replace('+', '-').replace('/', '_').trimEnd('='))

    private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)

    fun newPasskey(rpId: String, username: String, userHandle: String): PasskeyData {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----"
        return PasskeyData(b64(ByteArray(32).also { random.nextBytes(it) }), pem, rpId, username, userHandle)
    }

    private fun privateKey(pem: String): PrivateKey {
        val body = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("").trim()
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(body)))
    }

    /** Ricava la chiave pubblica moltiplicando il punto base per la chiave privata. */
    fun publicKeyOf(pem: String): PublicKey {
        val key = privateKey(pem) as ECPrivateKey
        val params = key.params
        val point = multiply(key.s, params.generator, params.curve.field.fieldSize, params)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }

    private fun multiply(k: BigInteger, g: java.security.spec.ECPoint, bits: Int, params: java.security.spec.ECParameterSpec): java.security.spec.ECPoint {
        val p = (params.curve.field as java.security.spec.ECFieldFp).p
        val a = params.curve.a
        fun add(s: java.security.spec.ECPoint, t: java.security.spec.ECPoint): java.security.spec.ECPoint {
            if (s == java.security.spec.ECPoint.POINT_INFINITY) return t
            if (t == java.security.spec.ECPoint.POINT_INFINITY) return s
            if (s.affineX == t.affineX && s.affineY.add(t.affineY).mod(p).signum() == 0) return java.security.spec.ECPoint.POINT_INFINITY
            val m = if (s == t) {
                s.affineX.pow(2).multiply(BigInteger.valueOf(3)).add(a).multiply(s.affineY.shiftLeft(1).modInverse(p)).mod(p)
            } else {
                t.affineY.subtract(s.affineY).multiply(t.affineX.subtract(s.affineX).modInverse(p)).mod(p)
            }
            val x = m.pow(2).subtract(s.affineX).subtract(t.affineX).mod(p)
            return java.security.spec.ECPoint(x, m.multiply(s.affineX.subtract(x)).subtract(s.affineY).mod(p))
        }
        var result = java.security.spec.ECPoint.POINT_INFINITY
        var addend = g
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) result = add(result, addend)
            addend = add(addend, addend)
        }
        check(bits > 0)
        return result
    }

    fun sign(pem: String, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(privateKey(pem)); update(data); sign() }

    /** Il contatore resta a zero: con una cassaforte su più dispositivi non può essere affidabile. */
    fun assertionAuthData(rpId: String): ByteArray =
        sha256(rpId.toByteArray()) + byteArrayOf((FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS).toByte()) + ByteArray(4)

    fun attestationAuthData(rpId: String, credentialId: ByteArray, publicKey: PublicKey): ByteArray {
        val flags = FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS or FLAG_AT
        val length = ByteBuffer.allocate(2).putShort(credentialId.size.toShort()).array()
        return sha256(rpId.toByteArray()) + byteArrayOf(flags.toByte()) + ByteArray(4) + ByteArray(16) + length + credentialId + cose(publicKey as ECPublicKey)
    }

    private fun fixed(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun cose(key: ECPublicKey): ByteArray {
        val head = byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20)
        return head + fixed(key.w.affineX) + byteArrayOf(0x22, 0x58, 0x20) + fixed(key.w.affineY)
    }

    /** CBOR scritto a mano: la struttura è una sola e sempre uguale. */
    fun attestationObject(authData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xA3)
        out.write(byteArrayOf(0x63) + "fmt".toByteArray() + byteArrayOf(0x64) + "none".toByteArray())
        out.write(byteArrayOf(0x67) + "attStmt".toByteArray() + byteArrayOf(0xA0.toByte()))
        out.write(byteArrayOf(0x68) + "authData".toByteArray())
        if (authData.size < 256) out.write(byteArrayOf(0x58, authData.size.toByte()))
        else out.write(byteArrayOf(0x59, (authData.size shr 8).toByte(), authData.size.toByte()))
        out.write(authData)
        return out.toByteArray()
    }

    fun clientData(type: String, challenge: String, origin: String): ByteArray =
        org.json.JSONObject().put("type", type).put("challenge", challenge).put("origin", origin).put("crossOrigin", false).toString().toByteArray()

    fun clientHash(clientData: ByteArray): ByteArray = sha256(clientData)

    /** L'identità del sito deve coincidere con la pagina o essere un suo dominio superiore, e solo su https. */
    fun rpAllowed(rpId: String, origin: String): Boolean =
        origin.startsWith("https://", ignoreCase = true) && Matching.domainMatches(rpId, origin)

    fun appOrigin(certificate: ByteArray): String =
        "android:apk-key-hash:" + b64(sha256(certificate))

    fun fingerprint(certificate: ByteArray): String =
        sha256(certificate).joinToString(":") { "%02X".format(it) }
}
