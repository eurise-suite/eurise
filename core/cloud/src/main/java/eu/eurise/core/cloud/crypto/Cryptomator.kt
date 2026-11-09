package eu.eurise.core.cloud.crypto

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cartelle cifrate nel formato Cryptomator 8 (SIV_GCM), apribili anche con Cryptomator sul computer. Tutto avviene sul
 * telefono: al cloud arrivano solo nomi e contenuti cifrati. Senza librerie: scrypt, AES-SIV e AES Key Wrap sono qui
 * sotto, provati con i vettori delle RFC e, nella compilazione, con un altro programma.
 */
object Cryptomator {
    const val CONFIG_FILE = "vault.cryptomator"
    const val MASTERKEY_FILE = "masterkey.cryptomator"
    const val HEADER_SIZE = 68
    private const val CHUNK_PLAIN = 32 * 1024
    private const val CHUNK_OVERHEAD = 12 + 16
    private const val SCRYPT_N = 32768
    private const val SCRYPT_R = 8
    private const val VERSION = 999
    private val random = SecureRandom()
    private val url = Base64.getUrlEncoder().withoutPadding()

    /** I nomi dei file, a differenza del JWT, tengono il riempimento "=": senza, Cryptomator non li troverebbe. */
    private val nameEncoder = Base64.getUrlEncoder()
    private val urlDecoder = Base64.getUrlDecoder()
    private val std = Base64.getEncoder()
    private val stdDecoder = Base64.getDecoder()

    class WrongPassword : Exception("Frase sbagliata")
    class Unsupported(message: String) : Exception(message)
    class Corrupted(message: String) : Exception(message)

    class Created(val config: String, val masterkey: String, val vault: Vault)

    /** Nuove chiavi per una cartella. Lento di proposito: va chiamato fuori dal thread principale. */
    fun create(password: String): Created {
        val enc = bytes(32)
        val mac = bytes(32)
        val salt = bytes(8)
        val kek = kek(password, salt, SCRYPT_N, SCRYPT_R)
        val versionMac = hmac("HmacSHA256", mac, ByteBuffer.allocate(4).putInt(VERSION).array())
        val masterkey = """{"version":$VERSION,"scryptSalt":"${std.encodeToString(salt)}","scryptCostParam":$SCRYPT_N,"scryptBlockSize":$SCRYPT_R,""" +
            """"primaryMasterKey":"${std.encodeToString(aesWrap(kek, enc))}","hmacMasterKey":"${std.encodeToString(aesWrap(kek, mac))}","versionMac":"${std.encodeToString(versionMac)}"}"""
        val header = url.encodeToString("""{"kid":"masterkeyfile:$MASTERKEY_FILE","typ":"JWT","alg":"HS256"}""".toByteArray())
        val payload = url.encodeToString("""{"format":8,"shorteningThreshold":220,"jti":"${UUID.randomUUID()}","cipherCombo":"SIV_GCM"}""".toByteArray())
        val signature = url.encodeToString(hmac("HmacSHA256", enc + mac, "$header.$payload".toByteArray()))
        return Created("$header.$payload.$signature", masterkey, Vault(enc, mac, 220))
    }

    /** Apre una cartella dai suoi due file di configurazione. Lento di proposito: va chiamato fuori dal thread principale. */
    fun open(config: String, masterkey: String, password: String): Vault {
        val parts = config.trim().split('.')
        if (parts.size != 3) throw Corrupted("configurazione non valida")
        val header = String(urlDecoder.decode(parts[0]))
        val payload = String(urlDecoder.decode(parts[1]))
        val algorithm = when (field(header, "alg")) {
            "HS256" -> "HmacSHA256"
            "HS384" -> "HmacSHA384"
            "HS512" -> "HmacSHA512"
            else -> throw Unsupported("firma non supportata")
        }
        if (field(header, "kid")?.startsWith("masterkeyfile:") != true) throw Unsupported("chiave gestita altrove")
        if (field(payload, "format") != "8" || field(payload, "cipherCombo") != "SIV_GCM") throw Unsupported("formato non supportato")
        val salt = stdDecoder.decode(need(masterkey, "scryptSalt"))
        val kek = kek(password, salt, need(masterkey, "scryptCostParam").toInt(), need(masterkey, "scryptBlockSize").toInt())
        val enc = aesUnwrap(kek, stdDecoder.decode(need(masterkey, "primaryMasterKey")))
        val mac = aesUnwrap(kek, stdDecoder.decode(need(masterkey, "hmacMasterKey")))
        val expected = hmac(algorithm, enc + mac, "${parts[0]}.${parts[1]}".toByteArray())
        if (!MessageDigest.isEqual(expected, urlDecoder.decode(parts[2]))) throw Corrupted("firma non valida")
        return Vault(enc, mac, field(payload, "shorteningThreshold")?.toIntOrNull() ?: 220)
    }

