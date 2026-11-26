package eu.eurise.camera.ui

import android.content.Intent
import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.camera.CameraModel
import eu.eurise.camera.R
import eu.eurise.camera.Shot
import eu.eurise.camera.data.DestinationState
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Le foto di questa sessione (tavola FotBlocco: dal blocco schermo si vedono solo queste). Condividi esce sempre con una
 * copia pulita; dal blocco schermo Condividi e Galleria chiedono prima lo sblocco.
 */
@Composable
internal fun ReviewScreen(model: CameraModel, locked: Boolean, onClose: () -> Unit, onUnlockThen: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(model.session.firstOrNull()?.uri) }
    BackHandler(onBack = onClose)
    val shot = model.session.firstOrNull { it.uri == selected } ?: model.session.firstOrNull()
    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(EuriseIcons.close(), stringResource(R.string.review_close), onClick = onClose)
            Text(
                stringResource(R.string.review_title, model.session.size),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            if (locked) StatusChip(stringResource(R.string.locked_status), icon = EuriseIcons.lock())
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (shot == null) Text(stringResource(R.string.review_empty), color = Soft)
            else {
                val big = rememberPreview(shot)
                big?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                if (shot.isVideo) Icon(EuriseIcons.video(), null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(model.session, key = { it.uri.toString() }) { s ->
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
                        .border(2.dp, if (s.uri == shot?.uri) Accent else Color.Transparent, RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2D33)).clickable { selected = s.uri },
                ) { s.thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
            }
        }
        if (shot != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.review_delete), primary = false, modifier = Modifier.weight(1f)) { model.delete(shot) }
                PillButton(stringResource(R.string.review_share), primary = !locked, modifier = Modifier.weight(1f)) {
                    onUnlockThen {
                        scope.launch {
                            val chooser = model.cleanShare(listOf(shot))
                            if (chooser == null) toast(context, R.string.error_save)
                            else { toast(context, R.string.review_clean); runCatching { context.startActivity(chooser) } }
                        }
                    }
                }
            }
            PillButton(
                stringResource(if (locked) R.string.locked_unlock else R.string.review_gallery), primary = locked,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                onUnlockThen {
                    val gallery = model.galleryIntent(shot)
                    val intent = gallery ?: Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).setDataAndType(shot.uri, if (shot.isVideo) "video/*" else "image/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null,
                    )
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }
}

@Composable
private fun rememberPreview(shot: Shot): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(shot.thumb, shot.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (shot.isVideo) context.contentResolver.loadThumbnail(shot.uri, android.util.Size(1080, 1080), null).asImageBitmap()
                else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, shot.uri)) { d, info, _ ->
                    val side = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (side > 1600) {
                        val k = 1600f / side
                        d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    }
                }.asImageBitmap()
            }.getOrNull() ?: shot.thumb
        }
    }
    return state.value
}

/** «Scatta foto» per un'altra app: la foto appena fatta, Rifai o Usa questa. */
@Composable
internal fun CaptureConfirm(file: File, onRetake: () -> Unit, onUse: () -> Unit) {
    BackHandler(onBack = onRetake)
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, info, _ ->
                    val side = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (side > 1600) {
                        val k = 1600f / side
                        d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    }
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize().background(Ground).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.capture_retake), primary = false, modifier = Modifier.weight(1f), onClick = onRetake)
            PillButton(stringResource(R.string.capture_use), primary = true, modifier = Modifier.weight(1f), onClick = onUse)
        }
    }
}

/** Dove va la foto (tavola FotDestinazione): Rullino, le cartelle per cliente o progetto, una nuova. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DestinationSheet(model: CameraModel, onDismiss: () -> Unit) {
    val state = model.destinations.value
    var naming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.dest_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                DestRow(stringResource(R.string.dest_roll), stringResource(R.string.dest_roll_sub), null, state, model, onDismiss)
                state.folders.forEach { folder ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    DestRow(folder, stringResource(R.string.dest_folder_sub, folder), folder, state, model, onDismiss)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                Row(
                    Modifier.fillMaxWidth().clickable { naming = true }.padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(EuriseIcons.plus(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.dest_new), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(R.string.dest_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.dest_new)) },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text(stringResource(R.string.dest_new_hint)) }) },
            confirmButton = {
                TextButton(onClick = { model.update { it.add(name) }; naming = false; onDismiss() }) { Text(stringResource(R.string.dest_create)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.dest_cancel)) } },
        )
    }
}

@Composable
private fun DestRow(title: String, sub: String, dest: String?, state: DestinationState, model: CameraModel, onDismiss: () -> Unit) {
    val current = state.current == dest
    Row(
        Modifier.fillMaxWidth().clickable { model.update { it.select(dest) }; onDismiss() }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(EuriseIcons.folder(), null, tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold)
            Text(sub, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val fav = state.favorite == dest
        IconButton(onClick = { model.update { it.star(if (fav) null else dest) } }) {
            Icon(EuriseIcons.star(), stringResource(R.string.dest_star), tint = if (fav) Color(0xFFD9A93F) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dest != null) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var making by remember { mutableStateOf(false) }
            IconButton(enabled = !making, onClick = {
                making = true
                toast(context, R.string.report_making)
                scope.launch {
                    val share = model.report(dest)
                    making = false
                    if (share == null) toast(context, R.string.report_empty)
                    else { toast(context, R.string.report_saved); runCatching { context.startActivity(share) } }
                }
            }) {
                Icon(EuriseIcons.pdf(), stringResource(R.string.report_make, dest), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { model.update { it.remove(dest) } }) {
                Icon(EuriseIcons.trash(), stringResource(R.string.dest_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
