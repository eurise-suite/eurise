package eu.eurise.read

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import eu.eurise.read.ocr.Ocr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Prototipo di Eurise Leggi: si sceglie un'immagine (scansione, foto di un documento), si legge il testo in italiano
 * sul telefono e si vedono tempo e fiducia media. Serve a misurare, non è ancora l'app.
 */
class ReadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { Surface(Modifier.fillMaxSize()) { ReadScreen() } } }
    }
}


@Composable
private fun ReadScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataDir = remember { TessModel.dataDir(context) }
    val model = remember { TessModel.file(context) }
    val english = remember { TessModel.file(context, "eng") }
    var hasModel by remember { mutableStateOf(model.exists()) }
    var hasEnglish by remember { mutableStateOf(english.exists()) }
    var askDownload by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }

    fun read(uri: Uri) {
        busy = context.getString(R.string.read_reading)
        scope.launch {
            val (text, line) = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        ?: error("immagine")
                    val start = System.currentTimeMillis()
                    val ocr = Ocr.open(dataDir, TessModel.languages(context) ?: "ita") ?: error("modello")
                    ocr.use {
                        val out = it.read(bitmap)
                        val ms = System.currentTimeMillis() - start
                        out to context.getString(R.string.read_stats, ms / 1000.0, it.confidence(), bitmap.width, bitmap.height)
                    }
                }.getOrElse { "" to context.getString(R.string.read_failed, it.message.orEmpty()) }
            }
            result = text
            stats = line
            busy = null
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::read) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.read_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.read_intro), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasModel) Button(onClick = { pick.launch("image/*") }, enabled = busy == null) { Text(stringResource(R.string.read_pick)) }
            else Button(onClick = { askDownload = true }, enabled = busy == null) { Text(stringResource(R.string.read_download)) }
            if (hasModel) OutlinedButton(onClick = { model.delete(); hasModel = false }) { Text(stringResource(R.string.read_remove)) }
        }
        if (hasModel) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasEnglish) OutlinedButton(onClick = { english.delete(); hasEnglish = false }, enabled = busy == null) { Text(stringResource(R.string.read_remove_english)) }
            else OutlinedButton(onClick = {
                busy = context.getString(R.string.read_downloading)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { TessModel.download(english) }.isSuccess }
                    hasEnglish = ok && english.exists()
                    busy = if (ok) null else context.getString(R.string.read_download_failed)
                }
            }, enabled = busy == null) { Text(stringResource(R.string.read_add_english)) }
        }
        OutlinedButton(onClick = {
            context.startActivity(android.content.Intent(context, eu.eurise.read.translate.TranslateActivity::class.java))
        }) { Text(stringResource(R.string.read_translate)) }
        busy?.let { Text(it) }
        if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.labelLarge)
        if (result.isNotEmpty()) SelectionContainer { Text(result, style = MaterialTheme.typography.bodyMedium) }
    }

    if (askDownload) {
        AlertDialog(
            onDismissRequest = { askDownload = false },
            title = { Text(stringResource(R.string.read_download_title)) },
            text = { Text(stringResource(R.string.read_download_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askDownload = false
                    busy = context.getString(R.string.read_downloading)
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { TessModel.download(model) }.isSuccess }
                        hasModel = ok && model.exists()
                        busy = if (ok) null else context.getString(R.string.read_download_failed)
                    }
                }) { Text(stringResource(R.string.read_download_ok)) }
            },
            dismissButton = { TextButton(onClick = { askDownload = false }) { Text(stringResource(R.string.read_cancel)) } },
        )
    }
}

