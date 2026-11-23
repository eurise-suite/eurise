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
import eu.eurise.core.suite.AppHubContract
import eu.eurise.core.suite.SuiteAccess
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

    /**
     * L'originale di una foto sostituita dall'editor della Galleria, tenuto 30 giorni. Rimetterlo apre la foto nella
     * Galleria («Torna all'originale», Android chiede il permesso di riscriverla); eliminarlo toglie la copia messa da parte.
     */
    data class Original(val pkg: String, val item: AppHubContract.Item, val photo: Uri) : BinRow {
        override val key get() = "orig:" + item.id
        override val title get() = item.title
        override val at get() = item.at
    }

    /** Una voce nel cestino della cassaforte di Password (a cassaforte aperta). */
    data class Password(val pkg: String, val item: AppHubContract.Item) : BinRow {
        override val key get() = "pwd:" + item.id
        override val title get() = item.title
        override val at get() = item.at
    }

    /** Password chiusa: si sa solo quante voci ci sono; «Sblocca» apre Password e torna qui. */
    data class PasswordLocked(val pkg: String, val count: Int) : BinRow {
        override val key get() = "pwd-locked"
        override val title get() = ""
        override val at get() = Long.MAX_VALUE
    }
}

private enum class BinFilter(val label: Int) { All(R.string.bin_all), Notes(R.string.bin_notes), Tasks(R.string.bin_tasks), Events(R.string.bin_events), Files(R.string.bin_files), Photos(R.string.bin_photos), Passwords(R.string.bin_passwords) }

/** Cosa danno Password e Galleria al cestino unico ([AppHubContract]). */
private data class AppBins(val passwordPkg: String, val password: AppHubContract.Trash?, val gallery: List<BinRow.Photo>, val originals: List<BinRow.Original>)

