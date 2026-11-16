package app.organicmaps.eurise.ui

import android.app.Activity
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.organicmaps.R
import app.organicmaps.routing.RoutingBottomMenuListener
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.routing.RouteMarkData
import app.organicmaps.sdk.routing.RouteMarkType
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.routing.RoutingOptions
import app.organicmaps.sdk.settings.RoadType
import app.organicmaps.util.ThemeUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Il percorso in alto (tavole MapViaggio4Percorso e MapViaggio6Bici, approvate il 16 novembre): al posto della barra di
 * CoMaps (indietro, cinque tipi di percorso, opzioni) una scheda di vetro con partenza e arrivo, il tasto per scambiarli,
 * e sotto le pillole «Evita …» del mezzo scelto e «+ Tappa». Il mezzo si sceglie solo in basso ([EurisePercorso]).
 * La barra di CoMaps resta nel layout, nascosta: il suo controllore continua a fare il lavoro (avanzamento, errori).
 *
 * Toccando partenza o arrivo si cerca un altro punto con la ricerca di Mappe (come «Cerca» di CoMaps); «+ Tappa» apre
 * le tappe ([showStops], tavola MapViaggio5Tappe).
 */
object EuriseViaggio {
    @JvmStatic
    var attached = false
        private set

    /** Da RoutingPlanController: la scheda nostra, la barra e le opzioni di CoMaps nascoste. */
    @JvmStatic
    fun attachTop(view: View?, toolbar: View?, banner: View?) {
        val compose = view as? ComposeView ?: return
        attached = true
        (toolbar?.parent as? View)?.visibility = View.GONE
        banner?.visibility = View.GONE
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Top(ThemeUtils.isNightTheme()) }
    }

    internal fun searchPoint(activity: Activity?, type: RouteMarkType) {
        (activity as? RoutingBottomMenuListener)?.onSearchRoutePoint(type)
    }

    /** Le tappe (tavola MapViaggio5Tappe) in un foglio dal basso. */
    @JvmStatic
    fun showStops(activity: Activity) {
        val dialog = BottomSheetDialog(activity)
        val compose = ComposeView(activity)
        compose.setContent { Stops(ThemeUtils.isNightTheme(), activity) { dialog.dismiss() } }
        dialog.setContentView(compose)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }
}

// ---- Scheda in alto -------------------------------------------------------------------------------------------------

private class Glass(val glass: Color, val edge: Color)

private fun glass(night: Boolean) = if (night) Glass(Color(0xE61C2333), Color(0x29FFFFFF)) else Glass(Color(0xE6FFFFFF), Color(0x1F1E1D1B))

/** Il nome da mostrare per un punto: «La tua posizione», il nome del luogo o l'indirizzo. */
@Composable
private fun pointName(p: MapObject?): String = when {
    p == null -> stringResource(R.string.eurise_tr_choose)
    p.isMyPosition -> stringResource(R.string.eurise_tr_my_position)
    !p.title.isNullOrBlank() -> p.title
    !p.address.isNullOrBlank() -> p.address
    else -> stringResource(R.string.eurise_tr_point)
}

private class TopState(val start: MapObject?, val end: MapObject?, val stops: Int, val router: Router, val options: Set<RoadType>)

private fun readTop(): TopState {
    val rc = RoutingController.get()
    val router = rc.lastRouterType
    val stops = runCatching { Framework.nativeGetRoutePoints().count { it.mPointType == RouteMarkType.Intermediate } }.getOrDefault(0)
    return TopState(rc.startPoint, rc.endPoint, stops, router, runCatching { RoutingOptions.getActiveRoadTypes(router) }.getOrDefault(emptySet()))
}

