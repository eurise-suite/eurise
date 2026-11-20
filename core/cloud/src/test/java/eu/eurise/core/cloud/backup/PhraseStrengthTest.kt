package eu.eurise.core.cloud.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseStrengthTest {
    @Test
    fun emptyIsZero() = assertEquals(0, PhraseStrength.score(""))

    @Test
    fun shortNeverPasses() = assertTrue(PhraseStrength.score("Xy7!q") <= 3)

    @Test
    fun repeatedLettersAreWeak() = assertTrue(PhraseStrength.score("aaaaaaaaaaaaaaaa") <= 2)

    @Test
    fun wordsWithSpacesAreStrong() = assertEquals(10, PhraseStrength.score("girasole lento sotto la pioggia 1987"))

    @Test
    fun aPlainPasswordIsMiddling() {
        val s = PhraseStrength.score("password12")
        assertTrue("password12 = $s", s in 2..4)
    }
}
