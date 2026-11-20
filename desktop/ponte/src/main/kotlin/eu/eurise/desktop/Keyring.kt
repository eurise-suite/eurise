package eu.eurise.desktop

import eu.eurise.core.ponte.Identity
import eu.eurise.core.ponte.Json
import java.io.File
import java.io.IOException
import java.util.Base64

/** Dove tenere un segreto piccolo: il portachiavi del sistema, o (nelle prove) un finto portachiavi in memoria. */
interface SecretStore : AutoCloseable {
    /** Il segreto con questi attributi, o null se non c'è. Lancia [KeyringClosed] se l'utente non apre il portachiavi. */
    fun lookup(attributes: Map<String, String>): ByteArray?

    /** Salva (o sostituisce) il segreto. Lancia [KeyringClosed] se l'utente non apre il portachiavi. */
    fun store(label: String, attributes: Map<String, String>, secret: ByteArray)

    override fun close() {}
}

/** Il portachiavi c'è ma è chiuso e l'utente non l'ha aperto (finestra annullata), o non risponde. */
class KeyringClosed(message: String) : IOException(message)

/**
 * Il portachiavi del sistema con l'interfaccia Secret Service (`org.freedesktop.secrets`): GNOME Keyring su GNOME
 * (Ubuntu, Fedora), KWallet su KDE (Plasma 6 la offre di base), KeePassXC se l'utente la accende. Il segreto viaggia
 * sul bus di sessione dell'utente con la sessione «plain» (il bus è solo suo); se il portachiavi è chiuso il sistema
 * mostra la sua finestra per aprirlo.
 */
class SecretService private constructor(private val bus: DBus, private val session: String) : SecretStore {

    override fun lookup(attributes: Map<String, String>): ByteArray? {
        val found = bus.call(DEST, ROOT, SERVICE, "SearchItems", "a{ss}", attributes)
        @Suppress("UNCHECKED_CAST") val unlocked = found[0] as List<String>
        @Suppress("UNCHECKED_CAST") val locked = found[1] as List<String>
        val item = unlocked.firstOrNull() ?: locked.firstOrNull()?.let { unlock(it) } ?: return null
        val secret = bus.call(DEST, item, ITEM, "GetSecret", "o", session)[0] as List<*>
        return secret[2] as ByteArray
    }

    override fun store(label: String, attributes: Map<String, String>, secret: ByteArray) {
        val collection = bus.call(DEST, ROOT, SERVICE, "ReadAlias", "s", "default")[0] as String
        if (collection == "/") throw IOException("Nessun portachiavi predefinito")
        unlock(collection)
        val props = mapOf(
            "org.freedesktop.Secret.Item.Label" to Variant("s", label),
            "org.freedesktop.Secret.Item.Attributes" to Variant("a{ss}", attributes),
        )
        val r = bus.call(DEST, collection, COLLECTION, "CreateItem", "a{sv}(oayays)b",
            props, listOf(session, ByteArray(0), secret, "text/plain"), true)
        val prompt = r[1] as String
        if (prompt != "/") prompt(prompt)
    }

    /** Apre [path] (voce o raccolta) se è chiuso: il sistema chiede la password del portachiavi. */
    private fun unlock(path: String): String {
        val r = bus.call(DEST, ROOT, SERVICE, "Unlock", "ao", listOf(path))
        @Suppress("UNCHECKED_CAST") if ((r[0] as List<String>).contains(path)) return path
        val prompt = r[1] as String
        if (prompt == "/") throw KeyringClosed("Il portachiavi è chiuso")
        prompt(prompt)
        return path
    }

