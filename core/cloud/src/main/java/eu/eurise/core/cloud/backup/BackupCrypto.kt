package eu.eurise.core.cloud.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifratura dei backup, tutta sul telefono.
 *
 * Ogni backup è cifrato con una chiave dei dati casuale (AES-256-GCM). Questa chiave viaggia nel file chiusa due
 * volte: con una chiave ricavata dalla frase segreta dell'utente e con una ricavata dal codice di recupero
 * (PBKDF2-HMAC-SHA256). Senza l'una o l'altro il file non si apre: né il cloud né noi possiamo leggerlo.
 *
 * Formato: "EURISEB1", versione, data, iterazioni, sale e chiave chiusa per la frase, sale e chiave chiusa per il
 * codice, nonce, poi i dati cifrati; l'intestazione è autenticata insieme ai dati (AAD).
 */
object BackupCrypto {
    private const val MAGIC = "EURISEB1"
    private const val VERSION = 1
    const val ITERATIONS = 310_000
    private const val GCM_TAG = 128
    private val random = SecureRandom()

    /** Lettere e cifre senza quelle che si confondono (0/O, 1/I/L): 31 simboli. */
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Come si apre la chiave dei dati: per la frase segreta e per il codice di recupero. */
    class KeyHeader(
        val iterations: Int,
        val saltPass: ByteArray,
        val wrapPass: ByteArray,
        val saltCode: ByteArray,
        val wrapCode: ByteArray,
    )

    class Keys(val dataKey: ByteArray, val header: KeyHeader)

    /** Intestazione letta da un file, con la data del backup. */
    class Parsed(val header: KeyHeader, val createdAt: Long, internal val nonce: ByteArray, internal val aad: ByteArray, internal val body: ByteArray)

    class WrongSecret : Exception("Frase o codice non corretti")
    class BadFile : Exception("Il file non è un backup di Eurise o è rovinato")

    /** Codice di recupero: 24 simboli (oltre 110 bit), da scrivere a gruppi di quattro. */
    fun newRecoveryCode(): String = (1..24).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
        .chunked(4).joinToString("-") { it.joinToString("") }

    fun normalizeCode(code: String): String = code.uppercase().filter { it in CODE_ALPHABET }

    /** Nuove chiavi per una frase segreta e un codice di recupero. Lento di proposito (qualche secondo). */
    fun setup(passphrase: CharArray, recoveryCode: String, iterations: Int = ITERATIONS): Keys {
        val dataKey = bytes(32)
        val saltPass = bytes(16)
        val saltCode = bytes(16)
        val wrapPass = wrap(derive(passphrase, saltPass, iterations), dataKey)
        val wrapCode = wrap(derive(normalizeCode(recoveryCode).toCharArray(), saltCode, iterations), dataKey)
        return Keys(dataKey, KeyHeader(iterations, saltPass, wrapPass, saltCode, wrapCode))
    }

    /** Chiude di nuovo [dataKey] con una frase nuova, lasciando com'è la chiusura per il codice di recupero. */
    fun rewrapPassphrase(dataKey: ByteArray, passphrase: CharArray, old: KeyHeader): KeyHeader {
        val salt = bytes(16)
        return KeyHeader(old.iterations, salt, wrap(derive(passphrase, salt, old.iterations), dataKey), old.saltCode, old.wrapCode)
    }

    /** Apre la chiave dei dati con la frase segreta oppure con il codice di recupero. */
    fun unlock(header: KeyHeader, secret: String): ByteArray {
        runCatching { return unwrap(derive(secret.toCharArray(), header.saltPass, header.iterations), header.wrapPass) }
        val code = normalizeCode(secret)
        if (code.length == 24) runCatching { return unwrap(derive(code.toCharArray(), header.saltCode, header.iterations), header.wrapCode) }
        throw WrongSecret()
    }

    fun encrypt(keys: Keys, createdAt: Long, plain: ByteArray): ByteArray {
        val nonce = bytes(12)
        val aad = headerBytes(keys.header, createdAt, nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.dataKey, "AES"), GCMParameterSpec(GCM_TAG, nonce))
        cipher.updateAAD(aad)
        return aad + cipher.doFinal(plain)
    }

    fun parse(file: ByteArray): Parsed {
        try {
            val input = DataInputStream(ByteArrayInputStream(file))
            val magic = ByteArray(8).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw BadFile()
            if (input.readByte().toInt() != VERSION) throw BadFile()
            val createdAt = input.readLong()
            val iterations = input.readInt()
            val saltPass = read(input)
            val wrapPass = read(input)
            val saltCode = read(input)
            val wrapCode = read(input)
            val nonce = read(input)
            val headerLength = file.size - input.available()
            return Parsed(KeyHeader(iterations, saltPass, wrapPass, saltCode, wrapCode), createdAt, nonce, file.copyOfRange(0, headerLength), file.copyOfRange(headerLength, file.size))
        } catch (e: BadFile) {
            throw e
        } catch (e: Exception) {
            throw BadFile()
        }
    }

    /** Decifra e verifica tutto il contenuto: se un solo byte è cambiato, fallisce. */
    fun decrypt(parsed: Parsed, dataKey: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(GCM_TAG, parsed.nonce))
        cipher.updateAAD(parsed.aad)
        cipher.doFinal(parsed.body)
    } catch (e: Exception) {
        throw BadFile()
    }

    private fun headerBytes(h: KeyHeader, createdAt: Long, nonce: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            writeByte(VERSION)
            writeLong(createdAt)
            writeInt(h.iterations)
            listOf(h.saltPass, h.wrapPass, h.saltCode, h.wrapCode, nonce).forEach { writeShort(it.size); write(it) }
            flush()
        }
        return out.toByteArray()
    }

    private fun read(input: DataInputStream): ByteArray {
        val n = input.readUnsignedShort()
        if (n > 1024) throw BadFile()
        return ByteArray(n).also { input.readFully(it) }
    }

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret, salt, iterations, 256)).encoded

    private fun wrap(kek: ByteArray, key: ByteArray): ByteArray {
        val nonce = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, nonce))
        return nonce + cipher.doFinal(key)
    }

    private fun unwrap(kek: ByteArray, wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, wrapped.copyOfRange(0, 12)))
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }

    private fun bytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }
}
