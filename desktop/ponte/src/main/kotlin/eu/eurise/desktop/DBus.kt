package eu.eurise.desktop

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/** Un valore D-Bus di tipo variante: la firma del valore e il valore. */
data class Variant(val signature: String, val value: Any)

/** Una risposta (o un segnale) arrivata dal bus: la firma del corpo e i valori già letti. */
class DBusMessage(val type: Int, val serial: Long, val replyTo: Long?, val path: String?, val iface: String?,
                  val member: String?, val error: String?, val signature: String, val body: List<Any>)

/** Errore restituito da un servizio sul bus (per esempio `org.freedesktop.DBus.Error.ServiceUnknown`). */
class DBusError(val name: String, message: String) : IOException("$name: $message")

/**
 * Un client D-Bus minimo sul bus di sessione, per il solo portachiavi del sistema ([SecretService]): niente librerie né
 * programmi esterni (`secret-tool` su Ubuntu non c'è di base). Socket Unix dell'utente, autenticazione EXTERNAL (l'uid
 * del processo), messaggi little-endian; legge qualunque firma, scrive i tipi che servono (y b u i s o g v a ( {).
 * Nel Flatpak il bus è il proxy filtrato di Flatpak: si raggiunge solo ciò che `--talk-name` permette.
 */
class DBus private constructor(private val channel: SocketChannel) : Closeable {
    private val selector: Selector = Selector.open()
    private var serial = 0L
    private val pending = ArrayDeque<DBusMessage>()

    init {
        channel.configureBlocking(false)
        channel.register(selector, SelectionKey.OP_READ)
    }

