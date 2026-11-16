package eu.eurise.feature.drive

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import eu.eurise.core.data.docs.DocReading
import eu.eurise.core.data.docs.ReadClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.DavShare
import eu.eurise.core.cloud.DavVersion
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.cloud.crypto.Cryptomator
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// Schede, righe e finestre di Drive (File, Offline, Condivisi), staccate da DriveRoute.kt.

private enum class Sort { Name, Size }

@Composable
internal fun FilesTab(
    data: DriveData, access: Boolean, scope: Place?, folder: Folder?, refresh: Int, haze: HazeState,
    onScope: (Place?) -> Unit, onFolder: (Folder?) -> Unit, onOpen: (DriveItem) -> Unit, onMore: (DriveItem) -> Unit,
    onAccess: () -> Unit, onCloud: () -> Unit, onCreate: (Folder, String) -> Unit,
    sync: FolderSync, onManageSync: (SyncPair) -> Unit,
    onUnlock: (Place) -> Unit, onNewVault: (Folder) -> Unit, onAddFiles: (Folder) -> Unit, onLock: (Place) -> Unit,
    onAddTree: () -> Unit, onRemoveTree: (Place) -> Unit,
    onScan: () -> Unit, onMoveAll: (List<DriveItem>, String) -> Unit,
    onTrashAll: (List<DriveItem>) -> Unit, onRenameAll: (List<Rename>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.Name) }
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val places = remember(refresh) { data.places }
    val vaults = remember(refresh) { data.vaultPlaces }
    val trees = remember(refresh) { data.treePlaces }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hazeSource(haze)) {
            if (folder == null) Overview(
                data, access, places, refresh, top, onOpen, onFolder, onAccess, onCloud, sync, onManageSync,
                onScan = onScan,
                onUpload = { onAddFiles(Folder(data.phone, Environment.DIRECTORY_DOCUMENTS)) },
                onNewFolder = { creating = true },
                onMoveAll = onMoveAll,
                onTrashAll = onTrashAll,
                onRenameAll = onRenameAll,
            )
            else if (folder.place.account == null && !access) {
                Column(Modifier.fillMaxSize().padding(top = top + 130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(EuriseIcons.folder(), stringResource(R.string.drive_phone), stringResource(R.string.drive_access_title))
                    TextButton(onClick = onAccess) { Text(stringResource(R.string.drive_access_grant)) }
                }
            } else FolderList(data, folder, refresh, query, sort, top, onFolder, onOpen, onMore, onUnlock)
        }

        Column(Modifier.fillMaxWidth().glass(haze, RectangleShape, elevation = 0.dp, outline = false, solid = true).statusBarsPadding().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (searching) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.drive_search_hint)) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = false; query = "" }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.drive_cancel)) }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (folder != null && folder.path.isNotEmpty()) {
                        IconButton(onClick = { onFolder(folder.copy(path = folder.path.substringBeforeLast('/', ""))) }) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.drive_back)) }
                    }
                    Text(folder?.path?.substringAfterLast('/')?.ifEmpty { null } ?: stringResource(R.string.drive_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (folder != null) {
                        if (folder.place.vaultRoot != null) {
                            IconButton(onClick = { onAddFiles(folder) }) { Icon(EuriseIcons.upload(), contentDescription = stringResource(R.string.drive_vault_add)) }
                            IconButton(onClick = { onLock(folder.place) }) { Icon(EuriseIcons.lock(), contentDescription = stringResource(R.string.drive_vault_lock)) }
                        } else if (folder.place.isTree) {
                            IconButton(onClick = { onAddFiles(folder) }) { Icon(EuriseIcons.upload(), contentDescription = stringResource(R.string.drive_vault_add)) }
                            if (folder.path.isEmpty()) IconButton(onClick = { onRemoveTree(folder.place) }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.drive_tree_remove)) }
                        } else if (folder.place.account != null) {
                            IconButton(onClick = { onNewVault(folder) }) { Icon(EuriseIcons.lock(), contentDescription = stringResource(R.string.drive_vault_new)) }
                        }
                        IconButton(onClick = { creating = true }) { Icon(EuriseIcons.plus(), contentDescription = stringResource(R.string.drive_new_folder)) }
                        IconButton(onClick = { searching = true }) { Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.drive_search)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(EuriseIcons.sort(), contentDescription = stringResource(R.string.drive_sort)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.drive_sort_name)) }, onClick = { sort = Sort.Name; menu = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.drive_sort_size)) }, onClick = { sort = Sort.Size; menu = false })
                            }
                        }
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.drive_all), dot = null, selected = scope == null) { onScope(null) }
                FilterPill(stringResource(R.string.drive_phone), dot = scheme.onSurfaceVariant, selected = scope == data.phone) { onScope(data.phone) }
                places.forEach { p -> FilterPill(p.label, dot = scheme.primary, selected = scope == p) { onScope(p) } }
                trees.forEach { p -> FilterPill(p.label, dot = scheme.secondary, selected = scope == p) { onScope(p) } }
                FilterPill(stringResource(R.string.drive_tree_add), dot = null, selected = false) { onAddTree() }
                vaults.forEach { p -> FilterPill(stringResource(R.string.drive_vault_pill, p.label), dot = scheme.tertiary, selected = scope == p) { if (data.isOpen(p)) onScope(p) else onUnlock(p) } }
            }
        }
    }
    if (creating) {
        // Dalla panoramica la cartella nuova va in Documenti del telefono.
        val target = folder ?: Folder(data.phone, Environment.DIRECTORY_DOCUMENTS)
        NameDialog(stringResource(R.string.drive_new_folder), "", stringResource(R.string.drive_create), { creating = false }) { name ->
            creating = false
            onCreate(target, name)
        }
    }
}

