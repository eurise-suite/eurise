package eu.eurise.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le azioni che passano dall'attività (file, altre app, sistema). */
interface BrowserActions {
    fun listen(text: String)
    fun finish()
    fun pickImportFiles()
    fun openPassword()
    /** Apre Eurise Password per sbloccarla; aperta, la pagina si ricarica e compila. */
    fun unlockPassword()
    fun autofillSettings()
    fun openApp(packages: List<String>): Boolean
    fun capture(tab: Tab)
    fun savePdf(tab: Tab)
    fun addToHome(tab: Tab)
    fun openDownloads()
    fun exportBookmarks()
    fun toast(text: String)
    /** «Promemoria»: la data trovata nella pagina va nel Calendario. */
    fun addEvent(title: String, date: PageDate, url: String)
    /** «Cattura intera»: tutto l'articolo in un'immagine lunga. */
    fun captureArticle(title: String, text: String, url: String)
}

/** Le voci della griglia del menu (tavola BrwMenu); [needsPage]: servono una pagina web aperta. */
enum class MenuItem(val id: String, val label: Int, val needsPage: Boolean = true) {
    Favorite("favorite", R.string.brw_m_favorite),
    Notes("notes", R.string.brw_m_notes),
    Task("task", R.string.brw_m_task),
    Reader("reader", R.string.brw_m_reader),
    Translate("translate", R.string.brw_m_translate),
    Find("find", R.string.brw_m_find),
    Capture("capture", R.string.brw_m_capture),
    Zoom("zoom", R.string.brw_m_zoom, needsPage = false),
    Desktop("desktop", R.string.brw_m_desktop),
    Dark("dark", R.string.brw_m_dark, needsPage = false),
    Downloads("downloads", R.string.brw_m_downloads, needsPage = false),
    Bookmarks("bookmarks", R.string.brw_m_bookmarks, needsPage = false),
    Passwords("passwords", R.string.brw_m_passwords, needsPage = false),
    Pdf("pdf", R.string.brw_m_pdf),
    Home("home", R.string.brw_m_home);

    fun icon(): ImageVector = when (this) {
        Favorite -> EuriseIcons.star(2.1f)
        Notes -> EuriseIcons.note(2.1f)
        Task -> EuriseIcons.task(2.1f)
        Reader -> EuriseIcons.read(2.1f)
        Translate -> EuriseIcons.translate(2.1f)
        Find -> EuriseIcons.search(2.1f)
        Capture -> EuriseIcons.camera(2.1f)
        Zoom -> EuriseIcons.zoomIn(2.1f)
        Desktop -> EuriseIcons.monitor(2.1f)
        Dark -> EuriseIcons.moon(2.1f)
        Downloads -> EuriseIcons.download(2.1f)
        Bookmarks -> EuriseIcons.book(2.1f)
        Passwords -> EuriseIcons.key(2.1f)
        Pdf -> EuriseIcons.printer(2.1f)
        Home -> EuriseIcons.home(2.1f)
    }

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Il menu completo: la pagina in alto, tutto in una griglia, la protezione e le Impostazioni in fondo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MenuSheet(
    model: BrowserModel,
    tab: Tab?,
    downloads: Int,
    onClose: () -> Unit,
    onNewTab: () -> Unit,
    onPrivateTab: () -> Unit,
    onHistory: () -> Unit,
    onItem: (MenuItem) -> Unit,
    onProtection: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val page = tab?.url?.startsWith("http") == true
    val pinnedLabel = stringResource(R.string.brw_bar_pinned)
    val unpinnedLabel = stringResource(R.string.brw_bar_unpinned)
    BottomSheet(onClose = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (page) Links.host(tab!!.url) else stringResource(R.string.brw_new_tab),
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (tab != null) {
                SmallRound(EuriseIcons.chevronRight(2f), stringResource(R.string.brw_forward), tab.canForward) { tab.session.goForward(); onClose() }
                SmallRound(EuriseIcons.refresh(2f), stringResource(R.string.brw_reload), page) { tab.session.reload(); onClose() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.brw_new_tab), strong = true, onClick = onNewTab)
            Chip(stringResource(R.string.brw_new_private_short), onClick = onPrivateTab)
            Chip(stringResource(R.string.brw_history), onClick = onHistory)
        }
        MenuItem.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val enabled = !item.needsPage || page
                    val sub = when (item) {
                        MenuItem.Zoom -> "${(model.settings.textScale * 100).toInt()}%"
                        MenuItem.Downloads -> if (downloads > 0) "$downloads" else null
                        MenuItem.Dark -> if (model.settings.darkPages) stringResource(R.string.brw_on) else null
                        MenuItem.Desktop -> if (tab?.desktop == true) stringResource(R.string.brw_on) else null
                        MenuItem.Translate -> if (tab?.translated == true) stringResource(R.string.brw_on) else null
                        MenuItem.Favorite -> if (tab != null && model.isPinned(tab.url)) stringResource(R.string.brw_on) else null
                        else -> null
                    }
                    val label = stringResource(item.label)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                enabled = enabled, role = Role.Button,
                                onLongClick = {
                                    val same = model.settings.barAction == item.id
                                    model.settings.setBarAction(if (same) "" else item.id)
                                    model.notice = if (same) unpinnedLabel else pinnedLabel.format(label)
                                },
                                onClick = { onItem(item) },
                            )
                            .alpha(if (enabled) 1f else 0.4f).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(scheme.surface), contentAlignment = Alignment.Center) {
                            Icon(item.icon(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sub != null) Text(sub, fontSize = 10.5.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val on = tab?.protectionOn != false
            Icon(EuriseIcons.shield(2f), null, tint = if (on) Safe else Half, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) stringResource(R.string.brw_menu_protection_on, tab?.blocked ?: 0) else stringResource(R.string.brw_menu_protection_off),
                    fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(if (model.settings.strictProtection) R.string.brw_menu_protection_strict else R.string.brw_menu_protection_standard),
                    fontSize = 11.5.sp, color = scheme.onSurfaceVariant,
                )
            }
            Chip(stringResource(R.string.brw_this_site), onClick = onProtection)
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(scheme.surface)
                .clickable(role = Role.Button, onClick = onSettings),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(EuriseIcons.gear(2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.brw_settings), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        }
        Text(stringResource(R.string.brw_menu_hint), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
internal fun Chip(label: String, strong: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (strong) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (strong) scheme.onPrimary else scheme.primary, maxLines = 1)
    }
}

