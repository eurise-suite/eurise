package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AckTest {
    @Test fun contentAndText() {
        val c = JSONObject(Ack.content(" Da lunedì si apre alle 8:30 ", "Conferma con ✅").toString())
        assertTrue(Ack.isRequest(c))
        assertEquals("Da lunedì si apre alle 8:30\n\nConferma con ✅", c.getString("body"))
        assertEquals("Da lunedì si apre alle 8:30", Ack.text(c, c.getString("body")))
        assertEquals("ciao", Ack.text(JSONObject(), "ciao"))
    }

    @Test fun status() {
        val s = Ack.status(listOf("@me", "@a", "@b", "@p", "@m", "@me"), "@me", setOf("@a", "@b"), mapOf("@p" to 200L, "@m" to 50L), 100)
        assertEquals(listOf("@a", "@b"), s.confirmed)
        assertEquals(listOf("@p"), s.readOnly)
        assertEquals(listOf("@m"), s.unread)
        assertEquals(4, s.total)
    }

    @Test fun readersPerUser() {
        val sync = JSONObject("""{"rooms":{"join":{"!r":{"ephemeral":{"events":[{"type":"m.receipt","content":{
            "${'$'}e1":{"m.read":{"@a":{"ts":5},"@b":{"ts":9}}},"${'$'}e2":{"m.read":{"@a":{"ts":7}}}}}]}}}}}""")
        assertEquals(mapOf("!r" to mapOf("@a" to 7L, "@b" to 9L)), Ack.readers(sync))
    }
}
