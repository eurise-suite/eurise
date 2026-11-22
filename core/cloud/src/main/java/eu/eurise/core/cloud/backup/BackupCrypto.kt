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
 *
 * Versione 1 (fino al 21 novembre 2026): un solo blocco GCM, tutto in memoria. Versione 2: i dati a pezzi di
 * [SEGMENT] byte, ciascuno cifrato e autenticato da solo, così un backup con le foto della cartella protetta non deve
 * stare tutto in memoria. Ogni pezzo: lunghezza (4 byte) e dati cifrati con l'etichetta; nonce = gli 8 byte
 * dell'intestazione + il numero del pezzo; AAD = intestazione + numero + «ultimo». Un file troncato, con pezzi
 * scambiati o con qualcosa dopo l'ultimo non si apre. I file della versione 1 si aprono come prima.
 */
object BackupCrypto {
    private const val MAGIC = "EURISEB1"
    private const val VERSION = 1
    private const val VERSION_STREAM = 2
    /** Dati in chiaro per pezzo, nella versione 2. */
    const val SEGMENT = 1 shl 20
    /** PBKDF2-HMAC-SHA256 per i backup nuovi: 600 000 iterazioni (OWASP 2023; erano 310 000 fino al 18 novembre 2026). */
    const val ITERATIONS = 600_000
    /** Quelle dei backup impostati prima: le si legge dal file o dalle preferenze, questo è solo il valore di riserva. */
    const val ITERATIONS_V1 = 310_000
    /** Un file con un numero di iterazioni assurdo (telefono bloccato per ore a provarlo) non si prova nemmeno. */
    private val ITERATIONS_RANGE = 1_000..5_000_000
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

    /**
     * Frase e codice di recupero nuovi per la stessa chiave dei dati (quella custodita nel Keystore): serve quando
     * l'utente non ricorda più la frase ma ha ancora il telefono. I file già fatti restano chiusi con quelli vecchi.
     */
    fun rewrapAll(dataKey: ByteArray, passphrase: CharArray, recoveryCode: String, iterations: Int = ITERATIONS): KeyHeader {
        val saltPass = bytes(16)
        val saltCode = bytes(16)
        return KeyHeader(
            iterations,
            saltPass, wrap(derive(passphrase, saltPass, iterations), dataKey),
            saltCode, wrap(derive(normalizeCode(recoveryCode).toCharArray(), saltCode, iterations), dataKey),
        )
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
            if (iterations !in ITERATIONS_RANGE) throw BadFile()
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

    // ---------------------------------------------------------------- versione 2, a pezzi

    /** L'intestazione letta da un flusso, di qualsiasi versione; [aad] sono i suoi byte, autenticati con i dati. */
    class Header internal constructor(val header: KeyHeader, val createdAt: Long, internal val version: Int, internal val nonce: ByteArray, internal val aad: ByteArray)

    /**
     * Il flusso che cifra a pezzi verso [out]: scrive subito l'intestazione; [OutputStream.close] scrive l'ultimo pezzo e
     * chiude [out]. Niente in chiaro esce da qui.
     */
    fun encryptingStream(keys: Keys, createdAt: Long, out: java.io.OutputStream): java.io.OutputStream {
        val prefix = bytes(8)
        val aad = headerBytes(keys.header, createdAt, prefix, VERSION_STREAM)
        out.write(aad)
        return SegmentWriter(SecretKeySpec(keys.dataKey, "AES"), prefix, aad, out)
    }

    /** Legge l'intestazione dall'inizio di [input] (versione 1 o 2). */
    fun readHeader(input: java.io.InputStream): Header {
        try {
            val head = ByteArrayOutputStream()
            val data = DataInputStream(input)
            fun raw(n: Int) = ByteArray(n).also { data.readFully(it); head.write(it) }
            if (String(raw(8), Charsets.US_ASCII) != MAGIC) throw BadFile()
            val version = raw(1)[0].toInt()
            if (version != VERSION && version != VERSION_STREAM) throw BadFile()
            val createdAt = java.nio.ByteBuffer.wrap(raw(8)).long
            val iterations = java.nio.ByteBuffer.wrap(raw(4)).int
            if (iterations !in ITERATIONS_RANGE) throw BadFile()
            fun field(): ByteArray {
                val len = raw(2).let { ((it[0].toInt() and 0xff) shl 8) or (it[1].toInt() and 0xff) }
                if (len > 1024) throw BadFile()
                return raw(len)
            }
            val saltPass = field(); val wrapPass = field(); val saltCode = field(); val wrapCode = field(); val nonce = field()
            return Header(KeyHeader(iterations, saltPass, wrapPass, saltCode, wrapCode), createdAt, version, nonce, head.toByteArray())
        } catch (e: BadFile) {
            throw e
        } catch (e: Exception) {
            throw BadFile()
        }
    }

    /**
     * I dati in chiaro dopo [header], letti da [input]: per la versione 2 a pezzi, verificati uno per uno (un pezzo
     * toccato, mancante o in più fa fallire la lettura con [java.io.IOException]); per la versione 1 tutto in memoria.
     */
    fun decryptingStream(input: java.io.InputStream, header: Header, dataKey: ByteArray): java.io.InputStream =
        if (header.version == VERSION) {
            val body = input.readBytes()
            ByteArrayInputStream(decrypt(Parsed(header.header, header.createdAt, header.nonce, header.aad, body), dataKey))
        } else SegmentReader(SecretKeySpec(dataKey, "AES"), header.nonce, header.aad, input)

    private fun segmentAad(aad: ByteArray, index: Int, last: Boolean): ByteArray =
        aad + java.nio.ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array()

    private fun segmentNonce(prefix: ByteArray, index: Int): ByteArray = prefix + java.nio.ByteBuffer.allocate(4).putInt(index).array()

    private class SegmentWriter(private val key: SecretKeySpec, private val prefix: ByteArray, private val aad: ByteArray, private val out: java.io.OutputStream) : java.io.OutputStream() {
        private val buffer = ByteArray(SEGMENT)
        private var filled = 0
        private var index = 0
        private var closed = false

        override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed)
            var o = off
            var left = len
            while (left > 0) {
                // Un pezzo pieno si scrive solo quando arriva altro: l'ultimo deve poter essere segnato come tale.
                if (filled == SEGMENT) emit(last = false)
                val n = minOf(left, SEGMENT - filled)
                System.arraycopy(b, o, buffer, filled, n)
                filled += n; o += n; left -= n
            }
        }

