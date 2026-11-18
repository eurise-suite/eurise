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

// Una voce: dettaglio, modifica, codici e carte. (diviso da PasswordApp.kt il 18 novembre).

@Composable
internal fun OtpCard(vm: PasswordViewModel, otp: String) {
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
internal fun EntryDetail(vm: PasswordViewModel, entry: VaultEntry, all: List<VaultEntry>, onBack: () -> Unit, onEdit: () -> Unit) {
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
internal fun cardBrand(number: String): String {
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
internal fun EntryEdit(entry: VaultEntry?, startAsCard: Boolean, prefill: EntryDraft?, onBack: () -> Unit, onSave: (EntryDraft) -> Unit) {
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
internal fun CardDetail(vm: PasswordViewModel, entry: VaultEntry, onBack: () -> Unit, onEdit: () -> Unit) {
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
