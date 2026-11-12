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

private sealed interface Screen {
    data object List : Screen
    data class Detail(val uuid: String) : Screen
    data class Edit(val uuid: String?, val card: Boolean = false, val prefill: EntryDraft? = null) : Screen
    data object Sync : Screen
    data class ProfileEdit(val uuid: String?) : Screen
}

private enum class Tab(val label: Int) { All(R.string.pwd_tab_all), Otp(R.string.pwd_tab_otp), Cards(R.string.pwd_tab_cards), MyData(R.string.pwd_tab_mydata), Notes(R.string.pwd_tab_notes), Fix(R.string.pwd_tab_fix) }

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
    var choosingCloud by remember { mutableStateOf(false) }
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
            TextButton(onClick = { choosingCloud = true }) { Text(stringResource(R.string.pwd_from_murena)) }
        }
    }
    if (choosingCloud) {
        CloudChoiceDialog(onDismiss = { choosingCloud = false }) { server ->
            choosingCloud = false
            vm.fetchFromCloud(server) { openUrl(context, it) }
        }
    }
}

/** Dove sta la cassaforte: Murena con un tocco, oppure un Nextcloud indicandone l'indirizzo. [onChoice] riceve null per Murena. */
@Composable
private fun CloudChoiceDialog(onDismiss: () -> Unit, onChoice: (String?) -> Unit) {
    var server by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pwd_cloud_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onChoice(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pwd_cloud_murena)) }
                Text(stringResource(R.string.pwd_cloud_or), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(server, { server = it }, singleLine = true, label = { Text(stringResource(R.string.pwd_cloud_server)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = server.isNotBlank(), onClick = { onChoice(server) }) { Text(stringResource(R.string.pwd_cloud_nextcloud)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pwd_cancel)) } },
    )
}

/** Con [compact] sta dentro il foglio di compilazione invece di occupare lo schermo. */
@Composable
internal fun UnlockScreen(vm: PasswordViewModel, compact: Boolean = false) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val bioOn by vm.bioOn.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (bioOn) vm.unlockWithBio(context) }
    val form: @Composable () -> Unit = {
        if (!compact) KeyMark()
        Text(stringResource(R.string.pwd_unlock_title), fontSize = if (compact) 18.sp else 26.sp, fontWeight = FontWeight.Bold)
        SecretField(password, stringResource(R.string.pwd_master), { password = it; vm.clearError() })
        StatusLines(vm)
        Button(onClick = { vm.unlock(password) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.pwd_open)) }
        if (bioOn) TextButton(onClick = { vm.unlockWithBio(context) }) { Text(stringResource(R.string.pwd_bio_use)) }
    }
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { form() }
    } else {
        CenteredForm(form)
    }
}

// --- Cassaforte aperta ---

