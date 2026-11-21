package eu.eurise.mail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.eurise.core.studio.Channel
import eu.eurise.core.studio.StudioContract
import org.json.JSONArray
import org.json.JSONObject

/** Esito del collegamento di una casella. */
sealed interface LinkResult {
    data class Done(val account: Account) : LinkResult
    data class Failed(val reason: Int) : LinkResult
}

/**
 * Lo stato di Email: caselle, posta di tutte le caselle in una (dalla cache sul telefono, poi aggiornata), messaggio
 * aperto. Le operazioni di rete vanno su [Dispatchers.IO].
 */
class MailModel(private val context: Context, private val scope: CoroutineScope) {
    private val store = AccountStore(context)
    private val prefs = context.getSharedPreferences("mail", Context.MODE_PRIVATE)

    val accounts = mutableStateListOf<Account>().apply { addAll(store.load()) }
    val messages = mutableStateListOf<Summary>()
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var opened by mutableStateOf<Opened?>(null)
        private set
    var opening by mutableStateOf(false)
        private set
    /** I pazienti dello Studio (nella Chat), per riconoscerli: vuoto con lo Studio spento o senza la Chat. */
    var studio by mutableStateOf(StudioContract.Known(emptySet(), emptySet()))
        private set
    /** Il paziente del messaggio aperto, se lo è. */
    var openedPatient by mutableStateOf<String?>(null)
        private set
    /** Immagini remote: bloccate (tavola MailCaselle: «Blocca chi spia l'apertura»). */
    var blockImages by mutableStateOf(prefs.getBoolean("block_images", true))
        private set

    init {
        messages.addAll(accounts.flatMap { readCache(it.id) }.sortedByDescending { it.date })
    }

    @JvmName("changeBlockImages") fun setBlockImages(on: Boolean) {
        blockImages = on
        prefs.edit().putBoolean("block_images", on).apply()
    }

    private fun cacheFile(id: String) = MailCache.file(context, id)

    private fun readCache(id: String): List<Summary> = MailCache.read(context, id)

    private fun writeCache(id: String, list: List<Summary>) {
        val a = JSONArray()
        list.forEach { m ->
            a.put(JSONObject().put("uid", m.uid).put("fn", m.fromName).put("fa", m.fromAddress).put("s", m.subject).put("d", m.date)
                .put("seen", m.seen).put("k", m.kind.name).put("ans", m.answered))
        }
        runCatching { cacheFile(id).writeText(a.toString()) }
    }

    /** Aggiorna la posta di tutte le caselle. */
    fun refresh() {
        if (refreshing || accounts.isEmpty()) return
        refreshing = true
        error = null
        scope.launch {
            val failures = mutableListOf<String>()
            val all = accounts.toList().flatMap { a ->
                withContext(Dispatchers.IO) {
                    runCatching { MailClient(a).latest() }
                        .onSuccess { list -> writeCache(a.id, list); SyncWorker.remember(context, a.id, list.maxOfOrNull { it.uid } ?: 0L) }
                        .onFailure { failures += a.label }
                        .getOrElse { readCache(a.id) }
                }
            }
            messages.clear()
            messages.addAll(all.sortedByDescending { it.date })
            refreshBalls()
            refreshing = false
            studio = withContext(Dispatchers.IO) { StudioContract.known(context).also { offerToStudio(context, it, all) } }
            if (failures.isNotEmpty()) error = context.getString(R.string.ml_refresh_failed, failures.joinToString(", "))
        }
    }

    fun account(id: String) = accounts.firstOrNull { it.id == id }

    // --- Passa la palla (tavola InsPalla) ---

    private val ballPrefs get() = context.getSharedPreferences("mail_balls", Context.MODE_PRIVATE)
    /** Le palle passate per email, sul telefono; fatte quando il collega risponde sullo stesso oggetto. */
    var balls by mutableStateOf(MailBalls.decode(ballPrefs.getString("list", null)))
        private set

    private fun saveBalls(list: List<MailBalls.Ball>) { balls = list; ballPrefs.edit().putString("list", MailBalls.encode(list)).apply() }

    fun refreshBalls() { val next = MailBalls.update(balls, messages); if (next != balls) saveBalls(next) }

    /** Inoltra l'email aperta a [to] con la riga sul da farsi in cima, e la segna tra le palle passate. */
    fun passBall(o: Opened, to: String, task: String, copyMe: String?, header: String, done: (Boolean) -> Unit) {
        val from = account(o.summary.accountId) ?: accounts.firstOrNull() ?: return done(false)
        val subject = MailText.forwardSubject(o.summary.subject)
        send(from, to.trim(), "", subject, MailBalls.body(task, copyMe, header, o.text), null, emptyList()) { ok ->
            if (ok) saveBalls(balls + MailBalls.Ball("m" + System.nanoTime(), to.trim(), o.summary.subject, task.trim(), System.currentTimeMillis()))
            done(ok)
        }
    }

