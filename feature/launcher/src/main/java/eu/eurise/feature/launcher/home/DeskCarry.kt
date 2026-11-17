package eu.eurise.feature.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.AppEntry
import eu.eurise.feature.launcher.apps.AppIcon

/**
 * Quello che si porta da una pagina all'altra (tavola ScrPagTrascina): sempre una bolla, anche un'app ([HomeKind.App]).
 * [from] è la pagina da cui viene (nulla se arriva dalle categorie o da «Tutte le app»); [fromFavorites] vero se era tra i
 * preferiti della home; [entry] l'app, per disegnarla sotto il dito.
 */
data class CarryItem(
    val slot: HomeSlot,
    val from: String? = null,
    val fromFavorites: Boolean = false,
    val entry: AppEntry? = null,
)

/** Dove va a finire: la pagina, il posto tra le sue bolle (-1 in fondo) o i preferiti della home. */
data class CarryTarget(val page: String, val index: Int, val favorites: Boolean = false, val newPage: Boolean = false)

/**
 * Il trascinamento tra le pagine della Scrivania. Il dito lo segue [deskCarryTracker], messo sulla radice della Scrivania:
 * così il trascinamento continua anche quando la pagina da cui è partito esce di scena (categorie che si chiudono, pagine
 * che scorrono). Chi tiene premuto qualcosa chiama [start]; finché il dito non si muove si può ancora lasciare
 * ([onStill]: il menu, la modifica); appena si muove l'oggetto si stacca e si vede la fila delle pagine.
 */
@Stable
class DeskCarry {
    var item by mutableStateOf<CarryItem?>(null)
        private set
    /** Il dito, in coordinate della radice. */
    var pointer by mutableStateOf(Offset.Zero)
    var moved by mutableStateOf(false)
        private set
    /** La pagina su cui l'oggetto cadrebbe adesso, il posto tra le bolle (-1 = in fondo) e se va nei preferiti. */
    var page by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(-1)
    var favorites by mutableStateOf(false)
    /** Il dito sta sopra un'etichetta della fila delle pagine (id, o [NEW] per il «+»). */
    var chip by mutableStateOf<String?>(null)
    /** Vero mentre un trascinamento di un altro tipo (categorie, preferiti) è in corso: si mostra già la fila. */
    var peek by mutableStateOf(false)
    /** Fondo della fila delle pagine: salendo fin lì un'app delle categorie passa alla Scrivania. */
    var rowBottom by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)

    internal var last = Offset.Zero
    internal var origin = Offset.Zero
    private var startAt = Offset.Zero
    private var onStill: (() -> Unit)? = null

    /** Dove stanno le bolle visibili: pagina → chiave della bolla → rettangolo nella radice. */
    val bounds = HashMap<String, HashMap<String, Rect>>()
    val chips = HashMap<String, Rect>()
    var favoritesArea: Rect? = null

    val active: Boolean get() = item != null && moved

    /**
     * Prende [carry]. Con [lifted] l'oggetto è già staccato (arriva da un altro trascinamento); altrimenti si stacca al
     * primo movimento e, se il dito si alza fermo, si chiama [onStill]. [at] di base è l'ultima posizione del dito.
     */
    fun start(carry: CarryItem, at: Offset = last, lifted: Boolean = false, index: Int = -1, onStill: (() -> Unit)? = null) {
        item = carry
        pointer = at
        startAt = at
        moved = lifted
        page = carry.from
        this.index = index
        favorites = false
        chip = null
        peek = false
        this.onStill = onStill
    }

    fun end() {
        item = null
        moved = false
        chip = null
        page = null
        index = -1
        favorites = false
        onStill = null
    }

    /** Vero se [p] (nella radice) sta sulla fila delle pagine, in alto. */
    fun inRow(p: Offset): Boolean = rowBottom > 0f && p.y <= rowBottom

    /** Vero se [p] sta sul bordo sinistro o destro dello schermo. */
    fun atEdge(p: Offset, edge: Float): Boolean = width > 0f && (p.x < edge || p.x > width - edge)

    /** La bolla lasciata qui: la pagina vista, il posto scelto, l'etichetta su cui sta il dito. */
    fun target(current: String): CarryTarget = when (val c = chip) {
        NEW -> CarryTarget(current, -1, newPage = true)
        null -> CarryTarget(page ?: current, index, favorites)
        else -> CarryTarget(c, if (c == page) index else -1)
    }

    /**
     * Le bolle di [pageId] durante il trascinamento: senza quella portata e, se è la pagina su cui cadrebbe, con il buco
     * dove andrà (le altre fanno spazio).
     */
    fun preview(pageId: String, slots: List<HomeSlot>): List<HomeSlot> {
        val carried = item ?: return slots
        if (!moved) return slots
        val rest = slots.filter { it.key != carried.slot.key }
        return if (page == pageId && chip == null && !favorites) rest.placing(hole(carried.slot), index) else rest
    }

    internal fun track(at: Offset, pressed: Boolean, slop: Float, onDrop: () -> Unit): Boolean {
        last = at
        if (item == null) return false
        if (pressed) {
            pointer = at
            if (!moved && (at - startAt).getDistance() > slop) moved = true
            return moved
        }
        val was = moved
        val still = onStill
        if (was) onDrop() else still?.invoke()
        end()
        return was
    }

    companion object {
        /** L'etichetta «+» della fila delle pagine. */
        const val NEW = "\u0000new"
        /** La chiave dell'app finta che fa da buco. */
        const val HOLE = "\u0000hole"

        /** Il buco: largo quanto la bolla portata. */
        fun hole(slot: HomeSlot) = HomeSlot(HomeKind.App, slot.width, app = HOLE)
    }
}

