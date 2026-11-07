package eu.eurise.feature.notes.doc

/**
 * Scrive e legge il contenuto delle note in HTML5. Il formato è quello che finirà nelle cartelle su
 * Murena: si apre con qualsiasi browser e conserva colori, evidenziature, elenchi e tabelle.
 *
 * Le informazioni che servono all'app stanno in attributi `data-*` (tinta, stato della voce, attività
 * collegata); gli stili in linea ripetono i colori, così anche un browser li mostra.
 */
object NoteHtml {

    /** Colore del testo e dell'evidenziatore per ogni tinta, nel tema chiaro. */
    private val tintHex = mapOf(
        "BluNotte" to ("#3E5C8A" to "#D9E1EE"),
        "Salvia" to ("#4F6B5A" to "#DCE7DF"),
        "Ottone" to ("#85672B" to "#EFE2BF"),
        "Borgogna" to ("#8C2F39" to "#F3D9DC"),
        "Petrolio" to ("#2E6E73" to "#D3E6E7"),
        "Prugna" to ("#6B4A7A" to "#E6DCEB"),
        "Terracotta" to ("#A0522D" to "#F2DED3"),
        "Grafite" to ("#55585E" to "#E2E3E5"),
    )

    /** Foglio di stile del documento esportato: elenchi di controllo e tabelle leggibili anche fuori dall'app. */
    private const val STYLE =
        "body{font-family:system-ui,sans-serif;max-width:46rem;margin:2rem auto;padding:0 1rem;line-height:1.6;color:#1E1D1B}" +
            "ul.checklist{list-style:none;padding-left:0}ul.checklist input{margin-right:.5em}" +
            "table{border-collapse:collapse}th,td{border:1px solid #DADAD3;padding:.3em .6em;text-align:left}" +
            "mark{padding:0 .15em;border-radius:.2em}"

    // Scrittura

    /** Il contenuto della nota (solo i blocchi, senza intestazione), come viene salvato nel database. */
    fun write(blocks: List<Block>): String = buildString {
        var openList: String? = null
        var drawingIndex = 0
        fun closeList() {
            openList?.let { append("</").append(if (it == "ol") "ol" else "ul").append(">\n") }
            openList = null
        }
        for (block in blocks) {
            val listTag = (block as? TextBlock)?.kind?.let {
                when (it) {
                    TextKind.Bullet -> "ul"
                    TextKind.Numbered -> "ol"
                    TextKind.Check -> "check"
                    else -> null
                }
            }
            if (listTag != openList) {
                closeList()
                if (listTag != null) {
                    append(if (listTag == "check") "<ul class=\"checklist\">" else "<$listTag>").append('\n')
                    openList = listTag
                }
            }
            when (block) {
                is TextBlock -> when (block.kind) {
                    TextKind.Paragraph -> append("<p>").append(inline(block.text)).append("</p>\n")
                    TextKind.Heading -> append("<h2>").append(inline(block.text)).append("</h2>\n")
                    TextKind.Subheading -> append("<h3>").append(inline(block.text)).append("</h3>\n")
                    TextKind.Bullet, TextKind.Numbered -> append("<li>").append(inline(block.text)).append("</li>\n")
                    TextKind.Check -> {
                        append("<li data-checked=\"").append(block.checked).append('"')
                        block.taskId?.let { append(" data-task=\"").append(it).append('"') }
                        append("><input type=\"checkbox\" disabled")
                        if (block.checked) append(" checked")
                        append(">").append(inline(block.text)).append("</li>\n")
                    }
                }
                is TableBlock -> {
                    append("<table>\n")
                    block.rows.forEachIndexed { r, row ->
                        val cell = if (r == 0) "th" else "td"
                        append("<tr>")
                        row.forEach { append("<$cell>").append(escape(it)).append("</$cell>") }
                        append("</tr>\n")
                    }
                    append("</table>\n")
                }
                is NoteLinkBlock -> append("<p class=\"note-link\"><a href=\"note:")
                    .append(block.noteId).append("\" data-note=\"").append(block.noteId).append("\">")
                    .append(escape(block.title)).append("</a></p>\n")
                is DividerBlock -> append("<hr>\n")
                is DrawingBlock -> append(drawingSvg(block, drawingIndex++))
            }
        }
        closeList()
    }

