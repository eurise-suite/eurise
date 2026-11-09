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

    @Test fun cardSurvivesTheFile() {
        val file = Kdbx.create(key, "Eurise")
        val card = eu.eurise.password.kdbx.CardData("4111 1111 1111 1111", "M R", "09/29", "123", "Debito")
        val id = vault(file).save(EntryDraft("Conto", "", "", "", "", null, card), null)
        val back = vault(Kdbx.read(Kdbx.write(file), key)).entries().single { it.uuid == id }
        assertEquals(card, back.card)
    }

    @Test fun cardChecks() {
        val c = eu.eurise.password.logic.Cards
        assertTrue(c.valid("4111 1111 1111 1111"))
        assertFalse(c.valid("4111 1111 1111 1112"))
        assertEquals("1111", c.lastFour("4111 1111 1111 1111"))
        val today = java.time.LocalDate.of(2026, 11, 9)
        assertEquals(2, c.monthsLeft("01/27", today))
        assertTrue(c.expiringSoon("12/26", today))
        assertFalse(c.expiringSoon("01/27", today))
    }

    @Test fun appLinkIsKept() {
        val file = Kdbx.create(key, "Eurise")
        val v = vault(file)
        val id = v.save(draft, null)
        v.linkApp(id, "it.esempio.app")
        v.linkApp(id, "it.esempio.app")
        assertEquals(listOf("it.esempio.app"), vault(Kdbx.read(Kdbx.write(file), key)).entries().single().apps)
    }

    @Test fun domainCheckRefusesLookAlikes() {
        val d = eu.eurise.password.autofill.Matching
        assertTrue(d.domainMatches("https://murena.io/login", "murena.io"))
        assertTrue(d.domainMatches("https://www.murena.io", "login.murena.io"))
        assertTrue(d.domainMatches("murena.io", "www.murena.io"))
        assertFalse(d.domainMatches("https://murena.io", "murena.io.truffa.com"))
        assertFalse(d.domainMatches("https://murena.io", "falsomurena.io"))
        assertFalse(d.domainMatches("https://login.murena.io", "murena.io"))
        assertFalse(d.domainMatches("", "murena.io"))
    }

    @Test fun formFieldsAreRecognised() {
        val f = eu.eurise.password.autofill.Matching
        val k = eu.eurise.password.autofill.FieldKind.entries.associateBy { it.name }
        assertEquals(k["Password"], f.classify(listOf("password"), emptyMap(), null, 0))
        assertEquals(k["NewPassword"], f.classify(emptyList(), mapOf("autocomplete" to "new-password", "type" to "password"), null, 0))
        assertEquals(k["Password"], f.classify(emptyList(), emptyMap(), "edit", 0x81))
        assertEquals(k["CardNumber"], f.classify(emptyList(), mapOf("autocomplete" to "cc-number"), null, 0))
        assertEquals(k["CardCvc"], f.classify(emptyList(), mapOf("name" to "cvv"), null, 0))
        assertEquals(k["Username"], f.classify(emptyList(), mapOf("type" to "email"), null, 0))
        assertEquals(null, f.classify(emptyList(), mapOf("name" to "cerca"), null, 1))
    }

    @Test fun usernameIsTheFieldBeforeThePassword() {
        val f = eu.eurise.password.autofill.Matching
        val kinds = f.fillUsername(listOf(null, null, eu.eurise.password.autofill.FieldKind.Password))
        assertEquals(eu.eurise.password.autofill.FieldKind.Username, kinds[1])
        assertEquals(null, kinds[0])
    }

    private val passJson = """{"version":"1.2","userId":"u","vaults":{"s1":{"name":"V","items":[
      {"itemId":"a","state":1,"aliasEmail":null,"data":{"type":"login","metadata":{"name":"Murena","note":"n"},"extraFields":[{"fieldName":"PIN","type":"hidden","data":{"content":"77"}}],"content":{"itemEmail":"io@x.it","itemUsername":"","password":"segreta-lunga","urls":["https://murena.io/","https://altro.it/"],"totpUri":"otpauth://totp/M?secret=GEZDGNBV","passkeys":[{"k":1}]}}},
      {"itemId":"b","state":2,"data":{"type":"login","metadata":{"name":"Cestinata"},"content":{"password":"x"}}},
      {"itemId":"c","state":1,"data":{"type":"creditCard","metadata":{"name":"Conto","note":""},"extraFields":[],"content":{"cardholderName":"M R","number":"4111111111111111","verificationNumber":"123","expirationDate":"2028-03","pin":"9"}}},
      {"itemId":"d","state":1,"data":{"type":"note","metadata":{"name":"Appunto","note":"testo"},"content":{}}},
      {"itemId":"e","state":1,"aliasEmail":"a.b@passmail.net","data":{"type":"alias","metadata":{"name":"Negozio","note":""},"content":{}}},
      {"itemId":"f","state":1,"data":{"type":"wifi","metadata":{"name":"Casa"},"content":{}}}
    ]}}}"""

    private fun ready(text: String) = eu.eurise.password.logic.Importer.parse(text) as eu.eurise.password.logic.ImportOutcome.Ready

    @Test fun protonPassExport() {
        val r = ready(passJson)
        assertEquals(1, r.skipped)
        assertEquals(1, r.passkeys)
        assertEquals(listOf("Murena", "Conto", "Appunto", "Negozio"), r.items.map { it.draft.title })
        val login = r.items[0].draft
        assertEquals("io@x.it", login.username)
        assertEquals("https://murena.io/", login.url)
        assertTrue(login.notes.contains("PIN: 77") && login.notes.contains("https://altro.it/"))
        assertEquals("03/28", r.items[1].draft.card!!.expiry)
        assertEquals("a.b@passmail.net", r.items[3].draft.username)
    }

    @Test fun protonPassInsideZip() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("Proton Pass/data.json")); z.write(passJson.toByteArray()); z.closeEntry() }
        val r = eu.eurise.password.logic.Importer.read(out.toByteArray().inputStream()) as eu.eurise.password.logic.ImportOutcome.Ready
        assertEquals(4, r.items.size)
    }

    @Test fun encryptedZipIsExplained() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("Proton Pass/data.pgp")); z.write(1); z.closeEntry() }
        assertEquals(eu.eurise.password.logic.ImportOutcome.Encrypted, eu.eurise.password.logic.Importer.read(out.toByteArray().inputStream()))
    }

    @Test fun authenticatorFormats() {
        val aegis = ready("""{"version":1,"db":{"entries":[{"type":"totp","name":"io","issuer":"Murena","info":{"secret":"GEZD","algo":"SHA256","digits":6,"period":30}},{"type":"hotp","name":"x","info":{}}]}}""")
        assertEquals("Murena", aegis.items.single().draft.title)
        assertEquals(1, aegis.skipped)
        assertTrue(aegis.items.single().draft.otp!!.contains("algorithm=SHA256"))
        val proton = ready("""{"version":1,"entries":[{"id":"1","content":{"uri":"otpauth://totp/Murena%3Aio%40x.it?secret=GEZD&issuer=Murena","name":"Murena"},"note":""}]}""")
        assertEquals("Murena", proton.items.single().draft.title)
        assertEquals("io@x.it", proton.items.single().draft.username)
        val fas = ready("""{"services":[{"name":"Sito","secret":"GEZD","otp":{"account":"io","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP"}}]}""")
        assertEquals("io", fas.items.single().draft.username)
        assertEquals(1, ready("otpauth://totp/A:b?secret=GEZD\n").items.size)
    }

    @Test fun importTwiceAddsNothingAndCodesJoinTheirLogin() {
        val file = Kdbx.create(key, "Eurise")
        val v = vault(file)
        val drafts = ready(passJson).items.map { it.draft }
        assertEquals(eu.eurise.password.kdbx.ImportCounts(4, 0, 0), v.importAll(drafts))
        assertEquals(eu.eurise.password.kdbx.ImportCounts(0, 0, 4), v.importAll(drafts))
        v.save(EntryDraft("Infomaniak", "io", "password-molto-lunga", "https://infomaniak.com", "", null), null)
        val code = ready("otpauth://totp/Infomaniak:io?secret=GEZDGNBV&issuer=Infomaniak").items.map { it.draft }
        assertEquals(eu.eurise.password.kdbx.ImportCounts(0, 1, 0), v.importAll(code))
        assertEquals(eu.eurise.password.kdbx.ImportCounts(0, 0, 1), v.importAll(code))
        assertNotNull(v.entries().single { it.title == "Infomaniak" }.otp)
        assertEquals(5, vault(Kdbx.read(Kdbx.write(file), key)).entries().size)
    }
}
