package eu.eurise.read.translate

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.read.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Lingue della traduzione in Eurise Leggi: si scaricano e si tolgono una per una (ognuna con l'inglese, in due direzioni),
 * e si può provare a tradurre. Le altre app della suite traducono con [TranslateService].
 */
class TranslateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { Surface(Modifier.fillMaxSize()) { LanguagesScreen() } } }
    }
}

private fun name(code: String): String = Locale(code).getDisplayLanguage(Locale.ITALIAN).replaceFirstChar { it.uppercase() }

@Composable
private fun LanguagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineThread = remember { Executors.newSingleThreadExecutor() }
    val dispatcher = remember { engineThread.asCoroutineDispatcher() }
    val engine = remember { TranslateEngine(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            engineThread.execute { engine.close() }
            engineThread.shutdown()
        }
    }
    var ready by remember { mutableStateOf(Models.languages(context).toSet()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableStateOf("it") }
    var to by remember { mutableStateOf("en") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tr_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.tr_intro), style = MaterialTheme.typography.bodyMedium)
        Models.LANGUAGES.forEach { lang ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name(lang), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (lang in ready) {
                    OutlinedButton(onClick = {
                        engineThread.execute { engine.forget(lang) }
                        Models.removeLanguage(context, lang)
                        ready = Models.languages(context).toSet()
                    }, enabled = busy == null) { Text(stringResource(R.string.tr_remove, Models.languageSize(context, lang) / 1_048_576.0)) }
                } else {
                    Button(onClick = { ask = lang }, enabled = busy == null) { Text(stringResource(R.string.tr_download)) }
                }
            }
        }
        busy?.let { Text(it) }
        if (ready.size >= 2) {
            Text(stringResource(R.string.tr_try), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { val l = ready.sorted(); from = l[(l.indexOf(from) + 1) % l.size] }) { Text(name(from)) }
                Text("→")
                OutlinedButton(onClick = { val l = ready.sorted(); to = l[(l.indexOf(to) + 1) % l.size] }) { Text(name(to)) }
            }
            OutlinedTextField(input, { input = it }, label = { Text(stringResource(R.string.tr_input)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
            Button(onClick = {
                val text = input.trim()
                busy = context.getString(R.string.tr_working)
                scope.launch {
                    output = withContext(dispatcher) {
                        runCatching { engine.translate(text, from, to) }.getOrElse { context.getString(R.string.tr_failed) }
                    }
                    busy = null
                }
            }, enabled = busy == null && input.isNotBlank() && from in ready && to in ready) { Text(stringResource(R.string.tr_translate)) }
            if (output.isNotEmpty()) SelectionContainer { Text(output, style = MaterialTheme.typography.titleMedium) }
        }
    }

    ask?.let { lang ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(stringResource(R.string.tr_download_title, name(lang))) },
            text = { Text(stringResource(R.string.tr_download_text)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = null
                    busy = context.getString(R.string.tr_downloading, name(lang))
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Models.downloadLanguage(context, lang) }.isSuccess }
                        ready = Models.languages(context).toSet()
                        busy = if (ok) null else context.getString(R.string.tr_download_failed)
                    }
                }) { Text(stringResource(R.string.tr_download_ok)) }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(stringResource(R.string.read_cancel)) } },
        )
    }
}
