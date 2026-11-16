package eu.eurise.office.rapida

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text

/**
 * Una presentazione letta per la vista rapida (tavola OffLetturaPresentazione): le diapositive una sotto l'altra, con
 * titolo, testi, tabelle, immagini e note del relatore. Sola lettura: l'impaginazione esatta resta al motore
 * («Aspetto esatto»). [ratio] è larghezza / altezza della diapositiva.
 */
data class SlideDeck(val slides: List<Slide>, val ratio: Float)

/** [background]: colore pieno dello sfondo (0xRRGGBB) se la diapositiva lo dichiara, altrimenti null (bianco). */
data class Slide(
    val number: Int,
    val title: String,
    val subtitle: String,
    val shapes: List<SlideShape>,
    val notes: String,
    val background: Int?,
    val hidden: Boolean = false,
) {
    /** Tutto il testo della diapositiva e delle note, per la ricerca. */
    val text: String
        get() = buildString {
            append(title).append('\n').append(subtitle).append('\n')
            shapes.forEach { s ->
                when (s) {
                    is SlideText -> s.paras.forEach { append(it.text).append('\n') }
                    is SlideTable -> s.rows.forEach { r -> append(r.joinToString("\t")).append('\n') }
                    is SlideImage -> append(s.name).append('\n')
                }
            }
            append(notes)
        }
}

sealed interface SlideShape
data class SlideText(val paras: List<SlidePara>) : SlideShape
data class SlidePara(val spans: List<Span>, val level: Int = 0, val bullet: Boolean = false, val align: Align = Align.Start) {
    val text: String get() = spans.joinToString("") { it.text }
}
/** Un'immagine dentro il file: [path] è la voce dello zip. */
data class SlideImage(val path: String, val name: String) : SlideShape
data class SlideTable(val rows: List<List<String>>) : SlideShape

/** Un formato di presentazione: PowerPoint (.pptx) o OpenDocument (.odp). */
interface SlideFormat {
    fun read(bundle: Bundle): SlideDeck
}

private fun mergeSpans(spans: List<Span>): List<Span> {
    val out = ArrayList<Span>()
    for (s in spans) {
        val last = out.lastOrNull()
        if (last != null && last.bold == s.bold && last.italic == s.italic && last.underline == s.underline) out[out.lastIndex] = last.copy(text = last.text + s.text)
        else out += s
    }
    return out
}

/** Toglie i paragrafi vuoti in testa e in coda (nelle caselle di testo ce ne sono spesso). */
private fun trimParas(paras: List<SlidePara>): List<SlidePara> {
    val first = paras.indexOfFirst { it.text.isNotBlank() }
    if (first < 0) return emptyList()
    val last = paras.indexOfLast { it.text.isNotBlank() }
    return paras.subList(first, last + 1)
}

/**
 * PowerPoint (.pptx): l'ordine delle diapositive da `ppt/presentation.xml`, le forme di ogni diapositiva ordinate
 * dall'alto in basso, le immagini dai collegamenti della diapositiva, le note dalla sua pagina di note.
 */
object Pptx : SlideFormat {
    const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    override fun read(bundle: Bundle): SlideDeck {
        val pres = Xml.parse(bundle.text("ppt/presentation.xml") ?: error("manca ppt/presentation.xml")).documentElement
        val rels = rels(bundle, "ppt/presentation.xml")
        val size = Xml.child(pres, P, "sldSz")
        val cx = size?.getAttribute("cx")?.toFloatOrNull()
        val cy = size?.getAttribute("cy")?.toFloatOrNull()
        val ratio = if (cx != null && cy != null && cy > 0f) cx / cy else 16f / 9f
        val ids = Xml.child(pres, P, "sldIdLst")?.let { Xml.children(it).filter { e -> e.localName == "sldId" } }.orEmpty()
        val slides = ids.mapIndexedNotNull { i, id ->
            val path = rels[id.getAttributeNS(R, "id")]?.first ?: return@mapIndexedNotNull null
            runCatching { slide(bundle, path, i + 1) }.getOrNull()
        }
        return SlideDeck(slides, ratio.coerceIn(0.5f, 3f))
    }

