package eu.eurise.office.rapida

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.w3c.dom.Element

/**
 * Excel (.xlsx) per la vista rapida. Legge fogli, testi condivisi, formati dei numeri e date; una cella cambiata viene
 * riscritta da sola dentro il foglio, così come i totali ricalcolati, senza toccare il resto. Il file chiede a Excel e
 * LibreOffice di ricalcolare all'apertura e perde `calcChain.xml`, che entrambi ricostruiscono da sé.
 */
object Xlsx : SheetFormat {
    const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PR = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val WORKBOOK = "xl/workbook.xml"

    private class Part(val name: String, val path: String)
    private class Styles(val looks: List<NumberLook>, val dates: List<Boolean>)

    private fun parts(bundle: Bundle): List<Part> {
        val wb = Xml.parse(bundle.text(WORKBOOK) ?: error("manca workbook.xml"))
        val rels = Xml.parse(bundle.text("xl/_rels/workbook.xml.rels") ?: error("mancano le relazioni"))
        val targets = Xml.all(rels.documentElement, PR, "Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
        return Xml.all(wb.documentElement, S, "sheet").mapNotNull { s ->
            val target = targets[s.getAttributeNS(R, "id")] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            Part(s.getAttribute("name"), path)
        }
    }

    private fun shared(bundle: Bundle): List<String> {
        val text = bundle.text("xl/sharedStrings.xml") ?: return emptyList()
        val doc = Xml.parse(text)
        return Xml.all(doc.documentElement, S, "si").map { si ->
            val direct = Xml.child(si, S, "t")
            direct?.textContent ?: Xml.children(si).filter { it.localName == "r" }.joinToString("") { r -> Xml.child(r, S, "t")?.textContent.orEmpty() }
        }
    }

    private fun styles(bundle: Bundle): Styles {
        val text = bundle.text("xl/styles.xml") ?: return Styles(emptyList(), emptyList())
        val doc = Xml.parse(text)
        val codes = HashMap<Int, String>()
        Xml.all(doc.documentElement, S, "numFmt").forEach { codes[it.getAttribute("numFmtId").toIntOrNull() ?: -1] = it.getAttribute("formatCode") }
        val builtin = mapOf(1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%")
        val xfs = Xml.child(doc.documentElement, S, "cellXfs")?.let { Xml.children(it).filter { e -> e.localName == "xf" } } ?: emptyList()
        val ids = xfs.map { it.getAttribute("numFmtId").toIntOrNull() ?: 0 }
        return Styles(
            looks = ids.map { NumberLook.fromCode(codes[it] ?: builtin[it]) },
            dates = ids.map { id -> id in 14..22 || id in 45..47 || codes[id]?.let { isDateCode(it) } == true },
        )
    }

    private fun isDateCode(code: String): Boolean {
        val plain = code.replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]|\\\\."), "").lowercase()
        return plain.contains('d') || plain.contains('y') || (plain.contains('m') && plain.contains('h'))
    }

    override fun read(bundle: Bundle): SheetDoc {
        val strings = shared(bundle)
        val styles = styles(bundle)
        return SheetDoc(parts(bundle).map { part -> sheet(bundle, part, strings, styles) })
    }

    private fun sheet(bundle: Bundle, part: Part, strings: List<String>, styles: Styles): Sheet {
        val doc = Xml.parse(bundle.text(part.path) ?: return Sheet(part.name, emptyMap(), 0, 0))
        val cells = HashMap<Long, Cell>()
        var maxRow = -1; var maxCol = -1
        each(doc.documentElement) { c, col, row ->
            val cell = cell(c, col, row, strings, styles)
            if (cell.display.isNotEmpty() || cell.hasFormula) {
                cells[Sheet.key(col, row)] = cell
                maxRow = maxOf(maxRow, row); maxCol = maxOf(maxCol, col)
            }
        }
        fillMissing(cells, styles, doc.documentElement)
        return Sheet(part.name, cells, maxRow + 1, maxCol + 1)
    }

    /**
     * Formule senza valore salvato (file fatti da programmi che non calcolano): la vista rapida le calcola, se sa, solo
     * per mostrarle; il file non cambia.
     */
    private fun fillMissing(cells: MutableMap<Long, Cell>, styles: Styles, root: Element) {
        val missing = cells.values.filter { it.formula != null && it.number == null && it.display.isEmpty() }
        if (missing.isEmpty()) return
        val styleOf = HashMap<Pair<Int, Int>, Int>()
        each(root) { c, col, row -> styleOf[col to row] = c.getAttribute("s").toIntOrNull() ?: 0 }
        var left = missing
        repeat(8) {
            val still = ArrayList<Cell>()
            for (cell in left) {
                val v = runCatching { Formula.parse(cell.formula!!).eval { c, r -> cells[Sheet.key(c, r)]?.number } }.getOrNull()
                if (v == null) { still += cell; continue }
                cells[Sheet.key(cell.col, cell.row)] = cell.copy(number = v, display = show(v, styleOf[cell.col to cell.row] ?: 0, styles))
            }
            if (still.size == left.size) return
            left = still
        }
    }

    /** Ogni cella del foglio con la sua colonna e riga (anche quando il file non scrive i riferimenti). */
    private fun each(root: Element, block: (Element, Int, Int) -> Unit) {
        val data = Xml.child(root, S, "sheetData") ?: return
        var rowIndex = -1
        for (row in Xml.children(data).filter { it.localName == "row" }) {
            rowIndex = row.getAttribute("r").toIntOrNull()?.minus(1) ?: (rowIndex + 1)
            var colIndex = -1
            for (c in Xml.children(row).filter { it.localName == "c" }) {
                val ref = Cells.parse(c.getAttribute("r"))
                colIndex = ref?.first ?: (colIndex + 1)
                block(c, colIndex, ref?.second ?: rowIndex)
            }
        }
    }

    private fun cell(c: Element, col: Int, row: Int, strings: List<String>, styles: Styles): Cell {
        val type = c.getAttribute("t")
        val v = Xml.child(c, S, "v")?.textContent
        val f = Xml.child(c, S, "f")
        val formula = f?.textContent?.takeIf { it.isNotBlank() }
        val style = c.getAttribute("s").toIntOrNull() ?: 0
        val number = if (type.isEmpty() || type == "n") v?.toDoubleOrNull() else null
        val display = when (type) {
            "s" -> v?.toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty()
            "inlineStr" -> Xml.child(c, S, "is")?.let { Xml.all(it, S, "t").joinToString("") { t -> t.textContent } }.orEmpty()
            "str", "e" -> v.orEmpty()
            "b" -> if (v == "1") "VERO" else "FALSO"
            else -> number?.let { show(it, style, styles) } ?: v.orEmpty()
        }
        return Cell(col, row, display, number, formula, hasFormula = f != null)
    }

    private fun show(n: Double, style: Int, styles: Styles): String {
        if (styles.dates.getOrNull(style) == true && n > 0 && n < 2_958_466) {
            val date = LocalDate.of(1899, 12, 30).plusDays(n.toLong())
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
        return (styles.looks.getOrNull(style) ?: NumberLook.GENERAL).format(n)
    }

    // ---- Modifica ----

    override fun setCell(bundle: Bundle, sheet: Int, col: Int, row: Int, input: String): CellResult {
        val all = parts(bundle)
        val part = all.getOrNull(sheet) ?: error("foglio $sheet non trovato")
        val xml = bundle.text(part.path) ?: error("foglio vuoto")
        val doc = Xml.parse(xml)
        val strings = shared(bundle)
        val styles = styles(bundle)
        val prefix = doc.documentElement.prefix?.let { "$it:" } ?: ""

        // Dove sta ogni cella, nel DOM e nel testo.
        val located = ArrayList<Triple<Element, Int, Int>>()
        each(doc.documentElement) { c, cc, rr -> located += Triple(c, cc, rr) }
        val cRanges = Xml.ranges(xml, setOf(prefix + "c"))
        val at = HashMap<Pair<Int, Int>, Int>()
        located.forEachIndexed { i, (_, cc, rr) -> at[cc to rr] = i }
        val values = HashMap<Pair<Int, Int>, Double>()
        val formulas = HashMap<Pair<Int, Int>, String?>()
        located.forEach { (c, cc, rr) ->
            val cell = cell(c, cc, rr, strings, styles)
            cell.number?.let { values[cc to rr] = it }
            if (cell.hasFormula && (cc to rr) != (col to row)) formulas[cc to rr] = cell.formula
        }

        val edits = ArrayList<Pair<IntRange, String>>() // intervallo sostituito (vuoto = inserimento in first) → testo
        val ref = Cells.ref(col, row)
        val existing = at[col to row]
        val keep = existing?.let { located[it].first }
        val s = keep?.getAttribute("s")?.takeIf { it.isNotEmpty() }
        val parsed = Cells.input(input)
        var written: Double? = null
        val newCell = when (parsed) {
            is Cells.Input.Empty -> if (s != null) "<${prefix}c r=\"$ref\" s=\"$s\"/>" else ""
            is Cells.Input.Number -> { written = parsed.value; cellXml(prefix, ref, s, null, null, Cells.plain(parsed.value)) }
            is Cells.Input.Text -> "<${prefix}c r=\"$ref\"${s?.let { " s=\"$it\"" } ?: ""} t=\"inlineStr\"><${prefix}is><${prefix}t xml:space=\"preserve\">${Xml.escape(parsed.text)}</${prefix}t></${prefix}is></${prefix}c>"
            is Cells.Input.Formula -> {
                written = runCatching { Formula.parse(parsed.text).eval { c, r -> values[c to r] } }.getOrNull()
                cellXml(prefix, ref, s, "<${prefix}f>${Xml.escape(parsed.text)}</${prefix}f>", null, written?.let { Cells.plain(it) })
            }
        }
        if (written != null) values[col to row] = written else values.remove(col to row)
        if (existing != null) edits += cRanges[existing] to newCell
        else if (newCell.isNotEmpty()) edits += insertion(xml, doc.documentElement, prefix, col, row, located, cRanges) to newCell

        val pending = ArrayList<String>()
        if (parsed is Cells.Input.Formula && written == null) pending += ref
        val outcome = Recalc.run(col to row, formulas) { c, r -> values[c to r] }
        for ((where, v) in outcome.values) {
            val i = at[where] ?: continue
            val c = located[i].first
            val original = xml.substring(cRanges[i].first, cRanges[i].last + 1)
            val fXml = Regex("<${Regex.escape(prefix)}f(\\s[^>]*)?(/>|>.*?</${Regex.escape(prefix)}f>)", RegexOption.DOT_MATCHES_ALL).find(original)?.value
            edits += cRanges[i] to cellXml(prefix, Cells.ref(where.first, where.second), c.getAttribute("s").takeIf { it.isNotEmpty() }, fXml, null, Cells.plain(v))
        }
        outcome.pending.forEach { pending += Cells.ref(it.first, it.second) }

        var out = xml
        for ((range, text) in edits.sortedByDescending { it.first.first }) {
            out = if (range.isEmpty()) out.substring(0, range.first) + text + out.substring(range.first)
            else out.substring(0, range.first) + text + out.substring(range.last + 1)
        }
        val changes = linkedMapOf<String, ByteArray?>(part.path to out.toByteArray(Charsets.UTF_8))
        changes += recalcOnLoad(bundle)
        // Le formule degli altri fogli che nominano questo li segnaliamo: si aggiornano all'apertura completa.
        all.forEachIndexed { i, p ->
            if (i != sheet) bundle.text(p.path)?.let { other ->
                if (other.contains(part.name + "!") || other.contains("'" + part.name + "'!")) pending += p.name
            }
        }
        return CellResult(bundle.with(changes), outcome.values.keys.map { Cells.ref(it.first, it.second) }, pending.distinct())
    }