val LocalDeskCarry = staticCompositionLocalOf<DeskCarry?> { null }

/** La pagina della Scrivania in cui si trova una bolla (per ricordare dov'è e per portarla via). */
val LocalDeskPage = staticCompositionLocalOf<String?> { null }

/**
 * Segue il dito per [carry] sulla radice della Scrivania. Guarda gli eventi prima dei figli (passaggio Initial): quando
 * l'oggetto si è staccato li consuma, così né le pagine né le bolle reagiscono, e al rilascio chiama [onDrop].
 */
fun Modifier.deskCarryTracker(carry: DeskCarry, onDrop: () -> Unit): Modifier = this
    .onGloballyPositioned { carry.origin = it.positionInRoot(); carry.width = it.size.width.toFloat() }
    .pointerInput(carry) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: continue
                val consume = carry.track(change.position + carry.origin, change.pressed, slop, onDrop)
                if (consume) event.changes.forEach { it.consume() }
            }
        }
    }

/** Registra dove sta una bolla di [page], perché il trascinamento sappia su quale passa il dito. */
fun Modifier.carryBounds(carry: DeskCarry?, page: String?, key: String): Modifier =
    if (carry == null || page == null) this
    else onGloballyPositioned { carry.bounds.getOrPut(page) { HashMap() }[key] = it.boundsInRoot() }

/**
 * Il posto dove cadrebbe la bolla sulla pagina [slots] (senza quella portata): sopra una bolla, prima o dopo secondo la
 * metà; sotto tutte, in fondo; altrove resta quello di prima ([current]). Restituisce -1 per «in fondo».
 */
fun carryIndex(slots: List<HomeSlot>, rects: Map<String, Rect>, p: Offset, current: Int, holeRect: Rect?): Int {
    if (holeRect != null && holeRect.contains(p)) return current
    val live = slots.mapIndexedNotNull { i, s -> rects[s.key]?.let { Triple(i, s, it) } }
    val over = live.firstOrNull { it.third.contains(p) }
    if (over != null) return if (p.x > over.third.center.x) over.first + 1 else over.first
    val bottom = live.maxOfOrNull { it.third.bottom } ?: return -1
    return if (p.y > bottom) -1 else current
}

