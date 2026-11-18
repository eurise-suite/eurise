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

// Importazione da altri gestori. (diviso da PasswordApp.kt il 18 novembre).

/** Un solo pulsante: il tipo di file si riconosce da solo, e prima di importare si vede che cosa contiene. */
@Composable
internal fun ImportButton(vm: PasswordViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readImport) }
    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.pwd_import_action)) }
}

/** Sta sopra ogni schermata, perché un file può arrivare da fuori mentre si è altrove nell'app. */
@Composable
internal fun ImportDialogs(vm: PasswordViewModel) {
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
