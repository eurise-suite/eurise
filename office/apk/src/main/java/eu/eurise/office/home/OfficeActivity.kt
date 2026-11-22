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
import eu.eurise.core.suite.FilesContract
import eu.eurise.office.BuildConfig
import eu.eurise.office.R
import eu.eurise.office.data.Appearance
import androidx.compose.material3.MaterialTheme
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Engine
import eu.eurise.office.data.EngineInstaller
import eu.eurise.office.data.EngineState
import eu.eurise.office.data.NewDocuments
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.data.Recent
import eu.eurise.office.data.Recents
import eu.eurise.office.data.Reports
import eu.eurise.office.data.Suite
import kotlinx.coroutines.launch

/** Schermata iniziale di Office (OffHome). Apre i recenti, «Apri da…» di Eurise File, crea documenti nuovi. */
class OfficeActivity : ComponentActivity(), HomeActions {
    private var query by mutableStateOf("")
    private var dialog by mutableStateOf<Int?>(null)
    private var showSettings by mutableStateOf(false)
    /** Tavola OffMotore aperta, e il documento che aspetta il motore (si apre da solo appena è installato). */
    private var engineSheet by mutableStateOf(false)
    private var pending by mutableStateOf<Uri?>(null)
    private var pendingFlags = 0
    private var onWifi by mutableStateOf(true)
    private var leftHanded by mutableStateOf(false)
    /** Vero quando la mano viene dalla Scrivania: allora l'interruttore di Office non serve. */
    private var handFromSuite by mutableStateOf(false)
    /** Tinte offerte per l'accento delle icone e scelta attuale (vuota = come la Scrivania). */
    private var accentTints by mutableStateOf<List<Appearance.Tint>>(emptyList())
    private var accentChoice by mutableStateOf(Appearance.DESK)
    private val recents by lazy { Recents.get(this) }
    /** Foglio «File dal cloud» della dock (pressione lunga su Cloud) e i cloud di Eurise File. */
    private var cloudSheet by mutableStateOf(false)
    private var clouds by mutableStateOf<List<Suite.Place>>(emptyList())
    private var favoriteCloud by mutableStateOf("")
    /** Rapporto del problema: la scheda della schermata iniziale, la schermata aperta e quanti sono sul telefono. */
    private var cardReport by mutableStateOf<Reports.Report?>(null)
    private var reportsOpen by mutableStateOf(false)
    private var reportFirst by mutableStateOf<Reports.Report?>(null)
    private var reports by mutableStateOf<List<Reports.Report>>(emptyList())

