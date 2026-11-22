package eu.eurise.desktop

import eu.eurise.core.ponte.Json
import eu.eurise.core.ponte.Kind
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Il socket locale del Ponte, in `$XDG_RUNTIME_DIR/eurise-ponte.sock` (solo l'utente può aprirlo). Da qui passano i
 * comandi del terminale (`eurise-ponte manda …`) e, quando arrivano, l'estensione di Firefox (native messaging) e Office.
 * Una richiesta è una riga JSON piatta `{"t":"clip","f.text":"…"}`; la risposta, una riga per telefono.
 * Oltre ai tipi da mandare, tre domande per Firefox: `status` (telefoni e chi è collegato), `recent` (le ultime cose
 * arrivate dal telefono, una riga JSON l'una) e `pwd-wait` (la risposta del telefono a una richiesta di password).
 */
object Control {
    val path: File by lazy {
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        File(runtime ?: dataDir(), "eurise-ponte.sock")
    }

    fun listen(query: (String, Map<String, String>) -> List<String>?, deliver: (Kind, Map<String, String>) -> List<String>) {
        path.delete()
        val server = runCatching {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path.toPath())) }
        }.getOrElse { println("Socket locale non disponibile (${it.message}): «manda» userà la coda."); return }
        runCatching { Files.setPosixFilePermissions(path.toPath(), PosixFilePermissions.fromString("rw-------")) }
        Runtime.getRuntime().addShutdownHook(Thread { path.delete() })
        thread(isDaemon = true, name = "ponte-locale") {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { answer(client, query, deliver) }
            }
        }
    }

    private fun answer(client: SocketChannel, query: (String, Map<String, String>) -> List<String>?, deliver: (Kind, Map<String, String>) -> List<String>) {
        client.use { c ->
            val line = Channels.newReader(c, Charsets.UTF_8).buffered().readLine() ?: return
            val asked = Json.read(line)?.let { m -> m["t"]?.takeIf { it in questions }?.let { t -> query(t, fields(m)) } }
            val reply = asked ?: request(line)?.let { (kind, fields) -> deliver(kind, fields) } ?: listOf("errore: richiesta non valida")
            c.write(ByteBuffer.wrap((reply.joinToString("\n") + "\n").toByteArray()))
        }
    }

    /** Solo i tipi che il computer può mandare al telefono. Un file si chiede con il suo percorso (`f.path`). */
    private val allowed = setOf(Kind.Clipboard, Kind.Handoff, Kind.Note, Kind.Task, Kind.Event, Kind.File, Kind.PasswordAsk,
        Kind.DictationAsk, Kind.TodayAsk, Kind.SyncAsk)
    private val questions = setOf("status", "recent", "pwd-wait", "dict-wait", "today", "sync-wait")
    /** Richieste che valgono solo adesso: mai in coda, solo al telefono collegato. */
    val liveOnly = setOf(Kind.PasswordAsk, Kind.DictationAsk, Kind.TodayAsk, Kind.SyncAsk)

    private fun fields(m: Map<String, String>) = m.filterKeys { it.startsWith("f.") }.mapKeys { it.key.removePrefix("f.") }

    fun request(line: String): Pair<Kind, Map<String, String>>? {
        val m = Json.read(line) ?: return null
        val kind = Kind.of(m["t"] ?: return null)?.takeIf { it in allowed } ?: return null
        return kind to fields(m)
    }

    /** Una domanda al Ponte acceso ([questions]); null se il Ponte non è acceso. */
    fun query(t: String, fields: Map<String, String> = emptyMap()): List<String>? = runCatching {
        SocketChannel.open(UnixDomainSocketAddress.of(path.toPath())).use { c ->
            c.write(ByteBuffer.wrap((Json.write(mapOf("t" to t) + fields.mapKeys { "f." + it.key }) + "\n").toByteArray()))
            Channels.newReader(c, Charsets.UTF_8).buffered().readLines()
        }
    }.getOrNull()

    /** Chiede al Ponte acceso di mandare; null se il Ponte non è acceso. */
    fun ask(kind: Kind, fields: Map<String, String>): List<String>? = runCatching {
        SocketChannel.open(UnixDomainSocketAddress.of(path.toPath())).use { c ->
            val line = Json.write(mapOf("t" to kind.wire) + fields.mapKeys { "f." + it.key }) + "\n"
            c.write(ByteBuffer.wrap(line.toByteArray()))
            Channels.newReader(c, Charsets.UTF_8).buffered().readLines()
        }
    }.getOrNull()
}

