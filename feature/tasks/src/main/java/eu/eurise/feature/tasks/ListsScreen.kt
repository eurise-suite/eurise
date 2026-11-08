package eu.eurise.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.component.LocalDockClearance
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.GroupCard

/**
 * Gli elenchi delle attività: quelli che vanno nel cloud, quelli automatici (Farmaci e Date importanti, di base
 * solo sul telefono, con l'avviso prima di sincronizzarli) e le preferenze delle Attività.
 */
@Composable
internal fun ListsScreen(
    state: TasksState,
    viewModel: TasksViewModel,
    onBack: () -> Unit,
    onOpenList: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<TaskList?>(null) }
    var creating by remember { mutableStateOf(false) }
    var warnFor by remember { mutableStateOf<TaskList?>(null) }
    val cloud = state.lists.filter { !it.list.automatic }
    val automatic = state.lists.filter { it.list.automatic }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(start = 4.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.chevronLeft(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.tasks_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(36.dp).clip(CircleShape).background(scheme.primaryContainer).clickable { creating = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(EuriseIcons.plus(), null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.tasks_list_new), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockClearance.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.tasks_lists),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )

            SectionLabel(stringResource(R.string.tasks_lists_cloud))
            GroupCard {
                cloud.forEachIndexed { i, s ->
                    if (i > 0) CardDivider()
                    ListRow(
                        list = s.list,
                        count = s.open,
                        subtitle = if (s.list.id == TaskList.MAIN) stringResource(R.string.tasks_list_main_hint) else null,
                        onClick = { onOpenList(s.list.id) },
                        onEdit = { editing = s.list },
                    )
                }
            }
            Text(
                stringResource(R.string.tasks_lists_cloud_hint),
                fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            SectionLabel(stringResource(R.string.tasks_lists_automatic), modifier = Modifier.padding(top = 6.dp))
            GroupCard {
                automatic.forEachIndexed { i, s ->
                    if (i > 0) CardDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable { onOpenList(s.list.id) }.padding(start = 4.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(tintColor(s.list.tint)))
                        Column(Modifier.weight(1f)) {
                            Text(s.list.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    if (s.list.kind == TaskList.KIND_MEDICINES) R.string.tasks_list_medicines_hint else R.string.tasks_list_dates_hint,
                                    s.open,
                                ),
                                fontSize = 12.sp, color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.tasks_sync), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                        val description = stringResource(R.string.tasks_sync_list, s.list.name)
                        Switch(
                            checked = s.list.sync,
                            onCheckedChange = { on -> if (on) warnFor = s.list else viewModel.setListSync(s.list, false) },
                            modifier = Modifier.semantics { contentDescription = description },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.tasks_lists_automatic_hint),
                fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            GroupCard(Modifier.padding(top = 6.dp)) {
                SwitchRow(stringResource(R.string.tasks_setting_show_done), state.showDone, viewModel::setShowDone)
                CardDivider()
                SwitchRow(stringResource(R.string.tasks_setting_in_calendar), state.inCalendar, viewModel::setShowInCalendar)
            }
        }
    }

    warnFor?.let { list ->
        SyncWarning(
            list = list,
            onKeepLocal = { warnFor = null },
            onSync = { viewModel.setListSync(list, true); warnFor = null },
        )
    }
    if (creating) {
        ListEditor(null, onSave = { name, tint -> viewModel.addList(name, tint) }, onDelete = null, onDismiss = { creating = false })
    }
    editing?.let { list ->
        ListEditor(
            list,
            onSave = { name, tint -> viewModel.updateList(list.copy(name = name, tint = tint)) },
            onDelete = if (list.kind == TaskList.KIND_USER) ({ viewModel.deleteList(list) }) else null,
            onDismiss = { editing = null },
        )
    }
}


@Composable
private fun ListRow(list: TaskList, count: Int, subtitle: String?, onClick: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tintColor(list.tint)))
        Column(Modifier.weight(1f)) {
            Text(list.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Text(count.toString(), fontSize = 14.sp, color = scheme.onSurfaceVariant)
        val label = stringResource(R.string.tasks_list_edit, list.name)
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onEdit).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(EuriseIcons.pen(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(start = 4.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Avviso prima di sincronizzare un elenco automatico: sono dati sulla salute o sulla vita privata.
 * "Lascia sul telefono" è il pulsante in evidenza.
 */
@Composable
private fun SyncWarning(list: TaskList, onKeepLocal: () -> Unit, onSync: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (ink, fill) = importantColors()
    AlertDialog(
        onDismissRequest = onKeepLocal,
        icon = {
            Box(Modifier.size(44.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.shield(), null, tint = ink, modifier = Modifier.size(22.dp))
            }
        },
        title = { Text(stringResource(R.string.tasks_sync_warning_title, list.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (list.kind == TaskList.KIND_MEDICINES) R.string.tasks_sync_warning_meds else R.string.tasks_sync_warning_dates
                    ),
                    fontSize = 14.sp, lineHeight = 21.sp,
                )
                listOf(
                    R.string.tasks_sync_risk_server, R.string.tasks_sync_risk_account,
                    R.string.tasks_sync_risk_apps, R.string.tasks_sync_risk_copies,
                ).forEach { risk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(ink))
                        Text(stringResource(risk), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
                Text(stringResource(R.string.tasks_sync_warning_gain), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onKeepLocal) { Text(stringResource(R.string.tasks_sync_keep_local)) } },
        dismissButton = { TextButton(onClick = onSync) { Text(stringResource(R.string.tasks_sync_anyway), color = ink) } },
    )
}

/** Nome e tinta di un elenco, nuovo o esistente; gli elenchi dell'utente si possono anche eliminare. */
@Composable
private fun ListEditor(list: TaskList?, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var tint by remember { mutableStateOf(list?.tint ?: CalendarTint.Salvia.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (list == null) R.string.tasks_list_new else R.string.tasks_list_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tasks_list_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarTint.entries.forEach { t ->
                        val selected = t.name == tint
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(tintColor(t.name))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { tint = t.name }
                                .semantics { this.selected = selected; contentDescription = t.name },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.tasks_list_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, tint); onDismiss() }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.tasks_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tasks_cancel)) } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tasks_list_delete_title, list?.name.orEmpty())) },
            text = { Text(stringResource(R.string.tasks_list_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false; onDismiss() }) {
                    Text(stringResource(R.string.tasks_list_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.tasks_cancel)) } },
        )
    }
}