    fun retakeBall(id: String) = saveBalls(balls.map { if (it.id == id) it.copy(taken = true) else it })

    fun open(s: Summary) {
        val a = account(s.accountId) ?: return
        opening = true
        opened = null
        scope.launch {
            val o = withContext(Dispatchers.IO) { runCatching { MailClient(a).open(s.uid) }.getOrNull() }
            opening = false
            if (o == null) { error = context.getString(R.string.ml_open_failed); return@launch }
            opened = o
            openedPatient = null
            // Un paziente: il testo intero va allo Studio (la coda aveva solo l'oggetto) e qui compare «Apri nello Studio».
            if (studio.email(s.fromAddress)) openedPatient = withContext(Dispatchers.IO) {
                StudioContract.offer(context, Channel.Email, s.fromAddress, s.subject + "\n" + o.text.take(4000), studioRef(s), s.date)?.patientId
            }
            val i = messages.indexOfFirst { it.accountId == s.accountId && it.uid == s.uid }
            if (i >= 0) messages[i] = messages[i].copy(seen = true)
            writeCache(a.id, messages.filter { it.accountId == a.id })
        }
    }

    fun close() { opened = null; opening = false; openedPatient = null }

    /** Archivia, cestina o segna come spam il messaggio aperto. */
    fun move(s: Summary, target: MailClient.Target, done: (Boolean) -> Unit) = relocate(s, done) { it.move(s.uid, target) }

    /** «Sposta in…»: nella cartella scelta. */
    fun moveTo(s: Summary, folder: String, done: (Boolean) -> Unit) = relocate(s, done) { it.moveTo(s.uid, folder) }

