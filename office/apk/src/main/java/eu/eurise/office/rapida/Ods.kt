package eu.eurise.office.rapida

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * OpenDocument foglio (.ods) per la vista rapida. Le righe e le celle ripetute (`number-rows-repeated`,
 * `number-columns-repeated`) si leggono come le vede LibreOffice; per cambiare una cella dentro una ripetizione la si
 * divide in tre (prima, lei, dopo). Si riscrive solo content.xml; il testo mostrato dei totali ricalcolati segue il
 * formato che avevano.
 */
object Ods : SheetFormat {
    private const val OFFICE = Odt.OFFICE
    private const val TABLE = Odt.TABLE
    private const val TEXT = Odt.TEXT
    private const val CALCEXT = "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0"
    private const val CONTENT = "content.xml"
    private const val MAX_ROWS = 20_000
    private const val MAX_COLS = 1_024

    private fun tables(doc: Document): List<Element> {
        val body = Xml.child(doc.documentElement, OFFICE, "body")?.let { Xml.child(it, OFFICE, "spreadsheet") } ?: error("non è un foglio")
        return Xml.children(body).filter { it.namespaceURI == TABLE && it.localName == "table" }
    }

    /** Le righe del foglio, anche dentro gruppi e intestazioni, nell'ordine. */
    private fun rows(table: Element): List<Element> {
        val out = ArrayList<Element>()
        fun walk(e: Element) {
            for (c in Xml.children(e)) if (c.namespaceURI == TABLE) when (c.localName) {
                "table-row" -> out += c
                "table-header-rows", "table-rows", "table-row-group" -> walk(c)
            }
        }
        walk(table)
        return out
    }

    private fun cellsOf(row: Element) = Xml.children(row).filter { it.namespaceURI == TABLE && (it.localName == "table-cell" || it.localName == "covered-table-cell") }
    private fun rowRepeat(e: Element) = e.getAttributeNS(TABLE, "number-rows-repeated").toIntOrNull() ?: 1
    private fun colRepeat(e: Element) = e.getAttributeNS(TABLE, "number-columns-repeated").toIntOrNull() ?: 1

    override fun read(bundle: Bundle): SheetDoc {
        val doc = Xml.parse(bundle.text(CONTENT) ?: error("manca content.xml"))
        return SheetDoc(tables(doc).map { sheet(it) })
    }

    private fun sheet(table: Element): Sheet {
        val cells = HashMap<Long, Cell>()
        var r = 0
        var maxRow = -1; var maxCol = -1
        for (row in rows(table)) {
            val repeat = rowRepeat(row)
            val rowCells = ArrayList<Cell>()
            var c = 0
            for (cell in cellsOf(row)) {
                val n = colRepeat(cell)
                val read = cell(cell, c, r)
                if (read.display.isNotEmpty() || read.hasFormula) for (k in 0 until minOf(n, MAX_COLS - c)) rowCells += read.copy(col = c + k)
                c += n
                if (c >= MAX_COLS) break
            }
            if (rowCells.isNotEmpty()) for (k in 0 until minOf(repeat, MAX_ROWS - r)) for (x in rowCells) {
                cells[Sheet.key(x.col, r + k)] = x.copy(row = r + k)
                maxRow = maxOf(maxRow, r + k); maxCol = maxOf(maxCol, x.col)
            }
            r += repeat
            if (r >= MAX_ROWS) break
        }
        return Sheet(table.getAttributeNS(TABLE, "name"), cells, maxRow + 1, maxCol + 1)
    }

