package eu.eurise.feature.drive

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.trash.TrashEntry
import eu.eurise.core.data.trash.TrashRepository
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una riga del cestino unico, qualunque sia la sua provenienza. */
private sealed interface BinRow {
    val key: String
    val title: String
    val at: Long

    data class Suite(val entry: TrashEntry) : BinRow {
        override val key get() = entry.key
        override val title get() = entry.title
        override val at get() = entry.deletedAt
    }

    data class FileRow(val item: TrashItem) : BinRow {
        override val key get() = "file:" + item.id
        override val title get() = item.name
        override val at get() = item.at
    }

    data class Photo(val uri: Uri, val name: String, override val at: Long, val size: Long) : BinRow {
        override val key get() = "photo:$uri"
        override val title get() = name
    }
}

private enum class BinFilter(val label: Int) { All(R.string.bin_all), Notes(R.string.bin_notes), Tasks(R.string.bin_tasks), Events(R.string.bin_events), Files(R.string.bin_files), Photos(R.string.bin_photos) }

/**
 * Il cestino unico: note, attività, eventi, file del telefono e foto (cestino di Android, dal 11) in un posto solo.
 * Tutto resta 30 giorni e torna con i suoi collegamenti. In fondo, il passaggio a un telefono nuovo.
 */
@Composable
internal fun AllTrash(
    data: DriveData,
    refresh: Int,
    onRestoreFile: (TrashItem) -> Unit,
    onDeleteFile: (TrashItem) -> Unit,
    onNewPhone: () -> Unit,
    bottomSpace: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val bin: TrashRepository = remember { EuriseData.create(context).trash }
    var localRefresh by remember { mutableIntStateOf(0) }
    val suite by remember { bin.observe() }.collectAsStateWithLifecycle(emptyList())
    val files by produceState(emptyList<TrashItem>(), refresh, localRefresh) { value = data.trash() }
    val photos by produceState(emptyList<BinRow.Photo>(), refresh, localRefresh) { value = trashedPhotos(context) }
    var filter by remember { mutableStateOf(BinFilter.All) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val photoRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { localRefresh++ }

    // Una volta all'apertura: ciò che ha passato i 30 giorni se ne va.
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { bin.purge() } }

    val rows: List<BinRow> = buildList {
        addAll(suite.map { BinRow.Suite(it) })
        addAll(files.map { BinRow.FileRow(it) })
        addAll(photos)
    }.filter { row ->
        when (filter) {
            BinFilter.All -> true
            BinFilter.Notes -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Note
            BinFilter.Tasks -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Task
            BinFilter.Events -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Event
            BinFilter.Files -> row is BinRow.FileRow
            BinFilter.Photos -> row is BinRow.Photo
        }
    }.sortedByDescending { it.at }

    fun photoRequest(row: BinRow.Photo, restore: Boolean) {
        if (Build.VERSION.SDK_INT < 30) return
        val sender = if (restore) MediaStore.createTrashRequest(context.contentResolver, listOf(row.uri), false)
        else MediaStore.createDeleteRequest(context.contentResolver, listOf(row.uri))
        photoRequest.launch(IntentSenderRequest.Builder(sender.intentSender).build())
    }

    fun restore(row: BinRow) = when (row) {
        is BinRow.Suite -> { scope.launch { runCatching { bin.restore(row.entry) } }; Unit }
        is BinRow.FileRow -> onRestoreFile(row.item)
        is BinRow.Photo -> photoRequest(row, restore = true)
    }

    fun delete(row: BinRow) = when (row) {
        is BinRow.Suite -> { scope.launch { runCatching { bin.deleteForever(row.entry) } }; Unit }
        is BinRow.FileRow -> onDeleteFile(row.item)
        is BinRow.Photo -> photoRequest(row, restore = false)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "t") {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.drive_trash), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (rows.any { it !is BinRow.Photo }) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.bin_empty), color = scheme.error) }
            }
        }
        item(key = "h") {
            Text(stringResource(R.string.bin_hint), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item(key = "f") {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BinFilter.entries.forEach { f ->
                    val on = f == filter
                    Text(
                        stringResource(f.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (on) scheme.onPrimary else scheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) scheme.primary else scheme.surfaceContainerLow)
                            .clickable { filter = f }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (rows.isEmpty()) item(key = "e") {
            EmptyState(EuriseIcons.trash(), stringResource(R.string.drive_empty_trash), stringResource(R.string.bin_empty_msg))
        }
        items(rows, key = { it.key }) { row ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(row.title.ifBlank { stringResource(R.string.bin_untitled) }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val days = (TrashRepository.DAYS - ((System.currentTimeMillis() - row.at) / 86_400_000L).toInt()).coerceAtLeast(0)
                    val size = when (row) {
                        is BinRow.FileRow -> Formatter.formatShortFileSize(context, row.item.size) + " · "
                        is BinRow.Photo -> Formatter.formatShortFileSize(context, row.size) + " · "
                        else -> ""
                    }
                    Text(size + stringResource(R.string.bin_days_left, days), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = { restore(row) }) { Text(stringResource(R.string.drive_restore)) }
                        TextButton(onClick = { delete(row) }) { Text(stringResource(R.string.drive_delete_forever), color = scheme.error) }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 30 && !canReadPhotos(context)) item(key = "p") {
            Text(stringResource(R.string.bin_photos_gallery), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item(key = "move") {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(scheme.surfaceContainerLow).clickable(onClick = onNewPhone).padding(16.dp),
            ) {
                Text(stringResource(R.string.bin_new_phone), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.bin_new_phone_msg), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.bin_empty_title)) },
            text = { Text(stringResource(R.string.bin_empty_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    val toDelete = rows.filterNot { it is BinRow.Photo }
                    scope.launch {
                        toDelete.forEach { row ->
                            when (row) {
                                is BinRow.Suite -> runCatching { bin.deleteForever(row.entry) }
                                is BinRow.FileRow -> runCatching { data.deleteForever(row.item) }
                                else -> Unit
                            }
                        }
                        localRefresh++
                    }
                }) { Text(stringResource(R.string.bin_empty), color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.bin_cancel)) } },
        )
    }
}

private fun BinRow.icon(): ImageVector = when (this) {
    is BinRow.Suite -> when (entry.kind) {
        TrashEntry.Kind.Note -> EuriseIcons.note()
        TrashEntry.Kind.Task -> EuriseIcons.task()
        TrashEntry.Kind.Event -> EuriseIcons.calendar()
    }
    is BinRow.FileRow -> EuriseIcons.folder()
    is BinRow.Photo -> EuriseIcons.image()
}

private fun canReadPhotos(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager())
}

/** Foto e video nel cestino di Android (dal 11), letti senza toccarli. */
private suspend fun trashedPhotos(context: Context): List<BinRow.Photo> = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 30 || !canReadPhotos(context)) return@withContext emptyList()
    val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val args = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        putString(
            android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
        )
    }
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_EXPIRES,
    )
    runCatching {
        context.contentResolver.query(uri, projection, args, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    // Android tiene le cose nel cestino 30 giorni: la data di scadenza dice quando sono state tolte.
                    val expires = c.getLong(3) * 1000L
                    val at = if (expires > 0) expires - TrashRepository.DAYS * 86_400_000L else System.currentTimeMillis()
                    add(BinRow.Photo(ContentUris.withAppendedId(uri, c.getLong(0)), c.getString(1).orEmpty(), at, c.getLong(2)))
                }
            }
        }
    }.getOrNull().orEmpty()
}
