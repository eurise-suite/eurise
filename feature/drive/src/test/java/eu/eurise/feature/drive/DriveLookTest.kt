package eu.eurise.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveLookTest {
    private val phone = Place(null, "Telefono")
    private fun file(name: String, days: Long, size: Long = 1000) =
        DriveItem(phone, "Download/$name", name, folder = false, size = size, modified = NOW - days * 86_400_000L)

    @Test fun tipiDiFile() {
        assertEquals(Kind.Pdf, Kind.of("PDF"))
        assertEquals(Kind.Sheet, Kind.of("xlsx"))
        assertEquals(Kind.Image, Kind.of("heic"))
        assertEquals(Kind.Other, Kind.of(""))
    }

    @Test fun riordinaDownload() {
        val t = tidyOf(
            listOf(file("bolletta.pdf", 3), file("foto.jpg", 90, 5_000), file("vecchio.zip", 200, 7_000), file("relazione.docx", 100)),
            now = NOW,
        )
        assertEquals(listOf("bolletta.pdf", "relazione.docx"), t.documents.map { it.name })
        assertEquals(listOf("foto.jpg", "vecchio.zip", "relazione.docx"), t.old.map { it.name })
        assertEquals(13_000L, t.oldBytes)
    }

    private companion object { const val NOW = 1_800_000_000_000L }
}
