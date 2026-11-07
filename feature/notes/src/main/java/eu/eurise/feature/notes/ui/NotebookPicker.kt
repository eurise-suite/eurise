package eu.eurise.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.model.NotebookSummary
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.notes.R

/** Foglio per scegliere il taccuino di una nota, con la possibilità di crearne uno nuovo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotebookPickerSheet(
    notebooks: List<NotebookSummary>,
    selected: Long?,
    onPick: (Long?) -> Unit,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.notes_notebook_choose),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            PickRow(stringResource(R.string.notes_notebook_none), null, selected == null) { onPick(null) }
            notebooks.forEach { s ->
                PickRow(s.notebook.name, s.notebook.tint, selected == s.notebook.id) { onPick(s.notebook.id) }
            }
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(EuriseIcons.plus(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.notes_notebook_new))
            }
        }
    }
    if (creating) {
        NotebookDialog(
            initialName = "",
            initialTint = CalendarTint.entries[notebooks.size % CalendarTint.entries.size].name,
            onConfirm = { name, tint -> creating = false; onCreate(name, tint) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PickRow(label: String, tint: String?, selected: Boolean, onClick: () -> Unit) {
    val ink = inkColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (tint != null) Box(Modifier.size(12.dp).clip(CircleShape).background(ink.dot(tint)))
        else Box(Modifier.size(12.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(EuriseIcons.check(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** Nome e tinta di un taccuino, per crearlo o modificarlo. */
@Composable
internal fun NotebookDialog(
    initialName: String,
    initialTint: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
    title: Int = R.string.notes_notebook_new,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var tint by rememberSaveable { mutableStateOf(initialTint) }
    val ink = inkColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.notes_notebook_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CalendarTint.entries.forEach { t ->
                        val label = tintLabel(t)
                        val isSelected = tint == t.name
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.RadioButton) { tint = t.name }
                                .semantics { contentDescription = label; this.selected = isSelected },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(4.dp) else Modifier)
                                    .clip(CircleShape)
                                    .background(ink.dot(t.name)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), tint) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.notes_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_cancel)) } },
    )
}
