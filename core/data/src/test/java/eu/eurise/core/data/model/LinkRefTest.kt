package eu.eurise.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRefTest {

    @Test
    fun roundTrip() {
        listOf(LinkRef.note(12), LinkRef.task(3), LinkRef.event(900), LinkRef.contact("0r1-4A2C:2")).forEach {
            assertEquals(it, LinkRef.parse(it.toString()))
        }
    }

    @Test
    fun contactKeysMayContainColons() {
        assertEquals("0r1-4A2C:2", LinkRef.parse("Contact:0r1-4A2C:2")!!.key)
    }

    @Test
    fun badTextIsRejected() {
        assertNull(LinkRef.parse("Photo:1"))
        assertNull(LinkRef.parse("Note:"))
        assertNull(LinkRef.parse(":5"))
        assertNull(LinkRef.parse("Note5"))
    }

    @Test
    fun orderIsStable() {
        assertTrue(LinkRef.event(1) < LinkRef.note(1))
        assertEquals(listOf(LinkRef.contact("a"), LinkRef.note(2)), listOf(LinkRef.note(2), LinkRef.contact("a")).sorted())
    }
}
