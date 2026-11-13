package eu.eurise.office.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.office.BuildConfig
import eu.eurise.office.R
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Engine
import eu.eurise.office.data.NewDocuments
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.data.Recent
import eu.eurise.office.data.Recents
import eu.eurise.office.data.Suite
import kotlinx.coroutines.launch

/** Schermata iniziale di Office (OffHome). Apre i recenti, «Apri da…» di Eurise File, crea documenti nuovi. */
class OfficeActivity : ComponentActivity(), HomeActions {
    private var query by mutableStateOf("")
    private var dialog by mutableStateOf<Int?>(null)
    private var showSettings by mutableStateOf(false)
    private var leftHanded by mutableStateOf(false)
    private val recents by lazy { Recents.get(this) }

    /** Eurise File («Apri da…»): torna con l'Uri e i permessi; senza l'app Eurise, il selettore di Android. */
    private val openFromSuite = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val uri = data.data ?: return@registerForActivityResult
        openPicked(uri, data.flags)
    }
    private val openFromSystem = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPicked(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            recents.load()
            leftHanded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { OfficePrefs.leftHanded(this@OfficeActivity) }
        }
        if (intent?.hasExtra(EXTRA_ENGINE_FOR) == true && !Engine.available()) dialog = R.string.office_engine_dialog
        setContent {
            EuriseTheme {
                val list by recents.items.collectAsStateWithLifecycle()
                HomeScreen(
                    recents = list,
                    query = query,
                    onQuery = { query = it },
                    engineReady = Engine.available(),
                    leftHanded = leftHanded,
                    actions = this,
                )
                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        confirmButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.office_ok)) } },
                        title = { Text(stringResource(R.string.office_settings)) },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(stringResource(R.string.office_left_handed), modifier = androidx.compose.ui.Modifier.weight(1f))
                                    androidx.compose.material3.Switch(checked = leftHanded, onCheckedChange = {
                                        leftHanded = it
                                        OfficePrefs.setLeftHanded(this@OfficeActivity, it)
                                    })
                                }
                                Text(stringResource(if (Engine.available()) R.string.office_about_engine else R.string.office_about_no_engine, BuildConfig.VERSION_NAME))
                            }
                        },
                    )
                }
                dialog?.let { text ->
                    AlertDialog(
                        onDismissRequest = { dialog = null },
                        confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.office_ok)) } },
                        text = { Text(stringResource(text, BuildConfig.VERSION_NAME)) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_ENGINE_FOR) && !Engine.available()) dialog = R.string.office_engine_dialog
    }

    private fun openPicked(uri: Uri, flags: Int) {
        OfficeDocs.keep(this, uri, flags)
        val writable = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
        lifecycleScope.launch {
            val info = OfficeDocs.info(this@OfficeActivity, uri)
            val kind = DocKind.of(info.name, contentResolver.getType(uri))
            recents.add(uri, info.name, OfficeDocs.where(this@OfficeActivity, uri), kind, writable)
            start(uri, kind, writable)
        }
    }

    private fun start(uri: Uri, kind: DocKind, writable: Boolean) {
        val ok = runCatching { Engine.open(this, uri, contentResolver.getType(uri), kind, writable) }
        when {
            ok.isFailure -> Toast.makeText(this, R.string.office_cannot_open, Toast.LENGTH_LONG).show()
            ok.getOrDefault(false).not() -> dialog = R.string.office_engine_dialog
        }
    }

    override fun open(recent: Recent) {
        val readable = runCatching { contentResolver.openFileDescriptor(recent.uri, "r")?.close() }.isSuccess
        if (!readable) {
            Toast.makeText(this, R.string.office_recent_gone, Toast.LENGTH_LONG).show()
            lifecycleScope.launch { recents.remove(recent.uri) }
            return
        }
        lifecycleScope.launch {
            recents.add(recent.uri, recent.name, recent.where, recent.kind, recent.writable)
            start(recent.uri, recent.kind, recent.writable && OfficeDocs.canWrite(this@OfficeActivity, recent.uri))
        }
    }

    override fun openFrom() {
        val fromSuite = Suite.installed(this) && runCatching {
            openFromSuite.launch(Suite.openFrom(DocKind.openable, getString(R.string.office_app_name)))
        }.isSuccess
        if (!fromSuite) openFromSystem.launch(arrayOf("*/*"))
    }

    override fun create(kind: DocKind) {
        if (kind != DocKind.Pdf && !Engine.available()) {
            dialog = R.string.office_engine_dialog
            return
        }
        lifecycleScope.launch {
            val title = getString(
                when (kind) {
                    DocKind.Sheet -> R.string.office_new_sheet_name
                    DocKind.Slides -> R.string.office_new_slides_name
                    DocKind.Pdf -> R.string.office_new_pdf_name
                    else -> R.string.office_new_text_name
                },
            )
            val created = runCatching { NewDocuments.create(this@OfficeActivity, kind, title) }.getOrElse {
                Toast.makeText(this@OfficeActivity, R.string.office_cannot_create, Toast.LENGTH_LONG).show()
                return@launch
            }
            recents.add(created.uri, created.name, getString(R.string.office_where_documents), kind, true)
            start(created.uri, kind, true)
        }
    }

    override fun note() {
        if (!Suite.section(this, "notes")) Toast.makeText(this, R.string.office_needs_eurise, Toast.LENGTH_LONG).show()
    }

    override fun scan() {
        if (!Suite.section(this, "scanner")) Toast.makeText(this, R.string.office_needs_eurise, Toast.LENGTH_LONG).show()
    }

    override fun calendar() {
        if (!Suite.section(this, "calendar")) Toast.makeText(this, R.string.office_needs_eurise, Toast.LENGTH_LONG).show()
    }

    override fun settings() {
        showSettings = true
    }

    override fun downloadEngine() {
        dialog = R.string.office_engine_dialog
    }

    override fun files() = openFrom()

    override fun templates() {
        dialog = R.string.office_templates_soon
    }

    companion object {
        /** Un documento arrivato con «Apri con» mentre il motore manca: si propone di scaricarlo. */
        const val EXTRA_ENGINE_FOR = "eu.eurise.office.extra.ENGINE_FOR"
    }
}