    /** I collegamenti di una parte: id → (percorso nello zip, tipo). */
    internal fun rels(bundle: Bundle, part: String): Map<String, Pair<String, String>> {
        val dir = part.substringBeforeLast('/', "")
        val file = part.substringAfterLast('/')
        val xml = bundle.text("$dir/_rels/$file.rels") ?: return emptyMap()
        val root = Xml.parse(xml).documentElement
        return Xml.children(root).filter { it.localName == "Relationship" && it.getAttribute("TargetMode") != "External" }.associate {
            it.getAttribute("Id") to (resolve(dir, it.getAttribute("Target")) to it.getAttribute("Type"))
        }
    }

    internal fun resolve(dir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayList(dir.split('/').filter { it.isNotEmpty() })
        for (p in target.split('/')) when (p) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += p
        }
        return parts.joinToString("/")
    }

    private fun slide(bundle: Bundle, path: String, number: Int): Slide {
        val root = Xml.parse(bundle.text(path) ?: error("manca $path")).documentElement
        val rels = rels(bundle, path)
        val cSld = Xml.child(root, P, "cSld") ?: error("diapositiva senza cSld")
        val background = Xml.child(cSld, P, "bg")?.let { Xml.child(it, P, "bgPr") }?.let { Xml.child(it, A, "solidFill") }?.let(::color)
        val found = ArrayList<Pair<Long, SlideShape>>()
        var title = ""
        var subtitle = ""
        fun walk(tree: Element) {
            for (e in Xml.children(tree)) when (e.localName) {
                "sp" -> {
                    val ph = Xml.child(e, P, "nvSpPr")?.let { Xml.child(it, P, "nvPr") }?.let { Xml.child(it, P, "ph") }
                    val type = ph?.getAttribute("type").orEmpty()
                    val body = Xml.child(e, P, "txBody") ?: continue
                    val bulleted = ph != null && type in setOf("", "body", "obj")
                    val paras = trimParas(Xml.children(body).filter { it.namespaceURI == A && it.localName == "p" }.map { para(it, bulleted) })
                    if (paras.isEmpty()) continue
                    when (type) {
                        "title", "ctrTitle" -> if (title.isEmpty()) { title = paras.joinToString(" ") { it.text.replace('\n', ' ') }.trim(); continue }
                        "subTitle" -> if (subtitle.isEmpty()) { subtitle = paras.joinToString("\n") { it.text }.trim(); continue }
                        "sldNum", "dt", "ftr", "hdr" -> continue
                    }
                    found += top(e) to SlideText(paras)
                }
                "pic" -> {
                    val embed = Xml.all(e, A, "blip").firstOrNull()?.getAttributeNS(R, "embed").orEmpty()
                    val target = rels[embed]?.first ?: continue
                    val name = Xml.child(e, P, "nvPicPr")?.let { Xml.child(it, P, "cNvPr") }?.let { it.getAttribute("descr").ifBlank { it.getAttribute("name") } }.orEmpty()
                    found += top(e) to SlideImage(target, name)
                }
                "graphicFrame" -> {
                    val tbl = Xml.all(e, A, "tbl").firstOrNull() ?: continue
                    val rows = Xml.children(tbl).filter { it.localName == "tr" }.map { tr ->
                        Xml.children(tr).filter { it.localName == "tc" }.map { tc ->
                            Xml.all(tc, A, "p").joinToString("\n") { p -> para(p, false).text }.trim()
                        }
                    }
                    if (rows.isNotEmpty()) found += top(e) to SlideTable(rows)
                }
                "grpSp" -> walk(e)
                "AlternateContent" -> Xml.children(e).firstOrNull { it.localName == "Fallback" }?.let(::walk)
            }
        }
        Xml.child(cSld, P, "spTree")?.let(::walk)
        val notes = rels.values.firstOrNull { it.second.endsWith("/notesSlide") }?.first?.let { notes(bundle, it) }.orEmpty()
        return Slide(
            number, title, subtitle, found.sortedBy { it.first }.map { it.second }, notes, background,
            hidden = root.getAttribute("show") == "0",
        )
    }

    /** La distanza dall'alto della forma (EMU); senza posizione propria (segnaposto del layout) va in fondo. */
    private fun top(e: Element): Long {
        val xfrm = Xml.all(e, A, "xfrm").firstOrNull() ?: Xml.all(e, P, "xfrm").firstOrNull()
        return xfrm?.let { Xml.child(it, A, "off") }?.getAttribute("y")?.toLongOrNull() ?: Long.MAX_VALUE / 2
    }

    private fun color(fill: Element): Int? =
        Xml.child(fill, A, "srgbClr")?.getAttribute("val")?.toIntOrNull(16)

    private fun para(p: Element, bulletedByDefault: Boolean): SlidePara {
        val pPr = Xml.child(p, A, "pPr")
        val level = pPr?.getAttribute("lvl")?.toIntOrNull() ?: 0
        val bullet = when {
            pPr != null && Xml.child(pPr, A, "buNone") != null -> false
            pPr != null && (Xml.child(pPr, A, "buChar") != null || Xml.child(pPr, A, "buAutoNum") != null) -> true
            else -> bulletedByDefault
        }
        val align = when (pPr?.getAttribute("algn")) {
            "ctr" -> Align.Center
            "r" -> Align.End
            "just", "dist" -> Align.Justify
            else -> Align.Start
        }
        val spans = ArrayList<Span>()
        for (e in Xml.children(p)) when (e.localName) {
            "r", "fld" -> {
                val rPr = Xml.child(e, A, "rPr")
                val text = Xml.child(e, A, "t")?.textContent.orEmpty()
                if (text.isNotEmpty()) spans += Span(
                    text,
                    bold = rPr?.getAttribute("b") == "1" || rPr?.getAttribute("b") == "true",
                    italic = rPr?.getAttribute("i") == "1" || rPr?.getAttribute("i") == "true",
                    underline = rPr?.getAttribute("u").let { !it.isNullOrEmpty() && it != "none" },
                )
            }
            "br" -> spans += Span("\n")
        }
        return SlidePara(mergeSpans(spans), level.coerceIn(0, 8), bullet && spans.isNotEmpty(), align)
    }

    private fun notes(bundle: Bundle, path: String): String {
        val root = Xml.parse(bundle.text(path) ?: return "").documentElement
        return Xml.all(root, P, "sp").filter { sp ->
            Xml.all(sp, P, "ph").firstOrNull()?.getAttribute("type") == "body"
        }.joinToString("\n") { sp ->
            Xml.all(sp, A, "p").joinToString("\n") { para(it, false).text }
        }.trim()
    }
}

