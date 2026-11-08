package eu.eurise.feature.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.backup.BackupCrypto
import eu.eurise.core.cloud.backup.BackupFile
import eu.eurise.core.cloud.backup.BackupFrequency
import eu.eurise.core.cloud.backup.BackupManager
import eu.eurise.core.cloud.backup.BackupPlace
import eu.eurise.core.cloud.backup.BackupState
import eu.eurise.core.cloud.backup.Tier
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val When = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.ITALIAN)

/** Riga di stato del backup, per la pagina Cloud. */
@Composable
internal fun backupStatus(s: BackupState): String = when {
    !s.configured -> stringResource(R.string.backup_off)
    s.running -> stringResource(R.string.backup_running)
    s.lastError != null -> stringResource(R.string.backup_error, s.lastError)
    s.lastAt != null -> stringResource(R.string.backup_last, whenText(s.lastAt))
    else -> stringResource(R.string.backup_never)
}

@Composable
private fun whenText(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return when (at.toLocalDate()) {
        LocalDate.now() -> stringResource(R.string.backup_today, at.format(DateTimeFormatter.ofPattern("HH:mm")))
        LocalDate.now().minusDays(1) -> stringResource(R.string.backup_yesterday, at.format(DateTimeFormatter.ofPattern("HH:mm")))
        else -> at.format(When)
    }
}

private fun sizeText(bytes: Long?): String = when {
    bytes == null -> ""
    bytes < 1024 * 1024 -> "%d KB".format(bytes / 1024)
    else -> "%.1f MB".format(Locale.ITALIAN, bytes / 1024.0 / 1024.0)
}

/** Che cosa sta facendo l'utente nella pagina del backup. */
private sealed interface Step {
    data object Explain : Step
    data object Passphrase : Step
    data class Code(val passphrase: String, val code: String) : Step
    data class Place(val restoring: Boolean) : Step
    data class Pick(val place: BackupPlace, val files: List<BackupFile>?) : Step
    data class Secret(val place: BackupPlace?, val file: BackupFile?, val bytes: ByteArray?) : Step
    data object Restart : Step
    data object ChangePassphrase : Step
}

