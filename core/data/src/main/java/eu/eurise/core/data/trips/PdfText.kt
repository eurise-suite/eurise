package eu.eurise.core.data.trips

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Il testo di un PDF "vero" (biglietti, ricevute, estratti conto), letto sul telefono senza librerie. Basta per capire
 * un biglietto: legge gli oggetti (anche dentro gli object stream), decomprime FlateDecode, segue i caratteri attraverso
 * le mappe ToUnicode dei font e ricostruisce le righe. Non legge le scansioni: quelle sono immagini (serve l'OCR).
 */
object PdfText {

    fun extract(bytes: ByteArray, maxPages: Int = 20): String = runCatching { Reader(bytes).run(maxPages) }.getOrDefault("")

    private class Obj(val dict: String, val stream: ByteArray?)

    private class Reader(private val bytes: ByteArray) {
        private val text = String(bytes, Charsets.ISO_8859_1)
        private val objects = HashMap<Int, Obj>()
        private val cmaps = HashMap<Int, CMap?>()

        fun run(maxPages: Int): String {
            readObjects()
            val pages = objects.entries.filter { PAGE.containsMatchIn(it.value.dict) }.sortedBy { it.key }.take(maxPages)
            val fallbackFonts = HashMap<String, Int>()
            pages.forEach { (_, page) -> fallbackFonts.putAll(fonts(page.dict)) }
            return pages.joinToString("\n\n") { (_, page) ->
                val fonts = fonts(page.dict).ifEmpty { fallbackFonts }
                val content = contents(page.dict)
                Content(content, fonts).run().trim()
            }.trim()
        }

        private fun readObjects() {
            OBJ.findAll(text).forEach { m ->
                val num = m.groupValues[1].toInt()
                val start = m.range.last + 1
                val end = text.indexOf("endobj", start).takeIf { it > 0 } ?: text.length
                val body = text.substring(start, end)
                val s = STREAM.find(body)
                if (s == null) { objects[num] = Obj(body, null); return@forEach }
                val dict = body.substring(0, s.range.first)
                val dataStart = start + s.range.last + 1
                val length = LENGTH.find(dict)?.groupValues?.get(1)?.toIntOrNull()
                val dataEnd = if (length != null && dataStart + length <= bytes.size && text.startsWith("endstream", skipWs(dataStart + length))) {
                    dataStart + length
                } else {
                    text.indexOf("endstream", dataStart).takeIf { it > 0 }?.let { trimEol(it) } ?: end
                }
                val raw = bytes.copyOfRange(dataStart, dataEnd.coerceAtLeast(dataStart))
                objects[num] = Obj(dict, decode(dict, raw))
            }
            // Oggetti compressi negli object stream (PDF 1.5 e successivi).
            objects.values.filter { "/ObjStm" in it.dict && it.stream != null }.toList().forEach { o ->
                val n = INT_KEY("N").find(o.dict)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                val first = INT_KEY("First").find(o.dict)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                val data = String(o.stream!!, Charsets.ISO_8859_1)
                val header = data.substring(0, first.coerceAtMost(data.length)).trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
                for (i in 0 until n) {
                    val num = header.getOrNull(2 * i) ?: break
                    val off = first + (header.getOrNull(2 * i + 1) ?: break)
                    val next = header.getOrNull(2 * i + 3)?.let { first + it } ?: data.length
                    if (off in 0..data.length && next in off..data.length && num !in objects) objects[num] = Obj(data.substring(off, next), null)
                }
            }
        }

        private fun skipWs(i: Int): Int { var j = i; while (j < text.length && text[j].isWhitespace()) j++; return j }
        private fun trimEol(i: Int): Int { var j = i; if (j > 0 && text[j - 1] == '\n') j--; if (j > 0 && text[j - 1] == '\r') j--; return j }

        private fun decode(dict: String, raw: ByteArray): ByteArray? {
            if ("/Filter" !in dict) return raw
            if ("/FlateDecode" !in dict) return null // immagini e altri filtri: non servono al testo
            return inflate(raw)
        }

        private fun ref(value: String?): Int? = value?.let { REF.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        /** Il dizionario che segue /Key: in linea (<<…>>) oppure attraverso un riferimento "n 0 R". */
        private fun dictAt(dict: String, key: String): String? {
            val i = Regex("/$key\\b").find(dict)?.range?.last?.plus(1) ?: return null
            val rest = dict.substring(i).trimStart()
            return if (rest.startsWith("<<")) balanced(rest) else ref(rest.take(24))?.let { objects[it]?.dict }
        }

        private fun balanced(s: String): String {
            var depth = 0
            var i = 0
            while (i < s.length - 1) {
                if (s[i] == '<' && s[i + 1] == '<') { depth++; i += 2; continue }
                if (s[i] == '>' && s[i + 1] == '>') { depth--; i += 2; if (depth == 0) return s.substring(0, i); continue }
                i++
            }
            return s
        }

        /** Nome del font → numero dell'oggetto, dalle risorse della pagina (o dei suoi genitori). */
        private fun fonts(page: String): Map<String, Int> {
            var node: String? = page
            repeat(8) {
                val resources = node?.let { dictAt(it, "Resources") }
                val fontDict = resources?.let { dictAt(it, "Font") }
                if (fontDict != null) return FONT_ENTRY.findAll(fontDict).associate { it.groupValues[1] to it.groupValues[2].toInt() }
                node = node?.let { n -> ref(Regex("/Parent\\s+\\d+\\s+\\d+\\s+R").find(n)?.value)?.let { objects[it]?.dict } }
            }
            return emptyMap()
        }

        private fun contents(page: String): ByteArray {
            val i = Regex("/Contents\\b").find(page)?.range?.last?.plus(1) ?: return ByteArray(0)
            val rest = page.substring(i).trimStart()
            val refs = if (rest.startsWith("[")) REF.findAll(rest.substringBefore(']')).map { it.groupValues[1].toInt() }.toList()
            else listOfNotNull(ref(rest.take(24)))
            val out = ByteArrayOutputStream()
            refs.forEach { r ->
                val o = objects[r] ?: return@forEach
                // Un array di contenuti può stare in un oggetto a parte.
                if (o.stream == null && o.dict.trimStart().startsWith("[")) {
                    REF.findAll(o.dict).forEach { m -> objects[m.groupValues[1].toInt()]?.stream?.let { out.write(it); out.write('\n'.code) } }
                } else {
                    o.stream?.let { out.write(it); out.write('\n'.code) }
                }
            }
            return out.toByteArray()
        }

        fun cmapOf(fontObj: Int): CMap? = cmaps.getOrPut(fontObj) {
            val font = objects[fontObj]?.dict ?: return@getOrPut null
            val stream = ref(Regex("/ToUnicode\\s+\\d+\\s+\\d+\\s+R").find(font)?.value)?.let { objects[it]?.stream }
            val twoBytes = "/Type0" in font
            stream?.let { CMap.parse(String(it, Charsets.ISO_8859_1), twoBytes) } ?: if (twoBytes) CMap(emptyMap(), 2) else null
        }

        /** Esegue gli operatori di testo di una pagina e ne ricostruisce le righe. */
        private inner class Content(data: ByteArray, private val fonts: Map<String, Int>) {
            private val s = String(data, Charsets.ISO_8859_1)
            private var i = 0
            private val out = StringBuilder()
            private val operands = ArrayList<Any>()
            private var cmap: CMap? = null
            private var lastY: Double? = null

            fun run(): String {
                while (true) {
                    val token = next() ?: break
                    if (token is Op) operator(token.name) else operands += token
                }
                return out.toString().lines().joinToString("\n") { it.replace(Regex(" {2,}"), " ").trim() }
                    .replace(Regex("\n{3,}"), "\n\n")
            }

            private fun newline() { if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
            private fun space() { if (out.isNotEmpty() && out.last() != ' ' && out.last() != '\n') out.append(' ') }

            private fun operator(op: String) {
                when (op) {
                    "Tf" -> cmap = (operands.getOrNull(operands.size - 2) as? Name)?.let { fonts[it.v] }?.let { cmapOf(it) }
                    "Tj" -> (operands.lastOrNull() as? Bytes)?.let { out.append(decode(it.v)) }
                    "'", "\"" -> { newline(); (operands.lastOrNull() as? Bytes)?.let { out.append(decode(it.v)) } }
                    "TJ" -> (operands.lastOrNull() as? List<*>)?.forEach { e ->
                        when (e) {
                            is Bytes -> out.append(decode(e.v))
                            is Double -> if (e < -180) space()
                        }
                    }
                    "Td", "TD" -> {
                        val ty = operands.getOrNull(operands.size - 1) as? Double ?: 0.0
                        if (kotlin.math.abs(ty) > 0.5) newline() else space()
                    }
                    "Tm" -> {
                        val y = operands.getOrNull(operands.size - 1) as? Double
                        if (y != null && lastY != null && kotlin.math.abs(y - lastY!!) > 0.5) newline() else space()
                        lastY = y
                    }
                    "T*" -> newline()
                    "ET" -> space()
                    "BI" -> skipInlineImage()
                }
                operands.clear()
            }

            private fun decode(b: ByteArray): String {
                val c = cmap
                if (c == null) return buildString { b.forEach { append(WIN_ANSI.getOrNull(it.toInt() and 0xFF) ?: (it.toInt() and 0xFF).toChar()) } }
                return c.decode(b)
            }

            private fun skipInlineImage() {
                val end = s.indexOf("EI", i)
                i = if (end < 0) s.length else end + 2
            }

            private fun next(): Any? {
                while (i < s.length) {
                    val ch = s[i]
                    when {
                        ch.isWhitespace() -> i++
                        ch == '%' -> { while (i < s.length && s[i] != '\n' && s[i] != '\r') i++ }
                        else -> break
                    }
                }
                if (i >= s.length) return null
                val ch = s[i]
                return when {
                    ch == '(' -> Bytes(literal())
                    ch == '<' && s.startsWith("<<", i) -> { i += 2; next() }
                    ch == '>' && s.startsWith(">>", i) -> { i += 2; next() }
                    ch == '<' -> Bytes(hex())
                    ch == '[' -> { i++; array() }
                    ch == ']' -> { i++; End }
                    ch == '/' -> { i++; Name(word()) }
                    ch == '{' || ch == '}' -> { i++; next() }
                    ch.isDigit() || ch == '-' || ch == '+' || ch == '.' -> word().toDoubleOrNull() ?: 0.0
                    else -> Op(word().ifEmpty { i++; ch.toString() })
                }
            }

            private fun array(): List<Any> {
                val list = ArrayList<Any>()
                while (true) {
                    val t = next() ?: break
                    if (t === End) break
                    list += t
                }
                return list
            }

            private fun word(): String {
                val start = i
                while (i < s.length && !s[i].isWhitespace() && s[i] !in "()<>[]{}/%") i++
                return s.substring(start, i)
            }

            private fun literal(): ByteArray {
                val out = ByteArrayOutputStream()
                var depth = 0
                i++ // (
                while (i < s.length) {
                    val c = s[i]
                    when {
                        c == '\\' && i + 1 < s.length -> {
                            val n = s[i + 1]
                            i += 2
                            when (n) {
                                'n' -> out.write('\n'.code); 'r' -> out.write('\r'.code); 't' -> out.write('\t'.code)
                                'b' -> out.write(8); 'f' -> out.write(12)
                                '\r' -> { if (i < s.length && s[i] == '\n') i++ }
                                '\n' -> Unit
                                in '0'..'7' -> {
                                    var v = n - '0'
                                    var k = 0
                                    while (k < 2 && i < s.length && s[i] in '0'..'7') { v = v * 8 + (s[i] - '0'); i++; k++ }
                                    out.write(v and 0xFF)
                                }
                                else -> out.write(n.code and 0xFF)
                            }
                            continue
                        }
                        c == '(' -> depth++
                        c == ')' -> { if (depth == 0) { i++; break }; depth-- }
                    }
                    out.write(c.code and 0xFF)
                    i++
                }
                return out.toByteArray()
            }

            private fun hex(): ByteArray {
                val end = s.indexOf('>', i).takeIf { it > 0 } ?: s.length
                val digits = s.substring(i + 1, end).filter { it.isLetterOrDigit() }
                i = end + 1
                val padded = if (digits.length % 2 == 1) digits + "0" else digits
                return ByteArray(padded.length / 2) { k -> padded.substring(2 * k, 2 * k + 2).toInt(16).toByte() }
            }
        }
    }

    private data class Name(val v: String)
    private class Bytes(val v: ByteArray)
    private data class Op(val name: String)
    private object End

    /** Mappa ToUnicode: codice del carattere nel font → testo. */
    internal class CMap(private val map: Map<Int, String>, private val width: Int) {
        fun decode(b: ByteArray): String = buildString {
            var k = 0
            while (k < b.size) {
                val code = if (width == 2 && k + 1 < b.size) ((b[k].toInt() and 0xFF) shl 8) or (b[k + 1].toInt() and 0xFF) else b[k].toInt() and 0xFF
                k += if (width == 2 && k + 1 < b.size) 2 else 1
                append(map[code] ?: if (width == 1) (WIN_ANSI.getOrNull(code) ?: code.toChar()).toString() else "")
            }
        }

        companion object {
            private val HEX = Regex("<([0-9A-Fa-f]+)>")

            fun parse(src: String, twoBytes: Boolean): CMap {
                val map = HashMap<Int, String>()
                var width = if (twoBytes) 2 else 1
                Regex("begincodespacerange(.*?)endcodespacerange", RegexOption.DOT_MATCHES_ALL).find(src)?.let { m ->
                    HEX.find(m.groupValues[1])?.let { width = (it.groupValues[1].length / 2).coerceIn(1, 2) }
                }
                Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(src).forEach { block ->
                    val hexes = HEX.findAll(block.groupValues[1]).map { it.groupValues[1] }.toList()
                    for (p in 0 until hexes.size / 2) map[hexes[2 * p].toInt(16)] = utf16(hexes[2 * p + 1])
                }
                Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(src).forEach { block ->
                    block.groupValues[1].lines().forEach { line ->
                        val parts = HEX.findAll(line).map { it.groupValues[1] }.toList()
                        if (parts.size < 3) return@forEach
                        val lo = parts[0].toInt(16)
                        val hi = parts[1].toInt(16)
                        if (hi - lo > 5000) return@forEach
                        if ('[' in line) {
                            parts.drop(2).forEachIndexed { k, d -> map[lo + k] = utf16(d) }
                        } else {
                            val base = utf16(parts[2])
                            for (c in lo..hi) {
                                val last = base.last().code + (c - lo)
                                map[c] = base.dropLast(1) + last.toChar()
                            }
                        }
                    }
                }
                return CMap(map, width)
            }

            private fun utf16(hex: String): String {
                val padded = hex.padStart((hex.length + 3) / 4 * 4, '0')
                return String((0 until padded.length / 4).map { padded.substring(4 * it, 4 * it + 4).toInt(16).toChar() }.toCharArray())
            }
        }
    }

    private fun inflate(raw: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(raw)
        val out = ByteArrayOutputStream(raw.size * 3)
        val buf = ByteArray(8192)
        var guard = 0
        while (!inflater.finished() && guard++ < 100_000) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()

    private val OBJ = Regex("""(?<![\d])(\d+)\s+\d+\s+obj\b""")
    private val STREAM = Regex("""stream\r?\n""")
    private val LENGTH = Regex("""/Length\s+(\d+)\b(?!\s+\d+\s+R)""")
    private fun INT_KEY(key: String) = Regex("""/$key\s+(\d+)""")
    private val REF = Regex("""(\d+)\s+\d+\s+R""")
    private val PAGE = Regex("""/Type\s*/Page(?![s\w])""")
    private val FONT_ENTRY = Regex("""/([^\s/<>\[\]()]+)\s+(\d+)\s+\d+\s+R""")

    /** Le posizioni 128–159 di WinAnsi che non coincidono con Latin-1 (€, virgolette, trattini). */
    private val WIN_ANSI: Array<Char?> = arrayOfNulls<Char>(256).also { t ->
        "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000\u0000‘’“”•–—˜™š›œ\u0000žŸ".forEachIndexed { k, c -> if (c != '\u0000') t[128 + k] = c }
    }
}
