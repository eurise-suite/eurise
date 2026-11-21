package eu.eurise.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailBallsTest {
    private fun s(from: String, subject: String, date: Long, kind: Kind = Kind.People) = Summary("a", date, from, from, subject, date, true, kind)

    @Test fun doneWhenTheColleagueAnswers() {
        val b = MailBalls.Ball("1", "paolo@x.it", "Varianti cucina", "Preventivo col quarzo", 1000)
        val inbox = listOf(s("paolo@x.it", "Re: I: Varianti cucina", 500), s("anna@x.it", "Re: Varianti cucina", 2000))
        assertFalse(MailBalls.update(listOf(b), inbox).single().done)
        assertTrue(MailBalls.update(listOf(b), inbox + s("Paolo@X.it", "R: Varianti cucina", 3000)).single().done)
        assertTrue(MailBalls.stale(b, 1000 + MailBalls.QUIET_MS))
        assertEquals(listOf(b), MailBalls.decode(MailBalls.encode(listOf(b))))
        assertEquals("varianti cucina", MailBalls.core("Fwd: RE: Varianti cucina"))
    }

    @Test fun bodyAndPeople() {
        assertTrue(MailBalls.body("Rispondi tu", null, "--- inoltrato ---", "Buongiorno").startsWith("🏐 Rispondi tu\n\n--- inoltrato ---"))
        val inbox = listOf(s("paolo@x.it", "a", 1), s("paolo@x.it", "b", 2), s("me@x.it", "c", 3), s("news@x.it", "d", 4, Kind.Newsletters), s("marta@x.it", "e", 5))
        assertEquals(listOf("paolo@x.it", "marta@x.it"), MailBalls.people(inbox, setOf("me@x.it")).map { it.first })
    }
}
