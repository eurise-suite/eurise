package eu.eurise.office.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLogicTest {
    @Test fun fiscalCodeCheckCharacter() {
        assertTrue(Sensitive.fiscalCheck("RSSMRA80A01H501U"))
        assertFalse(Sensitive.fiscalCheck("RSSMRA80A01H501A"))
    }

    @Test fun ibanModulo97() {
        assertTrue(Sensitive.ibanCheck("IT60X0542811101000000123456"))
        assertFalse(Sensitive.ibanCheck("IT61X0542811101000000123456"))
    }

    @Test fun findsBothInText() {
        val text = "Conduttore Mario Rossi, CF RSSMRA80A01H501U, IBAN IT60 X054 2811 1010 0000 0123 456 per il canone."
        val hits = Sensitive.find(text)
        assertEquals(listOf(SensitiveHit.Kind.FiscalCode, SensitiveHit.Kind.Iban), hits.map { it.kind })
        assertEquals("RSSMRA80A01H501U", text.substring(hits[0].start, hits[0].end))
    }

    @Test fun ignoresLookalikes() {
        assertTrue(Sensitive.find("Codice ABCDEF12G34H567I e conto IT00 0000 0000 0000").isEmpty())
    }

    @Test fun geometryRoundTripAllRotations() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val g = PageGeometry(10f, 20f, 595f, 842f, rotation)
            val p = ViewPoint(0.25f, 0.7f)
            val (x, y) = g.toPdf(p)
            val back = g.toView(x, y)
            assertEquals(p.u, back.u, 1e-4f)
            assertEquals(p.v, back.v, 1e-4f)
        }
    }

    @Test fun topLeftOfUnrotatedPage() {
        val g = PageGeometry(0f, 0f, 600f, 800f, 0)
        val (x, y) = g.toPdf(ViewPoint(0f, 0f))
        assertEquals(0f, x, 0f)
        assertEquals(800f, y, 0f)
        assertEquals(0.75f, g.displayAspect, 1e-6f)
        assertEquals(800f / 600f, PageGeometry(0f, 0f, 600f, 800f, 90).displayAspect, 1e-6f)
    }
}
