package eu.eurise.password.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.EmptyState
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.password.ImportStage
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.logic.ImportKind
import eu.eurise.password.R
import eu.eurise.password.data.SyncProblem
import eu.eurise.password.data.SyncState
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.kdbx.CardData
import eu.eurise.password.data.AliasProvider
import eu.eurise.password.logic.Cards
import eu.eurise.password.logic.ImportOutcome
import eu.eurise.password.logic.Importer
import eu.eurise.password.logic.Generator
import eu.eurise.password.logic.HealthIssue
import eu.eurise.password.logic.Strength
import eu.eurise.password.logic.Totp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

// L'elenco delle voci con le schede e il banner «Da sistemare». (diviso da PasswordApp.kt il 18 novembre).

@Composable
internal fun VaultList(vm: PasswordViewModel, entries: List<VaultEntry>, onOpen: (String) -> Unit, onAdd: (Tab) -> Unit, onSync: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haze = remember { HazeState() }
    var tab by remember { mutableStateOf(Tab.All) }
    var query by remember { mutableStateOf("") }
    val sync by vm.sync.collectAsStateWithLifecycle()
    val now = rememberNow()

    val breaches by vm.breaches.collectAsStateWithLifecycle()
    val issues = remember(entries, breaches) {
        val base = Generator.health(entries.map { it.uuid to it.password }).associateBy { it.uuid }
        base + entries.filter { breaches[it.uuid] == it.modified }.associate { e ->
            e.uuid to (base[e.uuid]?.copy(breached = true) ?: HealthIssue(e.uuid, weak = false, reused = false, breached = true))
        }
    }
    val expiring = remember(entries) {
        val today = LocalDate.now()
        entries.filter { e -> e.card?.let { Cards.expiringSoon(it.expiry, today) } == true }.mapTo(HashSet()) { it.uuid }
    }
    val sorted = remember(entries) { entries.sortedBy { it.title.lowercase() } }
    val rows = remember(sorted, issues, expiring, tab, query) {
        val q = query.trim().lowercase()
        val visible = sorted.filter { e ->
            val inTab = when (tab) {
                Tab.All -> true
                Tab.Otp -> e.otp != null
                Tab.Cards -> e.card != null
                Tab.MyData -> e.profile != null
                Tab.Notes -> e.card == null && e.password.isEmpty() && e.notes.isNotBlank()
                Tab.Fix -> e.uuid in issues || e.uuid in expiring
            }
            inTab && (q.isEmpty() || e.title.lowercase().contains(q) || e.username.lowercase().contains(q) || e.url.lowercase().contains(q))
        }
        buildList<Row2> {
            if (tab == Tab.All && q.isEmpty()) {
                val recent = sorted.filter { it.usageCount > 0 }.sortedByDescending { it.lastUsed }.take(5)
                if (recent.isNotEmpty()) {
                    add(Row2.Label("l-recent", R.string.pwd_recent))
                    recent.forEachIndexed { i, e -> add(Row2.Item("r-${e.uuid}", e, i, recent.size)) }
                    add(Row2.Label("l-all", R.string.pwd_all_entries))
                }
            }
            visible.forEachIndexed { i, e -> add(Row2.Item("a-${e.uuid}", e, i, visible.size)) }
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = topInset + 150.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if ((issues.isNotEmpty() || expiring.isNotEmpty()) && tab == Tab.All && query.isBlank()) {
                item(key = "banner") { FixBanner(issues.values, expiring.size) { tab = Tab.Fix } }
            }
            if (rows.none { it is Row2.Item }) {
                item(key = "empty") {
                    if (entries.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(EuriseIcons.key(), stringResource(R.string.pwd_empty_title), stringResource(R.string.pwd_empty_message))
                            ImportButton(vm)
                        }
                    }
                    else EmptyState(EuriseIcons.search(), stringResource(R.string.pwd_no_results), stringResource(R.string.pwd_no_results_message))
                }
            }
            itemsIndexed(rows, key = { _, r -> r.key }) { _, r ->
                when (r) {
                    is Row2.Label -> SectionLabel(stringResource(r.text), Modifier.padding(top = 10.dp, bottom = 8.dp))
                    is Row2.Item -> EntryRow(r.entry, issues[r.entry.uuid], now, r.index, r.size) { onOpen(r.entry.uuid) }
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass(haze, RectangleShape, elevation = 0.dp, outline = false).statusBarsPadding().padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val (syncText, syncTint) = syncLabel(sync)
            Row(Modifier.padding(start = 20.dp, end = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pwd_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(" ${entries.size}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.lock() }) { Icon(EuriseIcons.lock(), contentDescription = stringResource(R.string.pwd_lock_now)) }
            }
            // Lo stato della sincronizzazione sta qui; la ricerca è scesa in basso, vicino al pollice.
            Row(
                Modifier.padding(start = 20.dp, end = 16.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onSync).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(EuriseIcons.cloud(), contentDescription = null, tint = syncTint, modifier = Modifier.size(14.dp))
                Text(syncText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = syncTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.filter { it != Tab.Fix || issues.isNotEmpty() || expiring.isNotEmpty() || tab == Tab.Fix }.forEach { t ->
                    FilterPill(stringResource(t.label), dot = null, selected = tab == t) { tab = t }
                }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // La ricerca in basso (come nelle altre app della suite): si scrive subito, la ✕ svuota.
            Row(
                Modifier.weight(1f).height(52.dp).glass(haze, CircleShape).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(EuriseIcons.search(), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(stringResource(R.string.pwd_search_hint), fontSize = 15.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.pwd_cancel), modifier = Modifier.size(18.dp)) }
            }
            Box(Modifier.size(52.dp).glass(haze, CircleShape).clickable(onClick = { onAdd(tab) }), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.plus(), contentDescription = stringResource(R.string.pwd_add), tint = scheme.primary)
            }
        }
    }
}

@Composable
internal fun syncLabel(sync: SyncState): Pair<String, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (sync) {
        SyncState.Off -> stringResource(R.string.pwd_sync_off) to scheme.onSurfaceVariant
        SyncState.Working -> stringResource(R.string.pwd_sync_working) to scheme.onSurfaceVariant
        is SyncState.Done -> stringResource(R.string.pwd_sync_done) to scheme.secondary
        is SyncState.Pending -> stringResource(
            when (sync.reason) {
                SyncProblem.Network -> R.string.pwd_sync_network
                SyncProblem.Rejected -> R.string.pwd_sync_rejected
                SyncProblem.OtherPassword -> R.string.pwd_sync_other_password
            },
        ) to scheme.error
    }
}

@Composable
internal fun FixBanner(issues: Collection<HealthIssue>, expiring: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.errorContainer).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.shield(), contentDescription = null, tint = scheme.error, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(R.plurals.pwd_fix_title, issues.size + expiring, issues.size + expiring), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onErrorContainer)
            val breachedCount = issues.count { it.breached }
            if (breachedCount > 0) Text(stringResource(R.string.pwd_fix_breached, breachedCount), fontSize = 12.5.sp, color = scheme.onErrorContainer)
            if (issues.any { it.weak || it.reused }) Text(stringResource(R.string.pwd_fix_detail, issues.count { it.weak }, issues.count { it.reused }), fontSize = 12.5.sp, color = scheme.onErrorContainer)
            if (expiring > 0) Text(stringResource(R.string.pwd_fix_cards, expiring), fontSize = 12.5.sp, color = scheme.onErrorContainer)
        }
        Icon(EuriseIcons.chevronRight(), contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun EntryRow(entry: VaultEntry, issue: HealthIssue?, now: Long, index: Int, size: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val totp = remember(entry.otp) { Totp.parse(entry.otp) }
    val title = entry.title.ifBlank { stringResource(R.string.pwd_untitled) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(rowShape(index, size)).background(scheme.surfaceContainerLow).clickable(onClick = onClick).padding(horizontal = 14.dp)) {
        if (index > 0) CardDivider()
        Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(title, 36, 11, 14)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (detail, alert) = when {
                    issue?.breached == true -> stringResource(R.string.pwd_row_fix_breached) to true
                    issue?.weak == true -> stringResource(R.string.pwd_row_fix_weak) to true
                    issue?.reused == true -> stringResource(R.string.pwd_row_fix_reused) to true
                    entry.card != null -> stringResource(R.string.pwd_card_row, Cards.lastFour(entry.card.number)) to false
                    entry.profile != null -> pluralStringResource(R.plurals.pwd_profile_row, entry.profile.count { it.second.isNotBlank() }, entry.profile.count { it.second.isNotBlank() }) to false
                    totp != null -> stringResource(R.string.pwd_row_otp, entry.username) to false
                    entry.password.isEmpty() && entry.notes.isNotBlank() -> stringResource(R.string.pwd_row_note) to false
                    else -> entry.username to false
                }
                if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, fontWeight = if (alert) FontWeight.SemiBold else FontWeight.Normal, color = if (alert) scheme.error else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (totp != null) Text(Totp.display(totp.code(now)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        }
    }
}

// --- Scheda della voce ---
