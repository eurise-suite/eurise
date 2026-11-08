package eu.eurise.gallery.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import eu.eurise.gallery.logic.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val THUMB_PX = 256
private const val FULL_PX = 2048

/** Miniature già pronte: lo scorrimento non torna sul disco. Tetto di 32 MB. */
private val thumbs = object : LruCache<String, android.graphics.Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.allocationByteCount
}

private suspend fun loadThumb(context: Context, item: MediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
    val cached = thumbs.get(item.uri)
    val bitmap = cached ?: runCatching {
        context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(THUMB_PX, THUMB_PX), null)
    }.getOrNull()?.also { thumbs.put(item.uri, it) }
    bitmap?.asImageBitmap()
}

private suspend fun loadFull(context: Context, item: MediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (item.isVideo) {
            context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(FULL_PX, FULL_PX), null)
        } else {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(item.uri))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.setTargetSampleSize(max(1, max(info.size.width, info.size.height) / FULL_PX))
            }
        }.asImageBitmap()
    }.getOrNull()
}

/** Miniatura quadrata, con un fondo neutro finché non è pronta. */
@Composable
internal fun Thumb(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, item.uri) { value = loadThumb(context, item) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        image?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}

/** Immagine grande: si carica ridotta a quanto serve allo schermo. */
@Composable
internal fun rememberFull(item: MediaItem): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, item.uri) { value = loadFull(context, item) }
    return image
}
