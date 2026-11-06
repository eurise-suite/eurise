package eu.eurise.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.NoteRepository
import eu.eurise.core.data.model.Note
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.NoteCard
import java.time.LocalDate

/** Lista delle note con ricerca. [onOpenNote] riceve l'id della nota, oppure null per una nuova. */
@Composable
fun NotesListRoute(
    repository: NoteRepository,
    onOpenNote: (Long?) -> Unit,
) {
    val viewModel = viewModel { NotesListViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    NotesListScreen(
        query = query,
        notes = state.notes,
        today = LocalDate.now(),
        onQueryChange = viewModel::onQueryChange,
        onOpenNote = onOpenNote,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesListScreen(
    query: String,
    notes: List<Note>?,
    today: LocalDate,
    onQueryChange: (String) -> Unit,
    onOpenNote: (Long?) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenNote(null) },
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.notes_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                SearchField(query, onQueryChange, Modifier.padding(horizontal = 16.dp))
            }
            when {
                notes == null -> Unit
                notes.isEmpty() && query.isBlank() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.StickyNote2,
                        title = stringResource(R.string.notes_empty_title),
                        message = stringResource(R.string.notes_empty_message),
                    )
                }
                notes.isEmpty() -> item(key = "no-results") {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.notes_no_results_title),
                        message = stringResource(R.string.notes_no_results_message, query.trim()),
                    )
                }
                else -> items(notes, key = { it.id }) { note ->
                    NoteCard(
                        title = note.title,
                        body = note.body,
                        pinned = note.pinned,
                        updatedAt = note.updatedAt,
                        today = today,
                        onClick = { onOpenNote(note.id) },
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.notes_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.notes_search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
