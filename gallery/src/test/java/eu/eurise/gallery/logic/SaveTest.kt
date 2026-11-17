package eu.eurise.gallery.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveTest {
    @Test fun leSceltesiRicordanoMaNonSostituisci() {
        val o = SaveOptions(replace = true, format = SaveFormat.Webp, quality = 80, maxSide = 2048, stripLocation = false, stripCamera = true)
        val back = SaveOptions.decode(o.encode())
        assertFalse("ogni volta si riparte da una copia nuova", back.replace)
        assertEquals(SaveFormat.Webp, back.format)
        assertEquals(80, back.quality)
        assertEquals(2048, back.maxSide)
        assertFalse(back.stripLocation)
        assertTrue(back.stripCamera)
        assertEquals(SaveOptions(), SaveOptions.decode("rotto"))
        assertTrue(SaveOptions().stripLocation)
    }

    @Test fun sostituendoSiTieneIlFormatoDellOriginale() {
        assertEquals(SaveFormat.Png, SaveOptions(replace = true, format = SaveFormat.Jpeg).formatFor("image/png"))
        assertEquals(SaveFormat.Webp, SaveOptions(format = SaveFormat.Webp).formatFor("image/jpeg"))
        assertEquals(SaveFormat.Jpeg, SaveOptions(replace = true).formatFor("image/heic"))
    }

    @Test fun grandezza() {
        assertEquals(4000 to 3000, scaledSize(4000, 3000, 0))
        assertEquals(2048 to 1536, scaledSize(4000, 3000, 2048))
        assertEquals(810 to 1080, scaledSize(3000, 4000, 1080))
        assertEquals(800 to 600, scaledSize(800, 600, 1080))
    }

    @Test fun datiCheEscono() {
        val base = ExifTags.toCopy(SaveOptions())
        assertTrue("Model" in base)
        assertFalse("GPSLatitude" in base)
        val all = ExifTags.toCopy(SaveOptions(stripLocation = false))
        assertTrue("GPSLatitude" in all)
        val bare = ExifTags.toCopy(SaveOptions(stripCamera = true))
        assertFalse("Model" in bare)
        assertTrue("DateTimeOriginal" in bare)
        assertFalse("Orientation" in all)
    }

    @Test fun originaliConservati() {
        val k = listOf(KeptOriginal("content://media/1", "/data/originali/1", "image/jpeg", 1_000L))
        assertEquals(k, KeptCodec.decode(KeptCodec.encode(k)))
        assertEquals(1_000L + 30L * 86_400_000L, k[0].expiresAt)
        assertTrue(KeptCodec.decode("x\ty").isEmpty())
    }
}
