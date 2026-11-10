package eu.eurise.translate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Prototipo: traduce italiano ↔ inglese sul telefono e mostra i tempi, per decidere il motore della Tastiera. */
class TranslateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { Surface(Modifier.fillMaxSize()) { TranslateScreen() } } }
    }
}

@Composable
private fun TranslateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Un solo thread per il motore: slimt non va usato da più thread insieme.
    val engineThread = remember { Executors.newSingleThreadExecutor() }
    val engine = remember { engineThread.asCoroutineDispatcher() }
    val open = remember { HashMap<String, Translator>() }
    DisposableEffect(Unit) {
        onDispose {
            engineThread.execute { open.values.forEach { it.close() } }
            engineThread.shutdown()
        }
    }
    var pair by remember { mutableStateOf("it-en") }
    var installed by remember { mutableStateOf(setOf("it-en", "en-it").filter { Models.installed(context, it) != null }.toSet()) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf(false) }

    fun translate() {
        val text = input.trim().takeIf { it.isNotEmpty() } ?: return
        busy = context.getString(R.string.tr_working)
        scope.launch {
            val (result, line) = withContext(engine) {
                runCatching {
                    val t0 = System.nanoTime()
                    val translator = open[pair] ?: (Models.installed(context, pair)?.let(Translator::open) ?: error("modello")).also { open[pair] = it }
                    val t1 = System.nanoTime()
                    val out = text.split('\n').joinToString("\n") { if (it.isBlank()) it else translator.translate(it) }
                    val t2 = System.nanoTime()
                    val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
                    out to context.getString(R.string.tr_stats, (t1 - t0) / 1e9, (t2 - t1) / 1_000_000, words, words / ((t2 - t1) / 1e9).coerceAtLeast(1e-3))
                }.getOrElse { "" to context.getString(R.string.tr_failed, it.message.orEmpty()) }
            }
            output = result
            stats = line
            busy = null
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tr_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.tr_intro), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("it-en" to R.string.tr_it_en, "en-it" to R.string.tr_en_it).forEach { (p, label) ->
                if (p == pair) Button(onClick = {}) { Text(stringResource(label)) } else OutlinedButton(onClick = { pair = p; output = ""; stats = "" }) { Text(stringResource(label)) }
            }
        }
        if (pair !in installed) {
            Button(onClick = { ask = true }, enabled = busy == null) { Text(stringResource(R.string.tr_download)) }
        } else {
            OutlinedTextField(input, { input = it }, label = { Text(stringResource(R.string.tr_input)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::translate, enabled = busy == null && input.isNotBlank()) { Text(stringResource(R.string.tr_translate)) }
                OutlinedButton(onClick = {
                    val p = pair
                    engineThread.execute { open.remove(p)?.close() }
                    Models.remove(context, p)
                    installed = installed - p
                }, enabled = busy == null) { Text(stringResource(R.string.tr_remove, Models.size(context, pair) / 1_048_576.0)) }
            }
        }
        busy?.let { Text(it) }
        if (output.isNotEmpty()) SelectionContainer { Text(output, style = MaterialTheme.typography.titleMedium) }
        if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.labelLarge)
    }

    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(stringResource(R.string.tr_download_title)) },
        text = { Text(stringResource(R.string.tr_download_text)) },
        confirmButton = {
            TextButton(onClick = {
                ask = false
                val p = pair
                busy = context.getString(R.string.tr_downloading)
                scope.launch {
                    val ok = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Models.download(context, p) }.isSuccess }
                    if (ok) installed = installed + p
                    busy = if (ok) null else context.getString(R.string.tr_download_failed)
                }
            }) { Text(stringResource(R.string.tr_download_ok)) }
        },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(stringResource(R.string.tr_cancel)) } },
    )
}
