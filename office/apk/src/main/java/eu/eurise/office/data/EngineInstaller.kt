package eu.eurise.office.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import eu.eurise.office.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A che punto è il motore: da scaricare, in arrivo, da confermare nella finestra di Android, pronto, oppure fermo. */
sealed interface EngineState {
    data object Missing : EngineState
    /** [done] e [total] in byte; [total] 0 se il server non lo dice. */
    data class Downloading(val done: Long, val total: Long) : EngineState
    data object Installing : EngineState
    data object Ready : EngineState
    data class Failed(val reason: Reason) : EngineState

    enum class Reason { Network, Signature, Install, Cancelled }
}

/**
 * Scarica «Eurise Office Motore» dall'archivio delle versioni ([BuildConfig.ENGINE_URL]) e lo installa con il
 * PackageInstaller di Android, che chiede conferma alla persona. Prima di installare controlla che l'APK sia il motore
 * e che sia firmato con la stessa chiave di Office: un file diverso viene cancellato e non arriva mai al sistema.
 *
 * Dati che escono: solo la richiesta del file all'archivio (indirizzo IP, come ogni download); nessun identificativo.
 * Si scarica solo quando la persona lo chiede dalla tavola OffMotore.
 */
object EngineInstaller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<EngineState>(EngineState.Missing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Da chiamare quando la schermata torna in primo piano: il motore può essere stato installato o tolto da fuori. */
    fun refresh(context: Context) {
        val now = _state.value
        if (Engine.available(context)) _state.value = EngineState.Ready
        else if (now == EngineState.Ready) _state.value = EngineState.Missing
    }

    /** Wi-Fi o comunque rete non a consumo. */
    fun onUnmeteredNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        _state.value = EngineState.Downloading(0, 0)
        job = scope.launch {
            val apk = File(app.cacheDir, "motore/eurise-office-motore.apk")
            val ok = runCatching { download(apk) { isActive } }.isSuccess
            if (!isActive) { apk.delete(); return@launch }
            if (!ok) { apk.delete(); _state.value = EngineState.Failed(EngineState.Reason.Network); return@launch }
            if (!genuine(app, apk)) { apk.delete(); _state.value = EngineState.Failed(EngineState.Reason.Signature); return@launch }
            _state.value = EngineState.Installing
            runCatching { install(app, apk) }.onFailure {
                apk.delete()
                _state.value = EngineState.Failed(EngineState.Reason.Install)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = EngineState.Missing
    }

    private fun download(target: File, active: () -> Boolean) {
        target.parentFile?.mkdirs()
        var url = URL(BuildConfig.ENGINE_URL)
        var conn: HttpURLConnection
        var hops = 0
        // GitHub manda a un altro indirizzo (anche di dominio diverso): i rimandi si seguono a mano, solo in https.
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "EuriseOffice")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                val next = URL(url, conn.getHeaderField("Location") ?: throw IOException("rimando senza indirizzo"))
                conn.disconnect()
                if (next.protocol != "https") throw IOException("rimando non sicuro")
                url = next
                hops++
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("risposta $code")
            break
        }
        val total = conn.contentLengthLong.coerceAtLeast(0)
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (!active()) throw IOException("annullato")
                    out.write(buf, 0, n)
                    done += n
                    if (done - last > 512 * 1024) {
                        last = done
                        _state.value = EngineState.Downloading(done, total)
                    }
                }
                if (total > 0 && done != total) throw IOException("file incompleto")
            }
        }
        conn.disconnect()
    }

    /** È davvero il motore e porta le stesse firme di Office. */
    private fun genuine(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        var info = if (Build.VERSION.SDK_INT >= 28) pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) else null
        var theirs = Engine.signaturesOf(info)
        // Su alcune versioni gli archivi non portano signingInfo: si rilegge con le firme alla vecchia maniera.
        if (theirs.isEmpty()) {
            @Suppress("DEPRECATION")
            info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
            theirs = Engine.signaturesOf(info)
        }
        if (info?.packageName != Engine.packageName) return false
        return theirs.isNotEmpty() && theirs == Engine.ownSignatures(context)
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(Engine.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("motore.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 256 * 1024)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, EngineInstallReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
        apk.delete()
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = if (Engine.available(context)) EngineState.Ready else EngineState.Failed(EngineState.Reason.Signature)
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = EngineState.Failed(EngineState.Reason.Cancelled)
            else -> _state.value = EngineState.Failed(EngineState.Reason.Install)
        }
    }
}

/** Riceve l'esito del PackageInstaller (conferma da chiedere, installato, rifiutato). */
class EngineInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = EngineInstaller.onResult(context, intent)
}