/** Il buco dove andrà la bolla: tratteggiato, con «qui». */
@Composable
fun CarryHole(slot: HomeSlot, iconSize: Dp, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    val isApp = slot.width <= 0.3f
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
            Box(
                Modifier
                    .then(if (isApp) Modifier.size(iconSize) else Modifier.fillMaxWidth().height(72.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .drawBehind {
                        drawRoundRect(
                            color.copy(alpha = 0.6f), cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                        )
                    },
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.carry_here), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Sopra la Scrivania durante il trascinamento: la fila delle pagine in alto (si salta a una pagina passandoci sopra, sul
 * «+» nasce una pagina nuova), le frecce ai bordi e, sotto il dito, l'oggetto portato.
 */
@Composable
fun BoxScope.DeskCarryLayer(
    carry: DeskCarry,
    pages: List<DeskPage>,
    current: Int,
    hazeState: HazeState,
    iconSize: Dp,
    dark: Boolean,
    appOf: (String) -> AppEntry?,
    canGo: (Int) -> Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    Row(
        Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(hazeState, RoundedCornerShape(22.dp), outline = false)
            .onGloballyPositioned { carry.rowBottom = it.boundsInRoot().bottom + with(density) { 12.dp.toPx() } }
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { i, p ->
            val on = carry.chip == p.id || (carry.chip == null && i == current)
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) scheme.primary else Color.White.copy(alpha = 0.55f))
                    .onGloballyPositioned { carry.chips[p.id] = it.boundsInRoot() }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val tint = if (on) scheme.onPrimary else Color(0xFF1E1D1B)
                Icon(p.icon(), null, tint = tint, modifier = Modifier.size(12.dp))
                Text(p.title(), color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            }
        }
        val onNew = carry.chip == DeskCarry.NEW
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (onNew) scheme.primary else Color.White.copy(alpha = 0.55f))
                .onGloballyPositioned { carry.chips[DeskCarry.NEW] = it.boundsInRoot() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(EuriseIcons.plus(2.2f), stringResource(R.string.pages_new), tint = if (onNew) scheme.onPrimary else Color(0xFF1F2A44), modifier = Modifier.size(15.dp))
        }
    }
    val item = carry.item ?: return
    if (!carry.moved) return
    // Frecce ai bordi: tenendo l'oggetto lì si passa alla pagina accanto.
    val edgeColor = Color(0xFF1F2A44)
    if (canGo(-1)) EdgeArrow(Modifier.align(Alignment.CenterStart), left = true, edgeColor)
    if (canGo(1)) EdgeArrow(Modifier.align(Alignment.CenterEnd), left = false, edgeColor)
    // Sotto il dito.
    val p = carry.pointer - carry.origin
    val entry = item.entry ?: item.slot.app.takeIf { item.slot.kind == HomeKind.App }?.let(appOf)
    if (entry != null) {
        val half = with(density) { (iconSize * 0.6f).toPx() }
        AppIcon(
            entry, iconSize * 1.15f, dark,
            modifier = Modifier.graphicsLayer {
                translationX = p.x - half
                translationY = p.y - half
                rotationZ = -4f
                alpha = 0.95f
            },
        )
    } else {
        val w = with(density) { 180.dp.toPx() }
        val h = with(density) { 56.dp.toPx() }
        Row(
            Modifier.graphicsLayer {
                translationX = p.x - w / 2
                translationY = p.y - h / 2
                rotationZ = -3f
            }.width(180.dp).height(56.dp).glass(hazeState, RoundedCornerShape(20.dp), outline = false).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(item.slot.kind.icon(), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(item.slot.kind.title), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EdgeArrow(modifier: Modifier, left: Boolean, color: Color) {
    val fade = listOf(color.copy(alpha = 0f), color.copy(alpha = 0.30f))
    Box(
        modifier.width(26.dp).fillMaxHeight(0.6f)
            .clip(if (left) RoundedCornerShape(topEnd = 13.dp, bottomEnd = 13.dp) else RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp))
            .background(Brush.horizontalGradient(if (left) fade.reversed() else fade)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (left) EuriseIcons.chevronLeft() else EuriseIcons.chevronRight(), null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
