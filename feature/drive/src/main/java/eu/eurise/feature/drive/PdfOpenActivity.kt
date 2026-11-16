package eu.eurise.feature.drive

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.OfficeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * «Apri con › PDF di Eurise» (15 novembre): un PDF da Email, Messaggi, browser o File si legge con il lettore della
 * suite (ricerca nel testo, note a mano). Si lavora su una copia nella cache dell'app; l'originale non si tocca.
 * Con Eurise Office installata, «Modifica in Office» le passa l'originale, con la scrittura se l'app di partenza l'aveva data.
 */
class PdfOpenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run { finish(); return }
        val writable = intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 || uri.scheme == "file"
        val edit = OfficeContract.editIntent(this, uri, "application/pdf", writable)
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
        setContent {
            EuriseTheme {
                val file by produceState<File?>(null, uri) { value = withContext(Dispatchers.IO) { copy(uri, name) } }
                val f = file
                if (f == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else PdfViewer(f, name.removeSuffix(".pdf"), onEdit = edit?.let { { startActivity(it); finish() } }) { finish() }
            }
        }
    }

    private fun copy(uri: Uri, name: String): File? = runCatching {
        val dir = File(cacheDir, "pdf-aperti").apply { deleteRecursively(); mkdirs() }
        val safe = name.replace('/', '_').ifBlank { "documento.pdf" }.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        File(dir, safe).also { out -> contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } } }
    }.getOrNull()
}