/** Frase di una cartella cifrata, da scegliere o da inserire. La derivazione della chiave richiede qualche secondo. */
@Composable
internal fun VaultDialog(
    title: String, create: Boolean, onClose: () -> Unit, onDone: () -> Unit,
    onSubmit: suspend (String) -> Unit = {}, onCreate: suspend (String, String) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val valid = if (create) name.isNotBlank() && '/' !in name && password.length >= 8 && password == again else password.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (create) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.drive_name)) })
                    Text(stringResource(R.string.drive_vault_warning), fontSize = 13.sp)
                }
                OutlinedTextField(password, { password = it; error = null }, singleLine = true, label = { Text(stringResource(R.string.drive_vault_password)) }, visualTransformation = PasswordVisualTransformation())
                if (create) OutlinedTextField(again, { again = it }, singleLine = true, label = { Text(stringResource(R.string.drive_vault_again)) }, visualTransformation = PasswordVisualTransformation())
                error?.let { Text(stringResource(it), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        if (create) onCreate(name.trim(), password) else onSubmit(password)
                        onDone()
                    } catch (e: Cryptomator.WrongPassword) {
                        error = R.string.drive_vault_wrong
                    } catch (e: Cryptomator.Unsupported) {
                        error = R.string.drive_vault_unsupported
                    } catch (e: Exception) {
                        error = R.string.drive_error_network
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(if (create) R.string.drive_create else R.string.drive_open)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onClose: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val valid = name.isNotBlank() && '/' !in name
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.drive_name)) }) },
        confirmButton = { TextButton(enabled = valid, onClick = { onDone(name.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

internal val bottomSpace = 110.dp

@Composable
private fun Overview(
    data: DriveData, access: Boolean, places: List<Place>, refresh: Int, top: androidx.compose.ui.unit.Dp,
    onOpen: (DriveItem) -> Unit, onFolder: (Folder?) -> Unit, onAccess: () -> Unit, onCloud: () -> Unit,
    sync: FolderSync, onManageSync: (SyncPair) -> Unit,
    onScan: () -> Unit, onUpload: () -> Unit, onNewFolder: () -> Unit, onMoveAll: (List<DriveItem>, String) -> Unit,
    onTrashAll: (List<DriveItem>) -> Unit = {}, onRenameAll: (List<Rename>) -> Unit = {},
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val recents = remember(refresh) { data.recents() }
    val spaces by produceState(emptyList<Space>(), refresh, access) { value = if (access) data.spaces() else emptyList() }
    val tidy by produceState<Tidy?>(null, refresh, access) {
        value = if (access) runCatching {
            val downloads = data.list(data.phone, Environment.DIRECTORY_DOWNLOADS)
            val documents = runCatching { data.list(data.phone, Environment.DIRECTORY_DOCUMENTS) }.getOrDefault(emptyList())
            val base = tidyOf(downloads, documents)
            // Nomi veri per le scansioni con un nome da scanner: il testo si legge sul telefono, i file restano dove sono.
            base.copy(renames = DriveSmart.proposedNames(data, (downloads + documents).filter { it !in base.copies }))
        }.getOrNull() else null
    }
    // Per i tuoi impegni: oggi e domani dal Calendario, i file con le stesse parole nel nome (tutto sul telefono).
    val forEvents by produceState(emptyList<ForEvent>(), refresh, access) {
        value = runCatching {
            val calendar = eu.eurise.core.data.EuriseData.create(context).calendar
            if (!calendar.hasPermission()) return@runCatching emptyList()
            val now = System.currentTimeMillis()
            val events = calendar.startingBetween(now - 3_600_000L, now + 36 * 3_600_000L).take(6)
                .map { Triple(it.title, it.location, it.start) }
            val files = recents + (if (access) data.list(data.phone, Environment.DIRECTORY_DOCUMENTS) + data.list(data.phone, Environment.DIRECTORY_DOWNLOADS) else emptyList())
            filesForEvents(events, files).take(3)
        }.getOrDefault(emptyList())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 118.dp, bottom = bottomSpace), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "quick") { QuickActions(onScan, onUpload, onNewFolder) }
        if (forEvents.isNotEmpty()) item(key = "events") { ForEventsCard(forEvents, onOpen) }
        tidy?.takeIf { !it.isEmpty }?.let { t ->
            item(key = "tidy") {
                TidyCard(
                    t,
                    onMoveDocs = { onMoveAll(t.documents, Environment.DIRECTORY_DOCUMENTS) },
                    onReview = { onFolder(Folder(data.phone, Environment.DIRECTORY_DOWNLOADS)) },
                    onTrashCopies = onTrashAll,
                    onRename = onRenameAll,
                )
            }
        }
        if (recents.isNotEmpty()) {
            item(key = "l-r") { SectionLabel(stringResource(R.string.drive_recent)) }
            item(key = "recents") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recents, key = { it.key }) { RecentCard(it) { onOpen(it) } }
                }
            }
        }
        item(key = "l-f") { SectionLabel(stringResource(R.string.drive_folders)) }
        item(key = "folders") {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                if (access) {
                    FolderRow(stringResource(R.string.drive_documents), stringResource(R.string.drive_phone)) { onFolder(Folder(data.phone, Environment.DIRECTORY_DOCUMENTS)) }
                    CardDivider()
                    FolderRow(stringResource(R.string.drive_downloads), stringResource(R.string.drive_phone)) { onFolder(Folder(data.phone, Environment.DIRECTORY_DOWNLOADS)) }
                } else {
                    FolderRow(stringResource(R.string.drive_phone), stringResource(R.string.drive_access_grant), onClick = onAccess)
                }
                places.forEach { p ->
                    CardDivider()
                    FolderRow(p.label, stringResource(R.string.drive_title), p) { onFolder(Folder(p, "")) }
                }
            }
        }
        val pairs = sync.pairs()
        if (pairs.isNotEmpty()) {
            item(key = "l-s") { SectionLabel(stringResource(R.string.drive_sync_section)) }
            item(key = "syncs") {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    pairs.forEachIndexed { i, pair ->
                        if (i > 0) CardDivider()
                        FolderRow(pair.local.substringAfterLast('/'), stringResource(R.string.drive_sync_pair, pair.label) + " · " + syncStatus(pair)) { onManageSync(pair) }
                    }
                }
            }
        }
        if (places.isEmpty()) {
            item(key = "cloud") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.drive_no_cloud), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    TextButton(onClick = onCloud) { Text(stringResource(R.string.drive_connect)) }
                }
            }
        }
        if (spaces.isNotEmpty()) {
            item(key = "l-space") { SectionLabel(stringResource(R.string.drive_space)) }
            item(key = "space") { SpaceBars(spaces) }
        }
    }
}

