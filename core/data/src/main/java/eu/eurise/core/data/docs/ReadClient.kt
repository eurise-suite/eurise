package eu.eurise.core.data.docs

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import eu.eurise.core.suite.CardContract
import eu.eurise.core.suite.ReadContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/** Testo e parole di una pagina letta; i riquadri delle parole sono nei pixel [width]×[height] dell'immagine letta. */
class ReadPage(val text: String, val words: List<OcrWord>, val width: Int, val height: Int)

/**
 * Chiede a Eurise Leggi di leggere delle pagine ([ReadContract]). Le immagini passano per [ReadPagesProvider] con un
 * permesso temporaneo valido solo per Leggi, e si cancellano subito dopo. Tutto resta sul telefono.
 */
object ReadClient {
    sealed interface Result {
        class Pages(val pages: List<ReadPage>) : Result
        /** Eurise Leggi non è installata. */
        data object NotInstalled : Result
        /** Leggi c'è, ma manca la lingua: va aperta per scaricarla. */
        data object NoModel : Result
        data object Failed : Result
    }

    private const val TIMEOUT_MS = 180_000L

    fun readPackage(context: Context) = CardContract.readPackage(context.packageName.endsWith(".debug"))

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(readPackage(context), 0); true
    }.getOrDefault(false)

    /** L'app Leggi, per scaricare le lingue; null se non c'è. */
    fun setupIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(readPackage(context))

    /** Legge le immagini [images] (JPEG o PNG), nell'ordine. */
    suspend fun read(context: Context, images: List<File>): Result {
        if (images.isEmpty()) return Result.Pages(emptyList())
        if (!installed(context)) return Result.NotInstalled
        val out = ArrayList<ReadPage>()
        for (batch in images.chunked(ReadContract.MAX_PAGES)) {
            when (val r = readBatch(context.applicationContext, batch)) {
                is Result.Pages -> out += r.pages
                else -> return r
            }
        }
        return Result.Pages(out)
    }

    private suspend fun readBatch(context: Context, images: List<File>): Result {
        val pkg = readPackage(context)
        val shared = withContext(Dispatchers.IO) { images.map { ReadPagesProvider.share(context, it) } }
        shared.forEach { context.grantUriPermission(pkg, it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        try {
            return withTimeoutOrNull(TIMEOUT_MS) { ask(context, pkg, shared) } ?: Result.Failed
        } finally {
            shared.forEach { uri ->
                runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                ReadPagesProvider.forget(context, uri)
            }
        }
    }

    private suspend fun ask(context: Context, pkg: String, pages: List<Uri>): Result = suspendCancellableCoroutine { cont ->
        var connection: ServiceConnection? = null
        fun finish(result: Result) {
            connection?.let { runCatching { context.unbindService(it) } }
            connection = null
            if (cont.isActive) cont.resume(result)
        }
        val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == ReadContract.MSG_RESULT) finish(parse(msg.data))
            true
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val request = Message.obtain(null, ReadContract.MSG_READ).apply {
                    data = Bundle().apply { putParcelableArrayList(ReadContract.KEY_PAGES, ArrayList(pages)) }
                    replyTo = replies
                }
                runCatching { Messenger(service).send(request) }.onFailure { finish(Result.Failed) }
            }
            override fun onServiceDisconnected(name: ComponentName) = finish(Result.Failed)
            override fun onBindingDied(name: ComponentName) = finish(Result.Failed)
            override fun onNullBinding(name: ComponentName) = finish(Result.Failed)
        }
        connection = conn
        val intent = Intent(ReadContract.ACTION_READ).setPackage(pkg)
        val bound = runCatching { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) finish(if (installed(context)) Result.Failed else Result.NotInstalled)
        cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { connection?.let { runCatching { context.unbindService(it) } } } }
    }

    private fun parse(data: Bundle): Result {
        when (data.getString(ReadContract.KEY_ERROR)) {
            ReadContract.ERROR_NO_MODEL -> return Result.NoModel
            null -> Unit
            else -> return Result.Failed
        }
        val texts = data.getStringArrayList(ReadContract.KEY_TEXTS) ?: return Result.Failed
        val words = data.getStringArrayList(ReadContract.KEY_WORDS) ?: return Result.Failed
        val sizes = data.getIntArray(ReadContract.KEY_SIZES) ?: return Result.Failed
        if (words.size != texts.size || sizes.size != texts.size * 2) return Result.Failed
        return Result.Pages(texts.indices.map { i -> ReadPage(texts[i], OcrWords.fromTsv(words[i]), sizes[2 * i], sizes[2 * i + 1]) })
    }
}

/**
 * Espone a Eurise Leggi le pagine da leggere, una per volta, in sola lettura: non è aperto alle altre app e ogni file
 * si raggiunge solo col permesso temporaneo dato da [ReadClient]. I file stanno nella cache dell'app.
 */
class ReadPagesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("sola lettura")
        val file = fileOf(context ?: return null, uri) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = if (uri.lastPathSegment.orEmpty().endsWith(".png")) "image/png" else "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private fun dir(context: Context) = File(context.cacheDir, "read-pages").apply { mkdirs() }

        internal fun share(context: Context, image: File): Uri {
            val ext = if (image.name.endsWith(".png", ignoreCase = true)) "png" else "jpg"
            val copy = File(dir(context), "${UUID.randomUUID()}.$ext")
            image.copyTo(copy)
            return Uri.Builder().scheme("content").authority(context.packageName + ".readpages").appendPath(copy.name).build()
        }

        internal fun forget(context: Context, uri: Uri) {
            fileOf(context, uri)?.delete()
        }

        /** Solo nomi semplici dentro la cartella delle pagine: niente percorsi. */
        private fun fileOf(context: Context, uri: Uri): File? {
            val name = uri.lastPathSegment ?: return null
            if (uri.pathSegments.size != 1 || !Regex("[0-9a-f\\-]{36}\\.(jpg|png)").matches(name)) return null
            return File(dir(context), name).takeIf { it.isFile }
        }
    }
}
