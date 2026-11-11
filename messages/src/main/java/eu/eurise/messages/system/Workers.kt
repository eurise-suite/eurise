package eu.eurise.messages.system

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.messages.data.SmsStore
import java.util.concurrent.TimeUnit

/** I codici di verifica si cancellano da soli dopo dieci minuti (si spegne nelle impostazioni di Messaggi). */
object Codes {
    const val MINUTES = 10L

    fun enabled(context: Context) = context.getSharedPreferences("messages", Context.MODE_PRIVATE).getBoolean("codes_cleanup", true)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences("messages", Context.MODE_PRIVATE).edit().putBoolean("codes_cleanup", on).apply()

    fun scheduleCleanup(context: Context, uri: Uri, body: String) {
        if (!enabled(context) || MessageInsights.code(body) == null) return
        val work = OneTimeWorkRequestBuilder<CodeCleanupWorker>().setInitialDelay(MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf("uri" to uri.toString())).build()
        WorkManager.getInstance(context).enqueue(work)
    }
}

class CodeCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        inputData.getString("uri")?.let { SmsStore.deleteSms(applicationContext, Uri.parse(it)) }
        return Result.success()
    }
}

/** Invio programmato: il messaggio parte all'ora scelta, anche con l'app chiusa. */
object Scheduled {
    fun send(context: Context, addresses: List<String>, text: String, at: Long) {
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
        val work = OneTimeWorkRequestBuilder<ScheduledSendWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("to" to addresses.joinToString("\n"), "text" to text)).addTag("scheduled").build()
        WorkManager.getInstance(context).enqueue(work)
    }
}

class ScheduledSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val to = inputData.getString("to").orEmpty().lines().filter { it.isNotBlank() }
        val text = inputData.getString("text").orEmpty()
        if (to.isNotEmpty() && text.isNotBlank()) Sender.sendSms(applicationContext, to, text)
        return Result.success()
    }
}
