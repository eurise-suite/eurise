package eu.eurise.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryTest {
    private val day = 86_400_000L
    private val casa = Spot("kdrive", "Documenti/Casa")
    private val bollette = Spot("kdrive", "Documenti/Bollette")
    private val scansioni = Spot(null, "Documents/Eurise/Scansioni")

    @Test
    fun proposesTheFolderWithASimilarFileAndTheLastUsed() {
        val m = SaveMemory()
        m.remember("scan", casa, "2022-03-01_Contratto-affitto.pdf", 0)
        m.remember("scan", bollette, "2026-10-04_Bolletta-luce.pdf", 300 * day)
        m.remember("scan", scansioni, "2026-11-10_Scansione.pdf", 400 * day)

        val s = m.suggest("scan", "2026-11-11_Contratto-affitto", now = 401 * day)
        assertEquals(casa, s[0].spot)
        assertEquals("2022-03-01_Contratto-affitto.pdf", s[0].because)
        assertEquals(scansioni, s[1].spot)
        assertTrue(s[1].lastUsed)
    }

    @Test
    fun folderNameCountsAndKindsStaySeparate() {
        val m = SaveMemory()
        m.remember("scan", bollette, "a.pdf", 10 * day)
        m.remember("office", Spot(null, "Documents/Lavoro"), "Preventivo.docx", 20 * day)
        val s = m.suggest("scan", "Bollette gas novembre", now = 21 * day)
        assertEquals(bollette, s.first().spot)
        assertNull(s.first().because)
        assertTrue(s.none { it.spot.path == "Documents/Lavoro" })
    }

    @Test
    fun wordsIgnoreAccentsDatesAndShortWords() {
        assertEquals(setOf("attivita", "pagare", "affitto"), SaveMemory.words("2026-11-11 Attività: pagare l'affitto di casa".replace(" casa", "")))
    }

    @Test
    fun recordsSurviveEncoding() {
        val r = SaveMemory.Record("scan", casa, "Contratto.pdf", 42)
        assertEquals(r, SaveMemory.decode(SaveMemory.encode(r)))
        val phone = SaveMemory.Record("scan", scansioni, "x.pdf", 1)
        assertEquals(phone, SaveMemory.decode(SaveMemory.encode(phone)))
        assertNull(SaveMemory.decode("rotto"))
    }

    @Test
    fun namesAreCleanAndFree() {
        assertEquals("Fattura 3-2026.pdf", Names.clean("Fattura 3/2026", "pdf"))
        assertEquals("Contratto.pdf", Names.clean("Contratto.pdf", "pdf"))
        assertEquals("Contratto (3).pdf", Names.free("Contratto.pdf", setOf("Contratto.pdf", "Contratto (2).pdf")))
        assertEquals(listOf("", "Documenti", "Documenti/Casa"), casa.crumbs.map { it.path })
    }
}
