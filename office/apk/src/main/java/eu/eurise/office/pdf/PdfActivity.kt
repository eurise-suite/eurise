package eu.eurise.office.pdf

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.data.Recents
import kotlinx.coroutines.launch

/** Strumento attivo sulla pagina (tavola OffPdf). */
enum class Tool { None, Highlight, Note, Pen, Text, Sign, Cover, Image }

/** Le cinque schede della barra in basso (tavola OffPdf). */
enum class PdfTab { Annotate, Edit, Pages, Suite, More }

/**
 * Stato dello schermo PDF: la sessione del documento e le scelte dell'utente (scheda, strumento, colore, ricerca).
 * Sopravvive alla rotazione; alla chiusura salva e pulisce.
 */
class PdfModel(app: Application, val uri: Uri, writable: Boolean) : AndroidViewModel(app) {
    val session = PdfSession(app, uri, writable, viewModelScope)

    var tab by mutableStateOf(PdfTab.Annotate)
    var tool by mutableStateOf(Tool.None)
    var color by mutableStateOf(InkColor.Sabbia)
    var underline by mutableStateOf(false)
    var penColor by mutableStateOf(InkColor.Notte)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var hits by mutableStateOf<List<Pair<Int, PdfRect>>>(emptyList())
    var hit by mutableStateOf(0)
    var selectedText by mutableStateOf("")
    var showPages by mutableStateOf(false)
    var showShare by mutableStateOf(false)
    var showForms by mutableStateOf(false)
    var showPad by mutableStateOf(false)
    var pendingImage by mutableStateOf<android.graphics.Bitmap?>(null)

    init {
        session.open()
    }

    override fun onCleared() {
        session.close()
    }

    /** Cerca nel testo di tutte le pagine; [hits] tiene un riquadro per ogni riga trovata. */
    fun search() {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            return
        }
        viewModelScope.launch {
            val found = mutableListOf<Pair<Int, PdfRect>>()
            for (i in session.current.value.pages.indices) {
                val text = session.text(i) ?: continue
                var from = 0
                while (true) {
                    val at = text.text.indexOf(q, from, ignoreCase = true)
                    if (at < 0) break
                    text.boxesForRange(at, at + q.length).firstOrNull()?.let { found += i to it }
                    from = at + q.length
                }
            }
            hits = found
            hit = 0
        }
    }

    class Factory(private val uri: Uri, private val writable: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return PdfModel(app, uri, writable) as T
        }
    }
}

/** Editor PDF di Office: si apre dai recenti, da «Apri con» e da Eurise File. */
class PdfActivity : ComponentActivity() {
    private val uri: Uri by lazy { intent?.data ?: Uri.EMPTY }
    private val writable: Boolean by lazy {
        intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || OfficeDocs.canWrite(this, uri)
    }
    private val model: PdfModel by viewModels { PdfModel.Factory(uri, writable) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (uri == Uri.EMPTY) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            OfficeDocs.keep(this, uri, intent.flags)
            lifecycleScope.launch {
                val info = OfficeDocs.info(this@PdfActivity, uri)
                Recents.get(this@PdfActivity).add(uri, info.name, OfficeDocs.where(this@PdfActivity, uri), DocKind.Pdf, writable)
            }
        }
        setContent {
            EuriseTheme {
                PdfScreen(model, writable, onBack = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { model.session.flush() }
    }
}
