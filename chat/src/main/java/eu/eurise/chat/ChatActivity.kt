package eu.eurise.chat

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.theme.EuriseTheme
import java.io.File

/** Eurise Chat (Matrix). L'accesso dal sito del server torna qui con «eurisechat://login?loginToken=…». */
class ChatActivity : ComponentActivity(), ChatActions {
    private lateinit var model: ChatModel
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    /** Chi aspetta il file o la cartella scelti (un selettore alla volta). */
    private var calendarDone: ((Boolean) -> Unit)? = null
    private val calendar = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        calendarDone?.invoke(r.values.all { it }); calendarDone = null
    }

    private var micDone: ((Boolean) -> Unit)? = null
    private val mic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> micDone?.invoke(ok); micDone = null }

    private var locationDone: ((Boolean) -> Unit)? = null
    private val location = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        locationDone?.invoke(r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        locationDone = null
    }

    override fun askLocation(done: (Boolean) -> Unit) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (granted) { done(true); return }
        locationDone = done
        location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private var backgroundDone: ((Boolean) -> Unit)? = null
    private val backgroundLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> backgroundDone?.invoke(ok); backgroundDone = null }

    override fun askBackgroundLocation(done: (Boolean) -> Unit) {
        askLocation { fine ->
            if (!fine) return@askLocation done(false)
            if (Arrivo.background(this)) return@askLocation done(true)
            backgroundDone = done
            backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    override fun askMic(done: (Boolean) -> Unit) {
        micDone = done
        mic.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun askCalendar(done: (Boolean) -> Unit) {
        if (ShiftCalendar.allowed(this)) { done(true); return }
        calendarDone = done
        calendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    override fun shareFile(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.file", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri(file.name, uri) }
        runCatching { startActivity(Intent.createChooser(send, file.name)) }.onFailure { toast(getString(R.string.st_no_app)) }
    }

    override fun shareText(text: String) {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
            .onFailure { toast(getString(R.string.st_no_app)) }
    }

    private var picked: ((Uri?) -> Unit)? = null
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> picked?.invoke(uri); picked = null }
    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        picked?.invoke(uri); picked = null
    }

    override fun pickFile(mimes: Array<String>, done: (Uri?) -> Unit) {
        picked = done
        runCatching { openFile.launch(mimes) }.onFailure { picked = null; toast(getString(R.string.st_no_app)) }
    }

    override fun pickFolder(done: (Uri?) -> Unit) {
        picked = done
        runCatching { openFolder.launch(null) }.onFailure { picked = null; toast(getString(R.string.st_no_app)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = ChatModel(applicationContext, lifecycleScope)
        model.loadStudio()
        handle(intent)
        setContent { EuriseTheme { ChatApp(model, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Dalla notifica di un account: si apre quello.
        intent?.getStringExtra(EXTRA_ACCOUNT)?.let { name -> runCatching { Account.valueOf(name) }.getOrNull()?.let(model::switchTo) }
        // Da Email o Messaggi: «Apri nello Studio».
        if (intent?.action == eu.eurise.core.studio.StudioContract.ACTION_OPEN) {
            model.studioOpen(intent.getStringExtra(eu.eurise.core.studio.StudioContract.EXTRA_PATIENT))
            return
        }
        // Da Email (spese, scadenze, «chi va?», tappe, decisioni): solo se l'intent viene davvero da un'app della suite.
        if (intent?.action?.startsWith("eu.eurise.action.ADD_") == true || intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ASK_FAMILY) {
            if (!eu.eurise.core.insieme.SchedaContract.fromSuite(this, intent)) return
        }
        // Da Email: «Aggiungi alle spese di un gruppo» (tavola SpeEmail): si sceglie il gruppo, la spesa è già scritta.
        if (intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ADD_EXPENSE) {
            val cents = intent.getLongExtra(eu.eurise.core.insieme.SchedaContract.EXTRA_CENTS, 0L)
            if (cents > 0) model.pendingExpense = cents to intent.getStringExtra(eu.eurise.core.insieme.SchedaContract.EXTRA_TITLE).orEmpty().take(80)
            return
        }
        // Da Email: una scadenza per le Scadenze di casa (tavola FamScadenze), «chi va?» per la famiglia (tavola FamScuola).
        if (intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ADD_DEADLINE) {
            val C = eu.eurise.core.insieme.SchedaContract
            val title = intent.getStringExtra(C.EXTRA_TITLE).orEmpty().take(80)
            if (title.isNotBlank()) model.pendingDeadline = Triple(title, intent.getLongExtra(C.EXTRA_CENTS, 0L), intent.getLongExtra(C.EXTRA_DAY, 0L))
            return
        }
        if (intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ADD_STOP) {
            val C = eu.eurise.core.insieme.SchedaContract
            val title = intent.getStringExtra(C.EXTRA_TITLE).orEmpty().take(80)
            val day = intent.getLongExtra(C.EXTRA_DAY, 0L)
            if (title.isNotBlank() && day > 0) model.pendingStop = Amici.Stop("t" + System.nanoTime(), day, intent.getStringExtra(C.EXTRA_TIME).orEmpty().take(5), title,
                place = intent.getStringExtra(C.EXTRA_PLACE).orEmpty().take(100))
            return
        }
        if (intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ADD_DECISION) {
            val C = eu.eurise.core.insieme.SchedaContract
            val t = intent.getStringExtra(C.EXTRA_TITLE).orEmpty().take(300)
            if (t.isNotBlank()) model.pendingDecision = t to intent.getStringExtra(C.EXTRA_PLACE).orEmpty().take(80)
            return
        }
        if (intent?.action == eu.eurise.core.insieme.SchedaContract.ACTION_ASK_FAMILY) {
            val C = eu.eurise.core.insieme.SchedaContract
            val q = intent.getStringExtra(C.EXTRA_TITLE).orEmpty().take(200)
            val opts = intent.getStringArrayExtra(C.EXTRA_OPTIONS)?.toList().orEmpty().take(6)
            if (q.isNotBlank() && opts.size >= 2) model.pendingAsk = q to opts
            return
        }
        // Dalla scheda della Rubrica: «Invita sulla Chat» (tavola ChatInvita).
        if (intent?.action == eu.eurise.core.suite.PeopleContract.ACTION_INVITE) {
            val P = eu.eurise.core.suite.PeopleContract
            val who = People.Who(
                intent.getStringExtra(P.EXTRA_KEY).orEmpty(), intent.getStringExtra(P.EXTRA_NAME).orEmpty(),
                phones = intent.getStringArrayExtra(P.EXTRA_PHONES)?.toList().orEmpty(),
                emails = intent.getStringArrayExtra(P.EXTRA_EMAILS)?.toList().orEmpty(), contact = true,
            )
            if (who.name.isNotBlank()) model.uni.openInvite(who)
            return
        }
        val data = intent?.data ?: return
        if (data.scheme != "eurisechat") return
        // Ricordamelo (tavola ChatProgramma): dall'attività si torna al messaggio, nell'account giusto.
        if (data.host == "messaggio") {
            data.getQueryParameter("account")?.let { n -> runCatching { Account.valueOf(n) }.getOrNull()?.let(model::switchTo) }
            data.getQueryParameter("room")?.let(model::open)
            return
        }
        val token = data.getQueryParameter("loginToken") ?: return
        model.finishSso(token) { ok -> toast(getString(if (ok) R.string.ch_welcome else R.string.ch_err_sso)); if (ok) askNotifications() }
    }

    override fun onStart() {
        super.onStart()
        model.start()
        model.refreshLive()
        // Tornando nella Chat (magari da Messaggi o dal Telefono): il filo aperto si rilegge.
        if (model.uni.who != null) model.uni.loadThread()
    }

    override fun onDestroy() {
        model.voice.close()
        super.onDestroy()
    }

    override fun onStop() {
        model.stop()
        // Un vocale o una lettura ad alta voce non continuano con la Chat chiusa.
        model.voice.stopPlaying()
        if (!isChangingConfigurations) model.voice.stopSpeaking()
        super.onStop()
    }

    override fun openBrowser(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast(getString(R.string.ch_err_server)) }
    }

    override fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.file", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri(file.name, uri) }
        runCatching { startActivity(Intent.createChooser(view, file.name)) }
    }

    override fun launch(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { toast(getString(R.string.st_no_app)) }
    }

    override fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    override fun text(id: Int): String = getString(id)

    override fun copySecret(text: String) {
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(getString(R.string.ch_rec_title), text)
        // Android 13+: il contenuto non compare nell'anteprima degli appunti.
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip)
    }

    /**
     * «In Eurise Password»: la chiave arriva a Password come voce già compilata (si salva se confermi), senza passare
     * dagli appunti. Con una Password vecchia, che non ha ancora l'ingresso della suite: copia e apri, come prima.
     */
    override fun openPassword(key: String, userId: String) {
        val debug = packageName.endsWith(".debug")
        val add = eu.eurise.core.suite.PasswordContract.addIntent(this, debug, getString(R.string.ch_rec_password_entry), key,
            username = userId, url = "https://app.element.io", notes = getString(R.string.ch_rec_print_note))
        if (add != null && runCatching { startActivity(add) }.isSuccess) return
        copySecret(key)
        val launch = packageManager.getLaunchIntentForPackage(eu.eurise.core.suite.PasswordContract.passwordPackage(debug))
        if (launch != null) {
            toast(getString(R.string.ch_rec_copied))
            runCatching { startActivity(launch) }
        } else {
            toast(getString(R.string.ch_rec_password_missing))
        }
    }

    /** «Su carta»: una pagina semplice con la chiave, stampata con la stampa di Android (anche in PDF). */
    override fun printKey(key: String, userId: String) {
        val esc = { t: String -> t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val html = """<!doctype html><html><head><meta charset="utf-8"><style>
            body{font-family:sans-serif;margin:48px;color:#1E1D1B}h1{font-size:22px}
            .k{font-family:monospace;font-size:22px;font-weight:bold;letter-spacing:2px;line-height:1.8;padding:18px;border:2px solid #1F2A44;border-radius:12px}
            p{font-size:13px;line-height:1.6;color:#3A3A35}
            .q{display:flex;align-items:center;gap:18px;margin-top:22px}.q svg{width:150px;height:150px;flex:none}</style></head><body>
            <h1>${esc(getString(R.string.ch_rec_print_title))}</h1>
            <p>${esc(userId)} · ${esc(java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date()))}</p>
            <div class="k">${esc(key)}</div>
            <div class="q">${KeyQr.svg(key)}<p>${esc(getString(R.string.ch_rec_print_qr))}</p></div>
            <p>${esc(getString(R.string.ch_rec_print_note))}</p></body></html>"""
        val web = android.webkit.WebView(this)
        // Solo la pagina della chiave, scritta qui: niente JavaScript, file, contenuti di altre app né rete.
        web.settings.javaScriptEnabled = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.blockNetworkLoads = true
        web.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                val pm = getSystemService(android.print.PrintManager::class.java) ?: return
                pm.print(getString(R.string.ch_rec_print_title), view.createPrintDocumentAdapter("eurise-chat-chiave"), null)
            }
        }
        printing = web
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /** La pagina da stampare resta viva finché la stampa la usa. */
    private var printing: android.webkit.WebView? = null

    override fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** Quale account aprire (nome di [Account]), dalle notifiche. */
        const val EXTRA_ACCOUNT = "eu.eurise.chat.ACCOUNT"
    }
}
