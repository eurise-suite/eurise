package eu.eurise.core.cloud.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class CryptomatorTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun scryptMatchesAReferenceValue() {
        assertArrayEquals(hex("f5dfb3972e7908b22410c5c5f3788907cdbd1a79971b12277502bd4a77e6d5d3"), Cryptomator.scrypt("password".toByteArray(), "NaCl".toByteArray(), 16, 8, 32))
    }

    @Test
    fun keyWrapMatchesRfc3394() {
        val kek = hex("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F")
        val key = hex("00112233445566778899AABBCCDDEEFF000102030405060708090A0B0C0D0E0F")
        val wrapped = hex("28C9F404C4B810F4CBCCB35CFB87F8263F5786E2D80ED326CBC7F0E71A99F43BFB988B9B7A02DD21")
        assertArrayEquals(wrapped, Cryptomator.aesWrap(kek, key))
        assertArrayEquals(key, Cryptomator.aesUnwrap(kek, wrapped))
    }

    @Test
    fun sivMatchesRfc5297() {
        val mac = hex("fffefdfcfbfaf9f8f7f6f5f4f3f2f1f0")
        val ctr = hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val ad = hex("101112131415161718191a1b1c1d1e1f2021222324252627")
        val plain = hex("112233445566778899aabbccddee")
        val out = hex("85632d07c6e8f37f950acd320a2ecc9340c02b9690c4dc04daef7f6afe5c")
        assertArrayEquals(out, Cryptomator.sivEncrypt(ctr, mac, plain, ad))
        assertArrayEquals(plain, Cryptomator.sivDecrypt(ctr, mac, out, ad))
    }

    private fun seal(v: Cryptomator.Vault, plain: ByteArray) = ByteArrayOutputStream().also { v.encrypt(ByteArrayInputStream(plain), it) }.toByteArray()
    private fun open(v: Cryptomator.Vault, data: ByteArray) = ByteArrayOutputStream().also { v.decrypt(ByteArrayInputStream(data), it) }.toByteArray()

    @Test
    fun vaultOpensOnlyWithItsPassword() {
        val made = Cryptomator.create("prova-eurise")
        val again = Cryptomator.open(made.config, made.masterkey, "prova-eurise")
        assertEquals(made.vault.dirPath(""), again.dirPath(""))
        assertThrows(Cryptomator.WrongPassword::class.java) { Cryptomator.open(made.config, made.masterkey, "sbagliata") }
    }

    @Test
    fun contentRoundTripsAndSizesAreRight() {
        val v = Cryptomator.create("prova-eurise").vault
        for (size in listOf(0, 1, 32767, 32768, 32769, 70000)) {
            val plain = ByteArray(size) { (it * 7).toByte() }
            val sealed = seal(v, plain)
            assertArrayEquals(plain, open(v, sealed))
            assertEquals(size.toLong(), Cryptomator.cleartextSize(sealed.size.toLong()))
        }
    }

    @Test
    fun tamperingAndWrongFolderAreRejected() {
        val v = Cryptomator.create("prova-eurise").vault
        val sealed = seal(v, ByteArray(100) { 1 })
        sealed[90] = (sealed[90] + 1).toByte()
        assertThrows(Cryptomator.Corrupted::class.java) { open(v, sealed) }
        val name = v.encryptName("foto.jpg", "a")
        assertEquals("foto.jpg", v.decryptName(name, "a"))
        assertThrows(Cryptomator.Corrupted::class.java) { v.decryptName(name, "b") }
        assertNotEquals(v.dirPath(""), v.dirPath("a"))
        assertTrue(v.dirPath("a").matches(Regex("d/[A-Z2-7]{2}/[A-Z2-7]{30}")))
    }

    /** Scrive una cartella vera: la compilazione la fa aprire a un altro programma (pycryptomator). */
    @Test
    fun writesAVaultForTheInteropCheck() {
        val base = File("build/interop-vault").apply { deleteRecursively(); mkdirs() }
        val made = Cryptomator.create("prova-eurise")
        val v = made.vault
        base.resolve(Cryptomator.CONFIG_FILE).writeText(made.config)
        base.resolve(Cryptomator.MASTERKEY_FILE).writeText(made.masterkey)
        val root = base.resolve(v.dirPath("")).apply { mkdirs() }
        root.resolve(v.encryptName("ciao.txt", "")).writeBytes(seal(v, "ciao da Eurise".toByteArray()))
        root.resolve(v.encryptName("nota.md", "")).writeBytes(seal(v, "nome con riempimento".toByteArray()))
        val long = "a".repeat(200) + ".txt"
        val folder = root.resolve(v.shortened(v.encryptName(long, ""))).apply { mkdirs() }
        folder.resolve("name.c9s").writeText(v.encryptName(long, ""))
        folder.resolve("contents.c9r").writeBytes(seal(v, "nome lungo".toByteArray()))
        val id = "11111111-2222-3333-4444-555555555555"
        root.resolve(v.encryptName("sotto", "")).apply { mkdirs() }.resolve("dir.c9r").writeText(id)
        base.resolve(v.dirPath(id)).apply { mkdirs() }.resolve(v.encryptName("dati.bin", id)).writeBytes(seal(v, ByteArray(70000) { (it % 251).toByte() }))
    }
}
