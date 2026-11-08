package eu.eurise.core.links

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.model.Link
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("H:mm")

/**
 * Sezione "Collegati" di una cosa della suite: le cose legate a [from], aperte con un tocco senza cambiare app,
 * più il pulsante per collegarne altre. I collegamenti a cose che non esistono più si tolgono da soli.
 *
 * @param title titolo di [from], mostrato nel foglio per collegare.
 * @param onOpen apre una cosa collegata; i contatti si aprono già qui, nella scheda di sistema.
 */
@Composable
fun LinkedSection(
    from: LinkRef,
    title: String,
    data: EuriseData,
    onOpen: (LinkItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolver = remember(data) { LinkResolver(context, data) }
    val scope = rememberCoroutineScope()
    val links by remember(from) { data.links.observe(from) }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Ogni elenco di collegamenti si legge una volta; i riferimenti spariti si scollegano.
    val resolved by produceState(initialValue = emptyList<Pair<Link, Resolved>>(), links) {
        value = links.map { it to resolver.resolve(it.other) }
        value.filter { (link, r) -> r == Resolved.Gone && link.removable }.forEach { (link, _) -> data.links.unlink(from, link.other) }
    }
    val shown = resolved.mapNotNull { (link, r) -> (r as? Resolved.Found)?.let { link to it.item } }
    val hidden = resolved.count { it.second == Resolved.Unavailable }
    var picking by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                if (shown.isEmpty()) stringResource(R.string.links_title) else stringResource(R.string.links_title_count, shown.size),
                Modifier.weight(1f),
            )
            TextButton(onClick = { picking = true }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(EuriseIcons.link(), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.links_add), fontWeight = FontWeight.SemiBold)
            }
        }
        if (shown.isEmpty()) {
            GroupCard {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { picking = true }.padding(end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.link(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Text(stringResource(R.string.links_empty), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            GroupCard {
                shown.forEachIndexed { i, (link, item) ->
                    if (i > 0) CardDivider()
                    LinkRow(
                        item = item,
                        onClick = {
                            if (item.ref.kind == LinkRef.Kind.Contact) resolver.openContact(item.ref.key) else onOpen(item)
                        },
                        onRemove = if (link.removable) ({ scope.launch { data.links.unlink(from, item.ref) } }) else null,
                    )
                }
            }
        }
        if (hidden > 0) {
            Text(
                stringResource(R.string.links_unavailable, hidden),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    if (picking) {
        LinkPickerSheet(
            title = title,
            exclude = links.map { it.other }.toSet() + from,
            resolver = resolver,
            onConfirm = { chosen -> scope.launch { chosen.forEach { data.links.link(from, it) } } },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun LinkRow(item: LinkItem, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindBadge(item)
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.title.ifBlank { stringResource(R.string.links_untitled) },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) scheme.onSurfaceVariant else scheme.onSurface,
            )
            Text(detailOf(item), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            val label = stringResource(R.string.links_remove, item.title)
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRemove).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(EuriseIcons.close(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
        } else {
            Spacer(Modifier.size(10.dp))
        }
    }
}

/** Riquadro con il simbolo del tipo; per gli eventi prende il colore del calendario. */
@Composable
private fun KindBadge(item: LinkItem) {
    val scheme = MaterialTheme.colorScheme
    val tint = item.color?.let { Color(it) } ?: scheme.primary
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconOf(item.ref.kind), null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

private fun iconOf(kind: LinkRef.Kind): ImageVector = when (kind) {
    LinkRef.Kind.Note -> EuriseIcons.note()
    LinkRef.Kind.Task -> EuriseIcons.task()
    LinkRef.Kind.Event -> EuriseIcons.calendar()
    LinkRef.Kind.Contact -> EuriseIcons.person()
}

/** "Evento · domani 9:00", "Attività · Ven", "Nota", "Persona". */
@Composable
private fun detailOf(item: LinkItem): String {
    val kind = when (item.ref.kind) {
        LinkRef.Kind.Note -> stringResource(if (item.trashed) R.string.links_kind_note_trashed else R.string.links_kind_note)
        LinkRef.Kind.Task -> stringResource(if (item.done) R.string.links_kind_task_done else R.string.links_kind_task)
        LinkRef.Kind.Event -> stringResource(R.string.links_kind_event)
        LinkRef.Kind.Contact -> stringResource(R.string.links_kind_contact)
    }
    val at = item.at ?: return kind
    val day = dueLabel(at.toLocalDate(), LocalDate.now()).text
    return "$kind · $day" + if (item.withTime) " ${at.format(TimeFormat)}" else ""
}

/**
 * Foglio per collegare: una ricerca in tutta la suite con i tipi da filtrare; si scelgono una o più cose
 * e si collegano insieme. [exclude] sono le cose già collegate (e quella di partenza).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPickerSheet(
    title: String,
    exclude: Set<LinkRef>,
    resolver: LinkResolver,
    onConfirm: (List<LinkRef>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<LinkRef.Kind?>(null) }
    var results by remember { mutableStateOf<List<LinkItem>>(emptyList()) }
    val chosen = remember { mutableStateListOf<LinkRef>() }
    val focus = remember { FocusRequester() }
    val people = remember { resolver.canReadContacts() }

    // Si cerca poco dopo l'ultima lettera, per non leggere i dati a ogni tasto.
    LaunchedEffect(query, kind) {
        delay(220)
        results = resolver.search(query, kind?.let { setOf(it) } ?: LinkRef.Kind.entries.toSet())
            .filter { it.ref !in exclude }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.links_picker_title, title.ifBlank { stringResource(R.string.links_untitled) }),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.links_picker_search)) },
                leadingIcon = { Icon(EuriseIcons.search(), null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = scheme.surfaceContainerLow,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().focusRequester(focus),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val kinds = buildList {
                    add(null to R.string.links_picker_all)
                    add(LinkRef.Kind.Note to R.string.links_picker_notes)
                    add(LinkRef.Kind.Task to R.string.links_picker_tasks)
                    if (resolver.canReadCalendar()) add(LinkRef.Kind.Event to R.string.links_picker_events)
                    if (people) add(LinkRef.Kind.Contact to R.string.links_picker_people)
                }
                items(kinds, key = { it.first?.name ?: "all" }) { (k, label) ->
                    FilterPill(stringResource(label), null, kind == k) { kind = k }
                }
            }

            when {
                query.trim().length < 2 -> Hint(stringResource(R.string.links_picker_hint))
                results.isEmpty() -> Hint(stringResource(R.string.links_picker_none, query.trim()))
                else -> GroupCard {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(results, key = { it.ref.toString() }) { item ->
                            val selected = item.ref in chosen
                            val label = stringResource(R.string.links_select, item.title)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 58.dp)
                                    .toggleable(value = selected, role = Role.Checkbox) { on -> if (on) chosen.add(item.ref) else chosen.remove(item.ref) }
                                    .semantics { contentDescription = label }
                                    .padding(end = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                KindBadge(item)
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(item.title.ifBlank { stringResource(R.string.links_untitled) }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(detailOf(item), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (selected) {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                                        Icon(EuriseIcons.check(3f), null, tint = scheme.onPrimary, modifier = Modifier.size(13.dp))
                                    }
                                } else {
                                    Box(Modifier.size(22.dp).border(2.dp, scheme.outlineVariant, CircleShape))
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.lock(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.links_picker_private), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onConfirm(chosen.toList()); onDismiss() },
                    enabled = chosen.isNotEmpty(),
                ) { Text(stringResource(R.string.links_picker_confirm, chosen.size), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
