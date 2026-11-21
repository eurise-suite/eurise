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

internal sealed interface Screen {
    data object List : Screen
    data class Detail(val uuid: String) : Screen
    data class Edit(val uuid: String?, val card: Boolean = false, val prefill: EntryDraft? = null) : Screen
    data object Sync : Screen
    data object Privacy : Screen
    data class ProfileEdit(val uuid: String?) : Screen
}

internal enum class Tab(val label: Int) { All(R.string.pwd_tab_all), Otp(R.string.pwd_tab_otp), Cards(R.string.pwd_tab_cards), MyData(R.string.pwd_tab_mydata), Notes(R.string.pwd_tab_notes), Fix(R.string.pwd_tab_fix) }

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

@Composable
internal fun OpenVault(vm: PasswordViewModel, entries: List<VaultEntry>) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    // Una cassaforte arrivata dal backup accanto a quella di questo telefono: si propone di unirla.
    LaunchedEffect(Unit) { vm.offerRestoredVault() }
    var chooseCloud by remember { mutableStateOf(false) }
    val back: () -> Unit = {
        screen = when (val s = screen) {
            is Screen.Edit -> s.uuid?.let { Screen.Detail(it) } ?: Screen.List
            is Screen.ProfileEdit -> s.uuid?.let { Screen.Detail(it) } ?: Screen.List
            Screen.Privacy -> Screen.Sync
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
        Screen.Sync -> SyncScreen(vm, back, onPrivacy = { screen = Screen.Privacy }, chooseCloudFirst = chooseCloud.also { chooseCloud = false })
        Screen.Privacy -> PrivacyScreen(vm, back, onSettings = { screen = Screen.Sync }, onChooseCloud = { chooseCloud = true; screen = Screen.Sync })
        is Screen.ProfileEdit -> ProfileEdit(entries.firstOrNull { it.uuid == s.uuid }, back) { draft ->
            vm.save(draft, s.uuid)
            back()
        }
    }
}

internal sealed interface Row2 {
    val key: String
    data class Label(override val key: String, val text: Int) : Row2
    data class Item(override val key: String, val entry: VaultEntry, val index: Int, val size: Int) : Row2
}
