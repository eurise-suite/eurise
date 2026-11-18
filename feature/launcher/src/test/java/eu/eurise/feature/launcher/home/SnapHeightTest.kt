package eu.eurise.feature.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapHeightTest {
    @Test
    fun snapsOnlyNearANeighbour() {
        assertEquals(200 to true, snapHeight(192, listOf(200)))
        assertEquals(200 to true, snapHeight(212, listOf(200)))
        assertEquals(180 to false, snapHeight(180, listOf(200)))
        assertEquals(150 to false, snapHeight(150, emptyList()))
        assertEquals(240 to true, snapHeight(236, listOf(200, 240)))
    }

    @Test
    fun onlyListKindsAdapt() {
        assertEquals(true, HomeKind.Agenda.adapts)
        assertEquals(true, HomeKind.TodayTasks.adapts)
        assertEquals(true, HomeKind.Calendar.adapts)
        assertEquals(true, HomeKind.Messages.adapts)
        assertEquals(true, HomeKind.Notes.adapts)
        assertEquals(false, HomeKind.Weather.adapts)
        assertEquals(false, HomeKind.AppWidget.adapts)
    }
}