@Composable
private fun RecentCard(item: DriveItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).clickable(onClick = onClick).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (bg, ink) = kindColors(item.extension)
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(item.extension.take(4).uppercase().ifEmpty { "FILE" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        }
        Text(item.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.place.label, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun tint(extension: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (extension) {
        "pdf" -> scheme.errorContainer
        "xls", "xlsx", "ods", "csv" -> scheme.secondaryContainer
        "jpg", "jpeg", "png", "webp" -> scheme.tertiaryContainer
        else -> scheme.primaryContainer
    }
}

@Composable
private fun FolderRow(title: String, detail: String, place: Place? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FolderGlyph(place)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ItemRow(item: DriveItem, onClick: () -> Unit, onMore: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (item.folder) FolderGlyph(item.place, Modifier.size(34.dp, 29.dp))
        else KindTile(item.extension, 36.dp, 36.dp, 9.dp, 10)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(item.place.label, item.size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · ")
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        if (onMore != null) IconButton(onClick = onMore) { Icon(EuriseIcons.more(), contentDescription = item.name, tint = scheme.onSurfaceVariant) }
    }
}

@Composable
private fun FolderList(
    data: DriveData, folder: Folder, refresh: Int, query: String, sort: Sort, top: androidx.compose.ui.unit.Dp,
    onFolder: (Folder?) -> Unit, onOpen: (DriveItem) -> Unit, onMore: (DriveItem) -> Unit, onUnlock: (Place) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var failed by remember(folder) { mutableStateOf(false) }
    val items by produceState<List<DriveItem>?>(null, folder, refresh) {
        value = try { data.list(folder.place, folder.path) } catch (e: java.io.IOException) { failed = true; emptyList() } catch (e: DavException) { failed = true; emptyList() }
    }
    val current = items
    var kind by remember(folder) { mutableStateOf<Kind?>(null) }
    val shown = remember(current, query, sort, kind) {
        current.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .filter { kind == null || (!it.folder && Kind.of(it.extension) == kind) }
            .sortedWith(compareBy<DriveItem> { !it.folder }.then(if (sort == Sort.Size) compareByDescending { it.size ?: 0 } else compareBy { it.name.lowercase() }))
    }
    // Ricerca nel testo dei PDF della cartella (telefono): il nome non c'entra, la parola è dentro. Le scansioni senza
    // testo si rendono cercabili con «Aggiungi il testo». Tutto sul telefono.
    val inText by produceState(emptyList<Pair<DriveItem, String>>(), current, query) {
        value = emptyList()
        val q = query.trim()
        if (q.length < 3 || folder.place.account != null) return@produceState
        kotlinx.coroutines.delay(350)
        val names = shown.map { it.key }.toSet()
        val pdfs = current.orEmpty().filter { !it.folder && it.extension == "pdf" && it.key !in names }.take(80)
        val found = mutableListOf<Pair<DriveItem, String>>()
        for (f in pdfs) {
            DriveSmart.snippet(DriveSmart.pdfText(data, f), q)?.let { found += f to it; value = found.toList() }
        }
    }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 118.dp, bottom = bottomSpace)) {
        if (folder.place.account != null && !folder.place.isTree && folder.place.vaultRoot == null && current.any { it.name == Cryptomator.CONFIG_FILE }) item(key = "vault") {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)
                    .clickable { onUnlock(data.vaultPlace(folder.place, folder.path)) }.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.drive_vault_found), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // Filtri per tipo, solo quelli presenti nella cartella, con quanti sono (PDF 4, Immagini 12…).
        val kinds = current.filter { !it.folder }.groupingBy { Kind.of(it.extension) }.eachCount()
        if (kinds.size > 1) item(key = "kinds") { KindFilter(kinds, kind) { kind = it } }
        if (shown.isEmpty() && inText.isEmpty()) item(key = "e") {
            EmptyState(EuriseIcons.folder(), stringResource(if (failed) R.string.drive_error_network else R.string.drive_empty), stringResource(R.string.drive_empty_msg))
        }
        itemsIndexed(shown, key = { _, it -> it.key }) { i, item ->
            val top2 = if (i == 0) 18.dp else 0.dp
            val bot = if (i == shown.size - 1) 18.dp else 0.dp
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(top2, top2, bot, bot)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                if (i > 0) CardDivider()
                ItemRow(item, onClick = { if (item.folder) onFolder(folder.copy(path = item.path)) else onOpen(item) }, onMore = { onMore(item) })
            }
        }
        if (inText.isNotEmpty()) {
            item(key = "l-text") { SectionLabel(stringResource(R.string.drive_in_text)) }
            itemsIndexed(inText, key = { _, it -> "t:" + it.first.key }) { i, (item, snippet) ->
                val top2 = if (i == 0) 18.dp else 0.dp
                val bot = if (i == inText.size - 1) 18.dp else 0.dp
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(top2, top2, bot, bot)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    if (i > 0) CardDivider()
                    ItemRow(item, onClick = { onOpen(item) }, onMore = { onMore(item) })
                    Text(snippet, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
            }
        }
    }
}

