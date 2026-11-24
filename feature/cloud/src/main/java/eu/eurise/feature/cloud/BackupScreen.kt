package eu.eurise.feature.cloud

import eu.eurise.core.designsystem.component.SecretEye
import eu.eurise.core.designsystem.component.secretText
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import eu.eurise.core.cloud.backup.PhraseStrength
import eu.eurise.core.cloud.backup.Tier
import eu.eurise.core.designsystem.component.FolderTabs
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.privacyColor
import eu.eurise.core.designsystem.component.privacyScore
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
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

/** Riga di stato del backup, per la pagina Cloud. */
@Composable
internal fun backupStatus(s: BackupState): String {
    val error = s.lastError
    val last = s.lastAt
    return when {
        !s.configured -> stringResource(R.string.backup_off)
        s.running -> stringResource(R.string.backup_running)
        error != null -> stringResource(R.string.backup_error, error)
        last != null -> stringResource(R.string.backup_last, whenText(last))
        else -> stringResource(R.string.backup_never)
    }
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

/**
 * Le pagine del backup (tavole «Backup» e «Ripristino» della tela, 20 novembre 2026). Senza pagina si vede la pagina
 * principale: spento (cosa salva) o acceso (stato, frequenza, conservati).
 */
private sealed interface BkPage {
    /** Dove: per accenderlo ([setup]), per cambiare destinazione, o da dove ripristinare ([restoring]). */
    data class Where(val restoring: Boolean, val setup: Boolean = false) : BkPage
    data class Phrase(val place: BackupPlace?) : BkPage
    data class Code(val place: BackupPlace?, val phrase: String, val code: String) : BkPage
    data class Pick(val place: BackupPlace, val files: List<BackupFile>?) : BkPage
    /** [picked]: un file scelto a mano (resta dov'è finché non si apre). */
    data class Unlock(val place: BackupPlace?, val file: BackupFile?, val picked: Uri?) : BkPage
    data class Confirm(val opened: BackupManager.Opened) : BkPage
    /** Frase nuova dopo lo sblocco del telefono (non serve la vecchia). */
    data object NewPhrase : BkPage
    data class NewCode(val phrase: String, val code: String) : BkPage
}

/** Finestre sopra la pagina. */
private sealed interface Ask {
    /** Il controllo di una volta l'anno: ti ricordi la frase? */
    data object Check : Ask
    /** Telefono senza blocco schermo: per cambiare la frase serve quella attuale o il codice. */
    data object ChangeWithOld : Ask
}

/**
 * Backup cifrato di Eurise. [start]: «restore» apre subito il ripristino (dalla Scrivania di un telefono nuovo),
 * «check» il promemoria annuale della frase. [onConnectCloud] apre la pagina Cloud per collegare un account.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupRoute(onBack: () -> Unit, onConnectCloud: () -> Unit = {}, start: String? = null) {
    val context = LocalContext.current
    val manager = remember { BackupManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val accounts by remember { CloudAccounts.get(context) }.accounts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<BkPage?>(if (start == "restore") BkPage.Where(restoring = true) else null) }
    var ask by remember { mutableStateOf<Ask?>(if (start == "check") Ask.Check else null) }
    var files by remember { mutableStateOf<List<BackupFile>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val doneLabel = stringResource(R.string.backup_done)
    val failLabel = stringResource(R.string.backup_failed)

    fun reloadFiles() { scope.launch { files = manager.list() } }
    LaunchedEffect(state.place, state.lastAt) { if (state.configured && state.place != null) reloadFiles() }
    BackHandler {
        if (!busy) {
            (page as? BkPage.Confirm)?.let { manager.discard(it.opened) }
            if (page != null) page = null else onBack()
        }
    }

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_LONG).show()

    /** Dopo la scelta della destinazione, secondo il motivo per cui la si è scelta. */
    fun chosen(place: BackupPlace, why: BkPage.Where) {
        when {
            why.restoring -> {
                page = BkPage.Pick(place, null)
                scope.launch { page = BkPage.Pick(place, manager.list(place)) }
            }
            why.setup || !state.configured -> page = BkPage.Phrase(place)
            else -> {
                manager.setPlace(place); page = null
                scope.launch { manager.backupNow() }
            }
        }
    }

    var folderFor by remember { mutableStateOf<BkPage.Where?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val why = folderFor ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        chosen(BackupPlace.Folder(uri.toString()), why)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        page = BkPage.Unlock(null, null, uri)
    }

    // Lo sblocco del telefono (impronta, volto o codice) prima di una frase nuova senza quella vecchia.
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val confirmLegacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) page = BkPage.NewPhrase
    }
    val unlockTitle = stringResource(R.string.bk_unlock_title)
    val unlockHint = stringResource(R.string.bk_unlock_hint)
    fun newPhraseWithUnlock() {
        val kg = keyguard
        if (kg == null || !kg.isDeviceSecure) { ask = Ask.ChangeWithOld; return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricPrompt.Builder(context)
                .setTitle(unlockTitle).setSubtitle(unlockHint)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { page = BkPage.NewPhrase }
                })
        } else {
            @Suppress("DEPRECATION")
            kg.createConfirmDeviceCredentialIntent(unlockTitle, unlockHint)?.let { confirmLegacy.launch(it) }
        }
    }

    when (val p = page) {
        null -> HomePage(
            state = state, accounts = accounts, files = files, busy = busy, onBack = onBack,
            onStart = { page = BkPage.Where(restoring = false, setup = true) },
            onRestore = { page = BkPage.Where(restoring = true) },
            onNow = {
                scope.launch {
                    val r = manager.backupNow()
                    Toast.makeText(context, if (r.isSuccess) doneLabel else failLabel, Toast.LENGTH_SHORT).show()
                }
            },
            onFrequency = manager::setFrequency,
            onPlace = { page = BkPage.Where(restoring = false) },
            onFile = { f -> state.place?.let { page = BkPage.Unlock(it, f, null) } },
            onNewPhrase = { newPhraseWithUnlock() },
            onCheck = { ask = Ask.Check },
            onSeen = manager::restoreSeen,
            onOff = { manager.turnOff(); files = null },
        )
        is BkPage.Where -> WherePage(
            restoring = p.restoring, explain = p.setup, accounts = accounts,
            onBack = { page = null },
            onCloud = { acc -> chosen(BackupPlace.Cloud(acc.id), p) },
            onConnect = onConnectCloud,
            onFolder = { folderFor = p; folderPicker.launch(null) },
            onFile = if (p.restoring) ({ filePicker.launch(arrayOf("*/*")) }) else null,
        )
        is BkPage.Phrase -> PhrasePage(back = stringResource(R.string.backup_title), onBack = { page = null }) { phrase ->
            page = BkPage.Code(p.place, phrase, BackupCrypto.newRecoveryCode())
        }
        is BkPage.Code -> CodePage(p.code, working = busy, back = stringResource(R.string.bk_phrase_title), onBack = { page = BkPage.Phrase(p.place) }) {
            busy = true
            scope.launch {
                manager.setup(p.phrase, p.code)
                p.place?.let { manager.setPlace(it) }
                page = null
                val r = manager.backupNow()
                busy = false
                Toast.makeText(context, if (r.isSuccess) doneLabel else failLabel, Toast.LENGTH_SHORT).show()
            }
        }
        is BkPage.Pick -> PickPage(p.files, onBack = { page = BkPage.Where(restoring = true) }) { f -> page = BkPage.Unlock(p.place, f, null) }
        is BkPage.Unlock -> UnlockPage(p.file, working = busy, onBack = { page = null }) { secret ->
            busy = true
            scope.launch {
                // Si apre e si verifica soltanto: il telefono non cambia finché non si conferma (ConfirmPage).
                val r = runCatching {
                    val picked = p.picked
                    if (picked != null) {
                        val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(picked) } ?: throw BackupCrypto.BadFile()
                        manager.openStream(input, secret)
                    } else manager.open(p.place!!, p.file!!.name, secret)
                }
                busy = false
                r.getOrNull()?.let { page = BkPage.Confirm(it) }
                if (r.isFailure) toast(if (r.exceptionOrNull() is BackupCrypto.WrongSecret) R.string.backup_wrong_secret else R.string.backup_bad_file)
            }
        }
        is BkPage.Confirm -> ConfirmPage(p.opened, replacesBackupSettings = state.configured, working = busy, onBack = { manager.discard(p.opened); page = null }) {
            busy = true
            scope.launch {
                // Prepara e riavvia anche se nel frattempo si esce dalla schermata.
                val r = runCatching { manager.applyAndRestart(p.opened) }
                busy = false
                if (r.isFailure) toast(R.string.backup_bad_file)
            }
        }
        BkPage.NewPhrase -> PhrasePage(back = stringResource(R.string.backup_title), title = stringResource(R.string.bk_new_title), sub = stringResource(R.string.bk_new_sub), onBack = { page = null }) { phrase ->
            page = BkPage.NewCode(phrase, BackupCrypto.newRecoveryCode())
        }
        is BkPage.NewCode -> CodePage(p.code, working = busy, back = stringResource(R.string.bk_new_title), onBack = { page = BkPage.NewPhrase }, after = stringResource(R.string.bk_new_after)) {
            busy = true
            scope.launch {
                val ok = manager.resetSecrets(p.phrase, p.code)
                page = null
                if (ok && state.place != null) manager.backupNow()
                busy = false
                toast(if (ok) R.string.bk_new_done else R.string.backup_failed)
            }
        }
    }

    when (ask) {
        Ask.Check -> CheckDialog(
            onCheck = { secret -> manager.verify(secret) },
            onForgot = { ask = null; newPhraseWithUnlock() },
            onDismiss = { ask = null },
        )
        Ask.ChangeWithOld -> ChangePassDialog(onConfirm = { old, new ->
            scope.launch {
                val ok = manager.changePassphrase(old, new)
                toast(if (ok) R.string.backup_pass_changed else R.string.backup_wrong_secret)
                if (ok) ask = null
            }
        }, onDismiss = { ask = null })
        null -> Unit
    }
}

