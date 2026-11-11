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
 */
object Control {
    val path: File by lazy {
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }
        File(runtime ?: dataDir(), "eurise-ponte.sock")
    }

    fun listen(deliver: (Kind, Map<String, String>) -> List<String>) {
        path.delete()
        val server = runCatching {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path.toPath())) }
        }.getOrElse { println("Socket locale non disponibile (${it.message}): «manda» userà la coda."); return }
        runCatching { Files.setPosixFilePermissions(path.toPath(), PosixFilePermissions.fromString("rw-------")) }
        Runtime.getRuntime().addShutdownHook(Thread { path.delete() })
        thread(isDaemon = true, name = "ponte-locale") {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                thread(isDaemon = true) { answer(client, deliver) }
            }
        }
    }

    private fun answer(client: SocketChannel, deliver: (Kind, Map<String, String>) -> List<String>) = client.use { c ->
        val line = Channels.newReader(c, Charsets.UTF_8).buffered().readLine() ?: return
        val reply = request(line)?.let { (kind, fields) -> deliver(kind, fields) } ?: listOf("errore: richiesta non valida")
        c.write(ByteBuffer.wrap((reply.joinToString("\n") + "\n").toByteArray()))
    }

    /** Solo i tipi che il computer può mandare al telefono. */
    private val allowed = setOf(Kind.Clipboard, Kind.Handoff, Kind.Note, Kind.Task)

    fun request(line: String): Pair<Kind, Map<String, String>>? {
        val m = Json.read(line) ?: return null
        val kind = Kind.of(m["t"] ?: return null)?.takeIf { it in allowed } ?: return null
        return kind to m.filterKeys { it.startsWith("f.") }.mapKeys { it.key.removePrefix("f.") }
    }

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
            else -> stop(USAGE)
        }
        val replies = Control.ask(kind, fields)
        if (replies != null) { replies.forEach(::println); return }
        // Il Ponte non è acceso: resta in coda per ogni telefono e parte alla prossima accensione.
        val devices = store.devices().ifEmpty { stop("Nessun telefono abbinato: eurise-ponte collega") }
        devices.forEach { store.outbox.put(it.id, kind, fields) }
        println("In coda per ${devices.joinToString { it.name }}: parte quando eurise-ponte è acceso e il telefono è sulla rete di casa.")
    }

    private fun textOrStdin(rest: List<String>) =
        rest.joinToString(" ").ifBlank { generateSequence(::readLine).joinToString("\n") }.trim().ifBlank { stop("Manca il testo.") }

    private fun stop(text: String): Nothing { System.err.println(text); exitProcess(2) }

    private const val USAGE = "eurise-ponte manda appunti [testo] | link <url> [titolo] | nota <testo> | attivita <testo>"
}
