package app.organicmaps.eurise.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.eurise.EuriseGuide
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.util.ThemeUtils
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.routing.RoutingOptions
import app.organicmaps.sdk.settings.RoadType
import androidx.compose.ui.graphics.asImageBitmap
import java.text.DateFormat
import java.util.Date

/**
 * Passo d) del piano di Mappe (tavola MapPercorso, approvata): sotto il percorso calcolato, il pannello della suite.
 * Auto, Bici e A piedi con il tempo del mezzo scelto; l'impegno della suite che ha questa destinazione con «Parti entro
 * le…»; «Manda l'arrivo» e «Salva il luogo»; «Avvia la navigazione» grande al posto del pulsante di CoMaps (che resta nel
 * layout e fa il lavoro). Il percorso si calcola sul telefono: aprire il pannello non manda niente in rete.
 */
object EurisePercorso {
    private val tick = mutableIntStateOf(0)
    private var startButton: View? = null

    /** C'è il pannello: il pulsante «Inizia» di CoMaps non si mostra (lo usa il nostro). */
    @JvmStatic
    var attached = false
        private set

    /** Le righe di CoMaps nel pannello (tempo e dislivello, grafico con «Inizia», pulsanti): nascoste, c'è il riassunto. */
    private var comapsRows: List<View> = emptyList()

    @JvmStatic
    fun attach(view: View?, start: View?) {
        val compose = view as? ComposeView ?: return
        startButton = start
        attached = true
        val panel = compose.parent as? View
        comapsRows = listOfNotNull(
            panel?.findViewById(R.id.time_elevation_line),
            start?.parent as? View,
            panel?.findViewById<View>(R.id.btn__manage_route)?.parent as? View,
        )
        hideComaps()
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            // Il numero del giro entra come parametro: senza, Compose saltava il pannello (parametri uguali) e restava
            // fermo allo stato del primo disegno, con «Avvia la navigazione» spento (prova del 13 novembre, sera).
            Panel(ThemeUtils.isNightTheme(), tick.intValue)
        }
    }

    /** Il percorso è pronto, cambiato o tolto (da RoutingBottomMenuController). */
    @JvmStatic
    fun update() {
        tick.intValue++
        hideComaps()
        // CoMaps rimette in vista le sue righe dopo aver aggiornato il pannello: si nascondono anche al giro dopo.
        comapsRows.firstOrNull()?.post { hideComaps() }
    }

    private fun hideComaps() = comapsRows.forEach { it.visibility = View.GONE }

    /**
     * «Avvia la navigazione»: lo stesso percorso di «Inizia» di CoMaps (MwmActivity.onRoutingStart: punto di partenza,
     * avviso la prima volta, voce), chiamato direttamente. Il clic sul pulsante nascosto dipendeva dallo stato di CoMaps e
     * a volte non partiva (prova del 13 novembre); resta solo come ripiego.
     */
    internal fun start(context: Context) {
        val rc = RoutingController.get()
        if (!rc.isBuilt) {
            // Mai un tasto morto (prova del 15 novembre: grigio per minuti): si dice cosa succede e, se il calcolo si
            // è fermato senza risultato, si riparte.
            if (rc.isBuilding) {
                android.widget.Toast.makeText(context, R.string.eurise_pc_building, android.widget.Toast.LENGTH_SHORT).show()
            } else if (rc.isPlanning) {
                android.widget.Toast.makeText(context, R.string.eurise_pc_rebuilding, android.widget.Toast.LENGTH_SHORT).show()
                rc.rebuildLastRoute()
            }
            update()
            return
        }
        val listener = context.activity() as? app.organicmaps.routing.RoutingBottomMenuListener
        if (listener != null && RoutingController.get().isPlanning) listener.onRoutingStart()
        else startButton?.performClick()
    }
}

