package app.organicmaps.eurise.ui

import app.organicmaps.eurise.EuriseSuite
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.FeatureId
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.util.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Mezzi pubblici e viaggi lontani (tavole MapMezzi, MapLontano, MapLontanoScelta, approvate).
 *
 * Vicino: gli itinerari a piedi e coi mezzi, la tratta per tratta del primo e le partenze dopo; «Avvia la guida a piedi»
 * porta alla prima fermata con la guida di Mappe. Lontano (oltre 80 km): filtri Consigliato, Più veloce, Senza cambi,
 * «Parti dopo» e «Arriva entro», una scheda per itinerario; toccata, si apre la scelta con «Compra il biglietto» (sito
 * dell'azienda) e «Metti nella suite» (Calendario e Note, ognuno con l'interruttore). Prezzi: Transitous non li dà in modo
 * affidabile, si legge «sul sito del vettore». Aereo e pedaggi: nessuna fonte libera verificata, non si inventano.
 */
class EuriseMezziActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val e = intent.extras ?: Bundle()
        val trip = Trip(
            e.getDouble(FROM_LAT), e.getDouble(FROM_LON), e.getDouble(TO_LAT), e.getDouble(TO_LON),
            e.getString(TO_NAME).orEmpty(),
        )
        setContentView(ComposeView(this).apply { setContent { Screen(ThemeUtils.isNightTheme(), trip, ::finish) } })
    }

    companion object {
        private const val FROM_LAT = "from_lat"
        private const val FROM_LON = "from_lon"
        private const val TO_LAT = "to_lat"
        private const val TO_LON = "to_lon"
        private const val TO_NAME = "to_name"

        /** Dal pannello del percorso: partenza e arrivo del percorso che si sta pianificando. */
        @JvmStatic
        fun open(context: Context, from: MapObject, to: MapObject) {
            val i = Intent(context, EuriseMezziActivity::class.java)
                .putExtra(FROM_LAT, from.lat).putExtra(FROM_LON, from.lon)
                .putExtra(TO_LAT, to.lat).putExtra(TO_LON, to.lon)
                .putExtra(TO_NAME, to.title)
            runCatching { context.startActivity(i) }
        }
    }
}

internal class Trip(val fromLat: Double, val fromLon: Double, val toLat: Double, val toLon: Double, val toName: String) {
    val km get() = kmBetween(fromLat, fromLon, toLat, toLon)
    val far get() = km > 80.0
}

internal fun kmBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, r)
    return r[0] / 1000.0
}

private enum class Sort { Best, Fast, Direct }

