package eu.eurise.password.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.data.AliasProvider
import eu.eurise.password.data.PendingLogin
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.Vault
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.logic.Cards
import eu.eurise.password.logic.Generator
import eu.eurise.password.ui.RoundIcon
import eu.eurise.password.ui.UnlockScreen
import eu.eurise.password.ui.strengthName
import kotlinx.coroutines.launch

/** Foglio che compare sopra un'altra app: sblocca, poi restituisce a Android i valori scelti per quel modulo. */
class FillActivity : ComponentActivity() {
    private val repo by lazy { VaultRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (intent.getBooleanExtra(EXTRA_SAVE, false)) {
            val pending = repo.pendingLogin
            repo.pendingLogin = null
            if (pending == null) { finish(); return }
            setContent { EuriseTheme { SaveSheet(pending, onClose = ::finish) } }
            return
        }
        val form = readForm()
        if (form == null) { finish(); return }
        setContent { EuriseTheme { FillSheet(form, onCancel = ::finish, onDone = ::finishWith) } }
    }

    override fun onStart() { super.onStart(); repo.enter() }
    override fun onStop() { super.onStop(); repo.leave() }

    @Suppress("DEPRECATION")
    private fun readForm(): Form? {
        val ids = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_IDS) ?: return null
        val all = FieldKind.entries
        val kinds = intent.getIntArrayExtra(EXTRA_KINDS)?.map { all[it] } ?: return null
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        return Form(ids, kinds, intent.getStringExtra(EXTRA_DOMAIN), pkg)
    }

    private fun finishWith(dataset: Dataset?) {
        if (dataset != null) setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    companion object {
        const val EXTRA_SAVE = "save"
        const val EXTRA_IDS = "ids"
        const val EXTRA_KINDS = "kinds"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_PACKAGE = "package"
    }
}

@Composable
private fun FillSheet(form: Form, onCancel: () -> Unit, onDone: (Dataset?) -> Unit, vm: PasswordViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onCancel), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(scheme.outlineVariant))
            when (val s = state) {
                VaultState.Loading -> Unit
                VaultState.Empty -> Text(stringResource(R.string.pwd_fill_no_vault), modifier = Modifier.padding(20.dp))
                VaultState.Locked -> UnlockScreen(vm, compact = true)
                is VaultState.Open -> OpenSheet(form, s.entries, vm, onDone)
            }
        }
    }
}

