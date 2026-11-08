package eu.eurise.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.notes.FocusRequest
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.Block
import eu.eurise.feature.notes.doc.DividerBlock
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.NoteLinkBlock
import eu.eurise.feature.notes.doc.TableBlock
import eu.eurise.feature.notes.doc.TextBlock
import eu.eurise.feature.notes.doc.TextKind

/**
 * Carattere invisibile messo davanti al testo di ogni blocco: quando la tastiera lo cancella sappiamo che
 * si è premuto "cancella" con il cursore all'inizio (le tastiere su schermo non lo segnalano altrimenti).
 */
private const val SENTINEL = "​"

/** Azioni dei blocchi di testo verso il ViewModel. */
internal class TextBlockActions(
    val onChange: (Long, String, TextRange) -> Unit,
    val onBackspaceAtStart: (Long) -> Unit,
    val onFocus: (Long?) -> Unit,
    val onToggleChecked: (Long) -> Unit,
    val onAddToTasks: (Long) -> Unit,
)

/** Un blocco di testo: paragrafo, titolo, voce d'elenco o di un elenco di controllo. */
@Composable
internal fun TextBlockField(
    block: TextBlock,
    selection: TextRange?,
    focused: Boolean,
    number: Int,
    focusRequest: FocusRequest?,
    actions: TextBlockActions,
    placeholder: String?,
) {
    val ink = inkColors()
    val requester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest?.blockId == block.id) runCatching { requester.requestFocus() }
    }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = blockStyle(block, baseColor, muted)
    val text = block.text.text
    val sel = selection ?: TextRange(text.length)
    // La parola che la tastiera sta componendo va restituita a ogni giro, o le tastiere con i suggerimenti
    // ripetono le lettere.
    var composition by remember { mutableStateOf<TextRange?>(null) }
    val displayLength = text.length + 1
    val value = TextFieldValue(
        annotatedString = block.text.toAnnotated(ink, prefix = SENTINEL),
        selection = TextRange((sel.start + 1).coerceIn(1, displayLength), (sel.end + 1).coerceIn(1, displayLength)),
        composition = composition?.takeIf { it.max <= displayLength && it.min >= 1 },
    )
    val topPadding = when (block.kind) {
        TextKind.Heading -> 10.dp
        TextKind.Subheading -> 6.dp
        else -> 0.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        verticalAlignment = Alignment.Top,
    ) {
        ListMarker(block, number, style) { actions.onToggleChecked(block.id) }
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    composition = v.composition
                    if (!v.text.startsWith(SENTINEL)) {
                        composition = null
                        // Sparito il segnaposto: o si è cancellato all'inizio del blocco, o si è sostituito
                        // tutto il testo selezionato (segnaposto compreso).
                        if (v.text == text || v.text.isEmpty() && text.isEmpty()) actions.onBackspaceAtStart(block.id)
                        else actions.onChange(block.id, v.text.replace(SENTINEL, ""), TextRange(v.selection.end.coerceAtMost(v.text.length)))
                    } else {
                        val newText = v.text.substring(1)
                        val range = TextRange((v.selection.start - 1).coerceAtLeast(0), (v.selection.end - 1).coerceAtLeast(0))
                        actions.onChange(block.id, newText, range)
                    }
                },
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (block.kind == TextKind.Check) Modifier.heightIn(min = 32.dp).padding(vertical = 4.dp) else Modifier)
                    .focusRequester(requester)
                    .onFocusChanged { if (it.isFocused) actions.onFocus(block.id) },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = muted, textDecoration = null))
                        inner()
                    }
                },
            )
            if (block.kind == TextKind.Check && block.taskId != null) {
                Text(
                    stringResource(R.string.notes_check_in_tasks),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (block.kind == TextKind.Check && block.taskId == null && focused && text.isNotBlank()) {
            IconButton(onClick = { actions.onAddToTasks(block.id) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    EuriseIcons.task(),
                    contentDescription = stringResource(R.string.notes_check_add_to_tasks),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Ogni riga occupa tutta la sua altezza: così lo spazio tra due punti è uguale all'interlinea. */
private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun blockStyle(block: TextBlock, base: Color, muted: Color): TextStyle = when (block.kind) {
    TextKind.Heading -> TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = base)
    TextKind.Subheading -> TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = base)
    TextKind.Check -> if (block.checked) {
        TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = muted, textDecoration = TextDecoration.LineThrough)
    } else TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = base)
    else -> TextStyle(fontSize = 16.sp, lineHeight = 25.sp, color = base)
}.copy(lineHeightStyle = FullLine)

/** Il segno davanti a una voce d'elenco: alto come la prima riga del testo, così resta allineato. */
@Composable
private fun ListMarker(block: TextBlock, number: Int, style: TextStyle, onToggle: () -> Unit) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val leading = Modifier.width(26.dp).height(lineHeight)
    when (block.kind) {
        TextKind.Bullet -> Box(leading, contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(6.dp).background(style.color, CircleShape))
        }
        TextKind.Numbered -> Box(leading, contentAlignment = Alignment.CenterStart) {
            Text("$number.", style = style.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
        TextKind.Check -> CheckBox(block.checked, onToggle)
        else -> Unit
    }
}

/**
 * La nota in sola lettura per la modalità Seleziona: un'unica selezione attraversa titolo, paragrafi e punti
 * degli elenchi, cosa impossibile con un campo di testo per blocco.
 */
@Composable
internal fun SelectableBlocks(title: String, blocks: List<Block>) {
    val ink = inkColors()
    val base = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = base),
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            var number = 0
            blocks.forEach { block ->
                number = if (block is TextBlock && block.kind == TextKind.Numbered) number + 1 else 0
                when (block) {
                    is TextBlock -> {
                        val style = blockStyle(block, base, muted)
                        val current = number
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            DisableSelection { ListMarker(block, current, style) {} }
                            Text(block.text.toAnnotated(ink), style = style, modifier = Modifier.weight(1f).padding(vertical = if (block.kind == TextKind.Check) 4.dp else 0.dp))
                        }
                    }
                    is TableBlock -> Column(Modifier.padding(vertical = 6.dp)) {
                        block.rows.forEach { row -> Text(row.joinToString("  ·  "), fontSize = 14.sp, color = base) }
                    }
                    is NoteLinkBlock -> Text(block.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 6.dp))
                    is DividerBlock -> DividerView()
                    is DrawingBlock -> DisableSelection { DrawingPreview(block, onOpen = {}, modifier = Modifier.padding(vertical = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.notes_check_item)
    val state = stringResource(if (checked) R.string.notes_check_done else R.string.notes_check_open)
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = label; stateDescription = state },
        contentAlignment = Alignment.TopStart,
    ) {
        // A metà della prima riga: 4 dp di margine del testo più 2 per centrare i 20 dp sui 24 della riga.
        val box = Modifier.padding(top = 6.dp).size(20.dp).clip(RoundedCornerShape(5.dp))
        if (checked) {
            Box(box.background(primary), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.check(3f), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(box.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp)))
        }
    }
}

