package eu.eurise.desktop.app

import eu.eurise.core.ponte.Json
import eu.eurise.desktop.SecretService
import eu.eurise.desktop.dataDir
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Il cloud di Calendario e Attività sul computer (tavole PcCalendario e PcAttivita, approvate il 16 novembre): lo stesso
 * del telefono (Murena o un Nextcloud), con lo stesso accesso in due tocchi del telefono, il «Login Flow v2» di
 * Nextcloud. «Collega» apre il browser, la persona entra come sempre (anche con la verifica in due passaggi) e approva;
 * l'app riceve una password per le app, revocabile dal cloud. Nessuna password passa dall'app.
 *
 * La password per le app sta solo nel portachiavi del sistema (Secret Service, come la chiave del Ponte); su disco resta
 * solo l'indirizzo del server e il nome. Senza portachiavi il collegamento vale fino alla chiusura dell'app.
 */
data class CloudAccount(val server: String, val loginName: String, val davUser: String) {
    /** La base CalDAV di Nextcloud e Murena. */
    val calDav: String get() = server.trimEnd('/') + "/remote.php/dav/"
    /** Il nome da mostrare: «Murena» o l'indirizzo del server. */
    val label: String get() = if (server.contains("murena.io")) "Murena" else server.removePrefix("https://").trimEnd('/')
}

object Cloud {
    const val USER_AGENT = "Eurise per il computer"
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val file: File get() = File(dataDir(), "cloud.json")

    /** La password per le app tenuta in memoria quando il portachiavi non c'è (vale fino alla chiusura). */
    @Volatile private var memory: Pair<String, String>? = null

    data class Start(val loginUrl: String, val pollUrl: String, val token: String)

    /** «https://» davanti se manca; «murena.io» diventa il cloud di Murena. */
    fun normalize(server: String): String {
        val s = server.trim().trimEnd('/')
        return if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
    }

    /** Avvia l'accesso: la pagina da aprire nel browser e dove chiedere l'esito. */
    fun start(server: String): Start {
        val req = Request.Builder().url(normalize(server) + "/index.php/login/v2").header("User-Agent", USER_AGENT)
            .post(ByteArray(0).toRequestBody()).build()
        client.newCall(req).execute().use { res ->
            check(res.isSuccessful) { "Il server non risponde all'accesso (${res.code})" }
            val body = res.body.string()
            return Start(
                field(body, "login") ?: error("risposta senza pagina di accesso"),
                field(body, "endpoint") ?: error("risposta senza indirizzo"),
                field(body, "token") ?: error("risposta senza codice"),
            )
        }
    }

    /** Una richiesta dell'esito: null finché la persona non ha approvato nel browser. */
    fun poll(start: Start): Pair<CloudAccount, String>? = runCatching {
        val req = Request.Builder().url(start.pollUrl).header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("token", start.token).build()).build()
        client.newCall(req).execute().use { res ->
            if (res.code != 200) return@use null
            val body = res.body.string()
            val server = field(body, "server") ?: return@use null
            val login = field(body, "loginName") ?: return@use null
            val password = field(body, "appPassword") ?: return@use null
            CloudAccount(server.trimEnd('/'), login, userId(server, login, password) ?: login) to password
        }
    }.getOrNull()

    /** L'identificativo dell'utente negli indirizzi DAV (su molti server è diverso dal nome di accesso). */
    private fun userId(server: String, login: String, password: String): String? = runCatching {
        val req = Request.Builder().url(server.trimEnd('/') + "/ocs/v2.php/cloud/user?format=json")
            .header("User-Agent", USER_AGENT).header("OCS-APIRequest", "true")
            .header("Authorization", Credentials.basic(login, password)).build()
        client.newCall(req).execute().use { res -> if (res.isSuccessful) field(res.body.string(), "id") else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Salva il collegamento: il server e il nome su disco (permessi 600), la password nel portachiavi. Vero se è lì. */
    fun save(account: CloudAccount, password: String): Boolean {
        writePrivate(file, Json.write(mapOf("server" to account.server, "login" to account.loginName, "dav" to account.davUser)))
        val inKeyring = runCatching {
            SecretService.open()?.use { it.store("Eurise · ${account.label}", attributes(account), password.toByteArray()); true } ?: false
        }.getOrDefault(false)
        memory = if (inKeyring) null else account.server to password
        return inKeyring
    }

    fun account(): CloudAccount? = runCatching {
        Json.read(file.readText())?.let { m -> CloudAccount(m["server"] ?: return@let null, m["login"].orEmpty(), m["dav"] ?: m["login"].orEmpty()) }
    }.getOrNull()

    /** La password per le app: dal portachiavi (che può chiedere di sbloccarsi) o dalla memoria. */
    fun password(account: CloudAccount): String? {
        memory?.takeIf { it.first == account.server }?.let { return it.second }
        return runCatching { SecretService.open()?.use { it.lookup(attributes(account)) } }.getOrNull()?.toString(Charsets.UTF_8)
    }

    /** Scollega: toglie il file; la password per le app si revoca anche dalle impostazioni di sicurezza del cloud. */
    fun forget() {
        file.delete()
        memory = null
    }

    private fun attributes(a: CloudAccount) = mapOf("eurise" to "cloud", "server" to a.server, "user" to a.loginName)

    /** Un campo stringa da una risposta JSON (le risposte del Login Flow hanno solo stringhe). */
    internal fun field(json: String, name: String): String? =
        Regex("\"" + Regex.escape(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")?.replace("\\\"", "\"")?.replace("\\\\", "\\")

    /** Scrive un file leggibile solo dall'utente (600), creato già così. */
    internal fun writePrivate(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.delete()
        runCatching { Files.createFile(tmp.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
            .onFailure { tmp.createNewFile() }
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
