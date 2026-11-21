package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceLogTest {
    private val day = 86_400_000L

    @Test
    fun nearbyContactsBecomeOneRow() {
        val one = ServiceLog.merge("", "breach", 1_000_000L)
        val two = ServiceLog.merge(one, "breach", 1_000_000L + 60_000L)
        assertEquals(listOf(ServiceEvent(1_000_000L, "breach", 2)), ServiceLog.parse(two))
    }

    @Test
    fun otherServiceOrLaterIsNewRow() {
        var t = ServiceLog.merge("", "sync", 0L + 40 * day)
        t = ServiceLog.merge(t, "breach", 40 * day + 1000)
        t = ServiceLog.merge(t, "breach", 40 * day + 20 * 60_000L)
        assertEquals(listOf("sync", "breach", "breach"), ServiceLog.parse(t).map { it.id })
    }

    @Test
    fun oldRowsAndJunkAreDropped() {
        val text = "${day}\tsync\t3\nrotto\n\t\t\n${40 * day}\tupload\t1"
        val merged = ServiceLog.merge(text, "upload", 40 * day + 5 * 60_000L)
        assertEquals(listOf(ServiceEvent(40 * day, "upload", 2)), ServiceLog.parse(merged))
    }
}
