package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import eu.eurise.feature.launcher.contacts.Person
import eu.eurise.feature.launcher.files.RecentFile
import eu.eurise.feature.launcher.notifications.MessageItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.ScrubRail
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory
import eu.eurise.feature.launcher.apps.alphabetIndex
import eu.eurise.feature.launcher.apps.inUserOrder

/** Un'app presa con la pressione lunga: [target] è la categoria su cui si trova ora, [order] le sue app. */
private class AppDrag(val entry: AppEntry, val source: String, start: Offset, order: List<String>?, val locked: Boolean) {
    var pointer by mutableStateOf(start)
    var travelled by mutableFloatStateOf(0f)
    var moved by mutableStateOf(false)
    var target by mutableStateOf(source)
    var order by mutableStateOf(order)
}

/** Quanto tenere l'app sul bordo dello schermo prima di passare alla categoria accanto. */
private const val EDGE_DWELL_MS = 550L

/**
 * Le pagine delle categorie: si scorre dall'una all'altra; la barra in basso segue la pagina.
 * Con la pressione lunga un'app si prende: rilasciata subito apre il menu, trascinata cambia posto tra le altre,
 * tenuta sul bordo dello schermo passa alla categoria accanto.
 */
@Composable
fun CategoryPages(
    pager: PagerState,
    categories: List<BarCategory>,
    categoryOf: (AppEntry) -> BarCategory?,
    apps: List<AppEntry>,
    folders: List<eu.eurise.feature.launcher.folders.AppFolder> = emptyList(),
    onOpenFolder: (eu.eurise.feature.launcher.folders.AppFolder) -> Unit = {},
    onCreateFolder: (AppEntry, BarCategory) -> Unit = { _, _ -> },
    dark: Boolean,
    badges: Map<String, Int>,
    isFavorite: (AppEntry) -> Boolean,
    onOpen: (AppEntry) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onMoveTo: (AppEntry, BarCategory) -> Unit,
    orderOf: (BarCategory) -> List<String>?,
    onReorder: (BarCategory, List<String>) -> Unit,
    bottomClearance: androidx.compose.ui.unit.Dp,
    columns: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    showLabels: Boolean,
    onClose: () -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    favorites: List<AppEntry> = emptyList(),
) {
    val byKey = apps.associateBy { it.key }
    // Le cartelle di una categoria vengono prima; le app che contengono non si ripetono nella griglia.
    // I Preferiti mostrano le app e le cartelle della home, nell'ordine scelto; "Tutte" ogni app in ordine alfabetico.
    val foldersOf: (BarCategory) -> List<eu.eurise.feature.launcher.folders.AppFolder> = { category ->
        when {
            category.isFavorites -> folders.filter { f -> favorites.any { it.key == f.key } }
            category.isAll -> emptyList()
            else -> folders.filter { it.place == category.id }
        }
    }
    val appsOf: (BarCategory) -> List<AppEntry> = { category ->
        val here = foldersOf(category)
        val inFolders = here.flatMap { it.keys }.toSet() + here.map { it.key }
        when {
            category.isFavorites -> favorites.filter { it.key !in inFolders }
            category.isAll -> apps
            else -> apps.filter { categoryOf(it)?.id == category.id && it.key !in inFolders }.inUserOrder(orderOf(category))
        }
    }
    var drag by remember { mutableStateOf<AppDrag?>(null) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val grids = remember { mutableStateMapOf<Int, LazyGridState>() }
    val gridOrigins = remember { mutableStateMapOf<Int, Offset>() }
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 56.dp.toPx() }
    val currentCategories by rememberUpdatedState(categories)
    val currentAppsOf by rememberUpdatedState(appsOf)
    val currentByKey by rememberUpdatedState(byKey)
    val moveTo by rememberUpdatedState(onMoveTo)
    val reorder by rememberUpdatedState(onReorder)

    fun tileAt(page: Int, pointer: Offset): LazyGridItemInfo? {
        val state = grids[page] ?: return null
        val origin = gridOrigins[page] ?: return null
        val p = pointer + rootOrigin - origin
        return state.layoutInfo.visibleItemsInfo.firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= it.offset.y && p.y < it.offset.y + it.size.height
        }
    }

    /** L'app trascinata arriva sulla pagina [page]: in una categoria vera prende posto in fondo. */
    fun arrive(d: AppDrag, page: Int) {
        val category = currentCategories.getOrNull(page) ?: return
        d.target = category.id
        d.order = if (category.holdsApps) currentAppsOf(category).map { it.key }.filter { it != d.entry.key } + d.entry.key else null
    }

    // Sul bordo dello schermo, dopo un attimo, si passa alla categoria accanto.
    LaunchedEffect(drag) {
        val d = drag ?: return@LaunchedEffect
        val edge = edgePx
        var side = 0
        var since = 0L
        while (true) {
            delay(60)
            val now = if (!d.moved) 0 else if (d.pointer.x < edge) -1 else if (d.pointer.x > boxWidth - edge) 1 else 0
            if (now != side) { side = now; since = 0L } else if (side != 0) since += 60
            val next = pager.currentPage + side
            if (side != 0 && since >= EDGE_DWELL_MS && next in currentCategories.indices && !pager.isScrollInProgress) {
                pager.animateScrollToPage(next)
                arrive(d, next)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                since = 0L
            }
        }
    }

    Box(
        modifier
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .onSizeChanged { boxWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        val page = pager.currentPage
                        val category = currentCategories.getOrNull(page)
                        val entry = (tileAt(page, at)?.key as? String)?.let { currentByKey[it] }
                        if (category != null && entry != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val order = if (category.holdsApps) currentAppsOf(category).map { it.key } else null
                            drag = AppDrag(entry, category.id, at, order, locked = category.isFavorites)
                        }
                    },
                    onDrag = { change, amount ->
                        val d = drag
                        if (d != null) {
                            change.consume()
                            d.pointer += amount
                            d.travelled += amount.getDistance()
                            if (d.travelled > slop && !d.locked) d.moved = true
                            val order = d.order
                            if (d.moved && order != null) {
                                val over = tileAt(pager.currentPage, d.pointer)?.key as? String
                                if (over != null && over != d.entry.key && over in order) {
                                    d.order = order.toMutableList().apply {
                                        remove(d.entry.key)
                                        add(order.indexOf(over), d.entry.key)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        val d = drag
                        drag = null
                        if (d != null) {
                            if (!d.moved) {
                                menuKey = d.entry.key
                            } else {
                                val target = currentCategories.firstOrNull { it.id == d.target }
                                val order = d.order
                                if (target != null && target.holdsApps && order != null) {
                                    if (target.id != d.source) moveTo(d.entry, target)
                                    reorder(target, order)
                                }
                            }
                        }
                    },
                    onDragCancel = { drag = null },
                )
            },
    ) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().then(if (drag == null) Modifier.closeOnSwipe(up = false, onClose = onClose) else Modifier),
            userScrollEnabled = drag == null,
        ) { page ->
            val category = categories[page]
            val here = foldersOf(category)
            val active = drag
            val base = appsOf(category)
            val activeOrder = active?.order
            val list = when {
                active == null -> base
                active.target == category.id && activeOrder != null -> activeOrder.mapNotNull { byKey[it] }
                active.source == category.id && active.target != category.id && category.holdsApps -> base.filter { it.key != active.entry.key }
                else -> base
            }
            val gridState = rememberLazyGridState()
            DisposableEffect(page, gridState) {
                grids[page] = gridState
                onDispose { grids.remove(page); gridOrigins.remove(page) }
            }
            // Le app stanno in basso, vicino al pollice, con il titolo della categoria subito sopra.
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                    )
                    // Griglia e icone: colonne e dimensione valgono per home, categorie e ricerca.
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.appearance_title), onClick = onAppearance),
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.grid(), stringResource(R.string.appearance_title), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                if (list.isEmpty() && here.isEmpty()) {
                    Text(
                        stringResource(R.string.category_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = bottomClearance),
                    )
                }
                val letters = remember(list) { if (category.isAll) alphabetIndex(list.map { it.label }) else emptyList() }
                Box(Modifier.weight(1f, fill = false)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    userScrollEnabled = drag == null,
                    contentPadding = PaddingValues(end = if (letters.size > 1) 20.dp else 0.dp, bottom = bottomClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.onGloballyPositioned { gridOrigins[page] = it.positionInRoot() },
                ) {
                    items(here, key = { it.key }) { folder ->
                        Box(contentAlignment = Alignment.Center) {
                            eu.eurise.feature.launcher.folders.FolderTile(
                                folder, folder.keys.mapNotNull { byKey[it] }, dark, iconSize, showLabels, onOpen = { onOpenFolder(folder) },
                            )
                        }
                    }
                    items(list, key = { it.key }) { app ->
                        val lifted = active?.moved == true && active.entry.key == app.key
                        Box(Modifier.animateItem().alpha(if (lifted) 0f else 1f), contentAlignment = Alignment.Center) {
                            AppTile(
                                entry = app,
                                dark = dark,
                                badge = badges[app.packageName] ?: 0,
                                favorite = isFavorite(app),
                                // Durante una presa il rilascio non deve aprire l'app.
                                onOpen = { if (drag == null) onOpen(app) },
                                onToggleFavorite = { onToggleFavorite(app) },
                                onInfo = if (app.component != null) ({ onInfo(app) }) else null,
                                moveTargets = categories.filter { it.id != category.id && it.holdsApps },
                                onMoveTo = { onMoveTo(app, it) },
                                iconSize = iconSize,
                                showLabel = showLabels,
                                onCreateFolder = if (category.holdsApps) ({ onCreateFolder(app, category) }) else null,
                                externalLongPress = true,
                                menuRequest = menuKey == app.key,
                                onMenuClosed = { menuKey = null },
                            )
                        }
                    }
                }
                if (letters.size > 1 && drag == null) {
                    val scope = rememberCoroutineScope()
                    val letterAt = { f: Float -> letters[(f * letters.size).toInt().coerceIn(0, letters.lastIndex)] }
                    Box(Modifier.matchParentSize().padding(bottom = bottomClearance), contentAlignment = Alignment.TopEnd) {
                        ScrubRail(
                            labels = letters.map { it.first.toString() },
                            onScrub = { f -> scope.launch { gridState.scrollToItem(letterAt(f).second) } },
                            bubble = { f -> letterAt(f).first.toString() },
                            description = stringResource(R.string.all_apps_letters),
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
                }
            }
            }
        }
        // L'icona che segue il dito.
        drag?.takeIf { it.moved }?.let { d ->
            val half = with(LocalDensity.current) { (iconSize * 0.55f).toPx() }
            AppIcon(
                d.entry, iconSize * 1.1f, dark,
                modifier = Modifier.graphicsLayer {
                    translationX = d.pointer.x - half
                    translationY = d.pointer.y - half
                    alpha = 0.92f
                },
            )
        }
    }
}

