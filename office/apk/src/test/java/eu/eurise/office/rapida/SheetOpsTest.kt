package eu.eurise.office.rapida

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rinomina, Sposta, Duplica, Elimina sui fogli della vista rapida (xlsx e ods). */
class SheetOpsTest {
    private fun zip(files: Map<String, String>): Bundle {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
        return Bundle.read(out.toByteArray())
    }

    /** Passa dal file vero (zip) e torna, così si prova anche che il risultato si salva e si riapre. */
    private fun again(b: Bundle) = Bundle.read(b.toBytes())

    private fun fails(block: () -> Unit): Throwable? = runCatching(block).exceptionOrNull()

    // ---- Nomi e riferimenti ----

    @Test fun names() {
        assertNull(SheetNames.problem("Spese 2026", listOf("Entrate")))
        assertEquals(SheetNames.Problem.Empty, SheetNames.problem(" ", emptyList()))
        assertEquals(SheetNames.Problem.TooLong, SheetNames.problem("x".repeat(32), emptyList()))
        assertEquals(SheetNames.Problem.BadChar, SheetNames.problem("Gen/Feb", emptyList()))
        assertEquals(SheetNames.Problem.BadChar, SheetNames.problem("'Spese", emptyList()))
        assertEquals(SheetNames.Problem.Taken, SheetNames.problem("spese", listOf("Spese")))
        assertEquals("Spese (2)", SheetNames.copyName("Spese", listOf("Spese")))
        assertEquals("Spese (3)", SheetNames.copyName("Spese", listOf("Spese", "Spese (2)")))
        assertEquals(31, SheetNames.copyName("x".repeat(31), listOf("x".repeat(31))).length)
        assertEquals(listOf(2, 0, 1), listOf(0, 1, 2).map { movedIndex(it, 0, 2) })
        assertEquals(listOf(1, 2, 0), listOf(0, 1, 2).map { movedIndex(it, 2, 0) })
    }

    @Test fun excelReferences() {
        val f = "Spese!A1+'Spese'!B2+\"Spese!A1\"+[1]Spese!A1+SUM(Gen:Mar!A1)+Altre!A1+SUM(A1:B2)"
        val out = ExcelRefs.rewrite(f) { when (it) { "Spese" -> "Spese 2026"; "Mar" -> "Marzo"; else -> null } }
        assertEquals("'Spese 2026'!A1+'Spese 2026'!B2+\"Spese!A1\"+[1]Spese!A1+SUM(Gen:Marzo!A1)+Altre!A1+SUM(A1:B2)", out)
        assertEquals("'It''s'!A1", ExcelRefs.rewrite("Spese!A1") { "It's" })
        assertEquals("'2026'!B4", ExcelRefs.rewrite("Anno!B4") { "2026" })
        assertEquals("Anno!B4", ExcelRefs.rewrite("'2026'!B4") { "Anno" })
        assertEquals(setOf("2026", "Gen", "Mar"), ExcelRefs.names("'2026'!B4*SUM(Gen:Mar!A1)"))
    }

    @Test fun odfReferences() {
        val f = "of:=SUM([\$Spese.A1:.A3])+['Spese'.B1]+[.C1]+\"[Spese.A1]\"+['file:///a.ods'#\$Spese.A1]"
        val out = OdfRefs.formula(f) { if (it == "Spese") "Spese 2026" else null }
        assertEquals("of:=SUM([\$'Spese 2026'.A1:.A3])+['Spese 2026'.B1]+[.C1]+\"[Spese.A1]\"+['file:///a.ods'#\$Spese.A1]", out)
        assertEquals("\$Nuovo.\$A\$1:.\$B\$2 'Altro foglio'.C3", OdfRefs.address("\$Spese.\$A\$1:.\$B\$2 'Altro foglio'.C3") { if (it == "Spese") "Nuovo" else null })
        assertEquals(setOf("2026"), OdfRefs.names("of:=[\$'2026'.B5]*2", formula = true))
    }

    // ---- Excel ----

    private val S = Xlsx.S
    private val ns = "xmlns=\"$S\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
    private val ws = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"

