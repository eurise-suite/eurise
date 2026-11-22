package eu.eurise.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ElenchiTest {
    @Test fun flow() {
        var l = Elenchi.defaults(listOf("Spesa", "Casa"))
        l = Elenchi.add(l, "l0", "Latte e yogurt", "@anna:x", 10)
        l = Elenchi.add(l, "l0", "latte e yogurt", "@luca:x", 11) // già c'è
        l = Elenchi.add(l, "l0", "Pane", "@anna:x", 12)
        l = Elenchi.add(l, "l0", "Caffè", "@me:x", 13)
        assertEquals(3, Elenchi.open(l[0]))
        val caffe = l[0].items.first { it.text == "Caffè" }.id
        val pane = l[0].items.first { it.text == "Pane" }.id
        l = Elenchi.take(l, "l0", caffe, "@luca:x")
        l = Elenchi.done(l, "l0", pane, "@anna:x", 100)
        assertEquals(listOf("Caffè", "Latte e yogurt", "Pane"), Elenchi.shown(l[0], 200).map { it.text })
        assertEquals(listOf("Caffè", "Latte e yogurt"), Elenchi.shown(l[0], 100 + Elenchi.KEEP_DONE_MS).map { it.text })
        l = Elenchi.done(l, "l0", pane, "@anna:x", 300)
        assertEquals(3, Elenchi.open(l[0]))
        l = Elenchi.take(l, "l0", caffe, "@luca:x")
        assertEquals("", l[0].items.first { it.id == caffe }.taker)
        assertEquals(l, Elenchi.decode(Elenchi.encode(l, 400)))
    }
}
