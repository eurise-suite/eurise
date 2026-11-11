package eu.eurise.feature.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LogoMaskTest {
    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1F2A44.toInt()

    private fun square(bg: Int, fg: Int, size: Int = 40, from: Int = 12, to: Int = 28) =
        IntArray(size * size) { i -> val x = i % size; val y = i / size; if (x in from until to && y in from until to) fg else bg }

    @Test fun logoOnFlatBackground() {
        val mask = LogoMask.maskOf(square(white, blue), 40, 40)
        assertNotNull(mask)
        mask!!
        assertEquals(255, mask[20 * 40 + 20].toInt() and 0xFF)
        assertEquals(0, mask[2 * 40 + 2].toInt() and 0xFF)
    }

    @Test fun transparentBackgroundUsesAlpha() {
        val mask = LogoMask.maskOf(square(0x00000000, blue), 40, 40)!!
        assertEquals(255, mask[20 * 40 + 20].toInt() and 0xFF)
        assertEquals(0, mask[0].toInt() and 0xFF)
    }

    @Test fun photoLikeBorderIsRejected() {
        val pixels = IntArray(40 * 40) { i -> if ((i / 3) % 2 == 0) white else blue }
        assertNull(LogoMask.maskOf(pixels, 40, 40))
    }

    @Test fun logoFillingEverythingIsRejected() {
        assertNull(LogoMask.maskOf(square(white, blue, from = 3, to = 37), 40, 40))
    }
}