/**
 * Backup cifrato di Eurise: attivazione (spiegazione, frase segreta, codice di recupero, destinazione), frequenza,
 * backup adesso, elenco dei backup con la regola dei tre più mensile e trimestrale, ripristino, spegnimento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { BackupManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val accounts by remember { CloudAccounts.get(context) }.accounts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step?>(null) }
    var files by remember { mutableStateOf<List<BackupFile>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val doneLabel = stringResource(R.string.backup_done)
    val failLabel = stringResource(R.string.backup_failed)

    fun reloadFiles() { scope.launch { files = manager.list() } }
    LaunchedEffect(state.place, state.lastAt) { if (state.configured && state.place != null) reloadFiles() }
    BackHandler { if (step != null) step = null else onBack() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val restoring = (step as? Step.Place)?.restoring == true
        if (uri == null) return@rememberLauncherForActivityResult
        val place = BackupPlace.Folder(uri.toString())
        if (restoring) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            step = Step.Pick(place, null)
            scope.launch { step = Step.Pick(place, manager.list(place)) }
        } else {
            manager.setPlace(place); step = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes != null) step = Step.Secret(null, null, bytes)
        }
    }

    Screen(stringResource(R.string.cloud_title), stringResource(R.string.backup_title), onBack) {
        if (!state.configured) {
            item("intro") {
                Text(
                    stringResource(R.string.backup_intro), fontSize = 15.sp, lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item("start") { BigButton(stringResource(R.string.backup_turn_on)) { step = Step.Explain } }
            item("restore") {
                Group {
                    WhereRow(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restore_new_phone), "", onClick = { step = Step.Place(restoring = true) })
                }
            }
        } else {
            item("status") {
                Group {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val ok = state.lastError == null && state.lastAt != null
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(tintFill(if (state.lastError != null) "Borgogna" else "Salvia")),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.running || busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(if (state.lastError != null) EuriseIcons.close() else EuriseIcons.lock(), null, tint = tintInk(if (state.lastError != null) "Borgogna" else "Salvia"), modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(backupStatus(state), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (ok) stringResource(R.string.backup_verified, sizeText(state.lastSize)) else stringResource(R.string.backup_encrypted_hint),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item("now") {
                BigButton(stringResource(R.string.backup_now), enabled = !state.running && state.place != null) {
                    scope.launch {
                        val r = manager.backupNow()
                        Toast.makeText(context, if (r.isSuccess) doneLabel else failLabel, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item("h-freq") { Label(stringResource(R.string.backup_frequency)) }
            item("freq") {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupFrequency.entries.forEach { f ->
                        Chip(stringResource(freqLabel(f)), selected = state.frequency == f) { manager.setFrequency(f) }
                    }
                }
            }
            item("freq-hint") {
                Text(stringResource(R.string.backup_frequency_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            item("h-place") { Label(stringResource(R.string.backup_place)) }
            item("place") {
                Group {
                    val place = state.place
                    val (name, rating) = placeInfo(place, accounts)
                    WhereRow(name, rating, stringResource(R.string.backup_change), onClick = { step = Step.Place(restoring = false) })
                }
            }
            item("h-list") { Label(stringResource(R.string.backup_kept)) }
            item("list") {
                Group {
                    val list = files
                    when {
                        list == null -> Text(stringResource(R.string.backup_loading), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        list.isEmpty() -> Text(stringResource(R.string.backup_none_yet), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        else -> list.forEachIndexed { i, f ->
                            if (i > 0) Divider()
                            WhereRow(
                                f.at.format(When).replaceFirstChar { it.uppercase() },
                                stringResource(tierLabel(f.tier)) + (sizeText(f.size).takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                                stringResource(R.string.backup_restore),
                                onClick = { state.place?.let { step = Step.Secret(it, f, null) } },
                            )
                        }
                    }
                }
            }
            item("rule") {
                Text(stringResource(R.string.backup_rule), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            }
            item("more") {
                Group {
                    WhereRow(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restore_other), "", onClick = { step = Step.Place(restoring = true) })
                    Divider()
                    WhereRow(stringResource(R.string.backup_change_pass), stringResource(R.string.backup_change_pass_hint), "", onClick = { step = Step.ChangePassphrase })
                    Divider()
                    WhereRow(stringResource(R.string.backup_turn_off), stringResource(R.string.backup_turn_off_hint), "", onClick = { manager.turnOff(); files = null })
                }
            }
        }
    }

    when (val s = step) {
        Step.Explain -> ExplainDialog(onContinue = { step = Step.Passphrase }, onDismiss = { step = null })
        Step.Passphrase -> PassphraseDialog(onDone = { p -> step = Step.Code(p, BackupCrypto.newRecoveryCode()) }, onDismiss = { step = null })
        is Step.Code -> CodeDialog(s.code, working = busy, onDone = {
            busy = true
            scope.launch {
                manager.setup(s.passphrase, s.code)
                busy = false
                step = Step.Place(restoring = false)
            }
        }, onDismiss = { step = null })
        is Step.Place -> PlaceDialog(
            accounts = accounts, restoring = s.restoring,
            onCloud = { acc ->
                val place = BackupPlace.Cloud(acc.id)
                if (s.restoring) {
                    step = Step.Pick(place, null)
                    scope.launch { step = Step.Pick(place, manager.list(place)) }
                } else {
                    manager.setPlace(place); step = null
                    if (state.lastAt == null) scope.launch { manager.backupNow() }
                }
            },
            onFolder = { folderPicker.launch(null) },
            onFile = if (s.restoring) ({ filePicker.launch(arrayOf("*/*")) }) else null,
            onDismiss = { step = null },
        )
        is Step.Pick -> PickDialog(s.files, onPick = { f -> step = Step.Secret(s.place, f, null) }, onDismiss = { step = null })
        is Step.Secret -> SecretDialog(working = busy, onConfirm = { secret ->
            busy = true
            scope.launch {
                val r = runCatching {
                    if (s.bytes != null) manager.restoreBytes(s.bytes, secret) else manager.restore(s.place!!, s.file!!.name, secret)
                }
                busy = false
                if (r.isSuccess) step = Step.Restart
                else Toast.makeText(
                    context,
                    context.getString(if (r.exceptionOrNull() is BackupCrypto.WrongSecret) R.string.backup_wrong_secret else R.string.backup_bad_file),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }, onDismiss = { step = null })
        Step.Restart -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restored_title)) },
            text = { Text(stringResource(R.string.backup_restored_text)) },
            confirmButton = { TextButton(onClick = { manager.restartApp() }) { Text(stringResource(R.string.backup_restart), fontWeight = FontWeight.Bold) } },
        )
        Step.ChangePassphrase -> ChangePassDialog(onConfirm = { old, new ->
            scope.launch {
                val ok = manager.changePassphrase(old, new)
                Toast.makeText(context, context.getString(if (ok) R.string.backup_pass_changed else R.string.backup_wrong_secret), Toast.LENGTH_SHORT).show()
                if (ok) step = null
            }
        }, onDismiss = { step = null })
        null -> Unit
    }
}

private fun freqLabel(f: BackupFrequency) = when (f) {
    BackupFrequency.Daily -> R.string.backup_daily
    BackupFrequency.Weekly -> R.string.backup_weekly
    BackupFrequency.Monthly -> R.string.backup_monthly
    BackupFrequency.Manual -> R.string.backup_manual
}

private fun tierLabel(t: Tier) = when (t) {
    Tier.Recent -> R.string.backup_tier_recent
    Tier.Monthly -> R.string.backup_tier_monthly
    Tier.Quarterly -> R.string.backup_tier_quarterly
}

