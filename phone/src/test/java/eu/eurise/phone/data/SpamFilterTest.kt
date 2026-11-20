package eu.eurise.phone.data

import eu.eurise.phone.data.SpamFilter.Reason
import eu.eurise.phone.data.SpamFilter.Rules
import eu.eurise.phone.data.SpamFilter.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFilterTest {
    private fun d(rules: Rules, number: String?, contact: Boolean = false, called: Boolean = false, rejects: Int = 0) =
        SpamFilter.decide(rules, number, contact, called, "39", rejects)

    @Test
    fun contactsAlwaysRing() {
        assertEquals(Verdict.Ring to null, d(Rules(unknown = true), "+216 71 000 000", contact = true))
        assertEquals(Verdict.Ring to null, d(Rules(unknown = true), "3331234567", called = true))
    }

    @Test
    fun foreignUnknownIsSilenced() {
        assertEquals(Verdict.Silence to Reason.ForeignUnknown, d(Rules(), "+216 71 000 000"))
        assertEquals(Verdict.Silence to Reason.ForeignUnknown, d(Rules(), "0037360000000"))
        assertEquals(Verdict.Ring to null, d(Rules(), "+39 333 1234567"))
        assertEquals(Verdict.Ring to null, d(Rules(), "0824 123456"))
    }

    @Test
    fun hiddenAndUnknownFollowTheChoice() {
        assertEquals(Verdict.Ring to null, d(Rules(), null))
        assertEquals(Verdict.Silence to Reason.Hidden, d(Rules(hidden = true), ""))
        assertEquals(Verdict.Silence to Reason.Unknown, d(Rules(unknown = true), "3331234567"))
    }

    @Test
    fun insistentCallerIsRejected() {
        assertEquals(Verdict.Reject to Reason.Repeated, d(Rules(), "+216 71 000 000", rejects = 2))
        assertEquals(Verdict.Ring to null, d(Rules(unknown = true), "3331234567", rejects = 2))
        assertEquals(Verdict.Ring to null, d(Rules(enabled = false), "+216 71 000 000"))
    }

    @Test
    fun foreignNeedsAPrefix() {
        assertTrue(SpamFilter.isForeign("+4930123", "39"))
        assertFalse(SpamFilter.isForeign("+39333", "39"))
        assertFalse(SpamFilter.isForeign("333123", "39"))
    }

    @Test
    fun callCenterIsSuspect() {
        assertEquals("0844", SpamFilter.suspect("+39 0844 1234567", emptySet()))
        assertEquals("0843", SpamFilter.suspect("08431234567", emptySet()))
        assertEquals("029475", SpamFilter.suspect("0039 02 9475 1111", setOf("029475")))
        assertEquals(null, SpamFilter.suspect("3331234567", emptySet()))
        assertEquals(null, SpamFilter.suspect("+49 0844 1234", emptySet()))
        assertEquals(null, SpamFilter.suspect(null, setOf("02")))
    }

    @Test
    fun listedGoesSilentNotBlocked() {
        assertEquals(Verdict.Silence to Reason.Listed, SpamFilter.decide(Rules(), "0844 1234567", false, false, "39", listed = true))
        assertEquals(Verdict.Reject to Reason.Repeated, SpamFilter.decide(Rules(), "+216 71 000 000", false, false, "39", 2, listed = true))
        // La rubrica e chi si è già chiamato passano sempre, anche se nell'elenco.
        assertEquals(Verdict.Ring to null, SpamFilter.decide(Rules(), "0844 1234567", true, false, "39", listed = true))
        assertEquals(Verdict.Ring to null, SpamFilter.decide(Rules(enabled = false), "0844 1234567", false, false, "39", listed = true))
    }
}
