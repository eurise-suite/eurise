package eu.eurise.core.sync

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Un browser installato e i suoi profili trovati sul computer. */
data class BrowserProfile(val browser: String, val name: String, val dir: File, val family: BrowserFamily)

enum class BrowserFamily { Chromium, Firefox }

/** Un segnalibro importato, con la cartella da cui viene («Barra dei preferiti › Lavoro»). */
data class ImportedBookmark(val title: String, val url: String, val folder: String, val added: Long = 0)

/**
 * Importazione dai browser del computer (docs/PIATTAFORME.md, «Importazione»), solo se l'utente la chiede e solo in
 * lettura: Chrome, Chromium, Brave, Edge, Vivaldi, Opera e Firefox, anche nelle versioni snap e Flatpak.
 * Qui: profili, segnalibri (JSON di Chromium, copie `jsonlz4` di Firefox) e la decifratura delle password di Chromium
 * con la chiave del portachiavi. La lettura dei database SQLite (password, cronologia) arriva con la schermata.
 */
object BrowserImport {

    private val chromium = listOf(
        "Google Chrome" to listOf(".config/google-chrome", ".var/app/com.google.Chrome/config/google-chrome"),
        "Chromium" to listOf(".config/chromium", "snap/chromium/common/chromium", ".var/app/org.chromium.Chromium/config/chromium"),
        "Brave" to listOf(".config/BraveSoftware/Brave-Browser", ".var/app/com.brave.Browser/config/BraveSoftware/Brave-Browser"),
        "Microsoft Edge" to listOf(".config/microsoft-edge", ".var/app/com.microsoft.Edge/config/microsoft-edge"),
        "Vivaldi" to listOf(".config/vivaldi", ".var/app/com.vivaldi.Vivaldi/config/vivaldi"),
        "Opera" to listOf(".config/opera"),
    )
    private val firefox = listOf(".mozilla/firefox", "snap/firefox/common/.mozilla/firefox", ".var/app/org.mozilla.firefox/.mozilla/firefox")

    /** Tutti i profili trovati nella cartella [home]. I servizi chiusi non si nascondono: decide l'utente. */
    fun profiles(home: File = File(System.getProperty("user.home"))): List<BrowserProfile> {
        val out = ArrayList<BrowserProfile>()
        for ((name, places) in chromium) for (p in places) {
            val root = File(home, p)
            if (!root.isDirectory) continue
            root.listFiles().orEmpty().filter { it.isDirectory && (it.name == "Default" || it.name.startsWith("Profile ")) && File(it, "Bookmarks").exists() }
                .sortedBy { it.name }
                .forEach { out += BrowserProfile(name, it.name, it, BrowserFamily.Chromium) }
            // Opera tiene il profilo direttamente nella cartella principale.
            if (File(root, "Bookmarks").exists()) out += BrowserProfile(name, "Predefinito", root, BrowserFamily.Chromium)
        }
        for (p in firefox) {
            val root = File(home, p)
            root.listFiles().orEmpty().filter { it.isDirectory && (File(it, "places.sqlite").exists() || File(it, "bookmarkbackups").isDirectory) }
                .sortedBy { it.name }
                .forEach { out += BrowserProfile("Firefox", it.name.substringAfter('.'), it, BrowserFamily.Firefox) }
        }
        return out
    }

    fun bookmarks(p: BrowserProfile): List<ImportedBookmark> = when (p.family) {
        BrowserFamily.Chromium -> File(p.dir, "Bookmarks").takeIf { it.exists() }?.let { chromiumBookmarks(it.readText()) }.orEmpty()
        BrowserFamily.Firefox -> File(p.dir, "bookmarkbackups").listFiles().orEmpty()
            .filter { it.name.endsWith(".jsonlz4") }.maxByOrNull { it.lastModified() }
            ?.let { firefoxBookmarks(String(MozLz4.decode(it.readBytes()), Charsets.UTF_8)) }.orEmpty()
    }

