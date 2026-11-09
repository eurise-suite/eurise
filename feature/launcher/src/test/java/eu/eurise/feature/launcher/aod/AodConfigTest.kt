package eu.eurise.feature.launcher.aod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AodConfigTest {
    private val on = AodConfig(enabled = true)
    private val noon = 12 * 60

    @Test
    fun offMeansNeverOn() = assertFalse(shouldShowAod(AodConfig(), AodTrigger.Notification, noon, 90, false))

    @Test
    fun popupWakesOnEventsButNotWhenTheScreenGoesOff() {
        assertTrue(shouldShowAod(on, AodTrigger.Notification, noon, 90, false))
        assertTrue(shouldShowAod(on, AodTrigger.ScreenOn, noon, 90, false))
        assertFalse(shouldShowAod(on, AodTrigger.ScreenOff, noon, 90, false))
    }

    @Test
    fun alwaysComesBackWhenTheScreenGoesOff() =
        assertTrue(shouldShowAod(on.copy(mode = AodMode.Always), AodTrigger.ScreenOff, noon, 90, false))

    @Test
    fun chargingModeNeedsTheCharger() {
        val c = on.copy(mode = AodMode.Charging)
        assertFalse(shouldShowAod(c, AodTrigger.Notification, noon, 90, false))
        assertTrue(shouldShowAod(c, AodTrigger.Charger, noon, 90, true))
    }

    @Test
    fun monthStartsOnItsWeekday() {
        // Novembre 2026 inizia di domenica: sei caselle vuote prima dell'1.
        val cells = monthCells(java.time.YearMonth.of(2026, 11))
        assertEquals(36, cells.size)
        assertEquals(1, cells[6]?.dayOfMonth)
    }

    @Test
    fun nightCrossesMidnight() {
        assertTrue(isNight(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(isNight(3 * 60, 23 * 60, 7 * 60))
        assertFalse(isNight(7 * 60, 23 * 60, 7 * 60))
        assertFalse(shouldShowAod(on, AodTrigger.Notification, 3 * 60, 90, false))
    }

    @Test
    fun lowBatteryStopsItUnlessCharging() {
        assertFalse(shouldShowAod(on, AodTrigger.Notification, noon, 19, false))
        assertTrue(shouldShowAod(on, AodTrigger.Notification, noon, 19, true))
    }

    @Test
    fun alwaysOnShowsOnlyTheFirstBubbleOffCharger() {
        val c = on.copy(mode = AodMode.Always)
        assertEquals(listOf(AodBubble.NextEvent), visibleBubbles(c, false))
        assertEquals(4, visibleBubbles(c, true).size)
        assertEquals(4, visibleBubbles(on, false).size)
    }
}