@Composable
internal fun OfflineTab(data: DriveData, refresh: Int, onOpen: (DriveItem) -> Unit, onMore: (DriveItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val items = remember(refresh) { data.offlineItems() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(top = 12.dp, bottom = bottomSpace), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "t") { Text(stringResource(R.string.drive_offline), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (items.isEmpty()) item(key = "e") { EmptyState(EuriseIcons.download(), stringResource(R.string.drive_empty_offline), stringResource(R.string.drive_empty_offline_msg)) }
        items(items, key = { it.key }) { item ->
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                ItemRow(item, onClick = { onOpen(item) }, onMore = { onMore(item) })
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.height(70.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ItemSheet(
    data: DriveData, item: DriveItem, onClose: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit,
    onRename: (String) -> Unit, onTrip: () -> Unit, onSearchable: () -> Unit, onDelete: () -> Unit, onOffline: (Boolean) -> Unit,
    onMove: (String) -> Unit, onFail: (Exception) -> Unit, onRefresh: () -> Unit, onSync: () -> Unit,
) {
    var linking by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    val cloudExtras = remember(item) { data.supportsLinks(item.place) }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(data.isOffline(item)) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background).clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(item.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.place.label, item.size?.let { Formatter.formatShortFileSize(context, it) }).joinToString(" · "), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!item.folder) {
                    SheetAction(EuriseIcons.read(), stringResource(R.string.drive_open), Modifier.weight(1f), onOpen)
                    SheetAction(EuriseIcons.share(), stringResource(R.string.drive_share), Modifier.weight(1f), onShare)
                }
                SheetAction(EuriseIcons.pencil(), stringResource(R.string.drive_rename), Modifier.weight(1f)) { renaming = true }
                SheetAction(EuriseIcons.trash(), stringResource(R.string.drive_delete), Modifier.weight(1f)) { deleting = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(EuriseIcons.folder(), stringResource(R.string.drive_move), Modifier.weight(1f)) { moving = true }
                // Un biglietto in PDF: voli e treni nel Calendario, sveglia e fuso, cartella, modalità Viaggio.
                if (item.extension == "pdf") SheetAction(EuriseIcons.plane(), stringResource(R.string.drive_trip), Modifier.weight(1f), onTrip)
                if (item.folder && item.place.account == null && data.places.isNotEmpty()) {
                    SheetAction(EuriseIcons.refresh(), stringResource(R.string.drive_sync_action), Modifier.weight(1f), onSync)
                }
                if (cloudExtras) {
                    SheetAction(EuriseIcons.link(), stringResource(R.string.drive_link), Modifier.weight(1f)) { linking = true }
                    if (!item.folder) SheetAction(EuriseIcons.clock(), stringResource(R.string.drive_versions), Modifier.weight(1f)) { showVersions = true }
                }
            }
            // Un PDF fatto di sole immagini: copia cercabile accanto, letta sul telefono da Eurise Leggi.
            if (item.extension == "pdf") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(EuriseIcons.text(), stringResource(R.string.drive_text_action), Modifier.weight(1f), onSearchable)
            }
            if (!item.folder && item.place.account != null && item.place.vaultRoot == null && !item.place.isTree) {
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.drive_offline_switch), fontSize = 15.sp)
                        Text(stringResource(R.string.drive_offline_detail), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Switch(checked = offline, onCheckedChange = { offline = it; onOffline(it) })
                }
            }
        }
    }
    if (linking) LinkDialog(data, item, onClose = { linking = false; onRefresh() }, onFail = onFail)
    if (moving) MoveDialog(data, item, onClose = { moving = false }, onMove = onMove)
    if (showVersions) VersionsDialog(data, item, onClose = { showVersions = false; onRefresh() }, onFail = onFail)
    if (renaming) NameDialog(stringResource(R.string.drive_rename), item.name, stringResource(R.string.drive_save), { renaming = false }, onRename)
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.drive_delete_title, item.name)) },
            text = { Text(stringResource(if (item.place.account == null) R.string.drive_delete_phone else R.string.drive_delete_cloud)) },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.drive_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.drive_cancel)) } },
        )
    }
}

