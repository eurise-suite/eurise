package eu.eurise.password.data

import android.content.Context
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.NextcloudLogin
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.ImportCounts
import eu.eurise.password.kdbx.Kdbx
import eu.eurise.password.kdbx.KdbxFile
import eu.eurise.password.kdbx.Vault
import eu.eurise.password.kdbx.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VaultState {
    data object Loading : VaultState
    data object Empty : VaultState
    data object Locked : VaultState
    data class Open(val entries: List<VaultEntry>) : VaultState
}

/** Un accesso appena digitato che Android propone di salvare. */
class PendingLogin(val username: String, val password: String, val webDomain: String?, val packageName: String)

sealed interface SyncState {
    data object Off : SyncState
    data object Working : SyncState
    data class Done(val at: Long) : SyncState
    /** In attesa di rete o per un errore: la copia sul telefono è comunque salvata. */
    data class Pending(val reason: SyncProblem) : SyncState
}

enum class SyncProblem { Network, Rejected, OtherPassword }

/**
 * La cassaforte: il file .kdbx sta nella memoria privata dell'app e, solo se l'utente lo accende, una copia del file
 * (già cifrato) va su Murena. La chiave resta in memoria finché la cassaforte è aperta e [lock] la cancella.
 */
class VaultRepository private constructor(context: Context) {
    private val file = File(context.filesDir, "cassaforte.kdbx")
    private val prefs = context.getSharedPreferences("password_sync", Context.MODE_PRIVATE)
    private val accounts = CloudAccounts.get(context)
    private val mutex = Mutex()

