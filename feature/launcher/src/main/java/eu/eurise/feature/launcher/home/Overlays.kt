package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import eu.eurise.feature.launcher.contacts.Person
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import eu.eurise.feature.launcher.apps.launch
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
                                onInfo = if (app.component != null || app.shortcut != null) ({ onInfo(app) }) else null,
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

/** Le azioni della fila sopra la tastiera: ognuna apre un foglio con ciò che si è scritto già dentro. */
enum class QuickAction { Note, Task, Event, Documents, Web }

/**
 * Ricerca universale con una mano. La barra sta in basso, vicino al pollice, e tutto si legge dal basso in su:
 * subito sopra la barra i contatti (prima i più sentiti), poi le app, poi quello che c'è in Calendario, Note,
 * Attività e conversazioni. Contatti e app scorrono di lato, ognuno per conto suo: due si leggono per intero e il
 * terzo si intravede. Il primo risultato sta a destra (a sinistra con l'opzione per mancini).
 * Sopra la tastiera la fila Nota, Da fare, Evento, Documenti e Web porta ciò che si è scritto in un foglio rapido.
 * Invio apre il primo contatto, o la prima app se non ci sono contatti.
 */
@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    recents: List<AppEntry>,
    notes: List<Note>,
    tasks: List<Task>,
    dark: Boolean,
    hazeState: HazeState,
    leftHanded: Boolean,
    onOpenApp: (AppEntry) -> Unit,
    onOpenNote: (Note) -> Unit,
    onOpenTask: (Task) -> Unit,
    onClose: () -> Unit,
    conversations: List<MessageItem> = emptyList(),
    rankContacts: suspend (String) -> List<Person> = { emptyList() },
    onOpenContact: (Person) -> Unit = {},
    onOpenMessage: (MessageItem) -> Unit = {},
    searchEvents: suspend (String) -> List<eu.eurise.core.data.calendar.CalendarEvent> = { emptyList() },
    onOpenEvent: (eu.eurise.core.data.calendar.CalendarEvent) -> Unit = {},
    onQuick: (QuickAction, String) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val recentKeys = remember(recents) { recents.map { it.key } }
    val foundApps = if (q.isEmpty()) recents.take(12) else apps.filter { it.label.contains(q, ignoreCase = true) }
        .sortedWith(
            compareByDescending<AppEntry> { a -> a.label.split(' ').any { it.startsWith(q, ignoreCase = true) } }
                .thenBy { a -> recentKeys.indexOf(a.key).let { if (it < 0) Int.MAX_VALUE else it } }
                .thenBy { it.label.lowercase() },
        ).take(20)
    val foundNotes = if (q.isEmpty()) emptyList() else notes.filter { it.title.contains(q, true) || it.body.contains(q, true) }.take(5)
    val foundTasks = if (q.isEmpty()) emptyList() else tasks.filter { it.title.contains(q, true) }.take(5)
    val foundChats = if (q.isEmpty()) emptyList() else conversations
        .filter { it.title.contains(q, true) || it.text.contains(q, true) || it.appLabel.contains(q, true) }
        .distinctBy { it.packageName + it.title }.take(5)
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var events by remember { mutableStateOf<List<eu.eurise.core.data.calendar.CalendarEvent>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.isNotEmpty()) delay(120)
        people = runCatching { rankContacts(q) }.getOrDefault(emptyList())
        events = if (q.isEmpty()) emptyList() else runCatching { searchEvents(q) }.getOrDefault(emptyList()).take(5)
    }
    fun openFirst() {
        people.firstOrNull()?.let(onOpenContact) ?: foundApps.firstOrNull()?.let(onOpenApp)
    }

    Column(
        Modifier
            .fillMaxSize()
            .glass(hazeState, RectangleShape, elevation = 0.dp, outline = false)
            .background(scrimColor())
            .closeOnSwipe(up = false, onClose = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .windowInsetsPadding(WindowInsets.ime),
    ) {
        // Dal basso in su: Calendario, Note, Attività, conversazioni.
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = GlassDefaults.SideMargin),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
        ) {
            if (events.isNotEmpty()) item(key = "events") {
                ResultGroup(stringResource(R.string.search_events), hazeState) {
                    events.forEach { e ->
                        ResultRow(onClick = { onOpenEvent(e) }) {
                            Box(Modifier.size(width = 5.dp, height = 30.dp).clip(RoundedCornerShape(3.dp)).background(androidx.compose.ui.graphics.Color(e.color)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.start.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · H:mm", java.util.Locale.getDefault())), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (foundNotes.isNotEmpty()) item(key = "notes") {
                ResultGroup(stringResource(R.string.search_notes), hazeState) {
                    foundNotes.forEach { note ->
                        ResultRow(onClick = { onOpenNote(note) }) {
                            Icon(EuriseIcons.note(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
            if (foundTasks.isNotEmpty()) item(key = "tasks") {
                ResultGroup(stringResource(R.string.search_tasks), hazeState) {
                    foundTasks.forEach { task ->
                        ResultRow(onClick = { onOpenTask(task) }) {
                            Icon(EuriseIcons.task(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (foundChats.isNotEmpty()) item(key = "chats") {
                ResultGroup(stringResource(R.string.search_chats), hazeState) {
                    foundChats.forEach { m ->
                        ResultRow(onClick = { onOpenMessage(m) }) {
                            Icon(EuriseIcons.chat(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(m.title + " · " + m.appLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (foundApps.isNotEmpty()) {
            ResultStrip(stringResource(if (q.isEmpty()) R.string.search_recent else R.string.search_apps), foundApps, { it.key }, leftHanded, hazeState) { app ->
                StripCard(onClick = { onOpenApp(app) }, hazeState = hazeState) {
                    AppIcon(app, 36.dp, dark)
                    Spacer(Modifier.width(8.dp))
                    Text(app.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                }
            }
        }
        if (people.isNotEmpty()) {
            ResultStrip(stringResource(R.string.search_people), people, { "${it.contactId}${it.name}" }, leftHanded, hazeState) { person ->
                StripCard(onClick = { onOpenContact(person) }, hazeState = hazeState, strong = true) {
                    Avatar(person, 36)
                    Spacer(Modifier.width(8.dp))
                    Text(person.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .padding(horizontal = GlassDefaults.SideMargin)
                .fillMaxWidth()
                .height(GlassDefaults.PillHeight + 4.dp)
                .glass(hazeState, RoundedCornerShape(50), outline = false)
                .padding(start = 18.dp, end = 6.dp),
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
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openFirst() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.close), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(EuriseIcons.close(), stringResource(R.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        QuickRow(q, hazeState, onQuick)
        Spacer(Modifier.height(6.dp))
    }
}

/** La fila sopra la tastiera: Nota, Da fare, Evento, Documenti, Web. Documenti e Web servono solo con qualcosa scritto. */
@Composable
private fun QuickRow(q: String, hazeState: HazeState, onQuick: (QuickAction, String) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = GlassDefaults.SideMargin)
            .fillMaxWidth()
            .glass(hazeState, RoundedCornerShape(22.dp), outline = false)
            .padding(4.dp),
    ) {
        listOf(
            Triple(QuickAction.Note, R.string.quick_note, EuriseIcons.note(2f)),
            Triple(QuickAction.Task, R.string.quick_task, EuriseIcons.task(2f)),
            Triple(QuickAction.Event, R.string.quick_event, EuriseIcons.calendar(2f)),
            Triple(QuickAction.Documents, R.string.quick_documents, EuriseIcons.folder(2f)),
            Triple(QuickAction.Web, R.string.quick_web, EuriseIcons.globe(2f)),
        ).forEach { (action, label, icon) ->
            val enabled = q.isNotEmpty() || action == QuickAction.Note || action == QuickAction.Task || action == QuickAction.Event
            val text = stringResource(label)
            Column(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .alpha(if (enabled) 1f else 0.4f)
                    .clickable(enabled = enabled, onClickLabel = text) { onQuick(action, q) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

/**
 * Una fila di risultati che scorre di lato per conto suo: due voci si leggono per intero e la terza si intravede.
 * Per i destrimani la prima voce sta a destra e le altre si scoprono scorrendo da sinistra verso destra.
 */
@Composable
private fun <T> ResultStrip(
    title: String,
    entries: List<T>,
    key: (T) -> Any,
    leftHanded: Boolean,
    hazeState: HazeState,
    content: @Composable (T) -> Unit,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(entries.firstOrNull()?.let(key)) { state.scrollToItem(0) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = GlassDefaults.SideMargin + 4.dp), contentAlignment = if (leftHanded) Alignment.CenterStart else Alignment.CenterEnd) {
            Label(title)
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = GlassDefaults.SideMargin
            val gap = 8.dp
            val cardWidth = (maxWidth - side * 2 - gap * 2) / 2.3f
            androidx.compose.foundation.lazy.LazyRow(
                state = state,
                reverseLayout = !leftHanded,
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(horizontal = side),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(entries, key = key) { item ->
                    Box(Modifier.width(cardWidth)) { content(item) }
                }
            }
        }
        StripScrollbar(state, leftHanded)
    }
}

/** Barra di scorrimento sottile sotto una fila: quanto se ne vede e dove si è. */
@Composable
private fun StripScrollbar(state: androidx.compose.foundation.lazy.LazyListState, leftHanded: Boolean) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total <= 0 || visible >= total) {
        Spacer(Modifier.height(9.dp))
        return
    }
    val fraction = visible.toFloat() / total
    val position = state.firstVisibleItemIndex.toFloat() / total
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = GlassDefaults.SideMargin + 4.dp, vertical = 3.dp).height(3.dp)
            .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        val offset = maxWidth * position
        Box(
            Modifier
                .align(if (leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (leftHanded) offset else -offset)
                .width(maxWidth * fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun StripCard(onClick: () -> Unit, hazeState: HazeState, strong: Boolean = false, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(hazeState, RoundedCornerShape(18.dp), elevation = if (strong) 4.dp else 0.dp, outline = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ResultGroup(title: String, hazeState: HazeState, rows: @Composable () -> Unit) {
    Column {
        Box(Modifier.padding(start = 4.dp, bottom = 6.dp)) { Label(title) }
        Column(
            Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(20.dp), elevation = 4.dp, outline = false).padding(vertical = 4.dp),
        ) { rows() }
    }
}

@Composable
private fun ResultRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Apre la ricerca sul web con il browser predefinito e il suo motore, con ciò che si è scritto. */
fun android.content.Context.searchWeb(q: String) {
    val search = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
        .putExtra(android.app.SearchManager.QUERY, q).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(search) }.isFailure) {
        val page = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com/?q=" + android.net.Uri.encode(q)))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(page) }
    }
}
