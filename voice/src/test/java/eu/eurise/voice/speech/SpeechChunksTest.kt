package eu.eurise.voice.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test fun firstPieceIsShort() {
        val text = "Domani alle dieci ha il dentista in via Roma, poi alle quindici la riunione con Marco. Nient'altro."
        val parts = SpeechChunks.split(text)
        assertEquals("Domani alle dieci ha il dentista in via Roma,", parts[0])
        assertEquals("poi alle quindici la riunione con Marco.", parts[1])
        assertEquals("Nient'altro.", parts[2])
    }

    @Test fun shortSentencesStayWhole() {
        assertEquals(listOf("Annotato.", "Altro?"), SpeechChunks.split("Annotato. Altro?"))
    }

    @Test fun longWithoutCommasStaysWhole() {
        val s = "parola ".repeat(30).trim() + "."
        assertEquals(listOf(s), SpeechChunks.split(s))
    }

    @Test fun laterPiecesUnderMaxWhenPossible() {
        val s = "Prima. " + (1..12).joinToString(", ") { "elemento numero $it della lista" } + "."
        val parts = SpeechChunks.split(s)
        assertTrue(parts.drop(1).all { it.length <= SpeechChunks.MAX })
    }
}
