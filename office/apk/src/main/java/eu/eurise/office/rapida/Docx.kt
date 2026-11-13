package eu.eurise.office.rapida

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.IdentityHashMap
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Word (.docx) per la vista rapida. Legge paragrafi, titoli, elenchi, tabelle e commenti; corregge un paragrafo
 * cambiando solo i caratteri toccati dentro le sue «run», così la formattazione intorno resta com'era; il paragrafo
 * corretto viene rimesso nel file al suo posto, senza riscrivere il resto di document.xml.
 */
object Docx : TextFormat {
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val DOC = "word/document.xml"
    private const val COMMENTS = "word/comments.xml"

    /** Figli di un paragrafo che la correzione sa lasciare al loro posto. */
    private val SAFE_IN_P = setOf("pPr", "r", "bookmarkStart", "bookmarkEnd", "commentRangeStart", "commentRangeEnd", "proofErr", "permStart", "permEnd")
    /** Figli di una run che la correzione sa trattare. */
    private val SAFE_IN_R = setOf("rPr", "t", "tab", "br", "cr", "lastRenderedPageBreak", "softHyphen", "noBreakHyphen", "commentReference")

    /** Ordine degli elementi di w:rPr nello schema: Word li vuole così. */
    private val RPR_ORDER = listOf(
        "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss",
        "imprint", "noProof", "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs",
        "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout",
        "specVanish", "oMath",
    )

    private class StyleInfo(val name: String, val outline: Int?, val basedOn: String?)

    override fun read(bundle: Bundle): TextDoc {
        val doc = Xml.parse(bundle.text(DOC) ?: error("manca document.xml"))
        val styles = styles(bundle)
        val comments = commentTexts(bundle)
        val ids = IdentityHashMap<Element, Int>()
        Xml.all(doc.documentElement, W, "p").forEachIndexed { i, p -> ids[p] = i }
        val body = Xml.child(doc.documentElement, W, "body") ?: error("documento vuoto")
        val blocks = ArrayList<Block>()
        fun walk(parent: Element) {
            for (e in Xml.children(parent)) {
                if (e.namespaceURI != W) continue
                when (e.localName) {
                    "p" -> blocks += para(e, ids.getValue(e), styles, comments)
                    "tbl" -> blocks += table(e, ids, styles, comments)
                    "sdt" -> Xml.child(e, W, "sdtContent")?.let { walk(it) }
                }
            }
        }
        walk(body)
        return TextDoc(blocks, formatting = true, commenting = true)
    }

    private fun table(tbl: Element, ids: Map<Element, Int>, styles: Map<String, StyleInfo>, comments: Map<String, String>): Table {
        val rows = Xml.children(tbl).filter { it.namespaceURI == W && it.localName == "tr" }.map { tr ->
            Xml.children(tr).filter { it.namespaceURI == W && it.localName == "tc" }.map { tc ->
                Xml.children(tc).filter { it.namespaceURI == W && it.localName == "p" }.map { para(it, ids.getValue(it), styles, comments) }
            }
        }
        return Table(rows)
    }