    /** Il documento HTML completo, con titolo e stile: è il file che si apre nel browser. */
    fun document(title: String, blocks: List<Block>, meta: Map<String, String> = emptyMap()): String =
        "<!doctype html>\n<html lang=\"it\">\n<head>\n<meta charset=\"utf-8\">\n" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
            "<meta name=\"generator\" content=\"Eurise Note\">\n" +
            meta.entries.joinToString("") { (k, v) -> "<meta name=\"eurise:$k\" content=\"${escape(v)}\">\n" } +
            "<title>${escape(title)}</title>\n<style>$STYLE</style>\n</head>\n<body>\n" +
            "<h1>${escape(title)}</h1>\n" + write(blocks) + "</body>\n</html>\n"

    /** Colore pieno di una tinta nell'SVG (l'inchiostro è il nero caldo della suite). */
    private fun inkHex(tint: String?): String = tint?.let { tintHex[it]?.first } ?: "#1E1D1B"

    /**
     * Il disegno come SVG dentro un `<figure>`. Ogni tratto è il suo contorno pieno (si vede uguale in ogni
     * browser) e porta negli attributi `data-*` i punti originali, per poterlo modificare di nuovo.
     */
    private fun drawingSvg(d: DrawingBlock, index: Int): String = buildString {
        val w = DrawingBlock.WIDTH
        val h = d.height
        val paper = when (d.paper) { Paper.Blank -> "blank"; Paper.Lines -> "lines"; Paper.Grid -> "grid" }
        append("<figure class=\"drawing\">")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $w $h\" width=\"100%\" data-eurise=\"drawing\" data-paper=\"$paper\">")
        val step = DrawingBlock.PAPER_STEP.toInt()
        append("<rect width=\"$w\" height=\"$h\" fill=\"#FFFFFF\"/>")
        when (d.paper) {
            Paper.Grid -> append("<defs><pattern id=\"paper$index\" width=\"$step\" height=\"$step\" patternUnits=\"userSpaceOnUse\"><path d=\"M$step 0H0V$step\" fill=\"none\" stroke=\"#E6E4DD\" stroke-width=\"2\"/></pattern></defs><rect width=\"$w\" height=\"$h\" fill=\"url(#paper$index)\"/>")
            Paper.Lines -> append("<defs><pattern id=\"paper$index\" width=\"$w\" height=\"$step\" patternUnits=\"userSpaceOnUse\"><path d=\"M0 ${step - 1}H$w\" stroke=\"#E6E4DD\" stroke-width=\"2\"/></pattern></defs><rect width=\"$w\" height=\"$h\" fill=\"url(#paper$index)\"/>")
            Paper.Blank -> Unit
        }
        for (s in d.strokes) {
            val o = s.outline()
            if (o.size < 6) continue
            append("<path d=\"M")
            for (i in o.indices step 2) {
                if (i > 0) append('L')
                append(num(o[i])).append(' ').append(num(o[i + 1]))
            }
            append("Z\" fill=\"").append(inkHex(s.tint)).append('"')
            if (s.tool.alpha() < 1f) append(" fill-opacity=\"").append(num(s.tool.alpha())).append('"')
            append(" data-tool=\"").append(s.tool.name.lowercase()).append('"')
            s.tint?.let { append(" data-tint=\"").append(it).append('"') }
            append(" data-size=\"").append(num(s.size)).append('"')
            if (s.shape) append(" data-shape=\"1\"")
            append(" data-points=\"")
            s.points.forEachIndexed { i, p ->
                if (i > 0) append(' ')
                append(num(p.x)).append(',').append(num(p.y)).append(',').append(num(p.p))
            }
            append("\"/>")
        }
        append("</svg></figure>\n")
    }

    /** Numero con al più due decimali, senza zeri inutili: tiene piccolo il file. */
    private fun num(v: Float): String {
        val r = kotlin.math.round(v * 100f) / 100f
        return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
    }

    private fun inline(rich: RichText): String {
        val text = rich.text
        if (text.isEmpty()) return "<br>"
        val cuts = sortedSetOf(0, text.length)
        rich.spans.forEach { cuts += it.start; cuts += it.end }
        val points = cuts.toList()
        return buildString {
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                if (b <= a) continue
                val marks = rich.spans.filter { it.start <= a && it.end >= b }.map { it.mark }
                val ordered = marks.sortedBy { markOrder(it) }
                ordered.forEach { append(open(it)) }
                append(escape(text.substring(a, b)).replace("\n", "<br>"))
                ordered.asReversed().forEach { append(close(it)) }
            }
        }
    }

    private fun markOrder(mark: Mark) = when (mark) {
        Mark.Bold -> 0
        Mark.Italic -> 1
        Mark.Underline -> 2
        Mark.Strike -> 3
        is Mark.Color -> 4
        is Mark.Highlight -> 5
    }

    private fun open(mark: Mark): String = when (mark) {
        Mark.Bold -> "<b>"
        Mark.Italic -> "<i>"
        Mark.Underline -> "<u>"
        Mark.Strike -> "<s>"
        is Mark.Color -> "<span data-color=\"${mark.tint}\" style=\"color:${tintHex[mark.tint]?.first ?: "inherit"}\">"
        is Mark.Highlight -> "<mark data-tint=\"${mark.tint}\" style=\"background:${tintHex[mark.tint]?.second ?: "#EFE2BF"}\">"
    }

    private fun close(mark: Mark): String = when (mark) {
        Mark.Bold -> "</b>"
        Mark.Italic -> "</i>"
        Mark.Underline -> "</u>"
        Mark.Strike -> "</s>"
        is Mark.Color -> "</span>"
        is Mark.Highlight -> "</mark>"
    }

    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    // Lettura

    /**
     * Legge l'HTML scritto da [write] (o da [document]) e lo trasforma in blocchi. È tollerante: i tag
     * sconosciuti vengono ignorati e il loro testo resta. [ids] assegna l'identità ai blocchi.
     */
    fun read(html: String, ids: () -> Long): List<Block> = Reader(html, ids).run()

    /** I `<meta name="eurise:…">` di un documento, senza il prefisso. */
    fun meta(html: String): Map<String, String> {
        val head = html.substringBefore("</head>", html.take(4_000))
        return META.findAll(head).associate { it.groupValues[1] to decode(it.groupValues[2]) }
    }

    /** Il titolo di un documento (`<title>`). */
    fun title(html: String): String =
        TITLE.find(html)?.groupValues?.get(1)?.let { decode(it).trim() }.orEmpty()

    private val META = Regex("""<meta\s+name="eurise:([a-z-]+)"\s+content="([^"]*)"""")
    private val TITLE = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)

    /** Una nota di solo testo: ogni riga diventa un paragrafo. */
    fun fromPlain(text: String, ids: () -> Long): List<Block> =
        text.split('\n').map { TextBlock(ids(), TextKind.Paragraph, RichText(it)) }

    /** Il testo semplice dei blocchi: serve per ricerca, anteprime e tag. */
    fun plainText(blocks: List<Block>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is TextBlock -> block.text.text
            is TableBlock -> block.rows.joinToString("\n") { it.joinToString(" · ") }
            is NoteLinkBlock -> block.title
            is DividerBlock, is DrawingBlock -> ""
        }
    }.trim('\n')

    private class Tag(val name: String, val closing: Boolean, val attrs: Map<String, String>)

    private class Reader(private val html: String, private val ids: () -> Long) {
        private val blocks = mutableListOf<Block>()

        // Blocco di testo in costruzione.
        private var kind: TextKind? = null
        private val text = StringBuilder()
        private val spans = mutableListOf<Span>()
        private var checked = false
        private var taskId: Long? = null

        /** Stili aperti, con il tag che li ha aperti e il punto del testo dove iniziano. */
        private val open = mutableListOf<Triple<String, Mark?, Int>>()

        private var listKind: TextKind? = null
        private var inBody = true
        private var skipDepth = 0

        // Tabella in costruzione.
        private var table: MutableList<MutableList<String>>? = null
        private var cell: StringBuilder? = null

        // Disegno in costruzione.
        private var drawing: DrawingBlock? = null

        // Collegamento a una nota in costruzione.
        private var linkNote: Long? = null
        private val linkText = StringBuilder()

        fun run(): List<Block> {
            var i = 0
            while (i < html.length) {
                val lt = html.indexOf('<', i)
                val end = if (lt < 0) html.length else lt
                if (end > i) onText(decode(html.substring(i, end)))
                if (lt < 0) break
                if (html.startsWith("<!--", lt)) {
                    val close = html.indexOf("-->", lt)
                    i = if (close < 0) html.length else close + 3
                    continue
                }
                val gt = html.indexOf('>', lt)
                if (gt < 0) break
                parseTag(html.substring(lt + 1, gt))?.let(::onTag)
                i = gt + 1
            }
            finishText()
            finishTable()
            finishDrawing()
            return blocks
        }

        private fun onText(raw: String) {
            if (skipDepth > 0 || !inBody || drawing != null) return
            cell?.let { it.append(raw.collapse()); return }
            if (table != null) return
            if (linkNote != null) { linkText.append(raw); return }
            val content = raw.collapse()
            if (kind == null) {
                if (content.isBlank()) return
                startText(listKind ?: TextKind.Paragraph)
            }
            append(if (text.isEmpty()) content.trimStart() else content)
        }

        private fun append(s: String) {
            if (s.isEmpty()) return
            val start = text.length
            text.append(s)
            open.forEach { (_, mark, _) -> if (mark != null) spans += Span(start, text.length, mark) }
        }

        private fun onTag(tag: Tag) {
            val n = tag.name
            if (n == "head" || n == "style" || n == "script" || n == "title") {
                if (tag.closing) skipDepth = (skipDepth - 1).coerceAtLeast(0) else skipDepth++
                return
            }
            if (skipDepth > 0) return
            if (n == "h1" && !tag.closing) { inBody = false; return }
            if (n == "h1" && tag.closing) { inBody = true; return }
            if (!inBody) return
            if (drawing != null) {
                onDrawingTag(tag)
                return
            }
            when (n) {
                "figure" -> if (!tag.closing && tag.attrs["class"]?.split(' ')?.contains("drawing") == true) {
                    finishText()
                    drawing = DrawingBlock(ids())
                }
                "svg" -> if (!tag.closing && tag.attrs["data-eurise"] == "drawing") {
                    finishText()
                    drawing = DrawingBlock(ids())
                    onDrawingTag(tag)
                }
                "p", "div" -> if (tag.closing) {
                    finishLink()
                    finishText()
                } else {
                    finishText()
                    if (tag.attrs["class"] == "note-link") linkNote = -1
                    else startText(TextKind.Paragraph)
                }
                "a" -> if (!tag.closing && linkNote != null) {
                    linkNote = tag.attrs["data-note"]?.toLongOrNull() ?: -1
                }
                "h2" -> heading(tag, TextKind.Heading)
                "h3", "h4", "h5", "h6" -> heading(tag, TextKind.Subheading)
                "ul" -> if (tag.closing) { finishText(); listKind = null } else {
                    finishText()
                    listKind = if (tag.attrs["class"]?.split(' ')?.contains("checklist") == true) TextKind.Check else TextKind.Bullet
                }
                "ol" -> if (tag.closing) { finishText(); listKind = null } else { finishText(); listKind = TextKind.Numbered }
                "li" -> if (tag.closing) finishText() else {
                    finishText()
                    startText(listKind ?: TextKind.Bullet)
                    if (tag.attrs.containsKey("data-checked")) {
                        kind = TextKind.Check
                        checked = tag.attrs["data-checked"] == "true"
                        taskId = tag.attrs["data-task"]?.toLongOrNull()
                    }
                }
                "br" -> if (cell != null) cell?.append(' ') else if (kind != null) append("\n") else startText(TextKind.Paragraph)
                "hr" -> { finishText(); blocks += DividerBlock(ids()) }
                "table" -> if (tag.closing) finishTable() else { finishText(); table = mutableListOf() }
                "tr" -> if (!tag.closing) table?.add(mutableListOf())
                "td", "th" -> if (tag.closing) {
                    cell?.let { c -> table?.lastOrNull()?.add(c.toString().trim()) }
                    cell = null
                } else cell = StringBuilder()
                "b", "strong" -> inline(tag, Mark.Bold)
                "i", "em" -> inline(tag, Mark.Italic)
                "u" -> inline(tag, Mark.Underline)
                "s", "strike", "del" -> inline(tag, Mark.Strike)
                "span" -> inline(tag, tag.attrs["data-color"]?.let { Mark.Color(it) })
                "mark" -> inline(tag, Mark.Highlight(tag.attrs["data-tint"] ?: "Ottone"))
            }
        }

        private fun onDrawingTag(tag: Tag) {
            val d = drawing ?: return
            when {
                tag.name == "figure" && tag.closing -> finishDrawing()
                tag.name == "svg" && tag.closing -> finishDrawing()
                tag.name == "svg" -> {
                    val height = tag.attrs["viewbox"]?.trim()?.split(Regex("[\\s,]+"))?.getOrNull(3)?.toFloatOrNull()?.toInt()
                    val paper = when (tag.attrs["data-paper"]) { "blank" -> Paper.Blank; "lines" -> Paper.Lines; else -> Paper.Grid }
                    drawing = d.copy(height = (height ?: d.height).coerceIn(100, DrawingBlock.MAX_HEIGHT), paper = paper)
                }
                tag.name == "path" && !tag.closing -> {
                    val tool = tag.attrs["data-tool"]?.let { t -> Tool.entries.firstOrNull { it.name.equals(t, true) } } ?: return
                    val points = tag.attrs["data-points"].orEmpty().trim().split(Regex("\\s+")).mapNotNull { triple ->
                        val v = triple.split(',')
                        val x = v.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
                        val y = v.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                        InkPoint(x, y, v.getOrNull(2)?.toFloatOrNull() ?: 0.5f)
                    }
                    if (points.isEmpty()) return
                    val stroke = Stroke(
                        tool = tool,
                        tint = tag.attrs["data-tint"],
                        size = tag.attrs["data-size"]?.toFloatOrNull() ?: 6f,
                        points = points,
                        shape = tag.attrs["data-shape"] == "1",
                    )
                    drawing = d.copy(strokes = d.strokes + stroke)
                }
            }
        }

        private fun finishDrawing() {
            drawing?.let { blocks += it }
            drawing = null
        }

        private fun heading(tag: Tag, k: TextKind) {
            finishText()
            if (!tag.closing) startText(k)
        }

        private fun inline(tag: Tag, mark: Mark?) {
            if (tag.closing) {
                val idx = open.indexOfLast { it.first == tag.name }
                if (idx >= 0) open.removeAt(idx)
            } else {
                open += Triple(tag.name, mark, text.length)
            }
        }

        private fun startText(k: TextKind) {
            kind = k
            text.clear()
            spans.clear()
            checked = false
            taskId = null
        }

        private fun finishText() {
            val k = kind ?: return
            var content = text.toString()
            if (content == "\n") content = ""
            val trimmedEnd = content.trimEnd(' ', '\n')
            blocks += TextBlock(ids(), k, RichText(trimmedEnd, normalize(trimmedEnd.length, spans)), checked, taskId)
            kind = null
            text.clear()
            spans.clear()
        }

        private fun finishLink() {
            val id = linkNote ?: return
            if (id > 0) blocks += NoteLinkBlock(ids(), id, linkText.toString().collapse().trim())
            linkNote = null
            linkText.clear()
        }

        private fun finishTable() {
            val rows = table ?: return
            table = null
            cell = null
            val filled = rows.filter { it.isNotEmpty() }
            if (filled.isEmpty()) return
            val width = filled.maxOf { it.size }
            blocks += TableBlock(ids(), filled.map { r -> r + List(width - r.size) { "" } })
        }

        private fun parseTag(body: String): Tag? {
            var s = body.trim()
            if (s.startsWith("!") || s.startsWith("?")) return null
            val closing = s.startsWith("/")
            if (closing) s = s.substring(1).trim()
            if (s.endsWith("/")) s = s.dropLast(1)
            val nameEnd = s.indexOfFirst { it.isWhitespace() }.let { if (it < 0) s.length else it }
            val name = s.substring(0, nameEnd).lowercase()
            if (name.isEmpty()) return null
            val attrs = mutableMapOf<String, String>()
            ATTR.findAll(s.substring(nameEnd)).forEach { m ->
                val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                attrs[m.groupValues[1].lowercase()] = decode(value)
            }
            return Tag(name, closing, attrs)
        }
    }

    private val ATTR = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")

    /** Gli spazi dell'HTML: ogni sequenza di spazi e a capo vale un solo spazio. */
    private fun String.collapse(): String = replace(Regex("[ \\t\\r\\n]+"), " ")

    private fun decode(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e == "amp" -> "&"
                e == "lt" -> "<"
                e == "gt" -> ">"
                e == "quot" -> "\""
                e == "apos" -> "'"
                e == "nbsp" -> " "
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> m.value
            }
        }
    }

    private val ENTITY = Regex("&(#?[a-zA-Z0-9]+);")
}
