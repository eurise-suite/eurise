package eu.eurise.feature.clock.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimersTest {

    @Test
    fun startPausePlus() {
        val t = ClockTimer(1, "Pasta", 7 * 60_000L).start(1_000)
        assertEquals(7 * 60_000L, t.remaining(1_000))
        val paused = t.pause(61_000)
        assertEquals(6 * 60_000L, paused.remainingMs)
        assertEquals(7 * 60_000L, paused.plus(60_000, 0).remainingMs)
        assertEquals(8 * 60_000L, t.plus(60_000, 1_000).remaining(1_000))
    }

    @Test
    fun pomodoroPhases() {
        var t: ClockTimer? = ClockTimer(2, "Pomodoro", 25 * 60_000L, pomodoro = Pomodoro(25, 5, 2)).start(0)
        assertEquals(true, t!!.isWork)
        t = t.nextPhase(0)
        assertEquals(5 * 60_000L, t!!.totalMs)
        t = t.nextPhase(0)
        assertEquals(true, t!!.isWork)
        assertNull(t.nextPhase(0))
    }

    @Test
    fun durations() {
        assertEquals("4:12", formatDuration(252_000))
        assertEquals("1:05:00", formatDuration(3_900_000))
        assertEquals("0:01", formatDuration(400))
    }
}
