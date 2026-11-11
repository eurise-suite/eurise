package eu.eurise.browser

/**
 * Ciò che c'è in un file esportato da un altro browser (tavola BrwImportaPwd), letto sul telefono prima di importare.
 * Le password non restano qui: il file passa a Eurise Password, che le cifra. Senza Android: si prova con i test.
 */
data class ImportScan(
    /** Righe con una password (CSV di Vivaldi, Chrome, Brave, Edge, Firefox). */
    val passwords: Int = 0,
    /** Password con lo stesso sito e lo stesso nome utente comparse più volte: Eurise Password le unisce. */
    val duplicates: Int = 0,
    val bookmarks: Int = 0,
    val folders: Int = 0,
) {
    val isEmpty get() = passwords == 0 && bookmarks == 0
}

object Imports {
    /** Il file è un CSV di password? Lo dice la prima riga: una colonna «password». */
    fun isPasswordCsv(text: String): Boolean {
        val header = text.trimStart('﻿').lineSequence().firstOrNull()?.lowercase() ?: return false
        return csvRow(header).any { it.trim() == "password" }
    }

    fun isBookmarksHtml(text: String): Boolean {
        val head = text.take(2000).uppercase()
        return head.contains("NETSCAPE-BOOKMARK-FILE") || (head.contains("<DL") && head.contains("<A "))
    }

    /** Conta password e doppioni in un CSV esportato (colonne url/origin, username/login, password). */
    fun scanPasswords(text: String): ImportScan {
        val lines = csvLines(text.trimStart('﻿'))
        if (lines.isEmpty()) return ImportScan()
        val header = lines.first().map { it.trim().lowercase() }
        val pwd = header.indexOf("password")
        if (pwd < 0) return ImportScan()
        val url = header.indexOfFirst { it == "url" || it == "origin" || it == "login_uri" || it == "website" }
        val user = header.indexOfFirst { it == "username" || it == "login" || it == "login_username" || it == "user" }
        val seen = HashSet<String>()
        var count = 0
        var dup = 0
        for (row in lines.drop(1)) {
            val p = row.getOrNull(pwd).orEmpty()
            if (p.isEmpty()) continue
            count++
            val site = row.getOrNull(url)?.let { Links.host(it.trim()) }.orEmpty()
            val key = site.lowercase() + "\u0000" + row.getOrNull(user).orEmpty().trim().lowercase()
            if (!seen.add(key)) dup++
        }
        return ImportScan(passwords = count, duplicates = dup)
    }

    fun scanBookmarks(html: String): ImportScan {
        val list = BookmarksHtml.parse(html)
        return ImportScan(bookmarks = list.size, folders = list.map { it.folder }.filter { it.isNotEmpty() }.distinct().size)
    }

    /** Le righe di un CSV con le virgolette (campi con virgole e a capo), come lo scrivono i browser. */
    internal fun csvLines(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotEmpty() }) rows += row
        return rows
    }

    private fun csvRow(line: String) = csvLines(line).firstOrNull().orEmpty()

    /** Pacchetti dei browser che sappiamo riconoscere sul telefono, con il percorso del loro «esporta password». */
    data class Source(val label: String, val packages: List<String>, val steps: Int)

    val SOURCES = listOf(
        Source("Vivaldi", listOf("com.vivaldi.browser", "com.vivaldi.browser.snapshot"), R_STEPS_VIVALDI),
        Source("Chrome", listOf("com.android.chrome"), R_STEPS_CHROME),
        Source("Brave", listOf("com.brave.browser"), R_STEPS_CHROME),
        Source("Edge", listOf("com.microsoft.emmx"), R_STEPS_CHROME),
        Source("Firefox", listOf("org.mozilla.firefox", "org.mozilla.fenix", "org.mozilla.firefox_beta"), R_STEPS_FIREFOX),
    )
    const val R_STEPS_VIVALDI = 0
    const val R_STEPS_CHROME = 1
    const val R_STEPS_FIREFOX = 2
}

/** Il file dei segnalibri nel formato che tutti i browser sanno importare («Netscape bookmark»), con le cartelle. */
object BookmarksExport {
    fun html(items: List<Bookmark>): String = buildString {
        append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        items.groupBy { it.folder }.forEach { (folder, list) ->
            val indent = if (folder.isEmpty()) "    " else "        "
            if (folder.isNotEmpty()) append("    <DT><H3>").append(esc(folder)).append("</H3>\n    <DL><p>\n")
            list.forEach { b -> append(indent).append("<DT><A HREF=\"").append(esc(b.url)).append("\">").append(esc(b.title)).append("</A>\n") }
            if (folder.isNotEmpty()) append("    </DL><p>\n")
        }
        append("</DL><p>\n")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
