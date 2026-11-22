package eu.eurise.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import eu.eurise.core.designsystem.component.folderPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Dove sta la barra delle schede: nessuna, sopra la barra in basso, in cima alla pagina. */
internal enum class StripPlace { None, Bottom, Top }

/** La scelta delle Impostazioni, con «auto»: sul tablet in cima, sul telefono spenta. */
@Composable
internal fun stripPlace(setting: String): StripPlace = when (setting) {
    "off" -> StripPlace.None
    "bottom" -> StripPlace.Bottom
    "top" -> StripPlace.Top
    else -> if (LocalConfiguration.current.smallestScreenWidthDp >= 600) StripPlace.Top else StripPlace.None
}

/**
 * La barra delle schede sulla pagina (tavole BrwBarraSchede e BrwCompatta; a schedario con la tavola BrwSchedario,
 * approvata il 22 novembre): la scheda aperta è una linguetta dello stesso colore della barra dell'indirizzo e della
 * pagina, attaccata a loro con due curve morbide, come una cartella tirata avanti nello schedario. Le altre sono
 * linguette basse dietro, senza fondo, separate da un filo. Con molte schede le altre si stringono fino alla sola icona,
 * poi la fila scorre; la aperta resta larga abbastanza da leggere il titolo. Il colore del gruppo è la tacca sul bordo
 * della linguetta lontano dalla pagina. Con la barra in basso ([place] = Bottom) la linguetta pende dalla pagina.
 * Tieni premuto una scheda per spostarla, metterla in un gruppo o chiuderla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TabStrip(model: BrowserModel, modifier: Modifier, place: StripPlace, onNewTab: () -> Unit, onGroups: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val scheme = MaterialTheme.colorScheme
    val down = place == StripPlace.Bottom
    val list = rememberLazyListState()
    val current = model.current
    val index = model.tabs.indexOf(current)
    LaunchedEffect(index, model.tabs.size) { if (index >= 0) runCatching { list.animateScrollToItem(index) } }
    val sheet = scheme.surface
    val line = scheme.onSurface.copy(alpha = 0.14f)
    Row(
        Modifier.fillMaxWidth().background(scheme.surfaceContainer).then(modifier).height(40.dp).padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val gap = 2.dp
            val side = 10.dp // spazio per i piedini della linguetta aperta ai due capi della fila
            val lift = 6.dp // margine della linguetta aperta per non coprire le vicine con i piedini
            val count = model.tabs.size.coerceAtLeast(1)
            val room: Dp = maxWidth - side * 2 - lift * 2
            val share: Dp = (room - gap * (count - 1)) / count
            // Tutte uguali finché il titolo si legge; poi la scheda aperta tiene la sua larghezza e le altre si stringono.
            val roomy = share >= 96.dp
            val openWidth: Dp = if (roomy) share.coerceAtMost(220.dp) else 136.dp.coerceAtMost(maxWidth / 2)
            val otherWidth: Dp = if (roomy) openWidth
                else ((room - openWidth - gap * (count - 1)) / (count - 1).coerceAtLeast(1)).coerceIn(40.dp, 96.dp)
            LazyRow(
                state = list, modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = if (down) Alignment.Top else Alignment.Bottom,
                contentPadding = PaddingValues(horizontal = side),
            ) {
                items(model.tabs, key = { it.id }) { t ->
                    var menu by remember { mutableStateOf(false) }
                    val on = t === current
                    val i = model.tabs.indexOf(t)
                    // Il filo tra due linguette dietro, non accanto a quella aperta.
                    val divider = !on && i > 0 && model.tabs.getOrNull(i - 1)?.let { it !== current } == true
                    val color = groupColor(model, t.group)
                    val title = t.title.ifBlank { if (t.url.startsWith("http")) Links.host(t.url) else stringResource(R.string.brw_new_tab) }
                    val width = if (on) openWidth else otherWidth
                    val iconOnly = !on && width < 76.dp
                    val shape = if (down) RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp) else RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                    Box(if (on) Modifier.padding(horizontal = lift) else Modifier) {
                        Box(
                            Modifier.width(width).height(if (on) 36.dp else 32.dp)
                                .then(
                                    if (on) Modifier.drawBehind { drawPath(folderPath(size.width, size.height, 10.dp.toPx(), down), sheet) }
                                    else if (divider) Modifier.drawBehind {
                                        val h = 16.dp.toPx()
                                        drawLine(line, Offset(-gap.toPx() / 2, (size.height - h) / 2), Offset(-gap.toPx() / 2, (size.height + h) / 2), 1.dp.toPx())
                                    } else Modifier,
                                )
                                .clip(shape)
                                .semantics { selected = on }
                                .combinedClickable(role = Role.Tab, onClick = { model.show(t) }, onLongClick = { menu = true }),
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(start = if (iconOnly) 0.dp else 10.dp, end = if (on) 0.dp else if (iconOnly) 0.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (iconOnly) Arrangement.Center else Arrangement.Start,
                            ) {
                                if (t.private) TabInitial(t, on) else SiteIcon(t.url.ifBlank { t.pending.orEmpty() }, 16.dp) { TabInitial(t, on) }
                                if (!iconOnly) {
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        title, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
                                        overflow = TextOverflow.Clip, color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).fadeEnd(),
                                    )
                                }
                                if (on) {
                                    // La ✕ in fondo alla scheda, non dopo il titolo (16 novembre).
                                    val close = stringResource(R.string.brw_close_tab)
                                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable { model.close(t) }.semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                                        Icon(EuriseIcons.close(2.4f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                            // Il colore del gruppo: la tacca sul bordo lontano dalla pagina, come l'etichetta di una cartella.
                            if (color != null) Box(
                                Modifier.align(if (down) Alignment.BottomCenter else Alignment.TopCenter).padding(horizontal = 10.dp, vertical = 3.dp)
                                    .fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(color),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (i > 0) DropdownMenuItem(text = { Text(stringResource(R.string.brw_strip_left)) }, onClick = { menu = false; model.move(t, -1) })
                            if (i < model.tabs.size - 1) DropdownMenuItem(text = { Text(stringResource(R.string.brw_strip_right)) }, onClick = { menu = false; model.move(t, 1) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.brw_group_put)) }, onClick = { menu = false; model.show(t); onGroups() })
                            // Sul tablet: affianca questa scheda a quella che stai usando (tavola BrwSessioni).
                            if (wide && t !== model.current) DropdownMenuItem(text = { Text(stringResource(R.string.brw_split_with)) }, onClick = { menu = false; model.splitWith(t) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.brw_close_tab)) }, onClick = { menu = false; model.close(t) })
                        }
                    }
                }
            }
        }
        val add = stringResource(R.string.brw_new_tab)
        Box(
            Modifier.size(width = 40.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onNewTab).semantics { contentDescription = add },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.plus(2.4f), null, tint = scheme.onSurface, modifier = Modifier.size(17.dp)) }
    }
}

/** L'iniziale del sito (o la maschera delle schede private) quando l'icona non c'è. */
@Composable
private fun TabInitial(t: Tab, on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(16.dp).clip(CircleShape).background(if (on) scheme.primary.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (t.private) Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(10.dp))
        else Text(Links.host(t.url.ifBlank { t.pending.orEmpty() }).removePrefix("www.").take(1).uppercase().ifBlank { "+" }, fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold, color = if (on) scheme.primary else scheme.onSurfaceVariant)
    }
}
