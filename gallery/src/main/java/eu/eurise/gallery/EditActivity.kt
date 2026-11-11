package eu.eurise.gallery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.ui.EditorScreen

/**
 * L'editor della Galleria per le foto che arrivano da altre app: «Modifica» (ACTION_EDIT) da un visualizzatore,
 * da File o da una chat, oppure «Condividi › Modifica» (ACTION_SEND). Si apre solo l'editor, senza caricare la
 * Galleria; la foto originale non si tocca e la copia va in Pictures/Eurise. A chi l'ha chiesto torna la copia.
 */
class EditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = imageFrom(intent)
        if (uri == null) {
            Toast.makeText(this, R.string.action_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val item = externalItem(this, uri)
        setContent {
            EuriseTheme {
                EditorScreen(
                    item = item,
                    onClose = { finish() },
                    onSaved = { saved ->
                        setResult(Activity.RESULT_OK, Intent().setDataAndType(saved, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    },
                )
            }
        }
    }

    private fun imageFrom(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        else -> intent.data
    }
}

/** Una foto di un'altra app vista come voce della Galleria: nome e data presi dove si può, altrimenti ora. */
private fun externalItem(context: Context, uri: Uri): MediaItem {
    var name = ""
    var size = 0L
    var takenAt = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it).orEmpty() }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it >= 0 && !c.isNull(it) }?.let { takenAt = c.getLong(it) }
            }
        }
    }
    if (name.isEmpty()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    return MediaItem(
        id = 0,
        uri = uri.toString(),
        isVideo = false,
        takenAt = if (takenAt > 0) takenAt else System.currentTimeMillis(),
        durationMs = 0,
        bucket = "",
        favorite = false,
        name = name,
        size = size,
    )
}