@Composable
private fun Panel(night: Boolean, version: Int) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val rc = RoutingController.get()
    val end: MapObject? = rc.endPoint
    // Lo stato del percorso si rilegge da solo mentre si pianifica: il pannello non dipende più soltanto dagli
    // avvisi di CoMaps (se uno si perde, per esempio dopo che l'attività ha salvato lo stato, il tasto restava grigio).
    val state by produceState(RouteState.read(), version) {
        while (true) {
            value = RouteState.read()
            if (!RoutingController.get().isPlanning) break
            delay(400)
        }
    }
    val info = state.info
    val built = state.built
    val router = rc.lastRouterType
    val destinations by produceState<Destinations?>(null, end) { value = loadDestinations(context.applicationContext) }
    if (router == Router.Ruler) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Tavole MapMezzi e MapViaggio4Percorso: Auto di base, Mezzi, Bici, A piedi con simbolo e tempo; il tempo del
        // mezzo scelto è quello del percorso, gli altri una stima dalla distanza in linea d'aria (≈). «Mezzi» apre gli
        // orari (Transitous, dal centro privacy).
        val start = rc.startPoint
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val seconds = if (built) info?.totalTimeInSeconds ?: -1 else -1
            val km = if (start != null && end != null) kmBetween(start.lat, start.lon, end.lat, end.lon) * 1.3 else -1.0
            Mode(c, TripIcons.car, R.string.eurise_pc_car, Router.Vehicle, router, seconds, km, 45.0, Modifier.weight(1f))
            if (start != null && end != null && !end.isMyPosition)
                Transit(c, Modifier.weight(1f)) { EuriseMezziActivity.open(context, start, end) }
            Mode(c, TripIcons.bike, R.string.eurise_pc_bike, Router.Bicycle, router, seconds, km, 15.0, Modifier.weight(1f))
            Mode(c, TripIcons.walk, R.string.eurise_pc_walk, Router.Pedestrian, router, seconds, km, 4.8, Modifier.weight(1f))
        }

        // Il riassunto del percorso (tavole 4 e 6): tempo, distanza e arrivo; le scelte attive o il dislivello; l'impegno
        // della suite con questo luogo e quando partire. Al posto delle righe di CoMaps (tempo, grafico, pulsanti).
        val d = if (end != null) destinations?.items?.let { matching(it, end) } else null
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            val now = System.currentTimeMillis()
            if (built && info != null) {
                val arrive = fmt.format(Date(now + info.totalTimeInSeconds * 1000L))
                BasicText(stringResource(R.string.eurise_pc_summary, tripDuration(info.totalTimeInSeconds), info.distToTarget.toString(context), arrive),
                    style = TextStyle(color = c.ink, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold))
            } else {
                BasicText(stringResource(R.string.eurise_pc_building), style = TextStyle(color = c.ink, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold))
            }
            val avoid = runCatching { RoutingOptions.getActiveRoadTypes(router) }.getOrDefault(emptySet())
            val avoidText = avoid.mapNotNull { avoidLabel(it) }.map { stringResource(it) }
            if (avoidText.isNotEmpty())
                BasicText(avoidText.joinToString(" · "), style = TextStyle(color = c.muted, fontSize = 12.5.sp))
            if (built && (router == Router.Bicycle || router == Router.Pedestrian)) Altitude(c, state.version)
            if (d != null) {
                val seconds = if (built) info?.totalTimeInSeconds ?: 0 else 0
                val leave = d.start - seconds * 1000L - EuriseMargin
                val line = when {
                    !built -> stringResource(R.string.eurise_pc_event_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.start)))
                    leave > now -> stringResource(R.string.eurise_pc_leave_by, fmt.format(Date(leave)))
                    else -> stringResource(R.string.eurise_pc_leave_now, fmt.format(Date(now + seconds * 1000L)))
                }
                BasicText(d.title + " · " + line, Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = if (night) Color(0xFFA9C1F2) else Color(0xFF3E5C8A), fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Soft(c, night, stringResource(R.string.eurise_pc_send_arrival), Modifier.weight(1f), enabled = built && info != null) {
                context.activity()?.let { EuriseGuide.shareArrival(it, rc.cachedRoutingInfo) }
            }
            val saved = end?.isBookmark == true
            Soft(c, night, stringResource(if (saved) R.string.eurise_pl_saved else R.string.eurise_pc_save_place), Modifier.weight(1f),
                enabled = end != null && !end.isMyPosition && !saved) {
                end?.let { EuriseLuogo.save(it); EurisePercorso.update() }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
                .background(if (built) c.accent else c.accent.copy(alpha = 0.35f))
                .clickable { EurisePercorso.start(context) }.semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(if (built || !state.building) R.string.eurise_pc_start else R.string.eurise_pc_building), style = TextStyle(color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        BasicText(stringResource(R.string.eurise_pc_offline), style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}

/** Quello che il pannello legge di CoMaps, riletto ogni 400 ms mentre si pianifica. */
private data class RouteState(val built: Boolean, val building: Boolean, val info: app.organicmaps.sdk.routing.RoutingInfo?) {
    /** Cambia quando cambia il percorso: il grafico del dislivello si ridisegna solo allora. */
    val version: Int get() = (info?.totalTimeInSeconds ?: 0) * 31 + (if (built) 1 else 0)

    companion object {
        fun read(): RouteState {
            val rc = RoutingController.get()
            return RouteState(rc.isBuilt, rc.isBuilding, rc.cachedRoutingInfo)
        }
    }
}

/** «Senza pedaggi», «senza sterrati»…: le scelte attive del mezzo, dette nel riassunto. */
private fun avoidLabel(t: RoadType): Int? = when (t) {
    RoadType.Toll -> R.string.eurise_pc_no_toll
    RoadType.Motorway -> R.string.eurise_pc_no_motorway
    RoadType.Ferry -> R.string.eurise_pc_no_ferry
    RoadType.Dirty -> R.string.eurise_pc_no_dirty
    RoadType.Steps -> R.string.eurise_pc_no_steps
    else -> null
}

/** Il dislivello in bici e a piedi (tavola MapViaggio6Bici): salita, discesa e il profilo disegnato dal motore. */
@Composable
private fun Altitude(c: Colors, version: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val w = with(density) { 300.dp.roundToPx() }
    val h = with(density) { 56.dp.roundToPx() }
    val chart = androidx.compose.runtime.remember(version) {
        val limits = Framework.RouteAltitudeLimits()
        val bmp = runCatching { Framework.generateRouteAltitudeChart(w, h, limits) }.getOrNull()
        bmp to limits
    }
    val (bmp, limits) = chart
    if (bmp == null) return
    BasicText(stringResource(R.string.eurise_pc_altitude, limits.totalAscentString ?: "", limits.totalDescentString ?: ""),
        style = TextStyle(color = c.muted, fontSize = 12.5.sp))
    androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp),
        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
}

/** Margine prima dell'impegno: si arriva qualche minuto prima. */
private const val EuriseMargin = 10 * 60_000L

@Composable
private fun Mode(c: Colors, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, mode: Router, current: Router, seconds: Int, km: Double, kmh: Double, modifier: Modifier) {
    val on = mode == current
    val time = when {
        on && seconds > 0 -> tripDuration(seconds)
        !on && km > 0 -> "≈ " + tripDuration((km / kmh * 3600).toInt())
        else -> ""
    }
    ModeChip(c, icon, stringResource(label), time, on, modifier) { RoutingController.get().setRouterType(mode); EurisePercorso.update() }
}

@Composable
private fun Transit(c: Colors, modifier: Modifier, onClick: () -> Unit) {
    ModeChip(c, TripIcons.bus, stringResource(R.string.eurise_pc_transit), stringResource(R.string.eurise_pc_transit_sub), false, modifier, onClick)
}

/** Un mezzo nella fila della tavola: simbolo, nome e tempo, stessa larghezza per tutti. */
@Composable
private fun ModeChip(c: Colors, icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, time: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(62.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.accent else c.card)
            .clickable(enabled = !on, onClick = onClick)
            .semantics { role = Role.Tab; selected = on }.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.graphics.vector.rememberVectorPainter(icon), null, Modifier.size(18.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (on) c.onAccent else c.ink))
        BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (on) c.onAccent else c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        if (time.isNotEmpty()) BasicText(time, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (on) c.onAccent.copy(alpha = 0.8f) else c.muted, fontSize = 11.5.sp))
    }
}

@Composable
private fun Soft(c: Colors, night: Boolean, label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0)
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (enabled) c.accent else c.muted, fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
    }
}

