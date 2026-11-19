package eu.eurise.core.ponte

import java.security.PublicKey
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

    /**
     * «Sono io». Dalla v2 porta anche la chiave pubblica effimera del telefono per questo collegamento ([ephemeral]): il
     * computer risponde con la sua nel primo messaggio (campo [EPHEMERAL], cifrato con la chiave fissa) e da lì si usa
     * [Link.session]. Un computer vecchio la ignora e si resta in v1.
     */
    class Hi(val deviceId: String, val ephemeral: ByteArray? = null) : Opening() {
        override fun equals(other: Any?) = other is Hi && deviceId == other.deviceId &&
            (ephemeral?.contentEquals(other.ephemeral ?: return false) ?: (other.ephemeral == null))
        override fun hashCode() = deviceId.hashCode()
        override fun toString() = "Hi($deviceId${if (ephemeral != null) ", v2" else ""})"
    }

    fun encode(): ByteArray = when (this) {
        is Pair -> Json.write(mapOf("t" to "pair", "id" to deviceId, "n" to name, "k" to B64.encodeToString(publicKey), "p" to B64.encodeToString(proof)))
        is Hi -> Json.write(mapOf("t" to "hi", "id" to deviceId) + (ephemeral?.let { mapOf(EPHEMERAL to B64.encodeToString(it)) } ?: emptyMap()))
    }.toByteArray(Charsets.UTF_8)

    companion object {
        /** Il campo con la chiave pubblica effimera (v2), nel saluto del telefono e nel primo messaggio del computer. */
        const val EPHEMERAL = "e"

        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val D64 = Base64.getUrlDecoder()

        fun encodeKey(key: ByteArray): String = B64.encodeToString(key)
        fun decodeKey(text: String?): ByteArray? = text?.let { runCatching { D64.decode(it) }.getOrNull() }

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
                    "hi" -> Hi(m.getValue("id"), m[EPHEMERAL]?.let { D64.decode(it) })
                    else -> null
                }
            }.getOrNull()
        }
    }
}

/**
 * Il passaggio alla chiave di sessione (v2) sulla rete di casa, uguale sui due lati (docs/SICUREZZA.md, «Ponte»).
 *
 * 1. Il telefono manda [Opening.Hi] con la sua chiave effimera.
 * 2. Il computer risponde con il primo messaggio cifrato con la chiave fissa e dentro la sua chiave effimera: così è
 *    autenticata da chi ha le chiavi fisse.
 * 3. Da lì in poi i due lati usano [Link.session]; le chiavi effimere non si salvano da nessuna parte.
 * Se uno dei due lati è vecchio, nessuno dei due trova la chiave dell'altro e si resta sulla chiave fissa (v1).
 */
object Upgrade {
    /** Lato telefono: dal primo messaggio del computer, il canale per il resto del collegamento. */
    fun phone(me: Identity, ephemeral: Identity, computer: PublicKey, secret: ByteArray, hello: Message, fallback: Channel, deviceId: String): Channel {
        val theirs = Opening.decodeKey(hello[Opening.EPHEMERAL]) ?: return fallback
        val theirKey = runCatching { Identity.publicKey(theirs) }.getOrNull() ?: return fallback
        return Channel(Link.session(me.keys.private, computer, ephemeral.keys.private, theirKey, secret, ephemeral.publicBytes, theirs), me = deviceId)
    }

    /** Vero se il computer ha risposto in v2 (la sua chiave effimera è nel primo messaggio). */
    fun spokeV2(hello: Message) = hello[Opening.EPHEMERAL] != null

    /**
     * Lato computer: la chiave effimera del computer da mettere nel primo messaggio e il canale per dopo, oppure null se
     * il telefono è vecchio (nessuna chiave effimera nel saluto).
     */
    fun computer(me: Identity, phone: PublicKey, secret: ByteArray, hi: Opening.Hi, computerId: String): Pair<ByteArray, Channel>? {
        val theirs = hi.ephemeral ?: return null
        val theirKey = runCatching { Identity.publicKey(theirs) }.getOrNull() ?: return null
        val ephemeral = Identity.generate()
        val link = Link.session(me.keys.private, phone, ephemeral.keys.private, theirKey, secret, theirs, ephemeral.publicBytes)
        return ephemeral.publicBytes to Channel(link, me = computerId)
    }
}