    /** Il file `Bookmarks` di Chromium: `roots` con barra, altri e mobile; le date sono microsecondi dal 1601. */
    fun chromiumBookmarks(json: String): List<ImportedBookmark> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        val roots = root["roots"] as? Map<*, *> ?: return emptyList()
        val out = ArrayList<ImportedBookmark>()
        fun walk(node: Map<*, *>, path: String) {
            when (node["type"]) {
                "url" -> {
                    val url = node["url"] as? String ?: return
                    val added = (node["date_added"] as? String)?.toLongOrNull()?.let { it / 1000 - 11_644_473_600_000L } ?: 0L
                    out += ImportedBookmark((node["name"] as? String).orEmpty().ifBlank { url }, url, path, maxOf(0L, added))
                }
                "folder" -> {
                    val name = (node["name"] as? String).orEmpty()
                    val sub = if (path.isEmpty()) name else "$path › $name"
                    (node["children"] as? List<*>)?.forEach { (it as? Map<*, *>)?.let { c -> walk(c, sub) } }
                }
            }
        }
        for (key in listOf("bookmark_bar", "other", "synced")) (roots[key] as? Map<*, *>)?.let { walk(it, "") }
        return out.filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
    }

    /** La copia dei segnalibri di Firefox (JSON di `bookmarkbackups`): cartelle con `children`, link con `uri`. */
    fun firefoxBookmarks(json: String): List<ImportedBookmark> {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return emptyList()
        val out = ArrayList<ImportedBookmark>()
        val rootNames = mapOf("toolbar" to "Barra dei segnalibri", "bookmarksMenu" to "Menu dei segnalibri", "unfiledBookmarks" to "Altri segnalibri", "mobile" to "Dispositivi mobili")
        fun walk(node: Map<*, *>, path: String) {
            val uri = node["uri"] as? String
            if (uri != null) {
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    val added = (node["dateAdded"] as? Number)?.toLong()?.div(1000) ?: 0L
                    out += ImportedBookmark((node["title"] as? String).orEmpty().ifBlank { uri }, uri, path, added)
                }
                return
            }
            val title = rootNames[node["root"]?.toString()?.removeSuffix("Folder")] ?: (node["title"] as? String).orEmpty()
            val sub = when {
                node["root"] == "placesRoot" -> ""
                path.isEmpty() -> title
                else -> "$path › $title"
            }
            (node["children"] as? List<*>)?.forEach { (it as? Map<*, *>)?.let { c -> walk(c, sub) } }
        }
        walk(root, "")
        return out
    }

    /** Segnalibri nel formato HTML di Netscape, quello che ogni browser (e il Browser Eurise) sa importare. */
    fun netscapeHtml(items: List<ImportedBookmark>): String = buildString {
        append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
        for ((folder, list) in items.groupBy { it.folder }) {
            val indent = if (folder.isEmpty()) "    " else "        "
            if (folder.isNotEmpty()) append("    <DT><H3>").append(NoteDoc.escape(folder)).append("</H3>\n    <DL><p>\n")
            for (b in list) {
                append(indent).append("<DT><A HREF=\"").append(NoteDoc.escape(b.url)).append("\" ADD_DATE=\"").append(b.added / 1000).append("\">")
                append(NoteDoc.escape(b.title)).append("</A>\n")
            }
            if (folder.isNotEmpty()) append("    </DL><p>\n")
        }
        append("</DL><p>\n")
    }

    /**
     * Decifra una password di Chromium su Linux: `v10` con la chiave fissa «peanuts», `v11` con quella del portachiavi
     * («Chrome Safe Storage», «Brave Safe Storage»…), passate da [keyringSecret]. AES-128-CBC, chiave da PBKDF2-SHA1 con
     * sale «saltysalt» e un'iterazione, IV di 16 spazi. Le versioni recenti mettono 32 byte di controllo davanti al testo.
     */
    fun decryptChromium(blob: ByteArray, keyringSecret: String?, hostKeyDigest: Boolean = false): String? {
        if (blob.size < 3) return null
        val prefix = String(blob, 0, 3, Charsets.US_ASCII)
        val secret = when (prefix) {
            "v10" -> "peanuts"
            "v11" -> keyringSecret ?: return null
            else -> return String(blob, Charsets.UTF_8)
        }
        val spec = PBEKeySpec(secret.toCharArray(), "saltysalt".toByteArray(), 1, 128)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(ByteArray(16) { ' '.code.toByte() }))
        val plain = runCatching { cipher.doFinal(blob, 3, blob.size - 3) }.getOrNull() ?: return null
        val text = if (hostKeyDigest && plain.size >= 32) plain.copyOfRange(32, plain.size) else plain
        return String(text, Charsets.UTF_8)
    }
}

/** I file `mozLz40` di Firefox: intestazione, lunghezza, poi un blocco LZ4 senza cornice. */
object MozLz4 {
    private val MAGIC = "mozLz40\u0000".toByteArray(Charsets.ISO_8859_1)

    fun decode(data: ByteArray): ByteArray {
        require(data.size >= 12 && data.copyOfRange(0, 8).contentEquals(MAGIC)) { "non è un file mozLz4" }
        val size = ByteBuffer.wrap(data, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return lz4Block(data, 12, size)
    }

    /** Decompressione di un blocco LZ4 (formato a blocchi, senza dizionario). */
    fun lz4Block(src: ByteArray, start: Int, outSize: Int): ByteArray {
        val out = ByteArray(outSize)
        var i = start
        var o = 0
        while (i < src.size) {
            val token = src[i++].toInt() and 0xFF
            var lit = token ushr 4
            if (lit == 15) { var b: Int; do { b = src[i++].toInt() and 0xFF; lit += b } while (b == 255) }
            System.arraycopy(src, i, out, o, lit)
            i += lit; o += lit
            if (i >= src.size) break
            val offset = (src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8)
            i += 2
            var len = token and 0x0F
            if (len == 15) { var b: Int; do { b = src[i++].toInt() and 0xFF; len += b } while (b == 255) }
            len += 4
            var from = o - offset
            repeat(len) { out[o++] = out[from++] }
        }
        return if (o == outSize) out else out.copyOf(o)
    }
}

/** Un lettore JSON minimo (oggetti, liste, testi, numeri, vero/falso/null) per i file dei browser, senza librerie. */
object MiniJson {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); v }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("JSON non valido a $i")
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); i++ // ':'
                m[k] = value(); ws()
                if (s[i++] == '}') return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i++] == ']') return l
            }
        }
        fun str(): String {
            val sb = StringBuilder()
            i++ // '"'
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Number {
            val b = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(b, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
    }
}