        private fun emit(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG, segmentNonce(prefix, index)))
            cipher.updateAAD(segmentAad(aad, index, last))
            val ct = cipher.doFinal(buffer, 0, filled)
            out.write(java.nio.ByteBuffer.allocate(4).putInt(ct.size).array())
            out.write(ct)
            index++
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            emit(last = true)
            closed = true
            out.close()
        }
    }

    private class SegmentReader(private val key: SecretKeySpec, private val prefix: ByteArray, private val aad: ByteArray, input: java.io.InputStream) : java.io.InputStream() {
        private val data = DataInputStream(input)
        private var plain = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var done = false
        private var pending: ByteArray? = null

        /** Il prossimo pezzo cifrato, o null alla fine del file. */
        private fun nextCipher(): ByteArray? {
            val head = ByteArray(4)
            var got = 0
            while (got < 4) { val r = data.read(head, got, 4 - got); if (r < 0) break; got += r }
            if (got == 0) return null
            if (got < 4) throw java.io.IOException("backup troncato")
            val len = java.nio.ByteBuffer.wrap(head).int
            if (len < GCM_TAG / 8 || len > SEGMENT + GCM_TAG / 8) throw java.io.IOException("pezzo non valido")
            return ByteArray(len).also { data.readFully(it) }
        }

        private fun fill(): Boolean {
            if (done) return false
            val current = pending ?: nextCipher() ?: throw java.io.IOException("backup troncato")
            // Si guarda avanti di un pezzo: se non ce n'è un altro, questo deve essere l'ultimo.
            val next = nextCipher()
            val last = next == null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, segmentNonce(prefix, index)))
            cipher.updateAAD(segmentAad(aad, index, last))
            plain = try { cipher.doFinal(current) } catch (e: Exception) { throw java.io.IOException("backup rovinato o toccato") }
            pos = 0
            index++
            pending = next
            if (last) done = true
            return true
        }

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) { if (!fill()) return -1 }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = data.close()
    }

    private fun headerBytes(h: KeyHeader, createdAt: Long, nonce: ByteArray, version: Int = VERSION): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            write(MAGIC.toByteArray(Charsets.US_ASCII))
            writeByte(version)
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

    internal fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret, salt, iterations, 256)).encoded

    internal fun wrap(kek: ByteArray, key: ByteArray): ByteArray {
        val nonce = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, nonce))
        return nonce + cipher.doFinal(key)
    }

    internal fun unwrap(kek: ByteArray, wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG, wrapped.copyOfRange(0, 12)))
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }

    internal fun bytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }
}