    private var opened: KdbxFile? = null
    private var vault: Vault? = null
    private var compositeKey: ByteArray? = null

    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    val state: StateFlow<VaultState> = _state.asStateFlow()
    private val _sync = MutableStateFlow<SyncState>(SyncState.Off)
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    val syncAccountName: String? get() = accounts.get(prefs.getString(KEY_ACCOUNT, null))?.name
    val syncEnabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false) && syncAccountName != null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lockTask = Runnable { lock() }
    private var leftAt = 0L

    /** Passa dal servizio di compilazione al foglio di salvataggio senza uscire dalla memoria dell'app. */
    @Volatile var pendingLogin: PendingLogin? = null

    /** Un otpauth:// arrivato da un lettore di QR, in attesa che la cassaforte sia aperta. */
    val pendingOtp = MutableStateFlow<String?>(null)

    /** Una schermata dell'app è in primo piano. Se si è stati via troppo, il timer può aver ritardato: si blocca ora. */
    fun enter() {
        mainHandler.removeCallbacks(lockTask)
        expireIfDue()
        leftAt = 0L
    }

    /** Si esce dall'app: fra due minuti la cassaforte si blocca, anche se è stata aperta dal foglio di compilazione. */
    fun leave() {
        leftAt = android.os.SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(lockTask)
        mainHandler.postDelayed(lockTask, AUTO_LOCK_MS)
    }

    fun expireIfDue() {
        if (leftAt != 0L && android.os.SystemClock.elapsedRealtime() - leftAt >= AUTO_LOCK_MS) lock()
    }

    /** Copia della chiave, per chiuderla con l'impronta; null se la cassaforte è bloccata. */
    fun currentKey(): ByteArray? = compositeKey?.copyOf()

    /** Apertura con la chiave recuperata tramite impronta. */
    suspend fun unlockWithKey(key: ByteArray) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            attach(Kdbx.read(bytes, key), key)
        }
        syncNow()
    }

    suspend fun load() = mutex.withLock {
        if (opened != null) return@withLock
        _state.value = withContext(Dispatchers.IO) { if (file.exists()) VaultState.Locked else VaultState.Empty }
        refreshSyncIdle()
    }

    suspend fun create(password: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = Kdbx.compositeKey(password)
            val created = Kdbx.create(key, "Eurise")
            persist(created)
            attach(created, key)
        }
    }

    /** Apre la cassaforte. Lancia [eu.eurise.password.kdbx.KdbxException] se la frase è errata o il file non va. */
    suspend fun unlock(password: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = Kdbx.compositeKey(password)
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            attach(Kdbx.read(bytes, key), key)
        }
        syncNow()
    }

    /** Adotta un file .kdbx scelto dal telefono, dopo aver verificato che la frase lo apra. */
    suspend fun importFile(bytes: ByteArray, password: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val key = Kdbx.compositeKey(password)
            val read = Kdbx.read(bytes, key)
            withContext(Dispatchers.IO) { writeAtomically(bytes) }
            attach(read, key)
        }
    }

    fun lock() {
        opened = null
        vault = null
        compositeKey?.fill(0)
        compositeKey = null
        _state.value = if (file.exists()) VaultState.Locked else VaultState.Empty
        refreshSyncIdle()
    }

    suspend fun saveEntry(draft: EntryDraft, uuid: String?): String? {
        val id = edit { it.save(draft, uuid) }
        syncNow()
        return id
    }

    suspend fun deleteEntry(uuid: String) {
        edit { it.delete(uuid) }
        syncNow()
    }

    suspend fun importItems(drafts: List<EntryDraft>): ImportCounts? {
        val counts = edit { it.importAll(drafts) }
        syncNow()
        return counts
    }

    suspend fun linkApp(uuid: String, packageName: String) {
        edit { it.linkApp(uuid, packageName) }
        syncNow()
    }

    /** L'uso di una voce si salva sul telefono ma da solo non fa partire una sincronizzazione. */
    suspend fun markUsed(uuid: String) {
        edit(markDirty = false) { it.touch(uuid) }
    }

    private suspend fun <T> edit(markDirty: Boolean = true, change: (Vault) -> T): T? = withContext(Dispatchers.Default) {
        mutex.withLock {
            val v = vault ?: return@withLock null
            val result = change(v)
            persist(opened!!)
            if (markDirty) prefs.edit().putBoolean(KEY_DIRTY, true).apply()
            publish()
            result
        }
    }

    // --- Sincronizzazione ---

    suspend fun startMurenaLogin(): NextcloudLogin.Start = NextcloudLogin().start(NextcloudLogin.MURENA_SERVER)

    /** Aspetta l'approvazione nel browser e registra l'account; falso se il tempo scade. */
    suspend fun finishMurenaLogin(start: NextcloudLogin.Start): Boolean {
        val result = NextcloudLogin().await(start) ?: return false
        val id = accounts.add(
            kind = CloudKind.Murena,
            name = result.loginName,
            user = result.loginName,
            davUrl = CloudAccounts.nextcloudDav(result.server, result.loginName),
            password = result.appPassword,
        )
        prefs.edit().putString(KEY_ACCOUNT, id).remove(KEY_ETAG).putBoolean(KEY_DIRTY, true).apply()
        return true
    }

    /** Vero se su Murena c'è già una cassaforte, per il telefono nuovo. */
    suspend fun remoteExists(): Boolean = runCatching { dav()?.stat(REMOTE_FILE) != null }.getOrDefault(false)

    /** Telefono nuovo: scarica il file e accende la sincronizzazione; poi si apre con la frase principale. */
    suspend fun adoptRemote(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dav = dav() ?: return@withLock false
            val entry = dav.stat(REMOTE_FILE) ?: return@withLock false
            val bytes = dav.getBytes(REMOTE_FILE) ?: return@withLock false
            writeAtomically(bytes)
            prefs.edit().putBoolean(KEY_ENABLED, true).putString(KEY_ETAG, entry.etag).putBoolean(KEY_DIRTY, false).apply()
            _state.value = VaultState.Locked
            refreshSyncIdle()
            true
        }
    }

    suspend fun enableSync() {
        prefs.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_DIRTY, true).apply()
        syncNow()
    }

    /** Spegne la sincronizzazione: il file già su Murena resta dov'è, ma l'app non lo tocca più. */
    fun disableSync() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        _sync.value = SyncState.Off
    }

    /** Porta le due copie allo stesso punto; senza rete resta tutto salvato qui e si riprova alla prossima modifica. */
    suspend fun syncNow() {
        if (!syncEnabled) return
        _sync.value = SyncState.Working
        _sync.value = withContext(Dispatchers.Default) {
            mutex.withLock {
                try {
                    if (runSync()) SyncState.Done(System.currentTimeMillis()) else SyncState.Pending(SyncProblem.Rejected)
                } catch (e: eu.eurise.password.kdbx.KdbxException) {
                    SyncState.Pending(SyncProblem.OtherPassword)
                } catch (e: DavException) {
                    SyncState.Pending(if (e.code == 401 || e.code == 403) SyncProblem.Rejected else SyncProblem.Network)
                } catch (e: java.io.IOException) {
                    SyncState.Pending(SyncProblem.Network)
                }
            }
        }
    }

    private suspend fun runSync(): Boolean {
        val current = opened ?: return false
        val local = vault ?: return false
        val key = compositeKey ?: return false
        val dav = dav() ?: return false
        dav.mkdirs(REMOTE_DIR)
        repeat(3) {
            val remote = dav.stat(REMOTE_FILE)
            val known = prefs.getString(KEY_ETAG, null)
            var dirty = prefs.getBoolean(KEY_DIRTY, false)
            try {
                if (remote == null) {
                    remember(dav.putBytesIf(REMOTE_FILE, Kdbx.write(current), onlyIfNew = true))
                    return true
                }
                if (remote.etag != known) {
                    val bytes = dav.getBytes(REMOTE_FILE) ?: return@repeat
                    val merge = local.mergeFrom(Vault(Kdbx.read(bytes, key).document))
                    if (merge.localChanged) {
                        persist(current)
                        publish()
                    }
                    dirty = merge.remoteBehind
                    if (!dirty) {
                        remember(remote.etag)
                        return true
                    }
                }
                if (dirty) remember(dav.putBytesIf(REMOTE_FILE, Kdbx.write(current), ifMatch = remote.etag))
                return true
            } catch (e: DavException) {
                // 412: il file è cambiato mentre lo unavamo, si ricomincia; gli altri errori escono.
                if (e.code != 412) throw e
            }
        }
        throw DavException(412, "Il file cambia continuamente")
    }

    private fun remember(etag: String?) {
        prefs.edit().putString(KEY_ETAG, etag).putBoolean(KEY_DIRTY, false).apply()
    }

    private fun dav() = accounts.dav(prefs.getString(KEY_ACCOUNT, null))

    private fun refreshSyncIdle() {
        if (!syncEnabled) _sync.value = SyncState.Off
    }

    // --- File ---

    private fun attach(read: KdbxFile, key: ByteArray) {
        opened = read
        vault = Vault(read.document)
        compositeKey = key
        publish()
    }

    private fun publish() {
        _state.value = VaultState.Open(vault?.entries().orEmpty())
    }

    private suspend fun persist(target: KdbxFile) {
        val bytes = Kdbx.write(target)
        withContext(Dispatchers.IO) { writeAtomically(bytes) }
    }

    /** Scrive accanto e poi sostituisce: un'interruzione non può lasciare un file a metà. La versione prima resta in .bak. */
    private fun writeAtomically(bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out -> out.write(bytes); out.fd.sync() }
        if (file.exists()) file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
        check(tmp.renameTo(file)) { "Salvataggio non riuscito" }
    }

    companion object {
        private const val AUTO_LOCK_MS = 2 * 60_000L
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_ETAG = "etag"
        private const val KEY_DIRTY = "dirty"
        private const val REMOTE_DIR = "Eurise/Password"
        private const val REMOTE_FILE = "$REMOTE_DIR/eurise.kdbx"

        @Volatile private var instance: VaultRepository? = null

        fun get(context: Context): VaultRepository =
            instance ?: synchronized(this) { instance ?: VaultRepository(context.applicationContext).also { instance = it } }
    }
}