    /** Dimensione in chiaro di un file cifrato di [size] byte. */
    fun cleartextSize(size: Long): Long {
        val body = size - HEADER_SIZE
        if (body <= 0) return 0
        val full = (CHUNK_PLAIN + CHUNK_OVERHEAD).toLong()
        val rest = body % full
        return body / full * CHUNK_PLAIN + (if (rest > CHUNK_OVERHEAD) rest - CHUNK_OVERHEAD else 0)
    }

    class Vault internal constructor(private val enc: ByteArray, private val mac: ByteArray, val threshold: Int) {

        /** "d/AB/CDE…": dove stanno i contenuti di una cartella. La radice ha l'identificativo vuoto. */
        fun dirPath(dirId: String): String {
            val hash = base32(MessageDigest.getInstance("SHA-1").digest(sivEncrypt(enc, mac, dirId.toByteArray())))
            return "d/${hash.substring(0, 2)}/${hash.substring(2)}"
        }

        fun encryptName(name: String, dirId: String): String =
            nameEncoder.encodeToString(sivEncrypt(enc, mac, nfc(name).toByteArray(), dirId.toByteArray())) + ".c9r"

        fun decryptName(stored: String, dirId: String): String {
            val data = try {
                urlDecoder.decode(stored.removeSuffix(".c9r"))
            } catch (e: IllegalArgumentException) {
                throw Corrupted("nome non valido")
            }
            return String(sivDecrypt(enc, mac, data, dirId.toByteArray()))
        }

        /** Il nome da usare sul server per un nome cifrato troppo lungo. */
        fun shortened(encrypted: String): String =
            nameEncoder.encodeToString(MessageDigest.getInstance("SHA-1").digest(encrypted.toByteArray())) + ".c9s"

        fun encrypt(input: InputStream, output: OutputStream) {
            val headerNonce = bytes(12)
            val contentKey = bytes(32)
            val payload = ByteArray(8) { 0xFF.toByte() } + contentKey
            output.write(headerNonce)
            output.write(gcm(Cipher.ENCRYPT_MODE, enc, headerNonce, null).doFinal(payload))
            val buffer = ByteArray(CHUNK_PLAIN)
            var number = 0L
            while (true) {
                val n = fill(input, buffer)
                if (n == 0) return
                val nonce = bytes(12)
                output.write(nonce)
                output.write(gcm(Cipher.ENCRYPT_MODE, contentKey, nonce, aad(number++, headerNonce)).doFinal(buffer, 0, n))
                if (n < buffer.size) return
            }
        }

        fun decrypt(input: InputStream, output: OutputStream) {
            val header = ByteArray(HEADER_SIZE)
            if (fill(input, header) != HEADER_SIZE) throw Corrupted("intestazione troncata")
            val headerNonce = header.copyOf(12)
            val payload = try {
                gcm(Cipher.DECRYPT_MODE, enc, headerNonce, null).doFinal(header, 12, HEADER_SIZE - 12)
            } catch (e: GeneralSecurityException) {
                throw Corrupted("intestazione non valida")
            }
            val contentKey = payload.copyOfRange(8, 40)
            val buffer = ByteArray(CHUNK_PLAIN + CHUNK_OVERHEAD)
            var number = 0L
            while (true) {
                val n = fill(input, buffer)
                if (n == 0) return
                if (n < CHUNK_OVERHEAD) throw Corrupted("blocco troncato")
                val cipher = gcm(Cipher.DECRYPT_MODE, contentKey, buffer.copyOf(12), aad(number++, headerNonce))
                try {
                    output.write(cipher.doFinal(buffer, 12, n - 12))
                } catch (e: GeneralSecurityException) {
                    throw Corrupted("blocco non valido")
                }
                if (n < buffer.size) return
            }
        }

        fun close() {
            enc.fill(0)
            mac.fill(0)
        }
    }