/** `eurise-ponte manda …`: dal computer al telefono. */
object Send {
    fun run(store: Store, args: List<String>) {
        val what = args.firstOrNull() ?: stop(USAGE)
        val rest = args.drop(1)
        val (kind, fields) = when (what) {
            "appunti", "clip" -> {
                val text = rest.joinToString(" ").ifBlank { Linux.paste().orEmpty() }.ifBlank { stop("Gli appunti sono vuoti.") }
                Kind.Clipboard to mapOf("text" to text)
            }
            "link", "url" -> {
                val url = rest.firstOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: stop("Serve un indirizzo http o https.")
                Kind.Handoff to (mapOf("url" to url) + (rest.drop(1).joinToString(" ").takeIf { it.isNotBlank() }?.let { mapOf("title" to it) } ?: emptyMap()))
            }
            "nota", "note" -> Kind.Note to mapOf("text" to textOrStdin(rest))
            "attivita", "attività", "task" -> Kind.Task to mapOf("text" to textOrStdin(rest))
            "evento", "event" -> {
                // eurise-ponte manda evento 2026-11-20T15:30 [fine] Riunione con Anna
                val start = rest.firstOrNull()?.takeIf { Event.validTime(it) } ?: stop("Serve l'inizio: 2026-11-20T15:30 oppure 2026-11-20")
                val end = rest.getOrNull(1)?.takeIf { Event.validTime(it) }
                val title = rest.drop(if (end != null) 2 else 1).joinToString(" ").ifBlank { stop("Manca il titolo.") }
                Kind.Event to (mapOf("title" to title, "start" to start) + (end?.let { mapOf("end" to it) } ?: emptyMap()))
            }
            "file" -> {
                val f = rest.firstOrNull()?.let(::File)?.takeIf { it.isFile } ?: stop("Quale file? eurise-ponte manda file <percorso>")
                if (f.length() > eu.eurise.core.ponte.FileParts.MAX) stop("Il file supera 50 MB.")
                Kind.File to mapOf("path" to f.absolutePath)
            }
            else -> stop(USAGE)
        }
        val replies = Control.ask(kind, fields)
        if (replies != null) { replies.forEach(::println); return }
        // Il Ponte non è acceso: resta in coda per ogni telefono e parte alla prossima accensione.
        val devices = store.devices().ifEmpty { stop("Nessun telefono abbinato: eurise-ponte collega") }
        if (kind == Kind.File) FileSend.parts(fields).forEach { p -> devices.forEach { store.outbox.put(it.id, Kind.File, p) } }
        else devices.forEach { store.outbox.put(it.id, kind, fields) }
        println("In coda per ${devices.joinToString { it.name }}: parte quando eurise-ponte è acceso e il telefono è sulla rete di casa.")
    }

    private fun textOrStdin(rest: List<String>) =
        rest.joinToString(" ").ifBlank { generateSequence(::readLine).joinToString("\n") }.trim().ifBlank { stop("Manca il testo.") }

    private fun stop(text: String): Nothing { System.err.println(text); exitProcess(2) }

    private const val USAGE = "eurise-ponte manda appunti [testo] | link <url> [titolo] | nota <testo> | attivita <testo> | " +
        "evento <inizio> [fine] <titolo> | file <percorso>"
}
