package eu.eurise.feature.drive

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Lo stato di un file da un lato: [stamp] cambia quando cambia il file. */
data class Side(val stamp: String, val size: Long?)

/** Com'erano i due lati alla fine dell'ultimo allineamento. */
data class Known(val local: String, val remote: String)

sealed interface SyncAction {
    val path: String
    data class Upload(override val path: String) : SyncAction
    data class Download(override val path: String) : SyncAction
    data class DeleteLocal(override val path: String) : SyncAction
    data class DeleteRemote(override val path: String) : SyncAction
    /** Cambiato da entrambe le parti: si tengono tutte e due le versioni. */
    data class Conflict(override val path: String) : SyncAction
    /** Già uguali: basta ricordarlo. */
    data class Record(override val path: String) : SyncAction
}

/** Confronta i due lati con l'ultimo stato noto: solo così si distingue un file cancellato da uno nuovo. */
object SyncPlanner {
    fun plan(local: Map<String, Side>, remote: Map<String, Side>, known: Map<String, Known>): List<SyncAction> =
        (local.keys + remote.keys).sorted().mapNotNull { p ->
            val l = local[p]
            val r = remote[p]
            val k = known[p]
            when {
                l != null && r != null -> {
                    val sameSize = l.size != null && l.size == r.size
                    val localChanged = k == null || l.stamp != k.local
                    val remoteChanged = k == null || r.stamp != k.remote
                    when {
                        localChanged && remoteChanged -> if (sameSize) SyncAction.Record(p) else SyncAction.Conflict(p)
                        localChanged -> SyncAction.Upload(p)
                        remoteChanged -> SyncAction.Download(p)
                        else -> null
                    }
                }
                l != null -> if (k != null && l.stamp == k.local) SyncAction.DeleteLocal(p) else SyncAction.Upload(p)
                r != null -> if (k != null && r.stamp == k.remote) SyncAction.DeleteRemote(p) else SyncAction.Download(p)
                else -> null
            }
        }

    /** Freno: una scheda smontata o un account svuotato non devono propagare la cancellazione di tutto. */
    fun tooManyDeletions(actions: List<SyncAction>, known: Int): Boolean {
        val deletions = actions.count { it is SyncAction.DeleteLocal || it is SyncAction.DeleteRemote }
        return deletions > maxOf(10, known * 3 / 10)
    }

    /** "foto.jpg" diventa "foto (conflitto).jpg". */
    fun conflictName(path: String): String {
        val slash = path.lastIndexOf('/') + 1
        val dot = path.lastIndexOf('.')
        return if (dot > slash) path.substring(0, dot) + " (conflitto)" + path.substring(dot) else "$path (conflitto)"
    }
}

enum class SyncResult { Never, Ok, Failed, Guarded }

data class SyncPair(val id: String, val local: String, val account: String, val label: String, val remote: String, val state: SyncResult, val at: Long)

/** Tiene allineate cartelle del telefono e del cloud. Solo su richiesta della persona, cartella per cartella. */
class FolderSync(context: Context, private val data: DriveData) {
    private val prefs = context.getSharedPreferences("drive_sync", Context.MODE_PRIVATE)
    private val accounts = CloudAccounts.get(context)
    private val root get() = Environment.getExternalStorageDirectory()

    private fun load(): JSONArray = JSONArray(prefs.getString(KEY, "[]"))

    private fun save(array: JSONArray) { prefs.edit().putString(KEY, array.toString()).apply() }