/** Tabella di testo semplice; i comandi per righe e colonne compaiono quando una cella ha il cursore. */
@Composable
internal fun TableBlockView(
    block: TableBlock,
    onCell: (Int, Int, String) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRemove: () -> Unit,
    onFocus: (Long?) -> Unit,
) {
    val focusedCells = remember { mutableStateMapOf<Int, Boolean>() }
    val outline = MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            block.rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = outline)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (r == 0) Modifier.background(outline) else Modifier),
                ) {
                    row.forEachIndexed { c, cell ->
                        BasicTextField(
                            value = cell,
                            onValueChange = { onCell(r, c, it) },
                            textStyle = TextStyle(
                                fontSize = if (r == 0) 12.sp else 14.sp,
                                fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (r == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .onFocusChanged {
                                    focusedCells[r * 100 + c] = it.isFocused
                                    if (it.isFocused) onFocus(block.id)
                                },
                        )
                    }
                }
            }
        }
        if (focusedCells.values.any { it }) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAddRow) { Text(stringResource(R.string.notes_table_add_row)) }
                TextButton(onClick = onAddColumn) { Text(stringResource(R.string.notes_table_add_column)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.notes_table_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Collegamento a un'altra nota: si apre con un tocco, la crocetta lo toglie. */
@Composable
internal fun NoteLinkView(block: NoteLinkBlock, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, end = 4.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(EuriseIcons.link(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Text(
                block.title.ifBlank { stringResource(R.string.notes_untitled) },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.notes_link_remove), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
internal fun DividerView() {
    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