    private fun xlsx(): Bundle = zip(linkedMapOf(
        "[Content_Types].xml" to """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Default Extension="bin" ContentType="ps"/><Override PartName="/xl/workbook.xml" ContentType="wb"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="sheet"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="sheet"/><Override PartName="/xl/worksheets/sheet3.xml" ContentType="sheet"/><Override PartName="/xl/calcChain.xml" ContentType="cc"/></Types>""",
        "xl/workbook.xml" to """<workbook $ns><bookViews><workbookView activeTab="1"/></bookViews><sheets><sheet name="2026" sheetId="1" r:id="rId1"/><sheet name="Grafico" sheetId="2" r:id="rId2"/><sheet name="Note mie" sheetId="5" r:id="rId3"/></sheets><definedNames><definedName name="_xlnm.Print_Area" localSheetId="0">'2026'!${'$'}A${'$'}1:${'$'}D${'$'}4</definedName><definedName name="Totale">'2026'!${'$'}B${'$'}4</definedName><definedName name="Appunti" localSheetId="2">'Note mie'!${'$'}A${'$'}1</definedName></definedNames><calcPr calcId="191029"/></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$ws" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$ws" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="$ws" Target="worksheets/sheet3.xml"/><Relationship Id="rId9" Type="cc" Target="calcChain.xml"/></Relationships>""",
        "xl/calcChain.xml" to """<calcChain xmlns="$S"><c r="B4" i="1"/></calcChain>""",
        "xl/worksheets/sheet1.xml" to """<worksheet $ns><sheetViews><sheetView workbookViewId="0"/></sheetViews><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Affitto</t></is></c><c r="B1"><v>750</v></c></row><row r="4"><c r="B4"><f>SUM(B1:B2)</f><v>750</v></c></row></sheetData><pageSetup r:id="rId1"/></worksheet>""",
        "xl/worksheets/_rels/sheet1.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/printerSettings" Target="../printerSettings/printerSettings1.bin"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://eurise.eu" TargetMode="External"/></Relationships>""",
        "xl/printerSettings/printerSettings1.bin" to "stampante",
        "xl/worksheets/sheet2.xml" to """<worksheet $ns><sheetViews><sheetView tabSelected="1" workbookViewId="0"/></sheetViews><sheetData><row r="1"><c r="A1"><f>'2026'!B4*2</f><v>1500</v></c></row></sheetData><drawing r:id="rId1"/></worksheet>""",
        "xl/worksheets/_rels/sheet2.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""",
        "xl/charts/chart1.xml" to """<c:chartSpace xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:val><c:numRef><c:f>'2026'!${'$'}B${'$'}1:${'$'}B${'$'}2</c:f></c:numRef></c:val></c:chartSpace>""",
        "xl/worksheets/sheet3.xml" to """<worksheet $ns><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Ricordarsi</t></is></c></row></sheetData><hyperlinks><hyperlink ref="A1" location="'Note mie'!A1"/></hyperlinks></worksheet>""",
    ))

    private fun names(b: Bundle) = Xlsx.read(b).sheets.map { it.name }

    /** I nomi definiti del libro come «nome@foglio=testo» (foglio vuoto per i nomi di tutto il libro). */
    private fun defined(b: Bundle): Set<String> = Xml.all(Xml.parse(b.text("xl/workbook.xml")!!).documentElement, S, "definedName")
        .map { "${it.getAttribute("name")}@${it.getAttribute("localSheetId")}=${it.textContent}" }.toSet()

    @Test fun xlsxRename() {
        val b = again(Xlsx.renameSheet(xlsx(), 0, "Anno 2026"))
        assertEquals(listOf("Anno 2026", "Grafico", "Note mie"), names(b))
        assertTrue(b.text("xl/worksheets/sheet2.xml")!!.contains("<f>'Anno 2026'!B4*2</f>"))
        assertTrue(b.text("xl/charts/chart1.xml")!!.contains("<c:f>'Anno 2026'!\$B\$1:\$B\$2</c:f>"))
        assertTrue(defined(b).containsAll(listOf("Totale@='Anno 2026'!\$B\$4", "_xlnm.Print_Area@0='Anno 2026'!\$A\$1:\$D\$4")))
        assertEquals(750.0, Xlsx.read(b).sheets[0][1, 0]!!.number!!, 1e-9)
        // Il foglio che si rinomina non cambia nel resto: il resto del file resta uguale.
        assertEquals(xlsx().text("xl/worksheets/sheet1.xml"), b.text("xl/worksheets/sheet1.xml"))
        // Il collegamento interno segue il foglio rinominato.
        val c = again(Xlsx.renameSheet(xlsx(), 2, "Note"))
        assertTrue(c.text("xl/worksheets/sheet3.xml")!!.contains("location=\"Note!A1\""))
        // Nomi non validi o già presi.
        assertTrue(fails { Xlsx.renameSheet(xlsx(), 0, "grafico") } is IllegalArgumentException)
        assertTrue(fails { Xlsx.renameSheet(xlsx(), 0, "Gen:Feb") } is IllegalArgumentException)
    }

