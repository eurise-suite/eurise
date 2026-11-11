package eu.eurise.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Prototipo di Eurise AI: sceglie il modello, lo carica e misura. Non è ancora l'interfaccia delle tavole (AiCattura,
 * AiModelli, AiAttiva): serve a sapere se un modello da 3B sta in memoria e risponde abbastanza in fretta sul telefono.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cpu = runCatching { AiNative.backends(applicationInfo.nativeLibraryDir) }.getOrElse { "errore: ${it.message}" }
        setContent { EuriseTheme { Surface(Modifier.fillMaxSize()) { Prototype(cpu) } } }
    }

    override fun onDestroy() {
        if (isFinishing) Holder.ai?.close().also { Holder.ai = null }
        super.onDestroy()
    }
}

/** Il modello resta caricato finché l'app è aperta (anche ruotando lo schermo). */
private object Holder {
    var ai: Ai? = null
    val engine = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}

@Composable
private fun Prototype(cpu: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ai by remember { mutableStateOf(Holder.ai) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var threads by remember { mutableIntStateOf(Ai.defaultThreads()) }
    var memory by remember { mutableStateOf(memoryLine(context)) }
    var captureText by remember { mutableStateOf(context.getString(R.string.ai_capture_default)) }
    var captureResult by remember { mutableStateOf<Answer?>(null) }
    var question by remember { mutableStateOf(context.getString(R.string.ai_ask_default)) }
    var answer by remember { mutableStateOf<Answer?>(null) }
    var report by remember { mutableStateOf("") }
    val hasFile = remember(ai, busy) { ModelFile.file(context).exists() }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() } finally {
                busy = false
                memory = memoryLine(context)
            }
        }
    }

    fun loadModel() = run {
        status = context.getString(R.string.ai_loading)
        val loaded = withContext(Holder.engine) { Ai.load(ModelFile.file(context).absolutePath, threads) }
        Holder.ai = loaded
        ai = loaded
        status = if (loaded == null) context.getString(R.string.ai_load_failed) else null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        run {
            withContext(Holder.engine) { Holder.ai?.close() }
            Holder.ai = null
            ai = null
            val ok = withContext(Dispatchers.IO) {
                ModelFile.import(context, uri) { p -> scope.launch { status = context.getString(R.string.ai_copying, p) } }
            }
            status = if (ok) null else context.getString(R.string.ai_not_gguf)
        }
        if (ModelFile.file(context).exists()) scope.launch {
            while (busy) kotlinx.coroutines.delay(100)
            loadModel()
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.ai_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

        Section(stringResource(R.string.ai_phone))
        Text(stringResource(R.string.ai_phone_line, Device.name(), Device.cores(), Device.size(Device.totalRam(context))), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ai_cpu_line, cpu), style = MaterialTheme.typography.bodySmall)

        Section(stringResource(R.string.ai_model))
        val loaded = ai
        if (loaded == null) {
            Text(stringResource(R.string.ai_model_hint), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.ai_pick)) }
                if (hasFile) OutlinedButton(onClick = ::loadModel, enabled = !busy) { Text(stringResource(R.string.ai_load)) }
            }
        } else {
            Text(
                stringResource(R.string.ai_model_line, loaded.description, Device.size(loaded.bytes), Device.num(loaded.loadMs / 1000.0)),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = {
                run {
                    withContext(Holder.engine) { Holder.ai?.close() }
                    Holder.ai = null
                    ai = null
                    ModelFile.file(context).delete()
                }
            }, enabled = !busy) { Text(stringResource(R.string.ai_delete)) }
        }
        Text(memory, style = MaterialTheme.typography.bodySmall)
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (loaded != null) {
            Text(stringResource(R.string.ai_threads), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 4, 6, 8).filter { it <= Device.cores() }.forEach { n ->
                    FilterChip(selected = threads == n, enabled = !busy, onClick = {
                        threads = n
                        run { withContext(Holder.engine) { loaded.setThreads(n) } }
                    }, label = { Text("$n") })
                }
            }

            Section(stringResource(R.string.ai_capture))
            OutlinedTextField(captureText, { captureText = it }, Modifier.fillMaxWidth(), enabled = !busy)
            Button(onClick = {
                run {
                    status = context.getString(R.string.ai_working)
                    val today = LocalDate.now()
                    captureResult = withContext(Holder.engine) { loaded.capture(captureText, today.year, today.monthValue, today.dayOfMonth) }
                    status = null
                }
            }, enabled = !busy && captureText.isNotBlank()) { Text(stringResource(R.string.ai_understand)) }
            captureResult?.let { AnswerView(it, pretty = true) }

            Section(stringResource(R.string.ai_ask))
            OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), enabled = !busy, minLines = 2)
            Button(onClick = {
                run {
                    status = context.getString(R.string.ai_working)
                    answer = withContext(Holder.engine) { loaded.ask(question) }
                    status = null
                }
            }, enabled = !busy && question.isNotBlank()) { Text(stringResource(R.string.ai_answer)) }
            answer?.let { AnswerView(it, pretty = false) }

            Section(stringResource(R.string.ai_measure))
            Text(stringResource(R.string.ai_measure_d), style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                run {
                    val sentences = context.resources.openRawResource(R.raw.frasi_prova).bufferedReader().readLines().filter { it.isNotBlank() }
                    val total = sentences.size + 1
                    status = context.getString(R.string.ai_measure_progress, 0, total)
                    val b = withContext(Holder.engine) { loaded.bench() }
                    val sb = StringBuilder()
                    sb.appendLine("Eurise AI, misura — ${Device.name()}, ${Device.cores()} core, ${Device.size(Device.totalRam(context))}")
                    sb.appendLine("Motore: $cpu")
                    sb.appendLine("Modello: ${loaded.description}, ${Device.size(loaded.bytes)}, caricato in ${Device.num(loaded.loadMs / 1000.0)} s, $threads thread")
                    sb.appendLine(context.getString(R.string.ai_bench_line, Device.num(b.promptSpeed), Device.num(b.genSpeed)))
                    var sumMs = 0.0
                    sentences.forEachIndexed { i, s ->
                        status = context.getString(R.string.ai_measure_progress, i + 1, total)
                        val r = withContext(Holder.engine) { loaded.capture(s, 2026, 11, 11) }
                        sumMs += r.msPrompt + r.msGen
                        sb.appendLine("· $s → ${r.text} (${Device.num((r.msPrompt + r.msGen) / 1000.0)} s)")
                    }
                    sb.appendLine("Cattura: in media ${Device.num(sumMs / sentences.size / 1000.0)} s per frase")
                    sb.appendLine(memoryLine(context))
                    report = sb.toString()
                    status = null
                }
            }, enabled = !busy) { Text(stringResource(R.string.ai_measure_start)) }
            if (report.isNotEmpty()) {
                SelectionContainer { Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                OutlinedButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Eurise AI", report))
                    Toast.makeText(context, R.string.ai_copied, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.ai_copy)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AnswerView(a: Answer, pretty: Boolean) {
    if (!a.ok) {
        Text(stringResource(R.string.ai_failed), color = MaterialTheme.colorScheme.error)
        return
    }
    val text = if (pretty) runCatching { JSONObject(a.text).toString(2) }.getOrDefault(a.text) else a.text
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = if (pretty) FontFamily.Monospace else FontFamily.Default)
    }
    Text(
        stringResource(
            R.string.ai_timing,
            a.nPrompt, Device.num(a.msPrompt / 1000.0), Device.num(a.promptSpeed),
            a.nGen, Device.num(a.msGen / 1000.0), Device.num(a.genSpeed),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun memoryLine(context: Context) =
    context.getString(R.string.ai_memory_line, Device.size(Device.rss()), Device.size(Device.freeRam(context)))
