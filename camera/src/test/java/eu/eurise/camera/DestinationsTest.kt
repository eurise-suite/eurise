package eu.eurise.camera

import eu.eurise.camera.data.DestinationState
import eu.eurise.camera.data.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationsTest {
    @Test
    fun cleanKeepsReadableNames() {
        assertEquals("Cantiere Verdi", Destinations.clean("  Cantiere   Verdi "))
        assertEquals("Rossi & figli", Destinations.clean("Rossi & figli"))
        assertEquals("etcpasswd", Destinations.clean("../etc/passwd"))
        assertNull(Destinations.clean(" / "))
        assertEquals(Destinations.MAX_LENGTH, Destinations.clean("a".repeat(80))!!.length)
    }

    @Test
    fun relativePath() {
        assertEquals("DCIM/Camera", Destinations.relativePath(null))
        assertEquals("DCIM/Eurise/Bagno Verdi", Destinations.relativePath("Bagno Verdi"))
    }

    @Test
    fun tapGoesBackToTheLastOne() {
        val s = DestinationState().add("Verdi")
        assertEquals("Verdi", s.current)
        val back = s.toggle()
        assertNull(back.current)
        assertEquals("Verdi", back.toggle().current)
    }

    @Test
    fun tapWithNothingElseStays() {
        val s = DestinationState()
        assertEquals(s, s.toggle())
    }

    @Test
    fun addingTwiceKeepsOne() {
        val s = DestinationState().add("Verdi").add("verdi")
        assertEquals(listOf("Verdi"), s.folders)
    }

    @Test
    fun removingClearsEveryReference() {
        val s = DestinationState().add("Verdi").star("Verdi").remove("Verdi")
        assertEquals(emptyList<String>(), s.folders)
        assertNull(s.current)
        assertNull(s.favorite)
        assertEquals(s, s.toggle())
    }

    @Test
    fun startsOnTheFavorite() {
        val s = DestinationState().add("Verdi").select(null).star("Verdi")
        assertEquals("Verdi", s.atStart().current)
        assertNull(DestinationState().add("Verdi").select(null).atStart().current)
    }
}

class ZoomTest {
    @Test
    fun stopsFollowTheLenses() {
        assertEquals(listOf(1f), eu.eurise.camera.ui.zoomStops(1f, 1.9f))
        assertEquals(listOf(0.6f, 1f, 2f), eu.eurise.camera.ui.zoomStops(0.6f, 4f))
        assertEquals(listOf(1f, 2f, 5f), eu.eurise.camera.ui.zoomStops(1f, 10f))
    }

    @Test
    fun labelsAreShortAndItalian() {
        assertEquals("1", eu.eurise.camera.ui.zoomLabel(1f))
        assertEquals("0,6", eu.eurise.camera.ui.zoomLabel(0.6f))
        assertEquals("2,4", eu.eurise.camera.ui.zoomLabel(2.43f))
    }

    @Test
    fun fileNamesSortByTime() {
        val name = eu.eurise.camera.data.MediaOut.fileName("IMG", "jpg", 0L)
        assert(name.startsWith("IMG_19700101_") || name.startsWith("IMG_19691231_")) { name }
        assert(name.endsWith(".jpg"))
    }
}

class RitoccoTest {
    private val r = eu.eurise.camera.data.Ritocco

    @Test
    fun neutralPhotosStayAsTheyAre() {
        assertNull(r.gains(100.0, 100.0, 100.0))
        assertNull(r.gains(105.0, 100.0, 95.0))
    }

    @Test
    fun strongWarmCastLeavesALittleWarmth() {
        val (gr, gb) = r.gains(102.0, 82.0, 47.0)!!
        assert(gr < 1f && gr >= 0.8f) { gr }
        assert(gb > 1.4f && gb <= 1.6f) { gb }
        assert(102 * gr > 47 * gb) { "${102 * gr} ${47 * gb}" }
    }

    @Test
    fun pinkFrontCameraCastIsCorrectedToo() {
        val (gr, gb) = r.gains(110.0, 90.0, 108.0)!!
        assert(gr < 1f && gb < 1f) { "$gr $gb" }
    }

    @Test
    fun blurKeepsAFlatGridFlat() {
        val a = FloatArray(12) { 7f }
        r.boxBlur(a, 4, 3, 1)
        a.forEach { assertEquals(7f, it, 0.0001f) }
    }

    @Test
    fun darkPhotosGetBrighterMidtones() {
        assertNull(r.gamma(120))
        val g = r.gamma(76)!!
        assert(g in 0.7..0.8) { g }
        // La mediana sale verso 110, il bianco resta bianco.
        val mid = 255 * Math.pow(76 / 255.0, g)
        assert(mid in 105.0..115.0) { mid }
        assertEquals(255.0, 255 * Math.pow(1.0, g), 0.001)
    }
}
