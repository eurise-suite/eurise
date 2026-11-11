package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarksHtmlTest {
    private val chrome = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1" PERSONAL_TOOLBAR_FOLDER="true">Barra dei preferiti</H3>
            <DL><p>
                <DT><A HREF="https://www.comune.monza.it/" ADD_DATE="2">Comune di Monza</A>
                <DT><H3>Lavoro</H3>
                <DL><p>
                    <DT><A HREF="https://example.org/a?x=1&amp;y=2">Fatture &amp; preventivi</A>
                </DL><p>
            </DL><p>
            <DT><A HREF="https://eurise.eu/">Eurise</A>
            <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
            <DT><A HREF="https://eurise.eu/">Eurise doppio</A>
        </DL><p>
    """.trimIndent()

    @Test
    fun readsFoldersAndLinks() {
        val list = BookmarksHtml.parse(chrome)
        assertEquals(3, list.size)
        assertEquals(Bookmark("Comune di Monza", "https://www.comune.monza.it/", "Barra dei preferiti"), list[0])
        assertEquals(Bookmark("Fatture & preventivi", "https://example.org/a?x=1&y=2", "Barra dei preferiti › Lavoro"), list[1])
        assertEquals(Bookmark("Eurise", "https://eurise.eu/", ""), list[2])
    }
}