@Composable
private fun syncStatus(pair: SyncPair): String = when (pair.state) {
    SyncResult.Never -> stringResource(R.string.drive_sync_never)
    SyncResult.Ok -> stringResource(R.string.drive_sync_done, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(pair.at)))
    SyncResult.Failed -> stringResource(R.string.drive_sync_failed)
    SyncResult.Guarded -> stringResource(R.string.drive_sync_guard)
}

@Composable
internal fun SyncStartDialog(data: DriveData, item: DriveItem, onClose: () -> Unit, onStart: (Place) -> Unit) {
    var place by remember { mutableStateOf(data.places.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.drive_sync_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.places.forEach { p -> FilterPill(p.label, dot = null, selected = p == place) { place = p } }
                }
                Text(stringResource(R.string.drive_sync_privacy), fontSize = 13.sp, lineHeight = 19.sp)
            }
        },
        confirmButton = { TextButton(enabled = place != null, onClick = { place?.let(onStart) }) { Text(stringResource(R.string.drive_sync_start)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

@Composable
internal fun SyncManageDialog(pair: SyncPair, onClose: () -> Unit, onNow: (Boolean) -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(pair.local.substringAfterLast('/')) },
        text = { Text(syncStatus(pair) + "\n\n" + stringResource(R.string.drive_sync_stop_note)) },
        confirmButton = {
            Row {
                if (pair.state == SyncResult.Guarded) TextButton(onClick = { onNow(true) }) { Text(stringResource(R.string.drive_sync_force)) }
                else TextButton(onClick = { onNow(false) }) { Text(stringResource(R.string.drive_sync_now)) }
            }
        },
        dismissButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.drive_sync_stop)) } },
    )
}