private fun appBins(context: Context): AppBins {
    val debug = SuiteAccess.isDebug(context)
    val pwd = AppHubContract.passwordPackage(debug)
    val galleryPkg = AppHubContract.galleryPackage(debug)
    val (kept, trashed) = AppHubContract.trash(context, galleryPkg, withOriginals = true)?.items.orEmpty().partition { it.id.startsWith(AppHubContract.ORIGINAL_PREFIX) }
    // Le foto dalla Galleria servono solo se l'app Eurise non può leggerle da sé; gli originali messi da parte sempre.
    val gallery = if (canReadPhotos(context)) emptyList() else trashed
        .map { BinRow.Photo(Uri.parse(it.id), it.title, it.at.takeIf { at -> at > 0 } ?: System.currentTimeMillis(), it.size) }
    val originals = kept.mapNotNull { k -> AppHubContract.originalPhoto(k.id)?.let { BinRow.Original(galleryPkg, k, it) } }
    return AppBins(pwd, AppHubContract.trash(context, pwd), gallery, originals)
}

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
    // Tornando da Password (dopo «Sblocca») o dalla Galleria si rilegge.
    var resumed by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val apps by produceState<AppBins?>(null, refresh, localRefresh, resumed) { value = withContext(Dispatchers.IO) { appBins(context) } }
    var filter by remember { mutableStateOf(BinFilter.All) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val photoRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { localRefresh++ }

    // Una volta all'apertura: ciò che ha passato i 30 giorni se ne va.
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { bin.purge() } }

    val rows: List<BinRow> = buildList {
        addAll(suite.map { BinRow.Suite(it) })
        addAll(files.map { BinRow.FileRow(it) })
        addAll(photos)
        apps?.let { a ->
            addAll(a.gallery.filter { g -> photos.none { it.uri == g.uri } })
            addAll(a.originals)
            val pwd = a.password
            if (pwd != null && pwd.locked && pwd.count > 0) add(BinRow.PasswordLocked(a.passwordPkg, pwd.count))
            if (pwd != null && !pwd.locked) addAll(pwd.items.map { BinRow.Password(a.passwordPkg, it) })
        }
    }.filter { row ->
        when (filter) {
            BinFilter.All -> true
            BinFilter.Notes -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Note
            BinFilter.Tasks -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Task
            BinFilter.Events -> row is BinRow.Suite && row.entry.kind == TrashEntry.Kind.Event
            BinFilter.Files -> row is BinRow.FileRow
            BinFilter.Photos -> row is BinRow.Photo || row is BinRow.Original
            BinFilter.Passwords -> row is BinRow.Password || row is BinRow.PasswordLocked
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
        is BinRow.Original -> openOriginal(context, row)
        is BinRow.Password -> { scope.launch { withContext(Dispatchers.IO) { AppHubContract.restore(context, row.pkg, row.item.id) }; localRefresh++ }; Unit }
        is BinRow.PasswordLocked -> unlockPassword(context, row.pkg)
    }

    fun delete(row: BinRow) = when (row) {
        is BinRow.Suite -> { scope.launch { runCatching { bin.deleteForever(row.entry) } }; Unit }
        is BinRow.FileRow -> onDeleteFile(row.item)
        is BinRow.Photo -> photoRequest(row, restore = false)
        is BinRow.Original -> { scope.launch { withContext(Dispatchers.IO) { AppHubContract.delete(context, row.pkg, row.item.id) }; localRefresh++ }; Unit }
        is BinRow.Password -> { scope.launch { withContext(Dispatchers.IO) { AppHubContract.delete(context, row.pkg, row.item.id) }; localRefresh++ }; Unit }
        is BinRow.PasswordLocked -> Unit
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomSpace),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "t") {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.drive_trash), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (rows.any { it is BinRow.Suite || it is BinRow.FileRow }) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.bin_empty), color = scheme.error) }
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
            if (row is BinRow.PasswordLocked) {
                PasswordLockedRow(row.count) { unlockPassword(context, row.pkg) }
                return@items
            }
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    val title = row.title.ifBlank { stringResource(R.string.bin_untitled) }
                    Text(if (row is BinRow.Original) stringResource(R.string.bin_original, title) else title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val days = (TrashRepository.DAYS - ((System.currentTimeMillis() - row.at) / 86_400_000L).toInt()).coerceAtLeast(0)
                    val size = when (row) {
                        is BinRow.FileRow -> Formatter.formatShortFileSize(context, row.item.size) + " · "
                        is BinRow.Photo -> Formatter.formatShortFileSize(context, row.size) + " · "
                        is BinRow.Original -> Formatter.formatShortFileSize(context, row.item.size) + " · "
                        else -> ""
                    }
                    // Il cestino della cassaforte non si svuota da sé: lì nulla scade dopo 30 giorni.
                    val left = if (row is BinRow.Password) stringResource(R.string.bin_pwd_kept) else stringResource(R.string.bin_days_left, days)
                    Text(size + left, fontSize = 12.sp, color = scheme.onSurfaceVariant)
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
                    // Foto e voci di Password restano: le prime le toglie Android una per una, le seconde sono password.
                    val toDelete = rows.filter { it is BinRow.Suite || it is BinRow.FileRow }
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
    is BinRow.Original -> EuriseIcons.history()
    is BinRow.Password, is BinRow.PasswordLocked -> EuriseIcons.key()
}

/** Apre la foto nella Galleria, dove «Torna all'originale» chiede ad Android il permesso di riscriverla. */
private fun openOriginal(context: Context, row: BinRow.Original) {
    val open = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(row.photo, "image/*").setPackage(row.pkg)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(open) }
}

/** Apre Password per lo sblocco: appena la cassaforte è aperta, Password si chiude e il cestino si rilegge. */
private fun unlockPassword(context: Context, pkg: String) {
    AppHubContract.openIntent(context, pkg, AppHubContract.PAGE_TRASH)?.let { runCatching { context.startActivity(it) } }
}

@Composable
private fun PasswordLockedRow(count: Int, onUnlock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EuriseIcons.key(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.bin_pwd_locked, count, count), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.bin_pwd_locked_d), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            TextButton(onClick = onUnlock) { Text(stringResource(R.string.bin_unlock)) }
        }
    }
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
