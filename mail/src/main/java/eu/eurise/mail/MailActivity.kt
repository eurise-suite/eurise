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
import eu.eurise.core.suite.FilesContract
import eu.eurise.core.suite.HubBridge
import java.io.File

/** Eurise Email: la posta di tutte le caselle in una. Si apre anche dai link «mailto:» delle altre app. */
class MailActivity : ComponentActivity(), MailActions {
    private lateinit var model: MailModel
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var onPicked: ((List<Uri>) -> Unit)? = null
    private val fromFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { onPicked?.invoke(listOf(it)) }
    }
    private val fromSystem = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked?.invoke(uris)
    }

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
            // Da Office, File, Galleria, Scanner…: «Condividi» o «Invia per email» con i file allegati.
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> Draft(
                to = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.joinToString(", ").orEmpty(),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(),
                body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
                attachments = streams(intent),
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

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    override fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun text(id: Int): String = getString(id)

    override fun start(intent: Intent?): Boolean = intent != null && runCatching { startActivity(intent) }.isSuccess

    override fun toHub(task: Boolean, text: String) {
        val ok = HubBridge.send(this, BuildConfig.HUB_PACKAGE, BuildConfig.SUITE_PERMISSION,
            if (task) HubBridge.ACTION_TASK else HubBridge.ACTION_NOTE, text)
        if (!ok) toast(getString(R.string.ml_no_hub))
    }

    override fun saveIn(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.allegati", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val save = FilesContract.saveIn(BuildConfig.HUB_PACKAGE, uri, mime, file.name, "document", getString(R.string.ml_app_name))
        if (!start(save)) toast(getString(R.string.ml_no_hub))
    }

    override fun pickAttachments(onPicked: (List<Uri>) -> Unit) {
        this.onPicked = onPicked
        val files = FilesContract.openFrom(BuildConfig.HUB_PACKAGE, emptyList(), getString(R.string.ml_app_name))
        val ok = runCatching { fromFiles.launch(files) }.isSuccess
        if (!ok) runCatching { fromSystem.launch(arrayOf("*/*")) }
    }

    override fun openLink(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
