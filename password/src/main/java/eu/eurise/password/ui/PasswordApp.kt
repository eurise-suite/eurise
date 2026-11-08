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
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.data.SyncProblem
import eu.eurise.password.data.SyncState
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.logic.Generator
import eu.eurise.password.logic.HealthIssue
import eu.eurise.password.logic.Strength
import eu.eurise.password.logic.Totp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private sealed interface Screen {
    data object List : Screen
    data class Detail(val uuid: String) : Screen
    data class Edit(val uuid: String?) : Screen
    data object Sync : Screen
}

private enum class Tab(val label: Int) { All(R.string.pwd_tab_all), Otp(R.string.pwd_tab_otp), Notes(R.string.pwd_tab_notes), Fix(R.string.pwd_tab_fix) }

@Composable
fun PasswordRoute(vm: PasswordViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(background)) {
        when (val s = state) {
            VaultState.Loading -> Unit
            VaultState.Empty -> SetupScreen(vm)
            VaultState.Locked -> UnlockScreen(vm)
            is VaultState.Open -> OpenVault(vm, s.entries)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

// --- Prima apertura e sblocco ---

@Composable
private fun SecretField(value: String, label: String, onChange: (String) -> Unit, visibleByDefault: Boolean = false) {
    var visible by remember { mutableStateOf(visibleByDefault) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) EuriseIcons.eyeOff() else EuriseIcons.eye(),
                    contentDescription = stringResource(if (visible) R.string.pwd_hide else R.string.pwd_show),
                )
            }
        },
    )
}

@Composable
private fun KeyMark() {
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(EuriseIcons.key(), contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun CenteredForm(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun StatusLines(vm: PasswordViewModel) {
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val waiting by vm.waitingBrowser.collectAsStateWithLifecycle()
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.pwd_working), fontSize = 14.sp)
        }
    }
    if (waiting) Text(stringResource(R.string.pwd_waiting_browser), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupScreen(vm: PasswordViewModel) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<Int?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it; first = ""; vm.clearError() }

    CenteredForm {
        KeyMark()
        Text(stringResource(R.string.pwd_setup_title), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val file = picked
        if (file != null) {
            Text(stringResource(R.string.pwd_import_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecretField(first, stringResource(R.string.pwd_master), { first = it })
            StatusLines(vm)
            Button(onClick = { vm.import(file, first) }, enabled = !busy && first.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.pwd_open)) }
            TextButton(onClick = { picked = null; first = "" }) { Text(stringResource(R.string.pwd_cancel)) }
        } else {
            Text(stringResource(R.string.pwd_setup_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecretField(first, stringResource(R.string.pwd_master), { first = it; local = null })
            SecretField(second, stringResource(R.string.pwd_master_again), { second = it; local = null })
            TextButton(onClick = { val s = Generator.passphrase(); first = s; second = s; local = null }) { Text(stringResource(R.string.pwd_suggest)) }
            local?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            StatusLines(vm)
            Button(
                onClick = {
                    when {
                        first.length < 10 -> local = R.string.pwd_too_short
                        first != second -> local = R.string.pwd_mismatch
                        else -> vm.create(first)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(stringResource(R.string.pwd_create)) }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.pwd_import)) }
            TextButton(onClick = { vm.fetchFromMurena { openUrl(context, it) } }) { Text(stringResource(R.string.pwd_from_murena)) }
        }
    }
}

@Composable
private fun UnlockScreen(vm: PasswordViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    CenteredForm {
        KeyMark()
        Text(stringResource(R.string.pwd_unlock_title), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        SecretField(password, stringResource(R.string.pwd_master), { password = it; vm.clearError() })
        StatusLines(vm)
        Button(onClick = { vm.unlock(password) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.pwd_open)) }
    }
}

// --- Cassaforte aperta ---

@Composable
private fun OpenVault(vm: PasswordViewModel, entries: List<VaultEntry>) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val back: () -> Unit = {
        screen = when (val s = screen) {
            is Screen.Edit -> s.uuid?.let { Screen.Detail(it) } ?: Screen.List
            else -> Screen.List
        }
    }
    BackHandler(enabled = screen != Screen.List, onBack = back)
    when (val s = screen) {
        Screen.List -> VaultList(vm, entries, onOpen = { vm.markUsed(it); screen = Screen.Detail(it) }, onAdd = { screen = Screen.Edit(null) }, onSync = { screen = Screen.Sync })
        is Screen.Detail -> {
            val entry = entries.firstOrNull { it.uuid == s.uuid }
            if (entry == null) LaunchedEffect(Unit) { screen = Screen.List }
            else EntryDetail(vm, entry, entries, back, onEdit = { screen = Screen.Edit(s.uuid) })
        }
        is Screen.Edit -> EntryEdit(entries.firstOrNull { it.uuid == s.uuid }, back) { draft ->
            vm.save(draft, s.uuid)
            back()
        }
        Screen.Sync -> SyncScreen(vm, back)
    }
}

