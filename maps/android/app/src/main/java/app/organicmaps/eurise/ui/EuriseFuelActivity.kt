package app.organicmaps.eurise.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.eurise.ui.EuriseFuelData.Fuel
import app.organicmaps.eurise.ui.EuriseFuelData.Station
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.util.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * I benzinai col prezzo (tavola MapAttivita, approvata): più economici, più vicini o sul percorso in corso, per carburante.
 * I prezzi sono del Ministero (vedi [EuriseFuelData]), sul telefono; un tocco mostra l'impianto sulla mappa.
 */
class EuriseFuelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply { setContent { Screen(ThemeUtils.isNightTheme(), ::finish) } })
    }

    companion object {
        @JvmStatic
        fun open(context: Context) = runCatching { context.startActivity(Intent(context, EuriseFuelActivity::class.java)) }
    }
}

private enum class Order { Cheap, Near, Route }

private class FuelRow(val station: Station, val km: Double, val price: Double, val self: Boolean)

/** Dalla posizione salvata di CoMaps (l'ultima nota), senza chiederne una nuova. */
private fun here(c: Context): Pair<Double, Double>? = runCatching {
    MwmApplication.from(c).locationHelper.savedLocation?.let { it.latitude to it.longitude }
}.getOrNull()

private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val k = Math.cos(Math.toRadians((lat1 + lat2) / 2))
    val dx = Math.toRadians(lon2 - lon1) * k
    val dy = Math.toRadians(lat2 - lat1)
    return 6371.0 * Math.sqrt(dx * dx + dy * dy)
}

/** Il percorso in corso, a punti ogni ~200 m (per «sul percorso»); vuoto se non c'è. */
private fun routePoints(): List<Pair<Double, Double>> {
    val j = runCatching { RoutingController.get().routeGeometry.mJunctions }.getOrNull() ?: return emptyList()
    if (j.size < 2) return emptyList()
    val out = ArrayList<Pair<Double, Double>>()
    var last: Pair<Double, Double>? = null
    for (p in j) {
        val cur = p.mLat to p.mLon
        if (last == null || km(last.first, last.second, cur.first, cur.second) > 0.2) {
            out += cur
            last = cur
        }
    }
    return out
}

private fun rows(all: List<Station>, fuel: Fuel, order: Order, here: Pair<Double, Double>?, route: List<Pair<Double, Double>>): List<FuelRow> {
    val i = fuel.ordinal
    val list = all.asSequence().filter { !it.prices[i].isNaN() }.map { s ->
        FuelRow(s, here?.let { km(it.first, it.second, s.lat, s.lon) } ?: Double.NaN, s.prices[i], s.self[i])
    }
    val picked = when (order) {
        // Sul percorso: entro 1 km da un punto della strada che si fa.
        Order.Route -> list.filter { r -> route.any { km(it.first, it.second, r.station.lat, r.station.lon) < 1.0 } }.sortedBy { it.price }
        // Più vicini e più economici: entro 5 km (o i più vicini, se la posizione non c'è).
        Order.Near -> list.filter { it.km.isNaN() || it.km <= 5.0 }.sortedBy { it.km }
        Order.Cheap -> list.filter { it.km.isNaN() || it.km <= 5.0 }.sortedBy { it.price }
    }
    return picked.take(60).toList()
}

