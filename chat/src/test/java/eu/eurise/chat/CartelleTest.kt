package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CartelleTest {
    private fun room(id: String, unread: Int = 0) = Room(id, id, "", 0, unread, false)

    @Test fun addRenameRemove() {
        var l = Cartelle.add(emptyList(), "  Famiglia  ", "f1")
        assertEquals("Famiglia", l.single().name)
        // Doppioni e nomi vuoti non entrano.
        assertEquals(l, Cartelle.add(l, "famiglia", "f2"))
        assertEquals(l, Cartelle.add(l, "   ", "f3"))
        l = Cartelle.add(l, "Scuola", "f2")
        assertEquals(l, Cartelle.rename(l, "f2", "FAMIGLIA"))
        l = Cartelle.rename(l, "f2", "Scuola e sport")
        assertEquals("Scuola e sport", l[1].name)
        assertEquals(listOf("f1"), Cartelle.remove(l, "f2").map { it.id })
        assertEquals(24, Cartelle.cleanName(emptyList(), "x".repeat(40))!!.length)
    }

    @Test fun toggleAndOf() {
        var l = listOf(Cartelle.Folder("f1", "Famiglia"), Cartelle.Folder("f2", "Clienti"))
        l = Cartelle.toggle(l, "f1", "!a")
        l = Cartelle.toggle(l, "f2", "!a")
        assertEquals(listOf("f1", "f2"), Cartelle.of(l, "!a").map { it.id })
        l = Cartelle.toggle(l, "f1", "!a")
        assertEquals(listOf("f2"), Cartelle.of(l, "!a").map { it.id })
    }

    @Test fun shownAndUnread() {
        val rooms = listOf(room("!a", 2), room("!b"), room("!self", 1), room("!c", 1))
        val f = Cartelle.Folder("f1", "Famiglia", setOf("!a", "!b", "!c"))
        assertEquals(listOf("!a", "!b", "!c"), Cartelle.shown(rooms, null, "!self").map { it.id })
        assertEquals(listOf("!a", "!b", "!c"), Cartelle.shown(rooms, f, "!self").map { it.id })
        assertEquals(2, Cartelle.unread(rooms, f))
        assertEquals(setOf("!a"), Cartelle.prune(listOf(f), setOf("!a", "!x")).single().rooms)
    }

    @Test fun roundTripAndSelf() {
        val l = listOf(Cartelle.Folder("f1", "Famiglia", setOf("!a")), Cartelle.Folder("f2", "Scuola"))
        assertEquals(l, Cartelle.decode(Cartelle.encode(l)))
        assertEquals(emptyList<Cartelle.Folder>(), Cartelle.decode("{rotto"))
        assertEquals("!me:x.it", Cartelle.selfRoom(JSONObject("""{"room_id":"!me:x.it"}""")))
        assertNull(Cartelle.selfRoom(JSONObject("""{"room_id":""}""")))
        assertNull(Cartelle.selfRoom(null))
    }
}
