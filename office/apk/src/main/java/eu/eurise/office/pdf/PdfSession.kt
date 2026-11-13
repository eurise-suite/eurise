package eu.eurise.office.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import eu.eurise.office.data.OfficeDocs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Come sta il file rispetto al suo posto d'origine (riga sotto il nome, tavola OffPdf). */
enum class SaveState { Saved, Saving, Pending, ReadOnly, Protected, Failed }

data class PdfState(
    val name: String = "",
    val where: String = "",
    val pages: List<PageGeometry> = emptyList(),
    val version: Int = 0,
    val save: SaveState = SaveState.Saved,
    val canUndo: Boolean = false,
    val needsPassword: Boolean = false,
    val error: Boolean = false,
    val hasForm: Boolean = false,
)

/**
 * Un PDF aperto nell'editor. PDFBox e il disegno delle pagine (PdfRenderer) non reggono più thread: tutto passa da un
 * solo thread di lavoro. Ogni modifica scrive lo stato in un file di vista (che PdfRenderer ridisegna), lo mette nello
 * storico per «Annulla» e, se Office può scrivere sull'originale, lo salva lì poco dopo.
 */
class PdfSession(private val context: Context, val uri: Uri, private val writable: Boolean, private val scope: CoroutineScope) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "pdf").apply { priority = Thread.NORM_PRIORITY - 1 } }
        .asCoroutineDispatcher()
    private val dir = File(context.cacheDir, "copie/" + UUID.randomUUID()).apply { mkdirs() }
    private val source = File(dir, "originale.pdf")
    private val views = arrayOf(File(dir, "vista-a.pdf"), File(dir, "vista-b.pdf"))
    private var view = source
    private var doc: PDDocument? = null
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var encrypted = false
    private val history = ArrayDeque<File>()
    private var saveJob: Job? = null
    private var texts: Array<PageText?> = emptyArray()
    private val extra = mutableListOf<PDDocument>()
    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val state = MutableStateFlow(PdfState())
    val current: StateFlow<PdfState> = state.asStateFlow()

    /** Il file di lavoro con lo stato attuale (per condividere una copia, stampare, oscurare). */
    val workingFile: File get() = view

    fun open(password: String? = null) = scope.launch {
        withContext(worker) {
            runCatching {
                if (!source.exists()) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    context.contentResolver.openInputStream(uri)!!.use { input -> source.outputStream().use { input.copyTo(it) } }
                }
                val info = OfficeDocs.info(context, uri)
                state.update { it.copy(name = info.name, where = OfficeDocs.where(context, uri), needsPassword = false) }
                val loaded = try {
                    PDDocument.load(source, password ?: "", MemoryUsageSetting.setupMixed(64L * 1024 * 1024))
                } catch (e: InvalidPasswordException) {
                    state.update { it.copy(needsPassword = true) }
                    return@runCatching
                }
                doc = loaded
                encrypted = loaded.isEncrypted
                if (encrypted) {
                    // PdfRenderer non apre i file protetti: la vista è una copia senza password; l'originale non si tocca.
                    loaded.isAllSecurityToBeRemoved = true
                    writeView()
                } else {
                    view = source
                    reopenRenderer()
                }
                texts = arrayOfNulls(loaded.numberOfPages)
                publish(SaveState.Saved.takeIf { writable && !encrypted } ?: if (encrypted) SaveState.Protected else SaveState.ReadOnly)
            }.onFailure { state.update { s -> s.copy(error = true) } }
        }
    }

    /** Una modifica al documento: [block] gira sul thread di lavoro, poi vista, storico e salvataggio. */
    fun edit(block: (PDDocument) -> Unit) = scope.launch {
        withContext(worker) {
            val d = doc ?: return@withContext
            runCatching {
                remember()
                block(d)
                texts = arrayOfNulls(d.numberOfPages)
                writeView()
                publish(if (writable && !encrypted) SaveState.Pending else state.value.save)
            }.onFailure { state.update { s -> s.copy(error = true) } }
        }
        if (writable && !encrypted) scheduleSave()
    }

    /** Come [edit] ma con un risultato, sul thread di lavoro (per operazioni che leggono senza cambiare). */
    suspend fun <T> read(block: (PDDocument) -> T): T? = withContext(worker) { doc?.let { runCatching { block(it) }.getOrNull() } }

    /** Tiene aperto un PDF da cui si sono importate pagine finché la sessione vive (PDFBox ne usa gli oggetti). */
    suspend fun keepOpen(other: PDDocument) = withContext(worker) { extra += other }

    fun undo() = scope.launch {
        withContext(worker) {
            val previous = history.removeLastOrNull() ?: return@withContext
            runCatching {
                closeRenderer()
                doc?.close()
                val target = views.first { it != view }
                previous.copyTo(target, overwrite = true)
                previous.delete()
                doc = PDDocument.load(target, MemoryUsageSetting.setupMixed(64L * 1024 * 1024))
                view = target
                reopenRenderer()
                texts = arrayOfNulls(doc!!.numberOfPages)
                publish(if (writable && !encrypted) SaveState.Pending else state.value.save)
            }.onFailure { state.update { s -> s.copy(error = true) } }
        }
        if (writable && !encrypted) scheduleSave()
    }

    /** La pagina [index] disegnata larga [widthPx] pixel (con le annotazioni). */
    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val version = state.value.version
        val key = "$version/$index/$widthPx"
        cache.get(key)?.let { return it }
        return withContext(worker) {
            val r = renderer ?: return@withContext null
            if (index !in 0 until r.pageCount) return@withContext null
            runCatching {
                r.openPage(index).use { page ->
                    val height = (widthPx * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    /** Il testo della pagina con le posizioni (letto la prima volta che serve). */
    suspend fun text(index: Int): PageText? = withContext(worker) {
        val d = doc ?: return@withContext null
        if (index !in texts.indices) return@withContext null
        texts[index] ?: PageText.readPage(d, index + 1).also { texts[index] = it }
    }

    suspend fun marks(index: Int): List<Mark> = read { d -> PdfEdits.marks(d.getPage(index)) }.orEmpty()

    /** Salva subito sull'originale (all'uscita). */
    suspend fun flush() {
        saveJob?.cancel()
        if (writable && !encrypted && state.value.save == SaveState.Pending) saveNow()
    }

    /** Chiude la sessione anche se lo schermo è già andato via: salva, chiude i file, pulisce la cache. */
    fun close() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            flush()
            withContext(worker) {
                closeRenderer()
                runCatching { doc?.close() }
                extra.forEach { runCatching { it.close() } }
                dir.deleteRecursively()
            }
            worker.close()
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            saveNow()
        }
    }

    private suspend fun saveNow() = withContext(worker) {
        val d = doc ?: return@withContext
        state.update { it.copy(save = SaveState.Saving) }
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { d.save(it) }
        }.isSuccess
        state.update { it.copy(save = if (ok) SaveState.Saved else SaveState.Failed) }
    }

    private fun remember() {
        val snapshot = File(dir, "storico-${System.nanoTime()}.pdf")
        view.copyTo(snapshot, overwrite = true)
        history.addLast(snapshot)
        while (history.size > MAX_UNDO) history.removeFirst().delete()
    }

    private fun writeView() {
        val d = doc ?: return
        closeRenderer()
        val target = views.first { it != view }
        target.outputStream().use { d.save(it) }
        view = target
        reopenRenderer()
    }

    private fun reopenRenderer() {
        closeRenderer()
        fd = ParcelFileDescriptor.open(view, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fd!!)
    }

    private fun closeRenderer() {
        runCatching { renderer?.close() }
        runCatching { fd?.close() }
        renderer = null
        fd = null
    }

    private fun publish(save: SaveState) {
        val d = doc ?: return
        val geometries = (0 until d.numberOfPages).map { i ->
            val p = d.getPage(i)
            val crop = p.cropBox
            PageGeometry(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, p.rotation)
        }
        val form = runCatching { d.documentCatalog.acroForm?.fields?.isNotEmpty() == true }.getOrDefault(false)
        state.update {
            it.copy(pages = geometries, version = it.version + 1, save = save, canUndo = history.isNotEmpty(), hasForm = form)
        }
    }

    companion object {
        private const val MAX_UNDO = 12

        private fun maxCacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(96L * 1024 * 1024).toInt()
    }
}
