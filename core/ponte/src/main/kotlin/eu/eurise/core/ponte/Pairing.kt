package eu.eurise.core.ponte

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

/**
 * Ciò che il computer mostra nel codice QR per collegarsi (tavola Ponte, «Collega un computer»): chi è, la sua chiave
 * pubblica, dove trovarlo sulla rete di casa e un segreto di una volta sola. Il telefono lo inquadra con il lettore QR
 * della suite e risponde con la sua chiave: da quel momento i due si riconoscono senza account né server.
 */
data class Pairing(
    val deviceId: String,
    val name: String,
    val publicKey: ByteArray,
    val secret: ByteArray,
    val host: String? = null,
    val port: Int? = null,
) {
    fun toUri(): String = buildString {
        append(SCHEME).append("?v=1")
        append("&i=").append(enc(deviceId))
        append("&n=").append(enc(name))
        append("&k=").append(B64.encodeToString(publicKey))
        append("&s=").append(B64.encodeToString(secret))
        if (host != null) append("&h=").append(enc(host))
        if (port != null) append("&p=").append(port)
    }

    override fun equals(other: Any?) = other is Pairing && deviceId == other.deviceId && name == other.name &&
        publicKey.contentEquals(other.publicKey) && secret.contentEquals(other.secret) && host == other.host && port == other.port

    override fun hashCode() = deviceId.hashCode() * 31 + publicKey.contentHashCode()

    companion object {
        const val SCHEME = "eurise-ponte://collega"
        private val B64 = Base64.getUrlEncoder().withoutPadding()
        private val D64 = Base64.getUrlDecoder()

        fun newSecret(random: SecureRandom = SecureRandom()) = ByteArray(16).also(random::nextBytes)

        /** Un identificativo casuale del dispositivo (non legato al telefono né all'utente). */
        fun newDeviceId(random: SecureRandom = SecureRandom()) = B64.encodeToString(ByteArray(9).also(random::nextBytes))

        /** Legge il testo del QR; null se non è un codice del Ponte o manca qualcosa. */
        fun parse(uri: String): Pairing? {
            if (!uri.startsWith("$SCHEME?")) return null
            val q = uri.substringAfter('?').split('&').mapNotNull { p ->
                val k = p.substringBefore('=', "")
                if (k.isEmpty()) null else k to p.substringAfter('=')
            }.toMap()
            if (q["v"] != "1") return null
            return runCatching {
                Pairing(
                    deviceId = dec(q.getValue("i")),
                    name = dec(q.getValue("n")),
                    publicKey = D64.decode(q.getValue("k")),
                    secret = D64.decode(q.getValue("s")).also { require(it.size == 16) },
                    host = q["h"]?.let(::dec),
                    port = q["p"]?.toInt()?.takeIf { it in 1..65535 },
                )
            }.getOrNull()
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
    }
}

/**
 * Un codice di sei cifre da confrontare a occhio sui due schermi dopo l'abbinamento (come con il Bluetooth): nasce
 * dalle due chiavi pubbliche e dal segreto, quindi chi si mettesse in mezzo avrebbe un codice diverso.
 */
fun confirmCode(a: ByteArray, b: ByteArray, secret: ByteArray): String {
    val (first, second) = if (compare(a, b) <= 0) a to b else b to a
    val d = Link.hkdf(first + second, secret, "eurise-ponte conferma".toByteArray(), 4)
    val n = ((d[0].toInt() and 0xff) shl 24 or (d[1].toInt() and 0xff shl 16) or (d[2].toInt() and 0xff shl 8) or (d[3].toInt() and 0xff)).toLong() and 0xffffffffL
    return "%06d".format(n % 1_000_000)
}

private fun compare(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
        if (d != 0) return d
    }
    return a.size - b.size
}
