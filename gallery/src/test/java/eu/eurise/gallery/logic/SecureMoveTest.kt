package eu.eurise.gallery.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.random.Random

/** La cartella «dal backup» che entra nella tua: ricifrare senza file in chiaro, e accorgersi se qualcosa non va. */
class SecureMoveTest {
    private val oldKey = Random(1).nextBytes(32)
    private val newKey = Random(2).nextBytes(32)

    private fun sealed(key: ByteArray, label: String, plain: ByteArray) =
        ByteArrayOutputStream().also { SecureStream.encrypt(key, label, ByteArrayInputStream(plain), it) }.toByteArray()

    private fun open(key: ByteArray, label: String, data: ByteArray) =
        ByteArrayOutputStream().also { SecureStream.decrypt(key, label, ByteArrayInputStream(data), it) }.toByteArray()

    @Test
    fun passaAllaChiaveNuova() {
        for (size in listOf(0, 1, SecureStream.CHUNK - 1, SecureStream.CHUNK, SecureStream.CHUNK * 2 + 7)) {
            val plain = Random(size).nextBytes(size)
            val out = ByteArrayOutputStream()
            val sha = SecureMove.reencrypt(oldKey, "a", newKey, "b", ByteArrayInputStream(sealed(oldKey, "a", plain)), out)
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(plain), sha)
            assertArrayEquals(plain, open(newKey, "b", out.toByteArray()))
            assertArrayEquals(sha, SecureMove.digest(newKey, "b", ByteArrayInputStream(out.toByteArray())))
            // Con la chiave vecchia la copia nuova non si apre.
            assertThrows(Exception::class.java) { open(oldKey, "b", out.toByteArray()) }
        }
    }

    @Test
    fun chiaveSbagliataFerma() {
        val data = sealed(oldKey, "a", Random(3).nextBytes(5000))
        assertThrows(Exception::class.java) { SecureMove.reencrypt(newKey, "a", newKey, "b", ByteArrayInputStream(data), ByteArrayOutputStream()) }
    }

    @Test
    fun fileTroncatoFerma() {
        // Taglio a metà del secondo blocco: la parte già letta è buona, ma il passaggio deve fallire.
        val data = sealed(oldKey, "a", Random(4).nextBytes(SecureStream.CHUNK + 5000))
        val cut = data.copyOf(SecureStream.CHUNK + 2000)
        assertThrows(Exception::class.java) { SecureMove.reencrypt(oldKey, "a", newKey, "b", ByteArrayInputStream(cut), ByteArrayOutputStream()) }
    }

    @Test(timeout = 20_000)
    fun scritturaChesiFermaNonBlocca() {
        val data = sealed(oldKey, "a", Random(5).nextBytes(SecureStream.CHUNK * 3))
        val broken = object : OutputStream() {
            var n = 0
            override fun write(b: Int) { if (++n > 1000) throw IOException("disco pieno") }
            override fun write(b: ByteArray, off: Int, len: Int) { n += len; if (n > 1000) throw IOException("disco pieno") }
        }
        assertThrows(IOException::class.java) { SecureMove.reencrypt(oldKey, "a", newKey, "b", ByteArrayInputStream(data), broken) }
    }
}
