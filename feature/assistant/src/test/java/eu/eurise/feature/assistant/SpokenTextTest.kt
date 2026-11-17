package eu.eurise.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    @Test fun firstSentencesOnly() {
        assertEquals("Annotato.", SpokenText.brief("Annotato. Carta canta, diceva mia zia.", sentences = 1))
        assertEquals("Uno. Due.", SpokenText.brief("Uno. Due. Tre.", sentences = 2))
    }

    @Test fun longSentenceIsCutAtAComma() {
        val long = "La capitale è Roma, " + "che ha una storia lunghissima ".repeat(10)
        val out = SpokenText.brief(long, maxChars = 50)
        assertEquals("La capitale è Roma…", out)
    }

    @Test fun shortStaysAsIs() {
        assertEquals("Subito.", SpokenText.brief("Subito."))
        assertEquals("", SpokenText.brief("   "))
    }
}
