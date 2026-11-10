package eu.eurise.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {
    private val block = 1600 // 100 ms a 16 kHz

    private fun run(pattern: String): List<Int> {
        val c = Chunker()
        val cuts = mutableListOf<Int>()
        pattern.forEachIndexed { i, ch -> c.feed(if (ch == 'v') 0.1f else 0.002f, (i + 1) * block)?.let(cuts::add) }
        return cuts
    }

    @Test fun cutsInPausesAfterEnoughSpeech() {
        // 0,5 s di silenzio, 3 s di voce, 0,8 s di pausa, 2 s di voce
        val cuts = run("s".repeat(5) + "v".repeat(30) + "s".repeat(8) + "v".repeat(20))
        assertEquals(1, cuts.size)
        assertTrue(cuts[0] in 35 * block..43 * block)
    }

    @Test fun shortPhrasesAreNotCut() {
        assertTrue(run("s".repeat(5) + "v".repeat(10) + "s".repeat(10) + "v".repeat(10)).isEmpty())
    }

    @Test fun silenceAloneIsNeverCut() {
        assertTrue(run("s".repeat(400)).isEmpty())
    }

    @Test fun longSpeechIsCutAnyway() {
        assertEquals(1, run("v".repeat(260)).size)
    }

    @Test fun contextFollowsLength() {
        assertEquals(384, Chunker.audioCtx(16_000 * 3))
        assertEquals(704, Chunker.audioCtx(16_000 * 12))
        assertEquals(0, Chunker.audioCtx(16_000 * 29))
        assertNull(null)
    }
}
