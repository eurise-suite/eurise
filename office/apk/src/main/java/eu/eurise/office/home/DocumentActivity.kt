package eu.eurise.office.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import eu.eurise.office.R
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Engine
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.data.Recents
import kotlinx.coroutines.launch

/**
 * «Apri con» Office da Eurise File o da un'altra app. Non ha finestra: segna il documento tra i recenti e lo passa al
 * motore con gli stessi permessi ricevuti (lettura, e scrittura se c'è: così si salva al suo posto). Senza motore
 * porta alla schermata iniziale, che propone di scaricarlo (tavola OffMotore) e tiene il documento in attesa.
 */
class DocumentActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val flags = intent.flags
        OfficeDocs.keep(this, uri, flags)
        val writable = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
        lifecycleScope.launch {
            val info = OfficeDocs.info(this@DocumentActivity, uri)
            val kind = DocKind.of(info.name, intent.type)
            Recents.get(this@DocumentActivity).add(uri, info.name, OfficeDocs.where(this@DocumentActivity, uri), kind, writable)
            val opened = runCatching { Engine.open(this@DocumentActivity, uri, intent.type, kind, writable, info.name) }.getOrDefault(false)
            if (!opened) {
                if (kind != DocKind.Pdf) Toast.makeText(this@DocumentActivity, R.string.office_engine_needed, Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this@DocumentActivity, OfficeActivity::class.java)
                        .putExtra(OfficeActivity.EXTRA_ENGINE_FOR, uri.toString())
                        .setData(uri)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)),
                )
            }
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
