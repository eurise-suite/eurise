package eu.eurise.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.NoteRepository

/** Editor di una nota; [noteId] nullo crea una nota nuova. [onClose] torna alla schermata precedente. */
@Composable
fun NoteEditorRoute(
    repository: NoteRepository,
    noteId: Long?,
    onClose: () -> Unit,
) {
    val viewModel = viewModel { NoteEditorViewModel(repository, noteId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { viewModel.close(onClose) }
    NoteEditorScreen(
        state = state,
        isNew = noteId == null,
        onTitleChange = viewModel::onTitleChange,
        onBodyChange = viewModel::onBodyChange,
        onTogglePinned = viewModel::togglePinned,
        onDelete = { viewModel.delete(onClose) },
        onDone = { viewModel.close(onClose) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    state: NoteEditorState,
    isNew: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.notes_back))
                    }
                },
                actions = {
                    if (state.saved) {
                        IconButton(onClick = onTogglePinned) {
                            Icon(
                                if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (state.pinned) R.string.notes_unpin else R.string.notes_pin),
                            )
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.notes_delete))
                        }
                    }
                    TextButton(onClick = onDone) { Text(stringResource(R.string.notes_done)) }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val titleFocus = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            PlainField(
                value = state.title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.notes_title_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                modifier = Modifier.focusRequester(titleFocus),
            )
            PlainField(
                value = state.body,
                onValueChange = onBodyChange,
                placeholder = stringResource(R.string.notes_body_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                singleLine = false,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (isNew) {
            LaunchedEffect(Unit) { titleFocus.requestFocus() }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.notes_cancel)) }
            },
        )
    }
}

/** Campo di testo senza bordi, come una pagina: il segnaposto sparisce appena si scrive. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style.copy(color = color),
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            inner()
        },
    )
}
