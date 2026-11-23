package eu.eurise.mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Invio all'ora giusta (tavola MailScriviLavoro): l'email resta sul telefono, nella cartella privata dell'app, con le
 * copie degli allegati, finché un lavoro di WorkManager (solo con la rete) non la manda all'ora scelta. Niente passa da
 * server di terzi: si manda con la propria casella, come un invio normale. Annullare toglie lavoro e file.
 */
object MailOutbox {
    data class Item(val id: String, val accountId: String, val to: String, val cc: String, val subject: String, val body: String,
                    val inReplyTo: String?, val at: Long, val waitDays: Int, val name: String)

    private fun root(c: Context) = File(c.filesDir, "in_uscita").apply { mkdirs() }
    private fun dir(c: Context, id: String) = File(root(c), id)
    private fun work(id: String) = "invio-$id"

    fun list(c: Context): List<Item> = root(c).listFiles().orEmpty().mapNotNull { d -> read(File(d, "email.json")) }.sortedBy { it.at }

    fun files(c: Context, id: String): List<File> = File(dir(c, id), "allegati").listFiles().orEmpty().sortedBy { it.name }

    private fun read(f: File): Item? = runCatching {
        val o = JSONObject(f.readText())
        Item(o.getString("id"), o.getString("account"), o.getString("to"), o.optString("cc"), o.optString("subject"), o.optString("body"),
            o.optString("reply").ifEmpty { null }, o.getLong("at"), o.optInt("wait"), o.optString("name"))
    }.getOrNull()

    /** Mette in uscita; gli allegati vanno prima copiati in [attachmentDir]. */
    fun schedule(c: Context, item: Item) {
        val d = dir(c, item.id).apply { mkdirs() }
        File(d, "email.json").writeText(JSONObject().put("id", item.id).put("account", item.accountId).put("to", item.to).put("cc", item.cc)
            .put("subject", item.subject).put("body", item.body).put("reply", item.inReplyTo ?: "").put("at", item.at).put("wait", item.waitDays)
            .put("name", item.name).toString())
        val delay = (item.at - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .setInputData(workDataOf("id" to item.id))
            .addTag("in_uscita")
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork(work(item.id), ExistingWorkPolicy.REPLACE, req)
    }

    fun attachmentDir(c: Context, id: String) = File(dir(c, id), "allegati").apply { mkdirs() }

    fun cancel(c: Context, id: String) {
        WorkManager.getInstance(c).cancelUniqueWork(work(id))
        dir(c, id).deleteRecursively()
    }

    /** «Manda ora»: lo stesso lavoro senza attesa. */
    fun now(c: Context, id: String) { read(File(dir(c, id), "email.json"))?.let { schedule(c, it.copy(at = System.currentTimeMillis())) } }

    internal fun remove(c: Context, id: String) = dir(c, id).deleteRecursively()

    /** Le attese «se non risponde» nate da un invio fatto ad app chiusa: le raccoglie il modello alla prossima apertura. */
    internal fun addWaiting(c: Context, w: MailWork.Waiting) {
        val p = c.getSharedPreferences("mail_waiting", Context.MODE_PRIVATE)
        val list = MailWork.decode(p.getString("list", null)) + w
        p.edit().putString("list", MailWork.encode(list)).apply()
    }
}

/** Manda un'email in uscita con la casella scelta; se non riesce riprova più tardi (al massimo per un giorno). */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val id = inputData.getString("id") ?: return Result.failure()
        val item = MailOutbox.list(c).firstOrNull { it.id == id } ?: return Result.success()
        val account = AccountStore(c).load().firstOrNull { it.id == item.accountId } ?: run { MailOutbox.remove(c, id); return Result.failure() }
        val ok = runCatching { MailClient(account).send(item.to, item.cc, item.subject, item.body, item.inReplyTo, MailOutbox.files(c, id)) }.isSuccess
        if (!ok) return if (runAttemptCount < 12) Result.retry() else Result.failure()
        if (item.waitDays > 0) {
            val now = System.currentTimeMillis()
            MailOutbox.addWaiting(c, MailWork.Waiting("w$now", item.to.split(',').first().trim(), item.name, item.subject, now,
                java.time.LocalDate.now().plusDays(item.waitDays.toLong()).toEpochDay()))
        }
        MailOutbox.remove(c, id)
        return Result.success()
    }
}