    private fun para(p: Element, id: Int, styles: Map<String, StyleInfo>, comments: Map<String, String>): Para {
        val pPr = Xml.child(p, W, "pPr")
        val styleId = pPr?.let { Xml.child(it, W, "pStyle") }?.getAttributeNS(W, "val").orEmpty()
        val level = headingLevel(styleId, styles)
        val bullet = pPr?.let { Xml.child(it, W, "numPr") } != null || styles[styleId]?.name?.contains("list", ignoreCase = true) == true
        val align = when (pPr?.let { Xml.child(it, W, "jc") }?.getAttributeNS(W, "val")) {
            "center" -> Align.Center
            "right", "end" -> Align.End
            "both", "distribute" -> Align.Justify
            else -> Align.Start
        }
        val spans = ArrayList<Span>()
        val notes = ArrayList<String>()
        fun runs(parent: Element) {
            for (e in Xml.children(parent)) {
                if (e.namespaceURI != W) continue
                when (e.localName) {
                    "r" -> {
                        val rPr = Xml.child(e, W, "rPr")
                        val text = StringBuilder()
                        for (c in Xml.children(e)) if (c.namespaceURI == W) when (c.localName) {
                            "t" -> text.append(c.textContent)
                            "tab" -> text.append('\t')
                            "br", "cr" -> text.append('\n')
                            "noBreakHyphen" -> text.append('-')
                            "commentReference" -> comments[c.getAttributeNS(W, "id")]?.let { notes += it }
                        }
                        if (text.isNotEmpty()) spans += Span(text.toString(), on(rPr, "b"), on(rPr, "i"), underline(rPr))
                    }
                    "hyperlink", "ins", "smartTag", "customXml", "fldSimple" -> runs(e)
                }
            }
        }
        runs(p)
        return Para(id, merge(spans), level, align, bullet, editable(p), notes)
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

    private fun on(rPr: Element?, name: String): Boolean {
        val e = rPr?.let { Xml.child(it, W, name) } ?: return false
        return e.getAttributeNS(W, "val").let { it.isEmpty() || it == "true" || it == "1" || it == "on" }
    }

    private fun underline(rPr: Element?): Boolean {
        val e = rPr?.let { Xml.child(it, W, "u") } ?: return false
        return e.getAttributeNS(W, "val").let { it.isNotEmpty() && it != "none" }
    }

    private fun editable(p: Element): Boolean = Xml.children(p).all { c ->
        c.namespaceURI == W && c.localName in SAFE_IN_P &&
            (c.localName != "r" || Xml.children(c).all { it.namespaceURI == W && it.localName in SAFE_IN_R })
    }

    private fun headingLevel(styleId: String, styles: Map<String, StyleInfo>): Int {
        var id: String? = styleId
        var guard = 0
        while (id != null && id.isNotEmpty() && guard++ < 8) {
            val s = styles[id]
            val name = (s?.name ?: id).lowercase()
            if (name == "title" || name == "titolo") return 1
            Regex("^(heading|titolo|überschrift|titre|título)\\s*(\\d)$").find(name)?.let { return it.groupValues[2].toInt().coerceIn(1, 6) }
            s?.outline?.let { return (it + 1).coerceIn(1, 6) }
            id = s?.basedOn
        }
        return 0
    }

    private fun styles(bundle: Bundle): Map<String, StyleInfo> {
        val text = bundle.text("word/styles.xml") ?: return emptyMap()
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return emptyMap()
        return Xml.all(doc.documentElement, W, "style").associate { s ->
            val id = s.getAttributeNS(W, "styleId")
            val name = Xml.child(s, W, "name")?.getAttributeNS(W, "val").orEmpty()
            val outline = Xml.child(s, W, "pPr")?.let { Xml.child(it, W, "outlineLvl") }?.getAttributeNS(W, "val")?.toIntOrNull()
            val based = Xml.child(s, W, "basedOn")?.getAttributeNS(W, "val")
            id to StyleInfo(name, outline?.takeIf { it < 9 }, based)
        }
    }

    private fun commentTexts(bundle: Bundle): Map<String, String> {
        val text = bundle.text(COMMENTS) ?: return emptyMap()
        val doc = runCatching { Xml.parse(text) }.getOrNull() ?: return emptyMap()
        return Xml.all(doc.documentElement, W, "comment").associate { c ->
            val author = c.getAttributeNS(W, "author")
            val body = Xml.all(c, W, "t").joinToString("") { it.textContent }
            c.getAttributeNS(W, "id") to (if (author.isNotBlank()) "$author: $body" else body)
        }
    }

    // ---- Correzione ----

    override fun edit(bundle: Bundle, id: Int, text: String, style: Style): Bundle {
        val xml = bundle.text(DOC) ?: error("manca document.xml")
        val doc = Xml.parse(xml)
        val all = Xml.all(doc.documentElement, W, "p")
        val p = all.getOrNull(id) ?: error("paragrafo $id non trovato")
        if (!editable(p)) throw NotEditable("paragrafo con immagini, campi o revisioni")
        TextEdit.apply(pieces(doc, p), text)
        if (!style.none) for (r in Xml.children(p).filter { it.namespaceURI == W && it.localName == "r" }) {
            val rPr = rPr(doc, r)
            style.bold?.let { set(doc, rPr, "b", it); set(doc, rPr, "bCs", it) }
            style.italic?.let { set(doc, rPr, "i", it); set(doc, rPr, "iCs", it) }
            style.underline?.let { setUnderline(doc, rPr, it) }
            if (!rPr.hasChildNodes()) r.removeChild(rPr)
        }
        return bundle.withText(DOC, splice(xml, p, id))
    }

    override fun insertAfter(bundle: Bundle, id: Int, text: String): Bundle {
        val xml = bundle.text(DOC) ?: error("manca document.xml")
        val doc = Xml.parse(xml)
        val p = Xml.all(doc.documentElement, W, "p").getOrNull(id) ?: error("paragrafo $id non trovato")
        val fresh = doc.createElementNS(W, "w:p")
        Xml.child(p, W, "pPr")?.let { fresh.appendChild(it.cloneNode(true)) }
        if (text.isNotEmpty()) AnchorPiece(doc, fresh).insert(0, text)
        val range = Xml.ranges(xml, setOf((p.prefix?.let { "$it:" } ?: "") + "p"))[id]
        return bundle.withText(DOC, xml.substring(0, range.last + 1) + Xml.fragment(fresh) + xml.substring(range.last + 1))
    }

    /** Rimette nel testo XML il paragrafo [id] com'è ora nel DOM, al posto del vecchio. */
    private fun splice(xml: String, p: Element, id: Int): String {
        val range = Xml.ranges(xml, setOf((p.prefix?.let { "$it:" } ?: "") + "p"))[id]
        return xml.substring(0, range.first) + Xml.fragment(p) + xml.substring(range.last + 1)
    }

    private fun pieces(doc: Document, p: Element): List<Piece> {
        val list = ArrayList<Piece>()
        for (r in Xml.children(p)) {
            if (r.namespaceURI != W || r.localName != "r") continue
            for (c in Xml.children(r)) if (c.namespaceURI == W) when (c.localName) {
                "t" -> list += TPiece(doc, c)
                "tab", "br", "cr" -> list += MarkPiece(doc, c, if (c.localName == "tab") "\t" else "\n")
            }
        }
        if (list.isEmpty()) list += AnchorPiece(doc, p)
        return list
    }

    /** I nodi per [value]: testo, tabulazioni e a capo, da mettere dentro una run. */
    private fun nodes(doc: Document, value: String): List<Element> {
        val out = ArrayList<Element>()
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += t(doc, buf.toString()); buf.clear() } }
        for (ch in value) when (ch) {
            '\n' -> { flush(); out += doc.createElementNS(W, "w:br") }
            '\t' -> { flush(); out += doc.createElementNS(W, "w:tab") }
            else -> buf.append(ch)
        }
        flush()
        return out
    }

    private fun t(doc: Document, text: String): Element = doc.createElementNS(W, "w:t").apply {
        setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
        textContent = text
    }

    private class TPiece(val doc: Document, val e: Element) : Piece {
        override val text: String get() = e.textContent
        override fun delete(from: Int, to: Int) {
            e.textContent = text.removeRange(from, to)
            e.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
        }
        override fun insert(at: Int, value: String): Boolean {
            val s = text
            val made = nodes(doc, s.substring(0, at) + value + "\u0000" + s.substring(at))
            // Il segnaposto \u0000 separa il testo dopo: torna nell'ultimo w:t.
            val parent = e.parentNode
            for (n in made) {
                if (n.localName == "t") n.textContent = n.textContent.replace("\u0000", "")
                parent.insertBefore(n, e)
            }
            parent.removeChild(e)
            return true
        }
    }

    private class MarkPiece(val doc: Document, val e: Element, override val text: String) : Piece {
        override fun delete(from: Int, to: Int) { if (from == 0 && to >= 1) e.parentNode.removeChild(e) }
        override fun insert(at: Int, value: String): Boolean {
            val parent = e.parentNode ?: return false
            val ref = if (at == 0) e else e.nextSibling
            for (n in nodes(doc, value)) parent.insertBefore(n, ref)
            return true
        }
    }

    /** Un paragrafo vuoto: la prima run nasce con la formattazione del segno di paragrafo. */
    private class AnchorPiece(val doc: Document, val p: Element) : Piece {
        override val text = ""
        override fun delete(from: Int, to: Int) {}
        override fun insert(at: Int, value: String): Boolean {
            val r = doc.createElementNS(W, "w:r")
            Xml.child(p, W, "pPr")?.let { Xml.child(it, W, "rPr") }?.let { mark ->
                val rPr = doc.createElementNS(W, "w:rPr")
                Xml.children(mark).filter { it.localName in RPR_ORDER }.forEach { rPr.appendChild(it.cloneNode(true)) }
                if (rPr.hasChildNodes()) r.appendChild(rPr)
            }
            nodes(doc, value).forEach { r.appendChild(it) }
            p.appendChild(r)
            return true
        }
    }

    private fun rPr(doc: Document, r: Element): Element =
        Xml.child(r, W, "rPr") ?: doc.createElementNS(W, "w:rPr").also { r.insertBefore(it, r.firstChild) }

    private fun set(doc: Document, rPr: Element, name: String, on: Boolean) {
        Xml.child(rPr, W, name)?.let { rPr.removeChild(it) }
        if (on) insertOrdered(rPr, doc.createElementNS(W, "w:$name"))
    }

    private fun setUnderline(doc: Document, rPr: Element, on: Boolean) {
        Xml.child(rPr, W, "u")?.let { rPr.removeChild(it) }
        if (on) insertOrdered(rPr, doc.createElementNS(W, "w:u").apply { setAttributeNS(W, "w:val", "single") })
    }

    private fun insertOrdered(rPr: Element, e: Element) {
        val rank = RPR_ORDER.indexOf(e.localName)
        val before = Xml.children(rPr).firstOrNull { c -> RPR_ORDER.indexOf(c.localName).let { it < 0 || it > rank } }
        rPr.insertBefore(e, before)
    }

    // ---- Commenti ----

    override fun comment(bundle: Bundle, id: Int, text: String, author: String): Bundle {
        val xml = bundle.text(DOC) ?: error("manca document.xml")
        val doc = Xml.parse(xml)
        val p = Xml.all(doc.documentElement, W, "p").getOrNull(id) ?: error("paragrafo $id non trovato")

        val existing = bundle.text(COMMENTS)
        val cdoc = existing?.let { Xml.parse(it) } ?: Xml.parse("<w:comments xmlns:w=\"$W\"/>")
        val used = Xml.all(cdoc.documentElement, W, "comment").mapNotNull { it.getAttributeNS(W, "id").toIntOrNull() } +
            (Xml.all(doc.documentElement, W, "commentRangeStart") + Xml.all(doc.documentElement, W, "commentReference"))
                .mapNotNull { it.getAttributeNS(W, "id").toIntOrNull() }
        val cid = ((used.maxOrNull() ?: -1) + 1).toString()

        val c = cdoc.createElementNS(W, "w:comment").apply {
            setAttributeNS(W, "w:id", cid)
            setAttributeNS(W, "w:author", author)
            setAttributeNS(W, "w:initials", author.split(' ').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.uppercase().ifEmpty { "E" })
            setAttributeNS(W, "w:date", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
        }
        text.split('\n').forEach { line ->
            val cp = cdoc.createElementNS(W, "w:p")
            val cr = cdoc.createElementNS(W, "w:r")
            cr.appendChild(t(cdoc, line))
            cp.appendChild(cr)
            c.appendChild(cp)
        }
        cdoc.documentElement.appendChild(c)

        val start = doc.createElementNS(W, "w:commentRangeStart").apply { setAttributeNS(W, "w:id", cid) }
        val end = doc.createElementNS(W, "w:commentRangeEnd").apply { setAttributeNS(W, "w:id", cid) }
        val ref = doc.createElementNS(W, "w:r").apply {
            appendChild(doc.createElementNS(W, "w:commentReference").apply { setAttributeNS(W, "w:id", cid) })
        }
        val pPr = Xml.child(p, W, "pPr")
        p.insertBefore(start, pPr?.nextSibling ?: p.firstChild)
        p.appendChild(end)
        p.appendChild(ref)

        val changes = LinkedHashMap<String, ByteArray?>()
        changes[DOC] = splice(xml, p, Xml.all(doc.documentElement, W, "p").indexOf(p)).toByteArray(Charsets.UTF_8)
        changes[COMMENTS] = Xml.document(cdoc).toByteArray(Charsets.UTF_8)
        if (existing == null) {
            changes += relsWithComments(bundle)
            changes += typesWithComments(bundle)
        }
        return bundle.with(changes)
    }

    private fun relsWithComments(bundle: Bundle): Map<String, ByteArray> {
        val name = "word/_rels/document.xml.rels"
        val doc = Xml.parse(bundle.text(name) ?: "<Relationships xmlns=\"$REL\"/>")
        val rels = Xml.all(doc.documentElement, REL, "Relationship")
        if (rels.none { it.getAttribute("Type").endsWith("/comments") }) {
            val ids = rels.map { it.getAttribute("Id") }.toSet()
            var n = rels.size + 1
            while ("rId$n" in ids) n++
            doc.documentElement.appendChild(doc.createElementNS(REL, "Relationship").apply {
                setAttribute("Id", "rId$n")
                setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments")
                setAttribute("Target", "comments.xml")
            })
        }
        return mapOf(name to Xml.document(doc).toByteArray(Charsets.UTF_8))
    }

    private fun typesWithComments(bundle: Bundle): Map<String, ByteArray> {
        val name = "[Content_Types].xml"
        val doc = Xml.parse(bundle.text(name) ?: return emptyMap())
        if (Xml.all(doc.documentElement, CT, "Override").none { it.getAttribute("PartName") == "/word/comments.xml" }) {
            doc.documentElement.appendChild(doc.createElementNS(CT, "Override").apply {
                setAttribute("PartName", "/word/comments.xml")
                setAttribute("ContentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml")
            })
        }
        return mapOf(name to Xml.document(doc).toByteArray(Charsets.UTF_8))
    }
}
