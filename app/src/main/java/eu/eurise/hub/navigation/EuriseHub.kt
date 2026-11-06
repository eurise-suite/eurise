package eu.eurise.hub.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import eu.eurise.core.data.EuriseData
import eu.eurise.feature.notes.NoteEditorRoute
import eu.eurise.feature.notes.NotesListRoute
import eu.eurise.feature.tasks.TasksRoute
import eu.eurise.feature.today.TodayRoute
import eu.eurise.hub.R

/** Le sezioni principali dell'hub, nella barra in basso (al massimo quattro, come da design system). */
private enum class Section(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Today("today", R.string.section_today, Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    Notes("notes", R.string.section_notes, Icons.AutoMirrored.Outlined.StickyNote2, Icons.AutoMirrored.Filled.StickyNote2),
    Tasks("tasks", R.string.section_tasks, Icons.Outlined.TaskAlt, Icons.Filled.TaskAlt),
}

private const val NOTE_ROUTE = "note?id={id}"
private const val NO_ID = -1L

private fun NavHostController.openNote(id: Long?) = navigate("note?id=${id ?: NO_ID}")

/** Il guscio dell'app: barra delle sezioni e navigazione tra i moduli. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EuriseHub(data: EuriseData) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = Section.entries.any { s -> destination?.hierarchy?.any { it.route == s.route } == true }

    fun goTo(section: Section) = navController.navigate(section.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Section.entries.forEach { section ->
                        val selected = destination?.hierarchy?.any { it.route == section.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { goTo(section) },
                            icon = { Icon(if (selected) section.selectedIcon else section.icon, contentDescription = null) },
                            label = { Text(stringResource(section.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Ogni schermata gestisce da sé la barra di stato; qui si riserva solo lo spazio in basso.
        NavHost(
            navController = navController,
            startDestination = Section.Today.route,
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            composable(Section.Today.route) {
                TodayRoute(
                    notes = data.notes,
                    tasks = data.tasks,
                    onOpenNote = { navController.openNote(it) },
                    onShowNotes = { goTo(Section.Notes) },
                    onShowTasks = { goTo(Section.Tasks) },
                )
            }
            composable(Section.Notes.route) {
                NotesListRoute(repository = data.notes, onOpenNote = { navController.openNote(it) })
            }
            composable(Section.Tasks.route) {
                TasksRoute(repository = data.tasks)
            }
            composable(
                route = NOTE_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = NO_ID }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it != NO_ID }
                NoteEditorRoute(
                    repository = data.notes,
                    noteId = id,
                    onClose = {
                        // Evita di tornare indietro due volte se "Fine" viene toccato più volte di fila.
                        if (navController.currentBackStackEntry?.destination?.route == NOTE_ROUTE) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}
