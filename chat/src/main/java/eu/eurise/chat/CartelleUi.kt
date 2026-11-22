@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** La «cartella» dei gruppi a tempo scaduti (tavola ChatGruppoTempo). */
internal const val ARCHIVE = "__archivio"

/** Le cartelle sopra l'elenco (tavola ChatCartelle): Tutte, le tue cartelle con le chat da leggere, «+» per crearne una. */
@Composable
internal fun FolderChips(model: ChatModel, archived: Int, onNew: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(stringResource(R.string.cr_all), model.folder == null) { model.folder = null }
        model.folders.forEach { f ->
            val n = Cartelle.unread(model.rooms, f)
            Chip(if (n > 0) stringResource(R.string.cr_folder_n, f.name, n) else f.name, model.folder == f.id) {
                model.folder = if (model.folder == f.id) null else f.id
            }
        }
        if (archived > 0) Chip(stringResource(R.string.tb_archive, archived), model.folder == ARCHIVE) {
            model.folder = if (model.folder == ARCHIVE) null else ARCHIVE
        }
        Chip("+", false, onNew)
    }
}

/** «Messaggi a me» in cima all'elenco: il tuo blocco veloce. */
@Composable
internal fun SelfRow(model: ChatModel, actions: ChatActions) {
    val scheme = MaterialTheme.colorScheme
    val failed = stringResource(R.string.cr_self_failed)
    val room = model.selfRoom?.let { id -> model.rooms.firstOrNull { it.id == id } }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button) { model.openSelf { actions.toast(failed) } }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.pin(2.2f), null, tint = scheme.onPrimary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cr_self), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(room?.lastText?.ifBlank { null } ?: stringResource(R.string.cr_self_sub), fontSize = 13.sp, color = scheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
    }
}

/** Sotto ogni messaggio di «Messaggi a me»: un tocco lo manda in Note o in Attività. */
@Composable
internal fun SelfChips(model: ChatModel, actions: ChatActions, e: Event) {
    val noHub = stringResource(R.string.cr_no_hub)
    val inNotes = stringResource(R.string.cr_in_notes)
    val inTasks = stringResource(R.string.cr_in_tasks)
    val text = shownText(model, e)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
        Chip(stringResource(R.string.cr_to_notes), false) { actions.toast(if (model.sendToHub(text, task = false)) inNotes else noHub) }
        Chip(stringResource(R.string.cr_to_tasks), false) { actions.toast(if (model.sendToHub(text, task = true)) inTasks else noHub) }
    }
}

/** Una cartella nuova o da cambiare: nome, chat dentro (una chat può stare in più cartelle), elimina. */
@Composable
internal fun FolderEditSheet(model: ChatModel, actions: ChatActions, folderId: String?, preset: Set<String> = emptySet(), onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val existing = model.folders.firstOrNull { it.id == folderId }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var picked by remember { mutableStateOf(existing?.rooms ?: preset) }
    var confirm by remember { mutableStateOf(false) }
    val taken = stringResource(R.string.cr_name_taken)
    Sheet(onClose) {
        Text(stringResource(if (existing == null) R.string.cr_new else R.string.cr_edit), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 4.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (name.isEmpty()) Text(stringResource(R.string.cr_name_hint), fontSize = 15.sp, color = scheme.onSurfaceVariant)
            BasicTextField(value = name, onValueChange = { name = it.take(Cartelle.MAX_NAME) }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
        }
        Text(stringResource(R.string.cr_pick_chats), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface)) {
            items(model.rooms.filter { !it.invite && it.id != model.selfRoom }, key = { it.id }) { r ->
                val on = r.id in picked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { picked = if (on) picked - r.id else picked + r.id }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.name, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        if (on) Icon(EuriseIcons.check(2.6f), null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing != null) {
                Pill(stringResource(if (confirm) R.string.cr_delete_confirm else R.string.cr_delete), EuriseIcons.trash(2f), Modifier.weight(1f), strong = false) {
                    if (!confirm) confirm = true else { model.removeFolder(existing.id); onClose() }
                }
            }
            Pill(stringResource(R.string.cr_save), EuriseIcons.check(2f), Modifier.weight(1f), strong = true) {
                if (existing == null) {
                    val id = model.addFolder(name, picked)
                    if (id == null) actions.toast(taken) else { model.folder = id; onClose() }
                } else {
                    if (name.trim() != existing.name && !model.renameFolder(existing.id, name)) { actions.toast(taken); return@Pill }
                    (existing.rooms + picked).filter { (it in existing.rooms) != (it in picked) }.forEach { model.toggleFolder(existing.id, it) }
                    onClose()
                }
            }
        }
    }
}

/** Il bottone con la cartella in alto: l'elenco delle cartelle, per cambiarle o crearne una. */
@Composable
internal fun FoldersSheet(model: ChatModel, onEdit: (String?) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Sheet(onClose) {
        Text(stringResource(R.string.cr_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.cr_explain), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        if (model.folders.isNotEmpty()) Card {
            model.folders.forEachIndexed { i, f ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onEdit(f.id) }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.folder(2f), null, tint = scheme.onSurface, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(f.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.cr_count, f.rooms.size), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
        Pill(stringResource(R.string.cr_new), EuriseIcons.plus(2f), Modifier.fillMaxWidth(), strong = true) { onEdit(null) }
    }
}

/** Pressione lunga su una chat: in quali cartelle sta (tocco = mette o toglie), e una cartella nuova. */
@Composable
internal fun RoomFoldersSheet(model: ChatModel, room: Room, onNew: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Sheet(onClose) {
        Text(room.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.cr_room_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            model.folders.forEach { f -> Chip(f.name, room.id in f.rooms) { model.toggleFolder(f.id, room.id) } }
            Chip(stringResource(R.string.cr_new_short), false, onNew)
        }
        // Lo spazio (tavola SpaSpazi): Lavoro, Famiglia, Amici; di base lo dice il ruolo in Rubrica.
        @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
        Text(stringResource(R.string.sp_room), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val now = model.spaceOf(room)
            eu.eurise.core.insieme.Ruoli.Space.entries.forEach { sp ->
                Chip(stringResource(eu.eurise.core.insieme.Ruoli.label(sp)), now == sp) { model.setSpace(room.id, if (model.spaceSet(room.id) == sp) null else sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