@Composable
private fun Top(night: Boolean) {
    val c = if (night) Notte else Perla
    val g = glass(night)
    val context = LocalContext.current
    val activity = context.activity()
    val state by produceState(readTop()) {
        while (true) {
            value = readTop()
            delay(400)
        }
    }
    if (state.router == Router.Ruler) return
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(22.dp), clip = false).clip(RoundedCornerShape(22.dp))
                .background(g.glass).border(1.dp, g.edge, RoundedCornerShape(22.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoundIcon(TripIcons.back, c.accent, Color.Transparent, stringResource(R.string.eurise_tr_back)) {
                (activity as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
            }
            Column(Modifier.weight(1f)) {
                PointRow(c, kind = 0, name = pointName(state.start), bold = false) { EuriseViaggio.searchPoint(activity, RouteMarkType.Start) }
                if (state.stops > 0) {
                    Line(c)
                    PointRow(c, kind = 1, name = context.resources.getQuantityString(R.plurals.eurise_tr_stops, state.stops, state.stops), bold = false) {
                        activity?.let { EuriseViaggio.showStops(it) }
                    }
                }
                Line(c)
                PointRow(c, kind = 2, name = pointName(state.end), bold = true) { EuriseViaggio.searchPoint(activity, RouteMarkType.Finish) }
            }
            Box(Modifier.align(Alignment.CenterVertically)) {
                RoundIcon(TripIcons.swap, c.accent, if (night) Color(0xFF2C3547) else Color(0xFFEEF1F6), stringResource(R.string.eurise_tr_swap)) {
                    RoutingController.get().swapPoints()
                }
            }
        }
        // Le pillole del mezzo scelto: le scelte che il motore di CoMaps sa fare davvero, e «+ Tappa».
        val options = when (state.router) {
            Router.Vehicle -> listOf(RoadType.Toll to R.string.eurise_tr_avoid_toll, RoadType.Motorway to R.string.eurise_tr_avoid_motorway,
                RoadType.Ferry to R.string.eurise_tr_avoid_ferry)
            Router.Bicycle -> listOf(RoadType.Dirty to R.string.eurise_tr_avoid_dirty, RoadType.Steps to R.string.eurise_tr_avoid_steps,
                RoadType.Ferry to R.string.eurise_tr_avoid_ferry)
            Router.Pedestrian -> listOf(RoadType.Steps to R.string.eurise_tr_avoid_steps, RoadType.Dirty to R.string.eurise_tr_avoid_dirty)
            else -> emptyList()
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (type, label) ->
                val on = type in state.options
                Pill(c, g, stringResource(label), on) {
                    val router = RoutingController.get().lastRouterType
                    if (on) RoutingOptions.removeOption(type, router) else RoutingOptions.addOption(type, router)
                    RoutingController.get().rebuildLastRoute()
                }
            }
            if (state.router != Router.Transit && state.end != null)
                Pill(c, g, stringResource(R.string.eurise_tr_add_stop), false) { activity?.let { EuriseViaggio.showStops(it) } }
        }
    }
}

@Composable
private fun Line(c: Colors) = Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))

/** Un punto nella scheda: cerchio (partenza), quadrato (tappe), pallino dorato (arrivo). */
@Composable
private fun PointRow(c: Colors, kind: Int, name: String, bold: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(c, kind)
        BasicText(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = c.ink, fontSize = 14.5.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
    }
}

@Composable
private fun Dot(c: Colors, kind: Int) {
    when (kind) {
        0 -> Box(Modifier.size(12.dp).clip(CircleShape).border(3.dp, c.accent, CircleShape))
        1 -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3E5C8A)))
        else -> Box(Modifier.size(13.dp).clip(CircleShape).background(Color(0xFFD2B06A)))
    }
}

@Composable
private fun Pill(c: Colors, g: Glass, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).shadow(6.dp, RoundedCornerShape(17.dp), clip = false).clip(RoundedCornerShape(17.dp))
            .background(if (on) c.accent else g.glass).clickable(onClick = onClick)
            .semantics { role = Role.Switch; selected = on }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, maxLines = 1, style = TextStyle(color = if (on) c.onAccent else c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun RoundIcon(icon: ImageVector, tint: Color, bg: Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(19.dp), colorFilter = ColorFilter.tint(tint))
    }
}

// ---- Tappe (tavola MapViaggio5Tappe) -------------------------------------------------------------------------------

