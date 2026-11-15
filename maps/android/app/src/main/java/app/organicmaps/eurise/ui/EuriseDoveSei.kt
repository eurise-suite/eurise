package app.organicmaps.eurise.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import app.organicmaps.R
import app.organicmaps.eurise.EuriseRouteWeather
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.bookmarks.data.BookmarkManager
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.routing.RoutingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Passo f) del piano di Mappe (tavola MapEsplora, approvata): «Dove sei» sopra la barra mentre si esplora la mappa.
 * Comune con la sigla della provincia («Monza (MB)»), via, regione e paese con la bandiera; a destra i gradi, il cielo
 * e il tramonto (o l'alba). Tocco: si apre con «Salva il luogo» e «Manda la posizione». Il punto è il centro della mappa
 * (quello che si guarda; seguendo la posizione è dove si è). Tutto sul telefono: comune e via dalle mappe scaricate
 * (CityFinder del motore), sigle dalla tabella ISTAT nell'APK, tramonto calcolato; il meteo è quello del centro privacy
 * (spento di base) e, se spento, a destra resta solo il tramonto. Si spegne in Livelli.
 */
object EuriseDoveSei {
    const val PREF = "eurise_where_card"

    internal fun enabled(c: Context) = PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF, true)
    internal fun setEnabled(c: Context, on: Boolean) =
        PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(PREF, on).apply()

    internal class Place(
        val lat: Double, val lon: Double, val city: String, val sigla: String?, val street: String?,
        val region: String?, val iso: String?,
    )

    private class Comune(val sigla: String, val region: String)

    @Volatile private var comuni: Map<String, List<Comune>>? = null

    /** La tabella dei comuni (asset di 200 KB, compresso nell'APK): si legge una volta, fuori dal thread principale. */
    internal suspend fun loadComuni(c: Context) {
        if (comuni != null) return
        comuni = withContext(Dispatchers.IO) {
            val out = HashMap<String, MutableList<Comune>>(9000)
            runCatching {
                c.assets.open("eurise_comuni.tsv").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("#")) continue
                        val f = line.split('\t')
                        if (f.size < 3) continue
                        val comune = Comune(f[1], f[2].substringBefore('/'))
                        // «Bolzano/Bozen»: tutte e due le forme.
                        for (n in f[0].split('/')) out.getOrPut(key(n)) { ArrayList(1) }.add(comune)
                    }
                }
            }
            out
        }
    }

    private fun key(name: String) = name.trim().lowercase(Locale.ITALIAN).replace('’', '\'')

    /** Dove cade il punto (thread principale: il motore si chiama da qui). Null se lì non c'è una mappa scaricata. */
    internal fun at(lat: Double, lon: Double): Place? {
        val city = runCatching { Framework.nativeEuriseCityAt(lat, lon) }.getOrNull().orEmpty().trim()
        if (city.isEmpty()) return null
        val address = runCatching { Framework.nativeGetAddress(lat, lon) }.getOrNull().orEmpty()
        // «Via Italia, 12» → «Via Italia»: qui conta la via, non il numero.
        val street = address.replace(Regex(",\\s*\\d[^,]*$"), "").trim().takeIf { it.isNotEmpty() && it != city }
        var region: String? = null
        var iso: String? = null
        val id = runCatching { MapManager.nativeFindCountry(lat, lon) }.getOrNull()
        if (!id.isNullOrEmpty()) {
            val item = CountryItem(id)
            runCatching { MapManager.nativeGetAttributes(item) }
            iso = isoOf(item.topmostParentId ?: id)
            if (!item.directParentId.isNullOrEmpty() && item.directParentId != item.topmostParentId)
                region = item.directParentName
        }
        var sigla: String? = null
        if (iso == "IT") {
            // Comuni con lo stesso nome (sei in Italia): si sceglie quello della regione della mappa; se non si sa, niente sigla.
            val parts = city.split(" - ", "/").map { it.trim() }.filter { it.isNotEmpty() }
            val found = parts.firstNotNullOfOrNull { comuni?.get(key(it)) }
            val c = when {
                found == null -> null
                found.size == 1 -> found[0]
                else -> found.firstOrNull { region != null && it.region.equals(region, ignoreCase = true) }
            }
            if (c != null) {
                sigla = c.sigla
                region = c.region
            }
        }
        return Place(lat, lon, city, sigla, street, region, iso)
    }

    /** Il codice ISO del paese dal nome inglese di CoMaps («Italy» → «IT»). */
    private fun isoOf(country: String): String? {
        val name = country.substringBefore('_').replace('-', ' ')
        EXTRA[name]?.let { return it }
        return Locale.getISOCountries().firstOrNull { Locale("", it).getDisplayCountry(Locale.ENGLISH).equals(name, ignoreCase = true) }
    }

    private val EXTRA = mapOf(
        "UK" to "GB", "United Kingdom" to "GB", "Czech" to "CZ", "Czech Republic" to "CZ", "USA" to "US",
        "United States" to "US", "Bosnia and Herzegovina" to "BA", "Vatican City" to "VA", "Vatican" to "VA",
        "Macedonia" to "MK", "Kosovo" to "XK", "Ivory Coast" to "CI", "Congo Kinshasa" to "CD", "Congo Brazzaville" to "CG",
    )

    /** La bandiera come emoji dai due caratteri del codice. */
    internal fun flag(iso: String): String =
        if (iso.length != 2 || iso == "XK") "" else String(Character.toChars(0x1F1E6 + iso[0].code - 'A'.code)) +
            String(Character.toChars(0x1F1E6 + iso[1].code - 'A'.code))

    internal fun share(context: Context, p: Place) {
        val lat = String.format(Locale.ROOT, "%.6f", p.lat)
        val lon = String.format(Locale.ROOT, "%.6f", p.lon)
        val text = listOfNotNull(p.street, title(p), "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon")
            .joinToString("\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, null)) }
    }

    internal fun title(p: Place) = if (p.sigla != null) "${p.city} (${p.sigla})" else p.city
}

