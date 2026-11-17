package eu.eurise.feature.launcher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.modes.Mode
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.settings.ModeOrder
import eu.eurise.feature.launcher.settings.icon
import eu.eurise.feature.launcher.settings.label
import eu.eurise.feature.launcher.summary.BottomSheet

/** Il nome di una pagina come si mostra: quello dato dall'utente o quello delle tre di sempre. */
@Composable
fun DeskPage.title(): String = name.ifBlank { defaultName()?.let { stringResource(it) } ?: stringResource(R.string.page_untitled) }

/**
 * «Le tue pagine» (tavola ScrPagGestisci): tutte le pagine nell'ordine, con la stella della pagina di partenza, le frecce
 * per spostarle, «Modifica» (nome, simbolo, quando si vede), «Duplica», «Elimina»; in fondo «Pagina nuova».
 */
@Composable
fun PagesSheet(
    pages: List<DeskPage>,
    startPage: String,
    countOf: (DeskPage) -> Int,
    onPages: (List<DeskPage>) -> Unit,
    onStart: (String) -> Unit,
    onOpen: (DeskPage) -> Unit,
    onDuplicate: (DeskPage) -> Unit,
    onDelete: (DeskPage) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<DeskPage?>(null) }
    var deleting by remember { mutableStateOf<DeskPage?>(null) }
    BottomSheet(stringResource(R.string.pages_title), onClose) {
        item("hint") {
            Text(stringResource(R.string.pages_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        pages.forEachIndexed { i, page ->
            item(page.id) {
                var menu by remember { mutableStateOf(false) }
                val start = page.id == startPage
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(page) }.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(page.icon(), null, tint = scheme.primary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(page.title(), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = buildList {
                            if (start) add(stringResource(R.string.pages_opens_here))
                            page.mode?.let { add(stringResource(R.string.pages_only_in, stringResource(it.label()))) }
                            if (page.id != DeskPage.HOME) add(androidx.compose.ui.res.pluralStringResource(R.plurals.pages_count, countOf(page), countOf(page)))
                        }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SmallIcon(EuriseIcons.chevronUp(), stringResource(R.string.pages_up), enabled = i > 0) {
                        onPages(pages.toMutableList().apply { add(i - 1, removeAt(i)) })
                    }
                    SmallIcon(EuriseIcons.chevronDown(), stringResource(R.string.pages_down), enabled = i < pages.lastIndex) {
                        onPages(pages.toMutableList().apply { add(i + 1, removeAt(i)) })
                    }
                    Box {
                        SmallIcon(EuriseIcons.more(), stringResource(R.string.pages_options)) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!start) DropdownMenuItem(
                                text = { Text(stringResource(R.string.pages_start_here)) },
                                leadingIcon = { Icon(EuriseIcons.star(), null) },
                                onClick = { menu = false; onStart(page.id) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pages_edit)) },
                                leadingIcon = { Icon(EuriseIcons.pencil(), null) },
                                onClick = { menu = false; editing = page },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pages_duplicate)) },
                                leadingIcon = { Icon(EuriseIcons.copy(), null) },
                                onClick = { menu = false; onDuplicate(page) },
                            )
                            if (page.id != DeskPage.HOME) DropdownMenuItem(
                                text = { Text(stringResource(R.string.pages_delete), color = scheme.error) },
                                leadingIcon = { Icon(EuriseIcons.trash(), null, tint = scheme.error) },
                                onClick = { menu = false; deleting = page },
                            )
                        }
                    }
                }
            }
        }
        item("new") {
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                    .border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                    .clickable(role = Role.Button, onClick = onNew),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(EuriseIcons.plus(2.2f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pages_new), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
            }
        }
    }
    editing?.let { page ->
        PageDialog(
            title = page.title(),
            initial = page,
            onDismiss = { editing = null },
        ) { name, symbol, mode, _ ->
            onPages(pages.map { if (it.id == page.id) it.copy(name = name, symbol = symbol, mode = if (it.id == DeskPage.HOME) null else mode) else it })
            editing = null
        }
    }
    deleting?.let { page ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.pages_delete_title, page.title())) },
            text = { Text(stringResource(R.string.pages_delete_text)) },
            confirmButton = { TextButton(onClick = { onDelete(page); deleting = null }) { Text(stringResource(R.string.pages_delete), color = scheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.pages_cancel)) } },
        )
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(18.dp))
    }
}