/** Nome della destinazione e la sua valutazione di privacy. */
@Composable
private fun placeInfo(place: BackupPlace?, accounts: List<CloudAccount>): Pair<String, String> = when (place) {
    null -> stringResource(R.string.backup_place_none) to stringResource(R.string.backup_place_none_hint)
    is BackupPlace.Folder -> stringResource(R.string.backup_place_folder) to (Uri.parse(place.uri).lastPathSegment?.substringAfter(':') ?: "")
    is BackupPlace.Cloud -> {
        val acc = accounts.firstOrNull { it.id == place.accountId }
        (acc?.let { "${kindName(it.kind)} · ${it.name}" } ?: stringResource(R.string.backup_place_missing)) to (acc?.let { stringResource(rating(it.kind)) } ?: "")
    }
}

@Composable
private fun kindName(kind: CloudKind) = kindLabel(kind)

private fun rating(kind: CloudKind) = when (kind) {
    CloudKind.Murena -> R.string.rating_murena
    CloudKind.KDrive -> R.string.rating_kdrive
    CloudKind.Nextcloud -> R.string.rating_nextcloud
    CloudKind.WebDav -> R.string.rating_webdav
}

@Composable
private fun BigButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (selected) scheme.onPrimary else scheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// ---------------------------------------------------------------- finestre

@Composable
private fun ExplainDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(EuriseIcons.shield(2f), null) },
        title = { Text(stringResource(R.string.backup_explain_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.backup_explain_read, R.string.backup_explain_where, R.string.backup_explain_who, R.string.backup_explain_back, R.string.backup_explain_warning)
                    .forEach { Text(stringResource(it), fontSize = 14.sp, lineHeight = 19.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.backup_continue), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun PassphraseDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val long = a.length >= 10
    val same = a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_pass_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_pass_hint), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecretField(a, { a = it }, stringResource(R.string.backup_pass_field))
                SecretField(b, { b = it }, stringResource(R.string.backup_pass_again))
                if (a.isNotEmpty() && !long) Text(stringResource(R.string.backup_pass_short), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                else if (b.isNotEmpty() && !same) Text(stringResource(R.string.backup_pass_mismatch), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(a) }, enabled = long && same) { Text(stringResource(R.string.backup_continue), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeDialog(code: String, working: Boolean, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.backup_code_copied)
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.backup_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_code_hint), fontSize = 13.sp, lineHeight = 18.sp)
                Text(
                    code, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                )
                TextButton(onClick = {
                    val clip = ClipData.newPlainText("code", code).apply {
                        description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.backup_code_copy)) }
                Row(Modifier.clickable(role = Role.Checkbox) { saved = !saved }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = null)
                    Text(stringResource(R.string.backup_code_saved), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
                if (working) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.backup_preparing), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = saved && !working) { Text(stringResource(R.string.backup_continue), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

/** Scelta della destinazione, con la valutazione di privacy dei servizi. */
@Composable
private fun PlaceDialog(
    accounts: List<CloudAccount>,
    restoring: Boolean,
    onCloud: (CloudAccount) -> Unit,
    onFolder: () -> Unit,
    onFile: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (restoring) R.string.backup_place_from else R.string.backup_place_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                accounts.forEach { acc ->
                    PlaceRow("${kindName(acc.kind)} · ${acc.name}", stringResource(rating(acc.kind))) { onCloud(acc) }
                }
                if (accounts.isEmpty()) Text(stringResource(R.string.backup_no_accounts), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlaceRow(stringResource(R.string.backup_place_folder), stringResource(R.string.backup_place_folder_hint), onFolder)
                if (onFile != null) PlaceRow(stringResource(R.string.backup_place_file), stringResource(R.string.backup_place_file_hint), onFile)
                Text(stringResource(R.string.ratings_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                listOf(R.string.ratings_own, R.string.ratings_eu, R.string.ratings_proton, R.string.ratings_mega, R.string.ratings_us, R.string.ratings_local)
                    .forEach { Text(stringResource(it), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun PlaceRow(title: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickDialog(files: List<BackupFile>?, onPick: (BackupFile) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_pick_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    files == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    files.isEmpty() -> Text(stringResource(R.string.backup_pick_empty))
                    else -> files.forEach { f ->
                        PlaceRow(f.at.format(When).replaceFirstChar { it.uppercase() }, stringResource(tierLabel(f.tier)) + " · " + sizeText(f.size)) { onPick(f) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun SecretDialog(working: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.backup_secret_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_secret_hint), fontSize = 13.sp, lineHeight = 18.sp)
                SecretField(secret, { secret = it }, stringResource(R.string.backup_secret_field))
                if (working) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.backup_opening), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(secret) }, enabled = secret.isNotBlank() && !working) { Text(stringResource(R.string.backup_restore), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

@Composable
private fun ChangePassDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_change_pass)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecretField(old, { old = it }, stringResource(R.string.backup_secret_field))
                SecretField(new, { new = it }, stringResource(R.string.backup_pass_new))
                if (new.isNotEmpty() && new.length < 10) Text(stringResource(R.string.backup_pass_short), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(old, new) }, enabled = old.isNotBlank() && new.length >= 10) { Text(stringResource(R.string.backup_save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}