    private fun cellXml(prefix: String, ref: String, s: String?, f: String?, t: String?, v: String?): String = buildString {
        append("<${prefix}c r=\"$ref\"")
        s?.let { append(" s=\"$it\"") }
        t?.let { append(" t=\"$it\"") }
        if (f == null && v == null) { append("/>"); return@buildString }
        append('>')
        f?.let { append(it) }
        v?.let { append("<${prefix}v>$it</${prefix}v>") }
        append("</${prefix}c>")
    }

    /** Dove inserire una cella che non c'è: nella sua riga, in ordine di colonna; se manca la riga, la crea. */
    private fun insertion(xml: String, root: Element, prefix: String, col: Int, row: Int, located: List<Triple<Element, Int, Int>>, cRanges: List<IntRange>): IntRange {
        val sameRow = located.withIndex().filter { it.value.third == row }
        sameRow.firstOrNull { it.value.second > col }?.let { return IntRange(cRanges[it.index].first, cRanges[it.index].first - 1) }
        val rowRanges = Xml.ranges(xml, setOf(prefix + "row"))
        val rows = Xml.child(root, S, "sheetData")?.let { d -> Xml.children(d).filter { it.localName == "row" } } ?: emptyList()
        var index = -1
        val rowNumbers = rows.map { r -> index = r.getAttribute("r").toIntOrNull()?.minus(1) ?: (index + 1); index }
        val mine = rowNumbers.indexOf(row)
        if (mine >= 0) {
            val r = rowRanges[mine]
            if (xml.substring(r.first, r.last + 1).endsWith("/>")) throw NotEditable("riga vuota: si riempie nella modifica completa")
            val closeAt = r.last - "</${prefix}row>".length + 1
            return IntRange(closeAt, closeAt - 1)
        }
        throw NotEditable("cella in una riga che il file non ha ancora: si aggiunge nella modifica completa")
    }

