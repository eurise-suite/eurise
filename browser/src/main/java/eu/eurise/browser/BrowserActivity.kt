package eu.eurise.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.theme.EuriseTheme
import java.util.Locale

/** Eurise Browser: una schermata, le schede in un foglio. Apre i link e le ricerche che arrivano dalle altre app. */
class BrowserActivity : ComponentActivity() {
    private lateinit var model: BrowserModel
    private var tts: TextToSpeech? = null

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val html = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val added = html?.let { model.importBookmarks(it) } ?: -1
        Toast.makeText(this, if (added >= 0) getString(R.string.brw_import_done, added) else getString(R.string.brw_import_failed), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = BrowserModel(this)
        model.onDownload = ::download
        handle(intent)
        androidx.core.content.ContextCompat.registerReceiver(
            this, downloadDone, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        setContent {
            EuriseTheme {
                Box(Modifier.fillMaxSize()) {
                    BrowserScreen(
                        model = model,
                        onImport = { importFile.launch(arrayOf("text/html", "application/octet-stream", "*/*")) },
                        onListen = ::listen,
                        onFinish = { finish() },
                    )
                    finished?.let { done ->
                        LaunchedEffect(done) { kotlinx.coroutines.delay(12_000); if (finished == done) finished = null }
                        Text(
                            getString(R.string.brw_downloaded_save, done.name), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                                .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary)
                                .clickable { finished = null; saveIn(done) }.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    // --- Download finiti: «Salva in…» di Eurise File (tavola FileSalvaIn) ---

    private class Finished(val id: Long, val name: String, val mime: String)

    private var finished by mutableStateOf<Finished?>(null)
    private val mine = HashSet<Long>()

    private val downloadDone = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id !in mine) return
            val dm = getSystemService(DownloadManager::class.java)
            runCatching {
                dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) return@use
                    if (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return@use
                    val name = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank { "download" }
                    val mime = dm.getMimeTypeForDownloadedFile(id) ?: "application/octet-stream"
                    finished = Finished(id, name, mime)
                }
            }
        }
    }

    /** Una copia del file scaricato passa all'app Eurise, che la mette nella cartella scelta (telefono o cloud). */
    private fun saveIn(done: Finished) {
        runCatching {
            val dm = getSystemService(DownloadManager::class.java)
            val dir = java.io.File(cacheDir, "salva").apply { deleteRecursively(); mkdirs() }
            val file = java.io.File(dir, done.name.replace('/', '-'))
            dm.openDownloadedFile(done.id).use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { input -> file.outputStream().use { input.copyTo(it) } } }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.salva", file)
            startActivity(eu.eurise.core.suite.FilesContract.saveIn(BuildConfig.HUB_PACKAGE, uri, done.mime, file.name, "download", getString(R.string.brw_app_name)))
        }.onFailure { Toast.makeText(this, R.string.brw_save_in_missing, Toast.LENGTH_LONG).show() }
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val address = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(android.app.SearchManager.QUERY)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (address != null || model.tabs.isEmpty()) model.open(address)
    }

    /** I download vanno nel gestore di sistema, in Download; la notifica dice quando è finito. */
    private fun download(url: String) {
        runOnUiThread {
            runCatching {
                val uri = Uri.parse(url)
                val name = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "download"
                val request = DownloadManager.Request(uri)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                mine += getSystemService(DownloadManager::class.java).enqueue(request)
                Toast.makeText(this, getString(R.string.brw_download_started, name), Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(this, R.string.brw_download_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    /** «Ascolta»: la sintesi vocale del telefono legge la pagina (il testo non esce). Tocco di nuovo: si ferma. */
    private fun listen(text: String) {
        val engine = tts
        if (engine != null && engine.isSpeaking) { engine.stop(); return }
        if (text.isBlank()) { Toast.makeText(this, R.string.brw_listen_empty, Toast.LENGTH_SHORT).show(); return }
        fun speak(t: TextToSpeech) {
            t.language = Locale.getDefault()
            text.chunked(3500).forEachIndexed { i, part -> t.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "p$i") }
        }
        if (engine != null) speak(engine)
        else tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.let(::speak) }
    }

    override fun onDestroy() {
        tts?.shutdown()
        runCatching { unregisterReceiver(downloadDone) }
        super.onDestroy()
    }
}
