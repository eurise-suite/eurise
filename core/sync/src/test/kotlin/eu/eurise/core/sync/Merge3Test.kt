package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Merge3Test {
    private val base = listOf("a", "b", "c", "d")

    @Test fun cambiDaUnaParteSola() {
        val out = Merge3.clean(Merge3.merge(base, listOf("a", "B", "c", "d"), base))
        assertEquals(listOf("a", "B", "c", "d"), out)
    }

    @Test fun cambiInPuntiDiversiSiUniscono() {
        val local = listOf("a", "B", "c", "d")
        val remote = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "B", "c", "d", "e"), Merge3.clean(Merge3.merge(base, local, remote)))
    }

    @Test fun stessoCambioDaEntrambe() {
        val both = listOf("a", "x", "c", "d")
        assertEquals(both, Merge3.clean(Merge3.merge(base, both, both)))
    }

    @Test fun stessoPuntoInDueModiEConflitto() {
        val parts = Merge3.merge(base, listOf("a", "L", "c", "d"), listOf("a", "R", "c", "d"))
        assertNull(Merge3.clean(parts))
        val conflict = parts.filterIsInstance<Merged.Conflict<String>>().single()
        assertEquals(listOf("L"), conflict.local)
        assertEquals(listOf("R"), conflict.remote)
    }

    @Test fun eliminazioneDaUnaParteEAggiuntaDallAltra() {
        val local = listOf("a", "c", "d")
        val remote = listOf("z", "a", "b", "c", "d")
        assertEquals(listOf("z", "a", "c", "d"), Merge3.clean(Merge3.merge(base, local, remote)))
    }

    @Test fun baseVuota() {
        assertTrue(Merge3.merge(emptyList(), emptyList<String>(), emptyList()).isEmpty())
    }
}
