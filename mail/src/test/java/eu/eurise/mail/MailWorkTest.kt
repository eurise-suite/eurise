package eu.eurise.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class MailWorkTest {
    private val day = 86_400_000L
    private fun s(from: String, subject: String, date: Long, seen: Boolean = true, kind: Kind = Kind.People, answered: Boolean = false) =
        Summary("a", date, from, from, subject, date, seen, kind, answered)

    @Test fun asks() {
        assertEquals(MailWork.Ask.Approve, MailWork.ask("Re: Varianti cucina, mi dai l'ok per il quarzo"))
        assertEquals(MailWork.Ask.Approve, MailWork.ask("Richiesta di approvazione: preventivo"))
        assertEquals(MailWork.Ask.Confirm, MailWork.ask("Sopralluogo giovedì, mi confermi?"))
        assertEquals(MailWork.Ask.Urgent, MailWork.ask("URGENTE fattura"))
        assertEquals(MailWork.Ask.Question, MailWork.ask("Quando passate?"))
        assertNull(MailWork.ask("Foto del cantiere"))
    }

    @Test fun forYouSkipsAnsweredOldMineAndRobots() {
        val now = 30 * day
        val inbox = listOf(
            s("anna@x.it", "Varianti", now - day), s("anna@x.it", "Vecchia", now - 20 * day), s("me@x.it", "Mia", now - day),
            s("news@x.it", "Offerte", now - day, kind = Kind.Newsletters), s("paolo@x.it", "Fatto", now - day, answered = true),
            s("marta@x.it", "Mi approvi la spesa", now - 2 * day, seen = false),
        )
        assertEquals(listOf("marta@x.it", "anna@x.it"), MailWork.forYou(inbox, setOf("me@x.it"), now).map { it.fromAddress })
    }

    @Test fun waitingAndDigest() {
        val now = 10 * day
        val w = MailWork.Waiting("1", "marco@x.it", "Marco", "Preventivo Villa", now - 4 * day, 12)
        val inbox = listOf(s("marta@x.it", "Mi approvi la spesa", now - day, seen = false))
        assertEquals(listOf(w), MailWork.open(listOf(w), inbox))
        assertTrue(MailWork.open(listOf(w), inbox + s("Marco@x.it", "Re: Preventivo Villa", now - day)).isEmpty())
        val d = MailWork.digest(inbox, listOf(w), emptyList(), setOf("me@x.it"), now)
        assertEquals(1, d.asks); assertEquals(1, d.unread); assertEquals(w, d.longest); assertEquals(4, d.longestDays)
        assertFalse(d.empty)
        assertEquals(listOf(w), MailWork.decode(MailWork.encode(listOf(w))))
    }

    @Test fun bestTimeAndMornings() {
        // Lunedì 23 novembre 2026, 16:00.
        val now = LocalDateTime.of(2026, 11, 23, 16, 0)
        val at = { h: Int, m: Int, d: Int -> LocalDateTime.of(2026, 11, d, h, m).toInstant(ZoneOffset.UTC).toEpochMilli() }
        val inbox = listOf(s("marco@x.it", "a", at(8, 40, 16)), s("marco@x.it", "b", at(8, 55, 17)), s("marco@x.it", "c", at(9, 10, 18)),
            s("marco@x.it", "sabato", at(22, 0, 21)))
        assertEquals(LocalDateTime.of(2026, 11, 24, 8, 30), MailWork.bestTime(inbox, "Marco@x.it", now, ZoneOffset.UTC))
        assertNull(MailWork.bestTime(inbox.take(2), "marco@x.it", now, ZoneOffset.UTC))
        // Venerdì sera → lunedì mattina.
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 30), MailWork.nextMorning(LocalDateTime.of(2026, 11, 27, 18, 0)))
        // Stesso giorno se c'è ancora tempo.
        assertEquals(LocalDateTime.of(2026, 11, 23, 17, 0), MailWork.next(now, java.time.LocalTime.of(17, 0)))
    }
}
