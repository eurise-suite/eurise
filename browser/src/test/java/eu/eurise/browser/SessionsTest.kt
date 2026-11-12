package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsTest {
    @Test fun closedTabsAreRememberedNewestFirstWithoutDuplicates() {
        val a = SavedTab("https://a.it", "A")
        val b = SavedTab("https://b.it", "B")
        val list = SessionStore.remember(SessionStore.remember(emptyList(), listOf(a)), listOf(b, SavedTab("about:blank", "")))
        assertEquals(listOf(b, a), list)
        assertEquals(listOf(a, b), SessionStore.remember(list, listOf(a)))
    }

    @Test fun closedTabsAreCapped() {
        val many = (1..60).map { SavedTab("https://s$it.it", "$it") }
        val list = SessionStore.remember(emptyList(), many)
        assertEquals(SessionStore.MAX_CLOSED, list.size)
        assertEquals("https://s1.it", list.first().url)
    }

    @Test fun newGroupsTakeAFreeTint() {
        assertEquals(0, SessionStore.nextTint(emptyList()))
        assertEquals(2, SessionStore.nextTint(listOf(TabGroup("Lavoro", 0), TabGroup("Viaggio", 1))))
    }

    @Test fun savingASessionWithTheSameNameReplacesIt() {
        val old = Session("Viaggio Lisbona", listOf(SavedTab("https://tap.pt", "TAP")), 1)
        val other = Session("Lavoro", emptyList(), 2)
        val updated = SessionStore.upsert(listOf(other, old), Session("viaggio lisbona", listOf(SavedTab("https://a.pt", "A")), 3))
        assertEquals(2, updated.size)
        assertEquals(3L, updated.first().time)
    }

    @Test fun dataSurvivesTheFile() {
        val d = SessionStore.Data(
            groups = listOf(TabGroup("Lavoro", 0)),
            sessions = listOf(Session("Viaggio", listOf(SavedTab("https://tap.pt", "Voli · TAP", 5)), 9)),
            closed = listOf(SavedTab("https://agenziaentrate.gov.it", "Agenzia delle Entrate", 7)),
        )
        assertEquals(d, SessionStore.decode(SessionStore.encode(d)))
        assertTrue(SessionStore.decode("{}").groups.isEmpty())
    }
}
