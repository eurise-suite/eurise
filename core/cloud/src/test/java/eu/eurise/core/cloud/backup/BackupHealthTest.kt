package eu.eurise.core.cloud.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackupHealthTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L

    @Test
    fun weeklyOnTimeOrSlightlyLateSaysNothing() {
        assertNull(BackupHealth.lateDays(now - 7 * day, 7, now))
        assertNull(BackupHealth.lateDays(now - 9 * day, 7, now))
    }

    @Test
    fun weeklyThreeDaysLateWarns() {
        assertEquals(3, BackupHealth.lateDays(now - 10 * day, 7, now))
        assertEquals(23, BackupHealth.lateDays(now - 30 * day, 7, now))
    }

    @Test
    fun manualOrUnknownNeverWarns() {
        assertNull(BackupHealth.lateDays(now - 90 * day, 0, now))
        assertNull(BackupHealth.lateDays(null, 1, now))
        assertNull(BackupHealth.lateDays(now + day, 1, now))
    }
}