/** Android propone di salvare un accesso appena digitato: voce nuova, o nuova password per quella che c'era. */
@Composable
private fun SaveSheet(pending: PendingLogin, onClose: () -> Unit, vm: PasswordViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}.navigationBarsPadding().padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                VaultState.Loading -> Unit
                VaultState.Empty -> Text(stringResource(R.string.pwd_fill_no_vault), modifier = Modifier.padding(20.dp))
                VaultState.Locked -> UnlockScreen(vm, compact = true)
                is VaultState.Open -> {
                    val target = pending.webDomain ?: pending.packageName
                    val mine = s.entries.filter { e ->
                        e.card == null && e.username.equals(pending.username, ignoreCase = true) &&
                            if (pending.webDomain != null) Matching.domainMatches(e.url, pending.webDomain) else pending.packageName in e.apps
                    }
                    val existing = mine.singleOrNull()
                    if (mine.any { it.password == pending.password }) {
                        LaunchedEffect(Unit) { onClose() }
                    } else {
                        SheetTitle(if (existing != null) stringResource(R.string.pwd_save_update, existing.title) else stringResource(R.string.pwd_save_title, target))
                        if (pending.username.isNotEmpty()) Text(pending.username, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Button(onClick = { vm.saveLogin(pending, existing); onClose() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.pwd_save_confirm)) }
                            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pwd_save_skip)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSheet(form: Form, entries: List<VaultEntry>, vm: PasswordViewModel, onDone: (Dataset?) -> Unit) {
    val matches = remember(entries) { entries.filter(form::matches) }
    var register by remember { mutableStateOf(form.isSignUp && matches.isEmpty() && !form.hasCard) }
    when {
        form.hasCard && !form.hasLogin -> CardPicker(form, entries.filter { it.card != null }, vm, onDone)
        register -> RegisterSheet(form, vm, onDone)
        else -> LoginPicker(form, entries, matches, vm, onDone) { register = true }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun PickRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title.ifBlank { stringResource(R.string.pwd_untitled) }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginPicker(form: Form, entries: List<VaultEntry>, matches: List<VaultEntry>, vm: PasswordViewModel, onDone: (Dataset?) -> Unit, onRegister: () -> Unit) {
    val context = LocalContext.current
    // In un'app senza voci collegate si può scegliere tra tutte, e la scelta resta come collegamento; sul web mai.
    val forApp = form.webDomain == null && matches.isEmpty()
    val shown = if (forApp) entries.filter { it.card == null && it.password.isNotEmpty() }.sortedBy { it.title.lowercase() } else matches
    SheetTitle(stringResource(R.string.pwd_fill_choose))
    if (forApp) Text(stringResource(R.string.pwd_fill_link_app, form.packageName), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
    if (shown.isEmpty()) {
        Text(stringResource(R.string.pwd_fill_none, form.target), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
    } else {
        GroupCard {
            shown.forEachIndexed { i, e ->
                if (i > 0) CardDivider()
                PickRow(e.title, e.username) {
                    if (forApp) vm.linkApp(e.uuid, form.packageName)
                    vm.markUsed(e.uuid)
                    onDone(form.loginDataset(context, e.username, e.password, e.title))
                }
            }
        }
    }
    TextButton(onClick = onRegister, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.pwd_fill_new_account)) }
}

@Composable
private fun CardPicker(form: Form, cards: List<VaultEntry>, vm: PasswordViewModel, onDone: (Dataset?) -> Unit) {
    val context = LocalContext.current
    SheetTitle(stringResource(R.string.pwd_fill_choose_card))
    if (cards.isEmpty()) {
        Text(stringResource(R.string.pwd_fill_no_card), modifier = Modifier.padding(horizontal = 20.dp))
    } else {
        GroupCard {
            cards.forEachIndexed { i, e ->
                if (i > 0) CardDivider()
                val card = e.card!!
                PickRow(e.title, stringResource(R.string.pwd_card_row, Cards.lastFour(card.number))) {
                    vm.confirmBio(context) { onDone(form.cardDataset(context, card, e.title)) }
                }
            }
        }
    }
}

@Composable
private fun RegisterSheet(form: Form, vm: PasswordViewModel, onDone: (Dataset?) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val aliases = vm.aliases
    var email by remember { mutableStateOf(if (aliases.provider == AliasProvider.Fixed) aliases.fixedAddress.orEmpty() else "") }
    var isAlias by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf(Generator.passphrase()) }
    val strength = remember(password) { Generator.strength(password) }

    fun makeAlias() {
        working = true
        failed = false
        scope.launch {
            val created = aliases.create(form.target)
            working = false
            if (created != null) { email = created; isAlias = true } else failed = true
        }
    }
    LaunchedEffect(Unit) { if (aliases.generates) makeAlias() }

    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.key(), contentDescription = null, tint = scheme.tertiaryContainer, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(stringResource(R.string.pwd_reg_title, form.target), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(if (form.webDomain != null) R.string.pwd_reg_verified else R.string.pwd_reg_app), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.secondary)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; isAlias = false },
            singleLine = true,
            label = { Text(stringResource(if (isAlias) R.string.pwd_reg_alias else R.string.pwd_reg_email)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (working) Text(stringResource(R.string.pwd_reg_alias_working), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        if (failed) Text(stringResource(R.string.pwd_reg_alias_failed), fontSize = 13.sp, color = scheme.error)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(stringResource(R.string.pwd_reg_password, strengthName(strength))) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                RoundIcon(EuriseIcons.refresh(), stringResource(R.string.pwd_generate)) {
                    password = if (phrase) Generator.passphrase() else Generator.password()
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            eu.eurise.core.designsystem.component.FilterPill(stringResource(R.string.pwd_gen_phrase), dot = null, selected = phrase) { phrase = true; password = Generator.passphrase() }
            eu.eurise.core.designsystem.component.FilterPill(stringResource(R.string.pwd_gen_chars), dot = null, selected = !phrase) { phrase = false; password = Generator.password() }
        }
        Button(
            onClick = {
                val url = form.webDomain?.let { "https://$it" } ?: (Vault.APP_SCHEME + form.packageName)
                vm.save(EntryDraft(form.target, email.trim(), password, url, "", null), null)
                onDone(form.loginDataset(context, email.trim(), password, form.target))
            },
            enabled = email.isNotBlank() && !working,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(stringResource(R.string.pwd_reg_fill)) }
        if (aliases.fixedAddress != null || aliases.generates) {
            TextButton(onClick = { email = aliases.fixedAddress.orEmpty(); isAlias = false }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pwd_reg_own))
            }
        }
        if (aliases.generates && !isAlias && !working) {
            TextButton(onClick = ::makeAlias, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pwd_reg_alias_retry)) }
        }
    }
}