    private fun prompt(path: String) {
        bus.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "AddMatch", "s",
            "type='signal',interface='$PROMPT',member='Completed',path='$path'")
        bus.call(DEST, path, PROMPT, "Prompt", "s", "")
        val done = runCatching { bus.awaitSignal(path, PROMPT, "Completed", PROMPT_TIMEOUT_MS) }
            .getOrElse { throw KeyringClosed("Il portachiavi non è stato aperto") }
        if (done[0] as Boolean) throw KeyringClosed("Apertura del portachiavi annullata")
    }

    override fun close() {
        runCatching { bus.call(DEST, session, "org.freedesktop.Secret.Session", "Close") }
        bus.close()
    }

    companion object {
        private const val DEST = "org.freedesktop.secrets"
        private const val ROOT = "/org/freedesktop/secrets"
        private const val SERVICE = "org.freedesktop.Secret.Service"
        private const val COLLECTION = "org.freedesktop.Secret.Collection"
        private const val ITEM = "org.freedesktop.Secret.Item"
        private const val PROMPT = "org.freedesktop.Secret.Prompt"
        private const val PROMPT_TIMEOUT_MS = 5 * 60_000L

        /** Il portachiavi del sistema, o null se non c'è (computer senza sessione grafica, nessun servizio). */
        fun open(): SecretService? {
            if (System.getenv("EURISE_PORTACHIAVI") == "no") return null
            val bus = DBus.session() ?: return null
            return runCatching {
                val r = bus.call(DEST, ROOT, SERVICE, "OpenSession", "sv", "plain", Variant("s", ""))
                SecretService(bus, r[1] as String)
            }.getOrElse { bus.close(); null }
        }
    }
}

/**
 * La chiave privata del Ponte sul computer. Nel portachiavi del sistema quando c'è; nel file `chiavi` resta solo la
 * chiave pubblica con `"dove": "portachiavi"`. Senza portachiavi (computer senza sessione grafica) resta nel file
 * (0600, cartella 0700), come prima. Una chiave già nel file passa nel portachiavi alla prima occasione, senza rifare
 * l'abbinamento: si scrive, si rilegge, e solo se è uguale si toglie dal file.
 *
 * Se il file dice «portachiavi» e il portachiavi non risponde o resta chiuso, si ferma con [KeyringClosed]: mai una
 * chiave nuova al suo posto, che staccherebbe in silenzio tutti i telefoni abbinati.
 */
class PonteKey(private val file: File, private val deviceId: String, private val keyring: () -> SecretStore?) {
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val d64 = Base64.getUrlDecoder()
    private val attributes get() = mapOf("xdg:schema" to "eu.eurise.Ponte", "application" to "eu.eurise.Eurise", "ponte" to deviceId)

    /** Dove sta adesso la chiave privata: «portachiavi», «file» o null se non c'è ancora. */
    fun where(): String? = runCatching { Json.read(file.readText()) }.getOrNull()?.let { if (it["priv"] != null) "file" else it["dove"] }

    fun load(write: (File, String) -> Unit): Identity {
        val saved = runCatching { Json.read(file.readText()) }.getOrNull()
        if (saved == null) {
            val created = Identity.generate()
            val inKeyring = intoKeyring(created.privateBytes())
            write(file, json(created.publicBytes, if (inKeyring) null else created.privateBytes()))
            return created
        }
        val pub = d64.decode(saved.getValue("pub"))
        saved["priv"]?.let { priv ->
            val bytes = d64.decode(priv)
            if (intoKeyring(bytes)) write(file, json(pub, null))
            return Identity.restore(pub, bytes)
        }
        val ks = keyring() ?: throw KeyringClosed("Il portachiavi del sistema non risponde: la chiave del Ponte è lì")
        val priv = ks.use { it.lookup(attributes) }
            ?: throw IOException("La chiave del Ponte non è più nel portachiavi: per abbinare di nuovo i telefoni togli ${file.path} e usa «eurise-ponte collega»")
        return Identity.restore(pub, String(priv).let(d64::decode))
    }

    /** true se [priv] ora sta nel portachiavi (scritta e riletta uguale). */
    private fun intoKeyring(priv: ByteArray): Boolean = runCatching {
        keyring()?.use { ks ->
            val text = b64.encodeToString(priv).toByteArray()
            // Una voce diversa con lo stesso nome (cartella copiata da un altro computer) non si sovrascrive mai.
            ks.lookup(attributes)?.let { if (!it.contentEquals(text)) throw IOException("C'è già un'altra chiave del Ponte con questo nome") }
            ks.store("Eurise · chiave del Ponte", attributes, text)
            ks.lookup(attributes)?.contentEquals(text) == true
        } ?: false
    }.getOrElse { e ->
        System.err.println("Portachiavi non disponibile (${e.message}): la chiave del Ponte resta nel file ${file.path}")
        false
    }

    private fun json(pub: ByteArray, priv: ByteArray?) = Json.write(buildMap {
        put("pub", b64.encodeToString(pub))
        if (priv != null) put("priv", b64.encodeToString(priv)) else put("dove", "portachiavi")
    })
}
