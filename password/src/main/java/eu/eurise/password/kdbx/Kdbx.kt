package eu.eurise.password.kdbx

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory

/** Perché un file non si è aperto. */
sealed class KdbxException(message: String) : Exception(message) {
    class WrongPassword : KdbxException("Frase principale errata")
    class Unsupported(what: String) : KdbxException("Non supportato: $what")
    class Corrupt(what: String) : KdbxException("File danneggiato: $what")
}

/**
 * Un file KeePass aperto. Il contenuto resta nel documento XML, con i campi protetti in chiaro: tutto ciò che Eurise
 * non conosce (allegati, impostazioni di KeePassXC, campi aggiuntivi) viene riscritto tale e quale.
 */
class KdbxFile internal constructor(
    internal val version: Int,
    internal val fields: LinkedHashMap<Int, ByteArray>,
    internal val binaries: List<ByteArray>,
    val document: Document,
    internal val transformedKey: ByteArray,
)

/**
 * Lettura e scrittura del formato KDBX 4, lo stesso di KeePass e KeePassXC.
 * Riferimento: https://keepass.info/help/kb/kdbx_4.html
 */
object Kdbx {
    private const val SIG1 = 0x9AA2D903.toInt()
    private const val SIG2 = 0xB54BFB67.toInt()
    private const val VERSION_4 = 0x00040000
    private const val F_END = 0
    private const val F_CIPHER = 2
    private const val F_COMPRESSION = 3
    private const val F_SEED = 4
    private const val F_IV = 7
    private const val F_KDF = 11
    private const val I_END = 0
    private const val I_STREAM_ID = 1
    private const val I_STREAM_KEY = 2
    private const val I_BINARY = 3
    private const val STREAM_CHACHA = 3
    private const val BLOCK_SIZE = 1024 * 1024

    private val AES = uuid("31c1f2e6bf714350be5805216afc5aff")
    private val CHACHA = uuid("d6038a2b8b6f4cb5a524339a31dbb59a")
    private val KDF_AES = uuid("c9d9f39a628a4460bf740d08c18a4fea")
    private val KDF_ARGON2D = uuid("ef636ddf8c29444b91f7a9a403e30a0c")
    private val KDF_ARGON2ID = uuid("9e298b1956db4773b23dfc3ec6f0a1e6")

    private val random = SecureRandom()

    /** La chiave composta di KeePass per una sola frase, senza file chiave. */
    fun compositeKey(password: String): ByteArray = sha256(sha256(password.toByteArray(Charsets.UTF_8)))

    /** Apre il file; la fase lenta è Argon2, quindi non va chiamata dal thread principale. */
    fun read(bytes: ByteArray, compositeKey: ByteArray): KdbxFile = try {
        readInternal(bytes, compositeKey)
    } catch (e: java.nio.BufferUnderflowException) {
        throw KdbxException.Corrupt("finisce prima del previsto")
    } catch (e: RuntimeException) {
        throw KdbxException.Corrupt(e.message ?: "valori non validi")
    } catch (e: java.io.IOException) {
        throw KdbxException.Corrupt(e.message ?: "lettura non riuscita")
    } catch (e: org.xml.sax.SAXException) {
        throw KdbxException.Corrupt("contenuto non leggibile")
    }

    private fun readInternal(bytes: ByteArray, compositeKey: ByteArray): KdbxFile {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt() != SIG1 || buf.getInt() != SIG2) throw KdbxException.Unsupported("non è un file KeePass")
        val version = buf.getInt()
        if (version ushr 16 != 4) throw KdbxException.Unsupported("versione KDBX ${version ushr 16}")

        val fields = LinkedHashMap<Int, ByteArray>()
        while (true) {
            val id = buf.get().toInt() and 0xFF
            val data = take(buf, buf.getInt())
            if (id == F_END) break
            fields[id] = data
        }
        val headerEnd = buf.position()
        val header = bytes.copyOfRange(0, headerEnd)
        if (!MessageDigest.isEqual(take(buf, 32), sha256(header))) throw KdbxException.Corrupt("intestazione alterata")
        val storedMac = take(buf, 32)

        val transformed = transform(compositeKey, fields[F_KDF] ?: throw KdbxException.Corrupt("manca la derivazione"))
        val seed = fields[F_SEED] ?: throw KdbxException.Corrupt("manca il seme")
        val macBase = sha512(seed, transformed, byteArrayOf(1))
        if (!MessageDigest.isEqual(storedMac, hmac(blockKey(macBase, -1L), header))) throw KdbxException.WrongPassword()

