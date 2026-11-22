package eu.eurise.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Lo schedario della suite (tavole BrwSchedario, OffSchedarioComputer e OffSchedarioTelefono, approvate il 22
 * novembre): la scheda aperta è una linguetta dello stesso colore del foglio che apre e si salda a lui con due piedini
 * concavi, come una cartella tirata avanti; le altre sono linguette basse dietro, senza fondo, divise da un filo.
 * Solo per pagine o documenti diversi (schede del Browser, documenti, fogli di un file); i filtri e le scelte tra due
 * modi restano pillole (tavola SchedarioProve).
 */

/**
 * Il contorno della linguetta aperta, larga [w] e alta [h]: angoli tondi di raggio [r] dal lato lontano dal foglio e
 * due piedini concavi, larghi [r], che escono ai lati ([down]: il foglio sta sopra e la linguetta pende).
 */
fun folderPath(w: Float, h: Float, r: Float, down: Boolean): Path {
    fun y(v: Float) = if (down) h - v else v
    return Path().apply {
        moveTo(-r, y(h))
        quadraticTo(0f, y(h), 0f, y(h - r))
        lineTo(0f, y(r))
        quadraticTo(0f, y(0f), r, y(0f))
        lineTo(w - r, y(0f))
        quadraticTo(w, y(0f), w, y(r))
        lineTo(w, y(h - r))
        quadraticTo(w, y(h), w + r, y(h))
        close()
    }
}

/** Il fondo della linguetta aperta, con i piedini fuori dai suoi bordi (lasciare [radius] di spazio ai lati). */
fun Modifier.folderBackground(color: Color, down: Boolean, radius: Dp = 10.dp): Modifier =
    drawBehind { drawPath(folderPath(size.width, size.height, radius.toPx(), down), color) }

/** Il filo tra due linguette chiuse, sul bordo sinistro. */
fun Modifier.folderDivider(color: Color): Modifier = drawBehind {
    val h = 16.dp.toPx()
    drawLine(color, Offset(-1.dp.toPx(), (size.height - h) / 2), Offset(-1.dp.toPx(), (size.height + h) / 2), 1.dp.toPx())
}

/**
 * Una fila di linguette a schedario: [labels] in ordine, [selected] quella aperta, del colore [sheet] del foglio sotto
 * (o sopra, con [down]). Tocco = apri; pressione lunga = [onLongPress] (rinomina, sposta…). [trailing] va dopo le
 * linguette (per esempio «+»). La fila scorre quando non ci sta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    sheet: Color,
    modifier: Modifier = Modifier,
    down: Boolean = false,
    onLongPress: ((Int) -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.onSurface.copy(alpha = 0.14f)
    val shape = if (down) RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp) else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalAlignment = if (down) Alignment.Top else Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val divider = !on && i > 0 && i - 1 != selected
                Box(
                    Modifier
                        .then(if (on) Modifier.padding(horizontal = 6.dp) else Modifier)
                        .height(if (on) 36.dp else 32.dp)
                        .then(if (on) Modifier.folderBackground(sheet, down) else if (divider) Modifier.folderDivider(line) else Modifier)
                        .clip(shape)
                        .semantics { this.selected = on }
                        .combinedClickable(role = Role.Tab, onClick = { onSelect(i) }, onLongClick = onLongPress?.let { { it(i) } })
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
        trailing()
    }
}