@Composable
private fun SmallRound(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) }
}

/** Trova nella pagina: prende il posto della barra in basso. */
@Composable
internal fun FindBar(tab: Tab, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(stringResource(R.string.brw_find_hint), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                BasicTextField(
                    value = text, onValueChange = { text = it; tab.find(it) }, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { tab.find(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (text.isNotEmpty()) Text("${tab.findCurrent}/${tab.findTotal}", fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        SmallRound(EuriseIcons.chevronLeft(2f), stringResource(R.string.brw_find_previous), text.isNotEmpty()) { tab.find(text, backwards = true) }
        SmallRound(EuriseIcons.chevronRight(2f), stringResource(R.string.brw_find_next), text.isNotEmpty()) { tab.find(text) }
        SmallRound(EuriseIcons.close(2f), stringResource(R.string.brw_cancel), true) { tab.stopFind(); onClose() }
    }
}

/** La cronologia, dalla più recente, divisa per giorno; si cerca e si cancella. */
@Composable
internal fun HistorySheet(model: BrowserModel, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var visits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(query, version) { visits = withContext(Dispatchers.IO) { model.history.list(query) } }
    val today = stringResource(R.string.brw_today)
    val yesterday = stringResource(R.string.brw_yesterday)
    BottomSheet(onClose = onClose) {
        Text(stringResource(R.string.brw_history), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(scheme.surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.search(2.3f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.brw_history_search), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (visits.isEmpty()) {
            Text(stringResource(R.string.brw_history_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            var lastDay = ""
            visits.forEach { v ->
                val day = dayLabel(v.time, today, yesterday)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "d$day${v.time}") {
                        Text(day.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                            color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    }
                }
                item(key = "v${v.time}${v.url}") {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(v.url) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.title.ifBlank { Links.host(v.url) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Links.host(v.url) + " · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(v.time)),
                                fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        val remove = stringResource(R.string.brw_remove)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).clickable { Thread { model.history.remove(v) }.start(); version++ }
                                .semantics { contentDescription = remove },
                            contentAlignment = Alignment.Center,
                        ) { Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        if (visits.isNotEmpty() && query.isEmpty()) {
            Pill(stringResource(R.string.brw_history_clear), EuriseIcons.trash(2f), Modifier.fillMaxWidth()) {
                model.clearData(history = true, cookies = false, cache = false, permissions = false, since = 0L) { }
                visits = emptyList()
            }
        }
    }
}

private fun dayLabel(time: Long, today: String, yesterday: String): String {
    val c = Calendar.getInstance()
    val now = Calendar.getInstance()
    c.timeInMillis = time
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(c, now)) return today
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(c, now)) return yesterday
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
}

/** Dimensione del testo delle pagine (Zoom), uguale per tutti i siti. */
@Composable
internal fun ZoomSheet(model: BrowserModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BottomSheet(onClose = onClose) {
        Text(stringResource(R.string.brw_zoom_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrowserSettings.SCALES.forEach { s ->
                val on = kotlin.math.abs(model.settings.textScale - s) < 0.01f
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (on) scheme.primary else scheme.surface)
                        .clickable(role = Role.RadioButton) { model.setTextScale(s) },
                    contentAlignment = Alignment.Center,
                ) { Text("${(s * 100).toInt()}%", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurface) }
            }
        }
        Text(stringResource(R.string.brw_zoom_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
    }
}

/** Prima traduzione: cosa succede, prima di accendere (regola della privacy). */
@Composable
internal fun TranslateExplain(onAccept: () -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BottomSheet(onClose = onClose) {
        Text(stringResource(R.string.brw_translate_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.brw_translate_explain), fontSize = 13.5.sp, lineHeight = 20.sp, color = scheme.onSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.brw_cancel), EuriseIcons.close(2f), Modifier.weight(1f), onClick = onClose)
            Pill(stringResource(R.string.brw_m_translate), EuriseIcons.translate(2f), Modifier.weight(1f), strong = true, onClick = onAccept)
        }
    }
}
