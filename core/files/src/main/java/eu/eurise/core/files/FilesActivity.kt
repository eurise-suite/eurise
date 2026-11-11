package eu.eurise.core.files

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * «Salva in…» e «Apri da…» per gli altri APK della suite (Office, Galleria, Browser). L'attività è nel manifest
 * dell'app Eurise con il permesso della suite: solo le app firmate con la stessa chiave possono chiamarla.
 *
 * - [ACTION_SAVE_IN]: il file in `EXTRA_STREAM` (con il permesso di lettura), il nome in `EXTRA_TITLE`, il tipo nell'intent,
 *   [EXTRA_KIND] per le proposte e [EXTRA_FROM] per «da Office». Torna `RESULT_OK` con [EXTRA_WHERE] (la cartella).
 * - [ACTION_OPEN_FROM]: [EXTRA_EXTENSIONS] (es. «docx,odt,pdf»), [EXTRA_FROM]. Torna l'Uri del file in `data`, con il
 *   permesso di lettura, e il percorso d'origine in [EXTRA_ORIGIN] per salvarlo poi nello stesso posto.
 */
class FilesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val from = intent.getStringExtra(EXTRA_FROM) ?: getString(R.string.files_all)
        when (intent.action) {
            ACTION_SAVE_IN -> {
                val uri = streamOf(intent) ?: return finish()
                val mime = intent.type ?: contentResolver.getType(uri) ?: "application/octet-stream"
                val shown = intent.getStringExtra(Intent.EXTRA_TITLE) ?: displayName(uri) ?: "Documento"
                val ext = shown.substringAfterLast('.', "").ifEmpty { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
                val request = SaveRequest(
                    kind = intent.getStringExtra(EXTRA_KIND) ?: "app",
                    from = from,
                    name = shown.removeSuffix(".$ext"),
                    extension = ext,
                    mime = mime,
                    produce = { copyIn(uri, ext) },
                )
                setContent {
                    EuriseTheme {
                        SaveInSheet(request, onSaved = { saved ->
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_WHERE, saved.spot.key()).putExtra(Intent.EXTRA_TITLE, saved.name))
                            finish()
                        }, onDismiss = { setResult(Activity.RESULT_CANCELED); finish() })
                    }
                }
            }
            ACTION_OPEN_FROM -> {
                val extensions = intent.getStringExtra(EXTRA_EXTENSIONS).orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
                setContent {
                    EuriseTheme {
                        val system = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
                            if (picked != null) {
                                setResult(Activity.RESULT_OK, Intent().setData(picked).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri(null, picked) })
                            }
                            finish()
                        }
                        OpenFromScreen(from, extensions, onPicked = { file, entry ->
                            val uri = FileProvider.getUriForFile(this, "$packageName$AUTHORITY", file)
                            // Anche in scrittura: Office salva le modifiche nello stesso file (solo app della suite arrivano qui).
                            val back = Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            back.clipData = ClipData.newRawUri(entry?.name, uri)
                            entry?.let { back.putExtra(EXTRA_ORIGIN, it.spot.key()) }
                            setResult(Activity.RESULT_OK, back)
                            finish()
                        }, onCancel = { setResult(Activity.RESULT_CANCELED); finish() }, onSystem = { system.launch(arrayOf("*/*")) })
                    }
                }
            }
            else -> finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri ?: intent.clipData?.getItemAt(0)?.uri ?: intent.data

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private suspend fun copyIn(uri: Uri, ext: String): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "eurise-files/in").apply { mkdirs() }
        val target = File(dir, "da-salvare.$ext")
        (contentResolver.openInputStream(uri) ?: throw java.io.IOException("non leggibile")).use { input -> target.outputStream().use { input.copyTo(it) } }
        target
    }

    companion object {
        const val ACTION_SAVE_IN = "eu.eurise.action.SAVE_IN"
        const val ACTION_OPEN_FROM = "eu.eurise.action.OPEN_FROM"
        const val EXTRA_KIND = "eu.eurise.extra.KIND"
        const val EXTRA_FROM = "eu.eurise.extra.FROM"
        const val EXTRA_WHERE = "eu.eurise.extra.WHERE"
        const val EXTRA_EXTENSIONS = "eu.eurise.extra.EXTENSIONS"
        const val EXTRA_ORIGIN = "eu.eurise.extra.ORIGIN"
        /** Dopo il nome del pacchetto: `eu.eurise.hub.eurisefiles`. */
        const val AUTHORITY = ".eurisefiles"
    }
}

/** Il FileProvider di Eurise File (una classe sua: due provider con la stessa classe nel manifest non si possono unire). */
class EuriseFilesProvider : FileProvider()
