package eu.eurise.core.cloud.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dati del backup che devono aspettare sul telefono (per esempio le parole della Tastiera finché la Tastiera non è
 * installata): si tengono cifrati con AES-256-GCM e una chiave che non esce dal Keystore, mai in chiaro su disco.
 * Formato di [seal]: nonce di 12 byte, poi i dati cifrati con l'etichetta. Per i dati grandi ([sealTo], [openFrom]) a
 * pezzi: per ognuno lunghezza (4 byte), nonce del Keystore (12) e dati cifrati; AAD = numero del pezzo + «ultimo».
 */
internal object LocalSeal {
    private const val ALIAS = "eurise-backup-waiting"
    private const val NONCE = 12

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return generator.generateKey()
    }

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, NONCE))
        return c.doFinal(sealed, NONCE, sealed.size - NONCE)
    }

    private const val CHUNK = 1 shl 20

    private fun chunkAad(index: Int, last: Boolean) = java.nio.ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array()

    /** Sigilla tutto [input] in [target] a pezzi, senza tenerlo in memoria (prima in un file a parte, poi lo rinomina). */
    fun sealTo(input: java.io.InputStream, target: java.io.File) {
        val tmp = java.io.File(target.path + ".tmp")
        val key = key()
        java.io.DataOutputStream(java.io.BufferedOutputStream(tmp.outputStream())).use { out ->
            var index = 0
            var current = readChunk(input)
            while (true) {
                val next = if (current.size == CHUNK) readChunk(input) else null
                val last = next == null || next.isEmpty()
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key)
                c.updateAAD(chunkAad(index, last))
                val ct = c.doFinal(current)
                out.writeInt(ct.size); out.write(c.iv); out.write(ct)
                index++
                if (last) break
                current = next!!
            }
        }
        target.delete()
        check(tmp.renameTo(target)) { "sigillo non salvato" }
    }

    /** Il contenuto di un file fatto da [sealTo], aperto a pezzi mentre lo si legge. */
    fun openFrom(file: java.io.File): java.io.InputStream = object : java.io.InputStream() {
        private val data = java.io.DataInputStream(java.io.BufferedInputStream(file.inputStream()))
        private val key = key()
        private var plain = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var done = false
        private var pending: Pair<ByteArray, ByteArray>? = readSealed()

        private fun readSealed(): Pair<ByteArray, ByteArray>? {
            val len = try { data.readInt() } catch (e: java.io.EOFException) { return null }
            if (len < 16 || len > CHUNK + 16) throw java.io.IOException("sigillo rovinato")
            val iv = ByteArray(NONCE).also { data.readFully(it) }
            return iv to ByteArray(len).also { data.readFully(it) }
        }

        private fun fill(): Boolean {
            if (done) return false
            val (iv, ct) = pending ?: throw java.io.IOException("sigillo troncato")
            pending = readSealed()
            val last = pending == null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(chunkAad(index, last))
            plain = try { c.doFinal(ct) } catch (e: Exception) { throw java.io.IOException("sigillo rovinato") }
            pos = 0; index++
            if (last) done = true
            return true
        }

        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) { if (!fill()) return -1 }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n); pos += n
            return n
        }
        override fun close() = data.close()
    }

    private fun readChunk(input: java.io.InputStream): ByteArray {
        val buf = ByteArray(CHUNK)
        var got = 0
        while (got < CHUNK) { val r = input.read(buf, got, CHUNK - got); if (r < 0) break; got += r }
        return if (got == CHUNK) buf else buf.copyOf(got)
    }
}
