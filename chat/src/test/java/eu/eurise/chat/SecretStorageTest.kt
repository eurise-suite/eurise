package eu.eurise.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretStorageTest {
    @Test fun specExampleKeyDecodes() {
        // L'esempio della specifica Matrix (rappresentazione della chiave): prefisso e parità tornano.
        val key = SecretStorage.decodeRecoveryKey("EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d")
        assertNotNull(key)
        assertEquals(32, key!!.size)
        assertEquals("EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d", SecretStorage.encodeRecoveryKey(key))
    }

    @Test fun recoveryKeyRoundTrip() {
        repeat(20) {
            val key = SecretStorage.newKey()
            val text = SecretStorage.encodeRecoveryKey(key)
            assertEquals(12, text.split(" ").size)
            assertArrayEquals(key, SecretStorage.decodeRecoveryKey(text.replace(" ", "\n")))
        }
    }

    @Test fun typosAreRejected() {
        assertNull(SecretStorage.decodeRecoveryKey("EsTc LW2M PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d"))
        assertNull(SecretStorage.decodeRecoveryKey("ciao"))
        assertNull(SecretStorage.decodeRecoveryKey("0OIl"))
    }

    @Test fun secretsRoundTripAndKeyCheck() {
        val key = SecretStorage.newKey()
        val desc = SecretStorage.keyDescription(key, "Eurise")
        assertTrue(SecretStorage.checkKey(key, desc))
        assertFalse(SecretStorage.checkKey(SecretStorage.newKey(), desc))
        val enc = SecretStorage.encrypt(key, SecretStorage.MASTER, "seme-della-chiave")
        assertEquals("seme-della-chiave", SecretStorage.decrypt(key, SecretStorage.MASTER, enc))
        // Il nome fa parte della chiave: con un altro nome il mac non torna.
        assertNull(SecretStorage.decrypt(key, SecretStorage.SELF_SIGNING, enc))
        assertNull(SecretStorage.decrypt(SecretStorage.newKey(), SecretStorage.MASTER, enc))
    }
}
