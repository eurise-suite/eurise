package eu.eurise.feature.launcher.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.summary.BottomSheet
import eu.eurise.feature.launcher.summary.CardKind
import eu.eurise.feature.launcher.summary.longPressToEdit

/**
 * Le schede che si possono mettere sulla home: le bolle della home e le schede delle altre pagine.
 * [defaultHeight] 0 vuol dire "alta quanto il contenuto"; le schede delle altre pagine hanno bisogno di
 * un'altezza fissa.
 */
enum class HomeKind(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: () -> ImageVector,
    val defaultWidth: Float = 1f,
    val defaultHeight: Int = 0,
    val card: CardKind? = null,
) {
    Agenda("agenda", R.string.welcome_events, R.string.home_agenda_desc, { EuriseIcons.calendar(1.9f) }),
    Comms("comms", R.string.welcome_comms, R.string.home_comms_desc, { EuriseIcons.chat(1.9f) }),
    Health("health", R.string.welcome_health, R.string.home_health_desc, { EuriseIcons.heart(1.9f) }),
    Work("work", R.string.welcome_work, R.string.home_work_desc, { EuriseIcons.folder(1.9f) }, 0.57f),
    Music("music", R.string.welcome_music, R.string.home_music_desc, { EuriseIcons.music(1.9f) }, 0.43f),
    TodayTasks("today_tasks", R.string.bubble_today_name, R.string.bubble_today_desc, { EuriseIcons.task(1.9f) }),
    NextEvent("next_event", R.string.bubble_next_title, R.string.bubble_next_desc, { EuriseIcons.map(1.9f) }),
    Codes("codes", R.string.bubble_codes_title, R.string.bubble_codes_desc, { EuriseIcons.key(1.9f) }),
    Meds("meds", R.string.bubble_meds_title, R.string.bubble_meds_desc, { EuriseIcons.medical(1.9f) }),
    Clock("clock", R.string.bubble_clock_title, R.string.bubble_clock_desc, { EuriseIcons.alarm(1.9f) }),
    Memo("memo", R.string.bubble_memo_title, R.string.bubble_memo_desc, { EuriseIcons.pen(1.9f) }),
    Videos("videos", R.string.card_videos, R.string.card_videos_desc, { EuriseIcons.play2(1.9f) }),
    Weather("weather", R.string.weather_title, R.string.card_weather_desc, { EuriseIcons.sun(1.9f) }, 1f, 200, CardKind.Weather),
    Notes("notes", R.string.card_notes, R.string.card_notes_desc, { EuriseIcons.note(1.9f) }, 1f, 200, CardKind.PinnedNotes),
    Horoscope("horoscope", R.string.card_horoscope, R.string.card_horoscope_desc, { EuriseIcons.zodiac(4, 1.9f) }, 1f, 170, CardKind.Horoscope),
    Recipe("recipe", R.string.card_recipe, R.string.card_recipe_desc, { EuriseIcons.spoon(1.9f) }, 1f, 220, CardKind.Recipe),
    News("news", R.string.card_news, R.string.card_news_desc, { EuriseIcons.news(1.9f) }, 1f, 240, CardKind.News),
    Stocks("stocks", R.string.card_stocks, R.string.card_stocks_desc, { EuriseIcons.chart(1.9f) }, 1f, 170, CardKind.Stocks),
    Calendar("calendar_card", R.string.card_calendar, R.string.card_calendar_desc, { EuriseIcons.calendar(1.9f) }, 1f, 300, CardKind.Calendar),
    Messages("messages_card", R.string.card_messages, R.string.card_messages_desc, { EuriseIcons.chat(1.9f) }, 1f, 240, CardKind.Messages),
    /** Il widget di un'altra app (anteprime WidAggiungi, WidBolla): se ne possono mettere più d'uno, ognuno col suo numero. */
    AppWidget("widget", R.string.home_widget, R.string.home_widget_desc, { EuriseIcons.grid(1.9f) }, 1f, 180);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Una scheda sulla home: larghezza come frazione della riga (da 0,3 a 1), altezza in dp (0 = quanto il
 * contenuto). Le schede affiancate restano alte uguali finché l'utente non le scioglie ([free]).
 */
data class HomeSlot(val kind: HomeKind, val width: Float, val height: Int = kind.defaultHeight, val free: Boolean = false, val widget: Int = 0) {
    /** Chi è la bolla: il tipo, o per i widget il loro numero (ce ne possono essere più d'uno). */
    val key: String get() = if (kind == HomeKind.AppWidget) "widget#$widget" else kind.id
}

val DefaultHomeSlots = listOf(
    HomeSlot(HomeKind.Agenda, 1f),
    HomeSlot(HomeKind.Comms, 1f),
    HomeSlot(HomeKind.Health, 1f),
    HomeSlot(HomeKind.Work, 0.57f),
    HomeSlot(HomeKind.Music, 0.43f),
)

fun encodeHomeSlots(slots: List<HomeSlot>): String = slots.encodeHome()

fun List<HomeSlot>.encodeHome(): String =
    joinToString("|") { "${it.key}:${"%.3f".format(java.util.Locale.ROOT, it.width)}:${it.height}:${if (it.free) 1 else 0}" }

fun decodeHome(raw: String?): List<HomeSlot> {
    if (raw.isNullOrBlank()) return if (raw == null) DefaultHomeSlots else emptyList()
    return raw.split('|').mapNotNull { part ->
        val b = part.split(':')
        val widget = b[0].takeIf { it.startsWith("widget#") }?.removePrefix("widget#")?.toIntOrNull() ?: 0
        val kind = if (widget != 0) HomeKind.AppWidget else HomeKind.byId(b[0])?.takeIf { it != HomeKind.AppWidget } ?: return@mapNotNull null
        HomeSlot(
            kind,
            (b.getOrNull(1)?.toFloatOrNull() ?: kind.defaultWidth).coerceIn(MinWidth, 1f),
            b.getOrNull(2)?.toIntOrNull() ?: kind.defaultHeight,
            b.getOrNull(3) == "1",
            widget,
        )
    }.distinctBy { it.key }
}

private const val MinWidth = 0.3f
private const val MinHeight = 64

/** Righe della home: una scheda va accanto alla precedente finché la somma delle larghezze non supera la riga. */
fun homeRows(slots: List<HomeSlot>): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = 2f
    slots.forEachIndexed { i, s ->
        if (used + s.width > 1.001f) {
            rows += mutableListOf(i); used = s.width
        } else {
            rows.last() += i; used += s.width
        }
    }
    return rows
}

