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

    override fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
