package eu.eurise.core.ponte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MailboxTest {
    private val phone = Identity.generate()
    private val laptop = Identity.generate()
    private val secret = Pairing.newSecret()
    private val day = 24 * 3600_000L
    private val t0 = 1_800_000_000_000L
    private val msg = Message(Kind.Clipboard, "m1", t0, mapOf("text" to "ciao"))

    private fun announced(keys: MailboxKeys, now: Long) = PeerMailboxKey.from(Message(Kind.Hello, "h", now, keys.announce(now)))!!

    @Test
    fun phoneWritesLaptopReads() {
        val laptopBox = MailboxKeys()
        val file = Mailbox.seal(phone, "tel", laptop.publicBytes.let(Identity::publicKey), secret, announced(laptopBox, t0), msg, t0)!!
        assertEquals(msg, Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, file))
    }

    @Test
    fun onlyThePairedDeviceCanWrite() {
        val laptopBox = MailboxKeys()
        val intruder = Identity.generate()
        val forged = Mailbox.seal(intruder, "tel", Identity.publicKey(laptop.publicBytes), secret, announced(laptopBox, t0), msg, t0)!!
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, forged))
        val good = Mailbox.seal(phone, "tel", Identity.publicKey(laptop.publicBytes), secret, announced(laptopBox, t0), msg, t0)!!
        // Mittente cambiato, segreto sbagliato, un byte dell'intestazione toccato: niente.
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "altro", secret, good))
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", Pairing.newSecret(), good))
        val touched = good.copyOf().also { it[it.size / 3] = (it[it.size / 3] + 1).toByte() }
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, touched))
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, ByteArray(3)))
    }

    @Test
    fun stolenStaticKeysDoNotOpenOldFiles() {
        val laptopBox = MailboxKeys()
        val file = Mailbox.seal(phone, "tel", Identity.publicKey(laptop.publicBytes), secret, announced(laptopBox, t0), msg, t0)!!
        // Quattro settimane e un giorno dopo la chiave della cassetta è distrutta: le chiavi fisse e il QR non bastano.
        laptopBox.current(t0 + 7 * day); laptopBox.current(t0 + 14 * day)
        laptopBox.prune(t0 + 29 * day)
        assertNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, file))
        // Chi copia le chiavi del giorno del furto non ha quella vecchia.
        val stolen = MailboxKeys.decode(laptopBox.encode())
        assertNull(Mailbox.open(laptop, stolen, Identity.publicKey(phone.publicBytes), "tel", secret, file))
    }

    @Test
    fun rotationKeepsMessagesInFlight() {
        val laptopBox = MailboxKeys()
        val first = announced(laptopBox, t0)
        val file = Mailbox.seal(phone, "tel", Identity.publicKey(laptop.publicBytes), secret, first, msg, t0 + 20 * day)!!
        val second = laptopBox.current(t0 + 8 * day)
        assertNotEquals(first.id, second.id)
        laptopBox.prune(t0 + 21 * day)
        assertNotNull(Mailbox.open(laptop, laptopBox, Identity.publicKey(phone.publicBytes), "tel", secret, file))
        // Chi manda non usa una chiave dell'altro con più di tre settimane.
        assertNull(Mailbox.seal(phone, "tel", Identity.publicKey(laptop.publicBytes), secret, first, msg, t0 + 22 * day))
    }

    @Test
    fun keysSurviveSaving() {
        val box = MailboxKeys()
        box.current(t0); box.current(t0 + 8 * day)
        val back = MailboxKeys.decode(box.encode())
        assertEquals(box.all.map { it.id }, back.all.map { it.id })
        val file = Mailbox.seal(phone, "tel", Identity.publicKey(laptop.publicBytes), secret, announced(back, t0 + 9 * day), msg, t0 + 9 * day)!!
        assertNotNull(Mailbox.open(laptop, box, Identity.publicKey(phone.publicBytes), "tel", secret, file))
        assertNull(PeerMailboxKey.from(Message(Kind.Hello, "h", t0, mapOf(MailboxKeys.FIELD_ID to "../x", MailboxKeys.FIELD_KEY to "a", MailboxKeys.FIELD_TIME to "1"))))
    }
}
