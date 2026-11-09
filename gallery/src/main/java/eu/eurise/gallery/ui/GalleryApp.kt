package eu.eurise.gallery.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.ScrubRail
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudKind
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.PlaceData
import eu.eurise.gallery.R
import eu.eurise.gallery.data.cloudFile
import eu.eurise.gallery.data.fileUri
import eu.eurise.gallery.data.shareItems
import eu.eurise.gallery.logic.UploadRule
import eu.eurise.gallery.logic.dayPlace
import eu.eurise.gallery.logic.deletePlan
import eu.eurise.gallery.logic.places as placesOf
import eu.eurise.gallery.logic.uploadQueue
import eu.eurise.gallery.logic.DayGroup
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Memory
import eu.eurise.gallery.logic.groupAt
import eu.eurise.gallery.logic.groupByDay
import eu.eurise.gallery.logic.isToTidy
import eu.eurise.gallery.logic.onThisDay
import eu.eurise.gallery.logic.railMarks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val StatusGreen = Color(0xFF4F6B5A)

private enum class Filter(val label: Int) { All(R.string.filter_all), Favorites(R.string.filter_favorites), Videos(R.string.filter_videos), Tidy(R.string.filter_tidy) }

private val READ_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun Context.canRead() = READ_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/** Azioni su foto e video, che aprono i fogli di scelta o passano dalle conferme di Android. */
internal class MediaActions(
    val share: (List<MediaItem>) -> Unit,
    val shareClean: (List<MediaItem>) -> Unit,
    val trash: (List<MediaItem>) -> Unit,
    val setFavorite: (MediaItem, Boolean) -> Unit,
    val play: (MediaItem) -> Unit,
    val edit: (MediaItem) -> Unit,
    val diary: (List<MediaItem>) -> Unit,
)

/** Le richieste che Android deve confermare; [done] riceve true se la persona ha accettato. */
internal class SystemOps(
    val trash: (List<MediaItem>, (Boolean) -> Unit) -> Unit,
    val erase: (List<MediaItem>, (Boolean) -> Unit) -> Unit,
    val favorite: (MediaItem, Boolean) -> Unit,
)

@Composable
fun GalleryRoute(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.canRead()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = context.canRead() }
    LaunchedEffect(allowed) { viewModel.onPermission(allowed) }
    if (!allowed) {
        PermissionScreen { ask.launch(READ_PERMISSIONS) }
        return
    }
    val media by viewModel.media.collectAsStateWithLifecycle()
    var onSystem by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onSystem?.invoke(result.resultCode == Activity.RESULT_OK)
        onSystem = null
    }
    val ops = remember(context) {
        val resolver = context.contentResolver
        fun launch(request: android.app.PendingIntent, done: (Boolean) -> Unit) {
            onSystem = done
            system.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
        SystemOps(
            trash = { list, done ->
                if (list.isEmpty()) done(true) else launch(MediaStore.createTrashRequest(resolver, list.map { Uri.parse(it.uri) }, true), done)
            },
            erase = { list, done ->
                if (list.isEmpty()) done(true) else launch(MediaStore.createDeleteRequest(resolver, list.map { Uri.parse(it.uri) }), done)
            },
            favorite = { item, on ->
                system.launch(IntentSenderRequest.Builder(MediaStore.createFavoriteRequest(resolver, listOf(Uri.parse(item.uri)), on).intentSender).build())
            },
        )
    }
    GalleryApp(media, viewModel, ops)
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(EuriseIcons.image(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.permission_title), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.permission_message), fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAllow) { Text(stringResource(R.string.permission_allow)) }
    }
}

