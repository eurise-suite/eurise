package eu.eurise.office.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseThemeExtras
import eu.eurise.office.R
import eu.eurise.office.data.DocKind
import eu.eurise.office.data.Recent
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Quello che la schermata iniziale chiede all'attività. */
interface HomeActions {
    fun open(recent: Recent)
    fun openFrom()
    fun create(kind: DocKind)
    fun note()
    fun scan()
    fun calendar()
    fun settings()
    fun downloadEngine()
    fun files()
    fun templates()
}

/**
 * Schermata iniziale di Office (tavola OffHome, approvata il 13 novembre): ricerca in alto, «Nuovo» in sei tasti, i
 * recenti ovunque siano con «Apri da…», la riga del motore quando manca, e la barra satinata Recenti / File / Modelli
 * con il + accanto. Il Calendario sta in alto accanto alle impostazioni.
 */
@Composable
fun HomeScreen(
    recents: List<Recent>,
    query: String,
    onQuery: (String) -> Unit,
    engineReady: Boolean,
    leftHanded: Boolean,
    actions: HomeActions,
) {
    val haze = remember { HazeState() }
    val shown = remember(recents, query) {
        if (query.isBlank()) recents else recents.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "testata") { Header(actions) }
            item(key = "cerca") { SearchField(query, onQuery, leftHanded) }
            if (query.isBlank()) item(key = "nuovo") { NewGrid(actions) }
            item(key = "titolo-recenti") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (query.isBlank()) R.string.office_recent_title else R.string.office_found_title).uppercase(),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = actions::openFrom) {
                        Text(stringResource(R.string.office_open_from), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "vuoto") {
                    Text(
                        stringResource(if (query.isBlank()) R.string.office_recent_empty else R.string.office_found_empty),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item(key = "recenti") {
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp),
                    ) {
                        shown.forEachIndexed { index, recent ->
                            if (index > 0) CardDivider()
                            RecentRow(recent) { actions.open(recent) }
                        }
                    }
                }
            }
            if (!engineReady && query.isBlank()) item(key = "motore") { EngineRow(actions) }
        }
        val items = listOf(
            DockItem(stringResource(R.string.office_tab_recent)) { EuriseIcons.clock(it) },
            DockItem(stringResource(R.string.office_tab_files)) { EuriseIcons.folder(it) },
            DockItem(stringResource(R.string.office_tab_templates)) { EuriseIcons.document(it) },
        )
        EuriseDock(
            items = items,
            selected = 0,
            onSelect = { when (it) { 1 -> actions.files(); 2 -> actions.templates() } },
            hazeState = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
            actionLabel = stringResource(R.string.office_new),
            onAction = { actions.create(DocKind.Text) },
        )
    }
}

@Composable
private fun Header(actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.office_app_name), modifier = Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        IconButton(onClick = actions::calendar) {
            Icon(EuriseIcons.calendar(), contentDescription = stringResource(R.string.office_open_calendar))
        }
        IconButton(onClick = actions::settings) {
            Icon(EuriseIcons.gear(), contentDescription = stringResource(R.string.office_settings))
        }
    }
}

/** Campo di ricerca: la lente sta dal lato della mano (regola sull'ergonomia, 13 novembre). */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, leftHanded: Boolean) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val lens: @Composable () -> Unit = {
            Icon(EuriseIcons.search(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (leftHanded) lens()
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.office_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.5.sp, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!leftHanded) lens()
    }
}

private data class NewKey(val label: Int, val tint: Color, val icon: (Float) -> ImageVector, val run: (HomeActions) -> Unit)

@Composable
private fun NewGrid(actions: HomeActions) {
    val keys = listOf(
        NewKey(R.string.office_new_text, Color(0xFF3E5C8A), { EuriseIcons.document(it) }) { it.create(DocKind.Text) },
        NewKey(R.string.office_new_sheet, Color(0xFF3F5E4B), { EuriseIcons.table(it) }) { it.create(DocKind.Sheet) },
        NewKey(R.string.office_new_slides, Color(0xFF9A6B1F), { EuriseIcons.monitor(it) }) { it.create(DocKind.Slides) },
        NewKey(R.string.office_new_pdf, Color(0xFF8C2F39), { EuriseIcons.pdf(it) }) { it.create(DocKind.Pdf) },
        NewKey(R.string.office_new_note, Color(0xFF6B4A12), { EuriseIcons.note(it) }) { it.note() },
        NewKey(R.string.office_new_scan, Color(0xFF56564F), { EuriseIcons.scanner(it) }) { it.scan() },
    )
    val dark = EuriseThemeExtras.colors.isDark
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Column(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable { key.run(actions) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(key.icon(EuriseIcons.Stroke), contentDescription = null, tint = if (dark) MaterialTheme.colorScheme.primary else key.tint, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(stringResource(key.label), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(recent: Recent, onClick: () -> Unit) {
    val (badge, ink) = badgeColors(recent.kind)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(badge), contentAlignment = Alignment.Center) {
            Text(recent.kind.label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        }
        Column(Modifier.weight(1f)) {
            Text(recent.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                recent.where + " · " + whenText(recent.openedAt),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun badgeColors(kind: DocKind): Pair<Color, Color> {
    if (EuriseThemeExtras.colors.isDark) return MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    return when (kind) {
        DocKind.Text -> Color(0xFFDDE3F0) to Color(0xFF1F2A44)
        DocKind.Sheet -> Color(0xFFDCE8DF) to Color(0xFF2B4A37)
        DocKind.Slides -> Color(0xFFF2E6CC) to Color(0xFF6B4A12)
        DocKind.Pdf -> Color(0xFFF6DDE0) to Color(0xFF7A3440)
        else -> Color(0xFFECECE7) to Color(0xFF3A3A35)
    }
}

@Composable
private fun whenText(at: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> stringResource(R.string.office_today_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at)))
        yesterday -> stringResource(R.string.office_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
    }
}

@Composable
private fun EngineRow(actions: HomeActions) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.office_engine_missing), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.office_engine_missing_detail), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.height(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = actions::downloadEngine).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.office_engine_download), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
