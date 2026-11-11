package eu.eurise.feature.ponte

import android.content.Context
import android.os.Build
import eu.eurise.core.ponte.Channel
import eu.eurise.core.ponte.Identity
import eu.eurise.core.ponte.Json
import eu.eurise.core.ponte.Kind
import eu.eurise.core.ponte.Link
import eu.eurise.core.ponte.Message
import eu.eurise.core.ponte.Opening
import eu.eurise.core.ponte.Pairing
import eu.eurise.core.ponte.Wire
import eu.eurise.core.ponte.confirmCode
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.UUID

/** Un computer collegato: chiave pubblica, segreto dell'abbinamento e dove trovarlo sulla rete di casa. */
data class Computer(val id: String, val name: String, val publicKey: ByteArray, val secret: ByteArray, val host: String?, val port: Int) {
    override fun equals(other: Any?) = other is Computer && id == other.id
    override fun hashCode() = id.hashCode()
}

/**
 * Chiavi del telefono e computer collegati, nella memoria privata dell'app (fuori dal backup di Android). La chiave
 * privata non esce mai dal telefono.
 */
class PonteStore(context: Context) {
    private val prefs = context.getSharedPreferences("ponte", Context.MODE_PRIVATE)
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val d64 = Base64.getUrlDecoder()

    val deviceId: String = prefs.getString("id", null) ?: Pairing.newDeviceId().also { prefs.edit().putString("id", it).apply() }

    val name: String get() = listOfNotNull(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).distinct().joinToString(" ")

    val identity: Identity by lazy {
        val pub = prefs.getString("pub", null)
        val priv = prefs.getString("priv", null)
        if (pub != null && priv != null) Identity.restore(d64.decode(pub), d64.decode(priv))
        else Identity.generate().also {
            prefs.edit().putString("pub", b64.encodeToString(it.publicBytes)).putString("priv", b64.encodeToString(it.privateBytes())).apply()
        }
    }

    fun computers(): List<Computer> = prefs.getStringSet("computers", emptySet()).orEmpty().mapNotNull { line ->
        val m = Json.read(line) ?: return@mapNotNull null
        runCatching {
            Computer(m.getValue("id"), m.getValue("n"), d64.decode(m.getValue("k")), d64.decode(m.getValue("s")), m["h"], m["p"]?.toInt() ?: Wire.DEFAULT_PORT)
        }.getOrNull()
    }.sortedBy { it.name }

    fun save(c: Computer) {
        val all = computers().filterNot { it.id == c.id } + c
        prefs.edit().putStringSet("computers", all.map {
            Json.write(buildMap {
                put("id", it.id); put("n", it.name); put("k", b64.encodeToString(it.publicKey)); put("s", b64.encodeToString(it.secret))
                it.host?.let { h -> put("h", h) }; put("p", it.port.toString())
            })
        }.toSet()).apply()
    }

    fun forget(c: Computer) {
        val left = computers().filterNot { it.id == c.id }
        prefs.edit().remove("computers").apply()
        left.forEach(::save)
    }

    fun isOn(kind: Kind) = prefs.getBoolean("on." + kind.wire, true)
    fun setOn(kind: Kind, on: Boolean) = prefs.edit().putBoolean("on." + kind.wire, on).apply()
}

/** Il telefono parla con il computer: un collegamento breve per ogni invio (niente servizi sempre accesi). */
class PonteClient(private val store: PonteStore) {

    /** Abbinamento con il codice del QR; restituisce il computer e il codice di conferma da confrontare a occhio. */
    fun pair(p: Pairing): Pair<Computer, String> {
        val me = store.identity
        Socket().use { s ->
            s.connect(InetSocketAddress(p.host ?: error("manca l'indirizzo"), p.port ?: Wire.DEFAULT_PORT), 5_000)
            s.soTimeout = 8_000
            val out = s.getOutputStream()
            val input = s.getInputStream().buffered()
            Wire.writeFrame(out, Opening.pair(store.deviceId, store.name, me.publicBytes, p.secret).encode())
            val channel = Channel(Link.between(me.keys.private, Identity.publicKey(p.publicKey), p.secret), me = store.deviceId)
            val hello = Wire.readFrame(input)?.let { channel.open(it, from = p.deviceId) } ?: error("nessuna risposta")
            check(hello.kind == Kind.Hello)
            val computer = Computer(p.deviceId, hello["name"] ?: p.name, p.publicKey, p.secret, p.host, p.port ?: Wire.DEFAULT_PORT)
            store.save(computer)
            return computer to confirmCode(me.publicBytes, p.publicKey, p.secret)
        }
    }

    /** Manda un messaggio; vero se il computer l'ha ricevuto. */
    fun send(c: Computer, kind: Kind, fields: Map<String, String>): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(c.host ?: return false, c.port), 4_000)
            s.soTimeout = 6_000
            val out = s.getOutputStream()
            val input = s.getInputStream().buffered()
            Wire.writeFrame(out, Opening.Hi(store.deviceId).encode())
            val channel = Channel(Link.between(store.identity.keys.private, Identity.publicKey(c.publicKey), c.secret), me = store.deviceId)
            Wire.readFrame(input)?.let { channel.open(it, from = c.id) } ?: return false
            Wire.writeFrame(out, channel.seal(Message(kind, UUID.randomUUID().toString(), System.currentTimeMillis(), fields)))
            out.flush()
            true
        }
    }.getOrDefault(false)
}
