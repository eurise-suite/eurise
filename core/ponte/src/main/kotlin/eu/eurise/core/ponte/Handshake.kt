package eu.eurise.core.ponte

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Le prime cornici di un collegamento, in chiaro ma senza nulla di segreto. Per abbinarsi il telefono manda la sua
 * chiave pubblica con la prova di aver letto il QR (HMAC con il segreto): chi non ha visto il codice non può abbinarsi.
 * Per i collegamenti dopo basta dire chi è; tutto ciò che segue è cifrato con la chiave comune ([Link]).
 */
sealed class Opening {
    data class Pair(val deviceId: String, val name: String, val publicKey: ByteArray, val proof: ByteArray) : Opening() {
        fun verify(secret: ByteArray) = java.security.MessageDigest.isEqual(proof, proofFor(publicKey, secret))
        override fun equals(other: Any?) = other is Pair && deviceId == other.deviceId && name == other.name &&
            publicKey.contentEquals(other.publicKey) && proof.contentEquals(other.proof)
        override fun hashCode() = deviceId.hashCode()
    }

    data class Hi(val deviceId: String) : Opening()

    fun encode(): ByteArray = when (this) {
        is Pair -> Json.write(mapOf("t" to "pair", "id" to deviceId, "n" to name, "k" to B64.encodeToString(publicKey), "p" to B64.encodeToString(proof)))
        is Hi -> Json.write(mapOf("t" to "hi", "id" to deviceId))
    }.toByteArray(Charsets.UTF_8)

    companion object {
        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val D64 = Base64.getUrlDecoder()

        fun pair(deviceId: String, name: String, publicKey: ByteArray, secret: ByteArray) =
            Pair(deviceId, name, publicKey, proofFor(publicKey, secret))

        fun proofFor(publicKey: ByteArray, secret: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            return mac.doFinal("eurise-ponte abbina".toByteArray() + publicKey)
        }

        fun decode(bytes: ByteArray): Opening? {
            val m = Json.read(String(bytes, Charsets.UTF_8)) ?: return null
            return runCatching {
                when (m["t"]) {
                    "pair" -> Pair(m.getValue("id"), m.getValue("n"), D64.decode(m.getValue("k")), D64.decode(m.getValue("p")))
                    "hi" -> Hi(m.getValue("id"))
                    else -> null
                }
            }.getOrNull()
        }
    }
}
