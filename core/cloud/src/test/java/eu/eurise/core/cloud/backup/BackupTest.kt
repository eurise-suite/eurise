package eu.eurise.core.cloud.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupTest {

    private val fast = 1_000

    @Test
    fun encryptAndOpenWithPassphraseOrCode() {
        val code = BackupCrypto.newRecoveryCode()
        val keys = BackupCrypto.setup("una frase lunga e mia".toCharArray(), code, fast)
        val data = "note, attività e sveglie".toByteArray()
        val file = BackupCrypto.encrypt(keys, 1234L, data)
        val parsed = BackupCrypto.parse(file)
        assertEquals(1234L, parsed.createdAt)
        assertArrayEquals(data, BackupCrypto.decrypt(parsed, BackupCrypto.unlock(parsed.header, "una frase lunga e mia")))
        assertArrayEquals(data, BackupCrypto.decrypt(parsed, BackupCrypto.unlock(parsed.header, code.lowercase().replace("-", " "))))
    }

    @Test(expected = BackupCrypto.WrongSecret::class)
    fun wrongPassphrase() {
        val keys = BackupCrypto.setup("giusta".toCharArray(), BackupCrypto.newRecoveryCode(), fast)
        BackupCrypto.unlock(BackupCrypto.parse(BackupCrypto.encrypt(keys, 0, byteArrayOf(1))).header, "sbagliata")
    }

    @Test(expected = BackupCrypto.BadFile::class)
    fun tamperedFileIsRefused() {
        val keys = BackupCrypto.setup("frase".toCharArray(), BackupCrypto.newRecoveryCode(), fast)
        val file = BackupCrypto.encrypt(keys, 0, ByteArray(100) { it.toByte() })
        file[file.size - 20] = (file[file.size - 20] + 1).toByte()
        BackupCrypto.decrypt(BackupCrypto.parse(file), keys.dataKey)
    }

    @Test
    fun recoveryCodeShape() {
        val code = BackupCrypto.newRecoveryCode()
        assertEquals(29, code.length)
        assertEquals(24, BackupCrypto.normalizeCode(code).length)
    }

    private fun f(day: Int, tier: Tier = Tier.Recent): BackupFile {
        val at = LocalDateTime.of(2026, 1, 1, 3, 0).plusDays(day.toLong())
        return BackupFile(Retention.fileName(at, tier), at, tier)
    }

    @Test
    fun namesRoundTrip() {
        val b = f(5, Tier.Monthly)
        assertEquals(b, Retention.parse(b.name))
    }

    @Test
    fun dailyForAYearKeepsThreeRecentOneMonthlyOneQuarterly() {
        var files = emptyList<BackupFile>()
        for (day in 0 until 365) {
            files = Retention.apply(files + f(day), Retention.plan(files + f(day)))
            assertTrue(files.size <= 5)
        }
        val recents = files.filter { it.tier == Tier.Recent }
        val monthly = files.single { it.tier == Tier.Monthly }
        val quarterly = files.single { it.tier == Tier.Quarterly }
        assertEquals(3, recents.size)
        val newest = recents.maxOf { it.at }
        // Il mensile ha tra 3 e 34 giorni, il trimestrale tra un mese e quattro.
        assertTrue(java.time.Duration.between(monthly.at, newest).toDays() in 3..34)
        assertTrue(java.time.Duration.between(quarterly.at, newest).toDays() in 30..125)
    }

    @Test
    fun theNewOneReplacesTheOldestRecent() {
        val files = listOf(f(0), f(1), f(2), f(3))
        val plan = Retention.plan(files)
        // Primo giro: il più vecchio diventa il mensile (non ce n'era uno).
        assertEquals(listOf(RetentionAction.Rename(f(0).name, f(0, Tier.Monthly).name)), plan)
        val after = Retention.apply(files, plan) + f(4)
        assertEquals(listOf(RetentionAction.Delete(f(1).name)), Retention.plan(after))
    }
}
