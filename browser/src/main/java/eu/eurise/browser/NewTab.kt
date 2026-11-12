package eu.eurise.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.TodayLinksContract
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Le scorciatoie in fondo alla nuova scheda. */
enum class NewTabTool { Downloads, Passwords, Extensions, History }

/** Dove cerca il campo della nuova scheda: sul motore, all'AI scelta, nella cronologia. */
private enum class Mode { Search, Ai, History }

/** Le tinte dei siti (tondo con l'iniziale): chiaro/scuro come le pillole della suite, scelte dal nome del sito. */
private val TILE_LIGHT = listOf(
    0xFFDDE3F0 to 0xFF1F2A44, 0xFFF3E2CF to 0xFF7A4A2A, 0xFFDCE7DF to 0xFF2F6B45, 0xFFE8E1F2 to 0xFF5A4BA0,
    0xFFF6E0DC to 0xFF9A3B2E, 0xFFE3EDF0 to 0xFF2F6F73, 0xFFECECE7 to 0xFF3A3A35, 0xFFF2E9C9 to 0xFF7A5B12,
)

@Composable
internal fun siteTint(host: String): Pair<Color, Color> {
    val (bg, fg) = TILE_LIGHT[Math.floorMod(host.hashCode(), TILE_LIGHT.size)]
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    return if (dark) Color(fg).copy(alpha = 0.55f) to Color(bg) else Color(bg) to Color(fg)
}

/** Il tondo con l'iniziale del sito (nuova scheda, segnalibri, menu dell'indirizzo). */
@Composable
internal fun SiteDot(url: String, size: Int = 32) {
    val host = Links.host(url)
    val (bg, fg) = siteTint(host)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(host.take(1).uppercase(), fontSize = (size * 0.4f).sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

/**
 * La nuova scheda (tavola BrwNuovaSchedaPro): ricerca con «Chiedi all'AI», «Nella cronologia» e il lettore QR; «Riprendi
 * da dove eri»; siti fissati; «Per oggi, dalla suite» (impegni e cose da fare con un link); scorciatoie; protezione.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewTabPage(
    model: BrowserModel,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onQr: () -> Unit,
    onPin: () -> Unit,
    onLater: (LaterItem) -> Unit,
    onTool: (NewTabTool) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val private = model.current?.private == true
    var text by remember { mutableStateOf("") }
    val ai = model.settings.aiService
    var mode by remember { mutableStateOf(Mode.Search) }
    LaunchedEffect(Unit) { if (!private) model.loadToday() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.focusNewTab) { if (model.focusNewTab) { model.focusNewTab = false; runCatching { focus.requestFocus() } } }

    fun submit() {
        val t = text.trim()
        if (t.isEmpty()) return
        when (mode) {
            Mode.Search -> onOpen(t)
            Mode.Ai -> onOpen("? $t")
            Mode.History -> model.history.list(t, 1).firstOrNull()?.let { onOpen(it.url) } ?: onOpen(t)
        }
    }

    Column(
        Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(greeting(private), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().height(52.dp).shadow(6.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(RoundedCornerShape(26.dp)).background(scheme.surface).padding(start = 16.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(EuriseIcons.search(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(
                        stringResource(when (mode) { Mode.Ai -> R.string.brw_nt_hint_ai; Mode.History -> R.string.brw_nt_hint_history; else -> R.string.brw_address_hint }),
                        fontSize = 15.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    BasicTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                val qr = stringResource(R.string.brw_nt_qr)
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(scheme.background).clickable(role = Role.Button, onClick = onQr)
                        .semantics { contentDescription = qr },
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.scan(2.2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip(stringResource(R.string.brw_nt_search), null, mode == Mode.Search) { mode = Mode.Search }
                if (ai != null) ModeChip(stringResource(R.string.brw_nt_ask_ai), EuriseIcons.spark(2.2f), mode == Mode.Ai) { mode = Mode.Ai }
                if (!private) ModeChip(stringResource(R.string.brw_nt_in_history), null, mode == Mode.History) { mode = Mode.History }
            }
            if (mode == Mode.History && text.isNotBlank()) {
                val found = remember(text) { model.history.list(text.trim(), 6) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                    if (found.isEmpty()) Text(stringResource(R.string.brw_nt_history_none), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
                    found.forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().clickable { onOpen(v.url) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SiteDot(v.url, 28)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title.ifBlank { Links.host(v.url) }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Links.host(v.url), fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (private) Text(stringResource(R.string.brw_private_note), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        }
        if (private) return@Column

        // Riprendi da dove eri: l'ultima scheda chiusa e il primo «Leggi dopo».
        val last = model.closed.firstOrNull()
        val later = model.later.firstOrNull()
        if (last != null || later != null) {
            Section(stringResource(R.string.brw_nt_resume))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (last != null) ResumeCard(EuriseIcons.history(2.2f), stringResource(R.string.brw_nt_closed, ago(last.time)), last.title.ifBlank { Links.host(last.url) }, Modifier.weight(1f)) { model.reopen(last) }
                if (later != null) ResumeCard(EuriseIcons.bookmark(2.2f), stringResource(R.string.brw_nt_later, model.later.size), later.title.ifBlank { Links.host(later.url) }, Modifier.weight(1f)) { onLater(later) }
            }
        }

        // Siti fissati, quattro per riga, con «Fissa» in fondo. Tieni premuto un sito per toglierlo.
        Section(stringResource(R.string.brw_pinned))
        val pins = model.bookmarks.filter { it.pinned }
        val unpinned = stringResource(R.string.brw_unpinned_done)
        val cells: List<Bookmark?> = pins + listOf(null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        if (b == null) {
                            val pinLabel = stringResource(R.string.brw_nt_pin)
                            Column(
                                Modifier.weight(1f).height(74.dp).clip(RoundedCornerShape(18.dp))
                                    .border(1.5.dp, scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                                    .clickable(role = Role.Button, onClick = onPin).semantics { contentDescription = pinLabel },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(EuriseIcons.plus(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(pinLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                Modifier.weight(1f).height(74.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface)
                                    .combinedClickable(
                                        onClick = { onOpen(b.url) },
                                        onLongClick = { model.updateBookmark(b, b.copy(pinned = false)); model.notice = unpinned },
                                    ).padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                SiteDot(b.url)
                                Spacer(Modifier.height(6.dp))
                                Text(b.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (pins.isEmpty() && model.bookmarks.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(onClick = onImport).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.download(2.2f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.brw_pinned_empty), fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        // Per oggi, dalla suite.
        if (model.today.isNotEmpty()) {
            Section(stringResource(R.string.brw_nt_today))
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                model.today.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    TodayRow(t) { onOpen(t.url) }
                }
            }
        }

        // Scorciatoie.
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolChip(EuriseIcons.download(2.3f), if (model.downloads > 0) stringResource(R.string.brw_nt_downloads_n, model.downloads) else stringResource(R.string.brw_m_downloads)) { onTool(NewTabTool.Downloads) }
            ToolChip(EuriseIcons.key(2.3f), stringResource(R.string.brw_m_passwords)) { onTool(NewTabTool.Passwords) }
            ToolChip(EuriseIcons.puzzle(2.3f), stringResource(R.string.brw_s_extensions)) { onTool(NewTabTool.Extensions) }
            ToolChip(EuriseIcons.history(2.3f), stringResource(R.string.brw_nt_history)) { onTool(NewTabTool.History) }
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.shield(2.3f), null, tint = Safe, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.brw_nt_blocked_week, model.blockedWeek), fontSize = 12.sp, color = Safe)
        }
    }
}

@Composable
private fun greeting(private: Boolean): String {
    if (private) return stringResource(R.string.brw_private_title)
    val now = LocalDateTime.now()
    val day = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    val hello = stringResource(when (now.hour) { in 5..12 -> R.string.brw_nt_morning; in 13..17 -> R.string.brw_nt_afternoon; else -> R.string.brw_nt_evening })
    return "$day · $hello"
}

/** «10 min fa», «2 h fa», «ieri». */
@Composable
internal fun ago(time: Long): String {
    val min = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0)
    return when {
        min < 1 -> stringResource(R.string.brw_nt_now)
        min < 60 -> stringResource(R.string.brw_nt_min_ago, min.toInt())
        min < 24 * 60 -> stringResource(R.string.brw_nt_h_ago, (min / 60).toInt())
        else -> stringResource(R.string.brw_nt_d_ago, (min / (24 * 60)).toInt())
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun ModeChip(label: String, icon: ImageVector?, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) scheme.primary else scheme.surface)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (on) scheme.onPrimary else Color(0xFF8A3A5C), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) scheme.onPrimary else scheme.onSurface)
    }
}

