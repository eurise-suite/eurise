package eu.eurise.feature.notes.lock

import eu.eurise.core.designsystem.component.SecretEye
import eu.eurise.core.designsystem.component.secretText
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.PrivacyMeter
import eu.eurise.core.designsystem.component.SwitchRow
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.R

/**
 * Foglio «Blocca questa nota» (tavola NoteBlocca): cosa cambia, il titolo nell'elenco, poi Blocca. Senza backup attivo
 * (tavola NoteBloccaSenzaBackup) avvisa che la nota non si riaprirebbe più togliendo il blocco schermo o cambiando
 * telefono, e propone «Attiva il backup» ([onBackup]) accanto a «Blocca lo stesso».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LockSheet(backupOn: Boolean, onLock: (hideTitle: Boolean) -> Unit, onBackup: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val canLock = NoteLock.canLock(context)
    var showTitle by rememberSaveable { mutableStateOf(true) }
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.background,
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(stringResource(R.string.notes_lock_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.notes_lock_sub), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLowest).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!backupOn) {
                    LockPoint(EuriseIcons.lock(), R.string.notes_lock_bound, R.string.notes_lock_bound_sub)
                    LockPoint(EuriseIcons.cloud(), R.string.notes_lock_point_cloud, R.string.notes_lock_cloud_short)
                } else {
                    LockPoint(EuriseIcons.key(), R.string.notes_lock_point_phone, R.string.notes_lock_point_phone_sub)
                    LockPoint(EuriseIcons.lock(), R.string.notes_lock_point_open, R.string.notes_lock_point_open_sub)
                    LockPoint(EuriseIcons.cloud(), R.string.notes_lock_point_cloud, R.string.notes_lock_point_cloud_sub)
                    LockPoint(EuriseIcons.eyeOff(), R.string.notes_lock_point_title, R.string.notes_lock_point_title_sub)
                    LockPoint(EuriseIcons.shield(), R.string.notes_lock_point_backup, R.string.notes_lock_point_backup_sub)
                }
            }
            if (canLock && !backupOn) {
                NoBackupWarning()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onLock(false) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.notes_lock_anyway), fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = onBackup, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.notes_lock_turn_on_backup), fontWeight = FontWeight.Bold)
                    }
                }
                Text(stringResource(R.string.notes_lock_no_backup_foot), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            } else if (canLock) {
                SwitchRow(
                    stringResource(R.string.notes_lock_show_title),
                    showTitle,
                    hint = stringResource(R.string.notes_lock_show_title_sub),
                ) { showTitle = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrivacyMeter(10)
                    Text(stringResource(R.string.notes_lock_privacy), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.notes_lock_not_now), fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { onLock(!showTitle) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(stringResource(R.string.notes_lock_do), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(stringResource(R.string.notes_lock_needs_screen_lock), fontSize = 14.sp, lineHeight = 20.sp)
                Button(
                    onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.notes_lock_open_settings), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Il riquadro arancio «Il backup non è attivo» (tavola NoteBloccaSenzaBackup). */
@Composable
private fun NoBackupWarning() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.tertiaryContainer).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(scheme.tertiary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.warning(), contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notes_lock_no_backup), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onTertiaryContainer)
            Text(stringResource(R.string.notes_lock_no_backup_sub), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onTertiaryContainer)
        }
    }
}

/**
 * «Riapri le note bloccate» (tavola NoteRiapri): il blocco schermo è stato tolto e Android ha cancellato la chiave; con la
 * frase del backup (o il codice di recupero) si riapre la copia tenuta sul telefono, poi l'impronta o il PIN nuovi.
 */
@Composable
internal fun ReopenNotesView(busy: Boolean, wrong: Boolean, onReopen: (String) -> Unit, onNotNow: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var secret by rememberSaveable { mutableStateOf("") }
    var useCode by rememberSaveable { mutableStateOf(false) }
    val canLock = NoteLock.canLock(context)
    Column(
        Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(EuriseIcons.chevronLeft(), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.notes_reopen_back), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(26.dp))
                }
                Text(stringResource(R.string.notes_reopen_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
                Text(stringResource(R.string.notes_reopen_text), fontSize = 14.sp, lineHeight = 21.sp, color = scheme.onSurfaceVariant)
                if (!canLock) {
                    Text(stringResource(R.string.notes_lock_needs_screen_lock), fontSize = 14.sp, lineHeight = 20.sp)
                    Button(
                        onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.notes_lock_open_settings), fontWeight = FontWeight.Bold) }
                } else {
                    var shown by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text(stringResource(if (useCode) R.string.notes_reopen_field_code else R.string.notes_reopen_field)) },
                        singleLine = true,
                        isError = wrong,
                        supportingText = if (wrong) { { Text(stringResource(R.string.notes_reopen_wrong)) } } else null,
                        visualTransformation = secretText(shown), trailingIcon = { SecretEye(shown) { shown = !shown } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (secret.isNotBlank()) onReopen(secret) }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { useCode = !useCode; secret = "" }, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(stringResource(if (useCode) R.string.notes_reopen_use_phrase else R.string.notes_reopen_use_code), fontWeight = FontWeight.SemiBold)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLowest).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LockPoint(EuriseIcons.lock(), R.string.notes_reopen_point_after, R.string.notes_reopen_point_after_sub)
                    LockPoint(EuriseIcons.phone(), R.string.notes_reopen_point_phone, R.string.notes_reopen_point_phone_sub)
                }
            }
        }
        // Comando principale a destra, sotto il pollice (come nella tavola).
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onNotNow, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(stringResource(R.string.notes_lock_not_now), fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { onReopen(secret) },
                enabled = canLock && secret.isNotBlank() && !busy,
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.notes_reopen_do), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LockPoint(icon: ImageVector, title: Int, text: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(text), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * La nota bloccata e chiusa (tavola NoteBloccata): lucchetto, «Sblocca» con l'impronta, «Usa il PIN del telefono».
 * [missingKey]: la chiave non è su questo telefono (si recupera solo dal backup della suite).
 */
@Composable
internal fun LockedNoteView(missingKey: Boolean, onUnlock: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.lock(), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(stringResource(R.string.notes_locked), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (missingKey) R.string.notes_locked_missing_key else R.string.notes_locked_hint),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        if (!missingKey) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 52.dp)) {
                Icon(EuriseIcons.lock(), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.notes_unlock), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            TextButton(onClick = onUnlock, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(R.string.notes_unlock_pin), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
