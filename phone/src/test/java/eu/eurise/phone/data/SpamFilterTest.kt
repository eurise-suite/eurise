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
}
