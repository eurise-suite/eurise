package eu.eurise.core.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioTest {
    private var n = 0
    private val id = { "r${n++}" }
    private val esposito = Patient("p1", "Giuseppe Esposito", matrixId = "@giuseppe:studio.it", email = "G.Esposito@libero.it",
        phone = "+39 333 123 4567", therapies = listOf(Therapy("t1", "Ramipril", "5 mg")))
    private val on = StudioState(enabled = true, enabledAt = 1_000L, patients = listOf(esposito))

    @Test fun consensiConLaStoria() {
        val p = esposito.withConsent(Consent.OnlineReports, true, 10).withConsent(Consent.OnlineReports, false, 20)
        assertFalse(p.has(Consent.OnlineReports))
        assertEquals(2, p.consents.size)
        assertNull(p.since(Consent.OnlineReports))
        val again = p.withConsent(Consent.OnlineReports, true, 30).withConsent(Consent.OnlineReports, true, 40)
        assertEquals(30L, again.since(Consent.OnlineReports))
        assertEquals(3, again.consents.size)
    }

    @Test fun indirizzi() {
        assertTrue(Addresses.matches(esposito, Channel.Sms, "3331234567"))
        assertTrue(Addresses.matches(esposito, Channel.Sms, "0039 333-123-4567"))
        assertTrue(Addresses.matches(esposito, Channel.Email, "g.esposito@LIBERO.it "))
        assertTrue(Addresses.matches(esposito, Channel.Chat, "@Giuseppe:studio.it"))
        assertFalse(Addresses.matches(esposito, Channel.Sms, "3339999999"))
    }

    @Test fun codiceDiVerifica() {
        val code = Verification.newCode()
        assertEquals(7, code.length)
        val s = on.copy(codes = listOf(PendingCode("p1", code, 5_000L)))
        val (after, out) = Queue.receive(s, Channel.Chat, "@nuovo:matrix.org", "il codice è ${code.lowercase().replace("-", " ")}", 6_000L, "e1", 6_000L, id)
        assertTrue(out is Queue.Outcome.Verified)
        val p = after.patient("p1")!!
        assertEquals("@nuovo:matrix.org", p.matrixId)
        assertEquals(Channel.Chat, p.verifiedBy)
        assertTrue(after.codes.isEmpty())
        assertTrue(after.requests.isEmpty())
        // Scaduto: niente verifica.
        val late = Queue.receive(s, Channel.Chat, "@nuovo:matrix.org", code, 6_000L, "e2", 5_000L + Verification.VALID_MS + 1, id).second
        assertFalse(late is Queue.Outcome.Verified)
    }

    @Test fun coda() {
        var s = on
        s = Queue.receive(s, Channel.Chat, "@giuseppe:studio.it", "mi serve il ramipril", 2_000L, "e1", 2_000L, id).first
        s = Queue.receive(s, Channel.Sms, "3331234567", "Ho un dolore al petto", 3_000L, "sms:1", 3_000L, id).first
        s = Queue.receive(s, Channel.Email, "g.esposito@libero.it", "Grazie dottore", 4_000L, "mail:1", 4_000L, id).first
        // Già in coda, di uno sconosciuto, più vecchio dell'accensione: niente.
        s = Queue.receive(s, Channel.Chat, "@giuseppe:studio.it", "mi serve il ramipril", 2_000L, "e1", 5_000L, id).first
        s = Queue.receive(s, Channel.Chat, "@altro:studio.it", "ricetta", 5_000L, "e9", 5_000L, id).first
        s = Queue.receive(s, Channel.Chat, "@giuseppe:studio.it", "ricetta", 500L, "e0", 5_000L, id).first
        assertEquals(3, s.requests.size)
        val sum = Queue.summary(s, null)
        assertEquals(1, sum.urgent.size)
        assertEquals(listOf(Need.Prescription, Need.Thanks), sum.byNeed.keys.toList())
        assertEquals(1, sum.byChannel[Channel.Sms])
        assertEquals(listOf("Giuseppe Esposito"), Queue.names(s, sum.byNeed.getValue(Need.Prescription)))
        val done = s.copy(requests = s.requests.map { it.copy(doneAt = 9L) })
        assertTrue(Queue.summary(done, null).byNeed.isEmpty())
        assertEquals(3, Queue.history(done, "p1").size)
    }

    @Test fun testoPiuCompleto() {
        var s = Queue.receive(on, Channel.Email, "g.esposito@libero.it", "Una cosa", 2_000L, "mail:1", 2_000L, id).first
        assertEquals(Need.Question, s.requests.single().need)
        s = Queue.receive(s, Channel.Email, "g.esposito@libero.it", "Una cosa\nMi rifà la ricetta del Ramipril?", 2_000L, "mail:1", 3_000L, id).first
        assertEquals(Need.Prescription, s.requests.single().need)
        assertEquals(1, s.requests.size)
    }

    @Test fun spento() {
        val (s, out) = Queue.receive(on.copy(enabled = false), Channel.Chat, "@giuseppe:studio.it", "ricetta", 2_000L, "e1", 2_000L, id)
        assertEquals(Queue.Outcome.Ignored, out)
        assertTrue(s.requests.isEmpty())
    }

    @Test fun json() {
        var s = on.copy(codes = listOf(PendingCode("p1", "AAA-CCC", 7L)), info = Bussola.StudioInfo("Studio Rossi", "Lun 9-12", guard = "0824 000000"))
        s = Queue.receive(s, Channel.Chat, "@giuseppe:studio.it", "Posso venire domani alle 10?", 2_000L, "e1", 2_000L, id).first
        s = s.copy(patients = s.patients.map { it.withConsent(Consent.Notice, true, 3L).copy(practices = setOf(Practice.General, Practice.Acupuncture), verifiedAt = 4L, verifiedBy = Channel.Sms) })
        assertEquals(s, StudioCodec.decode(StudioCodec.encode(s)))
        assertEquals(StudioState(), StudioCodec.decode("non è json"))
    }
}
