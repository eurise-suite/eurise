package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimeTest {
    @Test fun clockFormats() {
        assertEquals("0:00", clock(0))
        assertEquals("0:12", clock(12_000))
        assertEquals("4:05", clock(245_000))
        assertEquals("1:02:33", clock(3_753_000))
        assertEquals("0:00", clock(-50))
    }

    @Test fun speedsCycle() {
        assertEquals(1.5f, nextSpeed(1f))
        assertEquals(0.5f, nextSpeed(2f))
        assertEquals(1f, nextSpeed(0.5f))
        assertEquals(1.5f, nextSpeed(3f))
        assertEquals("1,5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
    }

    @Test fun seekStaysInside() {
        assertEquals(0L, seekTarget(4_000, -10_000, 34_000))
        assertEquals(34_000L, seekTarget(30_000, 10_000, 34_000))
        assertEquals(22_000L, seekTarget(12_000, 10_000, 34_000))
    }

    @Test fun trimHandlesKeepAGap() {
        val d = 10_000L
        val t = moveTrim(Trim(), handleStart = true, to = 0.95f, durationMs = d)
        assertEquals(0.9f, t.start, 1e-4f)
        val u = moveTrim(t, handleStart = false, to = 0.1f, durationMs = d)
        assertEquals(1f, u.end, 1e-4f)
        assertTrue(Trim().isFull)
        assertEquals(2_500L, Trim(0.25f, 0.5f).startMs(d))
    }
}
