package eu.eurise.core.ponte

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PonteTest {

    private val phone = Identity.generate()
    private val laptop = Identity.generate()
    private val secret = Pairing.newSecret()

    @Test
    fun pairingRoundTrip() {
        val p = Pairing("pc-1", "Portatile di Marco · Ubuntu", laptop.publicBytes, secret, "192.168.1.20", Wire.DEFAULT_PORT)
        val back = Pairing.parse(p.toUri())
        assertEquals(p, back)
        assertNull(Pairing.parse("https://eurise.eu"))
        assertNull(Pairing.parse(p.toUri().replace("v=1", "v=9")))
        assertNull(Pairing.parse(Pairing.SCHEME + "?v=1&i=x&n=y"))
    }

    @Test
    fun bothSidesShareTheSameKey() {
        val a = Link.between(phone.keys.private, laptop.keys.public, secret)
        val b = Link.between(laptop.keys.private, phone.keys.public, secret)
        val sealed = a.seal("ciao".toByteArray(), "phone".toByteArray())
        assertArrayEquals("ciao".toByteArray(), b.open(sealed, "phone".toByteArray()))
        // Mittente diverso nei dati autenticati, messaggio toccato o segreto diverso: non si apre.
        assertNull(b.open(sealed, "laptop".toByteArray()))
        assertNull(b.open(sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }, "phone".toByteArray()))
        val other = Link.between(laptop.keys.private, phone.keys.public, Pairing.newSecret())
        assertNull(other.open(sealed, "phone".toByteArray()))
    }

    @Test
    fun identityRestores() {
        val restored = Identity.restore(phone.publicBytes, phone.privateBytes())
        val a = Link.between(restored.keys.private, laptop.keys.public, secret)
        val b = Link.between(laptop.keys.private, phone.keys.public, secret)
        assertArrayEquals("x".toByteArray(), b.open(a.seal("x".toByteArray())))
    }

    @Test
    fun confirmCodeIsTheSameOnBothScreens() {
        val c1 = confirmCode(phone.publicBytes, laptop.publicBytes, secret)
        val c2 = confirmCode(laptop.publicBytes, phone.publicBytes, secret)
        assertEquals(c1, c2)
        assertEquals(6, c1.length)
        assertNotEquals(c1, confirmCode(phone.publicBytes, Identity.generate().publicBytes, secret))
    }

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = Link.hkdf(ikm, salt, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun messagesAndJson() {
        val m = Message(Kind.Handoff, "m1", 1_000, mapOf("url" to "https://eurise.eu/?a=1&b=\"2\"", "title" to "Prova\nriga"))
        assertEquals(m, Message.decode(m.encode()))
        assertNull(Message.decode("{\"t\":\"sconosciuto\",\"id\":\"1\",\"at\":\"1\"}"))
        assertNull(Json.read("{\"a\":\"b\""))
        assertEquals(mapOf("a" to "é\u0001"), Json.read(Json.write(mapOf("a" to "é\u0001"))))
        assertEquals(emptyMap<String, String>(), Json.read("{ }"))
    }

    @Test
    fun channelRejectsReplayAndOldMessages() {
        val a = Channel(Link.between(phone.keys.private, laptop.keys.public, secret), me = "phone")
        val b = Channel(Link.between(laptop.keys.private, phone.keys.public, secret), me = "laptop")
        val now = 1_700_000_000_000L
        val m = Message(Kind.Clipboard, "c1", now, mapOf("text" to "IT60 X054"))
        val sealed = a.seal(m)
        assertEquals(m, b.open(sealed, from = "phone", now = now + 2_000))
        assertNull(b.open(sealed, from = "phone", now = now + 3_000)) // rimandato
        val old = a.seal(m.copy(id = "c2", time = now - 3_600_000))
        assertNull(b.open(old, from = "phone", now = now))
    }

    @Test
    fun framesAndMailbox() {
        val out = ByteArrayOutputStream()
        Wire.writeFrame(out, byteArrayOf(1, 2, 3))
        Wire.writeFrame(out, byteArrayOf(4))
        val input = ByteArrayInputStream(out.toByteArray())
        assertArrayEquals(byteArrayOf(1, 2, 3), Wire.readFrame(input))
        assertArrayEquals(byteArrayOf(4), Wire.readFrame(input))
        assertNull(Wire.readFrame(input))
        val bad = ByteArrayInputStream(byteArrayOf(0x7f, 0, 0, 0))
        assertTrue(runCatching { Wire.readFrame(bad) }.isFailure)
        val path = Wire.mailboxPath("/Eurise/Ponte/", "pc/../1", Message(Kind.Notify, "a b", 42))
        assertEquals("/Eurise/Ponte/pc1/0000000000042-ab.msg", path)
        assertFalse(path.contains(".."))
    }
}

class HandshakeTest {
    @Test
    fun pairingNeedsTheSecretFromTheQr() {
        val phone = Identity.generate()
        val secret = Pairing.newSecret()
        val open = Opening.pair("tel-1", "Fairphone", phone.publicBytes, secret)
        val back = Opening.decode(open.encode()) as Opening.Pair
        assertEquals(open, back)
        assertTrue(back.verify(secret))
        assertFalse(back.verify(Pairing.newSecret()))
        assertEquals(Opening.Hi("tel-1"), Opening.decode(Opening.Hi("tel-1").encode()))
        assertNull(Opening.decode("{\"t\":\"boh\"}".toByteArray()))
    }
}
