package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportsTest {
    // Esportazione di Vivaldi e Chrome: name,url,username,password,note (le note possono andare a capo).
    private val vivaldi = "﻿name,url,username,password,note\n" +
        "comune.monza.it,https://www.comune.monza.it/login,anna,\"segreta,con virgola\",\n" +
        "posta,https://mail.example.org/,anna@example.org,p1,\"nota\nsu due righe\"\n" +
        "comune.monza.it,https://comune.monza.it/altro,Anna,p2,\n" +
        "vuota,https://vuota.example/,x,,\n"

    // Firefox: "url","username","password","httpRealm",...
    private val firefox = "\"url\",\"username\",\"password\",\"httpRealm\",\"formActionOrigin\",\"guid\"\r\n" +
        "\"https://a.example\",\"u\",\"p\",,\"https://a.example\",\"{1}\"\r\n"

    @Test fun riconosceIFile() {
        assertTrue(Imports.isPasswordCsv(vivaldi))
        assertTrue(Imports.isPasswordCsv(firefox))
        assertFalse(Imports.isPasswordCsv("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(Imports.isBookmarksHtml("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>"))
    }

    @Test fun contaPasswordEDoppioni() {
        val scan = Imports.scanPasswords(vivaldi)
        assertEquals(3, scan.passwords)
        // Stesso sito (senza www) e stesso utente (maiuscole a parte): un doppione.
        assertEquals(1, scan.duplicates)
        assertEquals(1, Imports.scanPasswords(firefox).passwords)
    }

    @Test fun csvConVirgoletteEACapo() {
        val rows = Imports.csvLines(vivaldi.trimStart('﻿'))
        assertEquals(5, rows.size)
        assertEquals("segreta,con virgola", rows[1][3])
        assertEquals("nota\nsu due righe", rows[2][4])
    }

    @Test fun esportaERileggeISegnalibri() {
        val items = listOf(
            Bookmark("Comune & co", "https://www.comune.monza.it/?a=1&b=2", "Lavoro"),
            Bookmark("Eurise", "https://eurise.eu/"),
        )
        val back = BookmarksHtml.parse(BookmarksExport.html(items))
        assertEquals(items.map { it.url to it.title }.toSet(), back.map { it.url to it.title }.toSet())
        assertEquals("Lavoro", back.first { it.title == "Comune & co" }.folder)
        assertEquals(2, Imports.scanBookmarks(BookmarksExport.html(items)).bookmarks)
    }
}