/** Vero se la bolla ha un'altezza scelta: allora scorre tutta insieme e gli elenchi interni non hanno una barra propria. */
val LocalBubbleFixedHeight = compositionLocalOf { false }

/** La larghezza della bolla: sotto certe misure (per esempio in formato quadrato) i comandi vanno a capo. */
val LocalBubbleWidth = compositionLocalOf { 400.dp }

/** Dalle schede della vecchia griglia delle pagine laterali alle bolle, con la stessa misura. */
fun eu.eurise.feature.launcher.summary.PlacedCard.toHomeSlot(): HomeSlot? {
    val target = kind.bubbleId?.let { HomeKind.byId(it) }
        ?: if (kind == CardKind.Music) HomeKind.Music else HomeKind.entries.firstOrNull { it.card == kind }
    return target?.let { HomeSlot(it, (cols / 24f).coerceIn(MinWidth, 1f), rows * 8, free) }
}

/** Le bolle riferiscono qui la loro altezza naturale, per allineare quelle affiancate. */
val LocalPillMeasure = compositionLocalOf<((Int) -> Unit)?> { null }

private val Gap = 9.dp

/**
 * Le schede della home, modificabili come quelle delle altre pagine: pressione lunga per entrare in
 * modifica, maniglie che si trascinano in modo continuo (larghezza a destra, altezza in basso), il meno per
 * togliere, la catena per unire o sciogliere l'altezza delle affiancate, "Aggiungi scheda" per il catalogo.
 */