@Composable
private fun OpenVault(vm: PasswordViewModel, entries: List<VaultEntry>) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    val back: () -> Unit = {
        screen = when (val s = screen) {
            is Screen.Edit -> s.uuid?.let { Screen.Detail(it) } ?: Screen.List
            is Screen.ProfileEdit -> s.uuid?.let { Screen.Detail(it) } ?: Screen.List
            else -> Screen.List
        }
    }
    LaunchedEffect(Unit) { vm.checkBreachesIfDue() }
    val pendingOtp by vm.repo.pendingOtp.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOtp) {
        val uri = pendingOtp ?: return@LaunchedEffect
        vm.repo.pendingOtp.value = null
        val ready = Importer.parse(uri) as? ImportOutcome.Ready ?: return@LaunchedEffect
        if (uri.startsWith("otpauth-migration", ignoreCase = true)) vm.showImport(ready)
        else ready.items.firstOrNull()?.let { screen = Screen.Edit(null, prefill = it.draft) }
    }
    val pendingEntry by vm.repo.pendingEntry.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        val draft = pendingEntry ?: return@LaunchedEffect
        vm.repo.pendingEntry.value = null
        screen = Screen.Edit(null, prefill = draft)
    }
    val pendingImport by vm.repo.pendingImport.collectAsStateWithLifecycle()
    LaunchedEffect(pendingImport) {
        val file = pendingImport ?: return@LaunchedEffect
        vm.repo.pendingImport.value = null
        vm.readImport(file)
    }
    ImportDialogs(vm)
    BackHandler(enabled = screen != Screen.List, onBack = back)
    when (val s = screen) {
        Screen.List -> VaultList(vm, entries, onOpen = { vm.markUsed(it); screen = Screen.Detail(it) }, onAdd = { t -> screen = if (t == Tab.MyData) Screen.ProfileEdit(null) else Screen.Edit(null, t == Tab.Cards) }, onSync = { screen = Screen.Sync })
        is Screen.Detail -> {
            val entry = entries.firstOrNull { it.uuid == s.uuid }
            if (entry == null) LaunchedEffect(Unit) { screen = Screen.List }
            else if (entry.card != null) CardDetail(vm, entry, back, onEdit = { screen = Screen.Edit(s.uuid, true) })
            else if (entry.profile != null) ProfileDetail(vm, entry, back, onEdit = { screen = Screen.ProfileEdit(s.uuid) })
            else EntryDetail(vm, entry, entries, back, onEdit = { screen = Screen.Edit(s.uuid) })
        }
        is Screen.Edit -> EntryEdit(entries.firstOrNull { it.uuid == s.uuid }, s.card, s.prefill, back) { draft ->
            vm.save(draft, s.uuid)
            back()
        }
        Screen.Sync -> SyncScreen(vm, back)
        is Screen.ProfileEdit -> ProfileEdit(entries.firstOrNull { it.uuid == s.uuid }, back) { draft ->
            vm.save(draft, s.uuid)
            back()
        }
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
private fun VaultList(vm: PasswordViewModel, entries: List<VaultEntry>, onOpen: (String) -> Unit, onAdd: (Tab) -> Unit, onSync: () -> Unit) {
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
private fun FixBanner(issues: Collection<HealthIssue>, expiring: Int, onClick: () -> Unit) {
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
internal fun RoundIcon(icon: ImageVector, description: String, filled: Boolean = false, onClick: () -> Unit) {
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
internal fun strengthName(s: Strength) = stringResource(
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
                val breaches by vm.breaches.collectAsStateWithLifecycle()
                val breachOn by vm.breachOn.collectAsStateWithLifecycle()
                val last by vm.breachLast.collectAsStateWithLifecycle()
                if (breachOn && last > 0) {
                    if (breaches[entry.uuid] == entry.modified) InfoRow(EuriseIcons.shield(), stringResource(R.string.pwd_breach_found), stringResource(R.string.pwd_breach_found_detail))
                    else InfoRow(EuriseIcons.shield(), stringResource(R.string.pwd_breach_clean), stringResource(R.string.pwd_breach_clean_detail, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last))))
                    CardDivider()
                }
            }
            entry.passkey?.let { key ->
                InfoRow(EuriseIcons.key(), stringResource(R.string.pwd_passkey_title), stringResource(R.string.pwd_passkey_detail, key.rpId))
                CardDivider()
            }
            InfoRow(
                EuriseIcons.clock(),
                stringResource(R.string.pwd_changed, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modified))),
                if (entry.historyCount == 0) stringResource(R.string.pwd_history_none) else pluralStringResource(R.plurals.pwd_history, entry.historyCount, entry.historyCount),
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

/** Il circuito dalla prima cifra, come titolo proposto quando la carta si legge (si può cambiare). */
private fun cardBrand(number: String): String {
    val d = number.filter { it.isDigit() }
    return when {
        d.startsWith("4") -> "Visa"
        (d.take(2).toIntOrNull() ?: 0) in 51..55 || (d.take(4).toIntOrNull() ?: 0) in 2221..2720 -> "Mastercard"
        d.startsWith("34") || d.startsWith("37") -> "American Express"
        d.startsWith("5") || d.startsWith("6") -> "Maestro"
        else -> ""
    }
}

@Composable
private fun EntryEdit(entry: VaultEntry?, startAsCard: Boolean, prefill: EntryDraft?, onBack: () -> Unit, onSave: (EntryDraft) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(entry?.title ?: prefill?.title.orEmpty()) }
    var user by remember { mutableStateOf(entry?.username ?: prefill?.username.orEmpty()) }
    var phrase by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf(entry?.password ?: prefill?.password ?: Generator.passphrase()) }
    var url by remember { mutableStateOf(entry?.url ?: prefill?.url.orEmpty()) }
    var otp by remember { mutableStateOf(entry?.otp ?: prefill?.otp.orEmpty()) }
    var notes by remember { mutableStateOf(entry?.notes ?: prefill?.notes.orEmpty()) }
    var isCard by remember { mutableStateOf(startAsCard || entry?.card != null) }
    var number by remember { mutableStateOf(entry?.card?.number.orEmpty()) }
    var holder by remember { mutableStateOf(entry?.card?.holder.orEmpty()) }
    var expiry by remember { mutableStateOf(entry?.card?.expiry.orEmpty()) }
    var cvv by remember { mutableStateOf(entry?.card?.cvv.orEmpty()) }
    var type by remember { mutableStateOf(entry?.card?.type.orEmpty()) }
    val otpValid = otp.isBlank() || Totp.parse(otp) != null
    val strength = remember(password) { Generator.strength(password) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack) {
            PillAction(stringResource(R.string.pwd_save)) {
                when {
                    isCard -> if (number.isNotBlank()) onSave(EntryDraft(title.trim(), "", "", "", notes, null, CardData(number.trim(), holder.trim(), expiry.trim(), cvv.trim(), type.trim())))
                    otpValid -> onSave(EntryDraft(title.trim(), user.trim(), password, url.trim(), notes, otp.trim().ifEmpty { null }))
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (entry == null) stringResource(R.string.pwd_new) else entry.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (entry == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(stringResource(R.string.pwd_card_kind_login), dot = null, selected = !isCard) { isCard = false }
                    FilterPill(stringResource(R.string.pwd_card_kind_card), dot = null, selected = isCard) { isCard = true }
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.pwd_f_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (isCard) {
                CardReaders { n, e, h ->
                    if (n.isNotBlank()) number = n
                    if (e.isNotBlank()) expiry = e
                    if (h.isNotBlank()) holder = h
                    if (title.isBlank() && n.isNotBlank()) title = cardBrand(n)
                }
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.pwd_card_number)) }, singleLine = true, isError = number.isNotBlank() && !Cards.valid(number), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (number.isNotBlank() && !Cards.valid(number)) Text(stringResource(R.string.pwd_card_invalid), fontSize = 12.sp, color = scheme.error)
                OutlinedTextField(holder, { holder = it }, label = { Text(stringResource(R.string.pwd_card_holder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(expiry, { expiry = it }, label = { Text(stringResource(R.string.pwd_card_expiry)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SecretField(cvv, stringResource(R.string.pwd_card_cvv), { cvv = it })
                OutlinedTextField(type, { type = it }, label = { Text(stringResource(R.string.pwd_card_type)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
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
            }
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
    var choosingCloud by remember { mutableStateOf(false) }
    val waiting by vm.waitingBrowser.collectAsStateWithLifecycle()
    val on = sync !is SyncState.Off
    var explain by remember { mutableStateOf(false) }
    val (label, tint) = syncLabel(sync)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack)
        Text(stringResource(R.string.pwd_settings), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        val bioOn by vm.bioOn.collectAsStateWithLifecycle()
        GroupCard {
            Row(Modifier.heightIn(min = 64.dp).padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pwd_bio_switch), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (vm.bio.available) R.string.pwd_bio_detail else R.string.pwd_bio_unavailable), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Switch(checked = bioOn, enabled = vm.bio.available, onCheckedChange = { if (it) vm.enableBio(context) else vm.disableBio() })
            }
            CardDivider()
            val breachOn by vm.breachOn.collectAsStateWithLifecycle()
            val breachLast by vm.breachLast.collectAsStateWithLifecycle()
            val breachFailed by vm.breachFailed.collectAsStateWithLifecycle()
            var explainBreach by remember { mutableStateOf(false) }
            Row(Modifier.heightIn(min = 64.dp).padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pwd_breach_switch), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val lastText = if (breachLast > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(breachLast)) else stringResource(R.string.pwd_breach_never)
                    Text(if (breachOn) stringResource(R.string.pwd_breach_on, lastText) else stringResource(R.string.pwd_breach_off), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    if (breachOn && breachFailed) Text(stringResource(R.string.pwd_breach_failed), fontSize = 12.sp, color = scheme.error)
                }
                if (breachOn) RoundIcon(EuriseIcons.refresh(), stringResource(R.string.pwd_breach_now)) { vm.checkBreaches() }
                Switch(checked = breachOn, onCheckedChange = { if (it) explainBreach = true else vm.setBreach(false) })
            }
            if (explainBreach) {
                AlertDialog(
                    onDismissRequest = { explainBreach = false },
                    title = { Text(stringResource(R.string.pwd_privacy_title)) },
                    text = { Text(stringResource(R.string.pwd_breach_privacy)) },
                    confirmButton = { TextButton(onClick = { explainBreach = false; vm.setBreach(true) }) { Text(stringResource(R.string.pwd_privacy_ok)) } },
                    dismissButton = { TextButton(onClick = { explainBreach = false }) { Text(stringResource(R.string.pwd_cancel)) } },
                )
            }
            CardDivider()
            val autofill = remember(lifecycleTick(), context) { context.getSystemService(android.view.autofill.AutofillManager::class.java)?.hasEnabledAutofillServices() == true }
            Row(Modifier.heightIn(min = 64.dp).padding(end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pwd_section_fill), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (autofill) R.string.pwd_fill_active else R.string.pwd_fill_inactive), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                if (!autofill) PillAction(stringResource(R.string.pwd_fill_enable)) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:" + context.packageName)))
                }
            }
        }
        SectionLabel(stringResource(R.string.pwd_section_sync))
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
        SectionLabel(stringResource(R.string.pwd_import_title))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pwd_import_help), fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
            ImportButton(vm)
        }
        AliasSettings(vm)
        Spacer(Modifier.height(24.dp))
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            title = { Text(stringResource(R.string.pwd_privacy_title)) },
            text = { Text(stringResource(R.string.pwd_privacy_body)) },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    if (vm.hasSyncAccount) vm.enableSync(null) { openUrl(context, it) } else choosingCloud = true
                }) { Text(stringResource(R.string.pwd_privacy_ok)) }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text(stringResource(R.string.pwd_cancel)) } },
        )
    }
    if (choosingCloud) {
        CloudChoiceDialog(onDismiss = { choosingCloud = false }) { server ->
            choosingCloud = false
            vm.enableSync(server) { openUrl(context, it) }
        }
    }
}