/**
 * Nome, simbolo e «quando si vede» di una pagina (tavola ScrPagNuova). Per una pagina nuova anche «Parti da» e «Apri da
 * qui» ([forNew]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageDialog(
    title: String,
    initial: DeskPage?,
    onDismiss: () -> Unit,
    forNew: Boolean = false,
    copyChoices: List<DeskPage> = emptyList(),
    onCreate: ((name: String, symbol: String, mode: Mode?, start: PageStart, copyOf: String?, startHere: Boolean) -> Unit)? = null,
    onSave: (name: String, symbol: String, mode: Mode?, startHere: Boolean) -> Unit = { _, _, _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var symbol by remember { mutableStateOf(initial?.symbol?.ifBlank { null } ?: PageSymbol.Star.id) }
    var mode by remember { mutableStateOf(initial?.mode) }
    var start by remember { mutableStateOf(PageStart.Empty) }
    var copyOf by remember { mutableStateOf(copyChoices.firstOrNull()?.id) }
    var startHere by remember { mutableStateOf(false) }
    val builtIn = initial?.builtIn == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                    label = { Text(stringResource(R.string.pages_name)) },
                    placeholder = { Text(initial?.defaultName()?.let { stringResource(it) } ?: stringResource(R.string.pages_name_hint)) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (!builtIn) {
                    Caption(stringResource(R.string.pages_symbol))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSymbol.entries.forEach { s ->
                            val on = s.id == symbol
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (on) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                    .border(if (on) 2.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(14.dp))
                                    .clickable(role = Role.RadioButton) { symbol = s.id },
                                contentAlignment = Alignment.Center,
                            ) { Icon(s.icon(), null, tint = if (on) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
                if (forNew) {
                    Caption(stringResource(R.string.pages_start_from))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PageStart.Empty to R.string.pages_from_empty,
                            PageStart.AppsAndBubbles to R.string.pages_from_mixed,
                            PageStart.Bubbles to R.string.pages_from_bubbles,
                            PageStart.Copy to R.string.pages_from_copy,
                        ).forEach { (s, label) -> Chip(stringResource(label), start == s) { start = s } }
                    }
                    if (start == PageStart.Copy) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            copyChoices.forEach { p -> Chip(p.title(), copyOf == p.id) { copyOf = p.id } }
                        }
                    }
                }
                if (initial?.id != DeskPage.HOME) {
                    Caption(stringResource(R.string.pages_when))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.pages_always), mode == null) { mode = null }
                        ModeOrder.forEach { m -> Chip(stringResource(m.label()), mode == m, m.icon()) { mode = m } }
                    }
                    Text(stringResource(R.string.pages_when_hint), fontSize = 11.5.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant)
                }
                if (forNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pages_start_here), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.pages_start_here_hint), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(startHere, { startHere = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onCreate != null) onCreate(name, symbol, mode, start, copyOf.takeIf { start == PageStart.Copy }, startHere)
                else onSave(name, symbol, mode, startHere)
            }) { Text(stringResource(if (forNew) R.string.pages_create else R.string.pages_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pages_cancel)) } },
    )
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Chip(text: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
            .background(if (on) scheme.primary else Color.Transparent)
            .border(1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (on) scheme.onPrimary else scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurface)
    }
}

/**
 * «Aggiungi alla Scrivania» (tavola ScrPagAggiungi): le pagine come pulsanti, un tocco ci mette l'app; tenendone premuta
 * una le si dà la stella (dove va l'app al primo tocco dal menu). «Nuova» crea una pagina con l'app dentro.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PagePickerSheet(
    appLabel: String,
    pages: List<DeskPage>,
    addPage: String,
    onPick: (DeskPage) -> Unit,
    onStar: (DeskPage) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BottomSheet(stringResource(R.string.pages_add_title, appLabel), onClose) {
        item("pages") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pages.forEach { page ->
                    val star = page.id == addPage
                    Column(
                        Modifier.width(92.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (star) scheme.primary.copy(alpha = 0.12f) else scheme.onSurface.copy(alpha = 0.05f))
                            .border(if (star) 2.dp else 0.dp, if (star) scheme.primary else Color.Transparent, RoundedCornerShape(18.dp))
                            .combinedClickable(role = Role.Button, onClick = { onPick(page) }, onLongClick = { onStar(page) })
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(page.icon(), null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (star) Icon(EuriseIcons.star(), null, tint = scheme.primary, modifier = Modifier.size(11.dp))
                            Text(page.title(), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(
                    Modifier.width(92.dp).clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, scheme.primary.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onNew).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(EuriseIcons.plus(2.2f), null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.pages_new_short), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                }
            }
        }
        item("hint") {
            Text(stringResource(R.string.pages_add_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