@Composable
private fun ResumeCard(icon: ImageVector, label: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(3.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TodayRow(t: TodayLink, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, bg) = when (t.kind) {
        TodayLinksContract.KIND_MEETING -> Triple(EuriseIcons.video(2.3f), Color(0xFF3E5C8A), Color(0xFFDDE3F0))
        TodayLinksContract.KIND_TASK -> Triple(EuriseIcons.check(2.3f), Color(0xFF4F6B5A), Color(0xFFDCE7DF))
        else -> Triple(EuriseIcons.calendar(2.3f), Color(0xFF7A4A2A), Color(0xFFF3E2CF))
    }
    val from = stringResource(if (t.kind == TodayLinksContract.KIND_TASK) R.string.brw_nt_from_tasks else R.string.brw_nt_from_calendar)
    val now = System.currentTimeMillis()
    val minutes = ((t.time - now) / 60_000L).toInt()
    val time = t.time.takeIf { it > 0 && t.kind != TodayLinksContract.KIND_TASK }?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val title = when {
        t.kind == TodayLinksContract.KIND_MEETING && time != null -> stringResource(R.string.brw_nt_meeting, t.title, time)
        time != null -> "${t.title} · $time"
        else -> t.title
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (scheme.background.luminance() < 0.3f) tint.copy(alpha = 0.35f) else bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (scheme.background.luminance() < 0.3f) bg else tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Links.host(t.url)} · $from", fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.kind != TodayLinksContract.KIND_TASK && minutes in 1..180) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.brw_nt_in_min, minutes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E5C8A))
        }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(scheme.surface).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 11.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}
