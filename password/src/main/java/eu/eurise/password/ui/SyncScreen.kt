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
import eu.eurise.core.designsystem.component.SwitchRow
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

// Sincronizzazione e alias. (diviso da PasswordApp.kt il 18 novembre).

@Composable
internal fun SyncScreen(vm: PasswordViewModel, onBack: () -> Unit, onPrivacy: () -> Unit = {}, chooseCloudFirst: Boolean = false) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sync by vm.sync.collectAsStateWithLifecycle()
    // Dalla privacy, accendendo la sincronizzazione senza un cloud: si apre subito la scelta del cloud.
    var choosingCloud by remember { mutableStateOf(chooseCloudFirst) }
    val waiting by vm.waitingBrowser.collectAsStateWithLifecycle()
    val on = sync !is SyncState.Off
    var explain by remember { mutableStateOf(false) }
    val (label, tint) = syncLabel(sync)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackHeader(stringResource(R.string.pwd_back), onBack)
        Text(stringResource(R.string.pwd_settings), fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        val bioOn by vm.bioOn.collectAsStateWithLifecycle()
        GroupCard { PrivacySummaryRow(vm, onPrivacy) }
        GroupCard {
            SwitchRow(
                stringResource(R.string.pwd_bio_switch), bioOn, RowPad,
                hint = stringResource(if (vm.bio.available) R.string.pwd_bio_detail else R.string.pwd_bio_unavailable), enabled = vm.bio.available,
            ) { if (it) vm.enableBio(context) else vm.disableBio() }
            CardDivider()
            val breachOn by vm.breachOn.collectAsStateWithLifecycle()
            val breachLast by vm.breachLast.collectAsStateWithLifecycle()
            val breachFailed by vm.breachFailed.collectAsStateWithLifecycle()
            var explainBreach by remember { mutableStateOf(false) }
            val lastText = if (breachLast > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(breachLast)) else stringResource(R.string.pwd_breach_never)
            SwitchRow(
                stringResource(R.string.pwd_breach_switch), breachOn, RowPad,
                hint = if (breachOn) stringResource(R.string.pwd_breach_on, lastText) else stringResource(R.string.pwd_breach_off),
                trailing = if (breachOn) ({ RoundIcon(EuriseIcons.refresh(), stringResource(R.string.pwd_breach_now)) { vm.checkBreaches() } }) else null,
            ) { if (it) explainBreach = true else vm.setBreach(false) }
            if (breachOn && breachFailed) Text(stringResource(R.string.pwd_breach_failed), fontSize = 12.sp, color = scheme.error, modifier = Modifier.padding(bottom = 8.dp))
            if (explainBreach) {
                eu.eurise.core.designsystem.component.PrivacyConsentSheet(
                    stringResource(R.string.pwd_pv_breach_q), stringResource(R.string.pwd_pv_breach_read), stringResource(R.string.pwd_pv_breach_out),
                    stringResource(R.string.pwd_pv_breach_who), stringResource(R.string.pwd_pv_back), PwdPrivacy.BREACH,
                    eu.eurise.core.designsystem.component.Jurisdiction.UK,
                    onDismiss = { explainBreach = false }, onAccept = { explainBreach = false; vm.setBreach(true) },
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
            val detail = if (on) stringResource(R.string.pwd_sync_on_account, vm.repo.syncAccountName.orEmpty()) else stringResource(R.string.pwd_sync_off_detail)
            SwitchRow(stringResource(R.string.pwd_sync_switch), on, RowPad, hint = detail, enabled = !waiting) { if (it) explain = true else vm.disableSync() }
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
        eu.eurise.core.designsystem.component.PrivacyConsentSheet(
            stringResource(R.string.pwd_pv_sync_q), stringResource(R.string.pwd_pv_sync_read), stringResource(R.string.pwd_pv_sync_out),
            stringResource(R.string.pwd_pv_sync_who), stringResource(R.string.pwd_pv_sync_back), PwdPrivacy.SYNC,
            eu.eurise.core.designsystem.component.Jurisdiction.EU,
            onDismiss = { explain = false },
            onAccept = {
                explain = false
                if (vm.hasSyncAccount) vm.enableSync(null) { openUrl(context, it) } else choosingCloud = true
            },
        )
    }
    if (choosingCloud) {
        CloudChoiceDialog(onDismiss = { choosingCloud = false }) { server ->
            choosingCloud = false
            vm.enableSync(server) { openUrl(context, it) }
        }
    }
}

@Composable
internal fun AliasSettings(vm: PasswordViewModel) {
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

/** Lo spazio delle righe con interruttore dentro le schede delle impostazioni (SwitchRow comune della suite). */
private val RowPad = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp)