    private fun relocate(s: Summary, done: (Boolean) -> Unit, action: (MailClient) -> Boolean) {
        val a = account(s.accountId) ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { action(MailClient(a)) }.getOrDefault(false) }
            if (ok) {
                messages.removeAll { it.accountId == s.accountId && it.uid == s.uid }
                writeCache(a.id, messages.filter { it.accountId == a.id })
                opened = null
            }
            done(ok)
        }
    }

    /** Le cartelle della casella del messaggio, per «Sposta in…». */
    fun folders(s: Summary, done: (List<String>) -> Unit) {
        val a = account(s.accountId) ?: return done(emptyList())
        scope.launch {
            done(withContext(Dispatchers.IO) { runCatching { MailClient(a).folders() }.getOrDefault(emptyList()) })
        }
    }

    fun attachment(s: Summary, index: Int, done: (File?) -> Unit) {
        val a = account(s.accountId) ?: return
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "allegati").apply { deleteRecursively(); mkdirs() }
                runCatching { MailClient(a).attachment(s.uid, index, dir) }.getOrNull()
            }
            done(f)
        }
    }

    fun send(from: Account, to: String, cc: String, subject: String, body: String, inReplyTo: String?, files: List<android.net.Uri>, done: (Boolean) -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // Gli allegati (da File, Office, Galleria…) copiati qui, con il loro nome.
                    val dir = File(context.cacheDir, "da_inviare").apply { deleteRecursively(); mkdirs() }
                    val copies = files.mapIndexed { i, uri ->
                        val name = displayName(uri) ?: "allegato-${i + 1}"
                        val out = File(dir, name.replace('/', '-'))
                        context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                        out
                    }
                    MailClient(from).send(to, cc, subject, body, inReplyTo, copies)
                }.isSuccess
            }
            done(ok)
        }
    }

    private fun displayName(uri: android.net.Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    // --- Collegare una casella (tavola MailCaselle: «Collega», accedi, approva; server e porte li trova Eurise) ---

    /** Le caselle arrivate dal backup, da ricollegare: indirizzo e nome (i server li ricorda [restoredSettings]). */
    fun restoredAccounts(): List<Pair<String, String>> = restored().map { it.optString("email") to it.optString("name") }

    private fun restored(): List<org.json.JSONObject> = runCatching {
        val a = org.json.JSONArray(java.io.File(context.filesDir, eu.eurise.mail.backup.MailBackupProvider.PENDING).readText())
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    /** I server e le scelte di una casella arrivata dal backup, per provarli per primi. */
    private fun restoredSettings(address: String): Pair<ServerSettings, Int>? = restored().firstOrNull { it.optString("email").equals(address, ignoreCase = true) }?.let { o ->
        runCatching {
            fun srv(j: org.json.JSONObject) = Server(j.getString("host"), j.getInt("port"), Security.valueOf(j.getString("sec")))
            ServerSettings(srv(o.getJSONObject("imap")), srv(o.getJSONObject("smtp")), o.optString("user").ifBlank { null }) to o.optInt("tint")
        }.getOrNull()
    }

    private fun forgetRestored(address: String) {
        val file = java.io.File(context.filesDir, eu.eurise.mail.backup.MailBackupProvider.PENDING)
        val left = restored().filterNot { it.optString("email").equals(address, ignoreCase = true) }
        if (left.isEmpty()) file.delete() else file.writeText(org.json.JSONArray(left).toString())
    }

    /** Collega: trova i server, prova l'accesso, salva la casella con la password cifrata. */
    fun link(email: String, password: String, name: String, done: (LinkResult) -> Unit) {
        val address = email.trim()
        val domain = Providers.domainOf(address)
        when {
            !Providers.isValidEmail(address) -> return done(LinkResult.Failed(R.string.ml_err_address))
            domain in Providers.NEEDS_OAUTH -> return done(LinkResult.Failed(R.string.ml_err_oauth))
            domain in Providers.CLOSED -> return done(LinkResult.Failed(R.string.ml_err_closed))
            accounts.any { it.email.equals(address, ignoreCase = true) } -> return done(LinkResult.Failed(R.string.ml_err_exists))
        }
        scope.launch {
            val result = withContext<LinkResult>(Dispatchers.IO) {
                val secret = Vault.seal(password)
                val fromBackup = restoredSettings(address)
                val candidates = buildList {
                    fromBackup?.let { add(it.first) }
                    Providers.known(address)?.let { add(it.settings) }
                    autoconfig(address)?.let { add(it) }
                    addAll(Providers.guesses(address))
                }.distinct()
                var lastAuth = false
                for (settings in candidates) {
                    val tint = fromBackup?.second ?: ((accounts.size) % ACCOUNT_TINTS.size)
                    val label = Providers.known(address)?.name ?: domain.substringBefore('.').replaceFirstChar { it.uppercase() }
                    val account = Account(UUID.randomUUID().toString(), address, name.trim(), label, settings, secret, tint)
                    val outcome = runCatching { MailClient(account).check() }
                    if (outcome.isSuccess) return@withContext LinkResult.Done(account)
                    val e = outcome.exceptionOrNull()
                    if (e is javax.mail.AuthenticationFailedException) { lastAuth = true; break }
                }
                LinkResult.Failed(if (lastAuth) R.string.ml_err_password else R.string.ml_err_servers)
            }
            if (result is LinkResult.Done) {
                forgetRestored(address)
                accounts += result.account
                store.save(accounts.toList())
                SyncWorker.schedule(context)
                refresh()
            }
            done(result)
        }
    }

    fun remove(a: Account) {
        accounts.remove(a)
        store.save(accounts.toList())
        messages.removeAll { it.accountId == a.id }
        cacheFile(a.id).delete()
        if (accounts.isEmpty()) SyncWorker.cancel(context)
    }

    /**
     * Le impostazioni pubbliche di Thunderbird per il dominio (riceve solo il dominio, mai l'indirizzo), poi quelle che il
     * dominio pubblica da sé.
     */
    private fun autoconfig(email: String): ServerSettings? {
        val d = Providers.domainOf(email)
        val urls = listOf("https://autoconfig.thunderbird.net/v1.1/$d", "https://autoconfig.$d/mail/config-v1.1.xml")
        for (u in urls) {
            val xml = runCatching {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "EuriseEmail")
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
            }.getOrNull() ?: continue
            Providers.parseAutoconfig(xml, email)?.let { return it }
        }
        return null
    }
}

/** Il riferimento di un messaggio nella coda dello Studio. */
internal fun studioRef(m: Summary) = "mail:${m.accountId}:${m.uid}"

/** Le email non lette dei pazienti vanno nella coda dello Studio (con l'oggetto; il testo quando le apri). */
internal fun offerToStudio(context: Context, known: StudioContract.Known, list: List<Summary>) {
    list.filter { !it.seen && known.email(it.fromAddress) }.forEach { m ->
        StudioContract.offer(context, Channel.Email, m.fromAddress, m.subject.ifBlank { "—" }, studioRef(m), m.date)
    }
}

/** La posta ricevuta tenuta sul telefono (solo intestazioni), per l'elenco e per la Chat («Una persona, tutti i canali»). */
object MailCache {
    fun file(context: Context, id: String) = File(context.cacheDir, "posta_$id.json")

    fun read(context: Context, id: String): List<Summary> = runCatching {
        val a = JSONArray(file(context, id).readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Summary(
                id, o.getLong("uid"), o.optString("fn"), o.optString("fa"), o.optString("s"), o.getLong("d"),
                o.optBoolean("seen"), runCatching { Kind.valueOf(o.optString("k")) }.getOrDefault(Kind.People), o.optBoolean("ans"),
            )
        }
    }.getOrDefault(emptyList())
}
