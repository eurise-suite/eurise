package eu.eurise.core.cloud.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random

/** Il formato a pezzi (versione 2): si rilegge uguale, e un file toccato, troncato o allungato non si apre. */
class BackupStreamTest {
    private val keys = BackupCrypto.setup("una frase lunga e mia".toCharArray(), BackupCrypto.newRecoveryCode(), iterations = 1_000)

    private fun encrypt(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(keys, 4321L, out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, secret: String = "una frase lunga e mia"): ByteArray {
        val input = ByteArrayInputStream(file)
        val header = BackupCrypto.readHeader(input)
        assertEquals(4321L, header.createdAt)
        val key = BackupCrypto.unlock(header.header, secret)
        return BackupCrypto.decryptingStream(input, header, key).readBytes()
    }

    @Test
    fun roundTripAcrossSegments() {
        listOf(0, 1, BackupCrypto.SEGMENT - 1, BackupCrypto.SEGMENT, BackupCrypto.SEGMENT + 1, BackupCrypto.SEGMENT * 2 + 12345).forEach { n ->
            val data = Random(n).nextBytes(n)
            assertArrayEquals("$n byte", data, decrypt(encrypt(data)))
        }
    }

    @Test
    fun startsLikeEveryBackup() = assertTrue(String(encrypt(byteArrayOf(1)).copyOf(8), Charsets.US_ASCII) == "EURISEB1")

    @Test(expected = IOException::class)
    fun tamperedByteFails() {
        val file = encrypt(Random(1).nextBytes(BackupCrypto.SEGMENT + 10))
        file[file.size - 20] = (file[file.size - 20].toInt() xor 1).toByte()
        decrypt(file)
    }

    @Test(expected = IOException::class)
    fun truncatedAtSegmentBoundaryFails() {
        val data = Random(2).nextBytes(BackupCrypto.SEGMENT * 2 + 5)
        val file = encrypt(data)
        // Si toglie l'ultimo pezzo intero: quello che resta finisce con un pezzo che non è segnato come ultimo.
        val lastLen = 4 + 5 + 16
        decrypt(file.copyOf(file.size - lastLen))
    }

    @Test(expected = IOException::class)
    fun extraBytesAfterLastFail() {
        val file = encrypt(Random(3).nextBytes(100))
        decrypt(file + encrypt(Random(4).nextBytes(10)).copyOfRange(0, 40))
    }

    @Test
    fun oldSingleBlockFilesStillOpen() {
        val data = Random(5).nextBytes(5000)
        val v1 = BackupCrypto.encrypt(keys, 4321L, data)
        assertArrayEquals(data, decrypt(v1))
    }

    @Test(expected = BackupCrypto.WrongSecret::class)
    fun wrongSecretRefused() { decrypt(encrypt(byteArrayOf(1, 2, 3)), "un'altra frase") }
}
