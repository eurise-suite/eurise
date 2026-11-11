package eu.eurise.mail

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

/** Eurise Email: la posta di tutte le caselle in una. Si apre anche dai link «mailto:» delle altre app. */
class MailActivity : ComponentActivity(), MailActions {
    private lateinit var model: MailModel
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = MailModel(applicationContext, lifecycleScope)
        if (model.accounts.isNotEmpty()) SyncWorker.schedule(this)
        val draft = draftFrom(intent)
        setContent { EuriseTheme { MailApp(model, this, draft) } }
    }

    /** «mailto:», «Invia email» e «Condividi» delle altre app: si apre Scrivi già compilato. */
    private fun draftFrom(intent: Intent?): Draft? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "mailto" }?.let { uri ->
                val raw = uri.schemeSpecificPart.orEmpty()
                val to = raw.substringBefore('?')
                val query = raw.substringAfter('?', "")
                fun param(name: String) = query.split('&').firstOrNull { it.startsWith("$name=", ignoreCase = true) }
                    ?.substringAfter('=')?.let { Uri.decode(it) }.orEmpty()
                Draft(to = Uri.decode(to), cc = param("cc"), subject = param("subject"), body = param("body"))
            }
            Intent.ACTION_SEND -> Draft(
                to = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.joinToString(", ").orEmpty(),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(),
                body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            )
            else -> null
        }
    }

    override fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.allegati", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri(file.name, uri) }
        runCatching { startActivity(Intent.createChooser(view, file.name)) }.onFailure { toast(getString(R.string.ml_attachment_failed)) }
    }

    override fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