@Composable
private fun GalleryApp(media: List<MediaItem>?, vm: GalleryViewModel, ops: SystemOps) {
    val haze = remember { HazeState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val diaries by vm.diaries.collectAsStateWithLifecycle()
    val known by vm.knownUploads.collectAsStateWithLifecycle()
    val cloudAccounts by vm.accounts.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var placeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<String>, String>?>(null) }
    var memory by remember { mutableStateOf<Memory?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var shareFor by remember { mutableStateOf<List<MediaItem>?>(null) }
    var deleteFor by remember { mutableStateOf<List<MediaItem>?>(null) }
    var diaryFor by remember { mutableStateOf<List<MediaItem>?>(null) }
    var openDiary by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    var cloudOpen by remember { mutableStateOf(false) }
    var cleanupOpen by remember { mutableStateOf(false) }
    var secureOpen by remember { mutableStateOf(false) }
    var secureMove by remember { mutableStateOf<List<MediaItem>?>(null) }
    val all = media.orEmpty()
    val byUri = remember(all) { all.associateBy { it.uri } }
    val placeList = remember(all, places) { placesOf(all, places.cells, places.names) }
    val visible = remember(all, filter, album, placeKey, placeList) {
        val inPlace = placeKey?.let { key -> placeList.firstOrNull { it.key == key }?.items?.map { it.uri }?.toSet() ?: emptySet() }
        all.filter { item ->
            (album == null || item.bucket == album) && (inPlace == null || item.uri in inPlace) && when (filter) {
                Filter.All -> true
                Filter.Favorites -> item.favorite
                Filter.Videos -> item.isVideo
                Filter.Tidy -> isToTidy(item)
            }
        }.sortedByDescending { it.takenAt }
    }
    val groups = remember(visible) { groupByDay(visible) }
    val today = LocalDate.now()
    val memories = remember(all, today) { onThisDay(groupByDay(all), today) }
    val queue = remember(all, settings, known) {
        if (settings.uploadOn) uploadQueue(all, UploadRule(settings.buckets, settings.fromMillis), known) else emptyList()
    }
    val pending = remember(queue) { queue.map { it.uri }.toSet() }
    val cloudLabel = cloudAccounts.firstOrNull { it.id == settings.accountId }?.label().orEmpty()
    val failed = stringResource(R.string.share_failed)
    val preparing = stringResource(R.string.share_preparing)
    val sendFailed = stringResource(R.string.action_failed)

    LaunchedEffect(all, settings.placesOn, settings.namesOn) { if (media != null) vm.indexPlaces(all) }
    BackHandler(enabled = selected.isNotEmpty() || album != null || placeKey != null) {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            placeKey != null -> { placeKey = null; section = 2 }
            else -> album = null
        }
    }

    fun send(items: List<MediaItem>, clean: Boolean) {
        Toast.makeText(context, preparing, Toast.LENGTH_SHORT).show()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { shareItems(context, items, clean) } }
                .onSuccess { context.startActivity(it) }
                .onFailure { Toast.makeText(context, if (clean) failed else sendFailed, Toast.LENGTH_LONG).show() }
        }
    }

    val actions = MediaActions(
        share = { shareFor = it },
        shareClean = { send(it, true) },
        trash = { deleteFor = it },
        setFavorite = ops.favorite,
        play = { item ->
            scope.launch {
                val uri = if (item.cloudOnly) withContext(Dispatchers.IO) { cloudFile(context, item) }?.let(context::fileUri) else Uri.parse(item.uri)
                val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(open) } catch (_: ActivityNotFoundException) { }
            }
        },
        edit = { editing = it },
        diary = { diaryFor = it },
    )
    val openIn: (List<MediaItem>, MediaItem) -> Unit = { list, item -> viewer = list.map { it.uri } to item.uri }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when (section) {
                0 -> PhotosPage(
                    groups = groups,
                    visible = visible,
                    filter = filter,
                    album = album,
                    placeLabel = placeKey?.let { key -> placeList.firstOrNull { it.key == key }?.let { placeTitle(it) } ?: key },
                    loaded = media != null,
                    memory = memories.takeIf { filter == Filter.All && album == null && placeKey == null }?.firstOrNull(),
                    selected = selected,
                    actions = actions,
                    pending = pending,
                    places = places,
                    status = uploadStatus(settings.uploadOn, settings.wifiOnly, queue.size, vm.store.lastError != null),
                    onStatus = { cloudOpen = true },
                    onFilter = { filter = it },
                    onClearScope = { album = null; placeKey = null },
                    onSelected = { selected = it },
                    onOpen = { index -> openIn(visible, visible[index]) },
                    onMemory = { memory = it },
                )
                1 -> AlbumsPage(
                    all, diaries, settings.uploadOn, cloudLabel,
                    onOpen = { name -> album = name; placeKey = null; filter = Filter.All; section = 0 },
                    onCloud = { cloudOpen = true },
                    onSecure = { secureOpen = true },
                    onCleanup = { cleanupOpen = true },
                    onDiary = { openDiary = it },
                )
                2 -> PlacesPage(placeList, settings, vm) { key -> placeKey = key; album = null; filter = Filter.All; section = 0 }
                else -> SearchPage(all, places, onOpen = openIn)
            }
        }
        if (selected.isEmpty()) {
            EuriseDock(
                items = listOf(
                    DockItem(stringResource(R.string.tab_photos)) { EuriseIcons.image(it) },
                    DockItem(stringResource(R.string.tab_albums)) { EuriseIcons.grid(it) },
                    DockItem(stringResource(R.string.tab_places)) { EuriseIcons.map(it) },
                    DockItem(stringResource(R.string.tab_search)) { EuriseIcons.search(it) },
                ),
                selected = section,
                onSelect = { section = it },
                hazeState = haze,
                modifier = Modifier.align(Alignment.BottomCenter),
                actionLabel = stringResource(R.string.open_camera),
                actionIcon = EuriseIcons.camera(),
                onAction = { runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } },
            )
        }
        memory?.let { m -> MemoryScreen(m, actions, onClose = { memory = null }) }
        openDiary?.let { id ->
            val diary = diaries.firstOrNull { it.id == id }
            if (diary != null) DiaryScreen(diary, byUri, places, vm, actions, openIn, onClose = { openDiary = null })
        }
        // Il visore legge l'elenco vivo: una foto eliminata sparisce e il cuore si aggiorna subito.
        viewer?.let { (uris, start) ->
            val list = uris.mapNotNull(byUri::get)
            if (list.isEmpty()) viewer = null
            else Viewer(list, list.indexOfFirst { it.uri == start }.coerceAtLeast(0), actions, onClose = { viewer = null })
        }
        if (cloudOpen) CloudScreen(vm, all, onClose = { cloudOpen = false })
        if (cleanupOpen) CleanupScreen(all, ops, onClose = { cleanupOpen = false })
        if (secureOpen || secureMove != null) {
            SecureScreen(vm, ops, secureMove, cloudLabel, onMoved = { secureMove = null; selected = emptySet() }, onClose = { secureOpen = false; secureMove = null })
        }
        editing?.let { item -> EditorScreen(item, onClose = { editing = null }) }
    }

    shareFor?.let { list ->
        ShareSheet(onClose = { shareFor = null }, onChoose = { clean -> shareFor = null; send(list, clean) })
    }
    deleteFor?.let { list ->
        DeleteSheet(
            items = list,
            cloudLabel = cloudLabel,
            onClose = { deleteFor = null },
            onDelete = { choice ->
                deleteFor = null
                selected = emptySet()
                val plan = deletePlan(list, choice)
                ops.trash(plan.phone) { ok -> if (ok && plan.cloud.isNotEmpty()) vm.removeFromCloud(plan.cloud) }
            },
            onProtect = { deleteFor = null; secureMove = list.filter { !it.cloudOnly } },
        )
    }
    diaryFor?.let { list ->
        val title = stringResource(R.string.diary_default_title, longDate(Instant.ofEpochMilli(list.minOf { it.takenAt }).atZone(ZoneId.systemDefault()).toLocalDate()))
        DiaryPicker(
            diaries = diaries,
            onClose = { diaryFor = null },
            onNew = {
                diaryFor = null
                memory = null
                selected = emptySet()
                vm.io {
                    val id = vm.store.createDiary(title, list.map { it.uri })
                    withContext(Dispatchers.Main) { openDiary = id }
                }
            },
            onChoose = { id ->
                diaryFor = null
                memory = null
                selected = emptySet()
                vm.io { vm.store.addToDiary(id, list.map { it.uri }) }
                openDiary = id
            },
        )
    }
}