    /** Chiama [member]; restituisce i valori della risposta o lancia [DBusError]. */
    fun call(dest: String, path: String, iface: String, member: String, signature: String = "", vararg args: Any,
             timeoutMs: Long = CALL_TIMEOUT_MS): List<Any> {
        val s = send(dest, path, iface, member, signature, args.toList())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val m = pending.firstOrNull { it.replyTo == s }?.also { pending.remove(it) } ?: read(deadline)
            if (m.replyTo != s) { if (m.type == SIGNAL) pending.addLast(m); continue }
            if (m.type == ERROR) throw DBusError(m.error ?: "?", m.body.firstOrNull() as? String ?: "")
            return m.body
        }
    }

    /** Aspetta il segnale [member] da [path] (dopo un `AddMatch`), al massimo [timeoutMs]. */
    fun awaitSignal(path: String, iface: String, member: String, timeoutMs: Long): List<Any> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val m = pending.firstOrNull { it.type == SIGNAL && it.path == path && it.iface == iface && it.member == member }
                ?.also { pending.remove(it) } ?: read(deadline)
            if (m.type == SIGNAL && m.path == path && m.iface == iface && m.member == member) return m.body
            if (m.type == SIGNAL) pending.addLast(m)
        }
    }

    private fun send(dest: String, path: String, iface: String, member: String, signature: String, args: List<Any>): Long {
        val id = ++serial
        val body = Marshal().apply { writeAll(signature, args) }.bytes()
        val fields = buildList {
            add(listOf(1, Variant("o", path)))
            add(listOf(2, Variant("s", iface)))
            add(listOf(3, Variant("s", member)))
            add(listOf(6, Variant("s", dest)))
            if (signature.isNotEmpty()) add(listOf(8, Variant("g", signature)))
        }
        val m = Marshal()
        m.write("y", 'l'.code); m.write("y", CALL); m.write("y", 0); m.write("y", 1)
        m.write("u", body.size.toLong()); m.write("u", id)
        m.write("a(yv)", fields)
        m.align(8)
        writeFully(m.bytes() + body)
        return id
    }

    private fun read(deadline: Long): DBusMessage {
        val head = readFully(16, deadline)
        val order = if (head[0] == 'B'.code.toByte()) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val hb = ByteBuffer.wrap(head).order(order)
        val bodyLen = hb.getInt(4).toLong() and 0xffffffffL
        val fieldsLen = hb.getInt(12).toLong() and 0xffffffffL
        if (bodyLen > MAX_MESSAGE || fieldsLen > MAX_MESSAGE) throw IOException("Messaggio D-Bus troppo grande")
        val headerEnd = ((16 + fieldsLen + 7) / 8) * 8
        val rest = readFully((headerEnd - 16 + bodyLen).toInt(), deadline)
        val all = head + rest
        val u = Unmarshal(all, order)
        u.pos = 12
        @Suppress("UNCHECKED_CAST")
        val fields = (u.read("a(yv)") as List<List<Any>>).associate { (it[0] as Int) to (it[1] as Variant).value }
        val signature = fields[8] as? String ?: ""
        val bu = Unmarshal(all.copyOfRange(headerEnd.toInt(), all.size), order)
        return DBusMessage(
            type = head[1].toInt(), serial = hb.getInt(8).toLong() and 0xffffffffL, replyTo = fields[5] as? Long,
            path = fields[1] as? String, iface = fields[2] as? String, member = fields[3] as? String,
            error = fields[4] as? String, signature = signature, body = bu.readAll(signature),
        )
    }

    private fun writeFully(bytes: ByteArray) {
        val b = ByteBuffer.wrap(bytes)
        while (b.hasRemaining()) if (channel.write(b) == 0) Thread.sleep(1)
    }

    private fun readFully(n: Int, deadline: Long): ByteArray {
        val b = ByteBuffer.allocate(n)
        while (b.hasRemaining()) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) throw IOException("Il bus D-Bus non risponde")
            selector.select(left); selector.selectedKeys().clear()
            if (channel.read(b) < 0) throw IOException("Bus D-Bus chiuso")
        }
        return b.array()
    }

    private fun readLine(deadline: Long): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val c = readFully(1, deadline)[0]
            if (c == '\n'.code.toByte()) return out.toString(Charsets.US_ASCII).trimEnd('\r')
            out.write(c.toInt()); if (out.size() > 512) throw IOException("Risposta D-Bus non valida")
        }
    }

    override fun close() { runCatching { selector.close() }; runCatching { channel.close() } }

    companion object {
        const val CALL = 1
        const val RETURN = 2
        const val ERROR = 3
        const val SIGNAL = 4
        const val CALL_TIMEOUT_MS = 25_000L
        private const val MAX_MESSAGE = 1L shl 24

        /** Il percorso del socket del bus di sessione (`unix:path=`), o null se non c'è o è «abstract». */
        fun sessionPath(env: Map<String, String> = System.getenv()): Path? {
            val addr = env["DBUS_SESSION_BUS_ADDRESS"]
            if (addr.isNullOrBlank()) return env["XDG_RUNTIME_DIR"]?.let { Path.of(it, "bus") }?.takeIf(Files::exists)
            return addr.split(';').firstNotNullOfOrNull { a ->
                if (!a.startsWith("unix:")) return@firstNotNullOfOrNull null
                a.removePrefix("unix:").split(',').firstOrNull { it.startsWith("path=") }?.removePrefix("path=")
                    ?.let { Path.of(unescape(it)) }
            }
        }

        private fun unescape(s: String) = Regex("%([0-9a-fA-F]{2})").replace(s) { it.groupValues[1].toInt(16).toChar().toString() }

        /** Apre il bus di sessione e si presenta; null se non c'è un bus raggiungibile. */
        fun session(): DBus? {
            val path = sessionPath() ?: return null
            val ch = runCatching { SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(path)) } }
                .getOrNull() ?: return null
            val bus = DBus(ch)
            return runCatching {
                val uid = Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int
                val hex = uid.toString().toByteArray().joinToString("") { "%02x".format(it) }
                bus.writeFully("\u0000AUTH EXTERNAL $hex\r\n".toByteArray(Charsets.US_ASCII))
                val deadline = System.currentTimeMillis() + 5_000
                if (!bus.readLine(deadline).startsWith("OK ")) throw IOException("Autenticazione D-Bus rifiutata")
                bus.writeFully("BEGIN\r\n".toByteArray(Charsets.US_ASCII))
                bus.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello")
                bus
            }.getOrElse { bus.close(); null }
        }

        /** Il primo tipo completo di [sig] a partire da [start]: restituisce l'indice subito dopo. */
        internal fun typeEnd(sig: String, start: Int): Int = when (sig[start]) {
            'a' -> typeEnd(sig, start + 1)
            '(', '{' -> {
                val close = if (sig[start] == '(') ')' else '}'
                var i = start + 1
                while (sig[i] != close) i = typeEnd(sig, i)
                i + 1
            }
            else -> start + 1
        }

        internal fun types(sig: String): List<String> = buildList {
            var i = 0
            while (i < sig.length) { val e = typeEnd(sig, i); add(sig.substring(i, e)); i = e }
        }

        internal fun alignment(t: Char) = when (t) {
            'y', 'g', 'v' -> 1
            'n', 'q' -> 2
            'b', 'i', 'u', 's', 'o', 'a', 'h' -> 4
            else -> 8 // x t d ( {
        }
    }
}

/** Scrive valori D-Bus little-endian con l'allineamento del protocollo (relativo all'inizio del messaggio o del corpo). */
internal class Marshal {
    private val out = ByteArrayOutputStream()
    val size get() = out.size()

