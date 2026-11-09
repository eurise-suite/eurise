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
import androidx.compose.foundation.shape.CircleShape
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

private enum class Tab { Files, Offline, Shared, Trash }

/** Una cartella aperta, oppure null quando si è sulla panoramica. */
private data class Folder(val place: Place, val path: String)

private fun hasAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
fun DriveRoute(onBack: () -> Unit, onScan: () -> Unit, onCloud: () -> Unit) {
    val context = LocalContext.current
    val data = remember { DriveData(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(hasAccess(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) access = hasAccess(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var tab by remember { mutableStateOf(Tab.Files) }
    var scope by remember { mutableStateOf<Place?>(null) }
    var folder by remember { mutableStateOf<Folder?>(null) }
    var selected by remember { mutableStateOf<DriveItem?>(null) }
    var pdf by remember { mutableStateOf<Pair<File, String>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var askAccess by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val coroutines = rememberCoroutineScope()
    val haze = remember { HazeState() }
    val sync = remember { FolderSync(context.applicationContext, data) }
    var pickSync by remember { mutableStateOf<DriveItem?>(null) }
    var manageSync by remember { mutableStateOf<SyncPair?>(null) }
    var unlocking by remember { mutableStateOf<Place?>(null) }
    var newVaultIn by remember { mutableStateOf<Folder?>(null) }
    var addingTo by remember { mutableStateOf<Folder?>(null) }
    // Uscendo da File le cartelle si richiudono e le copie in chiaro spariscono.
    DisposableEffect(Unit) { onDispose { data.lockAll() } }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = hasAccess(context) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        addingTo?.let { target ->
            coroutines.launch {
                loading = true
                try { uris.forEach { data.addToVault(target, it) }; refresh++ } catch (e: Exception) { Toast.makeText(context, R.string.drive_error_network, Toast.LENGTH_SHORT).show() } finally { loading = false }
            }
        }
    }

    fun fail(e: Exception) {
        val text = when {
            e is DavException && (e.code == 401 || e.code == 403) -> R.string.drive_error_rejected
            e is java.io.IOException || e is DavException -> R.string.drive_error_network
            else -> R.string.drive_error
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun open(item: DriveItem) {
        loading = true
        coroutines.launch {
            try {
                val file = data.localCopy(item)
                data.remember(item)
                if (item.extension == "pdf") pdf = file to item.name else viewExternally(context, file)
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    fun action(block: suspend () -> Unit) {
        coroutines.launch {
            try { block(); refresh++ } catch (e: Exception) { fail(e) }
        }
    }

    pdf?.let { (file, title) ->
        BackHandler { pdf = null }
        PdfViewer(file, title) { pdf = null }
        return
    }

    BackHandler(enabled = folder != null) {
        val f = folder ?: return@BackHandler
        folder = if (f.path.isEmpty() || (scope == null && f.path.substringBeforeLast('/', "").isEmpty() && f.place.account == null)) null else f.copy(path = f.path.substringBeforeLast('/', ""))
    }
    BackHandler(enabled = folder == null, onBack = onBack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when (tab) {
                Tab.Files -> FilesTab(
                    data, access, scope, folder, refresh, haze,
                    onScope = { scope = it; folder = it?.let { p -> Folder(p, "") } },
                    onFolder = { folder = it },
                    onOpen = ::open,
                    onMore = { selected = it },
                    onAccess = { askAccess = true },
                    onCloud = onCloud,
                    onCreate = { f, name -> action { data.mkdir(f.place, DriveData.join(f.path, name)) } },
                    sync = sync,
                    onManageSync = { manageSync = it },
                    onUnlock = { unlocking = it },
                    onNewVault = { newVaultIn = it },
                    onAddFiles = { addingTo = it; pickFiles.launch(arrayOf("*/*")) },
                    onLock = { p -> data.lock(p); scope = null; folder = null; refresh++ },
                )
                Tab.Offline -> OfflineTab(data, refresh, ::open) { selected = it }
                Tab.Shared -> SharedTab(data, refresh) { place, share -> action { data.removeLink(place, share) } }
                Tab.Trash -> TrashTab(data, refresh, onRestore = { action { data.restore(it) } }, onDelete = { action { data.deleteForever(it) } })
            }
        }
        if (loading) {
            Row(Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.drive_downloading), fontSize = 14.sp)
            }
        }
        val titles = listOf(R.string.drive_title to { s: Float -> EuriseIcons.folder(s) }, R.string.drive_offline to { s: Float -> EuriseIcons.download(s) }, R.string.drive_shared to { s: Float -> EuriseIcons.link(s) }, R.string.drive_trash to { s: Float -> EuriseIcons.trash(s) })
        EuriseDock(
            items = titles.map { (label, icon) -> DockItem(stringResource(label), icon) },
            selected = tab.ordinal,
            onSelect = { tab = Tab.entries[it] },
            hazeState = haze,
            actionLabel = stringResource(R.string.drive_scan),
            onAction = onScan,
            actionIcon = EuriseIcons.scan(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    unlocking?.let { place ->
        VaultDialog(
            title = stringResource(R.string.drive_vault_open, place.label),
            create = false,
            onClose = { unlocking = null },
            onSubmit = { password -> data.unlock(place, password) },
            onDone = { unlocking = null; scope = place; folder = Folder(place, ""); refresh++ },
        )
    }
    newVaultIn?.let { parent ->
        var created by remember { mutableStateOf<Place?>(null) }
        VaultDialog(
            title = stringResource(R.string.drive_vault_new),
            create = true,
            onClose = { newVaultIn = null },
            onCreate = { name, password -> created = data.createVault(parent, name, password) },
            onDone = {
                newVaultIn = null
                created?.let { scope = it; folder = Folder(it, "") }
                refresh++
            },
        )
    }
    pickSync?.let { item ->
        SyncStartDialog(data, item, onClose = { pickSync = null }) { place ->
            pickSync = null
            action { sync.add(item.path, place); FolderSync.schedule(context.applicationContext) }
        }
    }
    manageSync?.let { pair ->
        SyncManageDialog(pair, onClose = { manageSync = null }, onNow = { force ->
            manageSync = null
            action { sync.run(pair.id, force) }
        }, onStop = {
            manageSync = null
            sync.remove(pair.id)
            refresh++
        })
    }
    selected?.let { item ->
        ItemSheet(
            data, item,
            onClose = { selected = null },
            onOpen = { selected = null; open(item) },
            onShare = {
                selected = null
                coroutines.launch { try { share(context, data.localCopy(item)) } catch (e: Exception) { fail(e) } }
            },
            onRename = { name -> selected = null; action { data.rename(item, name) } },
            onDelete = { selected = null; action { data.delete(item) } },
            onOffline = { on -> action { data.setOffline(item, on) } },
            onMove = { to -> selected = null; action { data.move(item, to) } },
            onFail = ::fail,
            onRefresh = { refresh++ },
            onSync = { selected = null; pickSync = item },
        )
    }
    if (askAccess) {
        AlertDialog(
            onDismissRequest = { askAccess = false },
            title = { Text(stringResource(R.string.drive_access_title)) },
            text = { Text(stringResource(R.string.drive_access_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askAccess = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)))
                    } else {
                        legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }) { Text(stringResource(R.string.drive_access_grant)) }
            },
            dismissButton = { TextButton(onClick = { askAccess = false }) { Text(stringResource(R.string.drive_cancel)) } },
        )
    }
}

private fun uriOf(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".drivefiles", file)

private fun mime(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "*/*"

private fun viewExternally(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(context, file), mime(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.drive_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).setType(mime(file)).putExtra(Intent.EXTRA_STREAM, uriOf(context, file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

private enum class Sort { Name, Size }

@Composable
private fun FilesTab(
    data: DriveData, access: Boolean, scope: Place?, folder: Folder?, refresh: Int, haze: HazeState,
    onScope: (Place?) -> Unit, onFolder: (Folder?) -> Unit, onOpen: (DriveItem) -> Unit, onMore: (DriveItem) -> Unit,
    onAccess: () -> Unit, onCloud: () -> Unit, onCreate: (Folder, String) -> Unit,
    sync: FolderSync, onManageSync: (SyncPair) -> Unit,
    onUnlock: (Place) -> Unit, onNewVault: (Folder) -> Unit, onAddFiles: (Folder) -> Unit, onLock: (Place) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.Name) }
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val places = remember(refresh) { data.places }
    val vaults = remember(refresh) { data.vaultPlaces }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        if (folder == null) Overview(data, access, places, refresh, top, onOpen, onFolder, onAccess, onCloud, sync, onManageSync)
        else if (folder.place.account == null && !access) {
            Column(Modifier.fillMaxSize().padding(top = top + 130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(EuriseIcons.folder(), stringResource(R.string.drive_phone), stringResource(R.string.drive_access_title))
                TextButton(onClick = onAccess) { Text(stringResource(R.string.drive_access_grant)) }
            }
        } else FolderList(data, folder, refresh, query, sort, top, onFolder, onOpen, onMore, onUnlock)

        Column(Modifier.fillMaxWidth().glass(haze, RectangleShape, elevation = 0.dp, outline = false).statusBarsPadding().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                vaults.forEach { p -> FilterPill(stringResource(R.string.drive_vault_pill, p.label), dot = scheme.tertiary, selected = scope == p) { if (data.isOpen(p)) onScope(p) else onUnlock(p) } }
            }
        }
    }
    if (creating && folder != null) {
        NameDialog(stringResource(R.string.drive_new_folder), "", stringResource(R.string.drive_create), { creating = false }) { name ->
            creating = false
            onCreate(folder, name)
        }
    }
}

/** Frase di una cartella cifrata, da scegliere o da inserire. La derivazione della chiave richiede qualche secondo. */
@Composable
private fun VaultDialog(
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

private val bottomSpace = 110.dp

@Composable
private fun Overview(
    data: DriveData, access: Boolean, places: List<Place>, refresh: Int, top: androidx.compose.ui.unit.Dp,
    onOpen: (DriveItem) -> Unit, onFolder: (Folder?) -> Unit, onAccess: () -> Unit, onCloud: () -> Unit,
    sync: FolderSync, onManageSync: (SyncPair) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val recents = remember(refresh) { data.recents() }
    val spaces by produceState(emptyList<Space>(), refresh, access) { value = if (access) data.spaces() else emptyList() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 118.dp, bottom = bottomSpace), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    FolderRow(p.label, stringResource(R.string.drive_title)) { onFolder(Folder(p, "")) }
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
            item(key = "space") {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drive_space), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    spaces.forEach { s ->
                        val used = Formatter.formatShortFileSize(context, s.used)
                        val total = s.total?.let { Formatter.formatShortFileSize(context, it) }
                        Text(if (total != null) "${s.label}: $used / $total" else "${s.label}: $used", fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(item: DriveItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).clickable(onClick = onClick).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(tint(item.extension)), contentAlignment = Alignment.Center) {
            Text(item.extension.take(4).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimaryContainer)
        }
        Text(item.name, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.place.label, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun tint(extension: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (extension) {
        "pdf" -> scheme.errorContainer
        "xls", "xlsx", "ods", "csv" -> scheme.secondaryContainer
        "jpg", "jpeg", "png", "webp" -> scheme.tertiaryContainer
        else -> scheme.primaryContainer
    }
}

@Composable
private fun FolderRow(title: String, detail: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(EuriseIcons.folder(), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
        if (item.folder) {
            Icon(EuriseIcons.folder(), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tint(item.extension)), contentAlignment = Alignment.Center) {
                Text(item.extension.take(3).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimaryContainer)
            }
        }
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
    val shown = remember(current, query, sort) {
        current.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy<DriveItem> { !it.folder }.then(if (sort == Sort.Size) compareByDescending { it.size ?: 0 } else compareBy { it.name.lowercase() }))
    }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 118.dp, bottom = bottomSpace)) {
        if (folder.place.account != null && folder.place.vaultRoot == null && current.any { it.name == Cryptomator.CONFIG_FILE }) item(key = "vault") {
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
        if (shown.isEmpty()) item(key = "e") {
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
    }
}

@Composable
private fun OfflineTab(data: DriveData, refresh: Int, onOpen: (DriveItem) -> Unit, onMore: (DriveItem) -> Unit) {
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
private fun TrashTab(data: DriveData, refresh: Int, onRestore: (TrashItem) -> Unit, onDelete: (TrashItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val items by produceState(emptyList<TrashItem>(), refresh) { value = data.trash() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(top = 12.dp, bottom = bottomSpace), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "t") { Text(stringResource(R.string.drive_trash), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp)) }
        if (items.isEmpty()) item(key = "e") { EmptyState(EuriseIcons.trash(), stringResource(R.string.drive_empty_trash), stringResource(R.string.drive_empty_trash_msg)) }
        items(items, key = { it.id }) { item ->
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Formatter.formatShortFileSize(context, item.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = { onRestore(item) }) { Text(stringResource(R.string.drive_restore)) }
                    TextButton(onClick = { onDelete(item) }) { Text(stringResource(R.string.drive_delete_forever), color = scheme.error) }
                }
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
private fun ItemSheet(
    data: DriveData, item: DriveItem, onClose: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit,
    onRename: (String) -> Unit, onDelete: () -> Unit, onOffline: (Boolean) -> Unit,
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
                if (item.folder && item.place.account == null && data.places.isNotEmpty()) {
                    SheetAction(EuriseIcons.refresh(), stringResource(R.string.drive_sync_action), Modifier.weight(1f), onSync)
                }
                if (cloudExtras) {
                    SheetAction(EuriseIcons.link(), stringResource(R.string.drive_link), Modifier.weight(1f)) { linking = true }
                    if (!item.folder) SheetAction(EuriseIcons.clock(), stringResource(R.string.drive_versions), Modifier.weight(1f)) { showVersions = true }
                }
            }
            if (!item.folder && item.place.account != null && item.place.vaultRoot == null) {
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
private fun SyncStartDialog(data: DriveData, item: DriveItem, onClose: () -> Unit, onStart: (Place) -> Unit) {
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
private fun SyncManageDialog(pair: SyncPair, onClose: () -> Unit, onNow: (Boolean) -> Unit, onStop: () -> Unit) {
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
private fun SharedTab(data: DriveData, refresh: Int, onRevoke: (Place, DavShare) -> Unit) {
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

// --- Lettore PDF ---

/** Il motore di sistema legge una pagina alla volta: il Mutex impedisce due letture insieme. */
private class PdfSource(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    val pageCount = renderer.pageCount
    val ratios: List<Float> = (0 until pageCount).map { i -> renderer.openPage(i).use { it.width.toFloat() / it.height } }
    @Volatile private var closed = false

    suspend fun render(index: Int, width: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.Default) {
            renderer.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    /** Pagine in cui compare [query] e quante volte. Il motore di sistema legge il testo solo da Android 15. */
    @androidx.annotation.RequiresApi(35)
    suspend fun search(query: String): List<Pair<Int, Int>> = mutex.withLock {
        withContext(Dispatchers.Default) {
            (0 until pageCount).mapNotNull { i ->
                val hits = renderer.openPage(i).use { it.searchText(query).size }
                if (hits > 0) i to hits else null
            }
        }
    }

    /** Ricrea il PDF pagina per pagina come immagini, disegnando sopra i tratti. */
    suspend fun exportWithInk(ink: Map<Int, List<List<Offset>>>, target: File, density: Float) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val document = android.graphics.pdf.PdfDocument()
            try {
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { source ->
                        val out = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(source.width, source.height, i + 1).create())
                        val width = source.width * 2
                        val height = source.height * 2
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            source.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        }
                        out.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, source.width, source.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        bitmap.recycle()
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(20, 32, 90)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.6f
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        ink[i].orEmpty().forEach { stroke ->
                            val path = android.graphics.Path()
                            stroke.forEachIndexed { n, p ->
                                val x = p.x * source.width
                                val y = p.y * source.height
                                if (n == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            out.canvas.drawPath(path, paint)
                        }
                        document.finishPage(out)
                    }
                }
                target.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
        }
    }

    override fun close() {
        closed = true
        renderer.close()
        descriptor.close()
    }
}

private fun savePdfCopy(context: Context, file: File, name: String): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Eurise/Firmati")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }
    @Suppress("DEPRECATION")
    val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Eurise/Firmati").apply { mkdirs() }
    file.copyTo(File(folder, name), overwrite = false)
    return true
}

/** Si disegna con il dito su una pagina alla volta; il salvataggio crea una copia e l'originale resta intatto. */
@Composable
private fun PdfAnnotator(source: PdfSource, start: Int, file: File, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var page by remember { mutableIntStateOf(start) }
    val ink = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<List<Offset>>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(scheme.surfaceVariant).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.drive_cancel)) }
            Text(stringResource(R.string.drive_pdf_page, page + 1, source.pageCount), fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { ink[page] = ink[page].orEmpty().dropLast(1) }) { Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.drive_pdf_undo)) }
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val ok = runCatching {
                        val out = File(context.cacheDir, "drive/firmato.pdf").apply { parentFile?.mkdirs() }
                        source.exportWithInk(ink.toMap(), out, density)
                        withContext(Dispatchers.IO) { savePdfCopy(context, out, title.removeSuffix(".pdf") + " firmato.pdf") }
                    }.getOrDefault(false)
                    saving = false
                    Toast.makeText(context, if (ok) R.string.drive_pdf_saved else R.string.drive_error, Toast.LENGTH_LONG).show()
                    if (ok) onClose()
                }
            }) { Text(stringResource(R.string.drive_pdf_save)) }
        }
        Text(stringResource(R.string.drive_pdf_save_note), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            val pixels = with(LocalDensity.current) { maxWidth.toPx().toInt() }
            val bitmap by produceState<Bitmap?>(null, page) { value = runCatching { source.render(page, pixels) }.getOrNull() }
            Box(Modifier.aspectRatio(source.ratios[page], matchHeightConstraintsFirst = maxWidth / maxHeight > source.ratios[page]).background(Color.White)) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxSize().pointerInput(page) {
                        detectDragGestures(
                            onDragStart = { current = listOf(Offset(it.x / size.width, it.y / size.height)) },
                            onDrag = { change, _ ->
                                change.consume()
                                current = current + Offset(change.position.x / size.width, change.position.y / size.height)
                            },
                            onDragEnd = {
                                ink[page] = ink[page].orEmpty() + listOf(current)
                                current = emptyList()
                            },
                        )
                    },
                ) {
                    (ink[page].orEmpty() + listOf(current)).filter { it.size > 1 }.forEach { stroke ->
                        val path = androidx.compose.ui.graphics.Path()
                        stroke.forEachIndexed { n, p -> if (n == 0) path.moveTo(p.x * size.width, p.y * size.height) else path.lineTo(p.x * size.width, p.y * size.height) }
                        drawPath(path, Color(0xFF14205A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text(stringResource(R.string.drive_pdf_prev)) }
            TextButton(enabled = page < source.pageCount - 1, onClick = { page++ }) { Text(stringResource(R.string.drive_pdf_next)) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfViewer(file: File, title: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val source = remember(file) { runCatching { PdfSource(file) }.getOrNull() }
    DisposableEffect(source) { onDispose { source?.close() } }
    var scale by remember { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { zoom, _, _ -> scale = (scale * zoom).coerceIn(1f, 4f) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var annotating by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int, Int>>?>(null) }
    val canSearch = Build.VERSION.SDK_INT >= 35

    if (annotating && source != null) {
        PdfAnnotator(source, list.firstVisibleItemIndex.coerceAtMost(source.pageCount - 1), file, title) { annotating = false }
        return
    }

    Column(Modifier.fillMaxSize().background(scheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.drive_back), tint = scheme.primary) }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (source != null) {
                if (canSearch) IconButton(onClick = { searching = !searching; results = null }) { Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.drive_pdf_search)) }
                IconButton(onClick = { annotating = true }) { Icon(EuriseIcons.pen(), contentDescription = stringResource(R.string.drive_pdf_annotate)) }
            }
        }
        if (source == null) {
            Text(stringResource(R.string.drive_pdf_error), modifier = Modifier.padding(24.dp))
            return
        }
        if (searching && canSearch) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.drive_pdf_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { if (query.isNotBlank() && Build.VERSION.SDK_INT >= 35) scope.launch { results = runCatching { source.search(query.trim()) }.getOrDefault(emptyList()) } }) {
                            Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.drive_pdf_search))
                        }
                    },
                )
                results?.let { found ->
                    if (found.isEmpty()) Text(stringResource(R.string.drive_pdf_no_results), fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        found.forEach { (index, hits) ->
                            FilterPill(stringResource(R.string.drive_pdf_result, index + 1, hits), dot = null, selected = false) { scope.launch { list.scrollToItem(index) } }
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().transformable(transform, canPan = { false }).horizontalScroll(rememberScrollState())) {
            val width = maxWidth * scale
            val pixels = with(LocalDensity.current) { maxWidth.toPx().toInt() }
            LazyColumn(Modifier.width(width).fillMaxSize(), state = list, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(source.ratios, key = { i, _ -> i }) { i, ratio ->
                    // Risoluzione fissata a 2× schermo: abbastanza per lo zoom, senza riempire la memoria.
                    val bitmap by produceState<Bitmap?>(null, source, i) { value = runCatching { source.render(i, pixels * 2) }.getOrNull() }
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White)) {
                        val page = stringResource(R.string.drive_pdf_page, i + 1, source.pageCount)
                        bitmap?.let { Image(it.asImageBitmap(), contentDescription = page, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
}
