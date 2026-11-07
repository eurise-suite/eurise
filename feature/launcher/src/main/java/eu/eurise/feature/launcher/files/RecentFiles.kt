package eu.eurise.feature.launcher.files

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un file modificato di recente, da riaprire con l'app adatta. */
data class RecentFile(val name: String, val folder: String, val mime: String, val modified: Long, val uri: Uri)

/**
 * Ultimi lavori: documenti, PDF, fogli, presentazioni e registrazioni modificati di recente nelle cartelle
 * Documenti, Download, Registrazioni e in quelle sincronizzate (per esempio il Drive di Murena).
 * Per leggere i documenti delle altre app Android chiede l'accesso a tutti i file.
 */
class RecentFilesRepository(private val context: Context) {

    fun hasAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        else -> context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** Su Android 11+ apre la pagina "Accesso a tutti i file"; prima basta il permesso di lettura (chiesto dalla Scrivania). */
    fun accessIntent(): Intent? = if (Build.VERSION.SDK_INT >= 30) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    } else null

    suspend fun recent(limit: Int = 10): List<RecentFile> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )
        val mimeClauses = WorkMimes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?" }
        val selection = "($mimeClauses) AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        val out = mutableListOf<RecentFile>()
        runCatching {
            context.contentResolver.query(
                collection, projection, selection, WorkMimes.toTypedArray(),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(4).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    // Le registrazioni audio contano solo se stanno in una cartella di registrazioni.
                    if (mime.startsWith("audio/") && !path.contains("Record", ignoreCase = true) && !path.contains("Registr", ignoreCase = true)) continue
                    if (path.contains("/.") || path.startsWith("Android/")) continue
                    out += RecentFile(
                        name = c.getString(1).orEmpty(),
                        folder = path.trimEnd('/').substringAfterLast('/').ifBlank { path },
                        mime = mime,
                        modified = c.getLong(3) * 1000,
                        uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                    )
                }
            }
        }
        out
    }

    fun open(file: RecentFile) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri, file.mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        val WorkMimes = listOf(
            "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument%",
            "application/vnd.ms-excel%", "application/vnd.ms-powerpoint%", "application/vnd.oasis.opendocument%",
            "text/plain", "text/markdown", "text/csv", "application/rtf", "audio/%",
        )
    }
}
