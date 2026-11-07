package eu.eurise.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.feature.notes.ConflictUi
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.MergeChoice
import eu.eurise.feature.notes.doc.NoteHtml
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "Cambiata in due posti": la stessa parte della nota è stata cambiata sul telefono e sul cloud.
 * Si sceglie un punto per volta; il foglio non si chiude finché non si è scelto tutto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConflictSheet(conflict: ConflictUi, onChoose: (MergeChoice) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = {}, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(stringResource(R.string.notes_conflict_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (conflict.total > 1) {
                        Text(
                            stringResource(R.string.notes_conflict_step, conflict.step + 1, conflict.total),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(stringResource(R.string.notes_conflict_message), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (local, remote) = if (conflict.choosingTitle) {
                conflict.result.titleConflict!!.let { (l, r) -> l to r }
            } else {
                val part = conflict.current
                (part?.local?.let(::describe) ?: "") to (part?.remote?.let(::describe) ?: "")
            }
            Version(stringResource(R.string.notes_conflict_here), null, local) { onChoose(MergeChoice.Local) }
            Version(stringResource(R.string.notes_conflict_cloud), whenText(conflict.remoteUpdated), remote) { onChoose(MergeChoice.Remote) }
            TextButton(
                onClick = { onChoose(MergeChoice.Both) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            ) { Text(stringResource(R.string.notes_conflict_both), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun Version(label: String, time: String?, text: String, onKeep: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Text(label.uppercase(Locale.getDefault()), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            time?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(
            text.ifBlank { stringResource(R.string.notes_conflict_empty) },
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        TextButton(
            onClick = onKeep,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).height(36.dp),
        ) { Text(stringResource(R.string.notes_conflict_keep), fontWeight = FontWeight.SemiBold) }
    }
}

/** Il testo dei blocchi in conflitto, una riga per blocco; le voci d'elenco con il loro segno. */
private fun describe(blocks: List<Block>): String = blocks.joinToString("\n") { b ->
    if (b is TextBlock) {
        when (b.kind) {
            TextKind.Check -> (if (b.checked) "[x] " else "[ ] ") + b.text.text
            TextKind.Bullet, TextKind.Numbered -> "– " + b.text.text
            else -> b.text.text
        }
    } else NoteHtml.plainText(listOf(b)).ifBlank { "…" }
}

@Composable
private fun whenText(millis: Long): String? {
    if (millis <= 0) return null
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) stringResource(R.string.notes_conflict_today, t.format(DateTimeFormatter.ofPattern("H:mm")))
    else t.format(DateTimeFormatter.ofPattern("d MMM, H:mm", Locale.getDefault()))
}