private fun copyText(context: Context, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clip.setPrimaryClip(android.content.ClipData.newPlainText("Link", text))
    Toast.makeText(context, R.string.drive_link_copied, Toast.LENGTH_SHORT).show()
}

@Composable
private fun LinkDialog(data: DriveData, item: DriveItem, onClose: () -> Unit, onFail: (Exception) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var days by remember { mutableStateOf<Int?>(30) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.drive_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.drive_link_privacy), fontSize = 13.sp)
                OutlinedTextField(password, { password = it }, singleLine = true, label = { Text(stringResource(R.string.drive_link_password)) })
                Text(stringResource(R.string.drive_link_expiry), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(stringResource(R.string.drive_link_days, 7), dot = null, selected = days == 7) { days = 7 }
                    FilterPill(stringResource(R.string.drive_link_days, 30), dot = null, selected = days == 30) { days = 30 }
                    FilterPill(stringResource(R.string.drive_link_never), dot = null, selected = days == null) { days = null }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                working = true
                scope.launch {
                    try {
                        val expires = days?.let { java.time.LocalDate.now().plusDays(it.toLong()).toString() }
                        copyText(context, data.createLink(item, password.ifBlank { null }, expires))
                        onClose()
                    } catch (e: Exception) {
                        onFail(e)
                    } finally {
                        working = false
                    }
                }
            }) { Text(stringResource(R.string.drive_link_create)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

@Composable
private fun MoveDialog(data: DriveData, item: DriveItem, onClose: () -> Unit, onMove: (String) -> Unit) {
    var path by remember { mutableStateOf(item.path.substringBeforeLast('/', "")) }
    val folders by produceState<List<DriveItem>>(emptyList(), path) {
        value = runCatching { data.list(item.place, path).filter { it.folder && it.path != item.path }.sortedBy { it.name.lowercase() } }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("/" + path) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                if (path.isNotEmpty()) item(key = "up") { FolderRow(stringResource(R.string.drive_move_up), item.place.label) { path = path.substringBeforeLast('/', "") } }
                items(folders, key = { it.key }) { f -> FolderRow(f.name, f.place.label) { path = f.path } }
            }
        },
        confirmButton = { TextButton(onClick = { onClose(); onMove(path) }) { Text(stringResource(R.string.drive_move_here)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

@Composable
private fun VersionsDialog(data: DriveData, item: DriveItem, onClose: () -> Unit, onFail: (Exception) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versions by produceState<List<DavVersion>?>(null) {
        value = try { data.versions(item) } catch (e: Exception) { onFail(e); emptyList() }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.drive_versions)) },
        text = {
            val list = versions
            when {
                list == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                list.isEmpty() -> Text(stringResource(R.string.drive_versions_none))
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.href }) { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.modified, fontSize = 13.sp)
                                v.size?.let { Text(Formatter.formatShortFileSize(context, it), fontSize = 12.sp) }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try { data.restoreVersion(item, v); onClose() } catch (e: Exception) { onFail(e) }
                                }
                            }) { Text(stringResource(R.string.drive_versions_restore)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

@Composable
internal fun SharedTab(data: DriveData, refresh: Int, onRevoke: (Place, DavShare) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val links by produceState(emptyList<Pair<Place, DavShare>>(), refresh) { value = data.links() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(top = 12.dp, bottom = bottomSpace), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "t") { Text(stringResource(R.string.drive_shared), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (links.isEmpty()) item(key = "e") { EmptyState(EuriseIcons.link(), stringResource(R.string.drive_empty_shared), stringResource(R.string.drive_empty_shared_msg)) }
        items(links, key = { it.second.id }) { (place, share) ->
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp)) {
                Text(share.path.substringAfterLast('/'), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(place.label, share.expires?.let { stringResource(R.string.drive_link_until, it.take(10)) }).joinToString(" · ")
                Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { copyText(context, share.url) }) { Text(stringResource(R.string.drive_copy)) }
                    TextButton(onClick = { onRevoke(place, share) }) { Text(stringResource(R.string.drive_link_revoke), color = scheme.error) }
                }
            }
        }
    }
}