    /** Chiede il ricalcolo all'apertura e toglie la catena di calcolo, che Excel ricostruisce. */
    private fun recalcOnLoad(bundle: Bundle): Map<String, ByteArray?> {
        val out = LinkedHashMap<String, ByteArray?>()
        bundle.text(WORKBOOK)?.let { wb ->
            val calc = Regex("<((?:\\w+:)?)calcPr\\b([^>]*?)(/?)>").find(wb)
            val updated = if (calc != null) {
                val attrs = calc.groupValues[2].replace(Regex("\\sfullCalcOnLoad=\"[^\"]*\""), "")
                wb.replaceRange(calc.range, "<${calc.groupValues[1]}calcPr$attrs fullCalcOnLoad=\"1\"${calc.groupValues[3]}>")
            } else {
                val p = Regex("<(\\w+:)?workbook\\b").find(wb)?.groupValues?.get(1).orEmpty()
                val before = Regex("<$p(oleSize|customWorkbookViews|pivotCaches|smartTagPr|smartTagTypes|webPublishing|fileRecoveryPr|webPublishObjects|extLst)\\b").find(wb)?.range?.first
                    ?: wb.lastIndexOf("</${p}workbook>")
                wb.substring(0, before) + "<${p}calcPr fullCalcOnLoad=\"1\"/>" + wb.substring(before)
            }
            out[WORKBOOK] = updated.toByteArray(Charsets.UTF_8)
        }
        if (bundle["xl/calcChain.xml"] != null) {
            out["xl/calcChain.xml"] = null
            bundle.text("xl/_rels/workbook.xml.rels")?.let { out["xl/_rels/workbook.xml.rels"] = it.replace(Regex("<Relationship\\b[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
            bundle.text("[Content_Types].xml")?.let { out["[Content_Types].xml"] = it.replace(Regex("<Override\\b[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
        }
        return out
    }
}
