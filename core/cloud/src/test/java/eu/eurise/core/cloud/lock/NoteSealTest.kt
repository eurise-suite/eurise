package eu.eurise.core.cloud.lock

import eu.eurise.core.cloud.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSealTest {
    private val fast = 1_000
    private val code = BackupCrypto.newRecoveryCode()
    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun copyOpensWithPassphraseOrCodeOnly() {
        val pair = NoteSeal.newPair("frase del backup".toCharArray(), code, fast)
        val entry = NoteSeal.seal(pair, key)
        assertArrayEquals(key, NoteSeal.open(entry, "frase del backup"))
        assertArrayEquals(key, NoteSeal.open(entry, code.lowercase().replace("-", " ")))
        assertNull(NoteSeal.open(entry, "frase sbagliata"))
        // La copia non contiene la chiave in chiaro.
        assertFalse(entry.toList().windowed(32).any { it.toByteArray().contentEquals(key) })
    }

    @Test
    fun newPassphraseKeepsCodeAndOldCopies() {
        val pair = NoteSeal.newPair("vecchia".toCharArray(), code, fast)
        val changed = NoteSeal.rewrapPassphrase(pair, "vecchia", "nuova".toCharArray())!!
        val entry = NoteSeal.seal(changed, key)
        assertArrayEquals(key, NoteSeal.open(entry, "nuova"))
        assertNull(NoteSeal.open(entry, "vecchia"))
        assertArrayEquals(key, NoteSeal.open(entry, code))
        // Anche con il codice al posto della frase attuale.
        assertNotNull(NoteSeal.rewrapPassphrase(pair, code, "terza".toCharArray()))
        assertNull(NoteSeal.rewrapPassphrase(pair, "sbagliata", "x".toCharArray()))
    }

    @Test
    fun copyFollowsNewPassphraseWithoutTheKey() {
        val pair = NoteSeal.newPair("vecchia".toCharArray(), code, fast)
        val entry = NoteSeal.seal(pair, key)
        val changed = NoteSeal.rewrapPassphrase(pair, "vecchia", "nuova".toCharArray())!!
        val moved = NoteSeal.rewrapCopy(entry, changed)!!
        assertArrayEquals(key, NoteSeal.open(moved, "nuova"))
        assertNull(NoteSeal.open(moved, "vecchia"))
        assertArrayEquals(key, NoteSeal.open(moved, code))
        // Una coppia diversa (backup rifatto da capo) non prende una copia che non è sua.
        assertNull(NoteSeal.rewrapCopy(entry, NoteSeal.newPair("altra".toCharArray(), code, fast)))
    }

    @Test
    fun pairSurvivesSavingAndRestore() {
        val pair = NoteSeal.newPair("frase".toCharArray(), code, fast)
        val back = NoteSeal.decodePair(NoteSeal.encodePair(pair))!!
        assertTrue(NoteSeal.opens(back, "frase"))
        val entry = NoteSeal.seal(back, key)
        val restored = NoteSeal.pairOf(entry)!!
        assertArrayEquals(key, NoteSeal.open(NoteSeal.seal(restored, key), code))
    }

    @Test
    fun tamperedCopyIsRefused() {
        val entry = NoteSeal.seal(NoteSeal.newPair("frase".toCharArray(), code, fast), key)
        entry[entry.size - 5] = (entry[entry.size - 5] + 1).toByte()
        assertNull(NoteSeal.open(entry, "frase"))
        assertNull(NoteSeal.open(ByteArray(10), "frase"))
    }
}
