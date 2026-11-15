package eu.eurise.desktop

import eu.eurise.core.ponte.FileParts
import eu.eurise.core.ponte.Json
import eu.eurise.core.ponte.Kind
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/** Gli orari degli appuntamenti: `2026-11-20T15:30` (o con i secondi) oppure solo `2026-11-20` per il giorno intero. */
object Event {
    private val time = Regex("""\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2})?)?""")
    fun validTime(s: String) = time.matches(s)
}

/** Un file chiesto con il suo percorso diventa i pezzi da mandare ([FileParts]). */
object FileSend {
    fun parts(fields: Map<String, String>): List<Map<String, String>> {
        val f = fields["path"]?.let(::File)?.takeIf { it.isFile && it.length() <= FileParts.MAX } ?: return emptyList()
        val name = fields["name"]?.takeIf { it.isNotBlank() } ?: f.name
        val mime = fields["mime"]?.takeIf { it.contains('/') }
            ?: runCatching { java.nio.file.Files.probeContentType(f.toPath()) }.getOrNull() ?: "application/octet-stream"
        val parts = FileParts.split(name, mime, f.readBytes())
        // I file arrivati da Firefox stanno nella cartella temporanea del Ponte: una volta in coda non servono più.
        if (f.parentFile == Recent.tmp) f.delete()
        return parts
    }
}

/**
 * Le ultime cose arrivate dal telefono (pannello «Dal telefono» di Firefox, tavola PcFirefox): solo sul computer, nella
 * cartella del Ponte, le ultime 30. Gli appunti segnati come riservati e le password non ci finiscono mai.
 */
object Recent {
    private val file by lazy { File(dataDir(), "recenti") }
    val tmp: File by lazy { File(dataDir(), "da-firefox").apply { mkdirs() } }

    @Synchronized
    fun add(kind: Kind, title: String, sub: String, url: String? = null) {
        val line = Json.write(mapOf("k" to kind.wire, "title" to title.take(140), "sub" to sub.take(200), "at" to System.currentTimeMillis().toString()) +
            (url?.let { mapOf("url" to it) } ?: emptyMap()))
        val all = (runCatching { file.readLines() }.getOrDefault(emptyList()) + line).takeLast(30)
        file.writeText(all.joinToString("\n"))
        file.setReadable(false, false); file.setReadable(true, true)
    }

    fun list(): List<String> = runCatching { file.readLines().filter { it.isNotBlank() }.reversed() }.getOrDefault(emptyList())
}

/**
 * Le risposte del telefono a una richiesta di Firefox, con lo stesso `ask`: password ([Kind.PasswordGive]) e dettature
 * chieste da «Detta dal telefono» ([Kind.Dictation]). Restano solo in memoria e solo per due minuti, e si consegnano
 * una volta sola a chi le ha chieste.
 */
object Answers {
    private class Given(val fields: Map<String, String>, val at: Long)
    private val given = ConcurrentHashMap<String, Given>()

    fun put(fields: Map<String, String>) {
        val ask = fields["ask"] ?: return
        given[ask] = Given(fields, System.currentTimeMillis())
    }

    fun take(ask: String): Map<String, String>? {
        val now = System.currentTimeMillis()
        given.entries.removeIf { now - it.value.at > 120_000 }
        return given.remove(ask)?.fields
    }
}

/**
 * L'estensione Eurise per Firefox parla con il Ponte attraverso il native messaging: Firefox avvia
 * `eurise-ponte firefox` e gli scrive messaggi JSON preceduti dalla lunghezza (4 byte, little endian). Qui si
 * traducono in richieste al socket locale del Ponte; se il Ponte non è acceso, ciò che si manda va in coda.
 * Una richiesta: `{"id":"7","t":"note","f.text":"…"}`; la risposta: `{"id":"7","ok":"1","lines":"…"}`.
 * File da Firefox: pezzi `{"t":"file-part","f.fid","f.part","f.parts","f.name","f.mime","f.data"}` raccolti in
 * [Recent.tmp], poi mandati come un file solo.
 */
/** L'ultimo «Oggi» del telefono ([Kind.Today]): solo in memoria, per il pannello di Firefox. */
object TodayCache {
    @Volatile private var last: Pair<Long, Map<String, String>>? = null
    fun put(fields: Map<String, String>) { last = System.currentTimeMillis() to fields }
    /** Il riassunto con `age` (secondi), se è arrivato negli ultimi dieci minuti. */
    fun get(): Map<String, String>? = last?.takeIf { System.currentTimeMillis() - it.first < 600_000 }
        ?.let { (at, f) -> f + ("age" to ((System.currentTimeMillis() - at) / 1000).toString()) }
}

object FirefoxHost {
    fun run(store: Store) {
        val input = DataInputStream(System.`in`.buffered())
        val out = System.out
        while (true) {
            val msg = read(input) ?: break
            val m = Json.read(msg) ?: continue
            val id = m["id"].orEmpty()
            val reply = runCatching { answer(store, m) }.getOrElse { mapOf("ok" to "0", "lines" to (it.message ?: "errore")) }
            write(out, Json.write(mapOf("id" to id) + reply))
        }
    }

    private val uploads = ConcurrentHashMap<String, File>()