/**
 * OpenDocument presentazione (.odp): le pagine di `content.xml` (`draw:page`), le cornici ordinate dall'alto in basso,
 * grassetto e corsivo dagli stili automatici, le note da `presentation:notes`.
 */
object Odp : SlideFormat {
    private const val OFFICE = Odt.OFFICE
    private const val TEXT = Odt.TEXT
    private const val TABLE = Odt.TABLE
    private const val STYLE = Odt.STYLE
    private const val FO = Odt.FO
    const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    const val PRESENTATION = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
    const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    const val XLINK = "http://www.w3.org/1999/xlink"

    private class Look(val bold: Boolean?, val italic: Boolean?, val underline: Boolean?, val parent: String?)

    override fun read(bundle: Bundle): SlideDeck {
        val doc = Xml.parse(bundle.text("content.xml") ?: error("manca content.xml"))
        val stylesDoc = bundle.text("styles.xml")?.let { runCatching { Xml.parse(it) }.getOrNull() }
        val looks = looks(doc.documentElement) + (stylesDoc?.documentElement?.let(::looks) ?: emptyMap())
        val fills = fills(doc.documentElement) + (stylesDoc?.documentElement?.let(::fills) ?: emptyMap())
        val ratio = stylesDoc?.documentElement?.let { root ->
            Xml.all(root, STYLE, "page-layout-properties").firstNotNullOfOrNull { p ->
                val w = length(p.getAttributeNS(FO, "page-width"))
                val h = length(p.getAttributeNS(FO, "page-height"))
                if (w != null && h != null && h > 0f) w / h else null
            }
        } ?: (16f / 9f)
        val body = Xml.child(doc.documentElement, OFFICE, "body")?.let { Xml.child(it, OFFICE, "presentation") } ?: error("non è una presentazione")
        val pages = Xml.children(body).filter { it.namespaceURI == DRAW && it.localName == "page" }
        val slides = pages.mapIndexed { i, page -> page(page, i + 1, looks, fills) }
        return SlideDeck(slides, ratio.coerceIn(0.5f, 3f))
    }

