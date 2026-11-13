package eu.eurise.office.rapida

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import eu.eurise.core.suite.FilesContract
import eu.eurise.office.R
import eu.eurise.office.data.OfficeDocs
import eu.eurise.office.data.Suite
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** I formati che la vista rapida sa aprire. Il resto va direttamente al motore. */
object QuickView {
    private const val MAX_BYTES = 40L * 1024 * 1024

    fun text(name: String?, mime: String?): TextFormat? = when (ext(name, mime)) {
        "docx" -> Docx
        "odt" -> Odt
        else -> null
    }

    fun sheet(name: String?, mime: String?): SheetFormat? = when (ext(name, mime)) {
        "xlsx" -> Xlsx
        "ods" -> Ods
        else -> null
    }

    fun supports(name: String?, mime: String?, size: Long? = null): Boolean =
        (text(name, mime) != null || sheet(name, mime) != null) && (size == null || size <= MAX_BYTES)

    fun mimeOf(name: String?, mime: String?): String = when (ext(name, mime)) {
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        else -> mime ?: "application/octet-stream"
    }

    private fun ext(name: String?, mime: String?): String {
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { return it }
        return when (mime) {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.oasis.opendocument.text" -> "odt"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
            else -> ""
        }
    }
}

enum class QuickSave { Saved, Saving, ReadOnly, Failed }

sealed interface QuickState {
    object Loading : QuickState
    data class Failed(val reason: Int) : QuickState
    data class Text(val doc: TextDoc) : QuickState
    data class Sheets(val doc: SheetDoc) : QuickState
}

/**
 * La vista rapida di un documento (tavole OffLettura, OffLetturaCorreggi, OffLetturaFoglio): lo legge dal telefono senza
 * motore, applica le correzioni al file e lo salva al suo posto subito dopo ognuna. Se il file è in sola lettura le
 * correzioni restano qui e si salvano come copia con «Salva in…».
 */
class QuickModel(app: Application, val uri: Uri, val writable: Boolean) : AndroidViewModel(app) {
    var name by mutableStateOf("")
        private set
    var where by mutableStateOf("")
        private set
    var mime by mutableStateOf("")
        private set
    var state by mutableStateOf<QuickState>(QuickState.Loading)
        private set
    var save by mutableStateOf(if (writable) QuickSave.Saved else QuickSave.ReadOnly)
        private set
    /** Correzioni fatte su un file in sola lettura, non ancora salvate come copia. */
    var unsaved by mutableStateOf(false)
        private set
    var canUndo by mutableStateOf(false)
        private set
    /** Un messaggio breve da mostrare una volta (id di stringa e argomento). */
    var notice by mutableStateOf<Pair<Int, String?>?>(null)

    // Scelte dello schermo
    var commenting by mutableStateOf(false)
    var showCorrect by mutableStateOf(false)
    var showFull by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var sheetIndex by mutableStateOf(0)
    var selected by mutableStateOf<Pair<Int, Int>?>(null)
    /** Le celle appena ricalcolate e quelle in attesa della modifica completa, per l'ultima modifica. */
    var lastResult by mutableStateOf<CellResult?>(null)

