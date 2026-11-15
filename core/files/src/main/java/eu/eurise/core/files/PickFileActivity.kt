package eu.eurise.core.files

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import eu.eurise.core.designsystem.theme.EuriseTheme

/**
 * Eurise File tra le scelte quando un'app chiede un file («Apri da», «Allega», «Scegli un file»: GET_CONTENT, 15
 * novembre). Mostra telefono e cloud collegati come «Apri da…» della suite; all'app che ha chiesto torna solo il
 * permesso di leggere il file scelto (mai di scriverlo, a differenza delle app della suite).
 */
class PickFileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val types = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() ?: listOfNotNull(intent.type)
        setContent {
            EuriseTheme {
                OpenFromScreen(
                    forApp = callingName() ?: getString(R.string.files_all),
                    extensions = extensionsFor(types),
                    onPicked = { file, entry ->
                        val uri = FileProvider.getUriForFile(this, "$packageName${FilesActivity.AUTHORITY}", file)
                        val back = Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        back.clipData = ClipData.newRawUri(entry?.name, uri)
                        setResult(Activity.RESULT_OK, back)
                        finish()
                    },
                    onCancel = { setResult(Activity.RESULT_CANCELED); finish() },
                )
            }
        }
    }

    private fun callingName(): String? = runCatching {
        val pkg = callingActivity?.packageName ?: return null
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    companion object {
        private val GROUPS = mapOf(
            "image/" to setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif"),
            "video/" to setOf("mp4", "mkv", "webm", "mov", "3gp", "avi"),
            "audio/" to setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav"),
            "text/" to setOf("txt", "md", "csv", "html", "htm", "ics", "vcf", "xml", "json"),
        )

        /** Le estensioni da mostrare per i tipi chiesti; vuoto vuol dire tutti i file. */
        fun extensionsFor(types: List<String>): Set<String> {
            if (types.isEmpty() || types.any { it == "*/*" || it.endsWith("/*") && GROUPS[it.removeSuffix("*")] == null }) return emptySet()
            return types.flatMap { t ->
                GROUPS[t.removeSuffix("*")].takeIf { t.endsWith("/*") }
                    ?: listOfNotNull(MimeTypeMap.getSingleton().getExtensionFromMimeType(t))
            }.toSet()
        }
    }
}
