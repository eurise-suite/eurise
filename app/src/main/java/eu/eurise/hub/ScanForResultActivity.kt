package eu.eurise.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.scanner.ScannerRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Scanner per un'altra app della suite ([eu.eurise.core.suite.ScannerContract]): le pagine tornano come PDF in sola
 * lettura, esposte dal FileProvider di Eurise File (cartella cache/drive/scansioni, ripulita a ogni nuova scansione).
 */
class ScanForResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EuriseTheme {
                ScannerRoute(onClose = { setResult(Activity.RESULT_CANCELED); finish() }, onReturn = ::deliver)
            }
        }
    }

    private fun deliver(pdf: File) {
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                val dir = File(cacheDir, "drive/scansioni").apply { deleteRecursively(); mkdirs() }
                File(dir, UUID.randomUUID().toString() + ".pdf").also { pdf.copyTo(it, overwrite = true) }
            }
            val uri = FileProvider.getUriForFile(this@ScanForResultActivity, "$packageName.drivefiles", out)
            setResult(Activity.RESULT_OK, Intent().setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            finish()
        }
    }
}
