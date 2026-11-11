package eu.eurise.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import eu.eurise.core.ponte.Channel
import eu.eurise.core.ponte.Identity
import eu.eurise.core.ponte.Json
import eu.eurise.core.ponte.Kind
import eu.eurise.core.ponte.Link
import eu.eurise.core.ponte.Message
import eu.eurise.core.ponte.Opening
import eu.eurise.core.ponte.Outbox
import eu.eurise.core.ponte.Pairing
import eu.eurise.core.ponte.Wire
import eu.eurise.core.ponte.confirmCode
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.Base64
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * eurise-ponte: il Ponte Eurise sul computer.
 *   eurise-ponte collega            mostra il QR da inquadrare con il telefono e aspetta l'abbinamento
 *   eurise-ponte                    resta in ascolto dei telefoni abbinati (appunti, dettatura, «Continua qui», chiamate)
 *   eurise-ponte dispositivi        elenca i telefoni abbinati
 *   eurise-ponte dimentica <nome>   toglie un telefono
 *   eurise-ponte manda appunti [testo]     manda al telefono gli appunti del computer (o il testo)
 *   eurise-ponte manda link <url> [titolo] «Continua qui» sul telefono
 *   eurise-ponte manda nota <testo>        una nota in Eurise Note (senza testo: legge dallo standard input)
 *   eurise-ponte manda attivita <testo>    una cosa da fare in Eurise Attività («pagare l'affitto venerdì»)
 *   eurise-ponte prova-telefono <codice>   fa finta di essere un telefono (per provare senza telefono)
 *
 * Se il telefono non è collegato, quello che si manda resta in coda e parte quando torna sulla rete di casa.
 */
fun main(args: Array<String>) {
    val home = Store(dataDir())
    when (args.firstOrNull()) {
        null, "ascolta" -> Server(home).run(pairing = false)
        "collega" -> Server(home).run(pairing = true)
        "dispositivi" -> home.devices().ifEmpty { println("Nessun telefono abbinato: eurise-ponte collega"); emptyList() }
            .forEach { println("• ${it.name}  (${it.id})") }
        "dimentica" -> {
            val who = args.getOrNull(1) ?: fail("Quale? eurise-ponte dimentica <nome>")
            val left = home.devices().filterNot { it.name == who || it.id == who }
            home.saveDevices(left)
            println("Fatto.")
        }
        "manda" -> Send.run(home, args.drop(1))
        "prova-telefono" -> FakePhone.run(Pairing.parse(args.getOrNull(1) ?: fail("Manca il codice")) ?: fail("Codice non valido"), args.drop(2))
        "-h", "--help", "aiuto" -> println(HELP)
        else -> fail(HELP)
    }
}

private const val HELP = """eurise-ponte collega | ascolta | dispositivi | dimentica <nome>
eurise-ponte manda appunti [testo] | manda link <url> [titolo] | manda nota <testo> | manda attivita <testo>
eurise-ponte prova-telefono <codice>"""

private fun fail(text: String): Nothing { System.err.println(text); exitProcess(2) }

/** ~/.local/share/eurise/ponte (o $XDG_DATA_HOME): chiavi e telefoni abbinati, leggibili solo dall'utente. */
internal fun dataDir(): File {
    val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
    return File(base, "eurise/ponte").apply { mkdirs(); setReadable(false, false); setReadable(true, true); setExecutable(false, false); setExecutable(true, true) }
}

/** Un telefono abbinato. */
data class Device(val id: String, val name: String, val publicKey: ByteArray, val secret: ByteArray)

class Store(private val dir: File) {
    /** Messaggi per i telefoni non collegati in questo momento. */
    val outbox = Outbox(File(dir, "coda"))

    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val d64 = Base64.getUrlDecoder()

    val id: String by lazy {
        val f = File(dir, "id")
        if (f.exists()) f.readText().trim() else Pairing.newDeviceId().also { private(f, it) }
    }

    val identity: Identity by lazy {
        val f = File(dir, "chiavi")
        val saved = runCatching { Json.read(f.readText()) }.getOrNull()
        if (saved != null) Identity.restore(d64.decode(saved.getValue("pub")), d64.decode(saved.getValue("priv")))
        else Identity.generate().also { private(f, Json.write(mapOf("pub" to b64.encodeToString(it.publicBytes), "priv" to b64.encodeToString(it.privateBytes())))) }
    }

    fun devices(): List<Device> = File(dir, "telefoni").takeIf { it.exists() }?.readLines()?.mapNotNull { line ->
        val m = Json.read(line) ?: return@mapNotNull null
        runCatching { Device(m.getValue("id"), m.getValue("n"), d64.decode(m.getValue("k")), d64.decode(m.getValue("s"))) }.getOrNull()
    }.orEmpty()

    fun saveDevices(list: List<Device>) = private(File(dir, "telefoni"), list.joinToString("\n") {
        Json.write(mapOf("id" to it.id, "n" to it.name, "k" to b64.encodeToString(it.publicKey), "s" to b64.encodeToString(it.secret)))
    })

    private fun private(f: File, text: String) {
        f.writeText(text)
        f.setReadable(false, false); f.setReadable(true, true); f.setWritable(false, false); f.setWritable(true, true)
    }
}

/**
 * Il server del Ponte sulla rete di casa: un collegamento per telefono, cifrato con la chiave comune. Il telefono lo
 * tiene aperto finché è sul Wi-Fi di casa (con la notifica fissa che lo dice), così anche il computer può mandargli
 * qualcosa; se non c'è, il messaggio aspetta in [Store.outbox].
 */
class Server(private val store: Store) {
    private val name = "${System.getProperty("user.name")} · ${InetAddress.getLocalHost().hostName}"

    /** Un telefono collegato adesso. Si scrive una cornice alla volta. */
    private class Session(val device: Device, val channel: Channel, private val out: java.io.OutputStream) {
        @Synchronized fun send(m: Message) = Wire.writeFrame(out, channel.seal(m))
    }

    private val live = ConcurrentHashMap<String, Session>()

    fun run(pairing: Boolean) {
        val socket = runCatching { ServerSocket(Wire.DEFAULT_PORT) }.getOrElse { ServerSocket(0) }
        var secret: ByteArray? = null
        if (pairing) {
            secret = Pairing.newSecret()
            val p = Pairing(store.id, name, store.identity.publicBytes, secret, lanAddress(), socket.localPort)
            println("Inquadra con il telefono: Eurise › Telefono e computer › Collega un computer\n")
            println(Qr.terminal(p.toUri()))
            println("\nO incolla questo codice nel telefono:\n${p.toUri()}\n")
        } else {
            println("Ponte Eurise in ascolto sulla porta ${socket.localPort}. Telefoni abbinati: ${store.devices().size}")
        }
        Control.listen { kind, fields -> deliver(kind, fields) }
        while (true) {
            val client = socket.accept()
            val s = secret
            thread(isDaemon = true) { serve(client, s) { secret = null } }
        }
    }

    /**
     * Manda a tutti i telefoni abbinati: subito a quelli collegati, in coda per gli altri. Restituisce una riga per
     * telefono («Pixel 8: arrivato», «Pixel 8: in coda»).
     */
    fun deliver(kind: Kind, fields: Map<String, String>): List<String> = store.devices().map { d ->
        store.outbox.put(d.id, kind, fields)
        val sent = live[d.id]?.let { flush(it) } ?: false
        "${d.name}: " + if (sent) "arrivato" else "in coda, parte quando il telefono torna sulla rete di casa"
    }

    /** Svuota la coda verso un telefono collegato; vero se non è rimasto niente. */
    private fun flush(session: Session): Boolean {
        for (item in store.outbox.pending(session.device.id)) {
            val ok = runCatching { session.send(item.message) }.isSuccess
            if (!ok) return false
            store.outbox.done(item)
            println("→ ${session.device.name}: ${item.message.kind.wire}")
        }
        return true
    }

    private fun serve(client: Socket, secret: ByteArray?, paired: () -> Unit) = client.use { c ->
        // Il telefono dà un segno di vita ogni pochi minuti: se tace così a lungo, il collegamento è morto.
        c.soTimeout = 15 * 60_000
        c.keepAlive = true
        val input = c.getInputStream().buffered()
        val output = c.getOutputStream()
        val opening = Opening.decode(Wire.readFrame(input) ?: return) ?: return
        val device = when (opening) {
            is Opening.Pair -> {
                if (secret == null || !opening.verify(secret)) { println("Abbinamento rifiutato: codice non valido."); return }
                val d = Device(opening.deviceId, opening.name, opening.publicKey, secret)
                store.saveDevices(store.devices().filterNot { it.id == d.id } + d)
                println("Abbinato: ${d.name}. Controlla che sul telefono ci sia lo stesso codice: ${confirmCode(store.identity.publicBytes, d.publicKey, secret)}")
                paired()
                d
            }
            is Opening.Hi -> store.devices().firstOrNull { it.id == opening.deviceId } ?: run { println("Telefono sconosciuto: ${opening.deviceId}"); return }
        }
        val link = Link.between(store.identity.keys.private, Identity.publicKey(device.publicKey), device.secret)
        val channel = Channel(link, me = store.id)
        val session = Session(device, channel, output)
        session.send(Message(Kind.Hello, UUID.randomUUID().toString(), System.currentTimeMillis(), mapOf("name" to name)))
        live[device.id] = session
        try {
            flush(session)
            while (true) {
                val frame = try { Wire.readFrame(input) } catch (e: SocketTimeoutException) { null } catch (e: Exception) { null } ?: break
                val m = channel.open(frame, from = device.id) ?: continue
                if (m.kind == Kind.Ping || m.kind == Kind.Hello) continue
                Linux.handle(device, m)
            }
        } finally {
            live.remove(device.id, session)
        }
    }

    /** L'indirizzo sulla rete di casa (il primo IPv4 privato), per il QR. */
    private fun lanAddress(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }?.hostAddress
}

/** Ciò che succede sul computer quando arriva un messaggio: con gli strumenti normali di Ubuntu e Fedora. */
object Linux {
    fun handle(device: Device, m: Message) {
        when (m.kind) {
            Kind.Clipboard -> m["text"]?.let { copy(it); notify("Appunti dal telefono", it.take(120)) }
            Kind.Dictation -> m["text"]?.let { text -> if (!type(text)) { copy(text); notify("Dettatura", "Copiata: incollala con Ctrl V") } }
            Kind.Handoff -> m["url"]?.let { open(it); notify("Continua qui", m["title"] ?: it) }
            Kind.Notify -> notify(m["title"] ?: device.name, m["text"].orEmpty())
            else -> println("${device.name}: ${m.kind.wire} ${m.fields}")
        }
        println("${device.name} → ${m.kind.wire}")
    }

    private fun has(cmd: String) = System.getenv("PATH").orEmpty().split(':').any { File(it, cmd).canExecute() }

    private fun run(vararg cmd: String, stdin: String? = null): Boolean = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        if (stdin != null) p.outputStream.use { it.write(stdin.toByteArray()) } else p.outputStream.close()
        p.waitFor() == 0
    }.getOrDefault(false)

    private val wayland get() = !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()

    fun copy(text: String) {
        val ok = when {
            wayland && has("wl-copy") -> run("wl-copy", stdin = text)
            has("xclip") -> run("xclip", "-selection", "clipboard", stdin = text)
            has("xsel") -> run("xsel", "--clipboard", "--input", stdin = text)
            else -> false
        }
        if (!ok) runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
        }
    }

    /** Gli appunti del computer, per mandarli al telefono. */
    fun paste(): String? {
        val out = when {
            wayland && has("wl-paste") -> output("wl-paste", "--no-newline")
            has("xclip") -> output("xclip", "-selection", "clipboard", "-o")
            has("xsel") -> output("xsel", "--clipboard", "--output")
            else -> null
        }
        return out ?: runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
        }.getOrNull()
    }

    private fun output(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.close()
        val text = p.inputStream.bufferedReader().readText()
        if (p.waitFor() == 0) text else null
    }.getOrNull()

    /** Scrive dove c'è il cursore (wtype su Wayland, xdotool su X11); falso se non c'è lo strumento. */
    fun type(text: String) = when {
        wayland && has("wtype") -> run("wtype", text)
        !wayland && has("xdotool") -> run("xdotool", "type", "--delay", "8", "--", text)
        else -> false
    }

    fun open(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        run("xdg-open", url)
    }

    fun notify(title: String, body: String) {
        if (has("notify-send")) run("notify-send", "--app-name=Eurise", title, body) else println("$title: $body")
    }
}