    fun bytes(): ByteArray = out.toByteArray()

    fun align(n: Int) { while (out.size() % n != 0) out.write(0) }

    private fun u32(v: Long) { align(4); for (i in 0 until 4) out.write(((v shr (8 * i)) and 0xff).toInt()) }

    fun writeAll(sig: String, values: List<Any>) {
        val t = DBus.types(sig)
        require(t.size == values.size) { "Firma $sig e ${values.size} valori" }
        t.zip(values).forEach { (ty, v) -> write(ty, v) }
    }

    fun write(t: String, v: Any) {
        when (t[0]) {
            'y' -> out.write((v as Number).toInt() and 0xff)
            'b' -> u32(if (v as Boolean) 1 else 0)
            'i', 'u' -> u32((v as Number).toLong())
            's', 'o' -> { val b = (v as String).toByteArray(); u32(b.size.toLong()); out.write(b); out.write(0) }
            'g' -> { val b = (v as String).toByteArray(); out.write(b.size); out.write(b); out.write(0) }
            'v' -> { val x = v as Variant; write("g", x.signature); write(x.signature, x.value) }
            '(' -> { align(8); DBus.types(t.substring(1, t.length - 1)).zip(v as List<*>).forEach { (ty, x) -> write(ty, x!!) } }
            'a' -> {
                val elem = t.substring(1)
                u32(0)
                val lenAt = out.size() - 4
                align(DBus.alignment(elem[0]))
                val start = out.size()
                when {
                    elem == "y" -> out.write(v as ByteArray)
                    elem[0] == '{' -> {
                        val (kt, vt) = DBus.types(elem.substring(1, elem.length - 1))
                        (v as Map<*, *>).forEach { (k, x) -> align(8); write(kt, k!!); write(vt, x!!) }
                    }
                    else -> (v as List<*>).forEach { write(elem, it!!) }
                }
                val len = out.size() - start
                val raw = out.toByteArray()
                for (i in 0 until 4) raw[lenAt + i] = ((len shr (8 * i)) and 0xff).toByte()
                out.reset(); out.write(raw)
            }
            else -> throw IllegalArgumentException("Tipo D-Bus non gestito: $t")
        }
    }
}

/** Legge valori D-Bus: `ay` → ByteArray, `a{..}` → Map, `a.` → List, struttura → List, `v` → [Variant], `u` → Long. */
internal class Unmarshal(private val data: ByteArray, order: ByteOrder) {
    private val b = ByteBuffer.wrap(data).order(order)
    var pos = 0

    private fun align(n: Int) { pos = (pos + n - 1) / n * n }
    private fun need(n: Int) { if (pos + n > data.size || n < 0) throw IOException("Messaggio D-Bus troncato") }
    private fun u32(): Long { align(4); need(4); return (b.getInt(pos).toLong() and 0xffffffffL).also { pos += 4 } }

    fun readAll(sig: String): List<Any> = DBus.types(sig).map { read(it) }

    fun read(t: String): Any = when (t[0]) {
        'y' -> { need(1); (data[pos++].toInt() and 0xff) }
        'b' -> u32() != 0L
        'n', 'q' -> { align(2); need(2); b.getShort(pos).toInt().also { pos += 2 } }
        'i' -> u32().toInt()
        'u', 'h' -> u32()
        'x', 't' -> { align(8); need(8); b.getLong(pos).also { pos += 8 } }
        'd' -> { align(8); need(8); b.getDouble(pos).also { pos += 8 } }
        's', 'o' -> { val n = u32().toInt(); need(n + 1); String(data, pos, n).also { pos += n + 1 } }
        'g' -> { need(1); val n = data[pos++].toInt() and 0xff; need(n + 1); String(data, pos, n).also { pos += n + 1 } }
        'v' -> { val s = read("g") as String; Variant(s, read(s)) }
        '(' -> { align(8); DBus.types(t.substring(1, t.length - 1)).map { read(it) } }
        'a' -> {
            val elem = t.substring(1)
            val n = u32().toInt()
            align(DBus.alignment(elem[0]))
            need(n)
            val end = pos + n
            when {
                elem == "y" -> data.copyOfRange(pos, end).also { pos = end }
                elem[0] == '{' -> {
                    val (kt, vt) = DBus.types(elem.substring(1, elem.length - 1))
                    val m = LinkedHashMap<Any, Any>()
                    while (pos < end) { align(8); m[read(kt)] = read(vt) }
                    m
                }
                else -> buildList { while (pos < end) add(read(elem)) }
            }
        }
        else -> throw IOException("Tipo D-Bus sconosciuto: $t")
    }
}
