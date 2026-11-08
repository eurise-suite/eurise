package eu.eurise.feature.tasks

import eu.eurise.core.data.model.QuickAdd
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDateTime

/**
 * Aggiunta rapida "come si parla": i pezzi riconosciuti (giorno, ora, ripetizione, importanza, elenco) si
 * colorano nel testo e compaiono sotto come pillole; toccandone una la si toglie dal testo.
 *
 * @param onAdd riceve quanto letto e se aprire poi la scheda completa ("Dettagli").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddSheet(
    lists: List<TaskList>,
    now: LocalDateTime,
    onAdd: (QuickAdd.Result, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, lists) { QuickAdd.parse(text, now, lists.map { it.name }) }
    val focus = remember { FocusRequester() }
    val scheme = MaterialTheme.colorScheme
    val highlight = scheme.primaryContainer
    val (importantInk, importantFill) = importantColors()
    val listTint = tintColor(lists.firstOrNull { it.name == parsed.listName }?.tint)
    val submit = { details: Boolean ->
        if (parsed.title.isNotBlank()) {
            onAdd(parsed, details)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.tasks_quick_hint), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.tasks_quick_placeholder)) },
                visualTransformation = { original ->
                    // I pezzi riconosciuti si vedono evidenziati nel testo stesso.
                    val annotated = buildAnnotatedString {
                        append(original.text)
                        parsed.pieces.filter { it.end <= original.text.length }.forEach { p ->
                            val bg = when (p.kind) {
                                QuickAdd.Kind.Priority -> importantFill
                                QuickAdd.Kind.List -> listTint.copy(alpha = 0.18f)
                                else -> highlight
                            }
                            addStyle(SpanStyle(background = bg, fontWeight = FontWeight.SemiBold), p.start, p.end)
                        }
                    }
                    TransformedText(annotated, OffsetMapping.Identity)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit(false) }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = scheme.surfaceContainerLow,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    focusedBorderColor = scheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            if (parsed.pieces.isNotEmpty()) {
                Text(stringResource(R.string.tasks_quick_understood), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = now.toLocalDate()
                    val removable = parsed.pieces
                    removable.forEach { piece ->
                        val label = when (piece.kind) {
                            QuickAdd.Kind.Date -> parsed.due?.let { d ->
                                dueLabel(d, today).text + (parsed.time?.let { " " + it.format(TimeFormat) } ?: "")
                            }
                            QuickAdd.Kind.Time -> if (parsed.pieces.any { it.kind == QuickAdd.Kind.Date }) null
                            else parsed.time?.let { t -> (parsed.due?.let { dueLabel(it, today).text + " " } ?: "") + t.format(TimeFormat) }
                            QuickAdd.Kind.Repeat -> repeatLabel(parsed.repeat)
                            QuickAdd.Kind.Priority -> stringResource(
                                if (parsed.priority == Task.PRIORITY_MEDIUM) R.string.tasks_priority_medium else R.string.tasks_important
                            )
                            QuickAdd.Kind.List -> parsed.listName
                        } ?: return@forEach
                        PieceChip(
                            label = label,
                            color = when (piece.kind) {
                                QuickAdd.Kind.Priority -> importantInk
                                else -> scheme.onSurface
                            },
                            dot = if (piece.kind == QuickAdd.Kind.List) listTint else null,
                            removeLabel = stringResource(R.string.tasks_quick_remove, label),
                        ) {
                            text = (text.substring(0, piece.start) + text.substring(piece.end)).replace(Regex("\\s{2,}"), " ").trim()
                        }
                    }
                    if (parsed.time != null) {
                        Row(
                            Modifier.height(34.dp).clip(CircleShape).background(scheme.surfaceVariant).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(EuriseIcons.bell(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.tasks_quick_alert, parsed.time!!.format(TimeFormat)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { submit(true) }, enabled = parsed.title.isNotBlank()) {
                    Text(stringResource(R.string.tasks_quick_details), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { submit(false) }, enabled = parsed.title.isNotBlank()) {
                    Text(stringResource(R.string.tasks_quick_add), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun PieceChip(label: String, color: Color, dot: Color?, removeLabel: String, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .clickable(onClickLabel = removeLabel, onClick = onRemove)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
        Icon(EuriseIcons.close(), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
    }
}
