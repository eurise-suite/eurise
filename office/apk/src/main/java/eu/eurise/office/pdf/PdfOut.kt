package eu.eurise.office.pdf

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import eu.eurise.core.suite.FilesContract
import eu.eurise.office.R
import eu.eurise.office.data.Suite
import java.io.File
import java.io.FileOutputStream

/** Dove va una copia del PDF: Eurise File, un'altra app, la stampante. L'originale non si tocca. */
object PdfOut {
    /** Una copia del PDF di lavoro, con il nome del documento, in una cartella condivisibile di Office. */
    fun copy(context: Context, from: File, name: String): Uri {
        val dir = File(context.cacheDir, "copie/uscita").apply { mkdirs() }
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "-").ifBlank { "documento.pdf" }
        val target = File(dir, if (safe.lowercase().endsWith(".pdf")) safe else "$safe.pdf")
        from.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, context.packageName + ".files", target)
    }

    fun saveIn(context: Context, model: PdfModel) = saveIn(context, model.session.workingFile, model.session.current.value.name)

    fun saveIn(context: Context, file: File, name: String) {
        val uri = copy(context, file, name)
        val intent = FilesContract.saveIn(Suite.hub, uri, "application/pdf", name, "office", context.getString(R.string.office_app_name))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            share(context, uri, name)
        } catch (e: SecurityException) {
            share(context, uri, name)
        }
    }

    fun share(context: Context, uri: Uri, name: String) {
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        context.startActivity(Intent.createChooser(send, null))
    }

    fun openWith(context: Context, model: PdfModel) {
        val name = model.session.current.value.name
        val uri = copy(context, model.session.workingFile, name)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(view, null))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.pdf_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /** Stampa di Android: il PDF passa così com'è (con le annotazioni). */
    fun print(context: Context, model: PdfModel) {
        val file = model.session.workingFile
        val name = model.session.current.value.name
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }

            override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
                runCatching {
                    file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message) }
            }
        }, null)
    }
}
