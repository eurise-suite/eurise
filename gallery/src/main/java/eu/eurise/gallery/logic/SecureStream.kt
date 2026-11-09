package eu.eurise.gallery.logic

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifratura a blocchi per la cartella protetta (AES-256-GCM): i file grandi non passano mai interi dalla memoria.
 *
 * Formato: "EUGS1", 7 byte casuali, poi blocchi da [CHUNK] byte con il loro tag. Il nonce di ogni blocco è fatto dai
 * 7 byte, dal numero del blocco e da un segno di "ultimo blocco": riordinare, togliere o troncare i blocchi fa
 * fallire la lettura. [label] lega il contenuto al suo file.
 */
object SecureStream {
    const val CHUNK = 1024 * 1024
    private const val TAG_BYTES = 16
    private val MAGIC = "EUGS1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    class Corrupted : Exception("File protetto rovinato o frase sbagliata")

    fun encrypt(key: ByteArray, label: String, input: InputStream, output: OutputStream) {
        val prefix = ByteArray(7).also { random.nextBytes(it) }
        output.write(MAGIC)
        output.write(prefix)
        var current = ByteArray(CHUNK)
        var next = ByteArray(CHUNK)
        var length = fill(input, current)
        var index = 0
        while (true) {
            val nextLength = if (length == CHUNK) fill(input, next) else 0
            val last = nextLength == 0
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, label, prefix, index, last)
            output.write(cipher.doFinal(current, 0, length))
            if (last) return
            val swap = current; current = next; next = swap
            length = nextLength
            index++
        }
    }

    fun decrypt(key: ByteArray, label: String, input: InputStream, output: OutputStream) {
        val head = ByteArray(MAGIC.size + 7)
        if (fill(input, head) != head.size || !head.copyOf(MAGIC.size).contentEquals(MAGIC)) throw Corrupted()
        val prefix = head.copyOfRange(MAGIC.size, head.size)
        var current = ByteArray(CHUNK + TAG_BYTES)
        var next = ByteArray(CHUNK + TAG_BYTES)
        var length = fill(input, current)
        var index = 0
        try {
            while (true) {
                val nextLength = if (length == current.size) fill(input, next) else 0
                val last = nextLength == 0
                if (length < TAG_BYTES) throw Corrupted()
                output.write(cipher(Cipher.DECRYPT_MODE, key, label, prefix, index, last).doFinal(current, 0, length))
                if (last) return
                val swap = current; current = next; next = swap
                length = nextLength
                index++
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            throw Corrupted()
        } catch (e: EOFException) {
            throw Corrupted()
        }
    }

    private fun cipher(mode: Int, key: ByteArray, label: String, prefix: ByteArray, index: Int, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(index).put(if (last) 1.toByte() else 0.toByte()).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(label.toByteArray())
        }
    }

    private fun fill(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
