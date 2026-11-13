package eu.eurise.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
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
 * La barra delle schede sulla pagina (tavola BrwBarraSchede), come sul computer e su Vivaldi: le schede in fila con il
 * colore del gruppo sotto, quella aperta in evidenza con la sua ✕, il «+» in fondo. Si scorre; tieni premuto una
 * scheda per spostarla, metterla in un gruppo o chiuderla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TabStrip(model: BrowserModel, modifier: Modifier, onNewTab: () -> Unit, onGroups: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val scheme = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val current = model.current
    val index = model.tabs.indexOf(current)
    LaunchedEffect(index, model.tabs.size) { if (index >= 0) runCatching { list.animateScrollToItem(index) } }
    Row(
        modifier.fillMaxWidth().background(scheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Molte schede: si stringono per starci tutte (fino alla sola icona), poi scorrono (richiesta del 13 novembre).
        BoxWithConstraints(Modifier.weight(1f)) {
        val count = model.tabs.size.coerceAtLeast(1)
        val share: Dp = (maxWidth - 4.dp) / count
        val narrowest = 40.dp
        val half = maxWidth / 2
        LazyRow(state = list, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(end = 4.dp)) {
            items(model.tabs, key = { it.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                val on = t === current
                val color = groupColor(model, t.group)
                val title = t.title.ifBlank { if (t.url.startsWith("http")) Links.host(t.url) else stringResource(R.string.brw_new_tab) }
                Box {
                    Column(
                        Modifier.padding(horizontal = 1.dp)
                            .then(
                                // La scheda aperta resta leggibile; le altre prendono la loro parte dello spazio.
                                if (on) Modifier.widthIn(min = 120.dp.coerceAtMost(half), max = 180.dp)
                                else Modifier.width(share.coerceIn(narrowest, 140.dp))
                            ).height(40.dp)
                            .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(20.dp)) else Modifier)
                            .clip(RoundedCornerShape(20.dp)).background(if (on) scheme.surface else Color.Transparent)
                            .semantics { selected = on }
                            .combinedClickable(role = Role.Tab, onClick = { model.show(t) }, onLongClick = { menu = true }),
                    ) {
                        val iconOnly = !on && share < 76.dp
                        Row(Modifier.weight(1f).padding(start = if (iconOnly) 0.dp else 8.dp, end = if (on) 2.dp else if (iconOnly) 0.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (iconOnly) androidx.compose.foundation.layout.Arrangement.Center else androidx.compose.foundation.layout.Arrangement.Start) {
                            if (t.private) TabInitial(t, on) else SiteIcon(t.url, 20.dp) { TabInitial(t, on) }
                            if (!iconOnly) {
                            Spacer(Modifier.width(7.dp))
                            Text(title, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = if (on) scheme.onSurface else scheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
                            }
                            if (on) {
                                val close = stringResource(R.string.brw_close_tab)
                                Box(Modifier.size(28.dp).clip(CircleShape).clickable { model.close(t) }.semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                                    Icon(EuriseIcons.close(2.4f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                        // Il colore del gruppo sotto la scheda.
                        Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(color ?: Color.Transparent))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val i = model.tabs.indexOf(t)
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
            Modifier.size(40.dp).clip(CircleShape).background(scheme.surface).clickable(role = Role.Button, onClick = onNewTab).semantics { contentDescription = add },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.plus(2.4f), null, tint = scheme.primary, modifier = Modifier.size(16.dp)) }
    }
}

/** L'iniziale del sito (o la maschera delle schede private) quando l'icona non c'è. */
@Composable
private fun TabInitial(t: Tab, on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(if (on) scheme.primary.copy(alpha = 0.10f) else scheme.onSurface.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (t.private) Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(12.dp))
        else Text(Links.host(t.url).removePrefix("www.").take(1).uppercase().ifBlank { "+" }, fontSize = 10.5.sp,
            fontWeight = FontWeight.ExtraBold, color = if (on) scheme.primary else scheme.onSurfaceVariant)
    }
}
