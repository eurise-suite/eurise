package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointTest {
    private val block = 1_600 // 100 ms a 16 kHz

    private fun run(levels: List<Float>): List<Endpoint.Event> {
        val e = Endpoint()
        return levels.mapIndexed { i, rms -> e.feed(rms, (i + 1) * block) }
    }

    @Test
    fun speechThenPauseFinishes() {
        val events = run(List(5) { 0.002f } + List(10) { 0.08f } + List(10) { 0.002f })
        assertEquals(Endpoint.Event.Started, events[6])
        val finished = events.indexOf(Endpoint.Event.Finished)
        assertEquals(15 + 8, finished)
    }

    @Test
    fun silenceGivesUp() {
        val events = run(List(70) { 0.002f })
        assertEquals(Endpoint.Event.NoSpeech, events.first { it != Endpoint.Event.None })
    }

    @Test
    fun shortPauseDoesNotFinish() {
        val events = run(List(10) { 0.08f } + List(4) { 0.002f } + List(10) { 0.08f })
        assertEquals(false, events.contains(Endpoint.Event.Finished))
    }
}
