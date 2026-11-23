package eu.eurise.office.rapida

import org.w3c.dom.Document
import org.w3c.dom.Element

/*
 * Rinomina, Sposta, Duplica, Elimina sui fogli della vista rapida (tavola OffSchedarioTelefono, pressione lunga sulla
 * linguetta). Ogni operazione tocca solo le parti del file che la riguardano; quando un foglio non si può trattare qui
 * senza rischi (grafici, tabelle, pivot, formule di altri fogli che lo usano) si rifiuta con [NotEditable] e la persona
 * passa alla modifica completa, come per le celle.
 */

/** Le regole dei nomi dei fogli: quelle di Excel, le più strette, così il file resta valido in entrambi i programmi. */
object SheetNames {
    const val MAX = 31
    private val FORBIDDEN = charArrayOf(':', '\\', '/', '?', '*', '[', ']')

    enum class Problem { Empty, TooLong, BadChar, Taken }

    /** Cosa non va nel nome [name] (già ripulito dagli spazi ai lati), con [others] i nomi degli altri fogli. */
    fun problem(name: String, others: List<String>): Problem? = when {
        name.isBlank() -> Problem.Empty
        name.length > MAX -> Problem.TooLong
        name.any { it in FORBIDDEN || it.code < 32 } || name.startsWith("'") || name.endsWith("'") -> Problem.BadChar
        others.any { it.equals(name, ignoreCase = true) } -> Problem.Taken
        else -> null
    }

    /** Il nome della copia, come fa Excel: «Spese (2)», «Spese (3)»… entro i 31 caratteri. */
    fun copyName(name: String, all: List<String>): String {
        var n = 2
        while (true) {
            val tail = " ($n)"
            val candidate = name.take(MAX - tail.length) + tail
            if (all.none { it.equals(candidate, ignoreCase = true) }) return candidate
            n++
        }
    }

    internal fun check(name: String, others: List<String>) {
        problem(name, others)?.let { throw IllegalArgumentException("nome del foglio non valido: $it") }
    }
}

/** L'indice che prende, dopo aver spostato [from] in [to], il foglio che era all'indice [i]. */
fun movedIndex(i: Int, from: Int, to: Int): Int = when {
    i == from -> to
    from < to && i in (from + 1)..to -> i - 1
    from > to && i in to until from -> i + 1
    else -> i
}

/**
 * I riferimenti ai fogli nelle formule di Excel: `Spese!A1`, `'Spese 2026'!A1`, `'Gen:Mar'!A1` (più fogli). Le stringhe
 * tra virgolette e i riferimenti ad altri file (`[1]Spese!A1`) restano come sono. [rename] riceve un nome di foglio e
 * restituisce quello nuovo, o null per lasciarlo.
 */