    private var bundle: Bundle? = null
    private val history = ArrayDeque<Bundle>()
    private val lock = Mutex()
    private var text: TextFormat? = null
    private var sheet: SheetFormat? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val context = getApplication<Application>()
        val info = withContext(Dispatchers.IO) { OfficeDocs.info(context, uri) }
        name = info.name
        where = OfficeDocs.where(context, uri)
        mime = QuickView.mimeOf(info.name, context.contentResolver.getType(uri))
        text = QuickView.text(info.name, mime)
        sheet = QuickView.sheet(info.name, mime)
        val read = withContext(Dispatchers.IO) {
            runCatching { Bundle.read(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }) }.getOrNull()
        }
        if (read == null) { state = QuickState.Failed(R.string.quick_cannot_read); return }
        bundle = read
        show(read)
    }

    private suspend fun show(b: Bundle) {
        state = withContext(Dispatchers.Default) {
            runCatching {
                text?.let { QuickState.Text(it.read(b)) } ?: sheet?.let { QuickState.Sheets(it.read(b)) } ?: QuickState.Failed(R.string.quick_unsupported)
            }.getOrElse { QuickState.Failed(R.string.quick_cannot_read) }
        }
    }

    /**
     * Corregge il paragrafo [id]. Il testo può contenere a capo (\n: paragrafi nuovi dopo questo) e a capo «morbidi»
     * ( , restano nello stesso paragrafo).
     */
    fun correct(id: Int, draft: String, style: Style) = change { b ->
        val format = text ?: error("non è un testo")
        val parts = draft.split('\n').map { it.replace(' ', '\n') }
        var next = format.edit(b, id, parts.first(), style)
        // I paragrafi nuovi nascono uno dopo l'altro: ogni inserimento sposta di uno gli indici dopo.
        parts.drop(1).forEachIndexed { i, part -> next = format.insertAfter(next, id + i, part) }
        next
    }

    fun comment(id: Int, note: String, author: String) = change { b -> (text ?: error("non è un testo")).comment(b, id, note, author) }

    fun setCell(col: Int, row: Int, input: String) = change { b ->
        val result = (sheet ?: error("non è un foglio")).setCell(b, sheetIndex, col, row, input)
        lastResult = result
        result.bundle
    }

    fun undo() = viewModelScope.launch {
        lock.withLock {
            val previous = history.removeLastOrNull() ?: return@withLock
            canUndo = history.isNotEmpty()
            lastResult = null
            bundle = previous
            show(previous)
            persist(previous)
        }
    }

    private fun change(block: (Bundle) -> Bundle) = viewModelScope.launch {
        lock.withLock {
            val current = bundle ?: return@withLock
            val next = withContext(Dispatchers.Default) {
                runCatching { block(current) }.onFailure { e ->
                    notice = if (e is NotEditable) R.string.quick_not_editable to null else R.string.quick_change_failed to (e.message ?: "")
                }.getOrNull()
            } ?: return@withLock
            history.addLast(current)
            while (history.size > 20) history.removeFirst()
            canUndo = true
            bundle = next
            show(next)
            persist(next)
        }
    }

    /** Scrive sul file originale; se non si può, le correzioni aspettano «Salva in…». */
    private suspend fun persist(b: Bundle) {
        if (!writable) { unsaved = true; return }
        save = QuickSave.Saving
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = b.toBytes()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            }.isSuccess
        }
        save = if (ok) QuickSave.Saved else QuickSave.Failed
        if (!ok) unsaved = true
    }

    /** Una copia del documento com'è ora, per «Salva in…», per condividere o per il motore. */
    suspend fun copy(context: Context): Uri? = withContext(Dispatchers.IO) {
        val b = bundle ?: return@withContext null
        val dir = File(context.cacheDir, "copie/uscita").apply { mkdirs() }
        val target = File(dir, name.replace(Regex("""[\\/:*?"<>|]"""), "-").ifBlank { "documento" })
        target.writeBytes(b.toBytes())
        FileProvider.getUriForFile(context, context.packageName + ".files", target)
    }

    fun saveIn(context: Context) = viewModelScope.launch {
        val copy = copy(context) ?: return@launch
        val intent = FilesContract.saveIn(Suite.hub, copy, mime, name, "office", context.getString(R.string.office_app_name))
        try {
            context.startActivity(intent)
            unsaved = false
        } catch (e: ActivityNotFoundException) {
            notice = R.string.office_needs_eurise to null
        } catch (e: SecurityException) {
            notice = R.string.office_needs_eurise to null
        }
    }

    class Factory(private val uri: Uri, private val writable: Boolean) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return QuickModel(app, uri, writable) as T
        }
    }
}
