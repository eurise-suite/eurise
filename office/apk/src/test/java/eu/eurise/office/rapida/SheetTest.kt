package eu.eurise.office.rapida

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTest {
    private fun zip(files: Map<String, String>): Bundle {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return Bundle.read(out.toByteArray())
    }

    private fun xlsx(): Bundle = zip(linkedMapOf(
        "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/xl/calcChain.xml" ContentType="x"/></Types>""",
        "xl/workbook.xml" to """<workbook xmlns="${Xlsx.S}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="2026" sheetId="1" r:id="rId1"/><sheet name="Grafico" sheetId="2" r:id="rId2"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="ws" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="ws" Target="worksheets/sheet2.xml"/><Relationship Id="rId9" Type="cc" Target="calcChain.xml"/></Relationships>""",
        "xl/sharedStrings.xml" to """<sst xmlns="${Xlsx.S}"><si><t>Affitto</t></si><si><t>Luce</t></si><si><t>Totale</t></si></sst>""",
        "xl/styles.xml" to """<styleSheet xmlns="${Xlsx.S}"><numFmts><numFmt numFmtId="164" formatCode="#,##0.00\ &quot;€&quot;"/></numFmts><cellXfs><xf numFmtId="0"/><xf numFmtId="164"/><xf numFmtId="14"/></cellXfs></styleSheet>""",
        "xl/calcChain.xml" to """<calcChain xmlns="${Xlsx.S}"><c r="B4" i="1"/></calcChain>""",
        "xl/worksheets/sheet1.xml" to """<worksheet xmlns="${Xlsx.S}"><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" s="1"><v>750</v></c></row><row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2" s="1"><v>79.1</v></c></row><row r="3"><c r="C3" s="2"><v>46337</v></c></row><row r="4"><c r="A4" t="s"><v>2</v></c><c r="B4" s="1"><f>SUM(B1:B2)</f><v>829.1</v></c><c r="C4"><f>B4*2</f><v>1658.2</v></c><c r="D4"><f>IF(B4&gt;0,1,0)</f><v>1</v></c></row></sheetData></worksheet>""",
        "xl/worksheets/sheet2.xml" to """<worksheet xmlns="${Xlsx.S}"><sheetData><row r="1"><c r="A1"><f>'2026'!B4</f><v>829.1</v></c></row></sheetData></worksheet>""",
    ))

    @Test fun formulaEngine() {
        assertEquals(10.0, Formula.parse("SUM(A1:A2)+2*B1").eval { c, r -> mapOf((0 to 0) to 3.0, (0 to 1) to 1.0, (1 to 0) to 3.0)[c to r] }, 1e-9)
        assertEquals("SUM(C2:C9)", Formula.fromOdf("of:=SUM([.C2:.C9])"))
        assertNull(Formula.fromOdf("of:=[Altro.B2]+1"))
        assertEquals("of:=SUM([.C2:.C9];[.D1])", Formula.toOdf("SUM(C2:C9,D1)"))
        assertTrue(runCatching { Formula.parse("IF(A1>0,1,0)") }.isFailure)
    }

    @Test fun inputAndLooks() {
        assertEquals(Cells.Input.Number(1234.5, false), Cells.input("1.234,50"))
        assertEquals(Cells.Input.Number(84.2, false), Cells.input("84,20 €"))
        assertEquals("1.844,70 €", NumberLook.from("1.477,75 €").format(1844.7))
        assertEquals("1.844,70 €", NumberLook.fromCode("#,##0.00\\ \"€\"").format(1844.7))
        assertEquals("B5", Cells.ref(1, 4))
    }

    @Test fun xlsxReadsAndRecalculates() {
        val b = xlsx()
        val s = Xlsx.read(b).sheets[0]
        assertEquals("Affitto", s[0, 0]!!.display)
        assertEquals("750,00 €", s[1, 0]!!.display)
        assertEquals("11/11/2026", s[2, 2]!!.display)
        assertEquals("829,10 €", s[1, 3]!!.display)

        val r = Xlsx.setCell(b, 0, 1, 1, "84,20")
        val after = Xlsx.read(r.bundle).sheets[0]
        assertEquals(84.2, after[1, 1]!!.number!!, 1e-9)
        assertEquals("834,20 €", after[1, 3]!!.display)
        assertEquals(1668.4, after[2, 3]!!.number!!, 1e-9)
        assertTrue(r.recalculated.containsAll(listOf("B4", "C4")))
        assertTrue("D4" in r.pending && "Grafico" in r.pending)
        // Il ricalcolo all'apertura e niente catena di calcolo.
        assertTrue(r.bundle.text("xl/workbook.xml")!!.contains("<calcPr fullCalcOnLoad=\"1\"/>"))
        assertFalse(r.bundle.names.contains("xl/calcChain.xml"))
        assertFalse(r.bundle.text("[Content_Types].xml")!!.contains("calcChain"))
        // Il resto del foglio resta com'era.
        assertTrue(r.bundle.text("xl/worksheets/sheet1.xml")!!.contains("<c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" s=\"1\"><v>750</v></c>"))
    }

    @Test fun xlsxNewCellAndText() {
        var r = Xlsx.setCell(xlsx(), 0, 2, 0, "nota")
        assertEquals("nota", Xlsx.read(r.bundle).sheets[0][2, 0]!!.display)
        r = Xlsx.setCell(r.bundle, 0, 0, 1, "Luce e gas")
        assertEquals("Luce e gas", Xlsx.read(r.bundle).sheets[0][0, 1]!!.display)
        r = Xlsx.setCell(r.bundle, 0, 4, 3, "=B4/2")
        assertEquals(414.55, Xlsx.read(r.bundle).sheets[0][4, 3]!!.number!!, 1e-9)
    }

    private fun ods(): Bundle {
        val content = """<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="${Odt.OFFICE}" xmlns:table="${Odt.TABLE}" xmlns:text="${Odt.TEXT}"><office:body><office:spreadsheet>
<table:table table:name="2026">
<table:table-row><table:table-cell office:value-type="string"><text:p>Affitto</text:p></table:table-cell><table:table-cell office:value-type="currency" office:currency="EUR" office:value="750"><text:p>750,00 €</text:p></table:table-cell></table:table-row>
<table:table-row><table:table-cell office:value-type="string"><text:p>Luce</text:p></table:table-cell><table:table-cell office:value-type="currency" office:currency="EUR" office:value="79.1"><text:p>79,10 €</text:p></table:table-cell></table:table-row>
<table:table-row table:number-rows-repeated="2"><table:table-cell table:number-columns-repeated="3"/></table:table-row>
<table:table-row><table:table-cell office:value-type="string"><text:p>Totale</text:p></table:table-cell><table:table-cell table:formula="of:=SUM([.B1:.B2])" office:value-type="currency" office:currency="EUR" office:value="829.1"><text:p>829,10 €</text:p></table:table-cell></table:table-row>
<table:table-row table:number-rows-repeated="1048570"><table:table-cell table:number-columns-repeated="1024"/></table:table-row>
</table:table></office:spreadsheet></office:body></office:document-content>"""
        return zip(linkedMapOf("mimetype" to "application/vnd.oasis.opendocument.spreadsheet", "content.xml" to content))
    }

    @Test fun odsReadsEditsRepeatsAndRecalculates() {
        val s = Ods.read(ods()).sheets[0]
        assertEquals("829,10 €", s[1, 4]!!.display)
        assertEquals("SUM(B1:B2)", s[1, 4]!!.formula)
        val r = Ods.setCell(ods(), 0, 1, 1, "84,20")
        val after = Ods.read(r.bundle).sheets[0]
        assertEquals("84,20 €", after[1, 1]!!.display)
        assertEquals("834,20 €", after[1, 4]!!.display)
        assertEquals(listOf("B5"), r.recalculated)
        // Una cella dentro una riga ripetuta.
        val r2 = Ods.setCell(r.bundle, 0, 2, 3, "nota")
        val s2 = Ods.read(r2.bundle).sheets[0]
        assertEquals("nota", s2[2, 3]!!.display)
        assertNull(s2[2, 2])
        assertEquals("834,20 €", s2[1, 4]!!.display)
    }
}