object ExcelRefs {
    fun rewrite(formula: String, rename: (String) -> String?): String {
        val out = StringBuilder()
        var i = 0
        while (i < formula.length) {
            val c = formula[i]
            when {
                c == '"' -> {
                    var j = i + 1
                    while (j < formula.length) { if (formula[j] == '"') { if (j + 1 < formula.length && formula[j + 1] == '"') j += 2 else break } else j++ }
                    out.append(formula, i, minOf(j + 1, formula.length)); i = j + 1
                }
                c == '\'' -> {
                    var j = i + 1
                    while (j < formula.length) { if (formula[j] == '\'') { if (j + 1 < formula.length && formula[j + 1] == '\'') j += 2 else break } else j++ }
                    val external = i > 0 && formula[i - 1] == ']'
                    if (j + 1 < formula.length && formula[j + 1] == '!' && !external) {
                        val name = formula.substring(i + 1, j).replace("''", "'")
                        out.append(prefix(name, rename)).append('!')
                        i = j + 2
                    } else { out.append(formula, i, minOf(j + 1, formula.length)); i = j + 1 }
                }
                c.isLetterOrDigit() || c == '_' -> {
                    var j = i
                    while (j < formula.length && (formula[j].isLetterOrDigit() || formula[j] == '_' || formula[j] == '.' || formula[j] == ':')) j++
                    val external = i > 0 && formula[i - 1] == ']'
                    val afterName = i > 0 && (formula[i - 1].isLetterOrDigit() || formula[i - 1] == '_')
                    if (j < formula.length && formula[j] == '!' && !external && !afterName) {
                        out.append(prefix(formula.substring(i, j), rename)).append('!')
                        i = j + 1
                    } else { out.append(formula, i, j); i = j }
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** I nomi dei fogli che la formula nomina. */
    fun names(formula: String): Set<String> {
        val found = HashSet<String>()
        rewrite(formula) { found += it; null }
        return found
    }

    private fun prefix(raw: String, rename: (String) -> String?): String {
        val parts = raw.split(':')
        val renamed = parts.map { rename(it) ?: it }
        val joined = renamed.joinToString(":")
        return if (renamed == parts && !raw.contains('\'') && !needsQuotes(joined)) raw else quote(joined)
    }

    private fun quote(name: String) = if (needsQuotes(name)) "'" + name.replace("'", "''") + "'" else name

    fun needsQuotes(name: String): Boolean {
        val parts = name.split(':')
        return parts.any { p ->
            !Regex("[\\p{L}_][\\p{L}\\p{N}_.]*").matches(p) ||
                Regex("(?i)[A-Z]{1,3}\\d+|R\\d*C?\\d*|C\\d*|TRUE|FALSE|VERO|FALSO").matches(p)
        }
    }
}

/**
 * I riferimenti ai fogli in OpenDocument: nelle formule stanno tra parentesi quadre (`[$Spese.A1:.B4]`,
 * `['Spese 2026'.A1]`), negli indirizzi senza (`Spese.A1:Spese.B4`, `$'Spese 2026'.$A$1`). I riferimenti ad altri file
 * (`'file:///…'#$Foglio1.A1`) restano come sono.
 */
object OdfRefs {
    /** Una formula (`of:=…`): solo i pezzi tra parentesi quadre, fuori dalle stringhe. */
    fun formula(text: String, rename: (String) -> String?): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '"' -> {
                    var j = i + 1
                    while (j < text.length) { if (text[j] == '"') { if (j + 1 < text.length && text[j + 1] == '"') j += 2 else break } else j++ }
                    out.append(text, i, minOf(j + 1, text.length)); i = j + 1
                }
                '[' -> {
                    var j = i + 1
                    var quoted = false
                    while (j < text.length && (quoted || text[j] != ']')) { if (text[j] == '\'') quoted = !quoted; j++ }
                    out.append('[').append(address(text.substring(i + 1, minOf(j, text.length)), rename))
                    if (j < text.length) out.append(']')
                    i = j + 1
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** Un indirizzo o un elenco di indirizzi, divisi da «:», spazi o «;» fuori dagli apici. */
    fun address(text: String, rename: (String) -> String?): String {
        val out = StringBuilder()
        val part = StringBuilder()
        var quoted = false
        fun flush() { out.append(part(part.toString(), rename)); part.clear() }
        for (c in text) {
            if (c == '\'') quoted = !quoted
            if (!quoted && (c == ':' || c == ' ' || c == ';')) { flush(); out.append(c) } else part.append(c)
        }
        flush()
        return out.toString()
    }

    private fun part(p: String, rename: (String) -> String?): String {
        val dollar = p.startsWith("$")
        val body = if (dollar) p.substring(1) else p
        val name: String
        val rest: String
        if (body.startsWith("'")) {
            var j = 1
            while (j < body.length) { if (body[j] == '\'') { if (j + 1 < body.length && body[j + 1] == '\'') j += 2 else break } else j++ }
            if (j >= body.length) return p
            rest = body.substring(j + 1)
            if (!rest.startsWith(".")) return p // file esterno (#) o altro
            name = body.substring(1, j).replace("''", "'")
        } else {
            val dot = body.indexOf('.')
            if (dot <= 0 || body.contains('#')) return p
            name = body.substring(0, dot)
            rest = body.substring(dot)
        }
        val renamed = rename(name) ?: return p
        return (if (dollar) "$" else "") + quote(renamed) + rest
    }

    fun quote(name: String) = if (Regex("[\\p{L}_][\\p{L}\\p{N}_]*").matches(name)) name else "'" + name.replace("'", "''") + "'"

    fun names(text: String, formula: Boolean): Set<String> {
        val found = HashSet<String>()
        if (formula) formula(text) { found += it; null } else address(text) { found += it; null }
        return found
    }
}

// ---- Excel ----

internal object XlsxSheets {
    private const val S = Xlsx.S
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PR = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val RELS = "xl/_rels/workbook.xml.rels"
    private const val TYPES = "[Content_Types].xml"

    private class Book(val doc: Document, val sheets: List<Element>, val rels: Document, val targets: Map<String, Element>) {
        fun name(i: Int): String = sheets[i].getAttribute("name")
        fun path(i: Int): String = targets[sheets[i].getAttributeNS(R, "id")]?.getAttribute("Target")?.let(::resolve) ?: error("foglio senza parte")
        fun definedNames(): List<Element> = Xml.all(doc.documentElement, S, "definedName")
        fun view(): Element? = Xml.child(doc.documentElement, S, "bookViews")?.let { Xml.child(it, S, "workbookView") }
    }

    private fun resolve(target: String) = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")

    private fun book(bundle: Bundle): Book {
        val doc = Xml.parse(bundle.text(WORKBOOK) ?: error("manca workbook.xml"))
        val rels = Xml.parse(bundle.text(RELS) ?: error("mancano le relazioni"))
        val sheets = Xml.child(doc.documentElement, S, "sheets")?.let { s -> Xml.children(s).filter { it.namespaceURI == S && it.localName == "sheet" } } ?: emptyList()
        val targets = Xml.all(rels.documentElement, PR, "Relationship").associateBy { it.getAttribute("Id") }
        return Book(doc, sheets, rels, targets)
    }

    /** Le parti XML dove possono stare formule e riferimenti (fogli, grafici, pivot, il libro). */
    private fun formulaParts(bundle: Bundle) = bundle.names.filter { it.startsWith("xl/") && it.endsWith(".xml") && it != "xl/sharedStrings.xml" && it != "xl/styles.xml" }

    private val FORMULA_TAGS = Regex("<((?:[\\w.-]+:)?)(f|formula|formula1|formula2|definedName)((?:\\s[^>]*)?)>(.*?)</\\1\\2>", RegexOption.DOT_MATCHES_ALL)
    private val LOCATION = Regex("(\\slocation=\")([^\"]*)(\")")
    private val PIVOT_SOURCE = Regex("(<(?:[\\w.-]+:)?worksheetSource\\b[^>]*?\\ssheet=\")([^\"]*)(\")")

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** Riscrive i riferimenti ai fogli in un XML del libro; il resto del testo resta identico. */
    private fun rewriteXml(xml: String, rename: (String) -> String?): String {
        var out = FORMULA_TAGS.replace(xml) { m ->
            val text = unescape(m.groupValues[4])
            val next = ExcelRefs.rewrite(text, rename)
            if (next == text) m.value else "<${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}>${Xml.escape(next)}</${m.groupValues[1]}${m.groupValues[2]}>"
        }
        out = LOCATION.replace(out) { m ->
            val text = unescape(m.groupValues[2])
            val next = ExcelRefs.rewrite(text, rename)
            if (next == text) m.value else m.groupValues[1] + Xml.escape(next).replace("\"", "&quot;") + m.groupValues[3]
        }
        out = PIVOT_SOURCE.replace(out) { m ->
            val next = rename(unescape(m.groupValues[2]))
            if (next == null) m.value else m.groupValues[1] + Xml.escape(next).replace("\"", "&quot;") + m.groupValues[3]
        }
        return out
    }

    /** Le parti (tranne [except]) che nominano il foglio [name]. */
    private fun usersOf(bundle: Bundle, name: String, except: Set<String>): List<String> = formulaParts(bundle).filter { it !in except }.filter { part ->
        val xml = bundle.text(part) ?: return@filter false
        var used = false
        rewriteXml(xml) { if (it.equals(name, ignoreCase = true)) used = true; null }
        used
    }

    fun rename(bundle: Bundle, index: Int, name: String): Bundle {
        val book = book(bundle)
        val old = book.name(index)
        if (old == name) return bundle
        SheetNames.check(name, book.sheets.indices.filter { it != index }.map(book::name))
        book.sheets[index].setAttribute("name", name)
        val rename: (String) -> String? = { if (it.equals(old, ignoreCase = true)) name else null }
        val changes = LinkedHashMap<String, ByteArray?>()
        for (part in formulaParts(bundle)) {
            if (part == WORKBOOK) continue
            val xml = bundle.text(part) ?: continue
            val next = rewriteXml(xml, rename)
            if (next != xml) changes[part] = next.toByteArray(Charsets.UTF_8)
        }
        book.definedNames().forEach { d -> d.textContent = ExcelRefs.rewrite(d.textContent, rename) }
        changes[WORKBOOK] = Xml.document(book.doc).toByteArray(Charsets.UTF_8)
        return bundle.with(changes)
    }

    fun move(bundle: Bundle, from: Int, to: Int): Bundle {
        val book = book(bundle)
        val n = book.sheets.size
        require(from in 0 until n && to in 0 until n)
        if (from == to) return bundle
        val parent = book.sheets[from].parentNode
        val moving = book.sheets[from]
        val others = book.sheets.filterIndexed { i, _ -> i != from }
        parent.removeChild(moving)
        if (to >= others.size) parent.appendChild(moving) else parent.insertBefore(moving, others[to])
        remapLocal(book) { movedIndex(it, from, to) }
        book.view()?.let { v -> listOf("activeTab", "firstSheet").forEach { a -> v.getAttribute(a).toIntOrNull()?.let { v.setAttribute(a, movedIndex(it, from, to).toString()) } } }
        val changes = linkedMapOf<String, ByteArray?>(WORKBOOK to Xml.document(book.doc).toByteArray(Charsets.UTF_8))
        dropCalcChain(bundle, changes)
        return bundle.with(changes)
    }

    fun duplicate(bundle: Bundle, index: Int, name: String): Bundle {
        val book = book(bundle)
        SheetNames.check(name, book.sheets.indices.map(book::name))
        val source = book.sheets[index]
        val path = book.path(index)
        val changes = LinkedHashMap<String, ByteArray?>()

        // Il foglio nuovo: sheetN.xml libero, con le sue relazioni se sono solo collegamenti esterni e impostazioni di stampa.
        var k = book.sheets.size + 1
        while (bundle["xl/worksheets/sheet$k.xml"] != null) k++
        val newPath = "xl/worksheets/sheet$k.xml"
        val relsPath = rels(path)
        bundle.text(relsPath)?.let { relsXml ->
            val doc = Xml.parse(relsXml)
            for (r in Xml.all(doc.documentElement, PR, "Relationship")) {
                when {
                    r.getAttribute("TargetMode") == "External" -> {}
                    r.getAttribute("Type").endsWith("/printerSettings") -> {
                        val target = resolveFrom(path, r.getAttribute("Target"))
                        val data = bundle[target] ?: continue
                        val ext = target.substringAfterLast('.', "bin")
                        var p = 1
                        while (bundle["xl/printerSettings/printerSettings$p.$ext"] != null || changes.containsKey("xl/printerSettings/printerSettings$p.$ext")) p++
                        val copy = "xl/printerSettings/printerSettings$p.$ext"
                        changes[copy] = data
                        r.setAttribute("Target", "../printerSettings/printerSettings$p.$ext")
                    }
                    else -> throw NotEditable("il foglio ha grafici, tabelle, commenti o disegni: si duplica nella modifica completa")
                }
            }
            changes[rels(newPath)] = Xml.document(doc).toByteArray(Charsets.UTF_8)
        }
        val sheetXml = bundle.text(path) ?: error("foglio vuoto")
        changes[newPath] = Regex("\\stabSelected=\"(1|true)\"").replace(sheetXml, "").toByteArray(Charsets.UTF_8)

        // Relazione, tipo di contenuto e voce nel libro, subito dopo l'originale.
        val sourceRel = book.targets[source.getAttributeNS(R, "id")]!!
        var rid = 1
        while (book.targets.containsKey("rId$rid")) rid++
        val rel = sourceRel.cloneNode(false) as Element
        rel.setAttribute("Id", "rId$rid")
        rel.setAttribute("Target", "worksheets/sheet$k.xml")
        book.rels.documentElement.appendChild(rel)
        changes[RELS] = Xml.document(book.rels).toByteArray(Charsets.UTF_8)
        bundle.text(TYPES)?.let { t ->
            val types = Xml.parse(t)
            val overrides = Xml.all(types.documentElement, CT, "Override")
            val mine = overrides.firstOrNull { it.getAttribute("PartName") == "/$path" }
            if (mine != null) {
                val copy = mine.cloneNode(false) as Element
                copy.setAttribute("PartName", "/$newPath")
                types.documentElement.appendChild(copy)
                changes[TYPES] = Xml.document(types).toByteArray(Charsets.UTF_8)
            }
        }
        val entry = source.cloneNode(false) as Element
        entry.setAttribute("name", name)
        entry.setAttribute("sheetId", ((book.sheets.maxOfOrNull { it.getAttribute("sheetId").toIntOrNull() ?: 0 } ?: 0) + 1).toString())
        entry.removeAttribute("state")
        val idAttr = (0 until source.attributes.length).map { source.attributes.item(it) }.first { it.namespaceURI == R && it.localName == "id" }
        entry.setAttributeNS(R, idAttr.nodeName, "rId$rid")
        source.parentNode.insertBefore(entry, source.nextSibling)

        // I nomi locali del foglio (area di stampa, titoli…) valgono anche per la copia.
        val old = book.name(index)
        val locals = book.definedNames().filter { it.getAttribute("localSheetId").toIntOrNull() == index }
        remapLocal(book) { if (it > index) it + 1 else it }
        for (d in locals) {
            val copy = d.cloneNode(true) as Element
            copy.setAttribute("localSheetId", (index + 1).toString())
            copy.textContent = ExcelRefs.rewrite(d.textContent) { if (it.equals(old, ignoreCase = true)) name else null }
            d.parentNode.insertBefore(copy, d.nextSibling)
        }
        book.view()?.let { v -> v.getAttribute("activeTab").toIntOrNull()?.let { if (it > index) v.setAttribute("activeTab", (it + 1).toString()) } }
        changes[WORKBOOK] = Xml.document(book.doc).toByteArray(Charsets.UTF_8)
        dropCalcChain(bundle, changes)
        return bundle.with(changes)
    }

    fun delete(bundle: Bundle, index: Int): Bundle {
        val book = book(bundle)
        val n = book.sheets.size
        if (n <= 1) throw NotEditable("un file ha almeno un foglio")
        if (book.sheets.filterIndexed { i, _ -> i != index }.all { it.getAttribute("state").let { s -> s == "hidden" || s == "veryHidden" } })
            throw NotEditable("gli altri fogli sono nascosti: si elimina nella modifica completa")
        val name = book.name(index)
        val path = book.path(index)
        // Se altri fogli, grafici o pivot lo usano, Excel li riempirebbe di #RIF!: meglio deciderlo nella modifica completa.
        val users = usersOf(bundle, name, setOf(path, WORKBOOK))
        val globalUse = book.definedNames().any { d ->
            d.getAttribute("localSheetId").toIntOrNull() != index && ExcelRefs.names(d.textContent).any { it.equals(name, ignoreCase = true) }
        }
        if (users.isNotEmpty() || globalUse) throw NotEditable("altri fogli o grafici usano questo foglio")

        val source = book.sheets[index]
        val rid = source.getAttributeNS(R, "id")
        source.parentNode.removeChild(source)
        book.definedNames().filter { it.getAttribute("localSheetId").toIntOrNull() == index }.forEach { it.parentNode.removeChild(it) }
        remapLocal(book) { if (it > index) it - 1 else it }
        Xml.child(book.doc.documentElement, S, "definedNames")?.let { if (Xml.children(it).isEmpty()) it.parentNode.removeChild(it) }
        book.view()?.let { v ->
            listOf("activeTab", "firstSheet").forEach { a ->
                v.getAttribute(a).toIntOrNull()?.let { t -> v.setAttribute(a, (if (t > index) t - 1 else t).coerceAtMost(n - 2).toString()) }
            }
        }
        book.targets[rid]?.let { it.parentNode.removeChild(it) }
        val changes = LinkedHashMap<String, ByteArray?>()
        changes[WORKBOOK] = Xml.document(book.doc).toByteArray(Charsets.UTF_8)
        changes[RELS] = Xml.document(book.rels).toByteArray(Charsets.UTF_8)
        changes[path] = null
        if (bundle[rels(path)] != null) changes[rels(path)] = null
        bundle.text(TYPES)?.let { t ->
            val types = Xml.parse(t)
            Xml.all(types.documentElement, CT, "Override").filter { it.getAttribute("PartName") == "/$path" }.forEach { it.parentNode.removeChild(it) }
            changes[TYPES] = Xml.document(types).toByteArray(Charsets.UTF_8)
        }
        // Se il foglio aperto era questo, Excel apre quello che ora sta al suo posto.
        val remaining = n - 1
        val active = book.view()?.getAttribute("activeTab")?.toIntOrNull() ?: 0
        val chosen = active.coerceIn(0, remaining - 1)
        val activeSheet = book.sheets.filterIndexed { i, _ -> i != index }[chosen]
        val activePath = book.targets[activeSheet.getAttributeNS(R, "id")]?.getAttribute("Target")?.let(::resolve)
        if (activePath != null && !(bundle.text(activePath) ?: "").contains(Regex("\\stabSelected=\"(1|true)\""))) {
            bundle.text(activePath)?.let { xml ->
                val sv = Regex("<((?:[\\w.-]+:)?)sheetView\\b").find(xml)
                if (sv != null) changes[activePath] = xml.replaceRange(sv.range, sv.value + " tabSelected=\"1\"").toByteArray(Charsets.UTF_8)
            }
        }
        dropCalcChain(bundle, changes)
        return bundle.with(changes)
    }

    /** Cambia `localSheetId` dei nomi locali secondo [map]. */
    private fun remapLocal(book: Book, map: (Int) -> Int) {
        for (d in book.definedNames()) d.getAttribute("localSheetId").toIntOrNull()?.let { d.setAttribute("localSheetId", map(it).toString()) }
    }

    private fun rels(part: String) = part.substringBeforeLast('/') + "/_rels/" + part.substringAfterLast('/') + ".rels"

    private fun resolveFrom(part: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = part.substringBeforeLast('/').split('/').toMutableList()
        for (seg in target.split('/')) when (seg) {
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            ".", "" -> {}
            else -> parts += seg
        }
        return parts.joinToString("/")
    }

    /**
     * Toglie la catena di calcolo, che indica i fogli per numero: dopo uno spostamento sarebbe sbagliata ed Excel
     * proporrebbe di riparare il file. Excel e LibreOffice la ricostruiscono da sé. Lavora sui cambiamenti già pronti.
     */
    private fun dropCalcChain(bundle: Bundle, changes: MutableMap<String, ByteArray?>) {
        if (bundle["xl/calcChain.xml"] == null) return
        changes["xl/calcChain.xml"] = null
        fun current(name: String) = if (changes.containsKey(name)) changes[name]?.toString(Charsets.UTF_8) else bundle.text(name)
        current(RELS)?.let { changes[RELS] = it.replace(Regex("<(?:\\w+:)?Relationship\\b[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
        current(TYPES)?.let { changes[TYPES] = it.replace(Regex("<(?:\\w+:)?Override\\b[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
    }
}

// ---- OpenDocument ----

internal object OdsSheets {
    private const val OFFICE = Odt.OFFICE
    private const val TABLE = Odt.TABLE
    private const val CONFIG = "urn:oasis:names:tc:opendocument:xmlns:config:1.0"
    private const val CONTENT = "content.xml"
    private const val SETTINGS = "settings.xml"
    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val CALCEXT = "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0"

    /** Gli attributi che contengono indirizzi di celle o formule (fogli, nomi, convalide, grafici, forme ancorate). */
    private val ADDRESS_ATTRS = setOf(
        "cell-range-address", "base-cell-address", "target-range-address", "end-cell-address", "cell-address",
        "range-address", "source-range-address", "target-cell-address", "values-cell-range-address", "label-cell-address",
    )
    private val FORMULA_ATTRS = setOf("formula", "condition", "expression")

    private fun tables(doc: Document): List<Element> {
        val body = Xml.child(doc.documentElement, OFFICE, "body")?.let { Xml.child(it, OFFICE, "spreadsheet") } ?: error("non è un foglio")
        return Xml.children(body).filter { it.namespaceURI == TABLE && it.localName == "table" }
    }

    private fun name(t: Element) = t.getAttributeNS(TABLE, "name")

    /** Riscrive (o solo legge, con [rename] che risponde null) i riferimenti ai fogli di tutti gli attributi sotto [root]. */
    private fun walk(root: Element, skip: Element? = null, rename: (String) -> String?): Boolean {
        var changed = false
        fun visit(e: Element) {
            if (e === skip) return
            val attrs = e.attributes
            for (i in 0 until attrs.length) {
                val a = attrs.item(i)
                val local = a.localName ?: continue
                val value = a.nodeValue
                val next = when (local) {
                    in ADDRESS_ATTRS -> OdfRefs.address(value, rename)
                    in FORMULA_ATTRS -> OdfRefs.formula(value, rename)
                    "value" -> if (a.namespaceURI == CALCEXT) OdfRefs.formula(value, rename) else value
                    else -> value
                }
                if (next != value) { a.nodeValue = next; changed = true }
            }
            for (c in Xml.children(e)) visit(c)
        }
        visit(root)
        return changed
    }

    private fun uses(root: Element, name: String, skip: Element? = null): Boolean {
        var used = false
        walk(root, skip) { if (it.equals(name, ignoreCase = true)) used = true; null }
        return used
    }

    /** I grafici e gli oggetti incorporati (`Object 1/content.xml`…). */
    private fun objects(bundle: Bundle) = bundle.names.filter { Regex("^Object [^/]+/content\\.xml$").matches(it) }

    private fun load(bundle: Bundle): Pair<Document, List<Element>> {
        val doc = Xml.parse(bundle.text(CONTENT) ?: error("manca content.xml"))
        return doc to tables(doc)
    }

    fun rename(bundle: Bundle, index: Int, name: String): Bundle {
        val (doc, tables) = load(bundle)
        val old = name(tables[index])
        if (old == name) return bundle
        SheetNames.check(name, tables.filterIndexed { i, _ -> i != index }.map(::name))
        val rename: (String) -> String? = { if (it.equals(old, ignoreCase = true)) name else null }
        tables[index].setAttributeNS(TABLE, "table:name", name)
        walk(doc.documentElement, rename = rename)
        val changes = linkedMapOf<String, ByteArray?>(CONTENT to Xml.document(doc).toByteArray(Charsets.UTF_8))
        for (part in objects(bundle)) {
            val obj = Xml.parse(bundle.text(part) ?: continue)
            if (walk(obj.documentElement, rename = rename)) changes[part] = Xml.document(obj).toByteArray(Charsets.UTF_8)
        }
        settings(bundle) { tablesMap, active ->
            tablesMap?.let { m -> Xml.children(m).filter { it.getAttributeNS(CONFIG, "name") == old }.forEach { it.setAttributeNS(CONFIG, "config:name", name) } }
            active?.let { if (it.textContent == old) it.textContent = name }
        }?.let { changes[SETTINGS] = it }
        return bundle.with(changes)
    }

    fun move(bundle: Bundle, from: Int, to: Int): Bundle {
        val (doc, tables) = load(bundle)
        require(from in tables.indices && to in tables.indices)
        if (from == to) return bundle
        val moving = tables[from]
        val others = tables.filterIndexed { i, _ -> i != from }
        val parent = moving.parentNode
        parent.removeChild(moving)
        if (to >= others.size) parent.insertBefore(moving, others.last().nextSibling) else parent.insertBefore(moving, others[to])
        return bundle.withText(CONTENT, Xml.document(doc))
    }

    fun duplicate(bundle: Bundle, index: Int, name: String): Bundle {
        val (doc, tables) = load(bundle)
        SheetNames.check(name, tables.map(::name))
        val source = tables[index]
        val embedded = listOf("object", "object-ole").any { Xml.all(source, DRAW, it).isNotEmpty() } || Xml.all(source, TABLE, "table-source").isNotEmpty()
        if (embedded) throw NotEditable("il foglio ha grafici o dati collegati: si duplica nella modifica completa")
        val copy = source.cloneNode(true) as Element
        copy.setAttributeNS(TABLE, "table:name", name)
        source.parentNode.insertBefore(copy, source.nextSibling)
        val changes = linkedMapOf<String, ByteArray?>(CONTENT to Xml.document(doc).toByteArray(Charsets.UTF_8))
        val old = name(source)
        settings(bundle) { tablesMap, _ ->
            // Zoom e posizione del foglio valgono anche per la copia.
            tablesMap?.let { m -> Xml.children(m).firstOrNull { it.getAttributeNS(CONFIG, "name") == old }?.let { e ->
                val c = e.cloneNode(true) as Element
                c.setAttributeNS(CONFIG, "config:name", name)
                m.insertBefore(c, e.nextSibling)
            } }
        }?.let { changes[SETTINGS] = it }
        return bundle.with(changes)
    }

    fun delete(bundle: Bundle, index: Int): Bundle {
        val (doc, tables) = load(bundle)
        if (tables.size <= 1) throw NotEditable("un file ha almeno un foglio")
        val table = tables[index]
        val old = name(table)
        val elsewhere = uses(doc.documentElement, old, skip = table) ||
            objects(bundle).any { p -> bundle.text(p)?.let { uses(Xml.parse(it).documentElement, old) } == true }
        if (elsewhere) throw NotEditable("altri fogli o grafici usano questo foglio")
        table.parentNode.removeChild(table)
        val changes = linkedMapOf<String, ByteArray?>(CONTENT to Xml.document(doc).toByteArray(Charsets.UTF_8))
        val next = tables.filterIndexed { i, _ -> i != index }[index.coerceAtMost(tables.size - 2)]
        settings(bundle) { tablesMap, active ->
            tablesMap?.let { m -> Xml.children(m).filter { it.getAttributeNS(CONFIG, "name") == old }.forEach { m.removeChild(it) } }
            active?.let { if (it.textContent == old) it.textContent = name(next) }
        }?.let { changes[SETTINGS] = it }
        return bundle.with(changes)
    }

    /**
     * Le impostazioni di vista di LibreOffice (settings.xml): la mappa «Tables» con zoom e cursore di ogni foglio e
     * «ActiveTable», il foglio aperto. [block] le cambia; il risultato è il nuovo settings.xml, o null se non c'è.
     */
    private fun settings(bundle: Bundle, block: (Element?, Element?) -> Unit): ByteArray? {
        val doc = Xml.parse(bundle.text(SETTINGS) ?: return null)
        val tablesMap = Xml.all(doc.documentElement, CONFIG, "config-item-map-named").firstOrNull { it.getAttributeNS(CONFIG, "name") == "Tables" }
        val active = Xml.all(doc.documentElement, CONFIG, "config-item").firstOrNull { it.getAttributeNS(CONFIG, "name") == "ActiveTable" }
        if (tablesMap == null && active == null) return null
        block(tablesMap, active)
        return Xml.document(doc).toByteArray(Charsets.UTF_8)
    }
}