/** Azioni della ricerca: oltre a trovare, si può scrivere e salvare subito. */
class CaptureActions(
    val onTask: (String) -> Unit,
    val onNote: (String) -> Unit,
    val onEvent: (String) -> Unit,
)

/**
 * Ricerca e cattura: un campo solo per trovare app, note e cose da fare, o per salvare ciò che si è
 * scritto come attività, nota o appuntamento.
 */
@Composable
fun SearchOverlay(
    columns: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    isFavorite: (AppEntry) -> Boolean,
    onToggleFavorite: (AppEntry) -> Unit,
    apps: List<AppEntry>,
    recents: List<AppEntry>,
    notes: List<Note>,
    tasks: List<Task>,
    dark: Boolean,
    hazeState: HazeState,
    capture: CaptureActions,
    onOpenApp: (AppEntry) -> Unit,
    onOpenNote: (Note) -> Unit,
    onOpenTask: (Task) -> Unit,
    onClose: () -> Unit,
    conversations: List<MessageItem> = emptyList(),
    searchContacts: suspend (String) -> List<Person> = { emptyList() },
    searchFiles: suspend (String) -> List<RecentFile> = { emptyList() },
    onOpenContact: (Person) -> Unit = {},
    onOpenMessage: (MessageItem) -> Unit = {},
    onOpenFile: (RecentFile) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val foundApps = if (q.isEmpty()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }.take(8)
    val foundNotes = if (q.isEmpty()) emptyList() else notes.filter { it.title.contains(q, true) || it.body.contains(q, true) }.take(5)
    val foundTasks = if (q.isEmpty()) emptyList() else tasks.filter { it.title.contains(q, true) }.take(5)
    val foundChats = if (q.isEmpty()) emptyList() else conversations
        .filter { it.title.contains(q, true) || it.text.contains(q, true) || it.appLabel.contains(q, true) }
        .distinctBy { it.packageName + it.title }.take(5)
    var foundPeople by remember { mutableStateOf<List<Person>>(emptyList()) }
    var foundFiles by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.isEmpty()) { foundPeople = emptyList(); foundFiles = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(150)
        foundPeople = runCatching { searchContacts(q) }.getOrDefault(emptyList())
        foundFiles = runCatching { searchFiles(q) }.getOrDefault(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .glass(hazeState, RectangleShape, elevation = 0.dp, outline = false)
            .background(scrimColor())
            .closeOnSwipe(up = false, onClose = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .windowInsetsPadding(WindowInsets.ime)
            .padding(horizontal = GlassDefaults.SideMargin),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(GlassDefaults.PillHeight + 4.dp)
                .glass(hazeState, RoundedCornerShape(50), outline = false)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.search(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { foundApps.firstOrNull()?.let(onOpenApp) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.close), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(EuriseIcons.plus(), stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp).rotate(45f))
            }
        }
        Spacer(Modifier.height(14.dp))
        if (q.isEmpty()) {
            if (recents.isNotEmpty()) {
                Label(stringResource(R.string.search_recent), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(recents.take(columns * 2), key = { it.key }) { app ->
                        Box(contentAlignment = Alignment.Center) {
                            AppTile(
                                entry = app, dark = dark, badge = 0, favorite = isFavorite(app),
                                onOpen = { onOpenApp(app) }, onToggleFavorite = { onToggleFavorite(app) }, onInfo = null,
                                iconSize = iconSize,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        } else LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .glass(hazeState, RoundedCornerShape(28.dp), elevation = 8.dp, outline = false),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            // Ordine: contatti, poi aggiungi/salva/nuovo, app, conversazioni, note e attività, documenti.
            if (foundPeople.isNotEmpty()) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    ) {
                        items(foundPeople.size) { i ->
                            val person = foundPeople[i]
                            Column(
                                Modifier.width(64.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenContact(person) }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                eu.eurise.feature.launcher.home.Avatar(person, 48)
                                Spacer(Modifier.height(4.dp))
                                Text(person.name, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (q.isNotEmpty()) {
                item { CaptureRow(EuriseIcons.task(), stringResource(R.string.capture_task), q) { capture.onTask(q); onClose() } }
                item { CaptureRow(EuriseIcons.note(), stringResource(R.string.capture_note), q) { capture.onNote(q); onClose() } }
                item { CaptureRow(EuriseIcons.calendar(), stringResource(R.string.capture_event), q) { capture.onEvent(q); onClose() } }
            }
            if (foundApps.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_apps)) }
                foundApps.forEach { app ->
                    item(key = app.key) {
                        ResultRow(onClick = { onOpenApp(app) }) {
                            AppIcon(app, 36.dp, dark)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
            if (foundChats.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_chats)) }
                foundChats.forEach { m ->
                    item(key = "m${m.key}") {
                        ResultRow(onClick = { onOpenMessage(m) }) {
                            Icon(EuriseIcons.chat(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(m.title + " · " + m.appLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (foundNotes.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_notes)) }
                foundNotes.forEach { note ->
                    item(key = "n${note.id}") {
                        ResultRow(onClick = { onOpenNote(note) }) {
                            Icon(EuriseIcons.note(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (note.body.isNotBlank() && note.title.isNotBlank()) {
                                    Text(note.body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            if (foundTasks.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_tasks)) }
                foundTasks.forEach { task ->
                    item(key = "t${task.id}") {
                        ResultRow(onClick = { onOpenTask(task) }) {
                            Icon(EuriseIcons.task(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (foundFiles.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.search_files)) }
                foundFiles.forEach { file ->
                    item(key = "f${file.uri}") {
                        ResultRow(onClick = { onOpenFile(file) }) {
                            Icon(EuriseIcons.folder(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(file.folder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) { Label(text) }
}

@Composable
private fun ResultRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun CaptureRow(icon: ImageVector, action: String, text: String, onClick: () -> Unit) {
    ResultRow(onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(action, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text("«$text»", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
