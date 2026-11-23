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
    fun grainFilterOnlyWithManyIso() {
        assertEquals(0, r.grainThreshold(400, 1))
        assertEquals(35, r.grainThreshold(2200, 1))
        assertEquals(0, r.grainThreshold(2200, 6))
        assertEquals(10, r.grainThreshold(2700, 4))
    }

    @Test
    fun sharpeningLeavesGrainAlone() {
        val w = 5
        val flat = Array(5) { IntArray(w) { 100 } }.also { it[2][2] = 103 }
        assertEquals(0f, r.detail(flat, 2, w))
        val line = Array(5) { IntArray(w) { 100 } }.also { it[2][2] = 150 }
        assert(r.detail(line, 2, w) > 40f)
    }

    @Test
    fun grainSmoothsButEdgesStay() {
        val w = 5
        // Muro con grana: il pixel scuro al centro va verso la media.
        val wall = Array(5) { IntArray(w) { 100 } }.also { it[2][2] = 90 }
        assert(r.smooth(wall, 2, w, 30) > 7f)
        // Bordo netto (0 | 200): il pixel resta dov'è.
        val edge = Array(5) { IntArray(w) { x -> if (x < 2) 0 else 200 } }
        assertEquals(0f, r.smooth(edge, 2, w, 30))
    }

    @Test
    fun neutralPhotosStayAsTheyAre() {
        assertNull(r.gains(100.0, 100.0, 100.0))
        assertNull(r.gains(105.0, 100.0, 95.0))
    }

    @Test
    fun strongWarmCastGoesHalfWay() {
        val (gr, gb) = r.gains(102.0, 82.0, 47.0)!!
        assert(gr < 1f && gr >= 0.8f) { gr }
        assert(gb > 1.25f && gb <= 1.6f) { gb }
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
        assert(g in 0.8..0.9) { g }
        // La mediana sale verso 95, il bianco resta bianco.
        val mid = 255 * Math.pow(76 / 255.0, g)
        assert(mid in 90.0..100.0) { mid }
        assertEquals(255.0, 255 * Math.pow(1.0, g), 0.001)
    }
}

class NotteTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun ghostThresholdFollowsTheGrain() {
        assertEquals(eu.eurise.camera.data.Notte.GHOST, eu.eurise.camera.data.Notte.ghostFor(2.0))
        assertEquals(60, eu.eurise.camera.data.Notte.ghostFor(16.0))
        assertEquals(eu.eurise.camera.data.Notte.MAX_GHOST, eu.eurise.camera.data.Notte.ghostFor(40.0))
    }

    @Test
    fun findsTheHandShift() {
        val w = 64; val h = 48
        val ref = ByteArray(w * h) { i -> (((i % w) * 7 + (i / w) * 13) % 251).toByte() }
        // Il fotogramma è il riferimento spostato di (3, -2): frame[y+dy][x+dx] = ref[y][x].
        val frame = ByteArray(w * h) { i ->
            val x = i % w - 3; val y = i / w + 2
            if (x in 0 until w && y in 0 until h) ref[y * w + x] else 0
        }
        assertEquals(3 to -2, eu.eurise.camera.data.Notte.shift(ref, frame, w, h, range = 5, step = 1))
    }

    @Test
    fun averagesNoiseAndIgnoresWhatMoved() {
        val n = eu.eurise.camera.data.Notte(2, 1)
        n.addReference(0, intArrayOf(gray(100), gray(100)))
        val ref = intArrayOf(gray(100), gray(100))
        n.add(0, ref, intArrayOf(gray(110), gray(250)), 0, last = true)
        n.add(0, ref, intArrayOf(gray(90), gray(250)), 0, last = true)
        val out = IntArray(2)
        n.result(0, out)
        assertEquals(100, out[0] and 0xFF)   // media di 100, 110, 90
        assertEquals(100, out[1] and 0xFF)   // 250 è «mosso»: resta il riferimento
        assertEquals(3, n.frames)
    }
}

class GpsTest {
    @Test
    fun degreesMinutesSeconds() {
        assertEquals("45/1,27/1,51120/1000", eu.eurise.camera.data.MediaOut.dms(45.4642))
        assertEquals("9/1,11/1,24000/1000", eu.eurise.camera.data.MediaOut.dms(-9.19))
    }
}