    @Test fun xlsxMove() {
        val b = again(Xlsx.moveSheet(xlsx(), 0, 2))
        assertEquals(listOf("Grafico", "Note mie", "2026"), names(b))
        val wb = b.text("xl/workbook.xml")!!
        assertTrue(defined(b).containsAll(listOf("_xlnm.Print_Area@2='2026'!\$A\$1:\$D\$4", "Appunti@1='Note mie'!\$A\$1")))  // seguono il foglio
        assertTrue(wb.contains("activeTab=\"0\""))              // Grafico era aperto e ora è il primo
        assertFalse(b.names.contains("xl/calcChain.xml"))
        assertFalse(b.text("xl/_rels/workbook.xml.rels")!!.contains("calcChain"))
        assertFalse(b.text("[Content_Types].xml")!!.contains("calcChain"))
        assertEquals(1500.0, Xlsx.read(b).sheets[0][0, 0]!!.number!!, 1e-9)
        // E indietro.
        assertEquals(listOf("2026", "Grafico", "Note mie"), names(Xlsx.moveSheet(b, 2, 0)))
    }

    @Test fun xlsxDuplicate() {
        val b = again(Xlsx.duplicateSheet(xlsx(), 0, "2026 (2)"))
        assertEquals(listOf("2026", "2026 (2)", "Grafico", "Note mie"), names(b))
        val copy = Xlsx.read(b).sheets[1]
        assertEquals("Affitto", copy[0, 0]!!.display)
        assertEquals("SUM(B1:B2)", copy[1, 3]!!.formula)
        val wb = b.text("xl/workbook.xml")!!
        assertTrue(wb.contains("sheetId=\"6\""))                // dopo il più alto (5)
        assertTrue(defined(b).containsAll(listOf(
            "_xlnm.Print_Area@0='2026'!\$A\$1:\$D\$4", "_xlnm.Print_Area@1='2026 (2)'!\$A\$1:\$D\$4", "Appunti@3='Note mie'!\$A\$1",
        )))
        assertTrue(wb.contains("activeTab=\"2\""))
        assertTrue(b.text("[Content_Types].xml")!!.contains("PartName=\"/xl/worksheets/sheet4.xml\""))
        assertTrue(b.text("xl/_rels/workbook.xml.rels")!!.contains("Target=\"worksheets/sheet4.xml\""))
        val rels = b.text("xl/worksheets/_rels/sheet4.xml.rels")!!
        assertTrue(rels.contains("../printerSettings/printerSettings2.bin") && rels.contains("https://eurise.eu"))
        assertEquals("stampante", b.text("xl/printerSettings/printerSettings2.bin"))
        assertTrue(fails { Xlsx.duplicateSheet(xlsx(), 1, "Grafico (2)") } is NotEditable)   // ha un disegno
        assertTrue(fails { Xlsx.duplicateSheet(xlsx(), 0, "Grafico") } is IllegalArgumentException)
        assertFalse(again(Xlsx.duplicateSheet(xlsx(), 2, "Note (2)")).text("xl/worksheets/sheet4.xml")!!.contains("tabSelected"))
    }

    @Test fun xlsxDelete() {
        // Grafico e il nome «Totale» usano 2026: si elimina nella modifica completa.
        assertTrue(fails { Xlsx.deleteSheet(xlsx(), 0) } is NotEditable)
        val b = again(Xlsx.deleteSheet(xlsx(), 2))
        assertEquals(listOf("2026", "Grafico"), names(b))
        assertFalse(b.names.contains("xl/worksheets/sheet3.xml"))
        assertFalse(b.text("[Content_Types].xml")!!.contains("sheet3.xml"))
        assertFalse(b.text("xl/_rels/workbook.xml.rels")!!.contains("sheet3.xml"))
        assertTrue(b.text("xl/workbook.xml")!!.contains("activeTab=\"1\""))   // Grafico resta aperto
        assertEquals(setOf("_xlnm.Print_Area@0='2026'!\$A\$1:\$D\$4", "Totale@='2026'!\$B\$4"), defined(b))  // «Appunti» se ne va con il foglio
        assertFalse(b.names.contains("xl/calcChain.xml"))
        // Eliminato il foglio aperto, si apre quello accanto e lo si segna come scelto.
        val c = again(Xlsx.deleteSheet(b, 1))
        assertEquals(listOf("2026"), names(c))
        assertTrue(c.text("xl/workbook.xml")!!.contains("activeTab=\"0\""))
        assertTrue(c.text("xl/worksheets/sheet1.xml")!!.contains("<sheetView tabSelected=\"1\" workbookViewId=\"0\"/>"))
        // L'ultimo foglio resta.
        assertTrue(fails { Xlsx.deleteSheet(c, 0) } is NotEditable)
    }

    // ---- OpenDocument ----