    private fun answer(store: Store, m: Map<String, String>): Map<String, String> {
        val t = m["t"].orEmpty()
        val fields = m.filterKeys { it.startsWith("f.") }.mapKeys { it.key.removePrefix("f.") }
        when (t) {
            "hello" -> {
                val status = Control.query("status")
                return mapOf("ok" to "1", "running" to if (status != null) "1" else "0",
                    "devices" to store.devices().joinToString("\n") { it.name }, "lines" to status.orEmpty().joinToString("\n"))
            }
            "status", "recent", "pwd-wait", "dict-wait", "today" -> {
                val lines = Control.query(t, fields) ?: return mapOf("ok" to "0", "running" to "0", "lines" to "")
                return mapOf("ok" to "1", "running" to "1", "lines" to lines.joinToString("\n"))
            }
            "file-part" -> {
                val fid = fields["fid"]?.takeIf { Regex("[A-Za-z0-9-]{8,64}").matches(it) } ?: return fail("pezzo non valido")
                val part = fields["part"]?.toIntOrNull() ?: return fail("pezzo non valido")
                val parts = fields["parts"]?.toIntOrNull() ?: return fail("pezzo non valido")
                val f = uploads.getOrPut(fid) { File(Recent.tmp, fid + "-" + FileParts.safeName(fields["name"].orEmpty())).also { it.delete() } }
                val bytes = Base64.getDecoder().decode(fields["data"].orEmpty())
                if (f.length() + bytes.size > FileParts.MAX) { uploads.remove(fid); f.delete(); return fail("Il file supera 50 MB.") }
                f.appendBytes(bytes)
                if (part < parts - 1) return mapOf("ok" to "1", "lines" to "")
                uploads.remove(fid)
                return send(store, Kind.File, mapOf("path" to f.absolutePath, "name" to fields["name"].orEmpty(), "mime" to fields["mime"].orEmpty()))
            }
        }
        val kind = Kind.of(t) ?: return fail("tipo sconosciuto")
        return send(store, kind, fields)
    }

    private fun send(store: Store, kind: Kind, fields: Map<String, String>): Map<String, String> {
        val line = Json.write(mapOf("t" to kind.wire) + fields.mapKeys { "f." + it.key })
        val (k, f) = Control.request(line) ?: return fail("tipo non permesso")
        val replies = Control.ask(k, f)
        if (replies != null) return mapOf("ok" to "1", "running" to "1", "lines" to replies.joinToString("\n"))
        val devices = store.devices().ifEmpty { return fail("Nessun telefono abbinato: apri un terminale e scrivi eurise-ponte collega") }
        if (kind in Control.liveOnly) return fail("Il Ponte non è acceso: serve il telefono collegato adesso.")
        if (kind == Kind.File) FileSend.parts(fields).forEach { p -> devices.forEach { store.outbox.put(it.id, Kind.File, p) } }
        else devices.forEach { store.outbox.put(it.id, kind, fields) }
        return mapOf("ok" to "1", "running" to "0", "lines" to "In coda: parte quando il Ponte è acceso e il telefono è sulla rete di casa.")
    }

    private fun fail(text: String) = mapOf("ok" to "0", "lines" to text)

    private fun read(input: InputStream): String? {
        val head = ByteArray(4)
        if (!readFully(input, head)) return null
        val size = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).int
        if (size <= 0 || size > 4 * 1024 * 1024) return null
        val body = ByteArray(size)
        if (!readFully(input, body)) return null
        return String(body, Charsets.UTF_8)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) { val n = input.read(buf, off, buf.size - off); if (n < 0) return false; off += n }
        return true
    }

    private fun write(out: OutputStream, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array())
        out.write(body)
        out.flush()
    }
}

/**
 * `eurise-ponte installa-firefox`: registra il Ponte presso Firefox (native messaging) per l'utente, senza sudo.
 * Firefox dei pacchetti (Fedora, .deb) e lo snap di Ubuntu leggono `~/.mozilla/native-messaging-hosts`; il Flatpak di
 * Firefox `~/.var/app/org.mozilla.firefox/.mozilla/native-messaging-hosts`.
 */
object FirefoxInstall {
    const val HOST = "eu.eurise.ponte"
    const val EXTENSION = "eurise-firefox@eurise.eu"

    fun run() {
        val bin = launcher() ?: run { System.err.println("Non trovo bin/eurise-ponte: avvialo dalla cartella estratta."); exitProcess(2) }
        val wrapper = File(dataDir(), "firefox-host").apply {
            writeText("#!/bin/sh\n# Avviato da Firefox per l'estensione Eurise.\nexec \"${bin.absolutePath}\" firefox \"$@\"\n")
            setExecutable(true, true)
        }
        val manifest = """{"name":"$HOST","description":"Ponte Eurise per Firefox","path":"${wrapper.absolutePath}","type":"stdio","allowed_extensions":["$EXTENSION"]}"""
        val home = File(System.getProperty("user.home"))
        val places = listOf(File(home, ".mozilla/native-messaging-hosts")) +
            listOf(File(home, ".var/app/org.mozilla.firefox/.mozilla")).filter { it.parentFile.isDirectory }.map { File(it, "native-messaging-hosts") }
        places.forEach { dir -> dir.mkdirs(); File(dir, "$HOST.json").writeText(manifest); println("Registrato in ${dir.path}") }
        println("Fatto. In Firefox: l'estensione Eurise trova il Ponte da sola (lascia acceso «eurise-ponte» per il telefono).")
    }

    /** bin/eurise-ponte accanto a lib/, dove sta questo jar. */
    private fun launcher(): File? = runCatching {
        val jar = File(FirefoxInstall::class.java.protectionDomain.codeSource.location.toURI())
        File(jar.parentFile.parentFile, "bin/eurise-ponte").takeIf { it.canExecute() }
    }.getOrNull()
}
