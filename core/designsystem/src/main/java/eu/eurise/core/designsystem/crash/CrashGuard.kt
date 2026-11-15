package eu.eurise.core.designsystem.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Quando un'app della suite si chiude da sola, l'errore resta scritto sul telefono (solo lì, nella cartella dell'app) e
 * alla riapertura si vede con «Copia» e «Condividi», per mandarlo a chi sviluppa. Nasce il 15 novembre per il Browser
 * che si chiudeva subito dopo l'apertura senza lasciare tracce leggibili.
 */
object CrashGuard {
    private const val FILE = "ultimo-errore.txt"

    /** Da chiamare all'avvio (prima possibile): tiene il gestore di Android dopo aver scritto l'errore. */
    fun install(context: Context, app: String) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull()
                File(appContext.filesDir, FILE).writeText(
                    "$app ${version.orEmpty()} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "thread ${thread.name} · ${java.time.LocalDateTime.now()}\n\n" + trace.take(30_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** L'errore dell'ultima chiusura, se c'è. */
    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.isFile }?.readText()

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}

/**
 * Parte con ogni app della suite (provider senza dati, come fa androidx.startup): installa [CrashGuard] e, se l'ultima
 * volta l'app si è chiusa da sola, al primo avvio di una schermata apre [CrashActivity] con l'errore.
 */
class CrashInstaller : android.content.ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return true
        val process = if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() else ""
        if (process.endsWith(":errore")) return true
        val label = runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault("Eurise")
        CrashGuard.install(context, label)
        (context.applicationContext as? android.app.Application)?.registerActivityLifecycleCallbacks(object : SimpleLifecycle() {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {
                if (activity is CrashActivity) return
                val report = CrashGuard.pending(activity) ?: return
                CrashGuard.clear(activity)
                runCatching {
                    activity.startActivity(
                        Intent(activity, CrashActivity::class.java).putExtra(CrashActivity.EXTRA_REPORT, report)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                    )
                }
            }
        })
        return true
    }

    override fun query(u: android.net.Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): android.database.Cursor? = null
    override fun getType(uri: android.net.Uri): String? = null
    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
    override fun delete(uri: android.net.Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: android.net.Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
}

private open class SimpleLifecycle : android.app.Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

/** «Si è chiusa da sola» in un processo a parte ([CrashInstaller]). */
class CrashActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT) ?: run { finish(); return }
        androidx.activity.compose.setContent(this) {
            eu.eurise.core.designsystem.theme.EuriseTheme {
                CrashScreen(this, report) {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { runCatching { startActivity(it) } }
                    finish()
                }
            }
        }
    }

    companion object { const val EXTRA_REPORT = "report" }
}

/** La schermata «Si è chiusa da sola»: l'errore, Copia, Condividi, Riapri. */
@Composable
fun CrashScreen(activity: Activity, report: String, onReopen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Si è chiusa da sola", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "L'ultima volta l'app si è chiusa per un errore. Copialo o condividilo e mandalo in chat: serve per correggerlo. " +
                "Resta solo su questo telefono finché non lo mandi tu.",
            fontSize = 14.sp, color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("errore", report))
            }) { Text("Copia") }
            OutlinedButton(onClick = {
                runCatching {
                    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), null))
                }
            }) { Text("Condividi") }
            OutlinedButton(onClick = onReopen) { Text("Riapri") }
        }
        Text(
            report, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(scheme.surface)
                .verticalScroll(rememberScrollState()).padding(12.dp),
        )
    }
}
