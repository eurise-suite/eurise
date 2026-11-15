package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BrowserImportTest {
    private val chrome = """{"checksum":"x","roots":{
        "bookmark_bar":{"type":"folder","name":"Barra dei preferiti","children":[
          {"type":"url","name":"Murena","url":"https://murena.io/","date_added":"13370000000000000"},
          {"type":"folder","name":"Lavoro","children":[{"type":"url","name":"PEC è qui","url":"https://pec.it/"}]},
          {"type":"url","name":"Impostazioni","url":"chrome://settings"}]},
        "other":{"type":"folder","name":"Altri preferiti","children":[]},
        "synced":{"type":"folder","name":"Mobile","children":[{"type":"url","name":"","url":"https://inps.it/"}]}},"version":1}"""

    @Test fun segnalibriDiChromium() {
        val b = BrowserImport.chromiumBookmarks(chrome)
        assertEquals(3, b.size)
        assertEquals("Barra dei preferiti", b[0].folder)
        assertEquals("Barra dei preferiti › Lavoro", b[1].folder)
        assertEquals("PEC è qui", b[1].title)
        assertEquals("https://inps.it/", b[2].title)
        assertTrue(b[0].added > 1_700_000_000_000L)
    }

    @Test fun segnalibriDiFirefox() {
        val json = """{"guid":"root________","root":"placesRoot","children":[
          {"title":"toolbar","root":"toolbarFolder","children":[{"title":"Wikipedia","uri":"https://it.wikipedia.org/","dateAdded":1700000000000000}]},
          {"title":"menu","root":"bookmarksMenuFolder","children":[{"title":"Viaggi","children":[{"title":"Trenitalia","uri":"https://www.trenitalia.com/"}]},
            {"title":"Interno","uri":"place:sort=8"}]}]}"""
        val b = BrowserImport.firefoxBookmarks(json)
        assertEquals(listOf("Barra dei segnalibri", "Menu dei segnalibri › Viaggi"), b.map { it.folder })
        assertEquals(1_700_000_000_000L, b[0].added)
    }

    @Test fun profiliTrovati() {
        val home = Files.createTempDirectory("casa").toFile()
        File(home, ".config/BraveSoftware/Brave-Browser/Default").mkdirs()
        File(home, ".config/BraveSoftware/Brave-Browser/Default/Bookmarks").writeText(chrome)
        File(home, ".mozilla/firefox/ab12.default-release/bookmarkbackups").mkdirs()
        val p = BrowserImport.profiles(home)
        assertEquals(listOf("Brave", "Firefox"), p.map { it.browser })
        assertEquals("default-release", p[1].name)
        assertEquals(3, BrowserImport.bookmarks(p[0]).size)
        home.deleteRecursively()
    }

    @Test fun lz4() {
        val lit = byteArrayOf(0x50) + "hello".toByteArray()
        assertEquals("hello", String(MozLz4.lz4Block(lit, 0, 5)))
        val match = byteArrayOf(0x32) + "abc".toByteArray() + byteArrayOf(3, 0)
        assertEquals("abcabcabc", String(MozLz4.lz4Block(match, 0, 9)))
        val file = "mozLz40\u0000".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(5, 0, 0, 0) + lit
        assertEquals("hello", String(MozLz4.decode(file)))
    }

    @Test fun passwordDiChromium() {
        val spec = PBEKeySpec("peanuts".toCharArray(), "saltysalt".toByteArray(), 1, 128)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ByteArray(16) { ' '.code.toByte() }))
        val blob = "v10".toByteArray() + c.doFinal("segreta123".toByteArray())
        assertEquals("segreta123", BrowserImport.decryptChromium(blob, null))
        assertEquals(null, BrowserImport.decryptChromium("v11".toByteArray() + ByteArray(16), null))
    }

    @Test fun htmlDiNetscape() {
        val html = BrowserImport.netscapeHtml(listOf(ImportedBookmark("A & B", "https://a.it/", "Lavoro"), ImportedBookmark("C", "https://c.it/", "")))
        assertTrue(html.contains("<DT><H3>Lavoro</H3>"))
        assertTrue(html.contains(">A &amp; B</A>"))
    }
}
