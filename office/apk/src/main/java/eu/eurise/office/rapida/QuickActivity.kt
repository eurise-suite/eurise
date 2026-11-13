package eu.eurise.office.rapida

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Engine
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.home.OfficeActivity
import kotlinx.coroutines.launch

/**
 * La vista rapida di Office: Word, Excel e OpenDocument si aprono subito, senza motore. «Completa» e «Aspetto esatto»
 * passano lo stesso file al motore, con gli stessi permessi; se il motore non c'è, Office propone di scaricarlo.
 */
class QuickActivity : ComponentActivity() {
    private val uri: Uri by lazy { intent?.data ?: Uri.EMPTY }
    private val writable: Boolean by lazy {
        intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
    }
    private val model: QuickModel by viewModels { QuickModel.Factory(uri, writable) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (uri == Uri.EMPTY) { finish(); return }
        setContent {
            EuriseTheme {
                QuickScreen(
                    model,
                    engineReady = Engine.available(this),
                    onBack = { finish() },
                    onFull = ::full,
                    onShare = ::share,
                )
            }
        }
    }

    /** Lo stesso documento nel motore. Le correzioni sono già nel file; in sola lettura il motore riceve la copia. */
    private fun full() {
        lifecycleScope.launch {
            val kind = DocKind.of(model.name, model.mime)
            val target = if (writable || !model.unsaved) uri else model.copy(this@QuickActivity) ?: uri
            val opened = runCatching { Engine.open(this@QuickActivity, target, model.mime, kind, writable && target == uri, full = true) }.getOrDefault(false)
            if (!opened) {
                startActivity(
                    Intent(this@QuickActivity, OfficeActivity::class.java)
                        .putExtra(OfficeActivity.EXTRA_ENGINE_FOR, uri.toString())
                        .setData(uri)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)),
                )
            } else {
                finish()
            }
        }
    }

    private fun share() {
        lifecycleScope.launch {
            val copy = model.copy(this@QuickActivity) ?: return@launch
            val send = Intent(Intent.ACTION_SEND).setType(model.mime).putExtra(Intent.EXTRA_STREAM, copy)
                .putExtra(Intent.EXTRA_TITLE, model.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(model.name, copy)
            startActivity(Intent.createChooser(send, null))
        }
    }
}
