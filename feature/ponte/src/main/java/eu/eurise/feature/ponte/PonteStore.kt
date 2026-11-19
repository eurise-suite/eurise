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
import eu.eurise.core.ponte.Upgrade
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

    /**
     * La coppia di chiavi del telefono. La privata sta sigillata con una chiave dell'Android Keystore («priv_k»): chi copia
     * i file dell'app non la può usare. Fino al 18 novembre 2026 era in chiaro («priv»): la si sigilla alla prima lettura,
     * senza rifare l'abbinamento. Se il Keystore non funziona, resta com'era (meglio un Ponte che va).
     */
    val identity: Identity by lazy {
        val pub = prefs.getString("pub", null)
        val sealed = prefs.getString("priv_k", null)
        val plain = prefs.getString("priv", null)
        val priv = sealed?.let { KeySeal.open(it) } ?: plain?.let { d64.decode(it) }
        if (pub != null && priv != null) {
            if (sealed == null) KeySeal.seal(priv)?.let { prefs.edit().putString("priv_k", it).remove("priv").apply() }
            Identity.restore(d64.decode(pub), priv)
        } else Identity.generate().also {
            val edit = prefs.edit().putString("pub", b64.encodeToString(it.publicBytes))
            val s = KeySeal.seal(it.privateBytes())
            if (s != null) edit.putString("priv_k", s).remove("priv") else edit.putString("priv", b64.encodeToString(it.privateBytes()))
            edit.apply()
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

    /**
     * Dal computer al telefono: spento di base. Acceso, il telefono tiene un collegamento fisso con il computer sul
     * Wi-Fi di casa, sempre segnalato dalla notifica (PonteLink).
     */
    var receiveOn: Boolean
        get() = prefs.getBoolean("ricevi", false)
        set(value) { prefs.edit().putBoolean("ricevi", value).apply() }

    /** L'ultima cosa arrivata dal computer, per la notifica fissa («12:03 · appunti dal computer»). */
    var lastThing: String?
        get() = prefs.getString("ultima", null)
        set(value) { prefs.edit().putString("ultima", value).apply() }

    /** Ciò che il computer riceve dal telefono è acceso di base, tranne «Oggi sul computer» ([Kind.Today]): spento di base. */
    /**
     * I computer che hanno già risposto con la chiave di sessione (v2). Con loro un collegamento che torna alla sola chiave
     * fissa è qualcuno che, sulla rete, ha tolto la chiave effimera dal saluto: si chiude (docs/SICUREZZA.md, «Ponte»).
     */
    fun spokeV2(id: String) = id in prefs.getStringSet("v2", emptySet()).orEmpty()
    fun markV2(id: String, on: Boolean = true) {
        val now = prefs.getStringSet("v2", emptySet()).orEmpty()
        prefs.edit().putStringSet("v2", if (on) now + id else now - id).apply()
    }

    /**
     * Apre il collegamento verso [c] (dopo la connessione): saluto con una chiave effimera nuova, primo messaggio del
     * computer con la chiave fissa e, se il computer è aggiornato, il canale di questa sola sessione. Null se il computer
     * non risponde come deve.
     */
    fun open(c: Computer, out: java.io.OutputStream, input: java.io.InputStream): Channel? {
        val ephemeral = Identity.generate()
        Wire.writeFrame(out, Opening.Hi(deviceId, ephemeral.publicBytes).encode())
        val fixed = Channel(Link.between(identity.keys.private, Identity.publicKey(c.publicKey), c.secret), me = deviceId)
        val hello = Wire.readFrame(input)?.let { fixed.open(it, from = c.id) } ?: return null
        if (Upgrade.spokeV2(hello)) { if (!spokeV2(c.id)) markV2(c.id) } else if (spokeV2(c.id)) return null
        return Upgrade.phone(identity, ephemeral, Identity.publicKey(c.publicKey), c.secret, hello, fixed, deviceId)
    }

    fun isOn(kind: Kind) = prefs.getBoolean("on." + kind.wire, kind != Kind.Today)
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
            store.markV2(computer.id, on = false)
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
            val channel = store.open(c, out, input) ?: return false
            Wire.writeFrame(out, channel.seal(Message(kind, UUID.randomUUID().toString(), System.currentTimeMillis(), fields)))
            out.flush()
            true
        }
    }.getOrDefault(false)
}

/** Sigilla piccoli segreti del Ponte con una chiave AES-GCM dell'Android Keystore (non esportabile). */
private object KeySeal {
    private const val ALIAS = "eurise-ponte"

    private fun key(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build(),
        )
        return generator.generateKey()
    }

    fun seal(plain: ByteArray): String? = runCatching {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        val e = Base64.getUrlEncoder().withoutPadding()
        e.encodeToString(c.iv) + ":" + e.encodeToString(c.doFinal(plain))
    }.getOrNull()

    fun open(stored: String): ByteArray? = runCatching {
        val (iv, data) = stored.split(':', limit = 2)
        val d = Base64.getUrlDecoder()
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, d.decode(iv)))
        c.doFinal(d.decode(data))
    }.getOrNull()
}
