package eu.eurise.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.eurise.core.designsystem.R
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.format.editedAt
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDate

/**
 * Riga di un'attività: la casella spunta l'attività, il resto della riga (se [onClick] non è nullo)
 * la apre per modificarla. La scadenza passata è scritta nel colore degli avvisi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRow(
    title: String,
    done: Boolean,
    due: LocalDate?,
    today: LocalDate,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick ?: { onCheckedChange(!done) },
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 8.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = done, onCheckedChange = onCheckedChange)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (due != null && !done) {
                    val label = dueLabel(due, today)
                    Text(
                        text = if (label.overdue) stringResource(R.string.ds_overdue, label.text) else label.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (label.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Scheda di una nota nelle liste: titolo, anteprima del testo, ora di modifica e stato "fissata". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(
    title: String,
    body: String,
    pinned: Boolean,
    updatedAt: Long,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val heading = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            Text(
                text = heading.ifBlank { stringResource(R.string.ds_untitled_note) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = if (title.isBlank()) body.substringAfter(heading, "").trim() else body.trim()
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pinned) {
                    Icon(
                        EuriseIcons.pin(),
                        contentDescription = stringResource(R.string.ds_pinned),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = editedAt(updatedAt, today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