// ---------------------------------------------------------------- pagina principale

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePage(
    state: BackupState,
    accounts: List<CloudAccount>,
    files: List<BackupFile>?,
    busy: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRestore: () -> Unit,
    onNow: () -> Unit,
    onFrequency: (BackupFrequency) -> Unit,
    onPlace: () -> Unit,
    onFile: (BackupFile) -> Unit,
    onNewPhrase: () -> Unit,
    onCheck: () -> Unit,
    onSeen: () -> Unit,
    onOff: () -> Unit,
) {
    val context = LocalContext.current
    if (!state.configured) {
        BackupPage(
            back = stringResource(R.string.cloud_title), title = stringResource(R.string.bk_title), sub = stringResource(R.string.bk_intro), onBack = onBack,
            bottom = {
                Secondary(stringResource(R.string.bk_have_one), onClick = onRestore)
                Primary(stringResource(R.string.bk_now), onClick = onStart)
            },
        ) {
            if (state.restoredAt != null) item("welcome") { WelcomeCard(state, context, onPlace, onSeen) }
            item("h-what") { Label(stringResource(R.string.bk_what)) }
            item("what") { WhatCard() }
            item("out") { Note(stringResource(R.string.bk_out)) }
        }
        return
    }
    BackupPage(
        back = stringResource(R.string.cloud_title), title = stringResource(R.string.bk_title), onBack = onBack,
        bottom = { Primary(stringResource(R.string.bk_now), enabled = !state.running && !busy && state.place != null, onClick = onNow) },
    ) {
        if (state.restoredAt != null) item("welcome") { WelcomeCard(state, context, onPlace, onSeen) }
        item("status") { StatusCard(state, busy) }
        if (state.checkDue()) item("check") {
            Group { WhereRow(stringResource(R.string.bk_check_title), stringResource(R.string.bk_check_row), "", onClick = onCheck) }
        }
        item("h-freq") { Label(stringResource(R.string.backup_frequency)) }
        item("freq") {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupFrequency.entries.forEach { f -> Chip(stringResource(freqLabel(f)), selected = state.frequency == f) { onFrequency(f) } }
            }
        }
        item("freq-hint") {
            val hint = if (state.frequency == BackupFrequency.Manual) stringResource(R.string.bk_freq_manual) else stringResource(R.string.backup_frequency_hint)
            Note(hint)
        }
        item("h-list") { Label(stringResource(R.string.backup_kept)) }
        item("list") {
            Group {
                when {
                    files == null -> Text(stringResource(R.string.backup_loading), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    files.isEmpty() -> Text(stringResource(R.string.backup_none_yet), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    else -> files.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        WhereRow(
                            f.at.format(When).replaceFirstChar { it.uppercase() },
                            stringResource(tierLabel(f.tier)) + (sizeText(f.size).takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                            stringResource(R.string.backup_restore),
                            onClick = { onFile(f) },
                        )
                    }
                }
            }
        }
        item("rule") { Note(stringResource(R.string.backup_rule)) }
        item("more") {
            Group {
                PlaceRow(state.place, accounts, onPlace)
                Divider()
                WhereRow(stringResource(R.string.bk_phrase_row), stringResource(R.string.bk_phrase_row_hint), "", onClick = onNewPhrase)
                Divider()
                WhereRow(stringResource(R.string.backup_restore_title), stringResource(R.string.backup_restore_other), "", onClick = onRestore)
                Divider()
                WhereRow(stringResource(R.string.backup_turn_off), stringResource(R.string.backup_turn_off_hint), "", onClick = onOff)
            }
        }
    }
}

/** Cosa salva: Eurise e ogni app separata della suite, quando è installata (Mappe compresa dal 21 novembre). */
@Composable
private fun WhatCard() {
    Group {
        WhereRow(stringResource(R.string.bk_what_eurise), stringResource(R.string.bk_what_eurise_hint), "")
        Divider()
        WhereRow(stringResource(R.string.backup_confirm_locked), stringResource(R.string.backup_confirm_locked_hint), "")
        eu.eurise.core.cloud.backup.SuiteApps.all.forEach { app ->
            val (name, hint) = appLabels(app.key)
            Divider()
            WhereRow(stringResource(name), stringResource(hint), "")
        }
    }
}

@Composable
private fun StatusCard(state: BackupState, busy: Boolean) {
    Group {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val failed = state.lastError != null
            val ok = !failed && state.lastAt != null
            Box(Modifier.size(40.dp).clip(CircleShape).background(tintFill(if (failed) "Borgogna" else "Salvia")), contentAlignment = Alignment.Center) {
                if (state.running || busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(if (failed) EuriseIcons.close() else if (ok) EuriseIcons.check() else EuriseIcons.lock(), null, tint = tintInk(if (failed) "Borgogna" else "Salvia"), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (ok && !state.running) stringResource(R.string.bk_verified_title) else backupStatus(state), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val last = state.lastAt
                Text(
                    when {
                        ok && last != null -> stringResource(R.string.bk_verified_sub, whenText(last), sizeText(state.lastSize))
                        else -> stringResource(R.string.backup_encrypted_hint)
                    },
                    fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** «Bentornato» dopo un ripristino: cosa è già al suo posto e i passi che restano, uno per app. */
@Composable
private fun WelcomeCard(state: BackupState, context: android.content.Context, onPlace: () -> Unit, onSeen: () -> Unit) {
    val waiting = remember { eu.eurise.core.cloud.backup.SuiteApps.waiting(context) }
    Group {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.bk_welcome_title), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.bk_welcome_sub), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WhereRow(stringResource(R.string.bk_what_eurise), stringResource(R.string.bk_welcome_eurise), "")
        Divider()
        // Le app che aspettano ancora i loro dati: arrivano appena le installi (da apk-prova o dal negozio).
        waiting.forEach { key ->
            val hint = when (key) {
                "messages" -> R.string.bk_welcome_messages_wait
                "phone" -> R.string.bk_welcome_phone_wait
                else -> R.string.bk_welcome_app_wait
            }
            WhereRow(stringResource(appLabels(key).first), stringResource(hint), "")
            Divider()
        }
        if (state.place == null) WhereRow(stringResource(R.string.bk_welcome_backup), stringResource(R.string.bk_welcome_backup_where), "", onClick = onPlace)
        else WhereRow(stringResource(R.string.bk_welcome_backup), stringResource(R.string.bk_welcome_backup_on), "")
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSeen) { Text(stringResource(R.string.bk_welcome_seen), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PlaceRow(place: BackupPlace?, accounts: List<CloudAccount>, onClick: () -> Unit) {
    val (name, score, where) = when (place) {
        null -> Triple(stringResource(R.string.backup_place_none), 0, null)
        is BackupPlace.Folder -> Triple(stringResource(R.string.backup_place_folder) + (Uri.parse(place.uri).lastPathSegment?.substringAfter(':')?.let { " · $it" } ?: ""), 10, Jurisdiction.Phone)
        is BackupPlace.Cloud -> {
            val acc = accounts.firstOrNull { it.id == place.accountId }
            if (acc == null) Triple(stringResource(R.string.backup_place_missing), 0, null)
            else Triple("${kindLabel(acc.kind)} · ${acc.name}", score(acc.kind), jurisdiction(acc.kind))
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.backup_place), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (score > 0) PrivacyMeter(score, where)
        Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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

private fun jurisdiction(kind: CloudKind) = when (kind) {
    CloudKind.Murena -> Jurisdiction.EU
    CloudKind.KDrive -> Jurisdiction.CH
    CloudKind.Nextcloud -> Jurisdiction.Own
    CloudKind.WebDav -> Jurisdiction.Other
}

/** Il file è cifrato da capo a capo: il servizio tiene i dati ma non li legge (regola delle tacchette). */
private fun score(kind: CloudKind) = privacyScore(DataOut.Personal, jurisdiction(kind), endToEnd = true)

private fun ratingHint(kind: CloudKind) = when (kind) {
    CloudKind.Murena -> R.string.rating_murena
    CloudKind.KDrive -> R.string.rating_kdrive
    CloudKind.Nextcloud -> R.string.rating_nextcloud
    CloudKind.WebDav -> R.string.rating_webdav
}

// ---------------------------------------------------------------- dove

@Composable
private fun WherePage(
    restoring: Boolean,
    explain: Boolean,
    accounts: List<CloudAccount>,
    onBack: () -> Unit,
    onCloud: (CloudAccount) -> Unit,
    onConnect: () -> Unit,
    onFolder: () -> Unit,
    onFile: (() -> Unit)?,
) {
    BackupPage(
        back = stringResource(R.string.bk_title),
        title = stringResource(if (restoring) R.string.bk_where_from else R.string.bk_where_title),
        sub = stringResource(if (restoring) R.string.bk_where_from_sub else R.string.bk_where_sub),
        onBack = onBack,
    ) {
        item("places") {
            Group {
                accounts.sortedByDescending { score(it.kind) }.forEachIndexed { i, acc ->
                    if (i > 0) Divider()
                    ChoiceRow("${kindLabel(acc.kind)} · ${acc.name}", stringResource(ratingHint(acc.kind)), score(acc.kind), jurisdiction(acc.kind)) { onCloud(acc) }
                }
                if (accounts.isNotEmpty()) Divider()
                ChoiceRow(stringResource(R.string.bk_where_folder), stringResource(R.string.backup_place_folder_hint), 10, Jurisdiction.Phone, onFolder)
                if (onFile != null) {
                    Divider()
                    ChoiceRow(stringResource(R.string.backup_place_file), stringResource(R.string.backup_place_file_hint), 0, null, onFile)
                }
                Divider()
                WhereRow(stringResource(R.string.bk_where_connect), stringResource(R.string.bk_where_connect_hint), "", onClick = onConnect)
            }
        }
        item("ratings") { Note(stringResource(R.string.bk_where_ticks)) }
        // Regola della privacy: prima di accendere, cosa viene letto, dove va, chi lo vede, come si torna indietro.
        if (explain) {
            item("h-explain") { Label(stringResource(R.string.backup_explain_title)) }
            item("explain") {
                Group {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.backup_explain_read, R.string.backup_explain_where, R.string.backup_explain_who, R.string.backup_explain_back)
                            .forEach { Text(stringResource(it), fontSize = 13.sp, lineHeight = 18.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, hint: String, score: Int, where: Jurisdiction?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (score > 0) PrivacyMeter(score, where, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------- frase e codice

@Composable
private fun PhrasePage(
    back: String,
    title: String = stringResource(R.string.bk_phrase_title),
    sub: String = stringResource(R.string.bk_phrase_sub),
    onBack: () -> Unit,
    onDone: (String) -> Unit,
) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val strength = PhraseStrength.score(a)
    val ok = a.length >= PhraseStrength.MIN_LENGTH && a == b
    BackupPage(
        back = back, title = title, sub = sub, onBack = onBack,
        bottom = { Primary(stringResource(R.string.backup_continue), enabled = ok) { onDone(a) } },
    ) {
        item("fields") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecretField(a, { a = it }, stringResource(R.string.backup_pass_field))
                Text(stringResource(R.string.bk_phrase_tip), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.isNotEmpty()) {
                    val label = stringResource(strengthLabel(strength))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Ticks(strength, Modifier.semantics { contentDescription = label })
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = privacyColor(strength.coerceAtLeast(1)))
                    }
                }
                SecretField(b, { b = it }, stringResource(R.string.backup_pass_again))
                if (a.isNotEmpty() && a.length < PhraseStrength.MIN_LENGTH) Text(stringResource(R.string.backup_pass_short), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                else if (b.isNotEmpty() && a != b) Text(stringResource(R.string.backup_pass_mismatch), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        item("code-next") {
            Group { WhereRow(stringResource(R.string.bk_phrase_code_next), stringResource(R.string.bk_phrase_code_next_hint), "") }
        }
    }
}

private fun strengthLabel(score: Int) = when {
    score >= 9 -> R.string.bk_strength_4
    score >= 7 -> R.string.bk_strength_3
    score >= 4 -> R.string.bk_strength_2
    else -> R.string.bk_strength_1
}

/** Dieci tacchette per la robustezza della frase, con i colori del semaforo della privacy. */
@Composable
private fun Ticks(n: Int, modifier: Modifier = Modifier) {
    val on = privacyColor(n.coerceAtLeast(1))
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(10) { i -> Box(Modifier.width(4.dp).height(11.dp).clip(RoundedCornerShape(2.dp)).background(if (i < n) on else off)) }
    }
}

/**
 * Il codice di recupero, a gruppi di quattro, da scrivere su carta. Per andare avanti si riscrive uno dei gruppi,
 * scelto a caso: così si è sicuri che sia stato copiato. Non si copia negli appunti (finirebbe nella cronologia).
 */
@Composable
private fun CodePage(code: String, working: Boolean, back: String, onBack: () -> Unit, after: String? = null, onDone: () -> Unit) {
    val groups = remember(code) { code.split('-') }
    val ask = remember(code) { (groups.indices).random() }
    var typed by remember(code) { mutableStateOf("") }
    val right = BackupCrypto.normalizeCode(typed) == groups.getOrNull(ask)
    BackupPage(
        back = back, title = stringResource(R.string.bk_code_title), sub = stringResource(R.string.bk_code_sub), onBack = { if (!working) onBack() },
        bottom = {
            Note(stringResource(R.string.bk_code_photo), padded = false)
            if (working) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.backup_preparing), fontSize = 13.sp)
            }
            Primary(stringResource(R.string.bk_code_go), enabled = right && !working, onClick = onDone)
        },
    ) {
        item("code") {
            Group {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groups.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { g ->
                                Text(
                                    g, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        item("verify") {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.bk_code_verify, stringResource(ordinal(ask))), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.take(6) }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.width(160.dp),
                )
            }
        }
        item("warn") { Warning(stringResource(R.string.bk_code_warn_title), after ?: stringResource(R.string.bk_code_warn)) }
    }
}

private fun ordinal(i: Int) = when (i) {
    0 -> R.string.bk_ord_1
    1 -> R.string.bk_ord_2
    2 -> R.string.bk_ord_3
    3 -> R.string.bk_ord_4
    4 -> R.string.bk_ord_5
    else -> R.string.bk_ord_6
}

// ---------------------------------------------------------------- ripristino

@Composable
private fun PickPage(files: List<BackupFile>?, onBack: () -> Unit, onPick: (BackupFile) -> Unit) {
    BackupPage(back = stringResource(R.string.bk_where_from), title = stringResource(R.string.bk_pick_title), onBack = onBack) {
        item("list") {
            Group {
                when {
                    files == null -> Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.backup_loading), fontSize = 13.sp)
                    }
                    files.isEmpty() -> Text(stringResource(R.string.backup_pick_empty), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    else -> files.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        WhereRow(f.at.format(When).replaceFirstChar { it.uppercase() }, stringResource(tierLabel(f.tier)) + " · " + sizeText(f.size), "", onClick = { onPick(f) })
                    }
                }
            }
        }
    }
}

/** Frase segreta oppure codice di recupero: due linguette, lo stesso campo (il backup si apre con l'uno o l'altro). */
@Composable
private fun UnlockPage(file: BackupFile?, working: Boolean, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var useCode by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") }
    val sub = file?.let { stringResource(R.string.bk_unlock_sub_file, it.at.format(When), sizeText(it.size)) } ?: stringResource(R.string.bk_unlock_sub)
    BackupPage(
        back = stringResource(R.string.bk_pick_title), title = stringResource(R.string.bk_open_title), sub = sub, onBack = { if (!working) onBack() },
        bottom = {
            if (working) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.backup_opening), fontSize = 13.sp)
            }
            Primary(stringResource(R.string.bk_open_go), enabled = secret.isNotBlank() && !working) { onOpen(secret) }
        },
    ) {
        // Frase segreta · Codice di recupero a schedario (tavola SchedarioProve, decisa il 22 novembre): due chiavi per la
        // stessa cartella; il campo sta sul foglio della linguetta aperta. In un elemento solo, senza spazio tra i due.
        item("folder") {
            val sheet = MaterialTheme.colorScheme.surfaceContainerLow
            Column {
                FolderTabs(
                    labels = listOf(stringResource(R.string.backup_pass_field), stringResource(R.string.bk_code_tab)),
                    selected = if (useCode) 1 else 0,
                    onSelect = { i -> val code = i == 1; if (useCode != code) { useCode = code; secret = "" } },
                    sheet = sheet,
                    modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth(),
                )
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(sheet, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp, topEnd = 18.dp)).padding(14.dp)) {
                if (useCode) OutlinedTextField(
                    value = secret, onValueChange = { secret = it }, singleLine = true, label = { Text(stringResource(R.string.bk_code_tab)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    visualTransformation = VisualTransformation.None, modifier = Modifier.fillMaxWidth(),
                ) else SecretField(secret, { secret = it }, stringResource(R.string.backup_pass_field))
            }
            }
        }
        item("hint") { Note(stringResource(if (useCode) R.string.bk_unlock_code_hint else R.string.bk_unlock_phrase_hint)) }
    }
}

/** Prima di ripristinare: che cosa c'è nel backup e che cosa verrà sostituito. Niente cambia prima di «Sostituisci». */
@Composable
private fun ConfirmPage(opened: BackupManager.Opened, replacesBackupSettings: Boolean, working: Boolean, onBack: () -> Unit, onConfirm: () -> Unit) {
    val c = opened.contents
    val phone = opened.onPhone
    val date = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).format(When)
    BackupPage(
        back = stringResource(R.string.bk_open_title), title = stringResource(R.string.backup_confirm_title), sub = stringResource(R.string.backup_confirm_from, date),
        onBack = { if (!working) onBack() },
        bottom = {
            if (working) Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.backup_applying), fontSize = 13.sp)
            }
            Note(stringResource(R.string.bk_confirm_restart), padded = false)
            Primary(stringResource(if (phone.empty) R.string.backup_confirm_go_empty else R.string.backup_confirm_go), enabled = !working, onClick = onConfirm)
        },
    ) {
        item("inside") {
            Group {
                val notes = c.notes
                val tasks = c.tasks
                val data = if (notes != null && tasks != null) stringResource(R.string.backup_confirm_data_counts, notes, tasks) else stringResource(R.string.backup_confirm_data)
                WhereRow(stringResource(R.string.backup_confirm_eurise), data, "")
                if (c.settings) { Divider(); WhereRow(stringResource(R.string.backup_confirm_settings), stringResource(R.string.backup_confirm_settings_hint), "") }
                if (c.lockedNotes) { Divider(); WhereRow(stringResource(R.string.backup_confirm_locked), stringResource(R.string.backup_confirm_locked_hint), "") }
                // Le app separate: ognuna riceve i suoi dati appena è installata (subito, se c'è già).
                c.apps.forEach { (key, summary) ->
                    val (name, hint) = appLabels(key)
                    Divider()
                    WhereRow(stringResource(name), summary ?: stringResource(hint), "")
                }
            }
        }
        item("replace") {
            val what = if (phone.empty) stringResource(R.string.backup_confirm_empty) else stringResource(R.string.backup_confirm_replace, phone.notes, phone.tasks)
            val keys = stringResource(R.string.backup_confirm_keys)
            val text = if (replacesBackupSettings) "$what $keys" else what
            Warning(if (phone.empty) null else stringResource(R.string.backup_confirm_replace_title), text)
        }
    }
}

/** Nome e descrizione di un'app separata nel backup, per la sua chiave ([eu.eurise.core.cloud.backup.SuiteApps]). */
private fun appLabels(key: String): Pair<Int, Int> = when (key) {
    "password" -> R.string.bk_app_password to R.string.bk_app_password_hint
    "messages" -> R.string.bk_app_messages to R.string.bk_app_messages_hint
    "phone" -> R.string.bk_app_phone to R.string.bk_app_phone_hint
    "gallery" -> R.string.bk_app_gallery to R.string.bk_app_gallery_hint
    "keyboard" -> R.string.backup_confirm_keyboard to R.string.backup_confirm_keyboard_hint
    "browser" -> R.string.bk_app_browser to R.string.bk_app_browser_hint
    "office" -> R.string.bk_app_office to R.string.bk_app_office_hint
    "mail" -> R.string.bk_app_mail to R.string.bk_app_mail_hint
    "chat" -> R.string.bk_app_chat to R.string.bk_app_chat_hint
    "voice" -> R.string.bk_app_voice to R.string.bk_app_settings_hint
    "read" -> R.string.bk_app_read to R.string.bk_app_settings_hint
    "ai" -> R.string.bk_app_ai to R.string.bk_app_settings_hint
    "maps" -> R.string.bk_app_maps to R.string.bk_app_maps_hint
    else -> R.string.bk_what_eurise to R.string.bk_app_settings_hint
}

// ---------------------------------------------------------------- finestre

/** Una volta l'anno: riscrivi la frase. Giusta → tra un anno; non la ricordi → frase nuova con lo sblocco. */
@Composable
private fun CheckDialog(onCheck: suspend (String) -> Boolean, onForgot: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var working by remember { mutableStateOf(false) }
    val next = remember { LocalDate.now().plusYears(1).format(MonthYear) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(EuriseIcons.lock(), null) },
        title = { Text(stringResource(R.string.bk_check_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bk_check_sub), fontSize = 13.sp, lineHeight = 18.sp)
                if (result != true) SecretField(secret, { secret = it; result = null }, stringResource(R.string.backup_secret_field))
                when (result) {
                    true -> Text(stringResource(R.string.bk_check_ok, next), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = privacyColor(10))
                    false -> Text(stringResource(R.string.bk_check_wrong), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    null -> if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                if (result == true) Text(stringResource(R.string.bk_check_code), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (result == true) TextButton(onClick = onDismiss) { Text(stringResource(R.string.bk_check_done), fontWeight = FontWeight.Bold) }
            else TextButton(onClick = { working = true; scope.launch { result = onCheck(secret); working = false } }, enabled = secret.isNotBlank() && !working) {
                Text(stringResource(R.string.bk_check_go), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (result != true) TextButton(onClick = onForgot, enabled = !working) { Text(stringResource(R.string.bk_check_forgot)) }
        },
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
                Text(stringResource(R.string.bk_no_lock), fontSize = 13.sp, lineHeight = 18.sp)
                SecretField(old, { old = it }, stringResource(R.string.backup_secret_field))
                SecretField(new, { new = it }, stringResource(R.string.backup_pass_new))
                if (new.isNotEmpty() && new.length < PhraseStrength.MIN_LENGTH) Text(stringResource(R.string.backup_pass_short), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(old, new) }, enabled = old.isNotBlank() && new.length >= PhraseStrength.MIN_LENGTH) { Text(stringResource(R.string.backup_save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

// ---------------------------------------------------------------- pezzi

/**
 * Una pagina del backup: titolo grande in alto, contenuto che scorre, e in basso, sotto il pollice, i comandi
 * ([bottom]): il principale a tutta larghezza, l'eventuale secondario sopra.
 */
@Composable
private fun BackupPage(
    back: String,
    title: String,
    sub: String? = null,
    onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = if (bottom != null) 180.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") {
                Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                    TextButton(onClick = onBack) {
                        Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(back, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (sub != null) Text(
                        sub, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
                    )
                }
            }
            content()
            item("nav") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }
        if (bottom != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { bottom() }
        }
    }
}

@Composable
private fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Secondary(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Note(text: String, padded: Boolean = true) {
    Text(
        text, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (padded) Modifier.padding(horizontal = 20.dp) else Modifier,
    )
}

@Composable
private fun Warning(title: String?, text: String) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.tertiaryContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val on = MaterialTheme.colorScheme.onTertiaryContainer
        if (title != null) Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = on)
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = on)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = if (selected) scheme.onPrimary else scheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.RadioButton, onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        visualTransformation = secretText(shown), trailingIcon = { SecretEye(shown) { shown = !shown } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