internal fun CloudAccount.label(): String = when (kind) {
    CloudKind.Murena -> "Murena"
    CloudKind.KDrive -> "kDrive"
    else -> name
}

@Composable
private fun uploadStatus(on: Boolean, wifiOnly: Boolean, count: Int, error: Boolean): String? = when {
    !on -> null
    error -> stringResource(R.string.queue_error)
    count == 0 -> stringResource(R.string.queue_done)
    wifiOnly -> stringResource(R.string.queue_wifi, count)
    else -> stringResource(R.string.queue_status, count)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosPage(
    groups: List<DayGroup>,
    visible: List<MediaItem>,
    filter: Filter,
    album: String?,
    placeLabel: String?,
    loaded: Boolean,
    memory: Memory?,
    selected: Set<Long>,
    actions: MediaActions,
    pending: Set<String>,
    places: PlaceData,
    status: String?,
    onStatus: () -> Unit,
    onFilter: (Filter) -> Unit,
    onClearScope: () -> Unit,
    onSelected: (Set<Long>) -> Unit,
    onOpen: (Int) -> Unit,
    onMemory: (Memory) -> Unit,
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerDp = with(density) { headerPx.toDp() }
    val indexOf = remember(visible) { visible.withIndex().associate { (i, item) -> item.id to i } }
    // Posizione nella griglia dell'intestazione di ogni giorno, per la barra di anni e mesi.
    val firstRow = if (memory != null) 1 else 0
    val starts = remember(groups, firstRow) {
        var next = firstRow
        groups.map { g -> next.also { next += 1 + g.items.size } }
    }
    val marks = remember(groups) { railMarks(groups) }
    val tap: (MediaItem) -> Unit = { item ->
        if (selected.isNotEmpty()) onSelected(if (item.id in selected) selected - item.id else selected + item.id)
        else indexOf[item.id]?.let(onOpen)
    }

    Box(Modifier.fillMaxSize()) {
        if (loaded && visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(icon = EuriseIcons.image(), title = stringResource(R.string.empty_title), message = stringResource(R.string.empty_message))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            contentPadding = PaddingValues(start = 12.dp, end = if (marks.size > 1) 42.dp else 12.dp, top = headerDp + 8.dp, bottom = GlassDefaults.PillHeight + 56.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            memory?.let { m ->
                item(key = "memory", span = { GridItemSpan(maxLineSpan) }) { MemoryCard(m) { onMemory(m) } }
            }
            groups.forEach { group ->
                item(key = "day-${group.date}", span = { GridItemSpan(maxLineSpan) }) {
                    val ids = group.items.map { it.id }.toSet()
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val place = remember(group, places) { dayPlace(group.items, places.cells, places.names) }
                        Text(if (place != null) "${dayLabel(group.date)} · $place" else dayLabel(group.date), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSelected(if (selected.containsAll(ids)) selected - ids else selected + ids) }) {
                            Text(stringResource(R.string.select), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(group.items, key = { it.uri }) { item ->
                    Tile(item, item.id in selected, item.uri in pending, onClick = { tap(item) }, onLongClick = {
                        onSelected(if (item.id in selected) selected - item.id else selected + item.id)
                    })
                }
            }
        }
        if (marks.size > 1) {
            val monthsDescription = stringResource(R.string.go_to_month)
            ScrubRail(
                labels = marks.map { "${Month.of(it.month).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)} ${it.year % 100}" },
                onScrub = { f ->
                    val index = groupAt(marks, groups.size, f)
                    scope.launch { grid.scrollToItem(starts[index]) }
                },
                bubble = { f -> longDate(groups[groupAt(marks, groups.size, f)].date) },
                description = monthsDescription,
                width = 42.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = headerDp + 8.dp, bottom = GlassDefaults.PillHeight + 56.dp).fillMaxHeight(),
            )
        }

        // Testata opaca: titolo, fotocamera e filtri, oppure la barra della selezione.
        Column(
            Modifier
                .fillMaxWidth()
                // Prima dei margini: la griglia deve partire sotto tutta la testata, barra di stato compresa.
                .onSizeChanged { headerPx = it.height }
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected.isNotEmpty()) {
                val chosen = visible.filter { it.id in selected }
                Row(Modifier.padding(start = 4.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelected(emptySet()) }) { Icon(EuriseIcons.close(), stringResource(R.string.close), modifier = Modifier.size(20.dp)) }
                    Text(pluralStringResource(R.plurals.selected_count, selected.size, selected.size), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { actions.diary(chosen) }) { Icon(EuriseIcons.book(), stringResource(R.string.add_to_diary), modifier = Modifier.size(21.dp)) }
                    IconButton(onClick = { actions.share(chosen) }) { Icon(EuriseIcons.share(), stringResource(R.string.share), modifier = Modifier.size(21.dp)) }
                    IconButton(onClick = { actions.trash(chosen) }) {
                        Icon(EuriseIcons.trash(), stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
                    }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(album ?: placeLabel ?: stringResource(R.string.tab_photos), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (status != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onStatus).padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(EuriseIcons.cloud(2.2f), null, tint = StatusGreen, modifier = Modifier.size(15.dp))
                            Text(status, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = StatusGreen)
                        }
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (album ?: placeLabel)?.let { scope -> item(key = "scope") { FilterPill(scope, null, true, closable = true, onClick = onClearScope) } }
                items(Filter.entries, key = { it.name }) { f -> FilterPill(stringResource(f.label), null, filter == f) { onFilter(f) } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Tile(item: MediaItem, chosen: Boolean, waiting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (chosen) Modifier.border(3.dp, primary) else Modifier),
    ) {
        Thumb(item, Modifier.fillMaxSize())
        if (item.isVideo) {
            Text(duration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        if (item.cloudOnly || waiting) {
            Box(
                Modifier.align(if (item.isVideo) Alignment.TopEnd else Alignment.BottomEnd).padding(6.dp).size(20.dp).background(Color(0x8C14171C), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (item.cloudOnly) Icon(EuriseIcons.cloud(2.4f), stringResource(R.string.badge_cloud), tint = Color.White, modifier = Modifier.size(12.dp))
                else Icon(EuriseIcons.upload(2.6f), stringResource(R.string.badge_pending), tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        if (item.favorite) {
            Icon(EuriseIcons.heart(2.4f), null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp))
        }
        if (chosen) {
            Box(Modifier.padding(6.dp).size(22.dp).background(primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.check(3.4f), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: Memory, onClick: () -> Unit) {
    val years = pluralStringResource(R.plurals.memory_years, memory.yearsAgo, memory.yearsAgo)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(memory.items.first(), Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.memory_card, years), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(
                "${longDate(memory.date)} · ${pluralStringResource(R.plurals.items_count, memory.items.size, memory.items.size)}",
                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMMM" else "d MMMM yyyy", Locale.getDefault()))
    }
}

internal fun longDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))

private fun duration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
