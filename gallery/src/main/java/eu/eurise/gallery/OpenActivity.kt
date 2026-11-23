package eu.eurise.gallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.ui.RoundAction
import eu.eurise.gallery.ui.VideoScreen
import eu.eurise.gallery.ui.ZoomableBitmap
import eu.eurise.gallery.ui.loadFull

/**
 * «Apri con › Galleria» (15 novembre: le app della suite devono comparire negli «Apri con»): una foto o un video che
 * arriva da un'altra app (File, Email, Messaggi, browser) si guarda qui, con zoom, Condividi e Modifica. Non tocca la
 * libreria: niente cestino né preferiti, perché il file è di chi l'ha aperto.
 */
class OpenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run { finish(); return }
        val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
        val video = mime.startsWith("video/")
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val item = MediaItem(id = 0, uri = uri.toString(), isVideo = video, takenAt = System.currentTimeMillis(), durationMs = 0, bucket = "", favorite = false, name = name)
        setContent {
            EuriseTheme {
                if (video) {
                    VideoScreen(item = null, title = name, load = { uri }, actions = null, onClose = { finish() })
                } else {
                    val image by produceState<ImageBitmap?>(null, uri) { value = loadFull(this@OpenActivity, item) }
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ZoomableBitmap(image, isVideo = false, key = uri.toString(), onPlay = {})
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            RoundAction(EuriseIcons.close(), stringResource(R.string.close)) { finish() }
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Una foto sostituita dall'editor (anche aperta dal cestino unico): l'originale torna per 30 giorni.
                            eu.eurise.gallery.ui.RestoreOriginal(item)
                            RoundAction(EuriseIcons.share(), stringResource(R.string.share)) { share(uri, mime) }
                            RoundAction(EuriseIcons.pencil(), stringResource(R.string.edit)) {
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(uri, mime.ifEmpty { "image/*" })
                                        .setClass(this@OpenActivity, EditActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun share(uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime.ifEmpty { "image/*" }).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(send, null)) }
    }
}
