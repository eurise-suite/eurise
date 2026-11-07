package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import eu.eurise.feature.launcher.contacts.Person
import eu.eurise.feature.launcher.files.RecentFile
import eu.eurise.feature.launcher.notifications.MessageItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon
import eu.eurise.feature.launcher.apps.BarCategory

/** Le pagine delle categorie: si scorre dall'una all'altra; la barra in basso segue la pagina. */
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
    bottomClearance: androidx.compose.ui.unit.Dp,
    columns: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    showLabels: Boolean,
    onClose: () -> Unit,
    onAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(pager, modifier.closeOnSwipe(up = false, onClose = onClose)) { page ->
        val category = categories[page]
        // Le cartelle di questa categoria vengono prima; le app che contengono non si ripetono nella griglia.
        val here = folders.filter { it.place == category.id }
        val inFolders = here.flatMap { it.keys }.toSet()
        val list = apps.filter { categoryOf(it)?.id == category.id && it.key !in inFolders }
        val byKey = apps.associateBy { it.key }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
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
            Spacer(Modifier.height(16.dp))
            if (list.isEmpty()) {
                Text(stringResource(R.string.category_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), modifier = Modifier.padding(8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(here, key = { it.key }) { folder ->
                    Box(contentAlignment = Alignment.Center) {
                        eu.eurise.feature.launcher.folders.FolderTile(
                            folder, folder.keys.mapNotNull { byKey[it] }, dark, iconSize, showLabels, onOpen = { onOpenFolder(folder) },
                        )
                    }
                }
                items(list, key = { it.key }) { app ->
                    Box(contentAlignment = Alignment.Center) {
                        AppTile(
                            entry = app,
                            dark = dark,
                            badge = badges[app.packageName] ?: 0,
                            favorite = isFavorite(app),
                            onOpen = { onOpen(app) },
                            onToggleFavorite = { onToggleFavorite(app) },
                            onInfo = if (app.component != null) ({ onInfo(app) }) else null,
                            moveTargets = categories.filter { it.id != category.id },
                            onMoveTo = { onMoveTo(app, it) },
                            iconSize = iconSize,
                            showLabel = showLabels,
                            onCreateFolder = { onCreateFolder(app, category) },
                        )
                    }
                }
            }
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
