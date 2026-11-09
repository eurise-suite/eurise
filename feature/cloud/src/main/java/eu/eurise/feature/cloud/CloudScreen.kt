package eu.eurise.feature.cloud

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavQuota
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.LocalEuriseColors
import java.util.Locale

private enum class Page { Accounts, Sync }

private enum class Form { Nextcloud, KDrive, WebDav }

/** Account cloud, cosa va dove e sincronizzazione, come nelle anteprime della pagina "Cloud". */
@Composable
fun CloudRoute(onBack: () -> Unit, onBackup: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel = viewModel { CloudViewModel(CloudSync.get(context.applicationContext)) }
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableStateOf(Page.Accounts) }
    var connectSheet by rememberSaveable { mutableStateOf(false) }
    var form by rememberSaveable { mutableStateOf<Form?>(null) }
    var pickNotes by rememberSaveable { mutableStateOf(false) }
    var askTasks by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (page == Page.Sync) page = Page.Accounts else onBack() }

    // La pagina di accesso di Murena o Nextcloud si apre nel browser.
    val browser = ui.connecting as? Connecting.Browser
    LaunchedEffect(browser?.url) {
        browser?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) } }
    }

    when (page) {
        Page.Accounts -> Screen(stringResource(R.string.cloud_back), stringResource(R.string.cloud_title), onBack) {
            if (accounts.isEmpty()) {
                item("intro") {
                    Text(
                        stringResource(R.string.cloud_intro),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            accounts.forEach { account ->
                item(account.id) { AccountCard(account, ui.quotas[account.id]) { viewModel.remove(account.id) } }
            }
            item("backup") {
                // Terzo livello dei dati: il backup cifrato di tutta la suite, anche senza account collegati.
                val backup by remember { eu.eurise.core.cloud.backup.BackupManager.get(context) }.state.collectAsStateWithLifecycle()
                Group {
                    WhereRow(
                        stringResource(R.string.backup_title),
                        backupStatus(backup),
                        "",
                        onClick = onBackup,
                    )
                }
            }
            if (accounts.isNotEmpty()) {
                item("h-where") { Label(stringResource(R.string.cloud_where)) }
                item("where") {
                    Group {
                        val notesAccount = accounts.firstOrNull { it.id == settings.notesAccount }
                        WhereRow(
                            stringResource(R.string.cloud_where_notes),
                            stringResource(R.string.cloud_where_notes_hint),
                            notesAccount?.let { kindLabel(it.kind) } ?: stringResource(R.string.cloud_only_phone),
                            onClick = { pickNotes = true },
                        )
                        Divider()
                        WhereRow(
                            stringResource(R.string.cloud_where_tasks),
                            stringResource(R.string.cloud_where_tasks_hint),
                            notesAccount?.takeIf { settings.tasksEnabled }?.let { kindLabel(it.kind) } ?: stringResource(R.string.cloud_only_phone),
                        )
                        Divider()
                        WhereRow(stringResource(R.string.cloud_where_calendar), stringResource(R.string.cloud_where_calendar_hint), stringResource(R.string.cloud_system))
                        Divider()
                        WhereRow(stringResource(R.string.cloud_where_photos), stringResource(R.string.cloud_where_photos_hint), stringResource(R.string.cloud_soon))
                    }
                }
                item("sync") {
                    Group {
                        WhereRow(
                            stringResource(R.string.cloud_sync_title),
                            statusLine(settings.lastSync, settings.lastError, running),
                            "",
                            onClick = { page = Page.Sync },
                        )
                    }
                }
            }
            item("connect") {
                TextButton(
                    onClick = { connectSheet = true },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(EuriseIcons.plus(), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (accounts.isEmpty()) R.string.cloud_connect else R.string.cloud_connect_another),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Page.Sync -> Screen(stringResource(R.string.cloud_title), stringResource(R.string.cloud_sync_title), { page = Page.Accounts }) {
            item("status") {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ok = settings.lastError == null
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (ok) tintFill("Salvia") else tintFill("Borgogna")),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(if (ok) EuriseIcons.check() else EuriseIcons.close(), contentDescription = null, tint = tintInk(if (ok) "Salvia" else "Borgogna"), modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                when {
                                    running -> R.string.cloud_status_running
                                    !ok -> R.string.cloud_status_error
                                    settings.lastSync == null -> R.string.cloud_status_never
                                    else -> R.string.cloud_status_ok
                                }
                            ),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(statusLine(settings.lastSync, settings.lastError, running), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = viewModel::syncNow,
                        enabled = !running,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).height(36.dp),
                    ) { Text(stringResource(R.string.cloud_sync_now), fontWeight = FontWeight.SemiBold) }
                }
            }
            item("h-apps") { Label(stringResource(R.string.cloud_apps)) }
            item("apps") {
                Group {
                    SwitchRow(
                        stringResource(R.string.cloud_app_notes),
                        accounts.firstOrNull { it.id == settings.notesAccount }?.let { stringResource(R.string.cloud_app_notes_hint, kindLabel(it.kind)) }
                            ?: stringResource(R.string.cloud_only_phone),
                        settings.notesEnabled,
                        viewModel::setNotesEnabled,
                    )
                    Divider()
                    SwitchRow(
                        stringResource(R.string.cloud_app_tasks),
                        accounts.firstOrNull { it.id == settings.notesAccount }?.let { stringResource(R.string.cloud_app_tasks_on, kindLabel(it.kind)) }
                            ?: stringResource(R.string.cloud_app_tasks_hint),
                        settings.tasksEnabled,
                        if (accounts.any { it.id == settings.notesAccount }) { on -> if (on) askTasks = true else viewModel.setTasksEnabled(false) } else null,
                    )
                }
            }
            item("h-how") { Label(stringResource(R.string.cloud_how)) }
            item("how") {
                Group {
                    SwitchRow(stringResource(R.string.cloud_wifi), stringResource(R.string.cloud_wifi_hint), settings.wifiOnly, viewModel::setWifiOnly)
                }
            }
            if (events.isNotEmpty()) {
                item("h-log") { Label(stringResource(R.string.cloud_log)) }
                events.take(15).forEachIndexed { i, e ->
                    item("log$i") {
                        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(96.dp),
                                maxLines = 1,
                            )
                            Text(e.text, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (askTasks) {
        AlertDialog(
            onDismissRequest = { askTasks = false },
            title = { Text(stringResource(R.string.cloud_tasks_explain_title)) },
            text = { Text(stringResource(R.string.cloud_tasks_explain)) },
            confirmButton = { TextButton(onClick = { askTasks = false; viewModel.setTasksEnabled(true) }) { Text(stringResource(R.string.cloud_tasks_explain_ok)) } },
            dismissButton = { TextButton(onClick = { askTasks = false }) { Text(stringResource(R.string.cloud_tasks_explain_no)) } },
        )
    }
    if (connectSheet) {
        ConnectSheet(
            onPick = { kind ->
                connectSheet = false
                when (kind) {
                    CloudKind.Murena -> viewModel.connectNextcloud(CloudKind.Murena, "")
                    CloudKind.Nextcloud -> form = Form.Nextcloud
                    CloudKind.KDrive -> form = Form.KDrive
                    CloudKind.WebDav -> form = Form.WebDav
                }
            },
            onDismiss = { connectSheet = false },
        )
    }
    form?.let { f ->
        ConnectForm(
            form = f,
            onConfirm = { a, b, c ->
                form = null
                when (f) {
                    Form.Nextcloud -> viewModel.connectNextcloud(CloudKind.Nextcloud, a)
                    Form.KDrive -> viewModel.connectKDrive(a, b, c)
                    Form.WebDav -> viewModel.connectWebDav(a, b, c)
                }
            },
            onDismiss = { form = null },
        )
    }
    ui.connecting?.let { ConnectingDialog(it, onCancel = viewModel::cancelConnecting) }
    if (pickNotes) {
        AlertDialog(
            onDismissRequest = { pickNotes = false },
            title = { Text(stringResource(R.string.cloud_where_notes)) },
            text = {
                Column {
                    (listOf<CloudAccount?>(null) + accounts).forEach { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { pickNotes = false; viewModel.setNotesAccount(a?.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                a?.let { "${kindLabel(it.kind)} · ${it.name}" } ?: stringResource(R.string.cloud_only_phone),
                                fontWeight = if (a?.id == settings.notesAccount) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickNotes = false }) { Text(stringResource(R.string.cloud_close)) } },
        )
    }
}

@Composable
internal fun kindLabel(kind: CloudKind): String = stringResource(
    when (kind) {
        CloudKind.Murena -> R.string.cloud_murena
        CloudKind.Nextcloud -> R.string.cloud_nextcloud
        CloudKind.KDrive -> R.string.cloud_kdrive
        CloudKind.WebDav -> R.string.cloud_webdav
    }
)

@Composable
private fun statusLine(lastSync: Long?, error: String?, running: Boolean): String = when {
    running -> stringResource(R.string.cloud_status_running)
    error != null -> stringResource(
        when (error) {
            "auth" -> R.string.cloud_error_auth
            "full" -> R.string.cloud_error_full
            "network" -> R.string.cloud_error_network
            else -> R.string.cloud_error_other
        }
    )
    lastSync == null -> stringResource(R.string.cloud_status_never)
    System.currentTimeMillis() - lastSync < 60_000 -> stringResource(R.string.cloud_last_sync_now)
    else -> stringResource(R.string.cloud_last_sync, DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase(Locale.getDefault()))
}

@Composable
internal fun tintInk(name: String): Color {
    val t = CalendarTint.entries.first { it.name == name }
    return if (LocalEuriseColors.current.isDark) t.dark else t.light
}

@Composable
internal fun tintFill(name: String): Color = tintInk(name).copy(alpha = 0.16f)

@Composable
private fun AccountCard(account: CloudAccount, quota: DavQuota?, onRemove: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val tint = when (account.kind) {
        CloudKind.Murena -> "BluNotte"
        CloudKind.Nextcloud -> "Salvia"
        CloudKind.KDrive -> "Ottone"
        CloudKind.WebDav -> "Grafite"
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tintFill(tint)), contentAlignment = Alignment.Center) {
                Text(kindLabel(account.kind).take(1), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = tintInk(tint))
            }
            Column(Modifier.weight(1f)) {
                Text(kindLabel(account.kind), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(account.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(EuriseIcons.more(), contentDescription = stringResource(R.string.cloud_account_actions), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cloud_disconnect), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirm = true },
                    )
                }
            }
        }
        val used = quota?.used
        val free = quota?.available
        if (used != null) {
            val total = free?.let { used + it }
            if (total != null && total > 0) {
                Box(Modifier.padding(end = 12.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    Box(Modifier.fillMaxWidth((used.toFloat() / total).coerceIn(0.01f, 1f)).height(6.dp).background(tintInk(tint)))
                }
            }
            Text(
                if (total != null) stringResource(R.string.cloud_quota, Formatter.formatShortFileSize(context, used), Formatter.formatShortFileSize(context, total))
                else stringResource(R.string.cloud_quota_used, Formatter.formatShortFileSize(context, used)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.cloud_disconnect_title, kindLabel(account.kind))) },
            text = { Text(stringResource(R.string.cloud_disconnect_message)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove() }) {
                    Text(stringResource(R.string.cloud_disconnect), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cloud_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectSheet(onPick: (CloudKind) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.cloud_connect), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.cloud_connect_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Group(padded = false) {
                ProviderRow("M", "BluNotte", stringResource(R.string.cloud_murena), stringResource(R.string.cloud_murena_hint)) { onPick(CloudKind.Murena) }
                Divider()
                ProviderRow("N", "Salvia", stringResource(R.string.cloud_nextcloud), stringResource(R.string.cloud_nextcloud_hint)) { onPick(CloudKind.Nextcloud) }
                Divider()
                ProviderRow("W", "Grafite", stringResource(R.string.cloud_webdav), stringResource(R.string.cloud_webdav_hint)) { onPick(CloudKind.WebDav) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                    Text("P", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column {
                    Text(stringResource(R.string.cloud_proton), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.cloud_proton_hint), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(letter: String, tint: String, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tintFill(tint)), contentAlignment = Alignment.Center) {
            Text(letter, fontWeight = FontWeight.ExtraBold, color = tintInk(tint))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(hint, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(EuriseIcons.chevronRight(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** Dati per collegare un Nextcloud (indirizzo), un kDrive (email, password per le app, numero) o un WebDAV. */
@Composable
private fun ConnectForm(form: Form, onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var c by rememberSaveable { mutableStateOf("") }
    val ready = when (form) {
        Form.Nextcloud -> a.isNotBlank()
        Form.KDrive -> a.isNotBlank() && b.isNotBlank() && c.any { it.isDigit() }
        Form.WebDav -> a.isNotBlank() && c.isNotBlank()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(when (form) { Form.Nextcloud -> R.string.cloud_nextcloud; Form.KDrive -> R.string.cloud_kdrive; Form.WebDav -> R.string.cloud_webdav }))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (form) {
                    Form.Nextcloud -> {
                        Field(a, { a = it }, R.string.cloud_field_server, KeyboardType.Uri)
                        Text(stringResource(R.string.cloud_nextcloud_form_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Form.KDrive -> {
                        Field(a, { a = it }, R.string.cloud_field_email, KeyboardType.Email)
                        Field(b, { b = it }, R.string.cloud_field_app_password, KeyboardType.Password, secret = true)
                        Field(c, { c = it }, R.string.cloud_field_drive_id, KeyboardType.Number)
                        Text(stringResource(R.string.cloud_kdrive_form_hint), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Form.WebDav -> {
                        Field(a, { a = it }, R.string.cloud_field_address, KeyboardType.Uri)
                        Field(b, { b = it }, R.string.cloud_field_user, KeyboardType.Text)
                        Field(c, { c = it }, R.string.cloud_field_password, KeyboardType.Password, secret = true)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (form) {
                    Form.WebDav -> onConfirm(a, b, c)
                    else -> onConfirm(a, b, c)
                }
            }, enabled = ready) { Text(stringResource(R.string.cloud_connect_short)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_cancel)) } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, type: KeyboardType, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectingDialog(state: Connecting, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when (state) {
                        is Connecting.Browser -> R.string.cloud_browser_title
                        Connecting.Checking -> R.string.cloud_checking
                        is Connecting.Failed -> R.string.cloud_failed_title
                    }
                )
            )
        },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state !is Connecting.Failed) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(
                    when (state) {
                        is Connecting.Browser -> stringResource(R.string.cloud_browser_message)
                        Connecting.Checking -> stringResource(R.string.cloud_checking_message)
                        is Connecting.Failed -> stringResource(
                            when (state.reason) {
                                Reason.Password -> R.string.cloud_failed_password
                                Reason.Address -> R.string.cloud_failed_address
                                Reason.Network -> R.string.cloud_failed_network
                                Reason.Timeout -> R.string.cloud_failed_timeout
                                Reason.Plan -> R.string.cloud_failed_plan
                            }
                        )
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            (state as? Connecting.Failed)?.code?.let {
                Text(stringResource(R.string.cloud_failed_code, it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(if (state is Connecting.Failed) R.string.cloud_close else R.string.cloud_cancel))
            }
        },
    )
}

// Pezzi comuni

@Composable
internal fun Screen(backLabel: String, title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                TextButton(onClick = onBack) {
                    Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(backLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        content()
        item("nav") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
internal fun Group(padded: Boolean = true, content: @Composable () -> Unit) {
    Column(
        Modifier
            .then(if (padded) Modifier.padding(horizontal = 16.dp) else Modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) { content() }
}

@Composable
internal fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.background)

@Composable
internal fun Label(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 6.dp),
    )
}

@Composable
internal fun WhereRow(title: String, hint: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value.isNotEmpty()) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (onClick != null) {
            Icon(EuriseIcons.chevronRight(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (onChange == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = onChange != null)
    }
}
