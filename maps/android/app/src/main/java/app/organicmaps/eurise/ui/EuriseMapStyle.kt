package app.organicmaps.eurise.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colori Perla e Notte delle schermate di Mappe in Compose, e gli impegni con un luogo dall'app Eurise. */
internal class Colors(
    val bg: Color, val card: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val onAccent: Color, val chip: Color, val ok: Color,
)

internal val Perla = Colors(
    Color(0xFFF4F4F1), Color.White, Color(0xFF1E1D1B), Color(0xFF56564F), Color(0x121E1D1B),
    Color(0xFF1F2A44), Color.White, Color(0xFFE9E9E3), Color(0xFF4F6B5A),
)
internal val Notte = Colors(
    Color(0xFF151B28), Color(0xFF222A3A), Color(0xFFE8ECF4), Color(0xFFA9B4C4), Color(0x1FFFFFFF),
    Color(0xFFA9C1F2), Color(0xFF14192A), Color(0xFF2C3547), Color(0xFF9CC3A9),
)

internal class Destination(val title: String, val place: String, val start: Long)
internal class Destinations(val hub: Boolean, val calendar: Boolean, val items: List<Destination>)

internal val HUBS = arrayOf("eu.eurise.hub.debug", "eu.eurise.hub")

/** Gli impegni con un luogo dall'app Eurise (DestinationsContract in core/suite), fuori dal thread principale. */
internal suspend fun loadDestinations(context: Context): Destinations = withContext(Dispatchers.IO) {
    var b: Bundle? = null
    for (h in HUBS) {
        b = runCatching { context.contentResolver.call(Uri.parse("content://$h.destinations"), "list", null, null) }.getOrNull()
        if (b != null) break
    }
    val bundle = b ?: return@withContext Destinations(hub = false, calendar = false, items = emptyList())
    val titles = bundle.getStringArray("title").orEmpty()
    val places = bundle.getStringArray("place").orEmpty()
    val starts = bundle.getLongArray("start") ?: LongArray(0)
    val n = minOf(titles.size, places.size, starts.size)
    Destinations(true, bundle.getBoolean("calendar"), List(n) { Destination(titles[it], places[it], starts[it]) })
}

