package eu.eurise.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.TaskRepository
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.format.longDate
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.GroupCard

/**
 * Le Attività: elenco con viste ed elenchi, scheda di un'attività, elenchi e aggiunta rapida.
 *
 * @param newTaskRequests contatore che il guscio incrementa quando si tocca il + della barra:
 * ogni aumento apre l'aggiunta rapida.
 * @param onOpenNote apre la nota da cui è nata un'attività.
 */
@Composable
fun TasksRoute(
    repository: TaskRepository,
    newTaskRequests: Int = 0,
    onOpenNote: (Long) -> Unit = {},
    openTaskId: Long? = null,
    onTaskOpened: () -> Unit = {},
    linked: @Composable (taskId: Long, title: String) -> Unit = { _, _ -> },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = viewModel { TasksViewModel(repository, eu.eurise.core.data.EuriseData.create(context).trash) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showLists by rememberSaveable { mutableStateOf(false) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    // Il valore presente all'apertura è già "servito": si reagisce solo agli aumenti successivi.
    var handledRequests by remember { mutableIntStateOf(newTaskRequests) }
    LaunchedEffect(newTaskRequests) {
        if (newTaskRequests > handledRequests) {
            handledRequests = newTaskRequests
            quickAdd = true
        }
    }

    // Un'attività aperta da un collegamento in un'altra app: si apre la sua scheda.
    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            detailId = openTaskId
            onTaskOpened()
        }
    }

    val openId = detailId
    when {
        openId != null -> {
            BackHandler { detailId = null }
            TaskDetailScreen(
                taskId = openId,
                repository = repository,
                state = state,
                viewModel = viewModel,
                onClose = { detailId = null },
                onOpenNote = onOpenNote,
                linked = linked,
            )
        }
        showLists -> {
            BackHandler { showLists = false }
            ListsScreen(
                state = state,
                viewModel = viewModel,
                onBack = { showLists = false },
                onOpenList = { viewModel.setFilter(TasksFilter.OfList(it)); showLists = false },
            )
        }
        else -> TasksScreen(
            state = state,
            today = LocalDate.now(),
            onFilter = viewModel::setFilter,
            onQuery = viewModel::setQuery,
            onSetDone = viewModel::setDone,
            onOpen = { detailId = it.id },
            onLists = { showLists = true },
            onClearCompleted = viewModel::clearCompleted,
        )
    }

    if (quickAdd) {
        QuickAddSheet(
            lists = state.lists.map { it.list },
            now = viewModel.now(),
            onAdd = { parsed, details ->
                viewModel.add(parsed, viewModel.currentList()) { id -> if (details) detailId = id }
            },
            onDismiss = { quickAdd = false },
        )
    }
}

/** Una sezione dell'elenco: titolo, attività e se le righe devono scrivere anche il giorno. */
private data class Section(
    val key: String,
    val title: String,
    val tasks: List<Task>,
    val showDay: Boolean,
    val alert: Boolean = false,
)