@Composable
fun HomeBoard(
    slots: List<HomeSlot>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onChange: (List<HomeSlot>) -> Unit,
    onCatalog: () -> Unit,
    content: @Composable (HomeSlot, Modifier) -> Unit,
) {
    val naturals = remember { mutableStateMapOf<String, Int>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val latest by rememberUpdatedState(slots)
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf<Rect?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val endDrag = {
        val from = dragging
        val start = dragStart
        if (from != null && start != null) {
            val center = start.center + dragOffset
            val target = latest.map { it.key }
                .minByOrNull { key -> (((if (key == from) start else bounds[key])?.center ?: Offset(Float.MAX_VALUE / 4, 0f)) - center).getDistanceSquared() }
            if (target != null && target != from) {
                val list = latest.toMutableList()
                val to = list.indexOfFirst { it.key == target }
                val moved = list.removeAt(list.indexOfFirst { it.key == from })
                list.add(to, moved)
                onChange(list)
            }
        }
        dragging = null
        dragStart = null
        dragOffset = Offset.Zero
    }
    // In modifica si lascia un margine per le maniglie, che sporgono dai bordi delle schede.
    Column(
        Modifier.padding(horizontal = if (editing) 12.dp else 0.dp, vertical = if (editing) 12.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (editing) 18.dp else Gap),
    ) {
        if (editing) EditBar(onAdd = onCatalog, onDone = { onEditingChange(false) })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rowPx = with(density) { maxWidth.toPx() }
            Column(verticalArrangement = Arrangement.spacedBy(if (editing) 18.dp else Gap)) {
                homeRows(slots).forEach { row ->
                    // Altezza comune delle schede legate: quella scelta dall'utente o la più alta delle naturali.
                    val linked = if (row.size > 1) row.filter { !slots[it].free } else emptyList()
                    val chosen = linked.map { slots[it].height }.filter { it > 0 }.maxOrNull()
                    val natural = linked.mapNotNull { naturals[slots[it].key] }.maxOrNull()?.let { with(density) { it.toDp() } }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap), verticalAlignment = Alignment.Top) {
                        row.forEachIndexed { pos, index ->
                            val slot = slots[index]
                            val heightMod = when {
                                index in linked && chosen != null -> Modifier.height(chosen.dp)
                                index in linked && natural != null -> Modifier.heightIn(min = natural)
                                slot.height > 0 -> Modifier.height(slot.height.dp)
                                else -> Modifier
                            }
                            val lifted = dragging == slot.key
                            Box(
                                Modifier.weight(slot.width)
                                    .onGloballyPositioned { if (dragging != slot.key) bounds[slot.key] = it.boundsInRoot() }
                                    .zIndex(if (lifted) 2f else if (editing) 1f else 0f)
                                    .graphicsLayer {
                                        if (lifted) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            scaleX = 1.03f
                                            scaleY = 1.03f
                                        }
                                    },
                            ) {
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalPillMeasure provides { h -> if (naturals[slot.key] != h) naturals[slot.key] = h },
                                    LocalBubbleFixedHeight provides (slot.height > 0 || (index in linked && chosen != null)),
                                ) {
                                    content(
                                        slot,
                                        Modifier.fillMaxWidth().then(heightMod)
                                            .then(if (!editing) Modifier.longPressToEdit { onEditingChange(true) } else Modifier),
                                    )
                                }
                                if (editing) {
                                    EditFrame(
                                        slot = slot,
                                        linkedLeft = pos > 0,
                                        isLinked = pos > 0 && !slot.free && !slots[row[pos - 1]].free,
                                        rowPx = rowPx,
                                        onRemove = {
                                            // Un widget tolto libera il suo numero presso Android.
                                            if (slot.kind == HomeKind.AppWidget) AppWidgets.delete(context, slot.widget)
                                            onChange(latest.filterNot { it.key == slot.key })
                                        },
                                        onWidth = { w -> onChange(latest.map { if (it.key == slot.key) it.copy(width = w.coerceIn(MinWidth, 1f)) else it }) },
                                        currentHeightPx = {
                                            val s = latest.firstOrNull { it.key == slot.key }
                                            when {
                                                s != null && s.height > 0 -> with(density) { s.height.dp.toPx() }
                                                else -> (naturals[slot.key] ?: 200).toFloat()
                                            }
                                        },
                                        onHeight = { px ->
                                            val dp = with(density) { px.toDp().value.toInt() }.coerceIn(MinHeight, 900)
                                            onChange(applyHeight(latest, slot.key, dp))
                                        },
                                        onToggleLink = { onChange(toggleHomeLink(latest, row.map { latest.getOrNull(it)?.key }, pos)) },
                                        onDragStart = { dragging = slot.key; dragStart = bounds[slot.key]; dragOffset = Offset.Zero },
                                        onDrag = { dragOffset += it },
                                        onDragEnd = endDrag,
                                    )
                                }
                            }
                        }
                        val used = row.sumOf { slots[it].width.toDouble() }.toFloat()
                        if (used < 0.999f) Spacer(Modifier.weight(1f - used))
                    }
                }
            }
        }
    }
}

/** Nuova altezza per una scheda; se è legata, la prendono anche le legate della stessa riga. */
private fun applyHeight(slots: List<HomeSlot>, key: String, dp: Int): List<HomeSlot> {
    val index = slots.indexOfFirst { it.key == key }
    val row = homeRows(slots).firstOrNull { index in it }.orEmpty()
    val target = slots[index]
    return slots.mapIndexed { i, s ->
        when {
            i == index -> s.copy(height = dp)
            !target.free && row.size > 1 && i in row && !s.free -> s.copy(height = dp)
            else -> s
        }
    }
}

/** La catena tra una scheda e quella alla sua sinistra: se sono legate si sciolgono, altrimenti si legano. */
private fun toggleHomeLink(slots: List<HomeSlot>, rowKeys: List<String?>, pos: Int): List<HomeSlot> {
    val a = rowKeys.getOrNull(pos - 1) ?: return slots
    val b = rowKeys.getOrNull(pos) ?: return slots
    val sa = slots.first { it.key == a }
    val sb = slots.first { it.key == b }
    val linked = !sa.free && !sb.free
    val common = maxOf(sa.height, sb.height)
    return slots.map {
        when (it.key) {
            a, b -> if (linked) it.copy(free = true) else it.copy(free = false, height = common)
            else -> it
        }
    }
}

