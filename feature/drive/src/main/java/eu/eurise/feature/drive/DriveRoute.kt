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

private enum class Tab { Files, Offline, Shared, Trash }

/** Una cartella aperta, oppure null quando si è sulla panoramica. */
internal data class Folder(val place: Place, val path: String)

private fun hasAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
fun DriveRoute(onBack: () -> Unit, onScan: () -> Unit, onCloud: () -> Unit, onNewPhone: () -> Unit = onCloud, startInTrash: Boolean = false) {
    val context = LocalContext.current
    val data = remember { DriveData(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    var access by remember { mutableStateOf(hasAccess(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) access = hasAccess(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var tab by remember { mutableStateOf(if (startInTrash) Tab.Trash else Tab.Files) }
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

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { data.addTree(uri) }
                .onSuccess { scope = it; folder = Folder(it, ""); refresh++ }
                .onFailure { Toast.makeText(context, R.string.drive_tree_failed, Toast.LENGTH_LONG).show() }
        }
    }
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
        PdfViewer(file, title, onEdit = officeEdit(context, file)) { pdf = null }
        return
    }

    BackHandler(enabled = folder != null) {
        val f = folder ?: return@BackHandler
        folder = if (f.path.isEmpty() || (scope == null && f.path.substringBeforeLast('/', "").isEmpty() && f.place.account == null)) null else f.copy(path = f.path.substringBeforeLast('/', ""))
    }
    BackHandler(enabled = folder == null, onBack = onBack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Il fondo da sfocare è il contenuto di ogni scheda; testata e dock stanno sopra (come nelle Note): se la testata
        // satinata fosse dentro il suo stesso fondo, Haze non la disegnerebbe e resterebbe trasparente.
        Box(Modifier.fillMaxSize()) {
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
                    onAddTree = { pickTree.launch(null) },
                    onRemoveTree = { p -> data.removeTree(p); scope = null; folder = null; refresh++ },
                    onScan = onScan,
                    onMoveAll = { items, to -> action { items.forEach { runCatching { data.move(it, to) } } } },
                    onTrashAll = { items -> action { items.forEach { runCatching { data.delete(it) } } } },
                    onRenameAll = { list -> action { list.forEach { r -> runCatching { data.rename(r.item, r.name) } } } },
                )
                Tab.Offline -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hazeSource(haze)) { OfflineTab(data, refresh, ::open) { selected = it } }
                Tab.Shared -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hazeSource(haze)) { SharedTab(data, refresh) { place, share -> action { data.removeLink(place, share) } } }
                Tab.Trash -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hazeSource(haze)) {
                    AllTrash(data, refresh, onRestoreFile = { action { data.restore(it) } }, onDeleteFile = { action { data.deleteForever(it) } }, onNewPhone = onNewPhone, bottomSpace = bottomSpace)
                }
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
            solid = true,
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
            onTrip = {
                selected = null
                coroutines.launch {
                    try {
                        val file = data.localCopy(item)
                        val intent = android.content.Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity")
                            .putExtra("eu.eurise.extra.SECTION", "trip:file:" + android.net.Uri.encode(file.absolutePath))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        context.startActivity(intent)
                    } catch (e: Exception) { fail(e) }
                }
            },
            onSearchable = {
                selected = null
                if (!DocReading.enabled(context)) {
                    Toast.makeText(context, R.string.drive_text_off, Toast.LENGTH_LONG).show()
                } else coroutines.launch {
                    Toast.makeText(context, R.string.drive_text_reading, Toast.LENGTH_SHORT).show()
                    try {
                        val message = when (val o = Searchable.make(context, data, item)) {
                            is Searchable.Outcome.Done -> context.getString(R.string.drive_text_done, o.name)
                            Searchable.Outcome.AlreadyText -> context.getString(R.string.drive_text_already)
                            is Searchable.Outcome.NotRead -> context.getString(
                                when (o.result) {
                                    ReadClient.Result.NotInstalled -> R.string.drive_text_missing
                                    ReadClient.Result.NoModel -> R.string.drive_text_no_model
                                    else -> R.string.drive_text_failed
                                },
                            )
                            Searchable.Outcome.Failed -> context.getString(R.string.drive_text_failed)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        refresh++
                    } catch (e: Exception) { fail(e) }
                }
            },
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

/**
 * Apre [file] con l'app scelta. I file del telefono sono quelli veri: l'app (Office) può anche salvarli. Le copie di
 * file del cloud o della cartella cifrata restano in sola lettura, perché una modifica lì non tornerebbe al suo posto.
 */
private fun viewExternally(context: Context, file: File) {
    val writable = file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath + "/")
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(context, file), mime(file)).addFlags(flags)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.drive_no_app, Toast.LENGTH_SHORT).show()
    }
}

/** «Modifica in Office» per un PDF di Eurise File, o null se Office non è installata. */
private fun officeEdit(context: Context, file: File): (() -> Unit)? {
    val writable = file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath + "/")
    val intent = eu.eurise.core.suite.OfficeContract.editIntent(context, uriOf(context, file), "application/pdf", writable) ?: return null
    return { context.startActivity(intent) }
}

internal fun share(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).setType(mime(file)).putExtra(Intent.EXTRA_STREAM, uriOf(context, file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
