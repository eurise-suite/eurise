package eu.eurise.feature.launcher.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.home.Label
import eu.eurise.feature.launcher.home.scrimColor

/** Passo dell'altezza delle schede: piccolo, perché il ridimensionamento sia continuo. */
private val RowUnit = ROW_STEP_DP.dp
private val Gap = 12.dp

/**
 * Il Riepilogo: schede su una griglia di 4 colonne. Tenendo premuta una scheda si entra in modifica:
 * si trascina per spostarla, si tocca per cambiarne la misura, il segno meno la toglie, "+ Scheda" apre il catalogo.
 */
@Composable
fun SummaryBoard(
    cards: List<PlacedCard>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onChange: (List<PlacedCard>) -> Unit,
    hazeState: HazeState,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    cardContent: @Composable (PlacedCard, Modifier) -> Unit,
) {
    var selected by remember { mutableStateOf<CardKind?>(null) }
    var catalog by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    LaunchedEffect(editing) { if (!editing) { selected = null; catalog = false } }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) EditHeader(onAdd = { catalog = true }, onDone = { onEditingChange(false) })
            layoutRows(cards).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.Top) {
                    row.forEach { index ->
                        val card = cards[index]
                        val isDragged = dragging == index
                        Box(
                            Modifier
                                .weight(card.cols.toFloat(), fill = true)
                                .height(RowUnit * card.rows)
                                .then(if (!editing) Modifier.longPressToEdit { onEditingChange(true) } else Modifier)
                                .onGloballyPositioned { bounds[index] = it.boundsInRoot() }
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x; translationY = dragOffset.y
                                        scaleX = 1.03f; scaleY = 1.03f
                                    }
                                },
                        ) {
                            cardContent(card, Modifier.fillMaxSize())
                            if (editing) {
                                EditOverlay(
                                    card = card,
                                    onResize = { c, r -> onChange(resize(cards, card, c, r)) },
                                    onToggleLink = { onChange(toggleLink(cards, card)) },
                                    selected = selected == card.kind,
                                    onTap = { selected = if (selected == card.kind) null else card.kind },
                                    onDragStart = { dragging = index; dragOffset = Offset.Zero; selected = null },
                                    onDrag = { dragOffset += it },
                                    onDragEnd = {
                                        val from = dragging
                                        val start = from?.let { bounds[it] }
                                        if (from != null && start != null) {
                                            val center = start.center + dragOffset
                                            val target = bounds.filterKeys { it < cards.size }
                                                .minByOrNull { (_, r) -> (r.center - center).getDistanceSquared() }?.key ?: from
                                            if (target != from) {
                                                val list = cards.toMutableList()
                                                val moved = list.removeAt(from)
                                                list.add(target, moved)
                                                onChange(list)
                                            }
                                        }
                                        dragging = null; dragOffset = Offset.Zero
                                    },
                                    onRemove = {
                                        onChange(cards.filterNot { it.kind == card.kind })
                                        if (selected == card.kind) selected = null
                                    },
                                )
                            }
                        }
                    }
                    val used = row.sumOf { cards[it].cols }
                    if (used < GRID_COLS) Spacer(Modifier.weight((GRID_COLS - used).toFloat()))
                }
            }
            if (!editing) footer()
        }

        if (catalog) {
            CardCatalog(
                cards = cards,
                hazeState = hazeState,
                onAdd = { kind -> onChange(cards + PlacedCard(kind, kind.defaultSize)) },
                onRemove = { kind -> onChange(cards.filterNot { it.kind == kind }) },
                onDone = { catalog = false },
            )
        }
    }
}

/**
 * Fuori dalla modifica: una pressione lunga su una scheda apre la modifica senza aprire la scheda. Si legge
 * nel passaggio iniziale, prima dei pulsanti della scheda, che così restano toccabili normalmente.
 */
internal fun Modifier.longPressToEdit(onLongPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val moved = event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
                if (event.changes.none { it.pressed } || moved) break
            }
            true
        }
        if (finished == null) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun EditOverlay(
    card: PlacedCard,
    onResize: (Int, Int) -> Unit,
    onToggleLink: () -> Unit,
    selected: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .then(if (selected) Modifier.border(2.dp, primary, RoundedCornerShape(28.dp)) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
    ) {
        Box(
            Modifier
                .offset(x = (-6).dp, y = (-6).dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.card_remove), onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.minus(), stringResource(R.string.card_remove), modifier = Modifier.size(14.dp)) }
        Icon(
            EuriseIcons.drag(), null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(16.dp),
        )
        if (selected) ResizeHandles(card, onResize, onToggleLink)
    }
}

