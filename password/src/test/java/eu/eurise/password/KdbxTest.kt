package eu.eurise.password

import eu.eurise.password.kdbx.Argon2
import eu.eurise.password.kdbx.Blake2b
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.Kdbx
import eu.eurise.password.kdbx.KdbxException
import eu.eurise.password.kdbx.Vault
import eu.eurise.password.logic.Generator
import eu.eurise.password.logic.Strength
import eu.eurise.password.logic.Totp
import eu.eurise.password.logic.TotpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KdbxTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private val fixture get() = javaClass.getResource("/prova.kdbx")!!.readBytes()
    private val key = Kdbx.compositeKey("prova-eurise")
    private fun vault(file: eu.eurise.password.kdbx.KdbxFile) = Vault(file.document)
    private val draft = EntryDraft("Sito", "io", "S3greta&<x>", "https://sito.it", "nota", null)

    @Test fun blake2bAbc() = assertEquals(
        "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
        hex(Blake2b.hash(64, "abc".toByteArray())),
    )

    private fun rfc(type: Int) = hex(
        Argon2.hash(type, ByteArray(32) { 1 }, ByteArray(16) { 2 }, 3, 32, 4, 32, ByteArray(8) { 3 }, ByteArray(12) { 4 }),
    )

    @Test fun argon2dRfc9106() = assertEquals("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb", rfc(Argon2.TYPE_D))
    @Test fun argon2idRfc9106() = assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", rfc(Argon2.TYPE_ID))

    @Test fun readsFileMadeByAnotherProgram() {
        val entries = vault(Kdbx.read(fixture, key)).entries().associateBy { it.title }
        assertEquals(setOf("Murena", "Biblioteca"), entries.keys)
        val m = entries.getValue("Murena")
        assertEquals("nome@murena.io", m.username)
        assertEquals("Pàssw0rd&<segreta>", m.password)
        assertEquals("https://murena.io", m.url)
        assertEquals("Busta blu", m.notes)
        assertTrue(m.otp!!.startsWith("otpauth://"))
        assertEquals("altra-password", entries.getValue("Biblioteca").password)
    }

    @Test fun wrongPasswordIsRecognised() {
        assertThrows(KdbxException.WrongPassword::class.java) { Kdbx.read(fixture, Kdbx.compositeKey("sbagliata")) }
    }

    @Test fun alteredFileIsRefused() {
        val bytes = fixture.also { it[it.size - 40] = (it[it.size - 40] + 1).toByte() }
        assertThrows(KdbxException::class.java) { Kdbx.read(bytes, key) }
    }

    @Test fun roundTripKeepsEverythingAndHistory() {
        val file = Kdbx.read(fixture, key)
        val v = vault(file)
        val id = v.save(draft, null, 1_000_000)
        v.save(draft.copy(password = "nuova-password"), id, 2_000_000)
        val again = vault(Kdbx.read(Kdbx.write(file), key)).entries().associateBy { it.uuid }
        assertEquals(3, again.size)
        assertEquals("nuova-password", again.getValue(id).password)
        assertEquals(1, again.getValue(id).historyCount)
        assertEquals("Pàssw0rd&<segreta>", again.values.first { it.title == "Murena" }.password)
    }

    @Test fun newVaultOpensAndIsLeftForInterop() {
        val file = Kdbx.create(key, "Eurise")
        vault(file).save(draft.copy(otp = "otpauth://totp/x?secret=GEZDGNBV"), null)
        val bytes = Kdbx.write(file)
        assertEquals("S3greta&<x>", vault(Kdbx.read(bytes, key)).entries().single().password)
        File("build/interop").apply { mkdirs() }.resolve("eurise.kdbx").writeBytes(bytes)
    }

    @Test fun deleteGoesToBin() {
        val file = Kdbx.create(key, "Eurise")
        val v = vault(file)
        val id = v.save(draft, null)
        v.delete(id)
        assertTrue(v.entries().isEmpty())
        assertTrue(vault(Kdbx.read(Kdbx.write(file), key)).entries().isEmpty())
    }

    private fun twoCopies(): Pair<Vault, Vault> {
        val file = Kdbx.create(key, "Eurise")
        Vault(file.document).save(draft, null, 1_000_000)
        val bytes = Kdbx.write(file)
        return vault(Kdbx.read(bytes, key)) to vault(Kdbx.read(bytes, key))
    }

    @Test fun mergeNewerWinsAndOlderGoesToHistory() {
        val (here, there) = twoCopies()
        val id = here.entries().single().uuid
        there.save(draft.copy(password = "dal-pc"), id, 5_000_000)
        val result = here.mergeFrom(there)
        assertTrue(result.localChanged)
        assertFalse(result.remoteBehind)
        assertEquals("dal-pc", here.entries().single().password)
        assertEquals(1, here.entries().single().historyCount)
    }

    @Test fun mergeAddsEntriesFromBothSides() {
        val (here, there) = twoCopies()
        here.save(draft.copy(title = "Qui"), null, 3_000_000)
        there.save(draft.copy(title = "La"), null, 4_000_000)
        val result = here.mergeFrom(there)
        assertTrue(result.localChanged)
        assertTrue(result.remoteBehind)
        assertEquals(setOf("Sito", "Qui", "La"), here.entries().map { it.title }.toSet())
    }

    @Test fun mergeOfEqualCopiesDoesNothing() {
        val (here, there) = twoCopies()
        assertEquals(eu.eurise.password.kdbx.MergeResult(false, false), here.mergeFrom(there))
    }

    @Test fun totpRfc6238() {
        val config = TotpConfig("12345678901234567890".toByteArray(), digits = 8)
        assertEquals("94287082", config.code(59_000))
        assertEquals("07081804", config.code(1_111_111_109_000))
    }

    @Test fun base32() = assertEquals("12345678901234567890", String(Totp.base32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")!!))

    @Test fun generatorWords() {
        assertEquals(256, Generator.WORDS.toSet().size)
        assertNotNull(Generator.WORDS.firstOrNull { it == "gufo" })
    }

    @Test fun generatedAreStrong() {
        assertTrue(Generator.strength(Generator.password()) >= Strength.Strong)
        assertTrue(Generator.strength(Generator.passphrase()) >= Strength.Strong)
        assertEquals(Strength.Weak, Generator.strength("ciao123"))
    }

    @Test fun healthFindsReused() {
        val issues = Generator.health(listOf("a" to "stessa-Password-lunga-9", "b" to "stessa-Password-lunga-9", "c" to "", "d" to "x"))
        assertEquals(setOf("a", "b", "d"), issues.map { it.uuid }.toSet())
    }
}
