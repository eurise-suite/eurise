package eu.eurise.core.suite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateInboxTest {
    @Test
    fun sealAndOpen() {
        val pair = PrivateInbox.newKeyPair()
        val photo = ByteArray(5000) { (it * 7).toByte() }
        val sealed = PrivateInbox.seal(PrivateInbox.publicKey(pair.public.encoded), PrivateInbox.Item("IMG_1.jpg", 42L, "image/jpeg", photo))
        val back = PrivateInbox.open(PrivateInbox.privateKey(pair.private.encoded), sealed)
        assertEquals("IMG_1.jpg", back.name)
        assertEquals(42L, back.takenAt)
        assertArrayEquals(photo, back.bytes)
    }

    @Test
    fun anotherKeyOrATouchFails() {
        val pair = PrivateInbox.newKeyPair()
        val sealed = PrivateInbox.seal(pair.public, PrivateInbox.Item("a.jpg", 1L, "image/jpeg", ByteArray(100)))
        assertTrue(runCatching { PrivateInbox.open(PrivateInbox.newKeyPair().private, sealed) }.isFailure)
        val touched = sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20] + 1).toByte() }
        assertTrue(runCatching { PrivateInbox.open(pair.private, touched) }.isFailure)
    }

    @Test
    fun onlyUuidsAsNames() {
        assertTrue(PrivateInbox.validId("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(PrivateInbox.validId("../header.json"))
        assertFalse(PrivateInbox.validId("123e4567-e89b-12d3-a456-426614174000/../x"))
        assertFalse(PrivateInbox.validId(null))
    }
}