@Composable
private fun TasksScreen(
    state: TasksState,
    today: LocalDate,
    onFilter: (TasksFilter) -> Unit,
    onQuery: (String) -> Unit,
    onSetDone: (Task, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
    onLists: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    val haze = remember { HazeState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val lists = state.lists.associate { it.list.id to it.list }
    val all = state.tasks
    val open = all?.filter { !it.done }.orEmpty()
    val done = all?.filter { it.done }.orEmpty()
    val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dueToday = open.count { it.due != null && !it.due!!.isAfter(today) }

    val overdueTitle = stringResource(R.string.tasks_section_overdue)
    val todayTitle = stringResource(R.string.tasks_section_today, longDate(today).replaceFirstChar { it.lowercase() })
    val tomorrowTitle = stringResource(R.string.tasks_section_tomorrow, today.plusDays(1).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN))
    val laterTitle = stringResource(R.string.tasks_section_later)
    val noDateTitle = stringResource(R.string.tasks_section_no_date)
    val doneTitle = stringResource(R.string.tasks_section_done)
    val doneTodayTitle = stringResource(R.string.tasks_section_done_today)
    val resultsTitle = stringResource(R.string.tasks_section_results)
    val importantTitle = stringResource(R.string.tasks_section_important)

    fun overdue(list: List<Task>) = list.filter { it.due?.isBefore(today) == true }

    // Le sezioni nascoste dietro "Più avanti · Fatte oggi" nella vista Oggi.
    var hidden: List<Section> = emptyList()
    val sections: List<Section> = when {
        all == null -> emptyList()
        state.query.isNotBlank() -> {
            val q = state.query.trim()
            listOf(Section("results", resultsTitle, all.filter { it.title.contains(q, true) || it.notes.contains(q, true) }, showDay = true))
        }
        state.filter == TasksFilter.Today -> {
            hidden = listOf(
                Section("later", laterTitle, open.filter { it.due?.isAfter(today.plusDays(1)) == true }, showDay = true),
                Section("doneToday", doneTodayTitle, done.filter { (it.completedAt ?: 0) >= startOfDay }, showDay = false),
            )
            listOf(
                Section("overdue", overdueTitle, overdue(open), showDay = true, alert = true),
                Section("today", todayTitle, open.filter { it.due == today }, showDay = false),
                Section("tomorrow", tomorrowTitle, open.filter { it.due == today.plusDays(1) }, showDay = false),
            )
        }
        state.filter == TasksFilter.Scheduled -> buildList {
            add(Section("overdue", overdueTitle, overdue(open), showDay = true, alert = true))
            open.filter { it.due != null && !it.due!!.isBefore(today) }.groupBy { it.due!! }.toSortedMap().forEach { (day, tasks) ->
                add(Section("d$day", "", tasks, showDay = false).copy(title = day.toString()))
            }
        }
        state.filter == TasksFilter.Important ->
            listOf(Section("important", importantTitle, open.filter { it.important }, showDay = true))
        else -> {
            val listId = (state.filter as? TasksFilter.OfList)?.id
            val mine = { t: Task -> listId == null || t.listId == listId }
            listOf(
                Section("overdue", overdueTitle, overdue(open).filter(mine), showDay = true, alert = true),
                Section("today", todayTitle, open.filter { it.due == today && mine(it) }, showDay = false),
                Section("later", laterTitle, open.filter { it.due?.isAfter(today) == true && mine(it) }, showDay = true),
                Section("nodate", noDateTitle, open.filter { it.due == null && mine(it) }, showDay = false),
                Section("done", doneTitle, if (state.showDone) done.filter(mine).take(50) else emptyList(), showDay = false),
            )
        }
    }.filter { it.tasks.isNotEmpty() }
    val hiddenVisible = hidden.filter { it.tasks.isNotEmpty() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = statusTop + 132.dp, bottom = LocalDockClearance.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (all != null && sections.isEmpty() && hiddenVisible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = EuriseIcons.task(),
                        title = stringResource(if (state.query.isBlank()) R.string.tasks_empty_title else R.string.tasks_no_results),
                        message = stringResource(R.string.tasks_empty_message),
                    )
                }
            }
            sections.forEach { section ->
                val title = if (section.key.startsWith("d") && state.filter == TasksFilter.Scheduled) {
                    null
                } else section.title
                item(key = "h-${section.key}") {
                    if (title != null) {
                        SectionHeader(
                            title = "$title · ${section.tasks.size}".takeIf { section.key == "overdue" || section.key == "done" } ?: title,
                            alert = section.alert,
                            action = if (section.key == "done") stringResource(R.string.tasks_clear_completed) else null,
                            onAction = { confirmClear = true },
                        )
                    } else {
                        val day = section.tasks.first().due!!
                        SectionHeader(title = dueLabel(day, today).text + if (day.isAfter(today.plusDays(6))) "" else " · " + longDate(day))
                    }
                }
                taskCard(section, lists, state, today, onSetDone, onOpen)
            }
            if (hiddenVisible.isNotEmpty()) {
                item(key = "more") {
                    val label = hiddenVisible.joinToString("  ·  ") { "${it.title} · ${it.tasks.size}" }
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(if (expanded) EuriseIcons.chevronLeft() else EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (expanded) hiddenVisible.forEach { section ->
                    item(key = "h-${section.key}") { SectionHeader(section.title) }
                    taskCard(section, lists, state, today, onSetDone, onOpen)
                }
            }
        }

        // Testata satinata con titolo, ricerca, elenchi e pillole delle viste.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(0.dp), elevation = 0.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(Modifier.padding(start = 16.dp, end = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = { Text(stringResource(R.string.tasks_search)) },
                        leadingIcon = { Icon(EuriseIcons.search(), contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    IconButton(onClick = { onQuery(""); searching = false }) {
                        Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.tasks_search_close), modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (dueToday > 0) stringResource(R.string.tasks_count_today, dueToday) else "",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp).weight(1f),
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.tasks_search), modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onLists) {
                        Icon(EuriseIcons.lists(), contentDescription = stringResource(R.string.tasks_lists), modifier = Modifier.size(22.dp))
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "today") { FilterPill(stringResource(R.string.tasks_view_today), null, state.filter == TasksFilter.Today) { onFilter(TasksFilter.Today) } }
                item(key = "scheduled") { FilterPill(stringResource(R.string.tasks_view_scheduled), null, state.filter == TasksFilter.Scheduled) { onFilter(TasksFilter.Scheduled) } }
                item(key = "important") { FilterPill(stringResource(R.string.tasks_view_important), null, state.filter == TasksFilter.Important) { onFilter(TasksFilter.Important) } }
                item(key = "all") { FilterPill(stringResource(R.string.tasks_view_all), null, state.filter == TasksFilter.All) { onFilter(TasksFilter.All) } }
                items(state.lists, key = { "l${it.list.id}" }) { s ->
                    val selected = state.filter == TasksFilter.OfList(s.list.id)
                    FilterPill(s.list.name, tintColor(s.list.tint), selected) {
                        onFilter(if (selected) TasksFilter.All else TasksFilter.OfList(s.list.id))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.tasks_clear_title)) },
            text = { Text(stringResource(R.string.tasks_clear_message)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearCompleted() }) {
                    Text(stringResource(R.string.tasks_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}

private fun LazyListScope.taskCard(
    section: Section,
    lists: Map<Long, TaskList>,
    state: TasksState,
    today: LocalDate,
    onSetDone: (Task, Boolean) -> Unit,
    onOpen: (Task) -> Unit,
) {
    item(key = "c-${section.key}") {
        GroupCard(Modifier.animateItem()) {
            section.tasks.forEachIndexed { i, task ->
                TaskItem(
                    task = task,
                    list = lists[task.listId],
                    progress = state.steps[task.id],
                    today = today,
                    showDay = section.showDay,
                    divider = i > 0,
                    onToggle = { onSetDone(task, !task.done) },
                    onOpen = { onOpen(task) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, alert: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(
            title,
            color = if (alert) importantColors().first else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(end = 8.dp)) {
                Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Millisecondi dell'epoca come giorno locale. */
internal fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