/** Cambia ogni volta che l'app torna in primo piano: per rileggere le scelte fatte nelle impostazioni di Android. */
@Composable
private fun lifecycleTick(): Int {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
private fun AliasSettings(vm: PasswordViewModel) {
    val scheme = MaterialTheme.colorScheme
    val selected by vm.alias.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<AliasProvider?>(null) }
    var secret by remember(selected) { mutableStateOf(if (selected == AliasProvider.Fixed) vm.aliases.fixedAddress.orEmpty() else "") }
    var kmailHosting by remember { mutableStateOf("") }
    var kmailBox by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var linked by remember(selected) { mutableStateOf(vm.aliases.connected(selected)) }

    val options = listOf(
        Triple(AliasProvider.SimpleLogin, R.string.pwd_alias_sl, R.string.pwd_alias_sl_d),
        Triple(AliasProvider.Addy, R.string.pwd_alias_addy, R.string.pwd_alias_addy_d),
        Triple(AliasProvider.Fixed, R.string.pwd_alias_fixed, R.string.pwd_alias_fixed_d),
        Triple(AliasProvider.None, R.string.pwd_alias_none, R.string.pwd_alias_none_d),
    )
    SectionLabel(stringResource(R.string.pwd_alias_title))
    Text(stringResource(R.string.pwd_alias_intro), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
    GroupCard {
        options.forEachIndexed { i, (provider, title, detail) ->
            if (i > 0) CardDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable {
                    message = null
                    // I servizi esterni passano dalla spiegazione; tornare a Nessuno o all'indirizzo fisso non fa uscire nulla.
                    if (provider == AliasProvider.SimpleLogin || provider == AliasProvider.Addy || provider == AliasProvider.KMail) pending = provider else vm.selectAlias(provider)
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.RadioButton(selected = selected == provider, onClick = null)
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(title), fontSize = 15.sp, fontWeight = if (selected == provider) FontWeight.Bold else FontWeight.SemiBold)
                    Text(stringResource(detail), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
    if (selected != AliasProvider.None) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = stringResource(
                when (selected) {
                    AliasProvider.SimpleLogin -> R.string.pwd_alias_key_sl
                    AliasProvider.Addy -> R.string.pwd_alias_key_addy
                    AliasProvider.KMail -> R.string.pwd_alias_key_kmail
                    else -> R.string.pwd_alias_address
                },
            )
            if (selected == AliasProvider.Fixed) OutlinedTextField(secret, { secret = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            else SecretField(secret, label, { secret = it })
            if (selected == AliasProvider.KMail) {
                OutlinedTextField(kmailHosting, { kmailHosting = it }, label = { Text(stringResource(R.string.pwd_alias_kmail_hosting)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(kmailBox, { kmailBox = it }, label = { Text(stringResource(R.string.pwd_alias_kmail_box)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message ?: if (linked) stringResource(R.string.pwd_alias_connected) else "",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (message != null) scheme.error else scheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                PillAction(stringResource(R.string.pwd_alias_connect)) {
                    val value = if (selected == AliasProvider.KMail) "${secret.trim()}\n${kmailHosting.trim()}\n${kmailBox.trim()}" else secret
                    if (secret.isNotBlank()) vm.connectAlias(selected, value) { error -> message = error; linked = error == null }
                }
            }
        }
    }
    pending?.let { provider ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.pwd_privacy_title)) },
            text = { Text(stringResource(R.string.pwd_alias_privacy)) },
            confirmButton = { TextButton(onClick = { pending = null; vm.selectAlias(provider) }) { Text(stringResource(R.string.pwd_privacy_ok)) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.pwd_cancel)) } },
        )
    }
}

// --- Carta ---

@Composable
private fun CardDetail(vm: PasswordViewModel, entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val card = entry.card ?: return
    val context = LocalContext.current
    var showNumber by remember(entry.uuid) { mutableStateOf(false) }
    var showCvv by remember(entry.uuid) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val masked = "•••• •••• •••• " + Cards.lastFour(card.number)
    val months = remember(card.expiry) { Cards.monthsLeft(card.expiry, LocalDate.now()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_tab_cards), onBack) { PillAction(stringResource(R.string.pwd_edit), onEdit) }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(196.dp).clip(RoundedCornerShape(20.dp)).background(scheme.primary).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title.ifBlank { stringResource(R.string.pwd_untitled) }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(card.type.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.tertiaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(40.dp, 29.dp).clip(RoundedCornerShape(7.dp)).background(scheme.tertiaryContainer))
            Spacer(Modifier.weight(1f))
            Text(masked, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = scheme.onPrimary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(card.holder.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimary, maxLines = 1)
                Text(card.expiry, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimary)
            }
        }
        GroupCard {
            FieldRow(stringResource(R.string.pwd_card_number), if (showNumber) card.number else masked) {
                RoundIcon(if (showNumber) EuriseIcons.eyeOff() else EuriseIcons.eye(), stringResource(R.string.pwd_card_show)) { if (showNumber) showNumber = false else vm.confirmSensitive(context) { showNumber = true } }
                RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_card_copy)) { vm.confirmSensitive(context) { vm.copy(card.number.filter { it.isDigit() }) } }
            }
            if (card.cvv.isNotEmpty()) {
                CardDivider()
                FieldRow(stringResource(R.string.pwd_card_cvv), if (showCvv) card.cvv else "•••") {
                    RoundIcon(if (showCvv) EuriseIcons.eyeOff() else EuriseIcons.eye(), stringResource(R.string.pwd_card_show_cvv)) { if (showCvv) showCvv = false else vm.confirmSensitive(context) { showCvv = true } }
                    RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_card_copy_cvv)) { vm.confirmSensitive(context) { vm.copy(card.cvv) } }
                }
            }
        }
        GroupCard {
            InfoRow(EuriseIcons.shield(), stringResource(R.string.pwd_card_fill), stringResource(R.string.pwd_card_fill_detail))
            if (months != null) {
                CardDivider()
                InfoRow(
                    EuriseIcons.clock(),
                    if (months < 0) stringResource(R.string.pwd_card_expired) else pluralStringResource(R.plurals.pwd_card_months, months, months),
                    stringResource(R.string.pwd_card_months_detail),
                )
            }
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

/** Un solo pulsante: il tipo di file si riconosce da solo, e prima di importare si vede che cosa contiene. */
@Composable
private fun ImportButton(vm: PasswordViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }
    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.pwd_import_action)) }
}