    private fun cell(e: Element, col: Int, row: Int): Cell {
        val type = e.getAttributeNS(OFFICE, "value-type")
        val shown = Xml.children(e).filter { it.namespaceURI == TEXT && it.localName == "p" }.joinToString("\n") { it.textContent }
        val number = when (type) {
            "float", "percentage", "currency" -> e.getAttributeNS(OFFICE, "value").toDoubleOrNull()
            else -> null
        }
        val display = shown.ifEmpty {
            when (type) {
                "date" -> e.getAttributeNS(OFFICE, "date-value").take(10).let { d -> runCatching { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(d) }
                "boolean" -> if (e.getAttributeNS(OFFICE, "boolean-value") == "true") "VERO" else "FALSO"
                else -> number?.let { NumberLook.GENERAL.format(it) }.orEmpty()
            }
        }
        val raw = e.getAttributeNS(TABLE, "formula")
        return Cell(col, row, display, number, raw.takeIf { it.isNotEmpty() }?.let { Formula.fromOdf(it) }, hasFormula = raw.isNotEmpty())
    }

    // ---- Modifica ----

    override fun setCell(bundle: Bundle, sheet: Int, col: Int, row: Int, input: String): CellResult {
        val doc = Xml.parse(bundle.text(CONTENT) ?: error("manca content.xml"))
        val table = tables(doc).getOrNull(sheet) ?: error("foglio $sheet non trovato")

        // Valori e formule del foglio, come li vede la lettura.
        val read = sheet(table)
        val values = HashMap<Pair<Int, Int>, Double>()
        val formulas = HashMap<Pair<Int, Int>, String?>()
        read.cells.values.forEach { c ->
            c.number?.let { values[c.col to c.row] = it }
            if (c.hasFormula && (c.col to c.row) != (col to row)) formulas[c.col to c.row] = c.formula
        }

        val target = single(doc, table, col, row)
        val oldDisplay = read[col, row]?.display.orEmpty()
        val parsed = Cells.input(input)
        val pending = ArrayList<String>()
        when (parsed) {
            is Cells.Input.Empty -> { clear(target); values.remove(col to row) }
            is Cells.Input.Number -> {
                val look = if (oldDisplay.isNotEmpty() && read[col, row]?.number != null) NumberLook.from(oldDisplay) else if (parsed.percent) NumberLook(null, false, percent = true) else NumberLook.GENERAL
                val type = target.getAttributeNS(OFFICE, "value-type").takeIf { it in setOf("float", "percentage", "currency") } ?: if (parsed.percent) "percentage" else "float"
                val currency = target.getAttributeNS(OFFICE, "currency")
                clear(target)
                setNumber(doc, target, type, parsed.value, look.format(parsed.value), currency)
                values[col to row] = parsed.value
            }
            is Cells.Input.Text -> {
                clear(target)
                target.setAttributeNS(OFFICE, "office:value-type", "string")
                target.setAttributeNS(CALCEXT, "calcext:value-type", "string")
                parsed.text.split('\n').forEach { line -> target.appendChild(doc.createElementNS(TEXT, "text:p").apply { textContent = line }) }
                values.remove(col to row)
            }
            is Cells.Input.Formula -> {
                val v = runCatching { Formula.parse(parsed.text).eval { c, r -> values[c to r] } }.getOrNull()
                clear(target)
                target.setAttributeNS(TABLE, "table:formula", Formula.toOdf(parsed.text))
                if (v != null) { setNumber(doc, target, "float", v, NumberLook.from(oldDisplay.ifEmpty { "0" }).format(v), ""); values[col to row] = v }
                else { pending += Cells.ref(col, row); values.remove(col to row) }
            }
        }

        val outcome = Recalc.run(col to row, formulas) { c, r -> values[c to r] }
        for ((where, v) in outcome.values) {
            val e = single(doc, table, where.first, where.second)
            val look = NumberLook.from(read[where.first, where.second]?.display.orEmpty().ifEmpty { "0" })
            e.setAttributeNS(OFFICE, "office:value", Cells.plain(v))
            if (e.getAttributeNS(OFFICE, "value-type").isEmpty()) e.setAttributeNS(OFFICE, "office:value-type", "float")
            Xml.children(e).filter { it.namespaceURI == TEXT && it.localName == "p" }.forEach { e.removeChild(it) }
            e.appendChild(doc.createElementNS(TEXT, "text:p").apply { textContent = look.format(v) })
        }
        outcome.pending.forEach { pending += Cells.ref(it.first, it.second) }
        val name = table.getAttributeNS(TABLE, "name")
        tables(doc).forEachIndexed { i, t ->
            if (i != sheet && Xml.all(t, TABLE, "table-cell").any { it.getAttributeNS(TABLE, "formula").contains("$name.") }) pending += t.getAttributeNS(TABLE, "name")
        }
        return CellResult(bundle.withText(CONTENT, Xml.document(doc)), outcome.values.keys.map { Cells.ref(it.first, it.second) }, pending.distinct())
    }

    private fun setNumber(doc: Document, e: Element, type: String, value: Double, shown: String, currency: String) {
        e.setAttributeNS(OFFICE, "office:value-type", type)
        e.setAttributeNS(CALCEXT, "calcext:value-type", type)
        e.setAttributeNS(OFFICE, "office:value", Cells.plain(value))
        if (type == "currency" && currency.isNotEmpty()) e.setAttributeNS(OFFICE, "office:currency", currency)
        e.appendChild(doc.createElementNS(TEXT, "text:p").apply { textContent = shown })
    }

    private fun clear(e: Element) {
        for (a in listOf("value-type", "value", "date-value", "time-value", "boolean-value", "string-value", "currency")) e.removeAttributeNS(OFFICE, a)
        e.removeAttributeNS(CALCEXT, "value-type")
        e.removeAttributeNS(TABLE, "formula")
        Xml.children(e).filter { it.namespaceURI == TEXT && it.localName == "p" }.forEach { e.removeChild(it) }
    }

    /**
     * La cella ([col], [row]) come elemento suo, non condiviso con altre: divide le ripetizioni di righe e celle e, se
     * la riga o la cella non esistono ancora, le aggiunge.
     */
    private fun single(doc: Document, table: Element, col: Int, row: Int): Element {
        var r = 0
        var rowEl: Element? = null
        for (e in rows(table)) {
            val n = rowRepeat(e)
            if (row < r + n) { rowEl = split(e, row - r, n, "number-rows-repeated"); break }
            r += n
        }
        if (rowEl == null) {
            val last = rows(table).lastOrNull()
            val parent = last?.parentNode ?: table
            if (row > r) parent.appendChild(doc.createElementNS(TABLE, "table:table-row").apply {
                if (row - r > 1) setAttributeNS(TABLE, "table:number-rows-repeated", (row - r).toString())
                appendChild(doc.createElementNS(TABLE, "table:table-cell"))
            })
            rowEl = doc.createElementNS(TABLE, "table:table-row")
            parent.appendChild(rowEl)
        }
        var c = 0
        for (e in cellsOf(rowEl!!)) {
            val n = colRepeat(e)
            if (col < c + n) {
                if (e.localName == "covered-table-cell") throw NotEditable("cella unita a un'altra")
                return split(e, col - c, n, "number-columns-repeated")
            }
            c += n
        }
        if (col > c) rowEl.appendChild(doc.createElementNS(TABLE, "table:table-cell").apply {
            if (col - c > 1) setAttributeNS(TABLE, "table:number-columns-repeated", (col - c).toString())
        })
        return doc.createElementNS(TABLE, "table:table-cell").also { rowEl.appendChild(it) }
    }

    /** Divide un elemento ripetuto [n] volte in prima / quello alla posizione [k] / dopo; restituisce quello. */
    private fun split(e: Element, k: Int, n: Int, attr: String): Element {
        if (n == 1) return e
        val parent = e.parentNode
        fun copy(times: Int): Element = (e.cloneNode(true) as Element).apply {
            if (times > 1) setAttributeNS(TABLE, "table:$attr", times.toString()) else removeAttributeNS(TABLE, attr)
        }
        if (k > 0) parent.insertBefore(copy(k), e)
        val mine = copy(1)
        parent.insertBefore(mine, e)
        if (n - k - 1 > 0) parent.insertBefore(copy(n - k - 1), e)
        parent.removeChild(e)
        return mine
    }
}