@Composable
private fun Screen(night: Boolean, trip: Trip, onClose: () -> Unit) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // null: si sta guardando; false: servizio spento; true: acceso (o app Eurise assente: si prova).
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var list by remember { mutableStateOf<List<EuriseTransit.Itinerary>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(Sort.Best) }
    var time by remember { mutableStateOf(System.currentTimeMillis()) }
    var arriveBy by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<EuriseTransit.Itinerary?>(null) }

    fun load() {
        scope.launch {
            failed = false
            list = null
            val on = withContext(Dispatchers.IO) { EuriseTransit.privacy(context) }
            allowed = on != false
            if (on == false) return@launch
            val res = withContext(Dispatchers.IO) {
                EuriseTransit.plan(context, trip.fromLat, trip.fromLon, trip.toLat, trip.toLon, time, arriveBy)
            }
            failed = res == null
            list = res ?: emptyList()
        }
    }
    LaunchedEffect(time, arriveBy) { load() }

    val pick = chosen
    if (pick != null) {
        Chosen(c, night, trip, pick) { chosen = null }
        return
    }

    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(if (trip.far) R.string.eurise_mz_far_title else R.string.eurise_mz_title, trip.toName.ifBlank { "…" }),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                BasicText(stringResource(R.string.eurise_mz_sub, Math.round(trip.km).toInt()),
                    style = TextStyle(color = c.muted, fontSize = 12.5.sp))
            }
            BasicText(stringResource(R.string.eurise_fuel_close), Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClose).padding(12.dp),
                style = TextStyle(color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        if (trip.far) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(c, if (!arriveBy) stringResource(R.string.eurise_mz_leave_after, fmt.format(Date(time))) else stringResource(R.string.eurise_mz_leave_now),
                    !arriveBy, Modifier.weight(1f)) {
                    if (arriveBy) { arriveBy = false; time = System.currentTimeMillis() } else time += 60 * 60_000L
                }
                Pill(c, if (arriveBy) stringResource(R.string.eurise_mz_arrive_by, fmt.format(Date(time))) else stringResource(R.string.eurise_mz_arrive_by_q),
                    arriveBy, Modifier.weight(1f)) {
                    if (!arriveBy) { arriveBy = true; time = System.currentTimeMillis() + 4 * 60 * 60_000L } else time += 60 * 60_000L
                }
            }
            LazyRow(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Chip(c, stringResource(R.string.eurise_mz_best), sort == Sort.Best) { sort = Sort.Best } }
                item { Chip(c, stringResource(R.string.eurise_mz_fast), sort == Sort.Fast) { sort = Sort.Fast } }
                item { Chip(c, stringResource(R.string.eurise_mz_direct), sort == Sort.Direct) { sort = Sort.Direct } }
            }
        }
        val l = list
        when {
            allowed == false -> Message(c, stringResource(R.string.eurise_mz_off), stringResource(R.string.eurise_mz_turn_on)) {
                scope.launch { withContext(Dispatchers.IO) { EuriseTransit.privacy(context, "enable") }; load() }
            }
            failed -> Message(c, stringResource(R.string.eurise_mz_failed), stringResource(R.string.eurise_fuel_retry)) { load() }
            l == null -> Message(c, stringResource(R.string.eurise_mz_loading), null) {}
            l.isEmpty() -> Message(c, stringResource(R.string.eurise_mz_none), null) {}
            else -> {
                val sorted = when (sort) {
                    Sort.Best -> l
                    Sort.Fast -> l.sortedBy { it.seconds }
                    Sort.Direct -> l.sortedWith(compareBy({ it.transfers }, { it.seconds }))
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!trip.far) {
                        val first = sorted.first()
                        item { Steps(c, first) }
                        val later = sorted.drop(1).map { fmt.format(Date(it.rides.firstOrNull()?.start ?: it.start)) }.distinct()
                        if (later.isNotEmpty()) item {
                            BasicText(stringResource(R.string.eurise_mz_later, later.joinToString(" · ")),
                                Modifier.clip(RoundedCornerShape(12.dp)).background(c.card).padding(horizontal = 10.dp, vertical = 6.dp),
                                style = TextStyle(color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                        }
                        item {
                            val firstStop = first.rides.firstOrNull()
                            Primary(c, stringResource(R.string.eurise_mz_walk_to, firstStop?.from ?: trip.toName)) {
                                walkTo(context, firstStop, trip)
                                onClose()
                            }
                        }
                    } else {
                        items(sorted.size) { i -> Option(c, sorted[i], i == 0 && sort == Sort.Best) { chosen = sorted[i] } }
                    }
                    item { Source(c) }
                }
            }
        }
        if (allowed == false || failed || l.isNullOrEmpty()) Box(Modifier.padding(16.dp)) { Source(c) }
    }
}

/** La guida a piedi di Mappe fino alla prima fermata (o fino all'arrivo se non c'è un mezzo). */
private fun walkTo(context: Context, stop: EuriseTransit.Leg?, trip: Trip) {
    val rc = RoutingController.get()
    val from = rc.startPoint
    val lat = stop?.fromLat?.takeIf { !it.isNaN() } ?: trip.toLat
    val lon = stop?.fromLon?.takeIf { !it.isNaN() } ?: trip.toLon
    val name = stop?.from ?: trip.toName
    val to = MapObject.createMapObject(FeatureId.EMPTY, MapObject.API_POINT, name, "", lat, lon)
    rc.setRouterType(Router.Pedestrian)
    rc.prepare(from, to, Router.Pedestrian)
    Toast.makeText(context, context.getString(R.string.eurise_mz_walking, name), Toast.LENGTH_SHORT).show()
}