/** Sta sopra ogni schermata, perché un file può arrivare da fuori mentre si è altrove nell'app. */
@Composable
private fun ImportDialogs(vm: PasswordViewModel) {
    val stage by vm.importing.collectAsStateWithLifecycle()
    val dismiss = vm::closeImport
    when (val s = stage) {
        null -> Unit
        is ImportStage.Preview -> {
            fun count(kind: ImportKind) = s.ready.items.count { it.kind == kind }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.pwd_import_found)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.pwd_import_counts, count(ImportKind.Login), count(ImportKind.Card), count(ImportKind.Note), count(ImportKind.Code)))
                        if (s.ready.passkeys > 0) Text(stringResource(R.string.pwd_import_passkeys, s.ready.passkeys))
                        if (s.ready.skipped > 0) Text(stringResource(R.string.pwd_import_skipped, s.ready.skipped))
                    }
                },
                confirmButton = { TextButton(onClick = { vm.confirmImport(s.ready) }) { Text(stringResource(R.string.pwd_import_confirm)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.pwd_cancel)) } },
            )
        }
        is ImportStage.Keepass -> {
            var password by remember(s) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.pwd_import_keepass)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecretField(password, stringResource(R.string.pwd_master), { password = it })
                        if (s.failed) Text(stringResource(R.string.pwd_err_wrong), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }
                },
                confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { vm.readKeepass(s.bytes, password) }) { Text(stringResource(R.string.pwd_open)) } },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.pwd_cancel)) } },
            )
        }
        is ImportStage.Done -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.pwd_import_done)) },
            text = { Text(stringResource(R.string.pwd_import_done_body, s.counts.added, s.counts.attached, s.counts.duplicates)) },
            confirmButton = {
                if (vm.canDeleteImportedFile) TextButton(onClick = { vm.deleteImportedFile(); dismiss() }) { Text(stringResource(R.string.pwd_import_delete)) }
                else TextButton(onClick = dismiss) { Text(stringResource(R.string.pwd_import_ok)) }
            },
            dismissButton = if (vm.canDeleteImportedFile) ({ TextButton(onClick = dismiss) { Text(stringResource(R.string.pwd_import_keep)) } }) else null,
        )
        ImportStage.Encrypted -> InfoDialog(R.string.pwd_import_encrypted_title, stringResource(R.string.pwd_import_encrypted), dismiss)
        ImportStage.Unreadable -> InfoDialog(R.string.pwd_import_unknown_title, stringResource(R.string.pwd_import_unknown), dismiss)
    }
}