        val encrypted = ByteArrayOutputStream()
        var index = 0L
        while (true) {
            val mac = take(buf, 32)
            val size = buf.getInt()
            val block = take(buf, size)
            val expected = hmac(blockKey(macBase, index), le64(index), le32(size), block)
            if (!MessageDigest.isEqual(mac, expected)) throw KdbxException.Corrupt("blocco $index alterato")
            if (size == 0) break
            encrypted.write(block)
            index++
        }

        val key = sha256(seed, transformed)
        val iv = fields[F_IV] ?: throw KdbxException.Corrupt("manca il vettore iniziale")
        val plain = decrypt(fields[F_CIPHER], key, iv, encrypted.toByteArray())
        val compressed = fields[F_COMPRESSION]?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).getInt() } == 1
        val payload = ByteBuffer.wrap(if (compressed) gunzip(plain) else plain).order(ByteOrder.LITTLE_ENDIAN)

        var streamId = 0
        var streamKey = ByteArray(0)
        val binaries = ArrayList<ByteArray>()
        while (true) {
            val id = payload.get().toInt() and 0xFF
            val data = take(payload, payload.getInt())
            when (id) {
                I_END -> break
                I_STREAM_ID -> streamId = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt()
                I_STREAM_KEY -> streamKey = data
                I_BINARY -> binaries += data
            }
        }
        if (streamId != STREAM_CHACHA) throw KdbxException.Unsupported("protezione interna $streamId")
        val xml = take(payload, payload.remaining())
        val document = parseXml(xml)
        val stream = innerStream(streamKey)
        forEachProtected(document.documentElement) { value ->
            value.textContent = String(stream.apply(Base64.getDecoder().decode(value.textContent.trim())), Charsets.UTF_8)
        }
        return KdbxFile(version, fields, binaries, document, transformed)
    }

    /** Scrive il file con un seme e un vettore nuovi; la chiave derivata resta quella già calcolata, quindi è veloce. */
    fun write(file: KdbxFile): ByteArray {
        val fields = LinkedHashMap(file.fields)
        val seed = randomBytes(32)
        val iv = randomBytes(if (file.fields[F_CIPHER].contentEqualsOrNull(CHACHA)) 12 else 16)
        fields[F_SEED] = seed
        fields[F_IV] = iv
        fields[F_COMPRESSION] = le32(1)

        val headerOut = ByteArrayOutputStream()
        headerOut.write(le32(SIG1)); headerOut.write(le32(SIG2)); headerOut.write(le32(file.version))
        for ((id, data) in fields) writeField(headerOut, id, data)
        writeField(headerOut, F_END, byteArrayOf(13, 10, 13, 10))
        val header = headerOut.toByteArray()

        val macBase = sha512(seed, file.transformedKey, byteArrayOf(1))
        val out = ByteArrayOutputStream()
        out.write(header)
        out.write(sha256(header))
        out.write(hmac(blockKey(macBase, -1L), header))

        val streamKey = randomBytes(64)
        val plainPayload = ByteArrayOutputStream()
        writeInner(plainPayload, I_STREAM_ID, le32(STREAM_CHACHA))
        writeInner(plainPayload, I_STREAM_KEY, streamKey)
        file.binaries.forEach { writeInner(plainPayload, I_BINARY, it) }
        writeInner(plainPayload, I_END, ByteArray(0))
        plainPayload.write(serialize(file.document, innerStream(streamKey)))

        val encrypted = encrypt(fields[F_CIPHER], sha256(seed, file.transformedKey), iv, gzip(plainPayload.toByteArray()))
        var index = 0L
        var at = 0
        while (at < encrypted.size) {
            val size = minOf(BLOCK_SIZE, encrypted.size - at)
            writeBlock(out, macBase, index++, encrypted.copyOfRange(at, at + size))
            at += size
        }
        writeBlock(out, macBase, index, ByteArray(0))
        return out.toByteArray()
    }

    /**
     * Cassaforte nuova: AES-256 con Argon2id da 64 MiB, valori che KeePassXC apre senza difficoltà e che su un
     * telefono richiedono circa un secondo.
     */
    fun create(compositeKey: ByteArray, name: String): KdbxFile {
        val kdf = variantDictionary(KDF_ARGON2ID, randomBytes(32), iterations = 3, memory = 64L * 1024 * 1024, parallelism = 2)
        val fields = LinkedHashMap<Int, ByteArray>()
        fields[F_CIPHER] = AES
        fields[F_COMPRESSION] = le32(1)
        fields[F_SEED] = randomBytes(32)
        fields[F_IV] = randomBytes(16)
        fields[F_KDF] = kdf
        val now = Base64.getEncoder().encodeToString(Vault.timeBytes(System.currentTimeMillis()))
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<KeePassFile><Meta><Generator>Eurise</Generator><DatabaseName>${escape(name)}</DatabaseName><DatabaseNameChanged>$now</DatabaseNameChanged><MemoryProtection><ProtectTitle>False</ProtectTitle><ProtectUserName>False</ProtectUserName><ProtectPassword>True</ProtectPassword><ProtectURL>False</ProtectURL><ProtectNotes>False</ProtectNotes></MemoryProtection><RecycleBinEnabled>True</RecycleBinEnabled></Meta><Root><Group><UUID>${Vault.newUuid()}</UUID><Name>${escape(name)}</Name><Times><CreationTime>$now</CreationTime><LastModificationTime>$now</LastModificationTime><LastAccessTime>$now</LastAccessTime><ExpiryTime>$now</ExpiryTime><Expires>False</Expires><UsageCount>0</UsageCount><LocationChanged>$now</LocationChanged></Times><IsExpanded>True</IsExpanded></Group><DeletedObjects></DeletedObjects></Root></KeePassFile>"""
        return KdbxFile(VERSION_4, fields, emptyList(), parseXml(xml.toByteArray(Charsets.UTF_8)), transform(compositeKey, kdf))
    }

    private fun transform(composite: ByteArray, kdfBytes: ByteArray): ByteArray {
        val kdf = readVariantDictionary(kdfBytes)
        val id = kdf["\$UUID"] as? ByteArray ?: throw KdbxException.Corrupt("derivazione senza nome")
        return when {
            id.contentEquals(KDF_ARGON2D) || id.contentEquals(KDF_ARGON2ID) -> {
                val salt = kdf["S"] as ByteArray
                val memoryKiB = (kdf["M"] as Long) / 1024
                if (memoryKiB > MAX_MEMORY_KIB) throw KdbxException.Unsupported("derivazione da ${memoryKiB / 1024} MiB, troppa memoria")
                Argon2.hash(
                    type = if (id.contentEquals(KDF_ARGON2D)) Argon2.TYPE_D else Argon2.TYPE_ID,
                    password = composite,
                    salt = salt,
                    iterations = (kdf["I"] as Long).toInt(),
                    memoryKiB = memoryKiB.toInt(),
                    parallelism = (kdf["P"] as Int),
                    tagLength = 32,
                )
            }
            id.contentEquals(KDF_AES) -> {
                val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kdf["S"] as ByteArray, "AES"))
                var value = composite.copyOf()
                repeat((kdf["R"] as Long).toInt()) { value = cipher.doFinal(value) }
                sha256(value)
            }
            else -> throw KdbxException.Unsupported("derivazione sconosciuta")
        }
    }

    private const val MAX_MEMORY_KIB = 256 * 1024L

    private fun decrypt(cipherId: ByteArray?, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray = when {
        cipherId.contentEqualsOrNull(AES) -> aes(Cipher.DECRYPT_MODE, key, iv, data)
        cipherId.contentEqualsOrNull(CHACHA) -> ChaCha20(key, iv).apply(data)
        else -> throw KdbxException.Unsupported("cifratura diversa da AES e ChaCha20")
    }

    private fun encrypt(cipherId: ByteArray?, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        if (cipherId.contentEqualsOrNull(CHACHA)) ChaCha20(key, iv).apply(data) else aes(Cipher.ENCRYPT_MODE, key, iv, data)

    private fun aes(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
        try {
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }.doFinal(data)
        } catch (e: java.security.GeneralSecurityException) {
            throw KdbxException.Corrupt("cifratura non valida")
        }

    private fun innerStream(key: ByteArray): ChaCha20 {
        val hash = sha512(key)
        return ChaCha20(hash.copyOfRange(0, 32), hash.copyOfRange(32, 44))
    }

    private fun parseXml(bytes: ByteArray): Document {
        val hasBom = bytes.size > 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val input = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(input))
    }

    internal fun forEachProtected(element: Element, action: (Element) -> Unit) {
        if (element.getAttribute("Protected").equals("True", ignoreCase = true)) action(element)
        var child = element.firstChild
        while (child != null) {
            if (child is Element) forEachProtected(child, action)
            child = child.nextSibling
        }
    }

    /** XML scritto a mano: il documento non cambia e i campi protetti si cifrano nell'ordine in cui compaiono. */
    private fun serialize(document: Document, stream: ChaCha20): ByteArray {
        val out = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>\n")
        fun walk(node: Node) {
            when (node) {
                is Element -> {
                    out.append('<').append(node.tagName)
                    val attributes = node.attributes
                    for (i in 0 until attributes.length) {
                        val a = attributes.item(i)
                        out.append(' ').append(a.nodeName).append("=\"").append(escape(a.nodeValue)).append('"')
                    }
                    if (node.getAttribute("Protected").equals("True", ignoreCase = true)) {
                        val cipher = stream.apply(node.textContent.toByteArray(Charsets.UTF_8))
                        out.append('>').append(Base64.getEncoder().encodeToString(cipher))
                        out.append("</").append(node.tagName).append('>')
                        return
                    }
                    if (node.firstChild == null) { out.append("/>"); return }
                    out.append('>')
                    var child = node.firstChild
                    while (child != null) { walk(child); child = child.nextSibling }
                    out.append("</").append(node.tagName).append('>')
                }
                else -> if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) out.append(escape(node.nodeValue))
            }
        }
        walk(document.documentElement)
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        for (c in text) when {
            c == '&' -> out.append("&amp;")
            c == '<' -> out.append("&lt;")
            c == '>' -> out.append("&gt;")
            c == '"' -> out.append("&quot;")
            // XML 1.0 non ammette gli altri caratteri di controllo.
            c < ' ' && c != '\n' && c != '\r' && c != '\t' -> Unit
            else -> out.append(c)
        }
        return out.toString()
    }

    private fun variantDictionary(id: ByteArray, salt: ByteArray, iterations: Long, memory: Long, parallelism: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 1))
        fun entry(type: Int, name: String, value: ByteArray) {
            out.write(type)
            val n = name.toByteArray(Charsets.UTF_8)
            out.write(le32(n.size)); out.write(n); out.write(le32(value.size)); out.write(value)
        }
        entry(0x42, "\$UUID", id)
        entry(0x42, "S", salt)
        entry(0x04, "P", le32(parallelism))
        entry(0x05, "M", le64(memory))
        entry(0x05, "I", le64(iterations))
        entry(0x04, "V", le32(0x13))
        out.write(0)
        return out.toByteArray()
    }

    private fun readVariantDictionary(bytes: ByteArray): Map<String, Any> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.getShort()
        val map = HashMap<String, Any>()
        while (buf.hasRemaining()) {
            val type = buf.get().toInt() and 0xFF
            if (type == 0) break
            val name = String(take(buf, buf.getInt()), Charsets.UTF_8)
            val value = take(buf, buf.getInt())
            val number = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            map[name] = when (type) {
                0x04, 0x0C -> number.getInt()
                0x05, 0x0D -> number.getLong()
                0x08 -> value[0] != 0.toByte()
                0x18 -> String(value, Charsets.UTF_8)
                else -> value
            }
        }
        return map
    }

    private fun writeField(out: ByteArrayOutputStream, id: Int, data: ByteArray) {
        out.write(id); out.write(le32(data.size)); out.write(data)
    }

    private fun writeInner(out: ByteArrayOutputStream, id: Int, data: ByteArray) = writeField(out, id, data)

    private fun writeBlock(out: ByteArrayOutputStream, macBase: ByteArray, index: Long, data: ByteArray) {
        out.write(hmac(blockKey(macBase, index), le64(index), le32(data.size), data))
        out.write(le32(data.size))
        out.write(data)
    }

    private fun blockKey(base: ByteArray, index: Long) = sha512(le64(index), base)

    private fun take(buf: ByteBuffer, size: Int): ByteArray {
        if (size < 0 || size > buf.remaining()) throw KdbxException.Corrupt("dimensione non valida")
        return ByteArray(size).also { buf.get(it) }
    }

    private fun sha256(vararg parts: ByteArray): ByteArray = digest("SHA-256", parts)
    private fun sha512(vararg parts: ByteArray): ByteArray = digest("SHA-512", parts)
    private fun digest(name: String, parts: Array<out ByteArray>): ByteArray =
        MessageDigest.getInstance(name).apply { parts.forEach { update(it) } }.digest()

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")); parts.forEach { update(it) } }.doFinal()

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun le64(v: Long): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    private fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }
    private fun uuid(hex: String) = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray?.contentEqualsOrNull(other: ByteArray) = this != null && contentEquals(other)
}