    private fun page(page: Element, number: Int, looks: Map<String, Look>, fills: Map<String, Int>): Slide {
        val found = ArrayList<Pair<Float, SlideShape>>()
        var title = ""
        var subtitle = ""
        var notes = ""
        fun walk(parent: Element) {
            for (e in Xml.children(parent)) {
                if (e.namespaceURI == PRESENTATION && e.localName == "notes") {
                    notes = Xml.children(e).filter { it.getAttributeNS(PRESENTATION, "class") == "notes" }
                        .joinToString("\n") { f -> paras(f, looks, false).joinToString("\n") { it.text } }.trim()
                    continue
                }
                if (e.namespaceURI != DRAW) continue
                val y = length(e.getAttributeNS(SVG, "y")) ?: Float.MAX_VALUE / 2
                val cls = e.getAttributeNS(PRESENTATION, "class")
                when (e.localName) {
                    "g" -> walk(e)
                    "frame", "custom-shape", "rect", "ellipse", "polygon", "path" -> {
                        if (cls in setOf("page-number", "date-time", "footer", "header")) continue
                        val image = Xml.child(e, DRAW, "image")
                        val table = Xml.all(e, TABLE, "table").firstOrNull()
                        when {
                            table != null -> {
                                val rows = Xml.all(table, TABLE, "table-row").map { row ->
                                    Xml.children(row).filter { it.namespaceURI == TABLE && it.localName == "table-cell" }.map { cell ->
                                        Xml.children(cell).filter { it.namespaceURI == TEXT }.joinToString("\n") { it.textContent }.trim()
                                    }
                                }
                                if (rows.isNotEmpty()) found += y to SlideTable(rows)
                            }
                            image != null && Xml.child(e, DRAW, "text-box") == null -> {
                                val href = image.getAttributeNS(XLINK, "href").removePrefix("./")
                                val name = Xml.child(e, SVG, "desc")?.textContent ?: e.getAttributeNS(DRAW, "name")
                                if (href.isNotEmpty() && !href.contains("://")) found += y to SlideImage(href, name)
                            }
                            else -> {
                                val list = trimParas(paras(e, looks, cls == "outline"))
                                if (list.isEmpty()) continue
                                when (cls) {
                                    "title" -> if (title.isEmpty()) { title = list.joinToString(" ") { it.text.replace('\n', ' ') }.trim(); continue }
                                    "subtitle" -> if (subtitle.isEmpty()) { subtitle = list.joinToString("\n") { it.text }.trim(); continue }
                                }
                                found += y to SlideText(list)
                            }
                        }
                    }
                }
            }
        }
        walk(page)
        val background = fills[page.getAttributeNS(DRAW, "style-name")]
        return Slide(number, title, subtitle, found.sortedBy { it.first }.map { it.second }, notes, background)
    }