@Composable
private fun InfoDialog(title: Int, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.pwd_import_ok)) } },
    )
}

// --- I miei dati ---

@Composable
private fun profileSectionName(section: eu.eurise.password.logic.Profiles.Section) = stringResource(
    when (section) {
        eu.eurise.password.logic.Profiles.Section.Person -> R.string.pwd_profile_person
        eu.eurise.password.logic.Profiles.Section.Work -> R.string.pwd_profile_work
        eu.eurise.password.logic.Profiles.Section.Address -> R.string.pwd_profile_address
    },
)

/**
 * Un profilo dei miei dati. I valori compaiono solo dopo l'impronta o il PIN e restano aperti per 2 minuti; i dati
 * riservati (codice fiscale, IBAN, documento e quelli aggiunti da te) restano coperti finché non si toccano.
 */
@Composable
private fun ProfileDetail(vm: PasswordViewModel, entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val profiles = eu.eurise.password.logic.Profiles
    val gate = eu.eurise.password.data.SensitiveGate
    val fields = entry.profile.orEmpty()
    var open by remember { mutableStateOf(gate.isOpen()) }
    var left by remember { mutableIntStateOf(gate.remainingSeconds()) }
    var revealed by remember(entry.uuid) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(open) {
        while (open) {
            left = gate.remainingSeconds()
            if (left == 0) { open = false; revealed = emptySet() }
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { if (!open) vm.confirmSensitive(context) { open = true } }
    val title = entry.title.ifBlank { stringResource(R.string.pwd_profile_default) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_tab_mydata), onBack) { if (open) PillAction(stringResource(R.string.pwd_edit), onEdit) }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(title, 52, 16, 22)
            Column {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (open) stringResource(R.string.pwd_profile_open_until, left) else stringResource(R.string.pwd_profile_closed),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (open) scheme.secondary else scheme.onSurfaceVariant,
                )
            }
        }
        if (!open) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.pwd_profile_locked), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = { vm.confirmSensitive(context) { open = true } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(stringResource(R.string.pwd_profile_unlock), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            if (fields.none { it.second.isNotBlank() }) {
                Text(stringResource(R.string.pwd_profile_empty), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            val toggle: (String) -> Unit = { key -> revealed = if (key in revealed) revealed - key else revealed + key }
            val copy: (String) -> Unit = { value -> vm.confirmSensitive(context) { vm.copy(value) } }
            val values = fields.toMap()
            eu.eurise.password.logic.Profiles.Section.entries.forEach { section ->
                val list = profiles.known.filter { it.section == section }.mapNotNull { k -> values[k.key]?.takeIf { it.isNotBlank() }?.let { k.key to it } }
                if (list.isNotEmpty()) {
                    SectionLabel(profileSectionName(section), Modifier.padding(top = 4.dp))
                    ProfileRows(list, revealed, toggle, copy)
                }
            }
            val custom = profiles.custom(fields).filter { it.second.isNotBlank() }
            if (custom.isNotEmpty()) {
                SectionLabel(stringResource(R.string.pwd_profile_custom), Modifier.padding(top = 4.dp))
                ProfileRows(custom, revealed, toggle, copy)
            }
        }
    }
}

@Composable
private fun ProfileRows(list: List<Pair<String, String>>, revealed: Set<String>, onToggle: (String) -> Unit, onCopy: (String) -> Unit) {
    GroupCard {
        list.forEachIndexed { i, (key, value) ->
            if (i > 0) CardDivider()
            val sensitive = eu.eurise.password.logic.Profiles.isSensitive(key)
            val shown = !sensitive || key in revealed
            FieldRow(key, value, hidden = !shown) {
                if (sensitive) {
                    RoundIcon(if (shown) EuriseIcons.eyeOff() else EuriseIcons.eye(), stringResource(if (shown) R.string.pwd_hide else R.string.pwd_show)) { onToggle(key) }
                }
                RoundIcon(EuriseIcons.copy(), stringResource(R.string.pwd_profile_copy, key)) { onCopy(value) }
            }
        }
    }
}

/** Modifica di un profilo: i campi noti divisi per sezione e, in fondo, i dati aggiunti da te con titolo e valore. */
@Composable
private fun ProfileEdit(entry: VaultEntry?, onBack: () -> Unit, onSave: (EntryDraft) -> Unit) {
    val profiles = eu.eurise.password.logic.Profiles
    val start = entry?.profile.orEmpty()
    val defaultName = stringResource(R.string.pwd_profile_default)
    var name by remember { mutableStateOf(entry?.title.orEmpty()) }
    val values = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(start.filter { profiles.isKnown(it.first) }) } }
    val custom = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>().apply { addAll(profiles.custom(start)) } }

    fun save() {
        val known = profiles.known.mapNotNull { k -> values[k.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { k.key to it } }
        val extra = custom.map { it.first.trim() to it.second.trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() && !profiles.isKnown(it.first) }
            .distinctBy { it.first }
        onSave(EntryDraft(name.trim().ifEmpty { defaultName }, "", "", "", "", null, profile = known + extra))
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack) { PillAction(stringResource(R.string.pwd_save), ::save) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (entry == null) stringResource(R.string.pwd_profile_new) else entry.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pwd_profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            eu.eurise.password.logic.Profiles.Section.entries.forEach { section ->
                SectionLabel(profileSectionName(section), Modifier.padding(top = 6.dp))
                profiles.known.filter { it.section == section }.forEach { k ->
                    val type = when (k.key) {
                        eu.eurise.password.logic.Profiles.PHONE -> KeyboardType.Phone
                        eu.eurise.password.logic.Profiles.EMAIL, eu.eurise.password.logic.Profiles.PEC -> KeyboardType.Email
                        eu.eurise.password.logic.Profiles.POSTAL_CODE -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }
                    OutlinedTextField(
                        values[k.key].orEmpty(), { values[k.key] = it },
                        label = { Text(k.key) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = type),
                    )
                }
            }
            SectionLabel(stringResource(R.string.pwd_profile_custom), Modifier.padding(top = 6.dp))
            custom.forEachIndexed { i, (title, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { custom[i] = it to custom[i].second }, label = { Text(stringResource(R.string.pwd_profile_field_title)) }, singleLine = true, modifier = Modifier.weight(0.42f))
                    OutlinedTextField(value, { custom[i] = custom[i].first to it }, label = { Text(stringResource(R.string.pwd_profile_field_value)) }, singleLine = true, modifier = Modifier.weight(0.58f))
                    IconButton(onClick = { custom.removeAt(i) }) { Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.pwd_profile_remove)) }
                }
            }
            TextButton(onClick = { custom.add("" to "") }) {
                Icon(EuriseIcons.plus(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pwd_profile_add_field), fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.pwd_profile_custom_note), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
