package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** I nomi delle copie dei file da caricare: mai fuori dalla loro cartella, mai caratteri riservati. */
class UploadNameTest {
    @Test fun keepsNormalNames() = assertEquals("Fattura 12.pdf", FilePicker.safeName("Fattura 12.pdf", null, 0))

    @Test fun dropsFolders() {
        assertEquals("passwd", FilePicker.safeName("../../etc/passwd", null, 0))
        assertEquals("a.txt", FilePicker.safeName("C:\\dati\\a.txt", null, 0))
        assertEquals("segreto", FilePicker.safeName("..segreto", null, 0))
    }

    @Test fun dropsReservedAndControlCharacters() =
        assertEquals("ab.jpg", FilePicker.safeName("a\u0000<b>.jpg", null, 0))

    @Test fun shortensKeepingTheExtension() {
        val name = FilePicker.safeName("x".repeat(300) + ".jpeg", null, 0)
        assertEquals(120, name.length)
        assertEquals(true, name.endsWith(".jpeg"))
    }
}