    fun pairs(): List<SyncPair> {
        val a = load()
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            SyncPair(
                it.getString("id"), it.getString("local"), it.getString("account"), it.getString("label"), it.getString("remote"),
                runCatching { SyncResult.valueOf(it.optString("state")) }.getOrDefault(SyncResult.Never), it.optLong("at"),
            )
        }
    }

    suspend fun add(localPath: String, place: Place) {
        val id = UUID.randomUUID().toString()
        val remote = "Sincronizzati/" + localPath.substringAfterLast('/')
        val entry = JSONObject().put("id", id).put("local", localPath).put("account", place.account).put("label", place.label).put("remote", remote)
        save(load().put(entry))
        run(id, force = false)
    }

    fun remove(id: String) {
        val a = load()
        save(JSONArray((0 until a.length()).map { a.getJSONObject(it) }.filter { it.getString("id") != id }))
        prefs.edit().remove("known_$id").apply()
    }

    suspend fun runAll() { pairs().forEach { run(it.id, force = false) } }

    /** Con [force] accetta le eliminazioni fermate dal freno, dopo che la persona ha controllato. */
    suspend fun run(id: String, force: Boolean) {
        val pair = pairs().firstOrNull { it.id == id } ?: return
        val result = try {
            withContext(Dispatchers.IO) { align(pair, force) }
        } catch (e: java.io.IOException) {
            SyncResult.Failed
        } catch (e: DavException) {
            SyncResult.Failed
        }
        val a = load()
        (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("id") == id }?.let {
            it.put("state", result.name)
            if (result == SyncResult.Ok) it.put("at", System.currentTimeMillis())
        }
        save(a)
    }

    private suspend fun align(pair: SyncPair, force: Boolean): SyncResult {
        val dav = accounts.dav(pair.account) ?: return SyncResult.Failed
        val folder = File(root, pair.local)
        // Cartella assente o permesso tolto: non è una cancellazione di massa, è un motivo per non fare nulla.
        if (!folder.isDirectory || folder.listFiles() == null) return SyncResult.Failed
        dav.mkdirs(pair.remote)
        val local = HashMap<String, Side>()
        walkLocal(folder, "", local)
        val remote = HashMap<String, Side>()
        walkRemote(dav, pair.remote, "", remote)
        val known = loadKnown(pair.id)
        val actions = SyncPlanner.plan(local, remote, known)
        if (!force && SyncPlanner.tooManyDeletions(actions, known.size)) return SyncResult.Guarded

        val next = HashMap(known)
        fun stamp(file: File) = file.lastModified().toString() + ":" + file.length()
        suspend fun remember(path: String) {
            next[path] = Known(stamp(File(folder, path)), dav.stat(pair.remote + "/" + path)?.etag.orEmpty())
        }
        suspend fun ensureRemoteParent(path: String) {
            if ('/' in path) dav.mkdirs(pair.remote + "/" + path.substringBeforeLast('/'))
        }
        var failed = false
        for (action in actions) {
            val p = action.path
            try {
                when (action) {
                    is SyncAction.Upload -> { ensureRemoteParent(p); dav.upload(pair.remote + "/" + p, File(folder, p)); remember(p) }
                    is SyncAction.Download -> { dav.download(pair.remote + "/" + p, File(folder, p)); remember(p) }
                    is SyncAction.Record -> remember(p)
                    is SyncAction.Conflict -> {
                        // Il file del cloud diventa una copia sul telefono; al giro dopo sale anche quella.
                        dav.download(pair.remote + "/" + p, File(folder, SyncPlanner.conflictName(p)))
                        dav.upload(pair.remote + "/" + p, File(folder, p))
                        remember(p)
                    }
                    is SyncAction.DeleteLocal -> {
                        // Il file è sparito dal cloud: qui finisce nel cestino di Eurise, non si perde.
                        data.delete(DriveItem(data.phone, pair.local + "/" + p, p.substringAfterLast('/'), false, File(folder, p).length(), null))
                        next.remove(p)
                    }
                    is SyncAction.DeleteRemote -> { dav.delete(pair.remote + "/" + p); next.remove(p) }
                }
            } catch (e: java.io.IOException) {
                failed = true
            } catch (e: DavException) {
                failed = true
            }
        }
        // Salvando anche quando qualche file è fallito, al giro dopo si riprende solo da quelli.
        storeKnown(pair.id, next)
        return if (failed) SyncResult.Failed else SyncResult.Ok
    }

    private fun walkLocal(dir: File, prefix: String, out: MutableMap<String, Side>) {
        dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }.forEach { f ->
            val path = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
            if (f.isDirectory) walkLocal(f, path, out) else out[path] = Side(f.lastModified().toString() + ":" + f.length(), f.length())
        }
    }

    private suspend fun walkRemote(dav: WebDav, base: String, prefix: String, out: MutableMap<String, Side>) {
        val at = if (prefix.isEmpty()) base else "$base/$prefix"
        dav.list(at).filterNot { it.name.startsWith(".") }.forEach { e ->
            val path = if (prefix.isEmpty()) e.name else "$prefix/${e.name}"
            if (e.folder) walkRemote(dav, base, path, out) else out[path] = Side(e.etag.orEmpty(), e.size)
        }
    }

    private fun loadKnown(id: String): Map<String, Known> {
        val json = JSONObject(prefs.getString("known_$id", "{}") ?: "{}")
        return json.keys().asSequence().associateWith { key ->
            val o = json.getJSONObject(key)
            Known(o.getString("l"), o.getString("r"))
        }
    }

    private fun storeKnown(id: String, known: Map<String, Known>) {
        val json = JSONObject()
        known.forEach { (k, v) -> json.put(k, JSONObject().put("l", v.local).put("r", v.remote)) }
        prefs.edit().putString("known_$id", json.toString()).apply()
    }

    companion object {
        private const val KEY = "pairs"
        private const val WORK = "drive-folder-sync"

        /** Ogni 6 ore e solo in Wi-Fi: "Allinea adesso" resta per quando serve subito. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FolderSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class FolderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        FolderSync(app, DriveData(app)).runAll()
        return Result.success()
    }
}