@Composable
private fun Screen(night: Boolean, onClose: () -> Unit) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fuel by remember { mutableStateOf(Fuel.Benzina) }
    var order by remember { mutableStateOf(Order.Cheap) }
    var stations by remember { mutableStateOf<List<Station>?>(null) }
    // null: si sta guardando; false: il servizio è spento nel centro privacy; true: acceso (o app Eurise assente).
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var failed by remember { mutableStateOf(false) }
    val route = remember { routePoints() }
    val here = remember { here(context) }

    fun load(force: Boolean = false) {
        scope.launch {
            failed = false
            val ok = withContext(Dispatchers.IO) {
                allowed = EuriseFuelData.privacy(context) != false
                if (allowed == true) EuriseFuelData.refresh(context, force) else EuriseFuelData.age(context) >= 0
            }
            stations = if (ok) withContext(Dispatchers.IO) { EuriseFuelData.stations(context) } else emptyList()
            failed = !ok && allowed == true
        }
    }
    LaunchedEffect(Unit) { load() }

    val list = remember(stations, fuel, order) { stations?.let { rows(it, fuel, order, here, route) } }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.eurise_fuel_title), Modifier.weight(1f),
                style = TextStyle(color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold))
            BasicText(stringResource(R.string.eurise_fuel_close), Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClose).padding(12.dp),
                style = TextStyle(color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        LazyRow(Modifier.padding(top = 10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Chip(c, stringResource(R.string.eurise_fuel_cheap), order == Order.Cheap) { order = Order.Cheap } }
            item { Chip(c, stringResource(R.string.eurise_fuel_near), order == Order.Near) { order = Order.Near } }
            if (route.isNotEmpty()) item { Chip(c, stringResource(R.string.eurise_fuel_route), order == Order.Route) { order = Order.Route } }
            items(Fuel.entries.toList(), key = { it.name }) { f -> Chip(c, label(f), fuel == f, outlined = true) { fuel = f } }
        }
        when {
            allowed == false && list.isNullOrEmpty() -> Message(c, stringResource(R.string.eurise_fuel_off),
                stringResource(R.string.eurise_fuel_turn_on)) {
                scope.launch { withContext(Dispatchers.IO) { EuriseFuelData.privacy(context, "enable") }; load() }
            }
            failed -> Message(c, stringResource(R.string.eurise_fuel_failed), stringResource(R.string.eurise_fuel_retry)) { load(force = true) }
            list == null -> Message(c, stringResource(R.string.eurise_fuel_loading), null) {}
            list.isEmpty() -> Message(c, stringResource(R.string.eurise_fuel_none), null) {}
            else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                item {
                    val head = stringResource(R.string.eurise_fuel_section, label(fuel),
                        stringResource(if (order == Order.Route) R.string.eurise_fuel_on_route else R.string.eurise_fuel_within))
                    BasicText(head.uppercase(), Modifier.padding(start = 4.dp, bottom = 8.dp),
                        style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp))
                }
                val cheapest = list.minByOrNull { it.price }
                items(list, key = { "${it.station.lat},${it.station.lon},${it.station.name}" }) { r ->
                    StationRow(c, r, r === cheapest) { show(context, r.station); onClose() }
                }
                item {
                    BasicText(stringResource(R.string.eurise_fuel_source), Modifier.padding(start = 4.dp, top = 10.dp),
                        style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
                }
            }
        }
    }
}

@Composable
private fun label(f: Fuel) = stringResource(when (f) {
    Fuel.Benzina -> R.string.eurise_fuel_petrol
    Fuel.Gasolio -> R.string.eurise_fuel_diesel
    Fuel.Gpl -> R.string.eurise_fuel_lpg
    Fuel.Metano -> R.string.eurise_fuel_methane
})

/** Mostra l'impianto sulla mappa di Mappe (link geo: gestito dall'app stessa). */
private fun show(c: Context, s: Station) {
    val lat = String.format(Locale.ROOT, "%.6f", s.lat)
    val lon = String.format(Locale.ROOT, "%.6f", s.lon)
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(s.name.ifBlank { s.brand })})")
    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(c.packageName).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
}

@Composable
private fun Chip(c: Colors, text: String, on: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (on) c.accent else if (outlined) c.chip else c.card).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = if (on) c.onAccent else c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun StationRow(c: Colors, r: FuelRow, best: Boolean, onClick: () -> Unit) {
    val s = r.station
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(c.card).clickable(onClick = onClick)
            .heightIn(min = 66.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(listOf(s.brand, s.name).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { s.address },
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            val km = if (r.km.isNaN()) null else String.format(Locale.getDefault(), "%.1f km", r.km)
            val mode = stringResource(if (r.self) R.string.eurise_fuel_self else R.string.eurise_fuel_served)
            BasicText(listOfNotNull(km, mode, s.address.takeIf { it.isNotBlank() }).joinToString(" · "), maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = TextStyle(color = c.muted, fontSize = 12.sp))
        }
        Column(horizontalAlignment = Alignment.End) {
            BasicText(String.format(Locale.ITALY, "%.3f", r.price),
                style = TextStyle(color = if (best) c.ok else c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
            BasicText(stringResource(R.string.eurise_fuel_unit), style = TextStyle(color = c.muted, fontSize = 11.sp))
        }
    }
}

@Composable
private fun Message(c: Colors, text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(text, style = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 20.sp))
        if (action != null) Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(c.accent).clickable(onClick = onAction).padding(horizontal = 18.dp, vertical = 12.dp),
        ) { BasicText(action, style = TextStyle(color = c.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
    }
}

