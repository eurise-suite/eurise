package eu.eurise.core.designsystem.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test fun hexRoundTrip() {
        assertEquals(0xFF3FA39A.toInt(), ColorMath.parseHex("#3FA39A"))
        assertEquals(0xFF33AA99.toInt(), ColorMath.parseHex("3a9"))
        assertEquals("#3FA39A", ColorMath.hex(0xFF3FA39A.toInt()))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("zzzzzz"))
    }

    @Test fun hsvRoundTrip() {
        listOf(0xFF3FA39A, 0xFFFF0000, 0xFF000000, 0xFFFFFFFF, 0xFF8C2F39, 0xFF1F2A44).map { it.toInt() }.forEach { c ->
            assertEquals(ColorMath.hex(c), ColorMath.hex(ColorMath.toArgb(ColorMath.toHsv(c))))
        }
        val red = ColorMath.toHsv(0xFFFF0000.toInt())
        assertEquals(0f, red.hue, 0.01f)
        assertEquals(1f, red.saturation, 0.01f)
    }

    @Test fun contrastBounds() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF3FA39A.toInt(), 0xFF3FA39A.toInt()), 0.001)
        assertTrue(ColorMath.contrast(0xFF1F2A44.toInt(), 0xFFF4F4F1.toInt()) > 4.5)
    }
}
