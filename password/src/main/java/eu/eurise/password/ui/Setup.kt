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

// Prima apertura, sblocco e stato della cassaforte. (diviso da PasswordApp.kt il 18 novembre).

@Composable
internal fun StatusLines(vm: PasswordViewModel) {
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
internal fun SetupScreen(vm: PasswordViewModel) {
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
internal fun CloudChoiceDialog(onDismiss: () -> Unit, onChoice: (String?) -> Unit) {
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
