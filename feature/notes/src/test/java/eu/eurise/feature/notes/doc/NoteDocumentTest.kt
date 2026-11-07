package eu.eurise.feature.notes.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentTest {

    private var next = 0L
    private val ids = { ++next }

    private fun sample(): List<Block> = listOf(
        TextBlock(1, TextKind.Heading, RichText("Nuova cucina")),
        TextBlock(
            2, TextKind.Paragraph,
            RichText(
                "Confrontare i preventivi entro venerdì, verde salvia",
                normalize(52, listOf(
                    Span(0, 11, Mark.Bold),
                    Span(32, 39, Mark.Highlight("Ottone")),
                    Span(41, 52, Mark.Color("Salvia")),
                    Span(41, 46, Mark.Italic),
                )),
            ),
        ),
        TextBlock(3, TextKind.Check, RichText("Misure del muro"), checked = true),
        TextBlock(4, TextKind.Check, RichText("Fissare il sopralluogo"), taskId = 42),
        TextBlock(5, TextKind.Bullet, RichText("a < b & \"c\"")),
        TextBlock(6, TextKind.Numbered, RichText("uno")),
        TextBlock(7, TextKind.Paragraph, RichText("")),
        TableBlock(8, listOf(listOf("Ditta", "Prezzo"), listOf("Rossi", "1.200 €"))),
        NoteLinkBlock(9, 17, "Misure della cucina"),
        DividerBlock(10),
        TextBlock(11, TextKind.Subheading, RichText("Fine")),
        DrawingBlock(
            12, height = 640, paper = Paper.Lines,
            strokes = listOf(
                Stroke(Tool.Pen, null, 6f, listOf(InkPoint(10f, 20f, 0.5f), InkPoint(40.25f, 60.5f, 0.8f), InkPoint(90f, 70f, 0.3f))),
                Stroke(Tool.Highlighter, "Ottone", 8f, listOf(InkPoint(100f, 100f), InkPoint(300f, 100f)), shape = true),
            ),
        ),
        TextBlock(13, TextKind.Paragraph, RichText("dopo il disegno")),
    )

    /** I blocchi senza id, per confrontare il contenuto. */
    private fun List<Block>.content() = map {
        when (it) {
            is TextBlock -> it.copy(id = 0)
            is TableBlock -> it.copy(id = 0)
            is NoteLinkBlock -> it.copy(id = 0)
            is DividerBlock -> it.copy(id = 0)
            is DrawingBlock -> it.copy(id = 0)
        }
    }

    @Test
    fun htmlRoundTripKeepsEverything() {
        val blocks = sample()
        val html = NoteHtml.write(blocks)
        assertEquals(blocks.content(), NoteHtml.read(html, ids).content())
    }

    @Test
    fun fullDocumentSkipsTitleAndStyle() {
        val blocks = sample()
        val doc = NoteHtml.document("Titolo <prova>", blocks)
        assertTrue(doc.contains("<title>Titolo &lt;prova&gt;</title>"))
        assertEquals(blocks.content(), NoteHtml.read(doc, ids).content())
    }

    @Test
    fun plainTextJoinsBlocks() {
        val plain = NoteHtml.plainText(sample())
        assertTrue(plain.startsWith("Nuova cucina\nConfrontare"))
        assertTrue(plain.contains("Rossi · 1.200 €"))
    }

    @Test
    fun typingAfterBoldContinuesBold() {
        val t = RichText("ciao", listOf(Span(0, 4, Mark.Bold)))
        val r = t.edit("ciao!")
        assertEquals(listOf(Span(0, 5, Mark.Bold)), r.spans)
    }

    @Test
    fun typingBeforeBoldIsNotBold() {
        val t = RichText("x ciao", listOf(Span(2, 6, Mark.Bold)))
        val r = t.edit("x ciao".let { "x " + "Z" + "ciao" }, typing = emptySet())
        assertEquals(listOf(Span(3, 7, Mark.Bold)), r.spans)
    }

    @Test
    fun deletingShiftsSpans() {
        val t = RichText("abc def", listOf(Span(4, 7, Mark.Italic)))
        val r = t.edit("ac def")
        assertEquals(listOf(Span(3, 6, Mark.Italic)), r.spans)
    }

    @Test
    fun pendingStyleAppliesToInsertedText() {
        val t = RichText("ab")
        val r = t.edit("abc", typing = setOf(Mark.Underline))
        assertEquals(listOf(Span(2, 3, Mark.Underline)), r.spans)
    }

    @Test
    fun toggleAddsThenRemoves() {
        val t = RichText("ciao mondo")
        val on = t.toggleMark(0, 4, Mark.Bold)
        assertEquals(listOf(Span(0, 4, Mark.Bold)), on.spans)
        val off = on.toggleMark(1, 3, Mark.Bold)
        assertEquals(listOf(Span(0, 1, Mark.Bold), Span(3, 4, Mark.Bold)), off.spans)
    }

    @Test
    fun colorReplacesColor() {
        val t = RichText("ciao").toggleMark(0, 4, Mark.Color("Salvia")).toggleMark(0, 2, Mark.Color("Borgogna"))
        assertEquals(setOf(Mark.Color("Borgogna")), t.marksIn(0, 2))
        assertEquals(setOf(Mark.Color("Salvia")), t.marksIn(2, 4))
    }

    @Test
    fun splitAndJoinKeepStyles() {
        val t = RichText("ciao mondo", listOf(Span(2, 8, Mark.Bold)))
        val a = t.substringTo(5)
        val b = t.substringFrom(5)
        assertEquals(listOf(Span(2, 5, Mark.Bold)), a.spans)
        assertEquals(listOf(Span(0, 3, Mark.Bold)), b.spans)
        assertEquals(t, a + b)
    }

    @Test
    fun readsForeignHtml() {
        val html = "<h1>T</h1><p>Uno <strong>due</strong>&nbsp;tre</p><ul><li>x</li></ul><p>a<br>b</p>"
        val blocks = NoteHtml.read(html, ids).content()
        assertEquals(
            listOf(
                TextBlock(0, TextKind.Paragraph, RichText("Uno due tre", listOf(Span(4, 7, Mark.Bold)))),
                TextBlock(0, TextKind.Bullet, RichText("x")),
                TextBlock(0, TextKind.Paragraph, RichText("a\nb")),
            ),
            blocks,
        )
    }

    @Test
    fun drawingIsSvgReadableByBrowsers() {
        val html = NoteHtml.write(sample())
        assertTrue(html.contains("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 640\""))
        assertTrue(html.contains("fill-opacity=\"0.35\""))
    }

    @Test
    fun recognizesStraightLine() {
        val pts = (0..20).map { InkPoint(100f + it * 20f, 300f + (if (it % 2 == 0) 3f else -3f)) }
        val shape = Stroke(Tool.Pen, null, 6f, pts).recognizeShape()
        assertEquals(2, shape?.points?.size)
        assertTrue(shape!!.shape)
    }

    @Test
    fun recognizesCircleAndRectangle() {
        val circle = (0..60).map { k ->
            val a = 2 * Math.PI * k / 60
            InkPoint(500f + (200 * Math.cos(a)).toFloat() + (k % 3), 500f + (200 * Math.sin(a)).toFloat())
        }
        val c = Stroke(Tool.Pen, null, 6f, circle).recognizeShape()
        assertTrue(c != null && c.points.size == 65)

        val rect = buildList {
            for (i in 0..20) add(InkPoint(100f + i * 20f, 100f))
            for (i in 0..15) add(InkPoint(500f, 100f + i * 20f))
            for (i in 0..20) add(InkPoint(500f - i * 20f, 400f))
            for (i in 0..15) add(InkPoint(100f + (i % 2), 400f - i * 20f))
        }
        val r = Stroke(Tool.Pen, null, 6f, rect).recognizeShape()
        assertTrue(r != null && r.points.size != 65 && r.points.size > 5)

        val scribble = (0..40).map { InkPoint(100f + it * 10f, 100f + (it * it % 97) * 4f) }
        assertEquals(null, Stroke(Tool.Pen, null, 6f, scribble).recognizeShape())
    }

    @Test
    fun outlineIsClosedPolygon() {
        val o = Stroke(Tool.Pen, null, 6f, listOf(InkPoint(0f, 0f), InkPoint(100f, 0f))).outline()
        assertTrue(o.size >= 8 && o.size % 2 == 0)
        assertTrue(Stroke(Tool.Pen, null, 6f, listOf(InkPoint(5f, 5f))).outline().size == 32)
    }
}
