package eu.eurise.gallery.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import eu.eurise.gallery.GalleryViewModel
import eu.eurise.gallery.R
import eu.eurise.gallery.logic.DayGroup
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Memory
import eu.eurise.gallery.logic.albums
import eu.eurise.gallery.logic.groupAt
import eu.eurise.gallery.logic.groupByDay
import eu.eurise.gallery.logic.isToTidy
import eu.eurise.gallery.logic.onThisDay
import eu.eurise.gallery.logic.railMarks
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Filter(val label: Int) { All(R.string.filter_all), Favorites(R.string.filter_favorites), Videos(R.string.filter_videos), Tidy(R.string.filter_tidy) }

private val READ_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun Context.canRead() = READ_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/** Azioni su foto e video, che passano dalle richieste di conferma di Android. */
internal class MediaActions(
    val share: (List<MediaItem>) -> Unit,
    val trash: (List<MediaItem>) -> Unit,
    val setFavorite: (MediaItem, Boolean) -> Unit,
    val play: (MediaItem) -> Unit,
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
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    val actions = remember(context) {
        val resolver = context.contentResolver
        MediaActions(
            share = { list -> context.shareMedia(list) },
            trash = { list ->
                val request = MediaStore.createTrashRequest(resolver, list.map { Uri.parse(it.uri) }, true)
                system.launch(IntentSenderRequest.Builder(request.intentSender).build())
            },
            setFavorite = { item, on ->
                val request = MediaStore.createFavoriteRequest(resolver, listOf(Uri.parse(item.uri)), on)
                system.launch(IntentSenderRequest.Builder(request.intentSender).build())
            },
            play = { item ->
                val open = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(open) } catch (_: ActivityNotFoundException) { }
            },
        )
    }
    GalleryApp(media, actions)
}

private fun Context.shareMedia(list: List<MediaItem>) {
    if (list.isEmpty()) return
    val uris = ArrayList(list.map { Uri.parse(it.uri) })
    val type = if (list.all { it.isVideo }) "video/*" else if (list.none { it.isVideo }) "image/*" else "*/*"
    val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    startActivity(Intent.createChooser(send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
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
private fun GalleryApp(media: List<MediaItem>?, actions: MediaActions) {
    val haze = remember { HazeState() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerId by remember { mutableStateOf<Long?>(null) }
    var memory by remember { mutableStateOf<Memory?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val all = media.orEmpty()
    val visible = remember(all, filter, album) {
        all.filter { item ->
            (album == null || item.bucket == album) && when (filter) {
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
    val context = LocalContext.current

    BackHandler(enabled = selected.isNotEmpty() || album != null) {
        if (selected.isNotEmpty()) selected = emptySet() else album = null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            if (section == 0) {
                PhotosPage(
                    groups = groups,
                    visible = visible,
                    filter = filter,
                    album = album,
                    loaded = media != null,
                    memory = memories.takeIf { filter == Filter.All && album == null }?.firstOrNull(),
                    selected = selected,
                    actions = actions,
                    onFilter = { filter = it },
                    onClearAlbum = { album = null },
                    onSelected = { selected = it },
                    onOpen = { index -> viewerId = visible[index].id },
                    onMemory = { memory = it },
                    onCamera = { runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } },
                )
            } else {
                AlbumsPage(all) { name -> album = name; filter = Filter.All; section = 0 }
            }
        }
        if (selected.isEmpty()) {
            EuriseDock(
                items = listOf(
                    DockItem(stringResource(R.string.tab_photos)) { EuriseIcons.image(it) },
                    DockItem(stringResource(R.string.tab_albums)) { EuriseIcons.grid(it) },
                ),
                selected = section,
                onSelect = { section = it },
                hazeState = haze,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        memory?.let { m -> MemoryScreen(m, actions, onClose = { memory = null }) }
        // Il visore legge l'elenco vivo: una foto eliminata sparisce e il cuore si aggiorna subito.
        viewerId?.let { id ->
            if (visible.isEmpty()) viewerId = null
            else Viewer(visible, visible.indexOfFirst { it.id == id }.coerceAtLeast(0), actions, onClose = { viewerId = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosPage(
    groups: List<DayGroup>,
    visible: List<MediaItem>,
    filter: Filter,
    album: String?,
    loaded: Boolean,
    memory: Memory?,
    selected: Set<Long>,
    actions: MediaActions,
    onFilter: (Filter) -> Unit,
    onClearAlbum: () -> Unit,
    onSelected: (Set<Long>) -> Unit,
    onOpen: (Int) -> Unit,
    onMemory: (Memory) -> Unit,
    onCamera: () -> Unit,
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
                        Text(dayLabel(group.date), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSelected(if (selected.containsAll(ids)) selected - ids else selected + ids) }) {
                            Text(stringResource(R.string.select), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(group.items, key = { it.uri }) { item ->
                    Tile(item, item.id in selected, onClick = { tap(item) }, onLongClick = {
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
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 12.dp)
                .onSizeChanged { headerPx = it.height },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected.isNotEmpty()) {
                val chosen = visible.filter { it.id in selected }
                Row(Modifier.padding(start = 4.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelected(emptySet()) }) { Icon(EuriseIcons.close(), stringResource(R.string.close), modifier = Modifier.size(20.dp)) }
                    Text(pluralStringResource(R.plurals.selected_count, selected.size, selected.size), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { actions.share(chosen) }) { Icon(EuriseIcons.share(), stringResource(R.string.share), modifier = Modifier.size(21.dp)) }
                    IconButton(onClick = { actions.trash(chosen); onSelected(emptySet()) }) {
                        Icon(EuriseIcons.trash(), stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
                    }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(album ?: stringResource(R.string.tab_photos), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCamera) { Icon(EuriseIcons.camera(), stringResource(R.string.open_camera), modifier = Modifier.size(23.dp)) }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (album != null) item(key = "album") { FilterPill(album, null, true, closable = true, onClick = onClearAlbum) }
                items(Filter.entries, key = { it.name }) { f -> FilterPill(stringResource(f.label), null, filter == f) { onFilter(f) } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(item: MediaItem, chosen: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
private fun AlbumsPage(all: List<MediaItem>, onOpen: (String) -> Unit) {
    val list = remember(all) { albums(all) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(R.string.tab_albums), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = GlassDefaults.PillHeight + 56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list, key = { it.first }) { (name, photos) ->
                Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(name) }) {
                    Thumb(photos.first(), Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                    Text(name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                    Text(pluralStringResource(R.plurals.items_count, photos.size, photos.size), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                }
            }
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
