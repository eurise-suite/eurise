package app.organicmaps.eurise.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.MwmActivity
import app.organicmaps.R
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.bookmarks.data.BookmarkManager
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.bookmarks.data.Metadata
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.util.ThemeUtils
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Passo c) del piano di Mappe (tavole MapLuogo e MapLuogoAperta, approvate): nella scheda del luogo di CoMaps, sotto
 * nome, tipo e indirizzo, le azioni della suite. «Indicazioni» grande e «Parti ora» (il percorso si calcola e la guida
 * parte da sola), poi Salva, Chiama, Impegno, Condividi, e l'impegno della suite che ha questo luogo. Le righe di dettaglio
 * di CoMaps (orari, contatti, Wikipedia) restano sotto. Aprire la scheda non manda niente in rete.
 */
object EuriseLuogo {

    /** «Parti ora»: appena il percorso è pronto la guida parte da sola (letto da MwmActivity.onBuiltRoute). */
    @Volatile private var startWhenBuilt = false

    @JvmStatic
    fun consumeStartWhenBuilt(): Boolean {
        val v = startWhenBuilt
        startWhenBuilt = false
        return v
    }

    private val state = mutableStateOf<MapObject?>(null)

    @JvmStatic
    fun attach(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            val place = state.value
            if (place != null && !place.isMyPosition) Head(ThemeUtils.isNightTheme(), place)
        }
    }

    /** La scheda mostra un luogo nuovo (o lo stesso aggiornato, per esempio dopo «Salva»). */
    @JvmStatic
    fun bind(place: MapObject?) {
        state.value = null
        state.value = place
    }

    internal fun route(context: Context, place: MapObject, now: Boolean) {
        val rc = RoutingController.get()
        startWhenBuilt = now
        if (rc.isPlanning) rc.setEndPoint(place)
        else (context.activity() as? MwmActivity)?.startLocationToPoint(place)
    }

    internal fun save(place: MapObject) {
        if (place.isBookmark) Framework.nativeDeleteBookmarkFromMapObject()
        else BookmarkManager.INSTANCE.addNewBookmark(place.lat, place.lon)
    }

    internal fun phone(place: MapObject): String? =
        place.getMetadata(Metadata.MetadataType.FMD_PHONE_NUMBER)?.split(';')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

    internal fun call(context: Context, number: String) =
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }

    /** Nuovo impegno con il luogo già scritto: si apre il calendario del telefono (Eurise Calendario se è il predefinito). */
    internal fun event(context: Context, place: MapObject) {
        val where = listOf(place.title, place.address).filter { !it.isNullOrBlank() }.joinToString(", ")
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, place.title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, where)
        runCatching { context.startActivity(i) }
    }

    internal fun share(context: Context, place: MapObject) {
        val lat = String.format(Locale.ROOT, "%.6f", place.lat)
        val lon = String.format(Locale.ROOT, "%.6f", place.lon)
        val text = listOf(place.title, place.address, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon")
            .filter { !it.isNullOrBlank() }.joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** L'impegno della suite che ha questo luogo (stesso nome o indirizzo), il più vicino nel tempo. */
private fun matching(items: List<Destination>, place: MapObject): Destination? {
    val keys = listOf(place.title, place.address).mapNotNull { it?.trim()?.lowercase(Locale.getDefault())?.takeIf { k -> k.length >= 4 } }
    if (keys.isEmpty()) return null
    return items.filter { d ->
        val p = d.place.lowercase(Locale.getDefault())
        keys.any { k -> p.contains(k) || k.contains(p.takeIf { it.length >= 4 } ?: "\u0000") }
    }.minByOrNull { it.start }
}

@Composable
private fun Head(night: Boolean, place: MapObject) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val destinations by produceState<Destinations?>(null, place) { value = loadDestinations(context.applicationContext) }
    val number = EuriseLuogo.phone(place)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(c.accent)
                    .clickable { EuriseLuogo.route(context, place, now = false) }.semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(rememberVectorPainter(LuogoIcons.go), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(c.onAccent))
                    BasicText(stringResource(R.string.eurise_pl_directions), style = TextStyle(color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(18.dp)).background(c.chip)
                    .clickable { EuriseLuogo.route(context, place, now = true) }.semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(R.string.eurise_pl_go_now), style = TextStyle(color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(c, LuogoIcons.star, stringResource(if (place.isBookmark) R.string.eurise_pl_saved else R.string.eurise_pl_save), Modifier.weight(1f)) {
                EuriseLuogo.save(place)
            }
            Action(c, LuogoIcons.phone, stringResource(R.string.eurise_pl_call), Modifier.weight(1f), enabled = number != null) {
                number?.let { EuriseLuogo.call(context, it) }
            }
            Action(c, LuogoIcons.calendar, stringResource(R.string.eurise_pl_event), Modifier.weight(1f)) { EuriseLuogo.event(context, place) }
            Action(c, LuogoIcons.share, stringResource(R.string.eurise_pl_share), Modifier.weight(1f)) { EuriseLuogo.share(context, place) }
        }
        destinations?.items?.let { matching(it, place) }?.let { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0)), contentAlignment = Alignment.Center) {
                    Image(rememberVectorPainter(LuogoIcons.calendar), null, Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(if (night) Color(0xFFA9C1F2) else Color(0xFF3E5C8A)))
                }
                Column(Modifier.weight(1f)) {
                    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.start))
                    BasicText(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    BasicText(stringResource(R.string.eurise_pl_event_here, time), style = TextStyle(color = c.muted, fontSize = 12.sp))
                }
            }
        }
    }
}

@Composable
private fun Action(c: Colors, icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (enabled) c.ink else c.muted.copy(alpha = 0.5f)
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(c.card)
            .clickable(enabled = enabled, onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(ink))
        BasicText(label, maxLines = 1, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
    }
}

/** Simboli a tratto delle tavole (gli stessi della barra e della ricerca). */
private object LuogoIcons {
    private fun icon(name: String, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val go = icon("go", "M5 19 19 5", "M19 5h-8", "M19 5v8")
    val star = icon("star", "M12 3.8l2.5 5.1 5.6.8-4 3.9.9 5.6-5-2.6-5 2.6.9-5.6-4-3.9 5.6-.8z")
    val phone = icon("phone", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1z")
    val calendar = icon("calendar", "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3.5 10h17M8 3v4M16 3v4")
    val share = icon("share", "M12 15V3M7 8l5-5 5 5", "M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
}
