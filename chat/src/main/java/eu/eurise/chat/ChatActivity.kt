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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = ChatModel(applicationContext, lifecycleScope)
        handle(intent)
        setContent { EuriseTheme { ChatApp(model, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "eurisechat") return
        val token = data.getQueryParameter("loginToken") ?: return
        model.finishSso(token) { ok -> toast(getString(if (ok) R.string.ch_welcome else R.string.ch_err_sso)); if (ok) askNotifications() }
    }

    override fun onStart() {
        super.onStart()
        model.start()
    }

    override fun onStop() {
        model.stop()
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

    override fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    override fun text(id: Int): String = getString(id)

    override fun copySecret(text: String) {
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(getString(R.string.ch_rec_title), text)
        // Android 13+: il contenuto non compare nell'anteprima degli appunti.
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip)
    }

    override fun openPassword(key: String) {
        copySecret(key)
        val pkg = if (packageName.endsWith(".debug")) "eu.eurise.password.debug" else "eu.eurise.password"
        val launch = packageManager.getLaunchIntentForPackage(pkg)
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
            p{font-size:13px;line-height:1.6;color:#3A3A35}</style></head><body>
            <h1>${esc(getString(R.string.ch_rec_print_title))}</h1>
            <p>${esc(userId)} · ${esc(java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date()))}</p>
            <div class="k">${esc(key)}</div>
            <p>${esc(getString(R.string.ch_rec_print_note))}</p></body></html>"""
        val web = android.webkit.WebView(this)
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
}