@Composable
private fun Steps(c: Colors, it: EuriseTransit.Itinerary) {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(stringResource(R.string.eurise_mz_leave_arrive, fmt.format(Date(it.start)), fmt.format(Date(it.end))),
            style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
        it.legs.forEach { leg -> LegRow(c, leg, showTime = false) }
    }
}

@Composable
private fun LegRow(c: Colors, leg: EuriseTransit.Leg, showTime: Boolean) {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val minutes = ((leg.end - leg.start) / 60_000L).toInt().coerceAtLeast(1)
    val title = if (leg.walk) stringResource(R.string.eurise_mz_walk_min, minutes)
    else listOf(leg.line, leg.headsign.takeIf { it.isNotBlank() }?.let { "→ $it" }).filterNotNull().joinToString(" ") +
        " · " + fmt.format(Date(leg.start)) + " " + stringResource(R.string.eurise_mz_from, leg.from)
    val sub = if (leg.walk) stringResource(R.string.eurise_mz_until, leg.to)
    else stringResource(R.string.eurise_mz_stops, leg.stops, leg.to)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showTime) BasicText(fmt.format(Date(leg.start)), Modifier.width(44.dp).padding(top = 6.dp),
            style = TextStyle(color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(
            if (leg.walk) Color(0xFF8A8A82) else leg.color?.let { Color(it) } ?: Color(0xFF3E5C8A)),
            contentAlignment = Alignment.Center) {
            BasicText(if (leg.walk) "•" else leg.line.take(3), style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            BasicText(sub, style = TextStyle(color = c.muted, fontSize = 12.sp))
        }
    }
}

@Composable
private fun Option(c: Colors, it: EuriseTransit.Itinerary, best: Boolean, onClick: () -> Unit) {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val main = it.rides.maxByOrNull { r -> r.end - r.start }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(listOfNotNull(main?.agency?.takeIf { a -> a.isNotBlank() }, main?.line?.takeIf { l -> l.isNotBlank() }).joinToString(" · ")
                .ifBlank { stringResource(R.string.eurise_mz_public) },
                Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold))
            if (best) BasicText(stringResource(R.string.eurise_mz_best),
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFE3EDE6)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(color = Color(0xFF4F6B5A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
        }
        Row {
            BasicText("${fmt.format(Date(it.start))} → ${fmt.format(Date(it.end))}", Modifier.weight(1f),
                style = TextStyle(color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            BasicText(stringResource(R.string.eurise_mz_price_site), style = TextStyle(color = c.muted, fontSize = 12.sp))
        }
        BasicText(stringResource(R.string.eurise_mz_door, duration(it.seconds), it.transfers),
            style = TextStyle(color = c.muted, fontSize = 12.sp))
    }
}

/** La scelta (tavola MapLontanoScelta): tratte con l'ora, biglietto sul sito, e cosa mettere nella suite. */
@Composable
private fun Chosen(c: Colors, night: Boolean, trip: Trip, it: EuriseTransit.Itinerary, onBack: () -> Unit) {
    val context = LocalContext.current
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    var calendar by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf(false) }
    val main = it.rides.maxByOrNull { r -> r.end - r.start }
    val title = listOfNotNull(main?.agency?.takeIf { a -> a.isNotBlank() }, main?.line?.takeIf { l -> l.isNotBlank() })
        .joinToString(" · ").ifBlank { stringResource(R.string.eurise_mz_public) }
    LazyColumn(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(title, style = TextStyle(color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
                    BasicText(stringResource(R.string.eurise_mz_door_times, fmt.format(Date(it.start)), fmt.format(Date(it.end))),
                        style = TextStyle(color = c.muted, fontSize = 12.5.sp))
                }
                BasicText(stringResource(R.string.eurise_mz_back), Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack).padding(12.dp),
                    style = TextStyle(color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                it.legs.forEach { leg -> LegRow(c, leg, showTime = true) }
            }
        }
        val site = it.rides.firstOrNull { r -> r.agencyUrl.startsWith("http") }
        if (site != null) item {
            Soft(c, night, stringResource(R.string.eurise_mz_buy, site.agency.ifBlank { Uri.parse(site.agencyUrl).host.orEmpty() })) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.agencyUrl))) }
            }
        }
        item {
            BasicText(stringResource(R.string.eurise_mz_suite).uppercase(), Modifier.padding(start = 4.dp, top = 6.dp),
                style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp))
        }
        item {
            SuiteToggle(c, stringResource(R.string.eurise_mz_calendar),
                stringResource(R.string.eurise_mz_calendar_sub, trip.toName, fmt.format(Date(it.start))), calendar) { v -> calendar = v }
        }
        item { SuiteToggle(c, stringResource(R.string.eurise_mz_notes), stringResource(R.string.eurise_mz_notes_sub), notes) { v -> notes = v } }
        item {
            Primary(c, stringResource(R.string.eurise_mz_confirm)) {
                val text = summary(context, trip, it)
                if (calendar) {
                    val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, context.getString(R.string.eurise_mz_event_title, trip.toName))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.start)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.end)
                        .putExtra(CalendarContract.Events.EVENT_LOCATION, it.rides.firstOrNull()?.from.orEmpty())
                        .putExtra(CalendarContract.Events.DESCRIPTION, text)
                    runCatching { context.startActivity(i) }
                }
                if (notes) note(context, text)
            }
        }
        item { Source(c) }
    }
}

