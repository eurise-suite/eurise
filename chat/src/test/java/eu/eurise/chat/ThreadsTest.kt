package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsTest {
    @Test fun contentRoundTrip() {
        val c = Threads.content("Seconda per me", "\$root", "\$last")
        assertEquals("\$root", Threads.root(c))
        assertTrue(Threads.fallingBack(c))
        assertEquals("\$last", Relations.replyTo(c))
        val real = Threads.content("Sì", "\$root", "\$last", replyTo = "\$sara")
        assertFalse(Threads.fallingBack(real))
        assertEquals("\$sara", Relations.replyTo(real))
        assertNull(Threads.root(JSONObject().put("m.relates_to", JSONObject().put("rel_type", "m.replace").put("event_id", "\$x"))))
    }

    @Test fun eventFromElement() {
        val o = JSONObject("""{"type":"m.room.message","event_id":"${'$'}r1","sender":"@sara:x.it","origin_server_ts":10,
            "content":{"msgtype":"m.text","body":"Io la prima","m.relates_to":{"rel_type":"m.thread","event_id":"${'$'}root",
            "is_falling_back":true,"m.in_reply_to":{"event_id":"${'$'}root"}}}}""")
        val e = Matrix.event(o)!!
        assertEquals("\$root", e.thread)
        // Il ripiego non è una risposta: niente citazione nel fumetto.
        assertNull(e.replyTo)
        val root = JSONObject("""{"type":"m.room.message","event_id":"${'$'}root","sender":"@luca:x.it","origin_server_ts":5,
            "content":{"msgtype":"m.text","body":"Turni pulizie"},"unsigned":{"m.relations":{"m.thread":{"count":6}}}}""")
        assertEquals(6, Matrix.event(root)!!.threadCount)
    }

    @Test fun summaryAndTitle() {
        val events = listOf(
            Event("\$a", "@luca", "Facciamo i turni per le pulizie di agosto? Propongo a settimane", 1, Event.Kind.Text),
            Event("\$b", "@sara", "Bollette", 2, Event.Kind.Text, threadCount = 3),
            Event("\$c", "@sara", "Io la prima", 3, Event.Kind.Text, thread = "\$a"),
            Event("\$d", "@me", "Seconda", 4, Event.Kind.Text, thread = "\$a"),
            Event("\$e", "@x", "Niente", 5, Event.Kind.Text),
        )
        val s = Threads.summary(events)
        assertEquals(listOf("\$a", "\$b"), s.map { it.root })
        assertEquals(2, s[0].count)
        assertEquals(3, s[1].count)
        assertEquals("Facciamo i turni per le pulizie di agos…", s[0].title)
        assertEquals("\$d", Threads.last(events, "\$a"))
        assertEquals("\$b", Threads.last(events, "\$b"))
        assertEquals("Ciao", Threads.title("\n  Ciao  \naltro"))
    }
}
