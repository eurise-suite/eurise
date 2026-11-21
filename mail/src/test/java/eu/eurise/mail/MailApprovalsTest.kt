package eu.eurise.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailApprovalsTest {
    @Test fun verdicts() {
        assertEquals(MailApprovals.Verdict.Approved, MailApprovals.verdict("Ok\n\nIl giorno 21/11 Luca ha scritto:\n> Preventivo")?.first)
        assertEquals(MailApprovals.Verdict.Approved, MailApprovals.verdict("Va bene, procedi pure.")?.first)
        assertEquals(MailApprovals.Verdict.Approved, MailApprovals.verdict("Sì!")?.first)
        assertEquals(MailApprovals.Verdict.Rejected, MailApprovals.verdict("No, costa troppo")?.first)
        val change = MailApprovals.verdict("Togli la voce 14 e rimandamelo\n> testo citato")!!
        assertEquals(MailApprovals.Verdict.Change, change.first)
        assertEquals("Togli la voce 14 e rimandamelo", change.second)
        assertNull(MailApprovals.verdict("> solo citazione"))
        assertEquals(MailApprovals.Verdict.Change, MailApprovals.verdict("Non so, ne parliamo")?.first)
    }

    @Test fun subjects() {
        val s = MailApprovals.subject("Preventivo Villa Rossi")
        assertTrue(MailApprovals.isRequest("Re: $s"))
        assertEquals("Preventivo Villa Rossi", MailApprovals.title("RE: $s"))
        assertFalse(MailApprovals.isRequest("Preventivo Villa Rossi"))
    }

    @Test fun candidatesAndList() {
        val r = MailApprovals.Request("a1", "ferri@studio.it", "Preventivo Villa Rossi", 1000)
        val inbox = listOf(
            Summary("x", 7, "Ferri", "Ferri@studio.it", "Re: Richiesta di approvazione: Preventivo Villa Rossi", 2000, false, Kind.People),
            Summary("x", 8, "Ferri", "ferri@studio.it", "Altro", 3000, false, Kind.People),
        )
        assertEquals(7L, MailApprovals.candidates(listOf(r), inbox).single().second.uid)
        assertTrue(MailApprovals.candidates(listOf(r.copy(answerUid = 7)), inbox).isEmpty())
        assertTrue(MailApprovals.waiting(r, 1000 + MailApprovals.QUIET_MS))
        assertEquals(listOf(r), MailApprovals.decode(MailApprovals.encode(listOf(r))))
    }
}
