package eu.eurise.core.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Scelte della sincronizzazione. [notesAccount] è l'account dove vanno le note (null: solo sul telefono). */
data class CloudPrefs(
    val notesAccount: String? = null,
    val notesEnabled: Boolean = true,
    /** Attività sullo stesso account delle note; spente finché la persona non le accende. */
    val tasksEnabled: Boolean = false,
    val wifiOnly: Boolean = false,
    val lastSync: Long? = null,
    val lastError: String? = null,
)

/** Una riga di "Ultimi passaggi". */
data class SyncEvent(val time: Long, val text: String)

/** Un lavoro di sincronizzazione (le note, più avanti le attività…), registrato dall'app all'avvio. */
interface SyncJob {
    /** Esegue il lavoro; restituisce le righe da mostrare in "Ultimi passaggi". */
    suspend fun run(context: Context, cloud: CloudSync): List<String>
}

/**
 * Regia della sincronizzazione: scelte, registro, lavori e pianificazione in background con WorkManager
 * (ogni 30 minuti, e subito dopo una modifica). Tutto resta valido anche se l'app è chiusa.
 */
class CloudSync private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    val accounts: CloudAccounts = CloudAccounts.get(context)

    private val _prefs = MutableStateFlow(loadPrefs())
    val settings: StateFlow<CloudPrefs> = _prefs.asStateFlow()

    private val _events = MutableStateFlow(loadEvents())
    val events: StateFlow<List<SyncEvent>> = _events.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val lock = Mutex()

    fun update(transform: (CloudPrefs) -> CloudPrefs) {
        val next = transform(_prefs.value)
        _prefs.value = next
        prefs.edit()
            .putString("notesAccount", next.notesAccount)
            .putBoolean("notesEnabled", next.notesEnabled)
            .putBoolean("tasksEnabled", next.tasksEnabled)
            .putBoolean("wifiOnly", next.wifiOnly)
            .apply()
        schedule()
    }

    /** Esegue subito tutti i lavori registrati, uno per volta. Vero se è andato tutto bene. */
    suspend fun runNow(): Boolean = lock.withLock {
        if (jobs.isEmpty()) return true
        _running.value = true
        try {
            val lines = mutableListOf<String>()
            for (job in jobs) lines += job.run(context, this)
            record(lines, error = null)
            true
        } catch (e: Exception) {
            record(emptyList(), error = describe(e))
            false
        } finally {
            _running.value = false
        }
    }

    /** Chiede una sincronizzazione a breve (dopo una modifica): più richieste ravvicinate diventano una. */
    fun requestSoon() {
        if (!anythingToSync()) return
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(8, TimeUnit.SECONDS)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, work)
    }

    /** Pianifica il controllo periodico, oppure lo toglie se non c'è niente da sincronizzare. */
    fun schedule() {
        val wm = WorkManager.getInstance(context)
        if (!anythingToSync()) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val work = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(constraints()).build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    private fun anythingToSync(): Boolean {
        val p = _prefs.value
        return (p.notesEnabled || p.tasksEnabled) && accounts.get(p.notesAccount) != null
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (_prefs.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private fun record(lines: List<String>, error: String?) {
        val now = System.currentTimeMillis()
        val added = lines.map { SyncEvent(now, it) }
        val events = (added.asReversed() + _events.value).take(MAX_EVENTS)
        _events.value = events
        _prefs.value = _prefs.value.copy(lastSync = if (error == null) now else _prefs.value.lastSync, lastError = error)
        prefs.edit()
            .putString("events", JSONArray(events.map { JSONObject().put("t", it.time).put("x", it.text) }).toString())
            .apply {
                if (error == null) putLong("lastSync", now)
                if (error == null) remove("lastError") else putString("lastError", error)
            }
            .apply()
    }

    private fun describe(e: Exception): String = when {
        e is DavException && (e.code == 401 || e.code == 403) -> "auth"
        e is DavException && e.code == 507 -> "full"
        e is java.io.IOException -> "network"
        else -> "other"
    }

    private fun loadPrefs() = CloudPrefs(
        notesAccount = prefs.getString("notesAccount", null),
        notesEnabled = prefs.getBoolean("notesEnabled", true),
        tasksEnabled = prefs.getBoolean("tasksEnabled", false),
        wifiOnly = prefs.getBoolean("wifiOnly", false),
        lastSync = prefs.getLong("lastSync", 0L).takeIf { it > 0 },
        lastError = prefs.getString("lastError", null),
    )

    private fun loadEvents(): List<SyncEvent> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> SyncEvent(o.getLong("t"), o.getString("x")) } }
    }.getOrDefault(emptyList())

    companion object {
        private const val NOW = "eurise-sync-now"
        private const val PERIODIC = "eurise-sync-periodic"
        private const val MAX_EVENTS = 30

        private val jobs = mutableListOf<SyncJob>()

        /** Registra un lavoro; da chiamare in `Application.onCreate`, prima che parta qualsiasi sincronizzazione. */
        fun register(job: SyncJob) {
            if (jobs.none { it::class == job::class }) jobs += job
        }

        @Volatile private var instance: CloudSync? = null

        fun get(context: Context): CloudSync =
            instance ?: synchronized(this) { instance ?: CloudSync(context.applicationContext).also { instance = it } }
    }
}

/** Il lavoro in background di WorkManager: esegue la sincronizzazione e, se la rete manca, riprova più tardi. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (CloudSync.get(applicationContext).runNow()) Result.success() else Result.retry()
}