private fun summary(context: Context, trip: Trip, it: EuriseTransit.Itinerary): String {
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val lines = it.legs.map { leg ->
        if (leg.walk) context.getString(R.string.eurise_mz_note_walk, fmt.format(Date(leg.start)), leg.to)
        else context.getString(R.string.eurise_mz_note_ride, fmt.format(Date(leg.start)), listOf(leg.agency, leg.line).filter { s -> s.isNotBlank() }.joinToString(" "), leg.from, leg.to)
    }
    return (listOf(context.getString(R.string.eurise_mz_event_title, trip.toName)) + lines).joinToString("\n")
}

private fun note(context: Context, text: String) {
    for (hub in HUBS) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            .setComponent(ComponentName(hub, "eu.eurise.hub.ShareToNotesActivity"))
        // Solo verso l'app Eurise della suite: un'app con lo stesso nome non riceve il testo della nota.
        val safe = EuriseSuite.trusted(context, i) ?: continue
        if (runCatching { context.startActivity(safe) }.isSuccess) return
    }
}

@Composable
private fun Source(c: Colors) {
    val context = LocalContext.current
    BasicText(stringResource(R.string.eurise_mz_source), Modifier.clip(RoundedCornerShape(10.dp))
        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EuriseTransit.SOURCES))) } }
        .padding(4.dp),
        style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
}

@Composable
private fun Message(c: Colors, text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(text, style = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 20.sp))
        if (action != null) Primary(c, action, onAction)
    }
}

@Composable
private fun Primary(c: Colors, label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(c.accent).clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun Soft(c: Colors, night: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0))
            .clickable(onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { BasicText(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = c.accent, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun Pill(c: Colors, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(c.card).clickable(onClick = onClick)
        .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
        BasicText(label, maxLines = 1, style = TextStyle(color = if (on) c.ink else c.muted, fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold))
    }
}

@Composable
private fun Chip(c: Colors, text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(34.dp).clip(RoundedCornerShape(12.dp)).background(if (on) c.accent else c.card).clickable(onClick = onClick)
        .semantics { role = Role.Tab }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = if (on) c.onAccent else c.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SuiteToggle(c: Colors, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(c.card).clickable { onChange(!on) }
            .semantics { role = Role.Switch }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold))
            BasicText(sub, style = TextStyle(color = c.muted, fontSize = 11.5.sp))
        }
        Box(Modifier.size(42.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) c.accent else Color(0xFFC9C9C2)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
        }
    }
}

private fun duration(seconds: Int): String {
    val m = (seconds + 30) / 60
    return if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}