    private fun aad(number: Long, headerNonce: ByteArray) = ByteBuffer.allocate(20).putLong(number).put(headerNonce).array()

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray?): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            aad?.let { updateAAD(it) }
        }

    private fun nfc(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)

    private fun kek(password: String, salt: ByteArray, n: Int, r: Int) = scrypt(nfc(password).toByteArray(), salt, n, r, 32)

    private fun bytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    private fun hmac(algorithm: String, key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance(algorithm).run { init(SecretKeySpec(key, algorithm)); doFinal(data) }

    /** I due file di configurazione sono JSON piatti: basta questo, e la logica resta provabile senza Android. */
    private fun field(json: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"?([^\",}\\s]+)").find(json)?.groupValues?.get(1)

    private fun need(json: String, name: String) = field(json, name) ?: throw Corrupted("manca $name")

    private fun fill(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun base32(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    // AES Key Wrap (RFC 3394)

    private fun xorCounter(block: ByteArray, t: Long) {
        for (k in 0..7) block[7 - k] = (block[7 - k].toInt() xor ((t shr (8 * k)).toInt() and 0xFF)).toByte()
    }

    internal fun aesWrap(kek: ByteArray, plain: ByteArray): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES")) }
        val r = plain.copyOf()
        val n = plain.size / 8
        val block = ByteArray(16) { if (it < 8) 0xA6.toByte() else 0 }
        for (j in 0..5) for (i in 0 until n) {
            System.arraycopy(r, i * 8, block, 8, 8)
            val out = aes.doFinal(block)
            System.arraycopy(out, 0, block, 0, 8)
            xorCounter(block, (n * j + i + 1).toLong())
            System.arraycopy(out, 8, r, i * 8, 8)
        }
        return block.copyOf(8) + r
    }

    internal fun aesUnwrap(kek: ByteArray, wrapped: ByteArray): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES")) }
        val n = wrapped.size / 8 - 1
        val r = wrapped.copyOfRange(8, wrapped.size)
        val block = ByteArray(16)
        System.arraycopy(wrapped, 0, block, 0, 8)
        for (j in 5 downTo 0) for (i in n - 1 downTo 0) {
            xorCounter(block, (n * j + i + 1).toLong())
            System.arraycopy(r, i * 8, block, 8, 8)
            val out = aes.doFinal(block)
            System.arraycopy(out, 0, block, 0, 8)
            System.arraycopy(out, 8, r, i * 8, 8)
        }
        if ((0..7).any { block[it] != 0xA6.toByte() }) throw WrongPassword()
        return r
    }

    // AES-SIV (RFC 5297): S2V con la chiave MAC, CTR con quella di cifratura.

    internal fun sivEncrypt(ctrKey: ByteArray, macKey: ByteArray, plain: ByteArray, vararg ad: ByteArray): ByteArray {
        val iv = s2v(macKey, plain, ad)
        return iv + ctr(ctrKey, iv, plain)
    }

    internal fun sivDecrypt(ctrKey: ByteArray, macKey: ByteArray, data: ByteArray, vararg ad: ByteArray): ByteArray {
        if (data.size < 16) throw Corrupted("nome troncato")
        val iv = data.copyOf(16)
        val plain = ctr(ctrKey, iv, data.copyOfRange(16, data.size))
        if (!MessageDigest.isEqual(iv, s2v(macKey, plain, ad))) throw Corrupted("nome non autentico")
        return plain
    }

    private fun ctr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val q = iv.clone()
        q[8] = (q[8].toInt() and 0x7F).toByte()
        q[12] = (q[12].toInt() and 0x7F).toByte()
        return Cipher.getInstance("AES/CTR/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(q))
            doFinal(data)
        }
    }

    private fun s2v(key: ByteArray, plain: ByteArray, ad: Array<out ByteArray>): ByteArray {
        val aes = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val k1 = dbl(aes.doFinal(ByteArray(16)))
        val k2 = dbl(k1)
        var d = cmac(aes, k1, k2, ByteArray(16))
        for (s in ad) d = xor(dbl(d), cmac(aes, k1, k2, s))
        val t = if (plain.size >= 16) {
            plain.clone().also { for (i in 0 until 16) it[plain.size - 16 + i] = (it[plain.size - 16 + i].toInt() xor d[i].toInt()).toByte() }
        } else {
            xor(dbl(d), pad(plain))
        }
        return cmac(aes, k1, k2, t)
    }

    private fun cmac(aes: Cipher, k1: ByteArray, k2: ByteArray, data: ByteArray): ByteArray {
        val blocks = if (data.isEmpty()) 1 else (data.size + 15) / 16
        var x = ByteArray(16)
        for (i in 0 until blocks - 1) x = aes.doFinal(xor(x, data.copyOfRange(i * 16, i * 16 + 16)))
        val rest = data.copyOfRange((blocks - 1) * 16, data.size)
        val last = if (rest.size == 16) xor(rest, k1) else xor(pad(rest), k2)
        return aes.doFinal(xor(x, last))
    }

    private fun pad(data: ByteArray) = ByteArray(16).also { System.arraycopy(data, 0, it, 0, data.size); it[data.size] = 0x80.toByte() }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun dbl(a: ByteArray): ByteArray {
        val out = ByteArray(16)
        var carry = 0
        for (i in 15 downTo 0) {
            val v = a[i].toInt() and 0xFF
            out[i] = ((v shl 1) or carry).toByte()
            carry = v ushr 7
        }
        if (a[0].toInt() and 0x80 != 0) out[15] = (out[15].toInt() xor 0x87).toByte()
        return out
    }

    // scrypt (RFC 7914) con p = 1

    internal fun scrypt(password: ByteArray, salt: ByteArray, n: Int, r: Int, length: Int): ByteArray {
        val blockInts = 32 * r
        val first = pbkdf2(password, salt, 128 * r)
        val x = IntArray(blockInts)
        ByteBuffer.wrap(first).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(x)
        val v = IntArray(blockInts * n)
        val y = IntArray(blockInts)
        for (i in 0 until n) {
            System.arraycopy(x, 0, v, i * blockInts, blockInts)
            blockMix(x, y, r)
        }
        for (i in 0 until n) {
            val j = x[(2 * r - 1) * 16] and (n - 1)
            for (k in 0 until blockInts) x[k] = x[k] xor v[j * blockInts + k]
            blockMix(x, y, r)
        }
        val out = ByteBuffer.allocate(128 * r).order(ByteOrder.LITTLE_ENDIAN)
        out.asIntBuffer().put(x)
        v.fill(0)
        return pbkdf2(password, out.array(), length)
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        val out = ByteArrayOutputStream()
        var block = 1
        while (out.size() < length) {
            mac.update(salt)
            out.write(mac.doFinal(ByteBuffer.allocate(4).putInt(block++).array()))
        }
        return out.toByteArray().copyOf(length)
    }

    private fun blockMix(b: IntArray, y: IntArray, r: Int) {
        val x = IntArray(16)
        System.arraycopy(b, (2 * r - 1) * 16, x, 0, 16)
        for (i in 0 until 2 * r) {
            for (k in 0 until 16) x[k] = x[k] xor b[i * 16 + k]
            salsa8(x)
            val target = if (i % 2 == 0) i / 2 else r + i / 2
            System.arraycopy(x, 0, y, target * 16, 16)
        }
        System.arraycopy(y, 0, b, 0, 32 * r)
    }

    private fun rotl(v: Int, c: Int) = (v shl c) or (v ushr (32 - c))

    private fun salsa8(b: IntArray) {
        val x = b.clone()
        repeat(4) {
            x[4] = x[4] xor rotl(x[0] + x[12], 7); x[8] = x[8] xor rotl(x[4] + x[0], 9)
            x[12] = x[12] xor rotl(x[8] + x[4], 13); x[0] = x[0] xor rotl(x[12] + x[8], 18)
            x[9] = x[9] xor rotl(x[5] + x[1], 7); x[13] = x[13] xor rotl(x[9] + x[5], 9)
            x[1] = x[1] xor rotl(x[13] + x[9], 13); x[5] = x[5] xor rotl(x[1] + x[13], 18)
            x[14] = x[14] xor rotl(x[10] + x[6], 7); x[2] = x[2] xor rotl(x[14] + x[10], 9)
            x[6] = x[6] xor rotl(x[2] + x[14], 13); x[10] = x[10] xor rotl(x[6] + x[2], 18)
            x[3] = x[3] xor rotl(x[15] + x[11], 7); x[7] = x[7] xor rotl(x[3] + x[15], 9)
            x[11] = x[11] xor rotl(x[7] + x[3], 13); x[15] = x[15] xor rotl(x[11] + x[7], 18)
            x[1] = x[1] xor rotl(x[0] + x[3], 7); x[2] = x[2] xor rotl(x[1] + x[0], 9)
            x[3] = x[3] xor rotl(x[2] + x[1], 13); x[0] = x[0] xor rotl(x[3] + x[2], 18)
            x[6] = x[6] xor rotl(x[5] + x[4], 7); x[7] = x[7] xor rotl(x[6] + x[5], 9)
            x[4] = x[4] xor rotl(x[7] + x[6], 13); x[5] = x[5] xor rotl(x[4] + x[7], 18)
            x[11] = x[11] xor rotl(x[10] + x[9], 7); x[8] = x[8] xor rotl(x[11] + x[10], 9)
            x[9] = x[9] xor rotl(x[8] + x[11], 13); x[10] = x[10] xor rotl(x[9] + x[8], 18)
            x[12] = x[12] xor rotl(x[15] + x[14], 7); x[13] = x[13] xor rotl(x[12] + x[15], 9)
            x[14] = x[14] xor rotl(x[13] + x[12], 13); x[15] = x[15] xor rotl(x[14] + x[13], 18)
        }
        for (i in 0 until 16) b[i] += x[i]
    }
}
