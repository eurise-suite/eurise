package eu.eurise.browser

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
        setContent {
            EuriseTheme {
                BrowserScreen(
                    model = model,
                    onImport = { importFile.launch(arrayOf("text/html", "application/octet-stream", "*/*")) },
                    onListen = ::listen,
                    onFinish = { finish() },
                )
            }
        }
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
                getSystemService(DownloadManager::class.java).enqueue(request)
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
        super.onDestroy()
    }
}
