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

// Pezzi comuni delle schermate di Password. (diviso da PasswordApp.kt il 18 novembre).

internal fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

// --- Prima apertura e sblocco ---

@Composable
internal fun SecretField(value: String, label: String, onChange: (String) -> Unit, visibleByDefault: Boolean = false) {
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
internal fun KeyMark() {
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(EuriseIcons.key(), contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
internal fun CenteredForm(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) { content() }
}

/** L'ora che scorre: aggiorna i codici 2FA una volta al secondo. */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    return now
}

internal fun rowShape(index: Int, size: Int): Shape {
    val top = if (index == 0) 18.dp else 0.dp
    val bottom = if (index == size - 1) 18.dp else 0.dp
    return RoundedCornerShape(top, top, bottom, bottom)
}

internal fun hostOf(url: String): String =
    url.trim().substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")

@Composable
internal fun Avatar(title: String, size: Int, corner: Int, fontSize: Int) {
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
internal fun BackHeader(label: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
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
internal fun PillAction(label: String, onClick: () -> Unit) {
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
internal fun FieldRow(label: String, value: String, hidden: Boolean = false, end: @Composable () -> Unit) {
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
internal fun InfoRow(icon: ImageVector, title: String, detail: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Column {
            Text(title, fontSize = 15.sp)
            Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** Cambia ogni volta che l'app torna in primo piano: per rileggere le scelte fatte nelle impostazioni di Android. */
@Composable
internal fun lifecycleTick(): Int {
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
internal fun InfoDialog(title: Int, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.pwd_import_ok)) } },
    )
}

// --- I miei dati ---
