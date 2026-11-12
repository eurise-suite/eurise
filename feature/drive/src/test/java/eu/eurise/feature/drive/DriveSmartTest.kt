package eu.eurise.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSmartTest {
    private val phone = Place(null, "Telefono")
    private fun file(name: String, size: Long, modified: Long, dir: String = "Download") =
        DriveItem(phone, "$dir/$name", name, folder = false, size = size, modified = modified)

    @Test fun copiesKeepTheNewest() {
        val a = file("Contratto.pdf", 1000, 1)
        val b = file("Contratto (1).pdf", 1000, 5)
        val c = file("contratto - Copia.pdf", 1000, 3, dir = "Documents")
        val other = file("Contratto.pdf", 999, 2, dir = "Documents")
        val copies = DriveSmart.duplicateCopies(listOf(a, b, c, other))
        assertEquals(setOf(a, c), copies.toSet())
    }

    @Test fun differentNumbersAreNotCopies() {
        assertTrue(DriveSmart.duplicateCopies(listOf(file("Fattura 12.pdf", 500, 1), file("Fattura 13.pdf", 500, 2))).isEmpty())
    }

    @Test fun scannerNames() {
        assertTrue(DriveSmart.isGenericName("Scan_0012.pdf"))
        assertTrue(DriveSmart.isGenericName("IMG_20261112_103000.pdf"))
        assertTrue(DriveSmart.isGenericName("CamScanner 11-12-2026 10.30.pdf"))
        assertTrue(DriveSmart.isGenericName("documento (3).pdf"))
        assertTrue(DriveSmart.isGenericName("20261112.pdf"))
        assertFalse(DriveSmart.isGenericName("Bolletta Enel novembre.pdf"))
        assertFalse(DriveSmart.isGenericName("Preventivo Villa.pdf"))
    }

    @Test fun snippetAroundTheWord() {
        val s = DriveSmart.snippet("Gentile cliente, la fattura n. 123 per la fornitura di luce scade il 16 dicembre.", "fornitura")
        assertTrue(s!!.contains("fornitura"))
        assertNull(DriveSmart.snippet("testo", "xy"))
        assertNull(DriveSmart.snippet("testo qualsiasi", "assente"))
    }

    @Test fun tidyDoesNotMoveCopies() {
        val a = file("Contratto.pdf", 1000, 1)
        val b = file("Contratto (1).pdf", 1000, 5)
        val t = tidyOf(listOf(a, b), now = 10)
        assertEquals(listOf(a), t.copies)
        assertEquals(listOf(b), t.documents)
    }
}