@Composable
private fun Stops(night: Boolean, activity: Activity, close: () -> Unit) {
    val c = if (night) Notte else Perla
    val points = remember { mutableStateListOf<RouteMarkData>().apply { addAll(runCatching { Framework.nativeGetRoutePoints() }.getOrDefault(emptyArray())) } }
    var changed by remember { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { 58.dp.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(stringResource(R.string.eurise_tr_stops_title), Modifier.weight(1f),
                style = TextStyle(color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
            val info = RoutingController.get().cachedRoutingInfo
            if (info != null && !changed) BasicText(tripDuration(info.totalTimeInSeconds) + " · " + info.distToTarget.toString(activity),
                style = TextStyle(color = c.muted, fontSize = 12.5.sp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp)) {
            points.forEachIndexed { i, p ->
                val lift = i == dragging
                Row(
                    Modifier.fillMaxWidth().height(58.dp).zIndex(if (lift) 1f else 0f)
                        .offset { IntOffset(0, if (lift) dragY.roundToInt() else 0) }
                        .background(if (lift) c.chip else Color.Transparent)
                        // Tenere premuto e trascinare cambia l'ordine (come nella tavola).
                        .pointerInput(points.size, i) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = i; dragY = 0f },
                                onDragEnd = { dragging = -1; dragY = 0f },
                                onDragCancel = { dragging = -1; dragY = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragY += amount.y
                                    val from = dragging
                                    if (from < 0) return@detectDragGesturesAfterLongPress
                                    val to = (from + (dragY / rowPx).roundToInt()).coerceIn(0, points.size - 1)
                                    if (to != from) {
                                        points.add(to, points.removeAt(from))
                                        dragY -= (to - from) * rowPx
                                        dragging = to
                                        changed = true
                                    }
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(rememberVectorPainter(TripIcons.drag), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.muted))
                    Dot(c, if (i == 0) 0 else if (i == points.size - 1) 2 else 1)
                    Column(Modifier.weight(1f)) {
                        val title = when {
                            p.mIsMyPosition -> stringResource(R.string.eurise_tr_my_position)
                            !p.mTitle.isNullOrBlank() -> p.mTitle!!
                            else -> stringResource(R.string.eurise_tr_point)
                        }
                        BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold))
                        if (!p.mSubtitle.isNullOrBlank())
                            BasicText(p.mSubtitle!!, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = c.muted, fontSize = 12.sp))
                    }
                    // Partenza e arrivo restano: si cambiano toccandoli nella scheda in alto.
                    if (points.size > 2 && i != 0 && i != points.size - 1)
                        RoundIcon(TripIcons.close, c.muted, c.chip, stringResource(R.string.eurise_tr_remove, p.mTitle ?: "")) {
                            points.removeAt(i)
                            changed = true
                        }
                }
                if (i < points.size - 1) Line(c)
            }
        }
        BasicText(stringResource(R.string.eurise_tr_along), Modifier.padding(start = 4.dp, top = 2.dp),
            style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.eurise_tr_along_fuel to R.string.category_fuel,
                R.string.eurise_tr_along_eat to R.string.category_eat,
                R.string.eurise_tr_along_parking to R.string.category_parking,
                R.string.eurise_tr_along_pharmacy to R.string.category_pharmacy,
            ).forEach { (label, category) ->
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.card).clickable {
                        apply(points, changed)
                        close()
                        // La ricerca della categoria: il luogo scelto entra come tappa (come «Aggiungi una tappa»).
                        RoutingController.get().waitForPoiPick(RouteMarkType.Intermediate)
                        EuriseEsplora.search(activity, category)
                    }.semantics { role = Role.Button }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(stringResource(label), style = TextStyle(color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)) }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0))
                .clickable {
                    apply(points, changed)
                    close()
                    EuriseViaggio.searchPoint(activity, RouteMarkType.Intermediate)
                }.semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { BasicText(stringResource(R.string.eurise_tr_add_stop_long), style = TextStyle(color = c.accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)) }
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(c.accent)
                .clickable { apply(points, changed); close() }.semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { BasicText(stringResource(R.string.eurise_tr_done), style = TextStyle(color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
        BasicText(stringResource(R.string.eurise_tr_stops_note), style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
        Spacer(Modifier.width(1.dp))
    }
}

/**
 * Rimette i punti nell'ordine scelto, come «Pianifica» di CoMaps (ManageRouteBottomSheet): via tutti, poi arrivo,
 * partenza e tappe in ordine, e il percorso si ricalcola.
 */
private fun apply(points: List<RouteMarkData>, changed: Boolean) {
    if (!changed || points.size < 2) return
    val list = points.toList()
    Framework.nativeRemoveRoutePoints()
    val last = list.last()
    last.mPointType = RouteMarkType.Finish
    last.mIntermediateIndex = 0
    Framework.addRoutePoint(last)
    val first = list.first()
    first.mPointType = RouteMarkType.Start
    first.mIntermediateIndex = 0
    Framework.addRoutePoint(first)
    for (i in 1 until list.size - 1) {
        val p = list[i]
        p.mPointType = RouteMarkType.Intermediate
        p.mIntermediateIndex = i - 1
        Framework.addRoutePoint(p, false)
    }
    RoutingController.get().launchPlanning()
}

/** «18 min», «1 h 5 min». */
internal fun tripDuration(seconds: Int): String {
    val m = (seconds + 30) / 60
    return if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}

/** Simboli a tratto delle tavole del viaggio. */
internal object TripIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    val car = icon("car", "M5 16V11l2-5h10l2 5v5M5 16h14M5 16v2.5M19 16v2.5M7.5 13h.01M16.5 13h.01")
    val bus = icon("bus", "M6 4h12a2 2 0 0 1 2 2v10H4V6a2 2 0 0 1 2-2zM4 11h16M7.5 19v-3M16.5 19v-3M8 14h.01M16 14h.01")
    val bike = icon("bike", "M6 17.5a3 3 0 1 0 0-.01M18 17.5a3 3 0 1 0 0-.01M6 17.5l4-7h5l3 7M10 10.5L8.5 7H7M14 7h2l-1 3.5")
    val walk = icon("walk", "M13 4.5a1.5 1.5 0 1 0 0-.01M10 21l2-6 2 2v4M9 11l3-3 3 3 2 1M12 8v7")
    val swap = icon("swap", "M7 4v16M7 4l-3 3M7 4l3 3M17 20V4M17 20l-3-3M17 20l3-3")
    val back = icon("back", "M15 5l-7 7 7 7")
    val drag = icon("drag", "M8 6h.01M8 12h.01M8 18h.01M16 6h.01M16 12h.01M16 18h.01")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
}
