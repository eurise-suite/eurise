package eu.eurise.feature.notes.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMergeTest {

    private var next = 0L
    private val ids = { ++next }

    private fun p(text: String) = TextBlock(ids(), TextKind.Paragraph, RichText(text))
    private fun c(text: String, checked: Boolean = false) = TextBlock(ids(), TextKind.Check, RichText(text), checked)
    private fun texts(blocks: List<Block>) = blocks.map { (it as TextBlock).text.text }

    @Test
    fun changesInDifferentPlacesMergeByThemselves() {
        val base = listOf(p("a"), p("b"), p("c"))
        val local = listOf(p("a"), p("b telefono"), p("c"))
        val remote = listOf(p("a"), p("b"), p("c"), p("d cloud"))
        val m = NoteMerge.merge("T", base, "T", local, "T", remote)
        assertTrue(m.clean)
        assertEquals(listOf("a", "b telefono", "c", "d cloud"), texts(m.blocks()))
    }

    @Test
    fun sameItemChangedTwiceIsAConflict() {
        val base = listOf(c("Pane"), c("Pomodori"), c("Caffè"))
        val local = listOf(c("Pane"), c("Pomodori san Marzano"), c("Caffè"))
        val remote = listOf(c("Pane"), c("Pomodori pelati"), c("Caffè"))
        val m = NoteMerge.merge("Spesa", base, "Spesa", local, "Spesa", remote)
        assertEquals(1, m.conflicts.size)
        assertEquals(listOf("Pane", "Pomodori pelati", "Caffè"), texts(m.blocks(listOf(MergeChoice.Remote))))
        assertEquals(listOf("Pane", "Pomodori san Marzano", "Pomodori pelati", "Caffè"), texts(m.blocks(listOf(MergeChoice.Both))))
    }

    @Test
    fun deletionOnOneSideWins() {
        val base = listOf(p("a"), p("b"), p("c"))
        val local = listOf(p("a"), p("c"))
        val remote = listOf(p("a"), p("b"), p("c"))
        assertEquals(listOf("a", "c"), texts(NoteMerge.merge("", base, "", local, "", remote).blocks()))
    }

    @Test
    fun checkingAnItemIsAChange() {
        val base = listOf(c("Pane"), c("Latte"))
        val local = listOf(c("Pane", checked = true), c("Latte"))
        val remote = listOf(c("Pane"), c("Latte"), c("Uova"))
        val m = NoteMerge.merge("", base, "", local, "", remote)
        assertTrue(m.clean)
        val out = m.blocks().map { it as TextBlock }
        assertEquals(listOf(true, false, false), out.map { it.checked })
        assertEquals(3, out.size)
    }

    @Test
    fun titles() {
        assertEquals("Nuovo", NoteMerge.merge("A", emptyList(), "A", emptyList(), "Nuovo", emptyList()).title)
        val m = NoteMerge.merge("A", emptyList(), "B", emptyList(), "C", emptyList())
        assertEquals("B" to "C", m.titleConflict)
        assertEquals("C", m.title(MergeChoice.Remote))
    }

    @Test
    fun fileKeepsMetadataAndDropsTaskLinks() {
        val file = NoteFile(
            uid = "abc-123", title = "Spesa <sabato>",
            blocks = listOf(TextBlock(1, TextKind.Check, RichText("Pane"), taskId = 9)),
            notebook = "Casa", notebookTint = "Ottone", pinned = true,
            created = 1_700_000_000_000, updated = 1_700_000_100_000,
        )
        val text = file.write()
        assertTrue(!text.contains("data-task"))
        val back = NoteFile.read(text, "altro", ids)
        assertEquals("abc-123", back.uid)
        assertEquals("Spesa <sabato>", back.title)
        assertEquals("Casa", back.notebook)
        assertEquals("Ottone", back.notebookTint)
        assertTrue(back.pinned)
        assertEquals(1_700_000_100_000, back.updated)
        assertEquals(null, back.trashed)
        assertEquals("Pane", (back.blocks.single() as TextBlock).text.text)
        // Scrivere di nuovo ciò che si è letto dà lo stesso file: niente invii inutili.
        assertEquals(text, back.copy(blocks = back.blocks).write())
    }
}
