package eu.eurise.password.logic

import android.net.Uri
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Parametri di un codice 2FA a tempo (RFC 6238). */
data class TotpConfig(val secret: ByteArray, val digits: Int = 6, val period: Int = 30, val algorithm: String = "HmacSHA1") {
    /** Il codice valido all'istante [millis]. */
    fun code(millis: Long): String {
        val counter = ByteBuffer.allocate(8).putLong(millis / 1000 / period).array()
        val hash = Mac.getInstance(algorithm).apply { init(SecretKeySpec(secret, "RAW")) }.doFinal(counter)
        val offset = hash.last().toInt() and 0x0F
        val value = ByteBuffer.wrap(hash, offset, 4).getInt() and 0x7FFFFFFF
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (value % modulus).toString().padStart(digits, '0')
    }

    /** Secondi che restano prima del cambio. */
    fun remaining(millis: Long): Int = period - ((millis / 1000) % period).toInt()

    override fun equals(other: Any?) = other is TotpConfig && secret.contentEquals(other.secret) && digits == other.digits && period == other.period && algorithm == other.algorithm
    override fun hashCode() = secret.contentHashCode() + 31 * digits + 961 * period
}

object Totp {
    /** Legge un indirizzo otpauth:// oppure un segreto in base32; null se non è valido. */
    fun parse(text: String?): TotpConfig? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (!value.startsWith("otpauth://", ignoreCase = true)) return base32(value)?.let { TotpConfig(it) }
        val query = value.substringAfter('?', "").split('&').associate {
            it.substringBefore('=').lowercase() to Uri.decode(it.substringAfter('=', ""))
        }
        val secret = base32(query["secret"].orEmpty()) ?: return null
        val algorithm = when (query["algorithm"]?.uppercase()) {
            null, "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> return null
        }
        return TotpConfig(secret, query["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6, query["period"]?.toIntOrNull()?.takeIf { it > 0 } ?: 30, algorithm)
    }

    /** "482913" diventa "482 913". */
    fun display(code: String): String = code.chunked(if (code.length == 8) 4 else 3).joinToString(" ")

    internal fun base32(text: String): ByteArray? {
        val clean = text.uppercase().filter { it != ' ' && it != '-' && it != '=' }
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
}
