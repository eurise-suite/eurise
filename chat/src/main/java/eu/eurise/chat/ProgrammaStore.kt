package eu.eurise.chat

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dove stanno sul telefono i messaggi programmati e quelli a scomparsa (tavola ChatProgramma), e chi li manda o li
 * cancella ad app chiusa. I file stanno fuori dal backup di Android (cartella `noBackup`): sono testi tuoi in attesa.
 */
object ProgrammaStore {
    private fun planFile(context: Context) = File(context.noBackupFilesDir, "chat_programmati.json")
    private fun fadeFile(context: Context) = File(context.noBackupFilesDir, "chat_scomparsa.json")

    @Synchronized fun planned(context: Context): List<Programma.Planned> =
        Programma.decodePlanned(runCatching { planFile(context).readText() }.getOrNull())

    /** [reschedule] falso solo dal lavoro stesso, che si riprogramma da sé alla fine. */
    @Synchronized fun savePlanned(context: Context, list: List<Programma.Planned>, reschedule: Boolean = true) {
        runCatching { planFile(context).writeText(Programma.encodePlanned(list)) }
        if (reschedule) PlannedWorker.schedule(context, Programma.next(list))
    }

    @Synchronized fun addPlanned(context: Context, p: Programma.Planned) = savePlanned(context, planned(context) + p)

    @Synchronized fun removePlanned(context: Context, id: String, reschedule: Boolean = true) =
        savePlanned(context, planned(context).filterNot { it.id == id }, reschedule)

    @Synchronized fun fading(context: Context): List<Programma.Fading> =
        Programma.decodeFading(runCatching { fadeFile(context).readText() }.getOrNull())

    @Synchronized fun saveFading(context: Context, list: List<Programma.Fading>) {
        runCatching { fadeFile(context).writeText(Programma.encodeFading(list)) }
    }

    @Synchronized fun addFading(context: Context, f: Programma.Fading) = saveFading(context, fading(context) + f)

    /** Le conferme di lettura di una tornata di /sync segnano letti i messaggi a scomparsa. */
    fun receipts(context: Context, account: Account, me: String, sync: org.json.JSONObject) {
        val read = Programma.receipts(sync, me)
        if (read.isEmpty()) return
        synchronized(this) {
            val before = fading(context)
            val after = Programma.markRead(before, account.name, read)
            if (after != before) saveFading(context, after)
        }
    }

    /**
     * Manda i messaggi programmati di [account] arrivati all'ora (cifrati se la stanza lo è, con [crypto]) e cancella dal
     * server quelli a scomparsa scaduti. Restituisce gli id dei programmati mandati.
     */
    suspend fun work(context: Context, account: Account, client: ChatClient, crypto: ChatCrypto?, reschedule: Boolean = true): List<String> {
        val now = System.currentTimeMillis()
        val sent = ArrayList<String>()
        Programma.due(planned(context), account.name, now).forEach { p ->
            val ok = runCatching {
                val id = sendNow(client, crypto, p.roomId, Programma.content(p.text, p.fadeMs), p.encrypted)
                if (p.fadeMs > 0) addFading(context, Programma.Fading(account.name, p.roomId, id, System.currentTimeMillis(), p.fadeMs))
            }.fold({ true }, { e -> e is MatrixException && e.status in listOf(403, 404) })
            if (ok) { removePlanned(context, p.id, reschedule); sent += p.id }
        }
        val gone = Programma.gone(fading(context), account.name, now)
        if (gone.isNotEmpty()) {
            val done = gone.filter { f ->
                runCatching { client.redact(f.roomId, f.eventId, null) }
                    .fold({ true }, { e -> e is MatrixException && e.status in listOf(403, 404) })
            }.toSet()
            synchronized(this) { saveFading(context, fading(context).filterNot { it in done }) }
        }
        return sent
    }

    /** Manda un messaggio e ne restituisce l'id (serve per la scomparsa). */
    suspend fun sendNow(client: ChatClient, crypto: ChatCrypto?, roomId: String, content: org.json.JSONObject, encrypted: Boolean): String =
        if (encrypted) {
            val cr = crypto ?: error("crypto")
            client.sendEncrypted(roomId, cr.encrypt(client, roomId, client.joinedMembers(roomId), "m.room.message", content))
        } else client.sendMessage(roomId, content)
}

/**
 * Ad app chiusa, all'ora del primo messaggio programmato (con la rete): manda quelli arrivati all'ora di ogni account e
 * cancella i messaggi a scomparsa scaduti. Se la Chat è aperta su quell'account lo fa lei (tiene lei le chiavi).
 */
class PlannedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        var retry = false
        Account.entries.forEach { account ->
            val s = SessionStore(ctx, account).load() ?: return@forEach
            if (ChatModel.active && ChatAccounts.active(ctx) == account) return@forEach
            val client = ChatClient(s)
            val needsCrypto = ProgrammaStore.planned(ctx).any { it.account == account.name && it.encrypted && it.at <= System.currentTimeMillis() }
            val crypto = if (needsCrypto) runCatching { ChatCrypto(ctx, s) }.getOrNull() else null
            runCatching { ProgrammaStore.work(ctx, account, client, crypto, reschedule = false) }.onFailure { retry = true }
            crypto?.let { runCatching { it.flush(client) } }
        }
        val left = Programma.due(ProgrammaStore.planned(ctx), Account.Personal.name, System.currentTimeMillis()) +
            Programma.due(ProgrammaStore.planned(ctx), Account.Work.name, System.currentTimeMillis())
        if (retry || left.isNotEmpty()) return Result.retry()
        // Il prossimo con l'altro nome: rimpiazzare il lavoro che sta girando lo fermerebbe a metà.
        schedule(ctx, Programma.next(ProgrammaStore.planned(ctx)), if (WORK in tags) WORK_NEXT else WORK)
        return Result.success()
    }

    companion object {
        private const val WORK = "eurise-chat-programmati"
        private const val WORK_NEXT = "eurise-chat-programmati-2"

        /** Prepara il lavoro per l'ora [at] (o lo toglie se non c'è più niente da mandare). */
        fun schedule(context: Context, at: Long?, name: String? = null) {
            val wm = WorkManager.getInstance(context)
            if (name == null) { wm.cancelUniqueWork(WORK_NEXT); if (at == null) { wm.cancelUniqueWork(WORK); return } }
            if (at == null) return
            val unique = name ?: WORK
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<PlannedWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(unique)
                .build()
            wm.enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