/** Alba e tramonto del giorno (equazione del sorgere, circa un minuto di errore): millisecondi, null nelle notti o nei giorni polari. */
internal object SunTimes {
    fun of(lat: Double, lon: Double, now: Long): Pair<Long, Long>? {
        val jDate = now / 86_400_000.0 + 2440587.5
        val n = ceil(jDate - 2451545.0 + 0.0008)
        val jStar = n - lon / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360.0)
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372) % 360.0)
        val transit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val decl = asin(sin(lambda) * sin(Math.toRadians(23.44)))
        val phi = Math.toRadians(lat)
        val cosW = (sin(Math.toRadians(-0.833)) - sin(phi) * sin(decl)) / (cos(phi) * cos(decl))
        if (cosW < -1 || cosW > 1) return null
        val w = Math.toDegrees(acos(cosW))
        fun ms(j: Double) = ((j - 2440587.5) * 86_400_000.0).toLong()
        return ms(transit - w / 360.0) to ms(transit + w / 360.0)
    }
}

@Composable
internal fun DoveSei(night: Boolean, modifier: Modifier = Modifier) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var place by remember { mutableStateOf<EuriseDoveSei.Place?>(null) }
    var weatherTick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(EuriseDoveSei.enabled(context)) }
    var hidden by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        EuriseDoveSei.loadComuni(context.applicationContext)
        var lastLat = Double.NaN
        var lastLon = Double.NaN
        var prev: DoubleArray? = null
        while (true) {
            // Una lettura al secondo del centro della mappa (niente disco né rete); il comune si cerca solo quando la mappa
            // si è fermata e il centro si è spostato di più di 30 m. Fermo con l'app in secondo piano.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                on = EuriseDoveSei.enabled(context)
                val rc = RoutingController.get()
                hidden = rc.isPlanning || rc.isNavigating
                val center = runCatching { Framework.nativeGetScreenRectCenter() }.getOrNull()
                if (on && !hidden && center != null && center.size >= 2) {
                    val still = prev?.let { dist(it[0], it[1], center[0], center[1]) < 5f } ?: false
                    prev = center
                    if (still && (lastLat.isNaN() || dist(lastLat, lastLon, center[0], center[1]) > 30f)) {
                        lastLat = center[0]
                        lastLon = center[1]
                        place = EuriseDoveSei.at(center[0], center[1])
                        if (place != null)
                            EuriseRouteWeather.here(context.applicationContext, center[0], center[1]) { weatherTick++ }
                    }
                }
            }
            delay(1000)
        }
    }

    val p = place
    if (!on || hidden || p == null) return
    val weather = remember(p, weatherTick) { EuriseRouteWeather.hereParts(context) }
    val sun = remember(p.lat.toInt(), p.lon.toInt(), System.currentTimeMillis() / 600_000) {
        SunTimes.of(p.lat, p.lon, System.currentTimeMillis())
    }
    val now = System.currentTimeMillis()
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val sunLine = sun?.let { (rise, set) ->
        if (now in rise until set) stringResource(R.string.eurise_ds_sunset, fmt.format(Date(set)))
        else stringResource(R.string.eurise_ds_sunrise, fmt.format(Date(if (now < rise) rise else rise + 86_400_000L)))
    }
    val glass = if (night) Color(0xF01C2333) else Color(0xF2FFFFFF)
    val edge = if (night) Color(0x29FFFFFF) else Color(0x1F1E1D1B)

    Column(
        modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(22.dp), clip = false).clip(RoundedCornerShape(22.dp))
            .background(glass).border(1.dp, edge, RoundedCornerShape(22.dp))
            .clickable { open = !open }.semantics { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            p.iso?.let { EuriseDoveSei.flag(it) }?.takeIf { it.isNotEmpty() }?.let {
                BasicText(it, style = TextStyle(fontSize = 22.sp))
            }
            Column(Modifier.weight(1f)) {
                BasicText(EuriseDoveSei.title(p), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
                BasicText(listOfNotNull(p.street, p.region, p.iso).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = c.muted, fontSize = 12.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                val temp = (weather?.get(2) as? Float)?.takeIf { !it.isNaN() }
                if (temp != null)
                    BasicText("${weather!![0]} ${Math.round(temp)}°", style = TextStyle(color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
                val right = listOfNotNull(weather?.get(1) as? String, sunLine).joinToString(" · ")
                if (right.isNotEmpty())
                    BasicText(right, maxLines = 1, style = TextStyle(color = c.muted, fontSize = 11.5.sp, textAlign = TextAlign.End))
            }
        }
        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Soft(c, night, stringResource(R.string.eurise_ds_save), Modifier.weight(1f)) {
                    BookmarkManager.INSTANCE.addNewBookmark(p.lat, p.lon)
                    open = false
                }
                Soft(c, night, stringResource(R.string.eurise_ds_send), Modifier.weight(1f)) { EuriseDoveSei.share(context, p) }
            }
            BasicText(stringResource(R.string.eurise_ds_note), style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 16.sp))
        }
    }
}

@Composable
private fun Soft(c: Colors, night: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(15.dp)).background(if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0))
            .clickable(onClick = onClick).semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BasicText(label, style = TextStyle(color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, r)
    return abs(r[0])
}
