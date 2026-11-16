package eu.eurise.core.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionQueryTest {

    @Test
    fun chiocciolaInFondo() {
        val hit = MentionQuery.find("Chiamare @Lu")!!
        assertEquals("Lu", hit.query)
        assertEquals(9, hit.start)
        assertEquals(true, hit.at)
    }

    @Test
    fun chiocciolaConCognomeIniziato() {
        assertEquals("Studio B", MentionQuery.find("Mandare le misure a @Studio B")!!.query)
    }

    @Test
    fun emailNonEUnaPersona() {
        assertNull(MentionQuery.find("scrivi a anna@posteo"))
    }

    @Test
    fun nomeDopoConSoloNegliAppuntamenti() {
        assertNull(MentionQuery.find("Pranzo con Ma"))
        val hit = MentionQuery.find("Pranzo con Ma", byName = true)!!
        assertEquals("Ma", hit.query)
        assertEquals(false, hit.at)
    }

    @Test
    fun conMinuscoloNonDisturba() {
        assertNull(MentionQuery.find("Pranzo con calma", byName = true))
        assertNull(MentionQuery.find("Pranzo con M", byName = true))
    }

    @Test
    fun cursoreDentroUnaParola() {
        assertNull(MentionQuery.find("@Marco Rossi", caret = 3))
    }

    @Test
    fun applicaConChiocciola() {
        val text = "Chiamare @Lu per la cantina"
        val hit = MentionQuery.find(text, caret = 12)!!
        val (out, caret) = MentionQuery.apply(text, hit, "Luca Verdi", keepAt = true)
        assertEquals("Chiamare @Luca per la cantina", out)
        assertEquals(14, caret)
    }

    @Test
    fun applicaNomeIntero() {
        val text = "Pranzo con @Ma"
        val hit = MentionQuery.find(text)!!
        val (out, caret) = MentionQuery.apply(text, hit, "Marco Rossi", keepAt = false)
        assertEquals("Pranzo con Marco Rossi ", out)
        assertEquals(out.length, caret)
    }

    @Test
    fun nomiAncoraNelTesto() {
        val names = listOf("Marco Rossi", "Luca Verdi")
        assertEquals(listOf("Marco Rossi"), MentionQuery.stillThere("Pranzo con Marco Rossi", names))
        assertEquals(listOf("Luca Verdi"), MentionQuery.stillThere("Chiamare @Luca", names))
    }
}
