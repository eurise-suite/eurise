package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.util.Locale

/**
 * Pillola delle viste e dei filtri sotto le testate (taccuini nelle Note, viste ed elenchi nelle Attività).
 * Selezionata prende il colore principale; [dot] è il pallino della tinta; [closable] aggiunge la crocetta.
 */
@Composable
fun FilterPill(label: String, dot: Color?, selected: Boolean, closable: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerLow)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) scheme.onPrimary else dot))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
        )
        if (closable) Icon(EuriseIcons.close(), contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
    }
}

/** Etichetta di gruppo in maiuscolo sopra le schede bianche; annunciata come titolo dal lettore dello schermo. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = color,
        modifier = modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp).semantics { heading() },
    )
}

/** Linea sottile tra le righe di una scheda bianca. */
@Composable
fun CardDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

/**
 * Scheda bianca arrotondata che raccoglie righe (attività, campi, collegamenti), staccata dai bordi dello schermo.
 * Il contenuto parte a 12 dp dal bordo sinistro della scheda, lasciando spazio alle caselle da 44 dp.
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 12.dp),
    ) { content() }
}

/**
 * Allarga il contenuto di [horizontal] per lato oltre il margine del contenitore: serve a mettere schede e
 * sezioni comuni (che hanno i propri margini) dentro schermate con margini più larghi, come l'editor delle note.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wide)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, placeable.height) { placeable.place(-extra / 2, 0) }
}
