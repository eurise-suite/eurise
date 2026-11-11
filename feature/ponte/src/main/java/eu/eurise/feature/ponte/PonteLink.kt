package eu.eurise.feature.ponte

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.ponte.Channel
import eu.eurise.core.ponte.Identity
import eu.eurise.core.ponte.Kind
import eu.eurise.core.ponte.Link
import eu.eurise.core.ponte.Message
import eu.eurise.core.ponte.Opening
import eu.eurise.core.ponte.Wire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Dal computer al telefono (decisione del proprietario, 11 novembre). Un collegamento fisso, **solo sul Wi-Fi di casa**
 * e solo con un computer abbinato e acceso: è l'eccezione limitata alla regola «niente servizi sempre attivi», e si vede
 * sempre. Finché è aperto c'è una notifica fissa che dice a quale computer si è collegati e l'ultima cosa passata
 * («12:03 · appunti dal computer»).
 *
 * È un lavoro di WorkManager in primo piano (tipo connectedDevice) con il vincolo di una rete senza consumo: prova il
 * computer, e solo se risponde mette la notifica e tiene il socket; se il computer tace o si spegne, si rimette in coda
 * fra [RETRY_MINUTES] minuti. Uscendo dal Wi-Fi WorkManager lo ferma e lo riprende al ritorno.
 */
class PonteLinkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val store = PonteStore(context)

    override suspend fun doWork(): Result {
        if (!store.receiveOn) return Result.success()
        val computer = store.computers().firstOrNull { it.host != null && isHome(it.host) } ?: return Result.success()
        var failures = 0
        while (failures < 3) {
            val connected = try { hold(computer) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { false }
            if (isStopped || !store.receiveOn) return Result.success()
            failures = if (connected) 0 else failures + 1
            delay(10_000)
        }
        PonteLink.schedule(applicationContext, delayMinutes = RETRY_MINUTES)
        return Result.success()
    }

    /** Tiene il collegamento finché il computer risponde. Vero se ci si è collegati almeno una volta. */
    private suspend fun hold(c: Computer): Boolean = coroutineScope {
        val socket = Socket()
        // La lettura blocca il thread: se WorkManager ferma il lavoro (fuori dal Wi-Fi, interruttore spento) si chiude il socket.
        val closer = launch { try { awaitCancellation() } finally { runCatching { socket.close() } } }
        try {
            withContext(Dispatchers.IO) { talk(socket, c) }
        } finally {
            closer.cancel()
            runCatching { socket.close() }
            shown(null)
        }
    }

    private suspend fun talk(s: Socket, c: Computer): Boolean {
        s.connect(InetSocketAddress(c.host, c.port), 3_000)
        s.soTimeout = 8_000
        s.keepAlive = true
        val out = s.getOutputStream()
        val input = s.getInputStream().buffered()
        Wire.writeFrame(out, Opening.Hi(store.deviceId).encode())
        val channel = Channel(Link.between(store.identity.keys.private, Identity.publicKey(c.publicKey), c.secret), me = store.deviceId)
        Wire.readFrame(input)?.let { channel.open(it, from = c.id) } ?: return false
        // Solo ora, a collegamento fatto, compare la notifica fissa.
        show(c.name, store.lastThing)
        s.soTimeout = PING_MS
        while (true) {
            val frame = try {
                Wire.readFrame(input) ?: break
            } catch (e: SocketTimeoutException) {
                Wire.writeFrame(out, channel.seal(Message(Kind.Ping, UUID.randomUUID().toString(), System.currentTimeMillis())))
                continue
            }
            val m = channel.open(frame, from = c.id) ?: continue
            val said = receive(m) ?: continue
            val line = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()) + " · " + said
            store.lastThing = line
            show(c.name, line)
        }
        return true
    }

    /**
     * La notifica fissa. Di norma è quella del lavoro in primo piano; se Android non permette il primo piano dal fondo
     * (Eurise ancora nell'ottimizzazione della batteria), la stessa notifica si mette a mano: collegati si è sempre visti.
     */
    private suspend fun show(computer: String, last: String?) {
        val ok = runCatching { setForeground(foregroundInfo(computer, last)) }.isSuccess
        if (!ok) shown(PonteLink.ongoing(applicationContext, computer, last))
    }

    private fun shown(n: Notification?) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching { if (n == null) nm.cancel(PonteLink.ONGOING_ID) else nm.notify(PonteLink.ONGOING_ID, n) }
    }

    /** Fa ciò che chiede il computer, se quel tipo è acceso. Restituisce la frase per la notifica fissa. */
    private suspend fun receive(m: Message): String? {
        val ctx = applicationContext
        return when (m.kind) {
            Kind.Clipboard -> {
                if (!store.isOn(Kind.Clipboard)) return null
                val text = m["text"]?.takeIf { it.isNotEmpty() } ?: return null
                withContext(Dispatchers.Main) {
                    val clip = ClipData.newPlainText(ctx.getString(R.string.ponte_title), text)
                    if (Build.VERSION.SDK_INT >= 33 && m["sensitive"] == "1") {
                        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    }
                    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                }
                ctx.getString(R.string.ponte_got_clip)
            }
            Kind.Handoff -> {
                if (!store.isOn(Kind.Handoff)) return null
                val url = m["url"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
                PonteLink.continueHere(ctx, m["title"] ?: url, url)
                ctx.getString(R.string.ponte_got_link, m["title"] ?: Uri.parse(url).host.orEmpty())
            }
            Kind.Note -> {
                val text = m["text"]?.takeIf { it.isNotBlank() } ?: return null
                ctx.sendBroadcast(Intent(ACTION_NOTE).setPackage(ctx.packageName).putExtra(Intent.EXTRA_TEXT, text))
                ctx.getString(R.string.ponte_got_note)
            }
            Kind.Task -> {
                val text = m["text"]?.takeIf { it.isNotBlank() } ?: return null
                ctx.sendBroadcast(Intent(ACTION_TASK).setPackage(ctx.packageName).putExtra(Intent.EXTRA_TEXT, text))
                ctx.getString(R.string.ponte_got_task, text.take(40))
            }
            else -> null
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(store.computers().firstOrNull()?.name.orEmpty(), store.lastThing)

    private fun foregroundInfo(computer: String, last: String?): ForegroundInfo {
        val n = PonteLink.ongoing(applicationContext, computer, last)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(PonteLink.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else ForegroundInfo(PonteLink.ONGOING_ID, n)
    }

    /** Il computer deve stare sulla rete di casa (indirizzo privato): il collegamento fisso non esce mai su Internet. */
    private fun isHome(host: String) = runCatching { InetAddress.getByName(host).let { it.isSiteLocalAddress || it.isLinkLocalAddress } }.getOrDefault(false)

    private companion object {
        /** Segno di vita verso il computer se non passa niente per tanto così. */
        const val PING_MS = 4 * 60_000
        const val RETRY_MINUTES = 15L
        // Lo stesso ricevitore della Tastiera nell'app Eurise (KeyboardBridgeReceiver): siamo nello stesso APK.
        const val ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE"
        const val ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK"
    }
}

/** Accende, spegne e mostra il collegamento dal computer. */
object PonteLink {
    private const val WORK = "ponte-collegamento"
    private const val CHANNEL_ONGOING = "ponte-collegamento"
    private const val CHANNEL_CONTINUE = "ponte-continua"
    const val ONGOING_ID = 4_710

    /** Da chiamare all'apertura della schermata, dopo un abbinamento e accendendo l'interruttore. */
    fun start(context: Context) {
        if (PonteStore(context).receiveOn) schedule(context, delayMinutes = 0, policy = ExistingWorkPolicy.KEEP)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        context.getSystemService(NotificationManager::class.java)?.cancel(ONGOING_ID)
    }

    internal fun schedule(context: Context, delayMinutes: Long, policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
        val request = OneTimeWorkRequestBuilder<PonteLinkWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
    }

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.ponte_channel_link), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CONTINUE, context.getString(R.string.ponte_channel_continue), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** La notifica fissa: a quale computer, l'ultima cosa passata, e il modo per smettere. */
    internal fun ongoing(context: Context, computer: String, last: String?): Notification {
        channels(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, PonteActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ponte_linked_to, computer))
            .setContentText(last ?: context.getString(R.string.ponte_linked_idle))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /** «Continua qui»: il link arrivato dal computer, da aprire con un tocco (dal fondo non si apre nulla da solo). */
    internal fun continueHere(context: Context, title: String, url: String) {
        channels(context)
        val view = PendingIntent.getActivity(
            context, url.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_CONTINUE)
            .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
            .setContentTitle(context.getString(R.string.ponte_continue_here))
            .setContentText(title)
            .setSubText(Uri.parse(url).host)
            .setContentIntent(view)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(url.hashCode(), n) }
    }
}
