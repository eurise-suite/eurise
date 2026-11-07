package eu.eurise.hub.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.data.EuriseData
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.calendar.CalendarRoute
import eu.eurise.feature.calendar.EventEditorRoute
import eu.eurise.feature.notes.NoteEditorRoute
import eu.eurise.feature.notes.NotesListRoute
import eu.eurise.feature.tasks.TasksRoute
import eu.eurise.feature.today.TodayRoute
import eu.eurise.hub.R

/** Le sezioni dell'hub nella barra in basso (al massimo quattro, come da design system). */
private enum class Section(val route: String, @StringRes val label: Int, val icon: (Float) -> ImageVector, @StringRes val action: Int?) {
    Today("today", R.string.section_today, { EuriseIcons.today(it) }, null),
    Calendar("calendar", R.string.section_calendar, { EuriseIcons.calendar(it) }, R.string.action_new_event),
    Notes("notes", R.string.section_notes, { EuriseIcons.note(it) }, R.string.action_new_note),
    Tasks("tasks", R.string.section_tasks, { EuriseIcons.task(it) }, R.string.action_new_task),
}

/** Richiesta di aprire una sezione arrivata da fuori (la Scrivania); [id] distingue due richieste uguali. */
data class HubRequest(val section: String, val id: Int)

private const val NOTE_ROUTE = "note?id={id}"
private const val EVENT_ROUTE = "event?id={id}&begin={begin}&start={start}"
private const val NO_ID = -1L

private fun NavHostController.openNote(id: Long?) = navigate("note?id=${id ?: NO_ID}")

private fun NavHostController.openEvent(id: Long?, begin: Long?, start: Long?) =
    navigate("event?id=${id ?: NO_ID}&begin=${begin ?: NO_ID}&start=${start ?: NO_ID}")

/** Torna indietro solo se la schermata in cima è ancora [route]: evita doppi ritorni con tocchi ripetuti. */
private fun NavHostController.closeIfOn(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}

/**
 * Il guscio dell'app: le sezioni e la barra flottante satinata con il pulsante + accanto.
 * Il contenuto scorre sotto la barra, che lo sfoca.
 */
@Composable
fun EuriseHub(data: EuriseData, request: HubRequest? = null) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current = Section.entries.firstOrNull { s -> destination?.hierarchy?.any { it.route == s.route } == true }
    val hazeState = remember { HazeState() }
    var newTaskRequests by rememberSaveable { mutableIntStateOf(0) }
    var calendarDay by remember { androidx.compose.runtime.mutableStateOf<java.time.LocalDate?>(null) }

    fun goTo(section: Section) = navController.navigate(section.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LaunchedEffect(request) {
        val section = request?.section ?: return@LaunchedEffect
        when {
            section == "today" -> goTo(Section.Today)
            section == "calendar" -> goTo(Section.Calendar)
            section.startsWith("day:") -> {
                calendarDay = runCatching { java.time.LocalDate.parse(section.removePrefix("day:")) }.getOrNull()
                goTo(Section.Calendar)
            }
            section == "notes" -> goTo(Section.Notes)
            section == "tasks" -> goTo(Section.Tasks)
            section == "new_event" -> navController.openEvent(null, null, null)
            section == "new_note" -> navController.openNote(null)
            section == "new_task" -> { goTo(Section.Tasks); newTaskRequests++ }
            section.startsWith("note:") -> section.removePrefix("note:").toLongOrNull()?.let { navController.openNote(it) }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearance = if (current != null) GlassDefaults.PillHeight + GlassDefaults.BottomMargin * 2 + navBottom else 0.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalDockClearance provides clearance) {
            NavHost(
                navController = navController,
                startDestination = Section.Today.route,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            ) {
                composable(Section.Today.route) {
                    TodayRoute(
                        notes = data.notes,
                        tasks = data.tasks,
                        calendar = data.calendar,
                        onOpenNote = { navController.openNote(it) },
                        onOpenEvent = { id, begin -> navController.openEvent(id, begin, null) },
                        onShowCalendar = { goTo(Section.Calendar) },
                        onShowNotes = { goTo(Section.Notes) },
                        onShowTasks = { goTo(Section.Tasks) },
                    )
                }
                composable(Section.Calendar.route) {
                    CalendarRoute(
                        calendar = data.calendar,
                        tasks = data.tasks,
                        onOpenEvent = { id, begin -> navController.openEvent(id, begin, null) },
                        onNewEvent = { start -> navController.openEvent(null, null, start) },
                        openDay = calendarDay,
                    )
                }
                composable(Section.Notes.route) {
                    NotesListRoute(repository = data.notes, onOpenNote = { navController.openNote(it) })
                }
                composable(Section.Tasks.route) {
                    TasksRoute(repository = data.tasks, newTaskRequests = newTaskRequests)
                }
                composable(
                    route = NOTE_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = NO_ID }),
                ) { entry ->
                    NoteEditorRoute(
                        repository = data.notes,
                        noteId = entry.arguments?.getLong("id")?.takeIf { it != NO_ID },
                        onClose = { navController.closeIfOn(NOTE_ROUTE) },
                    )
                }
                composable(
                    route = EVENT_ROUTE,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = NO_ID },
                        navArgument("begin") { type = NavType.LongType; defaultValue = NO_ID },
                        navArgument("start") { type = NavType.LongType; defaultValue = NO_ID },
                    ),
                ) { entry ->
                    val args = entry.arguments
                    EventEditorRoute(
                        repository = data.calendar,
                        eventId = args?.getLong("id")?.takeIf { it != NO_ID },
                        beginMillis = args?.getLong("begin")?.takeIf { it != NO_ID },
                        startMillis = args?.getLong("start")?.takeIf { it != NO_ID },
                        onClose = { navController.closeIfOn(EVENT_ROUTE) },
                    )
                }
            }
        }

        if (current != null) {
            EuriseDock(
                items = Section.entries.map { DockItem(stringResource(it.label), it.icon) },
                selected = current.ordinal,
                onSelect = { goTo(Section.entries[it]) },
                hazeState = hazeState,
                actionLabel = current.action?.let { stringResource(it) },
                onAction = when (current) {
                    Section.Today -> null
                    Section.Calendar -> ({ navController.openEvent(null, null, null) })
                    Section.Notes -> ({ navController.openNote(null) })
                    Section.Tasks -> ({ newTaskRequests++ })
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
