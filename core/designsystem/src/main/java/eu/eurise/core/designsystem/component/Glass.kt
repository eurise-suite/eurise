package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseThemeExtras

/** Misure comuni delle pillole di vetro: stessa altezza e arrotondamento in alto e in basso. */
object GlassDefaults {
    /** Altezza delle voci dentro una pillola; con il margine interno di 4 dp la pillola è alta 52. */
    val ItemHeight = 44.dp
    val Padding = 4.dp
    val PillHeight = ItemHeight + Padding * 2
    val BottomMargin = 16.dp
    val SideMargin = 16.dp
}

/**
 * Spazio da lasciare in fondo alle liste perché l'ultimo elemento non resti nascosto dietro la barra
 * delle sezioni. Lo fornisce il guscio dell'app.
 */
val LocalDockClearance = compositionLocalOf { 0.dp }

/**
 * Vetro satinato: sfoca ciò che scorre dietro (registrato con `hazeSource` sullo stesso [state]) e lo
 * vela con la tinta del tema. Sotto Android 12 la sfocatura diventa un velo semitrasparente.
 *
 * @param outline filo sottile attorno al vetro; la Scrivania usa il vetro piatto, senza filo.
 */
@Composable
fun Modifier.glass(state: HazeState, shape: Shape, elevation: Dp = 10.dp, outline: Boolean = true): Modifier {
    val extra = EuriseThemeExtras.colors
    val style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tints = listOf(HazeTint(extra.glass)),
        blurRadius = 22.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(extra.glass.copy(alpha = 0.94f)),
    )
    val shadowColor = if (extra.isDark) Color.Black.copy(alpha = 0.5f) else Color(0xFF1E1D1B).copy(alpha = 0.18f)
    return this
        .shadow(elevation, shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .hazeEffect(state = state, style = style)
        .then(if (outline) Modifier.border(1.dp, extra.glassOutline, shape) else Modifier)
}

@Immutable
data class DockItem(val label: String, val icon: (stroke: Float) -> ImageVector)

/**
 * Barra delle sezioni flottante e satinata, con il pulsante dell'azione principale (+) accanto.
 * La sezione attiva si riconosce dal colore d'accento, dal tratto più marcato e dall'etichetta in grassetto.
 */
@Composable
fun EuriseDock(
    items: List<DockItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = GlassDefaults.SideMargin, vertical = GlassDefaults.BottomMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pill = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(GlassDefaults.PillHeight)
                .glass(hazeState, pill)
                .padding(GlassDefaults.Padding)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    // Con cinque voci la parola in grassetto non entra: la voce attiva si prende un po' di spazio in più.
                    modifier = Modifier
                        .weight(if (active && items.size >= 5) 1.25f else 1f)
                        .height(GlassDefaults.ItemHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        item.icon(if (active) EuriseIcons.StrokeSelected else EuriseIcons.Stroke),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        item.label,
                        color = color,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (onAction != null) {
            Box(
                modifier = Modifier
                    .size(GlassDefaults.PillHeight)
                    .glass(hazeState, CircleShape)
                    .selectable(selected = false, role = Role.Button, onClick = onAction)
                    .semantics { if (actionLabel != null) contentDescription = actionLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(EuriseIcons.plus(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Selettore a pillola di vetro (per esempio le viste del Calendario). La voce attiva è una capsula chiara
 * con il testo in accento e in grassetto: per le sole parole serve un segno di forma, non solo il colore.
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val extra = EuriseThemeExtras.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassDefaults.PillHeight)
            .glass(hazeState, RoundedCornerShape(50), elevation = 6.dp)
            .padding(GlassDefaults.Padding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(GlassDefaults.ItemHeight)
                    .then(if (active) Modifier.shadow(2.dp, CircleShape).background(extra.glassSelected, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
