package eu.eurise.office.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** I tipi di documento che Office conosce: decidono icona, colore e chi apre il file (editor PDF o motore). */
enum class DocKind(val label: String, val extensions: Set<String>) {
    Text("DOC", setOf("odt", "docx", "doc", "rtf", "txt", "fodt")),
    Sheet("XLS", setOf("ods", "xlsx", "xls", "csv", "fods")),
    Slides("PPT", setOf("odp", "pptx", "ppt", "fodp")),
    Drawing("ODG", setOf("odg")),
    Pdf("PDF", setOf("pdf")),
    Other("FILE", emptySet());

    companion object {
        fun of(name: String?, mime: String? = null): DocKind {
            if (mime == "application/pdf") return Pdf
            val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
            entries.firstOrNull { ext in it.extensions }?.let { return it }
            return when {
                mime == null -> Other
                mime.contains("wordprocessing") || mime.contains("opendocument.text") || mime == "application/msword" -> Text
                mime.contains("spreadsheet") || mime.contains("ms-excel") || mime.contains("csv") -> Sheet
                mime.contains("presentation") || mime.contains("powerpoint") -> Slides
                else -> Other
            }
        }

        /** Estensioni accettate da «Apri da…» (Eurise File). */
        val openable: List<String> = entries.flatMap { it.extensions }
    }
}

/** Nome e dimensione di un documento, letti dal suo fornitore (Eurise File, Download, un'altra app). */
data class DocInfo(val name: String, val size: Long?)

object OfficeDocs {
    fun info(context: Context, uri: Uri): DocInfo {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val f = java.io.File(uri.path.orEmpty())
            return DocInfo(f.name, f.length())
        }
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c: Cursor ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
        }
        return DocInfo(name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento", size)
    }

    /** Dove sta il file, in parole: «Eurise File», «Download», «Telefono», «da un'altra app». */
    fun where(context: Context, uri: Uri): String {
        val authority = uri.authority.orEmpty()
        return when {
            uri.scheme == ContentResolver.SCHEME_FILE -> uri.path.orEmpty().substringBeforeLast('/').substringAfterLast('/').ifEmpty { "Telefono" }
            authority.contains("eurisefiles") || authority.contains("drivefiles") -> "Eurise File"
            authority == "com.android.providers.downloads.documents" || authority == "downloads" -> "Download"
            authority == "com.android.externalstorage.documents" ->
                Uri.decode(uri.lastPathSegment.orEmpty()).substringAfter(':').substringBeforeLast('/', "").replace("/", " › ").ifEmpty { "Telefono" }
            authority == "media" -> "Telefono"
            authority == context.packageName + ".files" -> "Office"
            else -> context.getString(eu.eurise.office.R.string.office_where_other_app)
        }
    }

    /** Si può scrivere sull'Uri? (permesso concesso a Office, persistente o di questa apertura). */
    fun canWrite(context: Context, uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_FILE && java.io.File(uri.path.orEmpty()).canWrite() ||
            context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Prova a tenere il permesso dopo il riavvio (Uri del selettore di sistema); quelli di Eurise File non sono persistenti. */
    fun keep(context: Context, uri: Uri, flags: Int) {
        val modes = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (modes == 0) return
        runCatching { context.contentResolver.takePersistableUriPermission(uri, modes) }
    }
}