/** Il QR nel terminale: due righe per carattere con i mezzi blocchi, bianco su nero come vogliono i lettori. */
object Qr {
    fun terminal(text: String): String {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2))
        val sb = StringBuilder()
        var y = 0
        while (y < m.height) {
            for (x in 0 until m.width) {
                val top = m.get(x, y)
                val bottom = y + 1 < m.height && m.get(x, y + 1)
                sb.append(when { top && bottom -> ' '; top -> '▄'; bottom -> '▀'; else -> '█' })
            }
            sb.append('\n')
            y += 2
        }
        return sb.toString()
    }
}

/** Un telefono finto: si abbina, manda un appunto e un «Continua qui», poi mostra per tre secondi ciò che arriva. */
object FakePhone {
    fun run(p: Pairing, extra: List<String>) {
        val me = Identity.generate()
        val id = "prova-" + Pairing.newDeviceId()
        Socket(p.host ?: "127.0.0.1", p.port ?: Wire.DEFAULT_PORT).use { s ->
            val out = s.getOutputStream()
            val input = s.getInputStream().buffered()
            Wire.writeFrame(out, Opening.pair(id, "Telefono di prova", me.publicBytes, p.secret).encode())
            val link = Link.between(me.keys.private, Identity.publicKey(p.publicKey), p.secret)
            val channel = Channel(link, me = id)
            val hello = Wire.readFrame(input)?.let { channel.open(it, from = p.deviceId) }
            println("Collegato a ${hello?.get("name")}. Codice: ${confirmCode(me.publicBytes, p.publicKey, p.secret)}")
            val now = System.currentTimeMillis()
            Wire.writeFrame(out, channel.seal(Message(Kind.Clipboard, UUID.randomUUID().toString(), now, mapOf("text" to (extra.firstOrNull() ?: "IT60 X054 2811 1010 0000 0123 456")))))
            Wire.writeFrame(out, channel.seal(Message(Kind.Handoff, UUID.randomUUID().toString(), now, mapOf("url" to "https://eurise.eu/", "title" to "Eurise"))))
            println("Mandati: appunti e «Continua qui».")
            // Come il telefono vero resta in ascolto: stampa quello che il computer manda (anche dalla coda).
            s.soTimeout = 3_000
            while (true) {
                val frame = try { Wire.readFrame(input) } catch (e: SocketTimeoutException) { null } ?: break
                channel.open(frame, from = p.deviceId)?.let { println("← ${it.kind.wire} ${it.fields}") }
            }
        }
    }
}
