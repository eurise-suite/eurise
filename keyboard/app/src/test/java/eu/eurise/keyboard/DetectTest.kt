package eu.eurise.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectTest {
    @Test fun commands() {
        assertEquals(Detect.Command.Availability to "/disp", Detect.command("ti propongo /disp"))
        assertEquals(Detect.Command.File, Detect.command("eccolo /file")?.first)
        assertNull(Detect.command("http://sito/file"))
        assertNull(Detect.command("1/2"))
    }

    @Test fun mentions() {
        assertEquals("Ann", Detect.mention("scrivi a @Ann"))
        assertNull(Detect.mention("anna@esempio"))
        assertNull(Detect.mention("@A"))
    }

    @Test fun tasks() {
        assertTrue(Detect.looksLikeTask("domani alle 9 chiamo il notaio"))
        assertTrue(Detect.looksLikeTask("Devo pagare il bollo entro il 30"))
        assertFalse(Detect.looksLikeTask("domani piove"))
        assertFalse(Detect.looksLikeTask("ciao come stai"))
        assertEquals("domani passo a prenderti", Detect.lastSentence("Va bene. domani passo a prenderti"))
    }

    @Test fun copiedThings() {
        assertEquals(Detect.Kind.Iban, Detect.kindOf("IT60 X054 2811 1010 0000 0123 456"))
        assertNull(Detect.kindOf("IT60X0542811101000000123457")) // controllo sbagliato
        assertEquals(Detect.Kind.Phone, Detect.kindOf("+39 333 123 4567"))
        assertEquals(Detect.Kind.Email, Detect.kindOf("anna@esempio.it"))
        assertNull(Detect.kindOf("buongiorno"))
    }

    @Test fun abbreviations() {
        val map = Detect.parseAbbreviations("ind=Via Roma 1, Torino\n=vuota; firma = Marco")
        assertEquals(mapOf("ind" to "Via Roma 1, Torino", "firma" to "Marco"), map)
        assertEquals("ind", Detect.lastWord("il mio ind"))
    }
}