    private fun ods(): Bundle {
        val o = Odt.OFFICE; val t = Odt.TABLE; val x = Odt.TEXT
        val content = """<?xml version="1.0" encoding="UTF-8"?><office:document-content xmlns:office="$o" xmlns:table="$t" xmlns:text="$x" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"><office:body><office:spreadsheet>
<table:table table:name="2026"><table:table-row><table:table-cell office:value-type="float" office:value="750"><text:p>750</text:p></table:table-cell><table:table-cell table:formula="of:=[.A1]*2" office:value-type="float" office:value="1500"><text:p>1500</text:p></table:table-cell></table:table-row></table:table>
<table:table table:name="Riepilogo"><table:table-row><table:table-cell table:formula="of:=[${'$'}'2026'.B1]+1" office:value-type="float" office:value="1501"><text:p>1501</text:p></table:table-cell></table:table-row></table:table>
<table:table table:name="Note mie"><table:table-row><table:table-cell office:value-type="string"><text:p>Ricordarsi</text:p></table:table-cell></table:table-row></table:table>
<table:named-expressions><table:named-range table:name="Totale" table:base-cell-address="${'$'}'2026'.${'$'}A${'$'}1" table:cell-range-address="${'$'}'2026'.${'$'}B${'$'}1"/></table:named-expressions>
</office:spreadsheet></office:body></office:document-content>"""
        val cfg = "urn:oasis:names:tc:opendocument:xmlns:config:1.0"
        val settings = """<?xml version="1.0" encoding="UTF-8"?><office:document-settings xmlns:office="$o" xmlns:config="$cfg"><office:settings><config:config-item-set config:name="ooo:view-settings"><config:config-item-map-indexed config:name="Views"><config:config-item-map-entry><config:config-item config:name="ActiveTable" config:type="string">Note mie</config:config-item><config:config-item-map-named config:name="Tables"><config:config-item-map-entry config:name="2026"><config:config-item config:name="ZoomValue" config:type="int">120</config:config-item></config:config-item-map-entry><config:config-item-map-entry config:name="Note mie"/></config:config-item-map-named></config:config-item-map-entry></config:config-item-map-indexed></config:config-item-set></office:settings></office:document-settings>"""
        return zip(linkedMapOf("mimetype" to "application/vnd.oasis.opendocument.spreadsheet", "content.xml" to content, "settings.xml" to settings))
    }

    private fun odsNames(b: Bundle) = Ods.read(b).sheets.map { it.name }

    @Test fun odsRename() {
        val b = again(Ods.renameSheet(ods(), 0, "Anno 2026"))
        assertEquals(listOf("Anno 2026", "Riepilogo", "Note mie"), odsNames(b))
        val c = b.text("content.xml")!!
        assertTrue(c.contains("of:=[\$'Anno 2026'.B1]+1"))
        assertTrue(c.contains("table:cell-range-address=\"\$'Anno 2026'.\$B\$1\""))
        assertTrue(c.contains("of:=[.A1]*2"))
        assertTrue(b.text("settings.xml")!!.contains("config:name=\"Anno 2026\""))
        val n = again(Ods.renameSheet(ods(), 2, "Note"))
        assertTrue(n.text("settings.xml")!!.contains(">Note</config:config-item>"))
        assertTrue(fails { Ods.renameSheet(ods(), 0, "riepilogo") } is IllegalArgumentException)
    }

    @Test fun odsMoveDuplicateDelete() {
        val m = again(Ods.moveSheet(ods(), 2, 0))
        assertEquals(listOf("Note mie", "2026", "Riepilogo"), odsNames(m))
        assertEquals(listOf("2026", "Riepilogo", "Note mie"), odsNames(Ods.moveSheet(m, 0, 2)))
        // I nomi del file restano dopo l'ultimo foglio.
        assertTrue(again(Ods.moveSheet(ods(), 0, 2)).text("content.xml")!!.indexOf("named-expressions") > again(Ods.moveSheet(ods(), 0, 2)).text("content.xml")!!.indexOf("table:name=\"2026\""))

        val d = again(Ods.duplicateSheet(ods(), 0, "2026 (2)"))
        assertEquals(listOf("2026", "2026 (2)", "Riepilogo", "Note mie"), odsNames(d))
        assertEquals(1500.0, Ods.read(d).sheets[1][1, 0]!!.number!!, 1e-9)
        assertTrue(d.text("settings.xml")!!.contains("config:name=\"2026 (2)\""))

        assertTrue(fails { Ods.deleteSheet(ods(), 0) } is NotEditable)   // Riepilogo e «Totale» lo usano
        val e = again(Ods.deleteSheet(ods(), 2))
        assertEquals(listOf("2026", "Riepilogo"), odsNames(e))
        val s = e.text("settings.xml")!!
        assertFalse(s.contains("Note mie"))
        assertTrue(s.contains(">Riepilogo</config:config-item>"))
        val one = Ods.deleteSheet(e, 1)
        assertEquals(listOf("2026"), odsNames(one))
        assertTrue(fails { Ods.deleteSheet(one, 0) } is NotEditable)   // l'ultimo foglio resta
    }
}
