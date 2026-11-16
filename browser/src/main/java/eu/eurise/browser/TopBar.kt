package eu.eurise.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons

/** Il testo che finisce sfumando invece dei puntini (schede e indirizzo, come Vivaldi; tavola BrwCompatta). */
internal fun Modifier.fadeEnd(from: Float = 0.72f): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(Brush.horizontalGradient(from to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

/**
 * La barra dell'indirizzo in alto, compatta (tavola BrwCompatta, approvata il 17 novembre: «come Vivaldi, più pagina
 * per leggere»): una fila di 46 dp senza cornici intorno. Nel campo lo scudo, il sito con il percorso in grigio e
 * Ricarica (Ferma mentre carica); fuori il menu Eurise, a destra o a sinistra per i mancini. Il numero delle schede c'è
 * solo se la fila delle schede è spenta. Scorrere giù la porta sotto il pollice, come prima.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun TopBar(
    tab: Tab?,
    count: Int,
    showTabs: Boolean,
    leftHanded: Boolean,
    barAction: MenuItem?,
    onBarAction: (MenuItem) -> Unit,
    onSwipe: ((Int) -> Unit)?,
    onShield: () -> Unit,
    onAddress: () -> Unit,
    onEditAddress: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onPull: ((down: Boolean) -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.onSurface.copy(alpha = 0.08f)
    val pull = if (onPull == null) Modifier else Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total > 48.dp.toPx()) onPull(true) },
            onVerticalDrag = { change, amount -> change.consume(); total += amount },
        )
    }
    val swipe = if (onSwipe == null) Modifier else Modifier.pointerInput(onSwipe) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (kotlin.math.abs(total) > 80.dp.toPx()) onSwipe(if (total < 0) 1 else -1) },
            onHorizontalDrag = { change, amount -> change.consume(); total += amount },
        )
    }
    val url = tab?.url.orEmpty()
    val blank = url.isBlank() || url == "about:blank"
    val loading = tab != null && tab.progress in 1..99

    val field: @Composable (Modifier) -> Unit = { m ->
        val addressLabel = stringResource(R.string.brw_addr_label)
        Row(
            m.height(38.dp).clip(RoundedCornerShape(10.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).then(swipe)
                .combinedClickable(onClick = onAddress, onLongClick = onEditAddress).semantics { contentDescription = addressLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shield = stringResource(R.string.brw_protection)
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onShield).semantics { contentDescription = shield },
                contentAlignment = Alignment.Center,
            ) {
                if (tab?.private == true) Icon(EuriseIcons.mask(1.8f), null, tint = scheme.primary, modifier = Modifier.size(17.dp))
                else Icon(EuriseIcons.shield(2.2f), null, tint = if (tab?.protectionOn != false) Safe else Half, modifier = Modifier.size(17.dp))
            }
            val query = if (blank) null else Links.queryOf(url)
            val text = when {
                blank -> buildAnnotatedString { append(stringResource(R.string.brw_address_hint)) }
                query != null -> buildAnnotatedString { append(query) }
                else -> {
                    val host = Links.host(url)
                    val rest = url.substringAfter("://").substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
                    buildAnnotatedString {
                        append(host)
                        withStyle(SpanStyle(color = scheme.onSurfaceVariant)) { append(rest) }
                    }
                }
            }
            Text(
                text, fontSize = 14.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                color = if (blank) scheme.onSurfaceVariant else scheme.onSurface,
                modifier = Modifier.weight(1f).fadeEnd(0.85f),
            )
            if (!blank) {
                val label = stringResource(if (loading) R.string.brw_stop else R.string.brw_reload)
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { tab?.session?.let { if (loading) it.stop() else it.reload() } }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (loading) EuriseIcons.close(2.2f) else EuriseIcons.refresh(2.2f), null, tint = scheme.onSurface, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
    val extras: @Composable () -> Unit = {
        if (barAction != null && barAction != MenuItem.Bookmarks) {
            val label = stringResource(barAction.label)
            SmallButton(label, { onBarAction(barAction) }) { Icon(barAction.icon(), null, tint = scheme.primary, modifier = Modifier.size(19.dp)) }
        }
        if (showTabs) {
            val tabsLabel = stringResource(R.string.brw_tabs_title, count)
            SmallButton(tabsLabel, onTabs) {
                Box(Modifier.size(21.dp).border(2.dp, scheme.onSurface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text(if (count > 99) "∞" else "$count", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    val menu: @Composable () -> Unit = {
        val more = stringResource(R.string.brw_menu)
        SmallButton(more, onMenu) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primary), contentAlignment = Alignment.Center) {
                val star = scheme.onPrimary
                // La stella di Eurise (la stessa della nuova scheda sul computer).
                Canvas(Modifier.size(16.dp)) {
                    val w = size.width / 24f
                    val p = Path().apply {
                        moveTo(12 * w, 3 * w); lineTo(13.6f * w, 10.4f * w); lineTo(21 * w, 12 * w); lineTo(13.6f * w, 13.6f * w)
                        lineTo(12 * w, 21 * w); lineTo(10.4f * w, 13.6f * w); lineTo(3 * w, 12 * w); lineTo(10.4f * w, 10.4f * w); close()
                    }
                    drawPath(p, star)
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().background(scheme.surface).then(pull)
            .drawBehind { drawLine(line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .height(46.dp).padding(start = if (leftHanded) 4.dp else 8.dp, end = if (leftHanded) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leftHanded) { menu(); extras(); Spacer(Modifier.width(4.dp)) }
        field(Modifier.weight(1f))
        if (!leftHanded) { Spacer(Modifier.width(4.dp)); extras(); menu() }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(width = 40.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
