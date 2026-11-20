package eu.eurise.desktop

import eu.eurise.core.ponte.Identity
import eu.eurise.core.ponte.Json
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KeyringTest {
    /** Portachiavi finto in memoria; [closed] simula la finestra annullata. */
    private class Fake(var closed: Boolean = false) : SecretStore {
        val items = HashMap<Map<String, String>, ByteArray>()
        override fun lookup(attributes: Map<String, String>) = if (closed) throw KeyringClosed("chiuso") else items[attributes]
        override fun store(label: String, attributes: Map<String, String>, secret: ByteArray) {
            if (closed) throw KeyringClosed("chiuso")
            items[attributes] = secret.copyOf()
        }
    }

    private fun dir() = Files.createTempDirectory("ponte").toFile()
    private val write: (File, String) -> Unit = { f, t -> f.writeText(t) }

    @Test fun newKeyGoesToKeyringAndFileKeepsOnlyThePublicPart() {
        val d = dir(); val ks = Fake()
        val id = PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write)
        val saved = Json.read(File(d, "chiavi").readText())!!
        assertNull(saved["priv"])
        assertEquals("portachiavi", saved["dove"])
        assertEquals(1, ks.items.size)
        val again = PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write)
        assertArrayEquals(id.privateBytes(), again.privateBytes())
    }

    @Test fun oldFileKeyMovesToKeyringWithoutChangingTheKey() {
        val d = dir(); val old = Identity.generate()
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
        File(d, "chiavi").writeText(Json.write(mapOf("pub" to b64.encodeToString(old.publicBytes), "priv" to b64.encodeToString(old.privateBytes()))))
        val ks = Fake()
        val loaded = PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write)
        assertArrayEquals(old.privateBytes(), loaded.privateBytes())
        assertTrue(!File(d, "chiavi").readText().contains("priv"))
        assertEquals("portachiavi", PonteKey(File(d, "chiavi"), "pc1") { ks }.where())
    }

    @Test fun withoutKeyringTheKeyStaysInTheFile() {
        val d = dir()
        val id = PonteKey(File(d, "chiavi"), "pc1") { null }.load(write)
        assertEquals("file", PonteKey(File(d, "chiavi"), "pc1") { null }.where())
        assertArrayEquals(id.privateBytes(), PonteKey(File(d, "chiavi"), "pc1") { null }.load(write).privateBytes())
    }

    @Test fun closedKeyringNeverReplacesTheKey() {
        val d = dir(); val ks = Fake()
        PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write)
        val before = File(d, "chiavi").readText()
        ks.closed = true
        try { PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write); fail() } catch (_: KeyringClosed) {}
        try { PonteKey(File(d, "chiavi"), "pc1") { null }.load(write); fail() } catch (_: KeyringClosed) {}
        assertEquals(before, File(d, "chiavi").readText())
    }

    @Test fun keyRemovedFromKeyringIsAnErrorNotANewKey() {
        val d = dir(); val ks = Fake()
        PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write)
        ks.items.clear()
        try { PonteKey(File(d, "chiavi"), "pc1") { ks }.load(write); fail() } catch (e: IOException) { assertTrue(e !is KeyringClosed) }
    }

    @Test fun anotherKeyInTheKeyringIsNeverOverwritten() {
        val ks = Fake()
        val first = PonteKey(File(dir(), "chiavi"), "pc1") { ks }.load(write)
        val other = dir()
        val second = PonteKey(File(other, "chiavi"), "pc1") { ks }.load(write)
        assertEquals("file", PonteKey(File(other, "chiavi"), "pc1") { ks }.where())
        assertArrayEquals(first.privateBytes(), String(ks.items.values.single()).let(java.util.Base64.getUrlDecoder()::decode))
        assertTrue(!first.privateBytes().contentEquals(second.privateBytes()))
    }

    @Test fun dbusValuesRoundTrip() {
        val sig = "a{sv}(oayays)bs"
        val values = listOf(
            mapOf("a" to Variant("s", "x"), "b" to Variant("a{ss}", mapOf("k" to "v"))),
            listOf("/s/1", ByteArray(0), byteArrayOf(1, 2, 3), "text/plain"), true, "è",
        )
        val bytes = Marshal().apply { writeAll(sig, values) }.bytes()
        val back = Unmarshal(bytes, ByteOrder.LITTLE_ENDIAN).readAll(sig)
        @Suppress("UNCHECKED_CAST") val m = back[0] as Map<String, Variant>
        assertEquals("x", m["a"]!!.value)
        assertEquals(mapOf("k" to "v"), m["b"]!!.value)
        val st = back[1] as List<*>
        assertArrayEquals(byteArrayOf(1, 2, 3), st[2] as ByteArray)
        assertEquals(true, back[2]); assertEquals("è", back[3])
    }

    @Test fun emptyArrayKeepsElementPadding() {
        // Un array vuoto di strutture: lunghezza 0, poi il riempimento fino a 8 (specifica D-Bus).
        val bytes = Marshal().apply { write("a(yv)", emptyList<Any>()) }.bytes()
        assertEquals(8, bytes.size)
        assertEquals(emptyList<Any>(), Unmarshal(bytes, ByteOrder.LITTLE_ENDIAN).read("a(yv)"))
    }

    @Test fun sessionBusAddress() {
        assertEquals("/run/user/1000/bus", DBus.sessionPath(mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus"))!!.toString())
        assertEquals("/run/flatpak/bus", DBus.sessionPath(mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/flatpak/bus,guid=abc"))!!.toString())
        assertNull(DBus.sessionPath(mapOf("DBUS_SESSION_BUS_ADDRESS" to "unix:abstract=/tmp/dbus-x")))
    }
}
