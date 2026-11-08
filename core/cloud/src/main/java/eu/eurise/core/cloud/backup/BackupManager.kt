package eu.eurise.core.cloud.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.cloud.CloudAccounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ogni quanto si fa il backup da solo; [Manual] solo quando lo chiede l'utente. */
enum class BackupFrequency(val days: Int) { Daily(1), Weekly(7), Monthly(30), Manual(0) }

/** Dove vanno i backup: un account cloud collegato o una cartella. */
sealed interface BackupPlace {
    data class Cloud(val accountId: String) : BackupPlace
    data class Folder(val uri: String) : BackupPlace

    fun encode() = when (this) { is Cloud -> "dav:$accountId"; is Folder -> "saf:$uri" }

    companion object {
        fun decode(s: String?): BackupPlace? = when {
            s == null -> null
            s.startsWith("dav:") -> Cloud(s.removePrefix("dav:"))
            s.startsWith("saf:") -> Folder(s.removePrefix("saf:"))
            else -> null
        }
    }
}

data class BackupState(
    val configured: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.Weekly,
    val place: BackupPlace? = null,
    val lastAt: Long? = null,
    val lastSize: Long? = null,
    val lastError: String? = null,
    val running: Boolean = false,
)

/**
 * Il backup cifrato di Eurise. La chiave dei dati resta sul telefono chiusa nel Keystore, così i backup automatici
 * non chiedono la frase; nel file viaggia chiusa con la frase e con il codice di recupero ([BackupCrypto]).
 */
class BackupManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private fun load() = BackupState(
        configured = prefs.contains("key"),
        frequency = runCatching { BackupFrequency.valueOf(prefs.getString("frequency", null)!!) }.getOrDefault(BackupFrequency.Weekly),
        place = BackupPlace.decode(prefs.getString("place", null)),
        lastAt = prefs.getLong("last_at", 0).takeIf { it > 0 },
        lastSize = prefs.getLong("last_size", 0).takeIf { it > 0 },
        lastError = prefs.getString("last_error", null),
    )

    private fun refresh(running: Boolean = _state.value.running) { _state.value = load().copy(running = running) }

    /** Prima attivazione: chiavi nuove per la frase e il codice di recupero. Lento di proposito. */
    suspend fun setup(passphrase: String, recoveryCode: String) = withContext(Dispatchers.Default) {
        val keys = BackupCrypto.setup(passphrase.toCharArray(), recoveryCode)
        storeKeys(keys)
        refresh()
    }

    /** Cambia la frase segreta (serve quella attuale o il codice di recupero). Il codice resta lo stesso. */
    suspend fun changePassphrase(current: String, next: String): Boolean = withContext(Dispatchers.Default) {
        val header = header() ?: return@withContext false
        val dataKey = runCatching { BackupCrypto.unlock(header, current) }.getOrNull() ?: return@withContext false
        // Si tiene la chiusura per il codice di recupero già consegnato all'utente.
        storeKeys(BackupCrypto.Keys(dataKey, BackupCrypto.rewrapPassphrase(dataKey, next.toCharArray(), header)))
        true
    }

    fun setFrequency(f: BackupFrequency) {
        prefs.edit().putString("frequency", f.name).apply()
        refresh(); schedule(context)
    }

    fun setPlace(place: BackupPlace) {
        if (place is BackupPlace.Folder) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(Uri.parse(place.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        prefs.edit().putString("place", place.encode()).apply()
        refresh(); schedule(context)
    }

    /** Spegne il backup: niente più backup automatici; i file già fatti restano dove sono. Nessuna conferma. */
    fun turnOff() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
        refresh(); schedule(context)
    }

    fun target(place: BackupPlace? = _state.value.place): BackupTarget? = when (place) {
        is BackupPlace.Cloud -> CloudAccounts.get(context).dav(place.accountId)?.let { DavTarget(it) }
        is BackupPlace.Folder -> FolderTarget(context, Uri.parse(place.uri))
        null -> null
    }

    /**
     * Fa un backup: archivio, cifratura, caricamento, verifica (lo si rilegge e lo si apre), poi la regola dei tre
     * più il mensile e il trimestrale. I vecchi si toccano solo dopo la verifica.
     */
    suspend fun backupNow(): Result<BackupFile> = mutex.withLock {
        refresh(running = true)
        val result = runCatching {
            val keys = keys() ?: error("Backup non attivo")
            val target = target() ?: error("Nessuna destinazione")
            val now = System.currentTimeMillis()
            val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
            val file = withContext(Dispatchers.Default) { BackupCrypto.encrypt(keys, now, BackupArchive.create(context, now)) }
            val name = Retention.fileName(at, Tier.Recent)
            target.write(name, file)
            val back = target.read(name) ?: error("Il backup non si rilegge")
            withContext(Dispatchers.Default) { BackupCrypto.decrypt(BackupCrypto.parse(back), keys.dataKey) }
            val files = target.list()
            Retention.plan(files).forEach { a ->
                when (a) {
                    is RetentionAction.Delete -> target.delete(a.name)
                    is RetentionAction.Rename -> target.rename(a.from, a.to)
                }
            }
            prefs.edit().putLong("last_at", now).putLong("last_size", file.size.toLong()).remove("last_error").apply()
            BackupFile(name, at, Tier.Recent, file.size.toLong())
        }
        result.exceptionOrNull()?.let { e -> prefs.edit().putString("last_error", e.message ?: e.javaClass.simpleName).apply() }
        refresh(running = false)
        result
    }

    /** I backup presenti nella destinazione, dal più recente. */
    suspend fun list(place: BackupPlace? = _state.value.place): List<BackupFile> =
        runCatching { target(place)?.list().orEmpty().sortedByDescending { it.at } }.getOrDefault(emptyList())

    /**
     * Ripristina [name] da [place] con la frase o il codice: verifica tutto, prepara i file e tiene le chiavi per i
     * prossimi backup su questo telefono. L'app va riavviata ([restartApp]) perché i dati prendano il loro posto.
     */
    suspend fun restore(place: BackupPlace, name: String, secret: String) = withContext(Dispatchers.Default) {
        val bytes = target(place)?.read(name) ?: throw BackupCrypto.BadFile()
        restoreBytes(bytes, secret)
        prefs.edit().putString("place", place.encode()).apply()
        if (place is BackupPlace.Folder) setPlace(place)
        refresh()
    }

    /** Ripristino da un file scelto a mano. */
    suspend fun restoreBytes(bytes: ByteArray, secret: String) = withContext(Dispatchers.Default) {
        val parsed = BackupCrypto.parse(bytes)
        val dataKey = BackupCrypto.unlock(parsed.header, secret)
        val zip = BackupCrypto.decrypt(parsed, dataKey)
        BackupArchive.stage(context, zip)
        storeKeys(BackupCrypto.Keys(dataKey, parsed.header))
    }

    /** Riavvia l'app dopo un ripristino. */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    // --- chiavi ---------------------------------------------------------------

    private fun storeKeys(keys: BackupCrypto.Keys) {
        val h = keys.header
        prefs.edit()
            .putString("key", sealKey(keys.dataKey))
            .putInt("iter", h.iterations)
            .putString("salt_p", b64(h.saltPass)).putString("wrap_p", b64(h.wrapPass))
            .putString("salt_c", b64(h.saltCode)).putString("wrap_c", b64(h.wrapCode))
            .apply()
    }

    private fun header(): BackupCrypto.KeyHeader? {
        if (!prefs.contains("wrap_p")) return null
        return BackupCrypto.KeyHeader(
            prefs.getInt("iter", BackupCrypto.ITERATIONS),
            unb64(prefs.getString("salt_p", "")!!), unb64(prefs.getString("wrap_p", "")!!),
            unb64(prefs.getString("salt_c", "")!!), unb64(prefs.getString("wrap_c", "")!!),
        )
    }

    private fun keys(): BackupCrypto.Keys? {
        val header = header() ?: return null
        val key = prefs.getString("key", null)?.let { openKey(it) } ?: return null
        return BackupCrypto.Keys(key, header)
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return generator.generateKey()
    }

    private fun sealKey(key: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return b64(c.iv) + ":" + b64(c.doFinal(key))
    }

    private fun openKey(stored: String): ByteArray? = runCatching {
        val (iv, data) = stored.split(':', limit = 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, unb64(iv)))
        c.doFinal(unb64(data))
    }.getOrNull()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val ALIAS = "eurise-backup"
        private const val WORK = "eurise-backup"

        @Volatile private var instance: BackupManager? = null
        fun get(context: Context): BackupManager =
            instance ?: synchronized(this) { instance ?: BackupManager(context.applicationContext).also { instance = it } }

        /**
         * Il controllo quotidiano: con Wi-Fi e telefono in carica, e solo se è il momento secondo la frequenza.
         * Niente servizi sempre attivi.
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val s = get(context).state.value
            if (!s.configured || s.place == null || s.frequency == BackupFrequency.Manual) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true).build()
            val work = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, work)
        }
    }
}

/** Lavoro quotidiano: fa il backup se dall'ultimo è passato il tempo scelto. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = BackupManager.get(applicationContext)
        val s = manager.state.value
        val days = s.frequency.days
        if (!s.configured || days == 0) return Result.success()
        val due = s.lastAt == null || System.currentTimeMillis() - s.lastAt >= TimeUnit.DAYS.toMillis(days.toLong()) - TimeUnit.HOURS.toMillis(3)
        if (!due) return Result.success()
        return if (manager.backupNow().isSuccess) Result.success() else Result.retry()
    }
}
