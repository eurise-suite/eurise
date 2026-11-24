package eu.eurise.password.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.kdbx.Attachment
import eu.eurise.password.kdbx.VaultEntry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Immagini nelle note sicure (24 novembre 2026). Stanno nel file della cassaforte come allegati di KeePass, cifrate con
 * tutto il resto (KeePassXC le apre). Entrando si rimpiccioliscono (lato lungo 2048 px) e si riscrivono in JPEG: niente
 * posizione né altri dati dello scatto. Si leggono solo in memoria: nessun file in chiaro, nessuna miniatura su disco; si
 * vedono dentro la finestra di Password, coperta (FLAG_SECURE), non in una finestra a parte.
 */

private const val MAX_SIDE = 2048
private const val MAX_IMAGES = 20

/** Un'immagine scelta, pronta per la cassaforte: nome e JPEG già ridotto. Null se non si legge. */
internal fun prepareImage(context: Context, uri: Uri): Pair<String, ByteArray>? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    bitmap.recycle()
    val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    val base = shown?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: context.getString(R.string.pwd_image_default)
    "$base.jpg" to out.toByteArray()
}.getOrNull()

/** Una miniatura decodificata ridotta, solo in memoria. */
private fun thumbnail(bytes: ByteArray, side: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun Thumb(bytes: ByteArray?, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val bitmap by produceState<Bitmap?>(null, bytes) { value = bytes?.let { withContext(Dispatchers.Default) { thumbnail(it, 320) } } }
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHigh)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (bytes == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(EuriseIcons.image(), contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun rememberAttachment(vm: PasswordViewModel, uuid: String, name: String): ByteArray? =
    produceState<ByteArray?>(null, uuid, name) { value = vm.attachment(uuid, name) }.value

/** Le immagini della voce, nella sua scheda: miniature in fila; il tocco le apre grandi. */
@Composable
internal fun EntryImages(vm: PasswordViewModel, entry: VaultEntry, onOpen: (Attachment) -> Unit) {
    val images = entry.attachments.filter { it.isImage }
    if (images.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.pwd_images), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            images.forEach { a -> Thumb(rememberAttachment(vm, entry.uuid, a.name), Modifier.size(96.dp)) { onOpen(a) } }
        }
    }
}

/** Un'immagine grande, dentro la finestra di Password: due dita per ingrandire, indietro per chiudere. */
@Composable
internal fun ImageViewer(vm: PasswordViewModel, uuid: String, attachment: Attachment, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val bytes = rememberAttachment(vm, uuid, attachment.name)
    val bitmap by produceState<Bitmap?>(null, bytes) { value = bytes?.let { withContext(Dispatchers.Default) { thumbnail(it, 2048) } } }
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val gestures = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        if (scale == 1f) { dx = 0f; dy = 0f } else { dx += pan.x; dy += pan.y }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).transformable(gestures), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) CircularProgressIndicator(color = Color.White)
        else Image(
            b.asImageBitmap(), contentDescription = attachment.name, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = dx; translationY = dy },
        )
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EuriseIcons.close(2f), contentDescription = stringResource(R.string.pwd_back), tint = Color.White) }
            Text(attachment.name, color = Color.White, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/**
 * Le immagini nella modifica della voce, sotto le note sicure: quelle che ci sono (con la crocetta per toglierle) e
 * quelle appena scelte. Si applicano tutte insieme con «Salva».
 */
@Composable
internal fun EditImages(
    vm: PasswordViewModel?, entry: VaultEntry?,
    added: List<Pair<String, ByteArray>>, removed: Set<String>,
    onAdd: (List<Pair<String, ByteArray>>) -> Unit, onRemoveExisting: (String) -> Unit, onRemoveAdded: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    val existing = entry?.attachments.orEmpty().filter { it.isImage && it.name !in removed }
    val room = MAX_IMAGES - existing.size - added.size
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ready = withContext(Dispatchers.Default) { uris.take(room.coerceAtLeast(0)).mapNotNull { prepareImage(context, it) } }
            busy = false
            if (ready.isNotEmpty()) onAdd(ready)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (existing.isNotEmpty() || added.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm != null && entry != null) existing.forEach { a ->
                    Removable(onRemove = { onRemoveExisting(a.name) }) { Thumb(rememberAttachment(vm, entry.uuid, a.name), Modifier.size(84.dp)) }
                }
                added.forEachIndexed { i, (_, bytes) ->
                    Removable(onRemove = { onRemoveAdded(i) }) { Thumb(bytes, Modifier.size(84.dp)) }
                }
            }
        }
        OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && room > 0) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(EuriseIcons.image(), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.pwd_image_add), modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.pwd_image_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun Removable(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box {
        content()
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.close(2f), contentDescription = stringResource(R.string.pwd_image_remove), tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}
