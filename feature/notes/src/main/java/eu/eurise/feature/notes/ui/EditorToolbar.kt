package eu.eurise.feature.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.EuriseThemeExtras
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.Mark
import eu.eurise.feature.notes.doc.TextKind

/** Azioni della barra di formattazione. */
internal class ToolbarActions(
    val onToggle: (Mark) -> Unit,
    val onColor: (String?) -> Unit,
    val onHighlight: (String?) -> Unit,
    val onKind: (TextKind) -> Unit,
    val onInsert: () -> Unit,
)

/**
 * Barra di formattazione satinata in basso, come la barra delle sezioni: stile del paragrafo, grassetto,
 * corsivo, sottolineato, colori, elenco di controllo e Inserisci. Il pannello dei colori si apre sopra.
 */
@Composable
internal fun FormatToolbar(
    hazeState: HazeState,
    active: Set<Mark>,
    kind: TextKind?,
    enabled: Boolean,
    actions: ToolbarActions,
    modifier: Modifier = Modifier,
) {
    var colors by remember { mutableStateOf(false) }
    var kinds by remember { mutableStateOf(false) }
    val color = active.filterIsInstance<Mark.Color>().firstOrNull()?.tint
    val highlight = active.filterIsInstance<Mark.Highlight>().firstOrNull()?.tint
    val ink = inkColors()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (colors && enabled) {
            ColorPanel(hazeState, color, highlight, actions)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDefaults.PillHeight)
                .glass(hazeState, RoundedCornerShape(50))
                .padding(GlassDefaults.Padding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                ToolButton(stringResource(R.string.notes_format_style), pressed = kinds, enabled = enabled, onClick = { kinds = true }) {
                    Text("Aa", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = it)
                }
                DropdownMenu(expanded = kinds, onDismissRequest = { kinds = false }) {
                    listOf(
                        TextKind.Paragraph to R.string.notes_kind_text,
                        TextKind.Heading to R.string.notes_kind_heading,
                        TextKind.Subheading to R.string.notes_kind_subheading,
                        TextKind.Bullet to R.string.notes_kind_bullet,
                        TextKind.Numbered to R.string.notes_kind_numbered,
                    ).forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label), fontWeight = if (k == kind) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { kinds = false; if (k != kind) actions.onKind(k) },
                        )
                    }
                }
            }
            ToolButton(stringResource(R.string.notes_format_bold), pressed = Mark.Bold in active, enabled = enabled, onClick = { actions.onToggle(Mark.Bold) }) {
                Text("B", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = it)
            }
            ToolButton(stringResource(R.string.notes_format_italic), pressed = Mark.Italic in active, enabled = enabled, onClick = { actions.onToggle(Mark.Italic) }) {
                Text("I", fontSize = 17.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, color = it)
            }
            ToolButton(stringResource(R.string.notes_format_underline), pressed = Mark.Underline in active, enabled = enabled, onClick = { actions.onToggle(Mark.Underline) }) {
                Text("U", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline, color = it)
            }
            ToolButton(stringResource(R.string.notes_format_colors), pressed = colors, enabled = enabled, onClick = { colors = !colors }) { c ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c, lineHeight = 16.sp)
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(width = 18.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color?.let { ink.text(it) } ?: highlight?.let { ink.highlight(it) } ?: c),
                    )
                }
            }
            ToolButton(stringResource(R.string.notes_kind_check), pressed = kind == TextKind.Check, enabled = enabled, onClick = { actions.onKind(TextKind.Check) }) {
                Icon(EuriseIcons.checklist(), contentDescription = null, tint = it, modifier = Modifier.size(20.dp))
            }
            ToolButton(stringResource(R.string.notes_insert), pressed = false, enabled = true, accent = true, onClick = actions.onInsert) {
                Icon(EuriseIcons.plus(), contentDescription = null, tint = it, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    pressed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
    content: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val extra = EuriseThemeExtras.colors
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        pressed || accent -> scheme.primary
        else -> scheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = GlassDefaults.ItemHeight)
            .then(
                when {
                    pressed -> Modifier.shadow(2.dp, CircleShape).background(extra.glassSelected, CircleShape)
                    accent -> Modifier.background(scheme.primaryContainer, CircleShape)
                    else -> Modifier
                }
            )
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; selected = pressed },
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

/** Colori del testo ed evidenziatore: le otto tinte della suite, più "nessuno". */
@Composable
private fun ColorPanel(hazeState: HazeState, color: String?, highlight: String?, actions: ToolbarActions) {
    val ink = inkColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(hazeState, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.notes_color_text), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwatchRow(selected = color, swatch = { ink.text(it) }, onPick = actions.onColor)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.notes_color_highlight), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwatchRow(selected = highlight, swatch = { ink.highlight(it) }, onPick = actions.onHighlight)
    }
}

@Composable
private fun SwatchRow(selected: String?, swatch: (String) -> androidx.compose.ui.graphics.Color, onPick: (String?) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val none = stringResource(R.string.notes_color_none)
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onPick(null) }
                .semantics { contentDescription = none; this.selected = selected == null },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(22.dp).border(if (selected == null) 2.dp else 1.5.dp, if (selected == null) primary else outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(16.dp).height(1.5.dp).background(outline)) }
        }
        CalendarTint.entries.forEach { tint ->
            val name = tintLabel(tint)
            val isSelected = selected == tint.name
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onPick(tint.name) }
                    .semantics { contentDescription = name; this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 24.dp else 22.dp)
                        .then(if (isSelected) Modifier.border(2.dp, primary, CircleShape).padding(3.dp) else Modifier)
                        .clip(CircleShape)
                        .background(swatch(tint.name)),
                )
            }
        }
    }
}

/** Nome leggibile di una tinta della suite. */
@Composable
internal fun tintLabel(tint: CalendarTint): String = stringResource(
    when (tint) {
        CalendarTint.BluNotte -> R.string.notes_tint_blu_notte
        CalendarTint.Salvia -> R.string.notes_tint_salvia
        CalendarTint.Ottone -> R.string.notes_tint_ottone
        CalendarTint.Borgogna -> R.string.notes_tint_borgogna
        CalendarTint.Petrolio -> R.string.notes_tint_petrolio
        CalendarTint.Prugna -> R.string.notes_tint_prugna
        CalendarTint.Terracotta -> R.string.notes_tint_terracotta
        CalendarTint.Grafite -> R.string.notes_tint_grafite
    }
)

/** Una voce del foglio Inserisci. */
internal data class InsertOption(val label: Int, val icon: () -> ImageVector, val onClick: () -> Unit)

/** Griglia del foglio Inserisci, tre per riga. */
@Composable
internal fun InsertGrid(options: List<InsertOption>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(role = Role.Button, onClick = option.onClick),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(option.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(option.label), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
