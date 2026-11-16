package eu.eurise.office.rapida

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

/**
 * OpenDocument testo (.odt) per la vista rapida: come [Docx], con i paragrafi di content.xml (`text:p`, `text:h`).
 * La correzione tocca solo i nodi di testo dove cade la modifica; il paragrafo torna nel file al suo posto.
 * Grassetto e corsivo si vedono ma non si cambiano qui (in ODF vivono negli stili automatici).
 */
object Odt : TextFormat {
    const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val DC = "http://purl.org/dc/elements/1.1/"
    private const val CONTENT = "content.xml"

    private val SAFE_TEXT = setOf("span", "s", "tab", "line-break", "soft-page-break", "bookmark", "bookmark-start", "bookmark-end")

    private class Look(val bold: Boolean, val italic: Boolean, val underline: Boolean, val align: Align?, val parent: String?)

    private fun isPara(e: Element) = e.namespaceURI == TEXT && (e.localName == "p" || e.localName == "h")

    /** Tutti i paragrafi nell'ordine del documento: lo stesso ordine dei tag nel testo XML. */
    private fun allParas(root: Element): List<Element> {
        val out = ArrayList<Element>()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                if (c is Element) { if (isPara(c)) out += c; walk(c) }
                c = c.nextSibling
            }
        }
        walk(root)
        return out
    }

    override fun read(bundle: Bundle): TextDoc {
        val doc = Xml.parse(bundle.text(CONTENT) ?: error("manca content.xml"))
        val looks = looks(doc) + looks(bundle.text("styles.xml")?.let { runCatching { Xml.parse(it) }.getOrNull() })
        val ids = java.util.IdentityHashMap<Element, Int>()
        allParas(doc.documentElement).forEachIndexed { i, p -> ids[p] = i }
        val body = Xml.child(doc.documentElement, OFFICE, "body")?.let { Xml.child(it, OFFICE, "text") } ?: error("non è un testo")
        val blocks = ArrayList<Block>()
        fun walk(parent: Element, bullet: Boolean) {
            for (e in Xml.children(parent)) when {
                isPara(e) -> blocks += para(e, ids.getValue(e), looks, bullet)
                e.namespaceURI == TEXT && e.localName == "list" ->
                    Xml.children(e).filter { it.localName == "list-item" || it.localName == "list-header" }.forEach { walk(it, true) }
                e.namespaceURI == TEXT && e.localName == "section" -> walk(e, bullet)
                e.namespaceURI == TABLE && e.localName == "table" -> blocks += table(e, ids, looks)
            }
        }
        walk(body, false)
        return TextDoc(blocks, formatting = false, commenting = true)
    }

    private fun table(t: Element, ids: Map<Element, Int>, looks: Map<String, Look>): Table {
        val rows = ArrayList<List<List<Para>>>()
        fun rows(parent: Element) {
            for (e in Xml.children(parent)) if (e.namespaceURI == TABLE) when (e.localName) {
                "table-row" -> rows += Xml.children(e).filter { it.namespaceURI == TABLE && it.localName == "table-cell" }.map { cell ->
                    allParas(cell).filter { p -> p.parentNode == cell || (p.parentNode as? Element)?.localName == "list-item" }
                        .map { para(it, ids.getValue(it), looks, false) }
                }
                "table-header-rows", "table-rows", "table-row-group" -> rows(e)
            }
        }
        rows(t)
        return Table(rows)
    }

    private fun para(p: Element, id: Int, looks: Map<String, Look>, bullet: Boolean): Para {
        val style = p.getAttributeNS(TEXT, "style-name")
        val base = resolve(style, looks)
        val spans = ArrayList<Span>()
        val notes = ArrayList<String>()
        fun walk(n: Node, look: Triple<Boolean, Boolean, Boolean>) {
            var c = n.firstChild
            while (c != null) {
                when {
                    c is Text -> if (c.data.isNotEmpty()) spans += Span(c.data.replace(Regex("[\\n\\r\\t ]+"), " "), look.first, look.second, look.third)
                    c is Element && c.namespaceURI == TEXT -> when (c.localName) {
                        "span", "a" -> walk(c, resolve(c.getAttributeNS(TEXT, "style-name"), looks, look))
                        "s" -> spans += Span(" ".repeat(c.getAttributeNS(TEXT, "c").toIntOrNull() ?: 1), look.first, look.second, look.third)
                        "tab" -> spans += Span("\t", look.first, look.second, look.third)
                        "line-break" -> spans += Span("\n", look.first, look.second, look.third)
                        "note", "bookmark", "bookmark-start", "bookmark-end", "soft-page-break" -> {}
                        else -> c.textContent.takeIf { it.isNotEmpty() }?.let { spans += Span(it, look.first, look.second, look.third) }
                    }
                    c is Element && c.namespaceURI == OFFICE && c.localName == "annotation" -> {
                        val who = Xml.child(c, DC, "creator")?.textContent.orEmpty()
                        val what = Xml.children(c).filter { isPara(it) }.joinToString("\n") { it.textContent }
                        notes += if (who.isNotBlank()) "$who: $what" else what
                    }
                }
                c = c.nextSibling
            }
        }
        walk(p, base)
        val level = if (p.localName == "h") (p.getAttributeNS(TEXT, "outline-level").toIntOrNull() ?: 1).coerceIn(1, 6) else 0
        return Para(id, merge(spans), level, align(style, looks), bullet, editable(p), notes)
    }

    private fun merge(spans: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic && last.underline == s.underline) out[out.lastIndex] = last.copy(text = last.text + s.text)
            else out += s
        }
        return out
    }

    private fun editable(p: Element): Boolean {
        fun ok(n: Node): Boolean {
            var c = n.firstChild
            while (c != null) {
                if (c is Element) {
                    val fine = (c.namespaceURI == TEXT && c.localName in SAFE_TEXT && ok(c)) ||
                        (c.namespaceURI == OFFICE && (c.localName == "annotation" || c.localName == "annotation-end"))
                    if (!fine) return false
                }
                c = c.nextSibling
            }
            return true
        }
        return ok(p)
    }

    private fun looks(doc: Document?): Map<String, Look> {
        doc ?: return emptyMap()
        return Xml.all(doc.documentElement, STYLE, "style").associate { s ->
            val tp = Xml.child(s, STYLE, "text-properties")
            val pp = Xml.child(s, STYLE, "paragraph-properties")
            val align = when (pp?.getAttributeNS(FO, "text-align")) {
                "center" -> Align.Center
                "end", "right" -> Align.End
                "justify" -> Align.Justify
                "start", "left" -> Align.Start
                else -> null
            }
            s.getAttributeNS(STYLE, "name") to Look(
                bold = tp?.getAttributeNS(FO, "font-weight") == "bold",
                italic = tp?.getAttributeNS(FO, "font-style") == "italic",
                underline = tp?.getAttributeNS(STYLE, "text-underline-style").let { it != null && it.isNotEmpty() && it != "none" },
                align = align,
                parent = s.getAttributeNS(STYLE, "parent-style-name").takeIf { it.isNotEmpty() },
            )
        }
    }

    private fun resolve(name: String, looks: Map<String, Look>, inherited: Triple<Boolean, Boolean, Boolean> = Triple(false, false, false)): Triple<Boolean, Boolean, Boolean> {
        var b = inherited.first; var i = inherited.second; var u = inherited.third
        var n: String? = name
        var guard = 0
        while (n != null && n.isNotEmpty() && guard++ < 8) {
            val l = looks[n] ?: break
            b = b || l.bold; i = i || l.italic; u = u || l.underline
            n = l.parent
        }
        return Triple(b, i, u)
    }

    private fun align(name: String, looks: Map<String, Look>): Align {
        var n: String? = name
        var guard = 0
        while (n != null && n.isNotEmpty() && guard++ < 8) {
            val l = looks[n] ?: break
            l.align?.let { return it }
            n = l.parent
        }
        return Align.Start
    }

    // ---- Correzione ----

    override fun edit(bundle: Bundle, id: Int, text: String, style: Style): Bundle {
        val xml = bundle.text(CONTENT) ?: error("manca content.xml")
        val doc = Xml.parse(xml)
        val p = allParas(doc.documentElement).getOrNull(id) ?: error("paragrafo $id non trovato")
        if (!editable(p)) throw NotEditable("paragrafo con immagini, campi o note")
        TextEdit.apply(pieces(doc, p), text)
        return bundle.withText(CONTENT, splice(xml, p, id))
    }

    override fun insertAfter(bundle: Bundle, id: Int, text: String): Bundle {
        val xml = bundle.text(CONTENT) ?: error("manca content.xml")
        val doc = Xml.parse(xml)
        val p = allParas(doc.documentElement).getOrNull(id) ?: error("paragrafo $id non trovato")
        // Dopo un titolo si torna al testo normale; altrimenti stesso stile del paragrafo.
        val fresh = doc.createElementNS(TEXT, "text:p")
        if (p.localName == "p") p.getAttributeNS(TEXT, "style-name").takeIf { it.isNotEmpty() }?.let { fresh.setAttributeNS(TEXT, "text:style-name", it) }
        if (text.isNotEmpty()) AnchorPiece(doc, fresh).insert(0, text)
        val prefix = p.prefix?.let { "$it:" } ?: ""
        val range = Xml.ranges(xml, setOf(prefix + "p", prefix + "h"))[id]
        return bundle.withText(CONTENT, xml.substring(0, range.last + 1) + Xml.fragment(fresh) + xml.substring(range.last + 1))
    }

    private fun splice(xml: String, p: Element, id: Int): String {
        val prefix = p.prefix?.let { "$it:" } ?: ""
        val range = Xml.ranges(xml, setOf(prefix + "p", prefix + "h"))[id]
        return xml.substring(0, range.first) + Xml.fragment(p) + xml.substring(range.last + 1)
    }

    private fun pieces(doc: Document, p: Element): List<Piece> {
        val list = ArrayList<Piece>()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                when {
                    c is Text -> list += TextPiece(doc, c)
                    c is Element && c.namespaceURI == TEXT -> when (c.localName) {
                        "span" -> walk(c)
                        "s" -> list += SpacePiece(doc, c)
                        "tab" -> list += MarkPiece(doc, c, "\t")
                        "line-break" -> list += MarkPiece(doc, c, "\n")
                    }
                }
                c = c.nextSibling
            }
        }
        walk(p)
        if (list.isEmpty()) list += AnchorPiece(doc, p)
        return list
    }

    /** I nodi ODF per [value]: testo, `text:tab`, `text:line-break`, e `text:s` per gli spazi ripetuti. */
    private fun nodes(doc: Document, value: String): List<Node> {
        val out = ArrayList<Node>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += doc.createTextNode(buf.toString()); buf.clear() } }
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            when {
                ch == '\n' -> { flush(); out += doc.createElementNS(TEXT, "text:line-break") }
                ch == '\t' -> { flush(); out += doc.createElementNS(TEXT, "text:tab") }
                ch == ' ' && i + 1 < value.length && value[i + 1] == ' ' -> {
                    var j = i
                    while (j < value.length && value[j] == ' ') j++
                    buf.append(' ')
                    flush()
                    out += doc.createElementNS(TEXT, "text:s").apply { setAttributeNS(TEXT, "text:c", (j - i - 1).toString()) }
                    i = j
                    continue
                }
                else -> buf.append(ch)
            }
            i++
        }
        flush()
        return out
    }

    private class TextPiece(val doc: Document, val node: Text) : Piece {
        override val text: String get() = node.data
        override fun delete(from: Int, to: Int) { node.deleteData(from, to - from) }
        override fun insert(at: Int, value: String): Boolean {
            val parent = node.parentNode ?: return false
            val made = nodes(doc, value)
            if (made.size == 1 && made[0] is Text) { node.insertData(at, (made[0] as Text).data); return true }
            val after = if (at < node.length) node.splitText(at) else node.nextSibling
            made.forEach { parent.insertBefore(it, after) }
            return true
        }
    }

    private class SpacePiece(val doc: Document, val e: Element) : Piece {
        private val count: Int get() = e.getAttributeNS(TEXT, "c").toIntOrNull() ?: 1
        override val text: String get() = " ".repeat(count)
        override fun delete(from: Int, to: Int) {
            val left = count - (to - from)
            if (left <= 0) e.parentNode?.removeChild(e) else e.setAttributeNS(TEXT, "text:c", left.toString())
        }
        override fun insert(at: Int, value: String): Boolean {
            val parent = e.parentNode ?: return false
            val made = nodes(doc, value)
            when {
                at <= 0 -> made.forEach { parent.insertBefore(it, e) }
                at >= count -> { val next = e.nextSibling; made.forEach { parent.insertBefore(it, next) } }
                else -> {
                    val rest = count - at
                    e.setAttributeNS(TEXT, "text:c", at.toString())
                    val next = e.nextSibling
                    made.forEach { parent.insertBefore(it, next) }
                    parent.insertBefore(doc.createElementNS(TEXT, "text:s").apply { setAttributeNS(TEXT, "text:c", rest.toString()) }, next)
                }
            }
            return true
        }
    }

    private class MarkPiece(val doc: Document, val e: Element, override val text: String) : Piece {
        override fun delete(from: Int, to: Int) { if (from == 0 && to >= 1) e.parentNode?.removeChild(e) }
        override fun insert(at: Int, value: String): Boolean {
            val parent = e.parentNode ?: return false
            val ref = if (at == 0) e else e.nextSibling
            nodes(doc, value).forEach { parent.insertBefore(it, ref) }
            return true
        }
    }

    private class AnchorPiece(val doc: Document, val p: Element) : Piece {
        override val text = ""
        override fun delete(from: Int, to: Int) {}
        override fun insert(at: Int, value: String): Boolean { nodes(doc, value).forEach { p.appendChild(it) }; return true }
    }

    // ---- Commenti ----

    override fun comment(bundle: Bundle, id: Int, text: String, author: String): Bundle {
        val xml = bundle.text(CONTENT) ?: error("manca content.xml")
        val doc = Xml.parse(xml)
        val p = allParas(doc.documentElement).getOrNull(id) ?: error("paragrafo $id non trovato")
        val note = doc.createElementNS(OFFICE, "office:annotation")
        note.appendChild(doc.createElementNS(DC, "dc:creator").apply { textContent = author })
        note.appendChild(doc.createElementNS(DC, "dc:date").apply { textContent = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString() })
        text.split('\n').forEach { line -> note.appendChild(doc.createElementNS(TEXT, "text:p").apply { textContent = line }) }
        p.insertBefore(note, p.firstChild)
        return bundle.withText(CONTENT, splice(xml, p, id))
    }
}
