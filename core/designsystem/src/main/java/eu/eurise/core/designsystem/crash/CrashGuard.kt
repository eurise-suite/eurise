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
