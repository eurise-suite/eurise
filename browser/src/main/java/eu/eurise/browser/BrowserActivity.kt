package eu.eurise.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.view.KeyEvent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
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
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.contacts.Reach
import eu.eurise.core.suite.FilesContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Eurise Browser: una schermata, le schede in un foglio. Apre i link e le ricerche che arrivano dalle altre app. */
class BrowserActivity : ComponentActivity(), BrowserActions {
    private lateinit var model: BrowserModel
    private var tts: TextToSpeech? = null

    /** Importa: uno o più file esportati (CSV delle password, HTML dei segnalibri), letti qui e poi lasciati andare. */
    private val importFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        Thread {
            val read = uris.mapNotNull { uri -> scan(uri) }
            runOnUiThread {
                read.forEach { f -> model.picked.removeAll { it.uri == f.uri }; model.picked += f }
                if (read.isEmpty() || read.all { it.scan.isEmpty }) toast(getString(R.string.brw_import_failed))
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = BrowserModel(this)
        model.onDownload = ::download
        model.restoreTabs()
        handle(intent)
        androidx.core.content.ContextCompat.registerReceiver(
            this, downloadDone, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        setContent {
            EuriseTheme {
                Box(Modifier.fillMaxSize()) {
                    BrowserScreen(model = model, actions = this@BrowserActivity, installed = ::installed, onRunImport = ::runImport)
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

    override fun onStop() {
        model.saveTabs()
        super.onStop()
    }

    // --- Download finiti: «Salva in…» di Eurise File (tavola FileSalvaIn) ---

    private class Finished(val id: Long, val name: String, val mime: String)

    private var finished by mutableStateOf<Finished?>(null)
    private val mine = HashSet<Long>()

    private val downloadDone = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id !in mine || !model.settings.askWhereToSave) return
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
            val file = File(freshDir(), done.name.replace('/', '-'))
            dm.openDownloadedFile(done.id).use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { input -> file.outputStream().use { input.copyTo(it) } } }
            offer(file, done.mime, "download")
        }.onFailure { toast(getString(R.string.brw_save_in_missing)) }
    }

    /** La cartella delle copie da passare alle altre app, vuotata a ogni uso. */
    private fun freshDir(): File = File(cacheDir, "salva").apply { deleteRecursively(); mkdirs() }

    /** Passa un file a «Salva in…» di Eurise File; senza l'app Eurise, al foglio di condivisione. */
    private fun offer(file: File, mime: String, kind: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.salva", file)
        val save = FilesContract.saveIn(BuildConfig.HUB_PACKAGE, uri, mime, file.name, kind, getString(R.string.brw_app_name))
        val ok = runCatching { startActivity(save) }.isSuccess
        if (!ok) {
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri(file.name, uri) }
            runCatching { startActivity(Intent.createChooser(send, file.name)) }.onFailure { toast(getString(R.string.brw_save_in_missing)) }
        }
    }

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(Date())

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
                model.downloads = mine.size
                toast(getString(R.string.brw_download_started, name))
            }.onFailure { toast(getString(R.string.brw_download_failed)) }
        }
    }

    // --- Azioni del menu e delle Impostazioni (BrowserActions) ---

    /** «Ascolta»: la sintesi vocale del telefono legge la pagina (il testo non esce). Tocco di nuovo: si ferma. */
    override fun listen(text: String) {
        val engine = tts
        if (engine != null && engine.isSpeaking) { engine.stop(); return }
        if (text.isBlank()) { toast(getString(R.string.brw_listen_empty)); return }
        val clean = text.lineSequence().joinToString("\n") { it.removePrefix("# ") }
        fun speak(t: TextToSpeech) {
            t.language = Locale.getDefault()
            clean.chunked(3500).forEachIndexed { i, part -> t.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "p$i") }
        }
        if (engine != null) speak(engine)
        else tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.let(::speak) }
    }

    override fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun pickImportFiles() {
        runCatching { importFiles.launch(arrayOf("text/csv", "text/comma-separated-values", "text/html", "text/plain", "application/octet-stream", "*/*")) }
    }

    private val passwordPackage get() = if (BuildConfig.DEBUG) "eu.eurise.password.debug" else "eu.eurise.password"

    override fun openPassword() {
        val launch = packageManager.getLaunchIntentForPackage(passwordPackage)
        if (launch == null) toast(getString(R.string.brw_no_password_app)) else startActivity(launch)
    }

    /** La scelta del servizio di compilazione automatica di Android (Eurise Password lo è). */
    override fun autofillSettings() {
        val pick = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:$passwordPackage"))
        runCatching { startActivity(pick) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    fun installed(packages: List<String>): Boolean = packages.any { p ->
        runCatching { packageManager.getPackageInfo(p, 0) }.isSuccess
    }

    override fun openApp(packages: List<String>): Boolean {
        val launch = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) } ?: return false
        return runCatching { startActivity(launch) }.isSuccess
    }

    /** «Cattura»: l'immagine di ciò che si vede, in PNG, a «Salva in…». */
    override fun capture(tab: Tab) {
        val view = Gecko.view ?: return toast(getString(R.string.brw_capture_failed))
        view.capturePixels().accept({ bmp: Bitmap? ->
            if (bmp == null) { toast(getString(R.string.brw_capture_failed)); return@accept }
            Thread {
                val file = File(freshDir(), "${Links.host(tab.url)} ${stamp()}.png")
                runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                runOnUiThread { offer(file, "image/png", "image") }
            }.start()
        }, { toast(getString(R.string.brw_capture_failed)) })
    }

    /** «Stampa PDF»: la pagina in PDF dal motore, a «Salva in…» (da lì si stampa o si condivide). */
    override fun savePdf(tab: Tab) {
        toast(getString(R.string.brw_pdf_working))
        tab.session.saveAsPdf().accept({ input ->
            if (input == null) { toast(getString(R.string.brw_pdf_failed)); return@accept }
            Thread {
                val name = (tab.title.ifBlank { Links.host(tab.url) }).replace(Regex("[\\\\/:*?\"<>|]"), " ").take(80).trim()
                val file = File(freshDir(), "$name.pdf")
                val ok = runCatching { input.use { i -> file.outputStream().use { i.copyTo(it) } } }.isSuccess
                runOnUiThread { if (ok) offer(file, "application/pdf", "document") else toast(getString(R.string.brw_pdf_failed)) }
            }.start()
        }, { toast(getString(R.string.brw_pdf_failed)) })
    }

    /** «In home»: un'icona del sito nella schermata Home, che apre il sito nel Browser. */
    override fun addToHome(tab: Tab) {
        val url = tab.url
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(this, BrowserActivity::class.java)
        val info = ShortcutInfoCompat.Builder(this, "site:" + Links.host(url))
            .setShortLabel(tab.title.ifBlank { Links.host(url) }.take(24))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_browser))
            .setIntent(open)
            .build()
        val ok = ShortcutManagerCompat.isRequestPinShortcutSupported(this) && ShortcutManagerCompat.requestPinShortcut(this, info, null)
        if (!ok) toast(getString(R.string.brw_home_unsupported))
    }

    /** «Promemoria» della Lettura: evento già compilato nel Calendario (con l'ora, se c'è; se no, il giorno intero). */
    override fun addEvent(title: String, date: PageDate, url: String) {
        val name = title.ifBlank { Links.host(url) }.take(80)
        val intent = date.time?.let { t -> Reach.event(name, date.date.atTime(t), description = date.text.take(500) + "\n" + Links.clean(url)) }
            ?: Reach.allDayEvent(name, date.date)
        if (!Reach.open(this, intent)) toast(getString(R.string.brw_no_calendar))
    }

    /**
     * «Cattura intera»: l'articolo della vista Lettura disegnato in un'immagine lunga (titolo, testo, sito), che va a
     * «Salva in…». Si disegna sul telefono; fino a [MAX_CAPTURE] pixel di altezza, poi si taglia con i puntini.
     */
    override fun captureArticle(title: String, text: String, url: String) {
        if (text.isBlank()) { toast(getString(R.string.brw_capture_failed)); return }
        toast(getString(R.string.brw_capture_working))
        Thread {
            val file = runCatching { ArticleImage.render(this, title, text, Links.host(url), File(freshDir(), "${Links.host(url)} ${stamp()}.png")) }.getOrNull()
            runOnUiThread { if (file != null) offer(file, "image/png", "image") else toast(getString(R.string.brw_capture_failed)) }
        }.start()
    }

    // Tastiera (tablet e computer): Ctrl+T nuova scheda, Ctrl+W chiudi, Ctrl+Tab / Ctrl+Maiusc+Tab scheda accanto,
    // Ctrl+K cerca tra le schede, Ctrl+L indirizzo (lo fa l'interfaccia).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_TAB -> { model.neighbour(if (event.isShiftPressed) -1 else 1).also { if (!it) cycle(event.isShiftPressed) }; return true }
                KeyEvent.KEYCODE_K -> { model.tabsSearch = true; model.tabsOpen = true; return true }
                KeyEvent.KEYCODE_T -> { model.tabsOpen = false; model.open(); return true }
                KeyEvent.KEYCODE_W -> { model.current?.let(model::close); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Ctrl+Tab sull'ultima scheda torna alla prima (e al contrario). */
    private fun cycle(back: Boolean) {
        val t = if (back) model.tabs.lastOrNull() else model.tabs.firstOrNull()
        t?.let(model::show)
    }

    override fun openDownloads() {
        runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }.onFailure { toast(getString(R.string.brw_download_failed)) }
    }

    override fun exportBookmarks() {
        val file = File(freshDir(), "Segnalibri Eurise ${stamp()}.html")
        runCatching { file.writeText(model.exportBookmarks()) }
            .onSuccess { offer(file, "text/html", "document") }
            .onFailure { toast(getString(R.string.brw_import_failed)) }
    }

    // --- Importa (tavola BrwImportaPwd) ---

    private fun nameOf(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    /** Legge il file solo per contare: cosa c'è dentro. Fuori dal thread principale. */
    private fun scan(uri: Uri): PickedFile? {
        val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() ?: return null
        val name = nameOf(uri)
        return when {
            Imports.isPasswordCsv(text) -> PickedFile(uri, name, passwords = true, scan = Imports.scanPasswords(text))
            Imports.isBookmarksHtml(text) -> PickedFile(uri, name, passwords = false, scan = Imports.scanBookmarks(text))
            else -> PickedFile(uri, name, passwords = false, scan = ImportScan())
        }
    }

    /**
     * Segnalibri nel Browser; password a Eurise Password (una copia passata con il permesso di sola lettura: la
     * cifra lei, dopo la conferma). Poi, se richiesto, i file esportati in chiaro si cancellano.
     */
    private fun runImport(passwords: Boolean, bookmarks: Boolean, deleteFiles: Boolean) {
        val files = model.picked.toList()
        Thread {
            var added = 0
            if (bookmarks) files.filter { !it.passwords && it.scan.bookmarks > 0 }.forEach { f ->
                val html = runCatching { contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                if (html != null) runOnUiThread { added += model.importBookmarks(html) }
            }
            var handoff: File? = null
            if (passwords) {
                val csv = files.filter { it.passwords }
                if (csv.isNotEmpty()) {
                    val dir = freshDir()
                    val out = File(dir, "password.csv")
                    runCatching {
                        val texts = csv.mapNotNull { f -> contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() } }
                        // Più CSV: si tiene l'intestazione del primo e si aggiungono le righe degli altri con le stesse colonne.
                        val first = texts.first()
                        val header = first.trimStart('﻿').lineSequence().first()
                        val rest = texts.drop(1).filter { it.trimStart('﻿').lineSequence().first() == header }
                            .joinToString("") { "\n" + it.trimStart('﻿').substringAfter('\n') }
                        out.writeText(first.trimStart('﻿').trimEnd() + rest)
                    }.onSuccess { handoff = out }
                }
            }
            if (deleteFiles) files.forEach { f ->
                val take = (f.passwords && passwords && handoff != null) || (!f.passwords && bookmarks)
                if (take) runCatching { DocumentsContract.deleteDocument(contentResolver, f.uri) }
            }
            runOnUiThread {
                model.picked.clear()
                if (bookmarks) toast(getString(R.string.brw_import_done, added))
                handoff?.let { file ->
                    val uri = FileProvider.getUriForFile(this, "$packageName.salva", file)
                    val send = Intent(Intent.ACTION_SEND).setPackage(passwordPackage).setType("text/csv")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .also { it.clipData = ClipData.newRawUri("password.csv", uri) }
                    runCatching { startActivity(send) }.onFailure { toast(getString(R.string.brw_no_password_app)) }
                }
            }
        }.start()
    }

    override fun onDestroy() {
        tts?.shutdown()
        runCatching { unregisterReceiver(downloadDone) }
        super.onDestroy()
    }
}
