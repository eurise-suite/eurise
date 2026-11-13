package eu.eurise.office.rapida

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTest {
    private val ns = "xmlns:w=\"${Docx.W}\" xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\""

    private fun docx(body: String, extra: Map<String, String> = emptyMap()): Bundle {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val files = linkedMapOf(
                "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>",
                "word/document.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document $ns><w:body>$body<w:sectPr/></w:body></w:document>",
                "word/styles.xml" to "<w:styles xmlns:w=\"${Docx.W}\"><w:style w:styleId=\"Titolo1\"><w:name w:val=\"heading 1\"/></w:style></w:styles>",
                "word/_rels/document.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>",
            ) + extra
            files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
        }
        return Bundle.read(out.toByteArray())
    }

    private val sample = """
        <w:p><w:pPr><w:pStyle w:val="Titolo1"/></w:pPr><w:r><w:t>Preventivo</w:t></w:r></w:p>
        <w:p w14:paraId="1A"><w:r><w:t xml:space="preserve">I lavori inizieranno entro </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>quindici giorni</w:t></w:r><w:r><w:t xml:space="preserve"> dalla firma.</w:t></w:r></w:p>
        <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Voce</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>1.200,00</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
        <w:p><w:r><w:drawing/></w:r><w:r><w:t>Con figura</w:t></w:r></w:p>
        <w:p/>
    """.trimIndent()

    @Test fun readsHeadingsSpansTables() {
        val doc = Docx.read(docx(sample))
        val paras = doc.paragraphs()
        assertEquals(1, (doc.blocks[0] as Para).level)
        assertEquals("I lavori inizieranno entro quindici giorni dalla firma.", paras[1].text)
        assertTrue(paras[1].spans[1].bold)
        assertEquals("1.200,00", (doc.blocks[2] as Table).rows[0][1][0].text)
        assertFalse(paras[4].editable)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), paras.map { it.id })
    }

    @Test fun editKeepsFormattingAroundAndRestOfFile() {
        val before = docx(sample)
        val after = Docx.edit(before, 1, "I lavori inizieranno entro dieci giorni lavorativi dalla firma.")
        val p = Docx.read(after).paragraphs()[1]
        assertEquals("I lavori inizieranno entro dieci giorni lavorativi dalla firma.", p.text)
        // Il tratto in grassetto resta in grassetto, il resto no.
        assertTrue(p.spans.first { it.text.contains("dieci") }.bold)
        assertFalse(p.spans.first().bold)
        // Le altre parti del file non cambiano.
        val oldXml = before.text("word/document.xml")!!
        val newXml = after.text("word/document.xml")!!
        assertTrue(newXml.startsWith(oldXml.substring(0, oldXml.indexOf("<w:p w14:paraId"))))
        assertTrue(newXml.endsWith(oldXml.substring(oldXml.indexOf("<w:tbl>"))))
        assertEquals(before.text("word/styles.xml"), after.text("word/styles.xml"))
    }

    @Test fun editTableCellTabsAndEmptyParagraph() {
        var b = Docx.edit(docx(sample), 3, "1.350,00")
        assertEquals("1.350,00", Docx.read(b).paragraphs()[3].text)
        b = Docx.edit(b, 5, "Nuovo\ttesto\nsu due righe")
        assertEquals("Nuovo\ttesto\nsu due righe", Docx.read(b).paragraphs()[5].text)
        b = Docx.edit(b, 5, "Nuovo testo\nsu due righe")
        assertEquals("Nuovo testo\nsu due righe", Docx.read(b).paragraphs()[5].text)
    }

    @Test(expected = NotEditable::class) fun refusesParagraphWithDrawing() {
        Docx.edit(docx(sample), 4, "altro")
    }

    @Test fun boldWholeParagraphInSchemaOrder() {
        val b = Docx.edit(docx(sample), 1, Docx.read(docx(sample)).paragraphs()[1].text, Style(bold = true, underline = true))
        val p = Docx.read(b).paragraphs()[1]
        assertTrue(p.allBold && p.allUnderline)
        assertTrue(b.text("word/document.xml")!!.contains("<w:rPr><w:b/><w:bCs/><w:u w:val=\"single\"/></w:rPr>"))
    }

    @Test fun commentCreatesPartsAndShowsUp() {
        val b = Docx.comment(docx(sample), 1, "Va bene così?", "Mario Rossi")
        assertTrue(b.names.contains("word/comments.xml"))
        assertTrue(b.text("[Content_Types].xml")!!.contains("/word/comments.xml"))
        assertTrue(b.text("word/_rels/document.xml.rels")!!.contains("comments.xml"))
        assertEquals(listOf("Mario Rossi: Va bene così?"), Docx.read(b).paragraphs()[1].comments)
    }

    @Test fun newParagraphAfterKeepsStyle() {
        var b = Docx.insertAfter(docx(sample), 0, "Sottotitolo")
        b = Docx.insertAfter(b, 1, "")
        val ps = Docx.read(b).paragraphs()
        assertEquals("Sottotitolo", ps[1].text)
        assertEquals(1, ps[1].level)
        assertEquals("", ps[2].text)
        assertEquals("I lavori inizieranno entro quindici giorni dalla firma.", ps[3].text)
    }
}