/**
 * Maniglie per ridimensionare: quella a destra cambia le colonne (da 1 a 4), quella in basso le righe,
 * quella nell'angolo entrambe. Al centro in basso la misura attuale.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResizeHandles(card: PlacedCard, onResize: (Int, Int) -> Unit, onToggleLink: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var widthPx by remember { mutableStateOf(0) }
    val latest by androidx.compose.runtime.rememberUpdatedState(card)
    val rowPx = with(density) { RowUnit.toPx() }
    Box(Modifier.matchParentSize().onGloballyPositioned { widthPx = it.size.width })
    val colPx = if (latest.cols > 0 && widthPx > 0) widthPx.toFloat() / latest.cols else 1f

    @Composable
    fun handle(alignment: Alignment, horizontal: Boolean, vertical: Boolean) {
        var accX by remember { mutableStateOf(0f) }
        var accY by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .align(alignment)
                .offset(x = if (horizontal) 10.dp else 0.dp, y = if (vertical) 10.dp else 0.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .pointerInput(horizontal, vertical) {
                    detectDragGestures(
                        onDragStart = { accX = 0f; accY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            if (horizontal) accX += amount.x
                            if (vertical) accY += amount.y
                            val dc = (accX / colPx).toInt()
                            val dr = (accY / rowPx).toInt()
                            if (dc != 0 || dr != 0) {
                                onResize(latest.cols + dc, latest.rows + dr)
                                accX -= dc * colPx; accY -= dr * rowPx
                            }
                        },
                    )
                },
        )
    }
    handle(Alignment.CenterEnd, horizontal = true, vertical = false)
    handle(Alignment.BottomCenter, horizontal = false, vertical = true)
    handle(Alignment.BottomEnd, horizontal = true, vertical = true)
    // Legame d'altezza con le schede accanto: unite (stessa altezza) o libere.
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .padding(10.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(if (card.free) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (card.free) R.string.card_link else R.string.card_unlink), onClick = onToggleLink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            EuriseIcons.link(2f), stringResource(if (card.free) R.string.card_link else R.string.card_unlink),
            tint = if (card.free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp),
        )
    }
    Text(
        "${card.cols * 100 / GRID_COLS}% · ${card.rows * ROW_STEP_DP} dp",
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun EditHeader(onAdd: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.page_summary), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f),
            )
            Pill(stringResource(R.string.card_add), filled = false, onClick = onAdd)
            Spacer(Modifier.width(8.dp))
            Pill(stringResource(R.string.card_done), filled = true, onClick = onDone)
        }
        Text(
            stringResource(R.string.card_edit_hint), fontSize = 13.sp, lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Catalogo: schede nel Riepilogo (con il meno) e schede da aggiungere (con il più). */
@Composable
private fun CardCatalog(
    cards: List<PlacedCard>,
    hazeState: HazeState,
    onAdd: (CardKind) -> Unit,
    onRemove: (CardKind) -> Unit,
    onDone: () -> Unit,
) {
    val inside = cards.map { it.kind }
    val outside = CardKind.entries.filterNot { it in inside }
    Column(
        Modifier
            .fillMaxSize()
            .glass(hazeState, RectangleShape, elevation = 0.dp, outline = false)
            .background(scrimColor())
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.card_catalog), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f),
            )
            Pill(stringResource(R.string.card_done), filled = true, onClick = onDone)
        }
        Text(stringResource(R.string.card_catalog_hint), fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        Label(stringResource(R.string.card_inside, inside.size), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        CatalogBlock(inside, hazeState, present = true) { onRemove(it) }
        if (outside.isNotEmpty()) {
            Label(stringResource(R.string.card_outside), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
            CatalogBlock(outside, hazeState, present = false) { onAdd(it) }
        }
        Text(stringResource(R.string.card_coming), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp).windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun CatalogBlock(kinds: List<CardKind>, hazeState: HazeState, present: Boolean, onAction: (CardKind) -> Unit) {
    if (kinds.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().glass(hazeState, RoundedCornerShape(24.dp), elevation = 6.dp, outline = false).padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        kinds.forEach { kind ->
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(kind.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(kind.title), fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(kind.description), fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (present) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .clickable(role = Role.Button) { onAction(kind) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (present) EuriseIcons.minus() else EuriseIcons.plus(),
                        stringResource(if (present) R.string.card_remove else R.string.card_add),
                        tint = if (present) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}


/**
 * Cambia la misura di una scheda. Se la scheda è legata, anche le schede legate della stessa riga prendono
 * la nuova altezza, così le affiancate restano alte uguali.
 */
internal fun resize(cards: List<PlacedCard>, card: PlacedCard, cols: Int, rows: Int): List<PlacedCard> {
    val index = cards.indexOfFirst { it.kind == card.kind }
    val row = layoutRows(cards).firstOrNull { index in it }.orEmpty()
    val resized = cards.map { if (it.kind == card.kind) it.resized(cols, rows) else it }
    if (card.free) return resized
    val newRows = resized[index].rows
    return resized.mapIndexed { i, c -> if (i in row && i != index && !c.free) c.resized(c.cols, newRows) else c }
}

/** Scioglie o rifà il legame d'altezza; quando si rilega, la scheda prende l'altezza delle legate accanto. */
internal fun toggleLink(cards: List<PlacedCard>, card: PlacedCard): List<PlacedCard> {
    val index = cards.indexOfFirst { it.kind == card.kind }
    val row = layoutRows(cards).firstOrNull { index in it }.orEmpty()
    val partner = row.filter { it != index }.map { cards[it] }.firstOrNull { !it.free }
    return cards.map {
        when {
            it.kind != card.kind -> it
            it.free -> it.copy(free = false, rows = partner?.rows ?: it.rows)
            else -> it.copy(free = true)
        }
    }
}
