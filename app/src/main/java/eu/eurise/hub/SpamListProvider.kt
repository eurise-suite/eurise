package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import eu.eurise.core.suite.SpamList
import eu.eurise.core.suite.SuiteAccess
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import eu.eurise.core.suite.SpamListContract as C

/**
 * L'elenco Eurise dei numeri molesti per Eurise Telefono ([C]), che non ha il permesso Internet: lo scarica questa app,
 * una volta a settimana e solo col Wi-Fi, se la persona l'ha acceso ([PrivacyService.SpamList], spento di base). Solo per
 * le app della suite: il controllo è qui, `call` non guarda il manifesto.
 */
class SpamListProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
        PrivacyCenter.init(context)
        when (method) {
            C.METHOD_ENABLE -> { PrivacyCenter.set(PrivacyService.SpamList, true); SpamListStore.schedule(context, now = true) }
            C.METHOD_DISABLE -> { PrivacyCenter.set(PrivacyService.SpamList, false); SpamListStore.stop(context) }
            C.METHOD_UPDATE -> SpamListStore.schedule(context, now = true)
            // Anche acceso dal centro privacy dell'app Eurise: il lavoro settimanale parte alla prima domanda del Telefono
            // (il primo giro di un lavoro periodico parte subito, appena c'è il Wi-Fi).
            C.METHOD_STATUS, C.METHOD_LIST -> SpamListStore.schedule(context, now = false)
            else -> return null
        }
        val on = PrivacyCenter.allowed(PrivacyService.SpamList)
        return Bundle().apply {
            putBoolean(C.KEY_ON, on)
            putLong(C.KEY_UPDATED, if (on) SpamListStore.updated(context) else 0L)
            putInt(C.KEY_COUNT, if (on) SpamListStore.count(context) else 0)
            if (on && method == C.METHOD_LIST) SpamListStore.text(context)?.let { putString(C.KEY_TEXT, it) }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** Il file scaricato, sul telefono, e i lavori che lo tengono aggiornato. */
object SpamListStore {
    /** Il file nel repository della suite, uguale per tutti. */
    const val URL = "https://raw.githubusercontent.com/eurise-suite/eurise/brand/data/spam/elenco.txt"
    private const val WEEKLY = "spam-list-weekly"
    private const val NOW = "spam-list-now"
    private const val PREFS = "spam_list"

    private fun file(context: Context) = File(context.filesDir, "spam_list.txt")
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun updated(context: Context): Long = prefs(context).getLong("updated", 0L)
    fun count(context: Context): Int = prefs(context).getInt("count", 0)
    fun text(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    private val wifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

    /** Il lavoro settimanale (se acceso) e, con [now], uno subito appena c'è il Wi-Fi. */
    fun schedule(context: Context, now: Boolean) {
        if (!PrivacyCenter.allowed(PrivacyService.SpamList)) return
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WEEKLY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SpamListWorker>(7, TimeUnit.DAYS).setConstraints(wifi).build(),
        )
        if (now) wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SpamListWorker>().setConstraints(wifi).build())
    }

    /** Spento: niente più scaricamenti e l'elenco si cancella. */
    fun stop(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WEEKLY)
        wm.cancelUniqueWork(NOW)
        file(context).delete()
        prefs(context).edit().clear().apply()
    }

    /** Scarica il file e lo tiene solo se è un elenco Eurise valido. */
    fun download(context: Context): Boolean {
        PrivacyCenter.init(context)
        if (!PrivacyCenter.allowed(PrivacyService.SpamList)) { stop(context); return true }
        PrivacyCenter.log(PrivacyService.SpamList)
        val conn = (URL(URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Eurise")
        }
        return try {
            if (conn.responseCode != 200) return false
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > SpamList.MAX_BYTES) return false
                }
                buf.toByteArray()
            }
            val text = String(bytes, Charsets.UTF_8)
            val entries = SpamList.parse(text) ?: return false
            val tmp = File(context.filesDir, "spam_list.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file(context))) { tmp.delete(); return false }
            prefs(context).edit().putLong("updated", System.currentTimeMillis()).putInt("count", entries.size).apply()
            true
        } catch (e: java.io.IOException) {
            false
        } finally {
            conn.disconnect()
        }
    }
}

/** Il lavoro che scarica l'elenco (col Wi-Fi); se non riesce riprova più tardi. */
class SpamListWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpamListStore.download(applicationContext) }) Result.success() else Result.retry()
}