    /** Eurise File («Apri da…»): torna con l'Uri e i permessi; senza l'app Eurise, il selettore di Android. */
    private val openFromSuite = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val uri = data.data ?: return@registerForActivityResult
        // La cartella del documento diventa quella di riferimento del suo cloud (la riga del foglio «File dal cloud»).
        data.getStringExtra(FilesContract.EXTRA_ORIGIN)?.let { origin ->
            val account = origin.substringBefore(':')
            if (clouds.any { it.id == account }) OfficePrefs.setCloudFolder(this, account, origin.substringBeforeLast('/', "$account:"))
        }
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
            val suiteHand = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Appearance.suiteLeftHanded(this@OfficeActivity) }
            handFromSuite = suiteHand != null
            leftHanded = suiteHand ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { OfficePrefs.leftHanded(this@OfficeActivity) }
            OfficePrefs.setLeftHanded(this@OfficeActivity, leftHanded)
            accentChoice = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { OfficePrefs.accent(this@OfficeActivity) }
            accentTints = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Appearance.palette(this@OfficeActivity) }
            loadClouds()
            loadReports()
        }
        engineFor(intent)
        setContent {
            EuriseTheme {
                val list by recents.items.collectAsStateWithLifecycle()
                val engine by EngineInstaller.state.collectAsStateWithLifecycle()
                if (reportsOpen) {
                    ReportScreen(
                        reports = reports,
                        first = reportFirst,
                        onShare = { text ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_title)).putExtra(Intent.EXTRA_TEXT, text)
                            startActivity(Intent.createChooser(send, getString(R.string.report_share)))
                        },
                        onCopy = { text ->
                            getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.report_title), text))
                            Toast.makeText(this@OfficeActivity, R.string.report_copied, Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { r ->
                            lifecycleScope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Reports.delete(this@OfficeActivity, r.id) }
                                if (cardReport?.id == r.id) cardReport = null
                                reportFirst = null
                                loadReports()
                                if (reports.isEmpty()) reportsOpen = false
                            }
                        },
                        onClose = { reportsOpen = false; reportFirst = null },
                    )
                    return@EuriseTheme
                }
                HomeScreen(
                    recents = list,
                    query = query,
                    onQuery = { query = it },
                    engineReady = engine == EngineState.Ready,
                    leftHanded = leftHanded,
                    actions = this,
                    report = cardReport,
                    onSeeReport = {
                        reportFirst = cardReport
                        reportsOpen = true
                        cardReport = null
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { Reports.dismiss(this@OfficeActivity) }
                    },
                    onIgnoreReport = {
                        cardReport = null
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { Reports.dismiss(this@OfficeActivity) }
                    },
                )
                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        confirmButton = { TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.office_ok)) } },
                        title = { Text(stringResource(R.string.office_settings)) },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                if (handFromSuite) Text(
                                    stringResource(if (leftHanded) R.string.office_hand_suite_left else R.string.office_hand_suite_right),
                                    style = MaterialTheme.typography.bodySmall,
                                ) else androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(stringResource(R.string.office_left_handed), modifier = androidx.compose.ui.Modifier.weight(1f))
                                    androidx.compose.material3.Switch(checked = leftHanded, onCheckedChange = {
                                        leftHanded = it
                                        OfficePrefs.setLeftHanded(this@OfficeActivity, it)
                                    })
                                }
                                if (reports.isNotEmpty()) TextButton(onClick = { showSettings = false; reportFirst = null; reportsOpen = true }) {
                                    Text(stringResource(R.string.report_settings, reports.size))
                                }
                                AccentChooser(
                                    tints = accentTints,
                                    chosen = accentChoice,
                                    onChoose = {
                                        accentChoice = it
                                        OfficePrefs.setAccent(this@OfficeActivity, it)
                                    },
                                )
                                Text(
                                    if (engine == EngineState.Ready) stringResource(R.string.office_about_engine, BuildConfig.VERSION_NAME, Engine.version(this@OfficeActivity).orEmpty())
                                    else stringResource(R.string.office_about_no_engine, BuildConfig.VERSION_NAME),
                                )
                            }
                        },
                    )
                }
                if (cloudSheet) {
                    CloudSheet(
                        clouds = clouds,
                        favorite = favoriteCloud,
                        folderOf = { id -> OfficePrefs.cloudFolder(this@OfficeActivity, id)?.substringAfter(':')?.ifEmpty { null }?.replace("/", " › ") },
                        onOpen = { cloudSheet = false; openCloud(it.id) },
                        onFavorite = {
                            favoriteCloud = it.id
                            OfficePrefs.setFavoriteCloud(this@OfficeActivity, it.id)
                        },
                        onOtherApp = { cloudSheet = false; openFromSystem.launch(arrayOf("*/*")) },
                        onConnect = {
                            cloudSheet = false
                            if (!Suite.section(this@OfficeActivity, "cloud")) Toast.makeText(this@OfficeActivity, R.string.office_needs_eurise, Toast.LENGTH_LONG).show()
                        },
                        onDismiss = { cloudSheet = false },
                    )
                }
                if (engineSheet) {
                    EngineSheet(
                        state = engine,
                        onWifi = onWifi,
                        hasPending = pending != null,
                        onDownload = { EngineInstaller.start(this@OfficeActivity) },
                        onCancel = EngineInstaller::cancel,
                        onOpenPending = ::openPending,
                        onDismiss = { engineSheet = false },
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
        engineFor(intent)
    }

    override fun onResume() {
        super.onResume()
        EngineInstaller.refresh(this)
        onWifi = EngineInstaller.onUnmeteredNetwork(this)
        // Tornando da un documento: se il motore si è chiuso da solo, la scheda del rapporto compare subito.
        lifecycleScope.launch { loadReports() }
    }

    /** Un documento arrivato mentre il motore manca: lo si tiene (con i suoi permessi) e si apre la tavola OffMotore. */
    private fun engineFor(intent: Intent?) {
        intent ?: return
        val uri = intent.data ?: intent.getStringExtra(EXTRA_ENGINE_FOR)?.let(Uri::parse) ?: return
        if (!intent.hasExtra(EXTRA_ENGINE_FOR)) return
        pending = uri
        pendingFlags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        showEngine()
    }

    private fun showEngine() {
        EngineInstaller.refresh(this)
        onWifi = EngineInstaller.onUnmeteredNetwork(this)
        engineSheet = true
    }

    private fun openPending() {
        val uri = pending ?: return
        pending = null
        engineSheet = false
        val writable = pendingFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
        lifecycleScope.launch {
            val info = OfficeDocs.info(this@OfficeActivity, uri)
            val kind = DocKind.of(info.name, contentResolver.getType(uri))
            val ok = runCatching { Engine.open(this@OfficeActivity, uri, contentResolver.getType(uri), kind, writable, info.name, full = true) }
            if (ok.getOrDefault(false).not()) Toast.makeText(this@OfficeActivity, R.string.office_cannot_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun openPicked(uri: Uri, flags: Int) {
        OfficeDocs.keep(this, uri, flags)
        val writable = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
        lifecycleScope.launch {
            val info = OfficeDocs.info(this@OfficeActivity, uri)
            val kind = DocKind.of(info.name, contentResolver.getType(uri))
            recents.add(uri, info.name, OfficeDocs.where(this@OfficeActivity, uri), kind, writable)
            start(uri, kind, writable, info.name)
        }
    }

    private fun start(uri: Uri, kind: DocKind, writable: Boolean, name: String?, full: Boolean = false) {
        val ok = runCatching { Engine.open(this, uri, contentResolver.getType(uri), kind, writable, name, full) }
        when {
            ok.isFailure -> Toast.makeText(this, R.string.office_cannot_open, Toast.LENGTH_LONG).show()
            ok.getOrDefault(false).not() -> showEngine()
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
            start(recent.uri, recent.kind, recent.writable && OfficeDocs.canWrite(this@OfficeActivity, recent.uri), recent.name)
        }
    }

    override fun openFrom() {
        val fromSuite = Suite.installed(this) && runCatching {
            openFromSuite.launch(Suite.openFrom(this, DocKind.openable, getString(R.string.office_app_name))!!)
        }.isSuccess
        if (!fromSuite) openFromSystem.launch(arrayOf("*/*"))
    }

    override fun create(kind: DocKind) {
        // Testi e fogli nuovi si scrivono anche nella vista rapida; le presentazioni vogliono il motore.
        if (kind != DocKind.Pdf && kind != DocKind.Text && kind != DocKind.Sheet && !Engine.available(this)) {
            showEngine()
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
            // Una presentazione nuova si scrive: va subito nel motore (la vista rapida delle presentazioni è in lettura).
            start(created.uri, kind, true, created.name, full = kind == DocKind.Slides)
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

    override fun downloadEngine() = showEngine()

    private suspend fun loadReports() {
        val (card, all) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Reports.refresh(this@OfficeActivity) to Reports.all(this@OfficeActivity)
        }
        cardReport = card
        reports = all
    }

    private suspend fun loadClouds() {
        clouds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Suite.clouds(this@OfficeActivity) }
        favoriteCloud = OfficePrefs.favoriteCloud(this).takeIf { id -> clouds.any { it.id == id } }
            ?: clouds.singleOrNull()?.id.orEmpty()
    }

    /** «Apri da…» di Eurise File già in [start]; senza l'app Eurise il selettore di Android. */
    private fun openFromAt(start: String) {
        val fromSuite = Suite.installed(this) && runCatching {
            openFromSuite.launch(Suite.openFrom(this, DocKind.openable, getString(R.string.office_app_name), start)!!)
        }.isSuccess
        if (!fromSuite) openFromSystem.launch(arrayOf("*/*"))
    }

    private fun openCloud(id: String) = openFromAt(OfficePrefs.cloudFolder(this, id) ?: "$id:")

    override fun phoneFiles() = openFromAt(FilesContract.START_PHONE)

    override fun cloud() {
        lifecycleScope.launch {
            loadClouds()
            // Senza Eurise File (o senza cloud collegati) il cloud si raggiunge dalla sua app, con il selettore di Android.
            if (!Suite.installed(this@OfficeActivity)) { openFromSystem.launch(arrayOf("*/*")); return@launch }
            if (favoriteCloud.isNotEmpty()) openCloud(favoriteCloud) else cloudSheet = true
        }
    }

    override fun chooseCloud() {
        lifecycleScope.launch { loadClouds(); cloudSheet = true }
    }

    companion object {
        /**
         * Un documento arrivato con «Apri con» mentre il motore manca: si propone di scaricarlo. L'Uri viaggia anche
         * come dato dell'intent, con i permessi ricevuti, così dopo l'installazione si apre senza chiederlo di nuovo.
         */
        const val EXTRA_ENGINE_FOR = "eu.eurise.office.extra.ENGINE_FOR"
    }
}