    /** I paragrafi di una cornice (o forma) con il loro livello d'elenco. */
    private fun paras(shape: Element, looks: Map<String, Look>, outline: Boolean): List<SlidePara> {
        val out = ArrayList<SlidePara>()
        fun walk(parent: Element, level: Int, inList: Boolean) {
            for (e in Xml.children(parent)) when {
                e.namespaceURI == TEXT && (e.localName == "p" || e.localName == "h") -> out += para(e, looks, level, inList)
                e.namespaceURI == TEXT && e.localName == "list" -> Xml.children(e).forEach { item ->
                    // In un elenco di primo livello che contiene solo un altro elenco, il livello sale di uno.
                    if (item.localName == "list-item" || item.localName == "list-header") walk(item, if (inList) level + 1 else level, true)
                }
                e.namespaceURI == DRAW && e.localName == "text-box" -> walk(e, level, inList)
            }
        }
        walk(shape, 0, false)
        // Le liste del contorno sono elenchi puntati anche quando lo stile non lo dice nel paragrafo.
        return if (outline) out.map { if (it.text.isNotBlank()) it.copy(bullet = true) else it } else out
    }

    private fun para(p: Element, looks: Map<String, Look>, level: Int, bullet: Boolean): SlidePara {
        val spans = ArrayList<Span>()
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
                }
                c = c.nextSibling
            }
        }
        walk(p, resolve(p.getAttributeNS(TEXT, "style-name"), looks, Triple(false, false, false)))
        return SlidePara(mergeSpans(spans), level.coerceIn(0, 8), bullet && spans.isNotEmpty())
    }

    private fun resolve(name: String, looks: Map<String, Look>, base: Triple<Boolean, Boolean, Boolean>): Triple<Boolean, Boolean, Boolean> {
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline: Boolean? = null
        var current: String? = name
        var guard = 0
        while (!current.isNullOrEmpty() && guard++ < 10) {
            val look = looks[current] ?: break
            if (bold == null) bold = look.bold
            if (italic == null) italic = look.italic
            if (underline == null) underline = look.underline
            current = look.parent
        }
        return Triple(bold ?: base.first, italic ?: base.second, underline ?: base.third)
    }

    private fun looks(root: Element): Map<String, Look> = Xml.all(root, STYLE, "style").associate { s ->
        val tp = Xml.child(s, STYLE, "text-properties")
        val weight = tp?.getAttributeNS(FO, "font-weight")
        val style = tp?.getAttributeNS(FO, "font-style")
        val under = tp?.getAttributeNS(STYLE, "text-underline-style")
        s.getAttributeNS(STYLE, "name") to Look(
            bold = weight?.takeIf { it.isNotEmpty() }?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 },
            italic = style?.takeIf { it.isNotEmpty() }?.let { it == "italic" || it == "oblique" },
            underline = under?.takeIf { it.isNotEmpty() }?.let { it != "none" },
            parent = s.getAttributeNS(STYLE, "parent-style-name").ifEmpty { null },
        )
    }

    /** Sfondi pieni delle pagine: nome dello stile → colore. */
    private fun fills(root: Element): Map<String, Int> = Xml.all(root, STYLE, "style").mapNotNull { s ->
        val props = Xml.child(s, STYLE, "drawing-page-properties") ?: return@mapNotNull null
        if (props.getAttributeNS(DRAW, "fill") != "solid") return@mapNotNull null
        val color = props.getAttributeNS(DRAW, "fill-color").removePrefix("#").toIntOrNull(16) ?: return@mapNotNull null
        s.getAttributeNS(STYLE, "name") to color
    }.toMap()

    /** Una lunghezza ODF («2.5cm», «10in», «12pt», «30mm») in centimetri. */
    internal fun length(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        val m = Regex("""^\s*(-?[0-9.]+)\s*([a-z]*)\s*$""").find(value) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return when (m.groupValues[2]) {
            "cm", "" -> n
            "mm" -> n / 10f
            "in" -> n * 2.54f
            "pt" -> n * 2.54f / 72f
            "pc" -> n * 2.54f / 6f
            "px" -> n * 2.54f / 96f
            else -> n
        }
    }
}
