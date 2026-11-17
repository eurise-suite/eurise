package eu.eurise.chat

import eu.eurise.chat.People.Channel
import eu.eurise.chat.People.Contact
import eu.eurise.chat.People.Entry
import eu.eurise.chat.People.Seen
import eu.eurise.chat.People.Who
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleTest {
    private val anna = Contact("lk-anna", "Anna Rossi", listOf("+39 347 123 4567"), listOf("anna.rossi@posteo.it"), listOf("@anna:matrix.org"))

    private fun e(id: String, ch: Channel, t: Long, incoming: Boolean = true) = Entry(id, ch, t, incoming, "x$id")

    @Test fun numeriScrittiInModiDiversi() {
        assertEquals(People.phoneKey("+39 347 123 4567"), People.phoneKey("0039 3471234567"))
        assertEquals(People.phoneKey("347-123 4567"), People.phoneKey("+393471234567"))
        assertTrue(People.samePhone("+39 347 1234567", "3471234567"))
        assertFalse(People.samePhone("112", "113"))
        assertFalse(People.samePhone("", ""))
    }

    @Test fun unaRigaPerPersonaConTuttiICanali() {
        val seen = listOf(
            Seen(Channel.Chat, "@anna:matrix.org", e("c1", Channel.Chat, 900), 3, "Anna", roomId = "!r"),
            Seen(Channel.Sms, "3471234567", e("s1", Channel.Sms, 500), 1),
            Seen(Channel.Email, "anna.rossi@posteo.it", e("m1", Channel.Email, 700), 0, "Anna Rossi"),
            Seen(Channel.Call, "+39 347 123 4567", e("k1", Channel.Call, 800), 0),
            Seen(Channel.Email, "preventivi@infissi.it", e("m2", Channel.Email, 950), 1, "Marco Bianchi"),
        )
        val rows = People.group(seen, listOf(anna, anna, anna, anna, null))
        assertEquals(2, rows.size)
        // Dalla più recente: l'email di Marco (950), poi Anna (900).
        assertEquals("Marco Bianchi", rows[0].who.name)
        assertFalse(rows[0].who.contact)
        val a = rows[1]
        assertEquals("Anna Rossi", a.who.name)
        assertEquals("lk-anna", a.who.key)
        assertEquals("!r", a.who.roomId)
        assertEquals(4, a.unread)
        assertEquals(setOf(Channel.Chat, Channel.Sms, Channel.Email, Channel.Call), a.channels)
        assertEquals("c1", a.last?.id)
    }

    @Test fun senzaRubricaLoStessoNumeroFaUnaRiga() {
        val seen = listOf(
            Seen(Channel.Sms, "+39 333 000 1111", e("s", Channel.Sms, 10), 0),
            Seen(Channel.Call, "3330001111", e("k", Channel.Call, 20), 0),
        )
        val rows = People.group(seen, listOf(null, null))
        assertEquals(1, rows.size)
        assertEquals(setOf(Channel.Sms, Channel.Call), rows[0].channels)
        assertEquals("k", rows[0].last?.id)
    }

    @Test fun ilFiloEInOrdineESenzaDoppioni() {
        val out = People.merge(listOf(e("a", Channel.Chat, 3), e("b", Channel.Chat, 1)), listOf(e("c", Channel.Sms, 2), e("a", Channel.Chat, 3)))
        assertEquals(listOf("b", "c", "a"), out.map { it.id })
    }

    @Test fun rispondiDoveTiHaScritto() {
        val all = listOf(Channel.Chat, Channel.Sms, Channel.Email)
        assertEquals(Channel.Sms, People.choose(all, Channel.Chat, auto = true, lastIncoming = Channel.Sms, textLength = 20))
        // Una chiamata non decide il canale: vale la stellina.
        assertEquals(Channel.Chat, People.choose(all, Channel.Chat, auto = true, lastIncoming = Channel.Call, textLength = 20))
        // Testo lungo: email.
        assertEquals(Channel.Email, People.choose(all, Channel.Chat, auto = true, lastIncoming = Channel.Sms, textLength = 900))
        // Scelta automatica spenta: sempre la stellina.
        assertEquals(Channel.Chat, People.choose(all, Channel.Chat, auto = false, lastIncoming = Channel.Sms, textLength = 900))
        // Nessuna stellina: Chat, poi SMS, poi email.
        assertEquals(Channel.Sms, People.choose(listOf(Channel.Email, Channel.Sms), null, auto = false, lastIncoming = null, textLength = 1))
        assertNull(People.choose(emptyList(), null, auto = true, lastIncoming = null, textLength = 1))
    }

    @Test fun canaliPerScrivere() {
        assertEquals(listOf(Channel.Chat, Channel.Sms, Channel.Email), People.writable(Who("k", "Anna", listOf("1"), listOf("a@b.it"), listOf("@a:b"))))
        assertEquals(listOf(Channel.Sms), People.writable(Who("k", "Luca", phones = listOf("3330001111"))))
        assertEquals(Channel.Email, People.lastIncoming(listOf(e("1", Channel.Email, 1), e("2", Channel.Call, 2), e("3", Channel.Sms, 3, incoming = false))))
    }
}