/** L'ora che scorre: aggiorna i codici 2FA una volta al secondo. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    return now
}

private fun rowShape(index: Int, size: Int): Shape {
    val top = if (index == 0) 18.dp else 0.dp
    val bottom = if (index == size - 1) 18.dp else 0.dp
    return RoundedCornerShape(top, top, bottom, bottom)
}

private fun hostOf(url: String): String =
    url.trim().substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")

private sealed interface Row2 {
    val key: String
    data class Label(override val key: String, val text: Int) : Row2
    data class Item(override val key: String, val entry: VaultEntry, val index: Int, val size: Int) : Row2
}

@Composable
private fun VaultList(vm: PasswordViewModel, entries: List<VaultEntry>, onOpen: (String) -> Unit, onAdd: () -> Unit, onSync: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haze = remember { HazeState() }
    var tab by remember { mutableStateOf(Tab.All) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sync by vm.sync.collectAsStateWithLifecycle()
    val now = rememberNow()

    val issues = remember(entries) { Generator.health(entries.map { it.uuid to it.password }).associateBy { it.uuid } }
    val sorted = remember(entries) { entries.sortedBy { it.title.lowercase() } }
    val rows = remember(sorted, issues, tab, query) {
        val q = query.trim().lowercase()
        val visible = sorted.filter { e ->
            val inTab = when (tab) {
                Tab.All -> true
                Tab.Otp -> e.otp != null
                Tab.Notes -> e.password.isEmpty() && e.notes.isNotBlank()
                Tab.Fix -> e.uuid in issues
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
            contentPadding = PaddingValues(top = topInset + 122.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (issues.isNotEmpty() && tab == Tab.All && query.isBlank()) {
                item(key = "banner") { FixBanner(issues.values) { tab = Tab.Fix } }
            }
            if (rows.none { it is Row2.Item }) {
                item(key = "empty") {
                    if (entries.isEmpty()) EmptyState(EuriseIcons.key(), stringResource(R.string.pwd_empty_title), stringResource(R.string.pwd_empty_message))
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
            if (searching) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(stringResource(R.string.pwd_search_hint)) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp))
                    IconButton(onClick = { searching = false; query = "" }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.pwd_cancel)) }
                }
            } else {
                Row(Modifier.padding(start = 20.dp, end = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pwd_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(" ${entries.size}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) { Icon(EuriseIcons.search(), contentDescription = stringResource(R.string.pwd_search)) }
                    IconButton(onClick = { vm.lock() }) { Icon(EuriseIcons.lock(), contentDescription = stringResource(R.string.pwd_lock_now)) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.filter { it != Tab.Fix || issues.isNotEmpty() || tab == Tab.Fix }.forEach { t ->
                    FilterPill(stringResource(t.label), dot = null, selected = tab == t) { tab = t }
                }
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val (label, tint) = syncLabel(sync)
            Row(
                Modifier.weight(1f).height(52.dp).glass(haze, CircleShape).clickable(onClick = onSync).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(EuriseIcons.cloud(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(52.dp).glass(haze, CircleShape).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.plus(), contentDescription = stringResource(R.string.pwd_add), tint = scheme.primary)
            }
        }
    }
}

@Composable
private fun syncLabel(sync: SyncState): Pair<String, Color> {
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
private fun FixBanner(issues: Collection<HealthIssue>, onClick: () -> Unit) {
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
            Text(pluralStringResource(R.plurals.pwd_fix_title, issues.size, issues.size), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onErrorContainer)
            Text(stringResource(R.string.pwd_fix_detail, issues.count { it.weak }, issues.count { it.reused }), fontSize = 12.5.sp, color = scheme.onErrorContainer)
        }
        Icon(EuriseIcons.chevronRight(), contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Avatar(title: String, size: Int, corner: Int, fontSize: Int) {
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.errorContainer to scheme.onErrorContainer,
    )
    val (back, front) = tints[Math.floorMod(title.hashCode(), tints.size)]
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(corner.dp)).background(back), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase().ifEmpty { "?" }, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = front)
    }
}

@Composable
private fun EntryRow(entry: VaultEntry, issue: HealthIssue?, now: Long, index: Int, size: Int, onClick: () -> Unit) {
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
                    issue?.weak == true -> stringResource(R.string.pwd_row_fix_weak) to true
                    issue?.reused == true -> stringResource(R.string.pwd_row_fix_reused) to true
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

@Composable
private fun BackHeader(label: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.height(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.chevronLeft(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.height(36.dp).clip(CircleShape).background(scheme.primaryContainer).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimaryContainer)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, filled: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (filled) scheme.primaryContainer else Color.Transparent).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun FieldRow(label: String, value: String, hidden: Boolean = false, end: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.heightIn(min = 60.dp).padding(end = 2.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            if (hidden) {
                val mask = stringResource(R.string.pwd_hidden)
                Text("••••••••••••••••", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.semantics { contentDescription = mask })
            } else {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        end()
    }
}

@Composable
private fun strengthName(s: Strength) = stringResource(
    when (s) {
        Strength.Weak -> R.string.pwd_s_weak
        Strength.Fair -> R.string.pwd_s_fair
        Strength.Strong -> R.string.pwd_s_strong
        Strength.VeryStrong -> R.string.pwd_s_very
    },
)

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Column {
            Text(title, fontSize = 15.sp)
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OtpCard(vm: PasswordViewModel, otp: String) {
    val scheme = MaterialTheme.colorScheme
    val config = remember(otp) { Totp.parse(otp) }
    val now = rememberNow()
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(16.dp)) {
        if (config == null) {
            Text(stringResource(R.string.pwd_otp_invalid), color = scheme.error, fontSize = 14.sp)
        } else {
            val left = config.remaining(now)
            val code = config.code(now)
            val track = scheme.surfaceVariant
            val arc = scheme.secondary
            val ringLabel = stringResource(R.string.pwd_otp_remaining, left)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).semantics { contentDescription = ringLabel }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(42.dp)) {
                        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(track, 0f, 360f, false, Offset.Zero, Size(size.width, size.height), style = stroke)
                        drawArc(arc, -90f, 360f * left / config.period, false, Offset.Zero, Size(size.width, size.height), style = stroke)
                    }
                    Text("$left", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pwd_otp_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    Text(Totp.display(code), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_copy_code), filled = true) { vm.copy(code) }
            }
        }
    }
}

@Composable
private fun EntryDetail(vm: PasswordViewModel, entry: VaultEntry, all: List<VaultEntry>, onBack: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var reveal by remember(entry.uuid) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val strength = remember(entry.password) { Generator.strength(entry.password) }
    val reused = remember(entry, all) { entry.password.isNotEmpty() && all.count { it.password == entry.password } > 1 }
    val title = entry.title.ifBlank { stringResource(R.string.pwd_untitled) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack) { PillAction(stringResource(R.string.pwd_edit), onEdit) }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(title, 52, 16, 22)
            Column {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (entry.url.isNotBlank()) Text(stringResource(R.string.pwd_only_here, hostOf(entry.url)), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            }
        }
        if (entry.username.isNotEmpty() || entry.password.isNotEmpty()) {
            GroupCard {
                if (entry.username.isNotEmpty()) {
                    FieldRow(stringResource(R.string.pwd_user), entry.username) { RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_copy_user)) { vm.copy(entry.username) } }
                }
                if (entry.username.isNotEmpty() && entry.password.isNotEmpty()) CardDivider()
                if (entry.password.isNotEmpty()) {
                    FieldRow(stringResource(R.string.pwd_password_strength, strengthName(strength)), entry.password, hidden = !reveal) {
                        RoundIcon(if (reveal) EuriseIcons.eyeOff() else EuriseIcons.eye(), stringResource(if (reveal) R.string.pwd_hide else R.string.pwd_show)) { reveal = !reveal }
                        RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_copy_password)) { vm.copy(entry.password) }
                    }
                }
            }
        }
        entry.otp?.let { OtpCard(vm, it) }
        GroupCard {
            if (entry.password.isNotEmpty()) {
                val text = when {
                    strength == Strength.Weak -> R.string.pwd_health_weak
                    reused -> R.string.pwd_health_reused
                    else -> R.string.pwd_health_ok
                }
                InfoRow(EuriseIcons.shield(), stringResource(text), stringResource(R.string.pwd_health_note))
                CardDivider()
            }
            InfoRow(
                EuriseIcons.clock(),
                stringResource(R.string.pwd_changed, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modified))),
                pluralStringResource(R.plurals.pwd_history, entry.historyCount, entry.historyCount),
            )
        }
        if (entry.notes.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pwd_notes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Text(entry.notes, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.pwd_delete), color = scheme.error)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pwd_delete_title)) },
            text = { Text(stringResource(R.string.pwd_delete_message)) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.delete(entry.uuid); onBack() }) { Text(stringResource(R.string.pwd_delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.pwd_cancel)) } },
        )
    }
}

// --- Modifica ---

@Composable
private fun EntryEdit(entry: VaultEntry?, onBack: () -> Unit, onSave: (EntryDraft) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var user by remember { mutableStateOf(entry?.username.orEmpty()) }
    var phrase by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf(entry?.password ?: Generator.passphrase()) }
    var url by remember { mutableStateOf(entry?.url.orEmpty()) }
    var otp by remember { mutableStateOf(entry?.otp.orEmpty()) }
    var notes by remember { mutableStateOf(entry?.notes.orEmpty()) }
    val otpValid = otp.isBlank() || Totp.parse(otp) != null
    val strength = remember(password) { Generator.strength(password) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack) {
            PillAction(stringResource(R.string.pwd_save)) {
                if (otpValid) onSave(EntryDraft(title.trim(), user.trim(), password, url.trim(), notes, otp.trim().ifEmpty { null }))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (entry == null) stringResource(R.string.pwd_new) else entry.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.pwd_f_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.pwd_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            SecretField(password, stringResource(R.string.pwd_password), { password = it }, visibleByDefault = entry == null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.pwd_gen_phrase), dot = null, selected = phrase) { phrase = true; password = Generator.passphrase() }
                FilterPill(stringResource(R.string.pwd_gen_chars), dot = null, selected = !phrase) { phrase = false; password = Generator.password() }
                RoundIcon(EuriseIcons.refresh(), stringResource(R.string.pwd_generate), filled = true) { password = if (phrase) Generator.passphrase() else Generator.password() }
                Spacer(Modifier.width(2.dp))
                Text(strengthName(strength), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (strength == Strength.Weak) scheme.error else scheme.secondary)
            }
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.pwd_f_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(otp, { otp = it }, label = { Text(stringResource(R.string.pwd_f_otp)) }, singleLine = true, isError = !otpValid, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.pwd_notes)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- Sincronizzazione ---

@Composable
private fun SyncScreen(vm: PasswordViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sync by vm.sync.collectAsStateWithLifecycle()
    val waiting by vm.waitingBrowser.collectAsStateWithLifecycle()
    val on = sync !is SyncState.Off
    var explain by remember { mutableStateOf(false) }
    val (label, tint) = syncLabel(sync)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack)
        Text(stringResource(R.string.pwd_settings), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        GroupCard {
            Row(Modifier.heightIn(min = 64.dp).padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pwd_sync_switch), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val detail = if (on) stringResource(R.string.pwd_sync_on_account, vm.repo.syncAccountName.orEmpty()) else stringResource(R.string.pwd_sync_off_detail)
                    Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Switch(checked = on, enabled = !waiting, onCheckedChange = { if (it) explain = true else vm.disableSync() })
            }
            if (on) {
                CardDivider()
                Row(Modifier.heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
                    RoundIcon(EuriseIcons.refresh(), stringResource(R.string.pwd_sync_now)) { vm.syncNow() }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusLines(vm)
            Text(stringResource(R.string.pwd_sync_pc), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.pwd_privacy_title)) },
            text = { Text(stringResource(R.string.pwd_privacy_body)) },
            confirmButton = { TextButton(onClick = { explain = false; vm.enableSync { openUrl(context, it) } }) { Text(stringResource(R.string.pwd_privacy_ok)) } },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.pwd_cancel)) } },
        )
    }
}
