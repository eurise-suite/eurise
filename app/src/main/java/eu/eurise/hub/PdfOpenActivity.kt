package eu.eurise.hub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.OfficeContract
import eu.eurise.feature.drive.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Lettore PDF dell'APK principale per «Apri con» da qualsiasi app. Legge una copia nella cache; «Modifica in Office»
 * passa a Office il file originale, con il permesso di scrittura se l'app di partenza l'aveva concesso.
 */
class PdfOpenActivity : ComponentActivity() {
    private var file by mutableStateOf<File?>(null)
    private var failed by mutableStateOf(false)
    private var title = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) { finish(); return }
        val writable = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || uri.scheme == "file"
        lifecycleScope.launch {
            val copy = withContext(Dispatchers.IO) { runCatching { copyOf(uri) }.getOrNull() }
            if (copy == null) failed = true else { title = copy.name; file = copy }
        }
        setContent {
            EuriseTheme {
                val current = file
                when {
                    current != null -> PdfReader(
                        current, title,
                        onEdit = OfficeContract.editIntent(this, uri, "application/pdf", writable)?.let { edit ->
                            { startActivity(edit); finish() }
                        },
                        onClose = { finish() },
                    )
                    failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.pdf_open_error)) }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // La copia serve solo finché il lettore è aperto.
        if (isFinishing) file?.parentFile?.let { dir -> Thread { dir.deleteRecursively() }.start() }
    }

    private fun copyOf(uri: Uri): File {
        val name = displayName(uri)?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "documento.pdf"
        val dir = File(cacheDir, "pdf-aperti/" + UUID.randomUUID()).apply { mkdirs() }
        val out = File(dir, if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf")
        contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun displayName(uri: Uri): String? =
        if (uri.scheme == "file") uri.lastPathSegment
        else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
