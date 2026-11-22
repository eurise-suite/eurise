package eu.eurise.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import java.text.DateFormat
import java.util.Date

/** Cosa mostra la griglia: tutte, un gruppo, le private. */
private sealed interface TabFilter {
    data object All : TabFilter
    data object Private : TabFilter
    data class Group(val name: String) : TabFilter
}

private enum class TabsSheet { None, More, Sessions, Closed, SaveName, Assign, Computer }

/** Il colore di un gruppo, come le tinte dei calendari (chiaro o scuro). */
@Composable
internal fun groupColor(model: BrowserModel, name: String?): Color? {
    val g = model.group(name) ?: return null
    val tint = CalendarTint.entries[g.tint.mod(CalendarTint.entries.size)]
    return if (MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue } < 1.5f) tint.dark else tint.light
}

/**
 * Le schede (tavola BrwSessioni): gruppi colorati in alto, la griglia con le miniature, poi sessioni salvate, schede
 * dimenticate e (in arrivo) quelle aperte sul computer. Tieni premuta una scheda per metterla in un gruppo.
 * [startSearch]: si apre già con la ricerca (Ctrl+K).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TabsScreen(model: BrowserModel, startSearch: Boolean, onOpenTab: () -> Unit, onNewTab: (private: Boolean) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var filter by remember { mutableStateOf<TabFilter>(model.current?.group?.let { TabFilter.Group(it) } ?: TabFilter.All) }
    var searching by remember { mutableStateOf(startSearch) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(TabsSheet.None) }
    var assigning by remember { mutableStateOf<Tab?>(null) }
    BackHandler {
        when {
            sheet != TabsSheet.None -> sheet = TabsSheet.None
            searching -> { searching = false; query = "" }
            else -> onClose()
        }
    }
    // Il gruppo scelto può sparire (ultima scheda chiusa): si torna a Tutte.
    (filter as? TabFilter.Group)?.let { f -> if (model.group(f.name) == null) filter = TabFilter.All }
    val shown = model.tabs.filter { t ->
        val inFilter = when (val f = filter) {
            TabFilter.All -> true
            TabFilter.Private -> t.private
            is TabFilter.Group -> t.group == f.name
        }
        inFilter && (query.isBlank() || t.title.contains(query, true) || t.url.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Row(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surface).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(stringResource(R.string.brw_tabs_search), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { model.show(it); onOpenTab() } }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                HeadButton(EuriseIcons.close(2.2f), stringResource(R.string.brw_cancel)) { searching = false; query = "" }
            } else {
                Text(stringResource(R.string.brw_tabs_count, model.tabs.size), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                HeadButton(EuriseIcons.search(2.2f), stringResource(R.string.brw_tabs_search)) { searching = true }
                Spacer(Modifier.width(8.dp))
                HeadButton(EuriseIcons.more(2.2f), stringResource(R.string.brw_more)) { sheet = TabsSheet.More }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(stringResource(R.string.brw_tabs_all), null, filter == TabFilter.All) { filter = TabFilter.All }
            model.groups.forEach { g ->
                val n = model.tabs.count { it.group == g.name }
                FilterChip("${g.name} · $n", groupColor(model, g.name), filter == TabFilter.Group(g.name)) { filter = TabFilter.Group(g.name) }
            }
            val privates = model.tabs.count { it.private }
            if (privates > 0) FilterChip(stringResource(R.string.brw_tabs_private, privates), null, filter == TabFilter.Private) { filter = TabFilter.Private }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shown.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.brw_tabs_none), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            }
            items(shown, key = { it.id }) { t ->
                TabCard(
                    model, t, current = t === model.current,
                    onOpen = { model.show(t); onOpenTab() },
                    onGroup = { assigning = t; sheet = TabsSheet.Assign },
                    onClose = { model.close(t) },
                )
            }
            if (!searching) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                        val last = model.sessions.firstOrNull()
                        InfoRow(
                            EuriseIcons.lists(2.2f), stringResource(R.string.brw_sessions),
                            last?.let { stringResource(R.string.brw_sessions_last, it.name, it.tabs.size) } ?: stringResource(R.string.brw_sessions_none),
                            onClick = { sheet = TabsSheet.Sessions },
                        ) { Chip(stringResource(R.string.brw_sessions_save)) { sheet = TabsSheet.SaveName } }
                        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        InfoRow(
                            EuriseIcons.clock(2.2f), stringResource(R.string.brw_forgotten),
                            stringResource(R.string.brw_forgotten_sub, 7),
                            onClick = null,
                        ) {
                            Switch(checked = model.settings.closeAfterDays > 0, onCheckedChange = { on -> model.settings.setCloseAfter(if (on) 7 else 0) })
                        }
                        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        // Le schede aperte su Firefox con Eurise (Telefono e computer, tavola BrwSincro).
                        val pc = model.computers.firstOrNull { it.tabs.isNotEmpty() }
                        InfoRow(
                            EuriseIcons.monitor(2.2f), stringResource(R.string.brw_on_computer),
                            pc?.let { stringResource(R.string.brw_on_computer_n, it.tabs.size, it.name.ifBlank { stringResource(R.string.brw_on_computer) }) }
                                ?: stringResource(R.string.brw_on_computer_soon),
                            onClick = if (pc != null) ({ sheet = TabsSheet.Computer }) else null,
                        ) { if (pc != null) Text("›", color = scheme.onSurfaceVariant, fontSize = 18.sp) }
                    }
                    Text(stringResource(R.string.brw_tabs_keys), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(scheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(stringResource(R.string.brw_new_tab), EuriseIcons.plus(2f), Modifier.weight(1f), strong = true) { onNewTab(false) }
            Pill(stringResource(R.string.brw_new_private_short), EuriseIcons.mask(1.8f), Modifier.weight(1f)) { onNewTab(true) }
        }
    }

    when (sheet) {
        TabsSheet.None -> Unit
        TabsSheet.More -> BottomSheet(onClose = { sheet = TabsSheet.None }) {
            Text(stringResource(R.string.brw_more), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            MenuRow(EuriseIcons.history(2f), stringResource(R.string.brw_closed_recent), stringResource(R.string.brw_closed_recent_sub, model.closed.size), enabled = model.closed.isNotEmpty()) {
                sheet = TabsSheet.Closed
            }
            MenuRow(EuriseIcons.lists(2f), stringResource(R.string.brw_sessions), stringResource(R.string.brw_sessions_sub, model.sessions.size)) { sheet = TabsSheet.Sessions }
            val f = filter
            val label = when (f) {
                TabFilter.All -> stringResource(R.string.brw_close_all)
                TabFilter.Private -> stringResource(R.string.brw_close_private)
                is TabFilter.Group -> stringResource(R.string.brw_close_group, f.name)
            }
            MenuRow(EuriseIcons.trash(2f), label, stringResource(R.string.brw_close_all_sub), enabled = shown.isNotEmpty()) {
                model.closeAll(shown); sheet = TabsSheet.None
            }
        }
        TabsSheet.Computer -> BottomSheet(onClose = { sheet = TabsSheet.None }) {
            model.computers.filter { it.tabs.isNotEmpty() }.forEach { pc ->
                Text(stringResource(R.string.brw_tabs_on_pc, pc.name.ifBlank { stringResource(R.string.brw_on_computer) }), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    pc.tabs.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { model.open(t.url); sheet = TabsSheet.None; onOpenTab() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(EuriseIcons.globe(2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Links.host(t.url), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        TabsSheet.Closed -> BottomSheet(onClose = { sheet = TabsSheet.None }) {
            Text(stringResource(R.string.brw_closed_recent), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                model.closed.toList().forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable { model.reopen(c); sheet = TabsSheet.None; onOpenTab() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(EuriseIcons.globe(2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title.ifBlank { Links.host(c.url) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Links.host(c.url) + (if (c.time > 0) " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(c.time)) else ""),
                                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        TabsSheet.Sessions -> BottomSheet(onClose = { sheet = TabsSheet.None }) {
            Text(stringResource(R.string.brw_sessions), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (model.sessions.isEmpty()) Text(stringResource(R.string.brw_sessions_explain), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                model.sessions.toList().forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.brw_session_tabs, s.tabs.size, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.time))),
                                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        Chip(stringResource(R.string.brw_session_open), strong = true) { model.openSession(s); sheet = TabsSheet.None; filter = TabFilter.Group(s.name) }
                        Spacer(Modifier.width(4.dp))
                        val remove = stringResource(R.string.brw_remove)
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { model.removeSession(s) }.semantics { contentDescription = remove }, contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Pill(stringResource(R.string.brw_sessions_save), EuriseIcons.plus(2f), Modifier.fillMaxWidth()) { sheet = TabsSheet.SaveName }
        }
        TabsSheet.SaveName -> {
            val f = filter
            val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
            val start = (f as? TabFilter.Group)?.name ?: stringResource(R.string.brw_session_default, today)
            val list = if (f is TabFilter.Group) shown else model.tabs.filter { !it.private }
            NameSheet(
                title = stringResource(R.string.brw_sessions_save_title, list.count { !it.private }), start = start,
                action = stringResource(R.string.brw_sessions_save),
                onDone = { name -> if (name.isNotBlank()) model.saveSession(name, list); sheet = TabsSheet.Sessions },
                onClose = { sheet = TabsSheet.None },
            )
        }
        TabsSheet.Assign -> assigning?.let { t ->
            var naming by remember { mutableStateOf(false) }
            if (naming) {
                NameSheet(
                    title = stringResource(R.string.brw_group_new), start = "", action = stringResource(R.string.brw_group_create),
                    onDone = { name -> model.setGroup(t, name); sheet = TabsSheet.None; model.group(name.trim())?.let { filter = TabFilter.Group(it.name) } },
                    onClose = { sheet = TabsSheet.None },
                )
            } else BottomSheet(onClose = { sheet = TabsSheet.None }) {
                Text(t.title.ifBlank { Links.host(t.url) }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.brw_group_put), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                model.groups.forEach { g ->
                    val on = t.group == g.name
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) scheme.primary.copy(alpha = 0.12f) else scheme.surface)
                            .clickable(role = Role.RadioButton) { model.setGroup(t, g.name); sheet = TabsSheet.None }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(groupColor(model, g.name) ?: scheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(g.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (on) Icon(EuriseIcons.check(2.2f), null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.group != null) Pill(stringResource(R.string.brw_group_none), EuriseIcons.close(2f), Modifier.weight(1f)) { model.setGroup(t, null); sheet = TabsSheet.None }
                    Pill(stringResource(R.string.brw_group_new), EuriseIcons.plus(2f), Modifier.weight(1f), strong = true) { naming = true }
                }
            }
        }
    }
}

/**
 * Una scheda nella griglia (richiesta del proprietario, 15 novembre: leggere di più e riconoscerla subito): icona del
 * sito sempre (anche delle schede non ancora caricate), titolo su due righe, sito, anteprima della pagina anche dopo un
 * riavvio ([Thumbs]); la scheda in uso ha il bordo nell'accento, la testata colorata e l'etichetta «In uso».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(model: BrowserModel, t: Tab, current: Boolean, onOpen: () -> Unit, onGroup: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val color = groupColor(model, t.group)
    val closeLabel = stringResource(R.string.brw_close_tab)
    val url = t.url.ifBlank { t.pending.orEmpty() }
    val host = Links.host(url).removePrefix("www.")
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.height(196.dp)
            .then(if (current) Modifier.shadow(6.dp, shape) else Modifier)
            .clip(shape).background(scheme.surface)
            .then(if (current) Modifier.border(2.5.dp, scheme.primary, shape) else Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.08f), shape))
            .combinedClickable(onClick = onOpen, onLongClick = onGroup),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(color ?: Color.Transparent))
        Row(
            Modifier.fillMaxWidth().background(if (current) scheme.primary.copy(alpha = 0.10f) else Color.Transparent).padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 3.dp)) {
                if (t.private) Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                else SiteIcon(url, 18.dp) { CardInitial(host, current) }
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title.ifBlank { if (url.startsWith("http")) host else stringResource(R.string.brw_new_tab) },
                    fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (current) scheme.primary else scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Text(
                            stringResource(R.string.brw_tab_in_use), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(scheme.primary).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    if (url.startsWith("http")) Text(host, fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 2.dp).clip(RoundedCornerShape(10.dp))
                .background((color ?: scheme.primary).copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = t.thumb ?: if (t.private) null else Thumbs.get(url)
            if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (t.private) Icon(EuriseIcons.mask(1.6f), null, tint = scheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(34.dp))
                else SiteIcon(url, 36.dp) {
                    Text(host.take(1).uppercase().ifBlank { "+" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = (color ?: scheme.primary).copy(alpha = 0.6f))
                }
                if (host.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(host, fontSize = 11.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CardInitial(host: String, on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(18.dp).clip(CircleShape).background(if (on) scheme.primary.copy(alpha = 0.16f) else scheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) { Text(host.take(1).uppercase().ifBlank { "+" }, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (on) scheme.primary else scheme.onSurfaceVariant) }
}

@Composable
private fun HeadButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(scheme.surface).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun FilterChip(label: String, dot: Color?, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) scheme.primary else scheme.surface)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) scheme.onPrimary else scheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, sub: String, onClick: (() -> Unit)?, end: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(scheme.onSurface.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        end()
    }
}

/** Un nome da scrivere (sessione, gruppo nuovo). */
@Composable
private fun NameSheet(title: String, start: String, action: String, onDone: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(start) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BottomSheet(onClose = onClose) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Pill(action, EuriseIcons.check(2f), Modifier.fillMaxWidth(), strong = true) { onDone(name) }
    }
}