@Composable
private fun EditBar(onAdd: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            BarButton(stringResource(R.string.home_add_card), filled = false, onClick = onAdd)
            BarButton(stringResource(R.string.card_done), filled = true, onClick = onDone)
        }
        Text(
            stringResource(R.string.home_edit_hint), fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun BarButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(50))
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
    }
}

/**
 * Cornice di modifica sopra una scheda: blocca i tocchi sul contenuto, mostra il meno, le maniglie e, tra
 * due schede affiancate, la catena.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EditFrame(
    slot: HomeSlot,
    linkedLeft: Boolean,
    isLinked: Boolean,
    rowPx: Float,
    onRemove: () -> Unit,
    onWidth: (Float) -> Unit,
    currentHeightPx: () -> Float,
    onHeight: (Float) -> Unit,
    onToggleLink: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val latest by rememberUpdatedState(slot)
    val dragStarted by rememberUpdatedState(onDragStart)
    val dragged by rememberUpdatedState(onDrag)
    val dragEnded by rememberUpdatedState(onDragEnd)
    Box(
        Modifier.matchParentSize()
            .border(2.dp, primary, RoundedCornerShape(22.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragStarted() },
                    onDrag = { change, amount -> change.consume(); dragged(amount) },
                    onDragEnd = { dragEnded() },
                    onDragCancel = { dragEnded() },
                )
            },
    )
    // Togli
    Box(
        Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-8).dp).size(24.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.card_remove), onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) { Icon(EuriseIcons.minus(), stringResource(R.string.card_remove), tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(13.dp)) }
    // Larghezza: maniglia a destra, continua.
    var startWidth by remember { mutableStateOf(0f) }
    var dragX by remember { mutableStateOf(0f) }
    Handle(
        Modifier.align(Alignment.CenterEnd).offset(x = 11.dp),
        onStart = { startWidth = latest.width; dragX = 0f },
        onDrag = { dx, _ -> dragX += dx; if (rowPx > 0f) onWidth(startWidth + dragX / rowPx) },
    )
    // Altezza: maniglia in basso, continua.
    var startHeight by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    Handle(
        Modifier.align(Alignment.BottomCenter).offset(y = 11.dp),
        onStart = { startHeight = currentHeightPx(); dragY = 0f },
        onDrag = { _, dy -> dragY += dy; onHeight(startHeight + dragY) },
    )
    if (linkedLeft) {
        val label = stringResource(if (isLinked) R.string.card_unlink else R.string.card_link)
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-19).dp).size(28.dp).clip(CircleShape)
                .background(if (isLinked) primary else MaterialTheme.colorScheme.surface)
                .border(1.5.dp, primary, CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onToggleLink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EuriseIcons.link(2f), label, tint = if (isLinked) MaterialTheme.colorScheme.onPrimary else primary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Handle(modifier: Modifier, onStart: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start() },
                    onDrag = { change, amount -> change.consume(); drag(amount.x, amount.y) },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary)) }
}

/**
 * Catalogo delle schede della home: quelle presenti (si tolgono) e quelle da aggiungere; con [onWidget] anche la
 * scheda "Widget delle app" (anteprima WidAggiungi), con i widget delle app installate raggruppati per app.
 */
@Composable
fun HomeCatalog(
    slots: List<HomeSlot>,
    onAdd: (HomeKind) -> Unit,
    onRemove: (HomeKind) -> Unit,
    onClose: () -> Unit,
    onWidget: ((android.appwidget.AppWidgetProviderInfo) -> Unit)? = null,
) {
    val present = slots.map { it.kind }.toSet()
    var widgets by remember { mutableStateOf(false) }
    BottomSheet(stringResource(R.string.home_cards_title), onClose) {
        if (onWidget != null) item(key = "tabs") {
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eu.eurise.core.designsystem.component.FilterPill(stringResource(R.string.home_widget_tab_bubbles), null, !widgets) { widgets = false }
                eu.eurise.core.designsystem.component.FilterPill(stringResource(R.string.home_widget_tab_apps), null, widgets) { widgets = true }
            }
        }
        if (widgets && onWidget != null) {
            widgetCatalog(onWidget)
            return@BottomSheet
        }
        HomeKind.entries.filter { it != HomeKind.AppWidget }.forEach { kind ->
            item(key = kind.id) {
                val on = kind in present
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(kind.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(kind.title), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(kind.description), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(if (on) R.string.on_home else R.string.videos_add),
                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = if (on) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (on) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { if (on) onRemove(kind) else onAdd(kind) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Altezza minima di comodo per le schede delle altre pagine messe sulla home. */
internal val HomeCardMinHeight: Dp = 120.dp
