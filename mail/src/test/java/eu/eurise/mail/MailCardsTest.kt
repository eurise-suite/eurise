package eu.eurise.mail

import eu.eurise.core.insieme.Scheda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailCardsTest {
    private val card = Scheda.Card("m1", Scheda.Kind.Uscita, "Pizza", options = listOf("sab 29", "dom 30"), origin = "chat:!g")
    private val sent = MailCards.Sent(card, listOf("anna@x.it", "luca@x.it"), "[Uscita] Pizza", 1000)

    private fun mail(uid: Long, from: String, subject: String, date: Long) = Summary("a", uid, "", from, subject, date, false, Kind.People, false)

    @Test fun candidatesAndApply() {
        val inbox = listOf(mail(1, "anna@x.it", "Re: [Uscita] Pizza", 2000), mail(2, "altro@x.it", "Re: [Uscita] Pizza", 2000),
            mail(3, "luca@x.it", "Altro", 2000), mail(4, "luca@x.it", "R: [Uscita] Pizza", 500))
        val c = MailCards.candidates(listOf(sent), inbox)
        assertEquals(listOf(1L), c.map { it.second.uid })
        val next = MailCards.apply(listOf(sent), "m1", "Anna@x.it", 1, Scheda.Answer(choices = setOf(1)), 2000)
        assertEquals(setOf(1), next[0].answers["anna@x.it"]?.answer?.choices)
        assertTrue(MailCards.candidates(next, inbox).isEmpty())
        assertEquals(listOf("luca@x.it"), MailCards.silent(next[0], 1000 + MailCards.QUIET_MS))
    }

    @Test fun roundTrip() {
        val next = MailCards.apply(listOf(sent), "m1", "anna@x.it", 1, Scheda.Answer(choices = setOf(0, 1)), 2000)
        assertEquals(next, MailCards.decode(MailCards.encode(next)))
    }
}
