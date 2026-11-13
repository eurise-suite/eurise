package eu.eurise.office.rapida

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Un documento Office (DOCX, XLSX, ODT, ODS…) com'è nel suo zip: le voci restano nell'ordine e con il metodo di
 * compressione originali (l'ODF vuole `mimetype` prima e non compresso). Le modifiche sostituiscono solo le voci toccate:
 * tutto il resto del file torna uguale byte per byte nel contenuto.
 */
class Bundle private constructor(private val entries: List<Entry>) {
    class Entry(val name: String, val data: ByteArray, val stored: Boolean, val time: Long)

    val names: List<String> get() = entries.map { it.name }

    operator fun get(name: String): ByteArray? = entries.firstOrNull { it.name == name }?.data
    fun text(name: String): String? = get(name)?.toString(Charsets.UTF_8)

    /** Una copia con [name] sostituita (o aggiunta in fondo); null come valore la toglie. */
    fun with(changes: Map<String, ByteArray?>): Bundle {
        val out = entries.mapNotNull { e ->
            if (e.name in changes) changes[e.name]?.let { Entry(e.name, it, e.stored, e.time) } else e
        }.toMutableList()
        changes.forEach { (name, data) -> if (data != null && out.none { it.name == name }) out += Entry(name, data, false, System.currentTimeMillis()) }
        return Bundle(out)
    }

    fun withText(name: String, text: String) = with(mapOf(name to text.toByteArray(Charsets.UTF_8)))

    fun toBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for (e in entries) {
                val entry = ZipEntry(e.name).apply { if (e.time > 0) time = e.time }
                if (e.stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = e.data.size.toLong()
                    entry.compressedSize = e.data.size.toLong()
                    entry.crc = CRC32().apply { update(e.data) }.value
                }
                zip.putNextEntry(entry)
                zip.write(e.data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    companion object {
        fun read(bytes: ByteArray): Bundle {
            val list = mutableListOf<Entry>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    list += Entry(e.name, zip.readBytes(), e.method == ZipEntry.STORED, e.time)
                }
            }
            require(list.isNotEmpty()) { "non è un documento Office" }
            return Bundle(list)
        }
    }
}

/** XML con i namespace: lettura in DOM e scrittura di un elemento solo o di un documento intero. */
internal object Xml {
    fun parse(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    /** Un elemento da solo, senza dichiarazione: i namespace che usa vengono dichiarati su di lui. */
    fun fragment(node: Node): String {
        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(node), StreamResult(out))
        return out.toString()
    }

    fun document(doc: Document): String {
        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.STANDALONE, "yes")
        }.transform(DOMSource(doc), StreamResult(out))
        return out.toString()
    }

    fun children(e: Node): List<Element> {
        val out = ArrayList<Element>()
        var c = e.firstChild
        while (c != null) { if (c is Element) out += c; c = c.nextSibling }
        return out
    }

    fun child(e: Node, ns: String, name: String): Element? = children(e).firstOrNull { it.namespaceURI == ns && it.localName == name }
    fun all(e: Element, ns: String, name: String): List<Element> {
        val list = e.getElementsByTagNameNS(ns, name)
        return (0 until list.length).map { list.item(it) as Element }
    }

    /**
     * Dove sono, nel testo XML, gli elementi chiamati [qnames] (con il prefisso, per esempio `w:p`), nell'ordine del
     * documento: lo stesso ordine di `getElementsByTagNameNS`. Serve a sostituire un elemento senza riscrivere il resto.
     */
    fun ranges(xml: String, qnames: Set<String>): List<IntRange> {
        val starts = ArrayList<Int>()
        val result = ArrayList<IntRange?>()
        val stack = ArrayList<Pair<Int, Int>>() // indice nel risultato, inizio
        var i = xml.indexOf('<')
        while (i >= 0 && i < xml.length) {
            when {
                xml.startsWith("<!--", i) -> { i = xml.indexOf("-->", i).let { if (it < 0) xml.length else it + 3 }; i = xml.indexOf('<', i); continue }
                xml.startsWith("<![CDATA[", i) -> { i = xml.indexOf("]]>", i).let { if (it < 0) xml.length else it + 3 }; i = xml.indexOf('<', i); continue }
                xml.startsWith("<?", i) -> { i = xml.indexOf("?>", i).let { if (it < 0) xml.length else it + 2 }; i = xml.indexOf('<', i); continue }
            }
            val close = xml.startsWith("</", i)
            val nameStart = if (close) i + 2 else i + 1
            var nameEnd = nameStart
            while (nameEnd < xml.length && !xml[nameEnd].isWhitespace() && xml[nameEnd] != '>' && xml[nameEnd] != '/') nameEnd++
            val name = xml.substring(nameStart, nameEnd)
            val end = tagEnd(xml, nameEnd)
            if (name in qnames) {
                if (close) {
                    val (index, start) = stack.removeAt(stack.lastIndex)
                    result[index] = start..end
                } else {
                    val selfClosing = xml[end - 1] == '/'
                    starts += i
                    result += if (selfClosing) i..end else null
                    if (!selfClosing) stack += result.lastIndex to i
                }
            }
            i = xml.indexOf('<', end + 1)
        }
        return result.map { it ?: error("XML non chiuso") }
    }

    /** L'indice del '>' che chiude il tag, saltando quelli dentro i valori degli attributi. */
    private fun tagEnd(xml: String, from: Int): Int {
        var quote = 0.toChar()
        var i = from
        while (i < xml.length) {
            val c = xml[i]
            if (quote != 0.toChar()) { if (c == quote) quote = 0.toChar() }
            else if (c == '"' || c == '\'') quote = c
            else if (c == '>') return i
            i++
        }
        return xml.length - 1
    }

    fun escape(text: String): String = buildString {
        for (c in text) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

/**
 * Un pezzo di testo modificabile dentro un paragrafo: testo vero, una tabulazione, un a capo o (ODF) più spazi.
 * Le correzioni toccano solo i pezzi dove cade la modifica; gli altri, con la loro formattazione, restano com'erano.
 */
internal interface Piece {
    val text: String
    /** Toglie i caratteri [from, to) di questo pezzo. */
    fun delete(from: Int, to: Int)
    /** Inserisce [value] (può contenere \n e \t) alla posizione [at] di questo pezzo; false se il pezzo non accetta testo. */
    fun insert(at: Int, value: String): Boolean
}

internal object TextEdit {
    /**
     * Porta i [pieces] dal loro testo a [target]: trova la parte cambiata (prefisso e suffisso comuni), toglie i caratteri
     * vecchi pezzo per pezzo e mette i nuovi nel pezzo di testo dove comincia la modifica (o in quello prima).
     */
    fun apply(pieces: List<Piece>, target: String) {
        val old = pieces.joinToString("") { it.text }
        if (old == target) return
        var p = 0
        while (p < old.length && p < target.length && old[p] == target[p]) p++
        var s = 0
        while (s < old.length - p && s < target.length - p && old[old.length - 1 - s] == target[target.length - 1 - s]) s++
        val delFrom = p
        val delTo = old.length - s
        val inserted = target.substring(p, target.length - s)
        // Prima l'inserimento (le posizioni sono quelle vecchie), poi la cancellazione dietro.
        val starts = IntArray(pieces.size)
        val lengths = IntArray(pieces.size) { pieces[it].text.length }
        var acc = 0
        pieces.forEachIndexed { i, _ -> starts[i] = acc; acc += lengths[i] }
        if (delTo > delFrom) {
            for (i in pieces.indices.reversed()) {
                val a = starts[i]; val b = a + pieces[i].text.length
                val from = maxOf(delFrom, a); val to = minOf(delTo, b)
                if (to > from) pieces[i].delete(from - a, to - a)
            }
        }
        if (inserted.isNotEmpty()) {
            require(pieces.isNotEmpty()) { "paragrafo senza punto di inserimento" }
            // Il pezzo che contiene la posizione, preferendo quello a sinistra (ne prende la formattazione); a inizio
            // paragrafo il primo. Ogni pezzo sa inserire anche prima o dopo di sé (tabulazioni e a capo compresi).
            // Se si sostituisce del testo, il nuovo prende la formattazione di quello che sostituisce.
            val replacing = if (delTo > delFrom) pieces.indices.firstOrNull { starts[it] <= delFrom && delFrom < starts[it] + lengths[it] } else null
            val left = pieces.indices.lastOrNull { starts[it] < delFrom } ?: 0
            for (i in listOfNotNull(replacing, left).distinct()) {
                val at = (delFrom - starts[i]).coerceIn(0, pieces[i].text.length)
                if (pieces[i].insert(at, inserted)) return
            }
            error("nessun punto dove inserire il testo")
        }
    }
}
