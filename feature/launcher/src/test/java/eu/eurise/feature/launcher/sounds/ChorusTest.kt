package eu.eurise.feature.launcher.sounds

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ChorusTest {
    /** Strofe tranquille e diverse tra loro, un ritornello forte che torna uguale due volte: lo trova. */
    @Test fun findsTheLoudRepeatedPart() {
        val fps = 10
        val parts = listOf(
            verse(30, 1.0), chorus(16), verse(30, 2.3), chorus(16), verse(20, 3.7),
        )
        val loud = parts.flatMap { it.first }.toFloatArray()
        val rough = parts.flatMap { it.second }.toFloatArray()
        val start = Chorus.find(loud, rough, fps, 15)
        val first = 30 * fps
        val second = (30 + 16 + 30) * fps
        assertTrue("inizio $start", start in first - fps..first + fps || start in second - fps..second + fps)
    }

    @Test fun shortSongStartsAtZero() {
        assertTrue(Chorus.find(FloatArray(50) { 0.5f }, FloatArray(50), 10, 15) == 0)
    }

    private fun verse(seconds: Int, seed: Double): Pair<List<Float>, List<Float>> {
        val n = seconds * 10
        return List(n) { (0.3 + 0.1 * sin(it * 0.37 * seed)).toFloat() } to List(n) { (0.4 + 0.2 * sin(it * 0.53 * seed + seed)).toFloat() }
    }

    private fun chorus(seconds: Int): Pair<List<Float>, List<Float>> {
        val n = seconds * 10
        return List(n) { (0.8 + 0.15 * sin(it * 0.9)).toFloat() } to List(n) { (0.6 + 0.2 * sin(it * 0.45)).toFloat() }
    }
}
