package eu.eurise.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/**
 * I segnalibri a portata di mano (tavola BrwSegnalibri): dall'icona accanto all'indirizzo. «Aggiungi questa pagina»,
 * ricerca, cartelle come pillole con i conteggi, l'elenco che **scorre** quando i segnalibri non entrano (correzione del
 * proprietario). Tieni premuto un segnalibro: apri in una scheda nuova, sposta, rinomina, fissa nella nuova scheda, togli.
 * [pinMode]: aperto da «Fissa» della nuova scheda, un tocco fissa il sito invece di aprirlo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookmarksPopup(
    model: BrowserModel,
    tab: Tab?,
    pinMode: Boolean,
    onImport: () -> Unit,
    onLater: (LaterItem) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    /** null = tutti; "" non si usa; LATER = «Leggi dopo». */
    var folder by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Bookmark?>(null) }
    var renaming by remember { mutableStateOf<Bookmark?>(null) }
    var moving by remember { mutableStateOf<Bookmark?>(null) }
    val added = stringResource(R.string.brw_bm_added)
    val removed = stringResource(R.string.brw_bm_removed)
    val pinned = stringResource(R.string.brw_pinned_done)
    // L'elenco scorre dentro la scheda: al massimo metà schermo, poi si scorre.
    val listMax = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

    FloatingCard(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (pinMode) R.string.brw_bm_pin_title else R.string.brw_bookmarks), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            val page = tab?.takeIf { it.url.startsWith("http") }
            if (page != null && !pinMode) {
                val on = model.isBookmarked(page.url)
                Row(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(scheme.primary.copy(alpha = 0.14f))
                        .clickable(role = Role.Button) { val nowOn = model.toggleBookmark(page, folder?.takeIf { it != LATER }.orEmpty()); model.notice = if (nowOn) added else removed }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.star(2.3f), null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (on) R.string.brw_bm_remove_page else R.string.brw_bm_add_page), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(scheme.background).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.search(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.brw_bm_search), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
        }
        val folders = model.folders()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FolderChip(stringResource(R.string.brw_bm_all, model.bookmarks.size), folder == null) { folder = null }
            if (!pinMode && model.later.isNotEmpty()) FolderChip(stringResource(R.string.brw_later_title, model.later.size), folder == LATER) { folder = LATER }
            folders.forEach { (f, n) -> FolderChip("${f.substringAfterLast(" › ")} · $n", folder == f) { folder = f } }
        }

        if (folder == LATER) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax)) {
                items(model.later.toList(), key = { it.url }) { l ->
                    Row(Modifier.fillMaxWidth().clickable { onClose(); onLater(l) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteDot(l.url)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title.ifBlank { Links.host(l.url) }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Links.host(l.url) + " · " + stringResource(R.string.brw_reader_minutes, Reading.minutes(l.text)), fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        val remove = stringResource(R.string.brw_remove)
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { model.removeLater(l) }.semantics { contentDescription = remove }, contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        } else {
            val q = query.trim()
            val shown = model.bookmarks.filter { b ->
                (folder == null || b.folder == folder) &&
                    (q.isEmpty() || b.title.contains(q, ignoreCase = true) || b.url.contains(q, ignoreCase = true) || b.folder.contains(q, ignoreCase = true))
            }
            if (model.bookmarks.isEmpty()) {
                Text(stringResource(R.string.brw_bookmarks_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Pill(stringResource(R.string.brw_import), EuriseIcons.download(2f), Modifier.fillMaxWidth(), strong = true) { onClose(); onImport() }
            } else if (shown.isEmpty()) {
                Text(stringResource(R.string.brw_bm_none), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = listMax).padding(horizontal = 4.dp)) {
                items(shown, key = { it.url }) { b ->
                    Column {
                        if (b !== shown.first()) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp).combinedClickable(
                                onClick = {
                                    if (pinMode) { model.updateBookmark(b, b.copy(pinned = true)); model.notice = pinned; onClose() }
                                    else { onClose(); model.go(b.url) }
                                },
                                onLongClick = { menuFor = b },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteDot(b.url)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(Links.host(b.url), b.folder).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.5.sp,
                                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (b.pinned) Icon(EuriseIcons.pin(2.2f), null, tint = scheme.primary, modifier = Modifier.size(15.dp))
                        }
                        if (menuFor == b) BookmarkActions(
                            b, model.folders().map { it.first },
                            onNewTab = { menuFor = null; onClose(); model.open(b.url) },
                            onPin = { model.updateBookmark(b, b.copy(pinned = !b.pinned)); menuFor = null },
                            onRename = { renaming = b; menuFor = null },
                            onMove = { moving = b; menuFor = null },
                            onRemove = { model.removeBookmark(b); menuFor = null },
                        )
                        if (renaming == b) RenameRow(b.title) { name -> if (name.isNotBlank()) model.updateBookmark(b, b.copy(title = name.trim())); renaming = null }
                        if (moving == b) MoveRow(b.folder, model.folders().map { it.first }) { f -> model.updateBookmark(b, b.copy(folder = f)); moving = null }
                    }
                }
            }
        }
        Text(stringResource(if (pinMode) R.string.brw_bm_pin_hint else R.string.brw_bm_hint), fontSize = 11.5.sp, lineHeight = 16.sp,
            color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private const val LATER = "\u0000later"

@Composable
private fun FolderChip(label: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) scheme.primary else scheme.background)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) scheme.onPrimary else scheme.onSurface, maxLines = 1) }
}

@Composable
private fun BookmarkActions(
    b: Bookmark,
    folders: List<String>,
    onNewTab: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 44.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallChip(stringResource(R.string.brw_bm_new_tab), onNewTab)
        SmallChip(stringResource(if (b.pinned) R.string.brw_bm_unpin else R.string.brw_bm_pin), onPin)
        SmallChip(stringResource(R.string.brw_bm_rename), onRename)
        SmallChip(stringResource(R.string.brw_bm_move), onMove)
        SmallChip(stringResource(R.string.brw_remove), onRemove)
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.primary) }
}

@Composable
private fun RenameRow(start: String, onDone: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(start) }
    Row(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            text, { text = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 10.dp, vertical = 9.dp),
        )
        Spacer(Modifier.width(6.dp))
        SmallChip(stringResource(R.string.brw_bm_save)) { onDone(text) }
    }
}

@Composable
private fun MoveRow(current: String, folders: List<String>, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var fresh by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FolderChip(stringResource(R.string.brw_bm_no_folder), current.isBlank()) { onPick("") }
            folders.forEach { f -> FolderChip(f.substringAfterLast(" › "), f == current) { onPick(f) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 10.dp, vertical = 9.dp)) {
                if (fresh.isEmpty()) Text(stringResource(R.string.brw_bm_new_folder), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                BasicTextField(fresh, { fresh = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(6.dp))
            SmallChip(stringResource(R.string.brw_bm_save)) { if (fresh.isNotBlank()) onPick(fresh.trim()) }
        }
    }
}
