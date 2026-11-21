package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SempliceTest {
    @Test fun toggleAndHelp() {
        var c = Semplice.Config()
        c = Semplice.toggle(c, "!luca")
        assertEquals("!luca", c.help)
        c = Semplice.toggle(c, "!anna")
        c = Semplice.toggle(c, "!dott")
        c = Semplice.toggle(c, "!x")
        // Al massimo quattro.
        assertEquals(c, Semplice.toggle(c, "!y"))
        c = Semplice.toggle(c, "!luca")
        assertEquals("!anna", c.help)
        assertEquals(listOf("!anna", "!dott", "!x"), c.rooms)
    }

    @Test fun roundTripAndShown() {
        val c = Semplice.Config(true, listOf("!b", "!a"), "!a", "Bruna")
        assertEquals(c, Semplice.decode(Semplice.encode(c)))
        assertEquals(Semplice.Config(), Semplice.decode("rotto"))
        assertNull(Semplice.decode("""{"on":true,"rooms":["!a"],"help":"!z"}""").help)
        val rooms = listOf(Room("!a", "Anna", "", 0, 0, false), Room("!b", "Luca", "", 0, 1, false))
        assertEquals(listOf("!b", "!a"), Semplice.shown(c, rooms).map { it.id })
        assertEquals("Bruna", Semplice.greetingName(c, "@x:y"))
        assertEquals("Bruna", Semplice.greetingName(Semplice.Config(), "@bruna.rossi:x.it"))
    }
}
