package app.organicmaps.eurise.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.downloader.DownloaderActivity
import app.organicmaps.eurise.EuriseArrival
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.search.DisplayedCategories
import app.organicmaps.sdk.search.SearchRecents
import app.organicmaps.sdk.util.Config
import app.organicmaps.sdk.util.Language
import app.organicmaps.util.ThemeUtils
import java.util.Locale

/**
 * Passo b) del piano di Mappe (tavola MapCerca): ciò che si vede nella ricerca prima di scrivere. Prende il posto delle
 * schede di CoMaps (cronologia e categorie), che restano nel layout ma nascoste: «Dove devi andare» (impegni con un luogo,
 * dall'app Eurise), «Cerca vicino» (le categorie del motore), le ricerche recenti, l'indirizzo di un contatto e le mappe
 * sul telefono. Tutto lavora sulle mappe scaricate; dall'app esce solo la richiesta all'app Eurise, sul telefono.
 */
object EuriseSearchHome {

    /** Cosa fa la ricerca di CoMaps quando si tocca qualcosa qui (SearchFragment, che ha `setQuery`). */
    fun interface Host {
        fun query(text: String, category: Boolean)
    }

    @JvmStatic
    fun attach(view: View?, host: Host, contactPicker: Runnable?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Home(ThemeUtils.isNightTheme(), host, contactPicker) }
    }
}

/** Le mappe scaricate (il motore le elenca con root nullo); chiamata dal thread principale come fa CoMaps. */
private fun downloadedMaps(): List<CountryItem> {
    val out = ArrayList<CountryItem>()
    runCatching { MapManager.nativeListItems(null, 0.0, 0.0, false, false, out) }
    return out.filter { it.present || it.status == CountryItem.STATUS_DONE || it.status == CountryItem.STATUS_UPDATABLE }
        .sortedBy { it.name }
}

/** Le categorie da offrire subito, nell'ordine pensato per chi è in giro; solo quelle che il motore conosce. */
private val NEAR_KEYS = listOf(
    "category_pharmacy", "category_fuel", "category_parking", "category_eat", "category_food", "category_atm",
    "category_hospital", "category_toilet", "category_hotel",
)

private fun nearCategories(context: Context): List<Pair<String, Int>> {
    val known = runCatching { DisplayedCategories.nativeGetKeys().toSet() }.getOrDefault(emptySet())
    return NEAR_KEYS.filter { it in known }.mapNotNull { key ->
        @Suppress("DiscouragedApi")
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        if (id == 0) null else key to id
    }
}

/** Il testo con cui CoMaps cerca una categoria: nella lingua del telefono se il motore la conosce, se no in inglese. */
private fun categoryQuery(context: Context, res: Int): String {
    val supported = runCatching { DisplayedCategories.nativeIsLangSupported(Language.getDefaultLocale()) }.getOrDefault(true)
    if (supported) return context.getString(res) + " "
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale("en")) }
    return context.createConfigurationContext(config).getString(res) + " "
}

@Composable
private fun Home(night: Boolean, host: EuriseSearchHome.Host, contactPicker: Runnable?) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val destinations by produceState<Destinations?>(null) { value = loadDestinations(context.applicationContext) }
    val maps = remember { downloadedMaps() }
    val near = remember { nearCategories(context) }
    val recents = remember {
        if (!Config.isSearchHistoryEnabled()) emptyList()
        else runCatching { SearchRecents.refresh(); List(minOf(SearchRecents.getSize(), 5)) { SearchRecents.get(it) } }
            .getOrDefault(emptyList())
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
    ) {
        Heading(stringResource(R.string.eurise_go_title), c)
        val d = destinations
        when {
            d == null -> Note(stringResource(R.string.eurise_sh_loading), c)
            !d.hub -> Note(stringResource(R.string.eurise_go_no_hub), c)
            !d.calendar -> Note(stringResource(R.string.eurise_go_no_calendar), c)
            d.items.isEmpty() && contactPicker == null -> Note(stringResource(R.string.eurise_go_empty), c)
            else -> Card(c) {
                d.items.forEachIndexed { i, item ->
                    if (i > 0) Divider(c)
                    val whenText = DateUtils.formatDateTime(
                        context, item.start,
                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_WEEKDAY,
                    )
                    Row2(
                        c, Badge.Calendar, "${item.title} · ${item.place}",
                        stringResource(R.string.eurise_go_from_calendar, whenText),
                    ) {
                        EuriseArrival.remember(context, item.title, item.start)
                        host.query(item.place, false)
                    }
                }
                if (d.items.isEmpty()) Note(stringResource(R.string.eurise_go_empty), c)
                if (contactPicker != null) {
                    if (d.items.isNotEmpty()) Divider(c)
                    Row2(c, Badge.Person, stringResource(R.string.eurise_sh_contact), stringResource(R.string.eurise_sh_contact_sub)) {
                        contactPicker.run()
                    }
                }
            }
        }

        if (near.isNotEmpty()) {
            Heading(stringResource(R.string.eurise_sh_near), c)
            // I benzinai col prezzo del Ministero (tavola MapAttivita): più economici, vicini o sul percorso.
            Card(c) {
                Row2(c, Badge.Fuel, stringResource(R.string.eurise_sh_fuel), stringResource(R.string.eurise_sh_fuel_sub)) {
                    EuriseFuelActivity.open(context)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                near.forEach { (_, res) ->
                    val label = stringResource(res)
                    Box(
                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(c.card)
                            .clickable { host.query(categoryQuery(context, res), true) }
                            .semantics { role = Role.Button }.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(label, style = TextStyle(color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)) }
                }
            }
        }

        if (recents.isNotEmpty()) {
            Heading(stringResource(R.string.eurise_sh_recent), c)
            Card(c) {
                recents.forEachIndexed { i, q ->
                    if (i > 0) Divider(c)
                    Row2(c, Badge.Clock, q.trim(), null) { host.query(q, false) }
                }
            }
        }

        Heading(stringResource(R.string.eurise_sh_maps), c)
        Card(c) {
            if (maps.isEmpty()) Note(stringResource(R.string.eurise_sh_maps_none), c)
            maps.forEachIndexed { i, m ->
                if (i > 0) Divider(c)
                MapRow(m, c, context)
            }
            Divider(c)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { openDownloader(context) }
                    .semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    stringResource(R.string.eurise_sh_maps_manage),
                    style = TextStyle(color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        BasicText(
            stringResource(R.string.eurise_go_privacy),
            Modifier.padding(horizontal = 4.dp),
            style = TextStyle(color = c.muted, fontSize = 12.sp, lineHeight = 18.sp),
        )
    }
}

@Composable
private fun MapRow(m: CountryItem, c: Colors, context: Context) {
    val size = Formatter.formatShortFileSize(context, if (m.totalSize > 0) m.totalSize else m.size)
    val updatable = m.status == CountryItem.STATUS_UPDATABLE
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(m.name ?: m.id, style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            val parent = m.topmostParentName?.takeIf { it.isNotBlank() && it != m.name }
            BasicText(
                if (parent != null) "$parent · $size" else size,
                style = TextStyle(color = c.muted, fontSize = 12.sp),
            )
        }
        if (updatable) {
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.accent)
                    .clickable { openDownloader(context) }.semantics { role = Role.Button }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    stringResource(R.string.eurise_sh_map_update),
                    style = TextStyle(color = c.onAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                )
            }
        } else {
            BasicText(
                stringResource(R.string.eurise_sh_map_ready),
                style = TextStyle(color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun openDownloader(context: Context) {
    val i = Intent(context, DownloaderActivity::class.java).putExtra(DownloaderActivity.EXTRA_OPEN_DOWNLOADED, true)
    if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(i)
}

private enum class Badge { Calendar, Person, Clock, Fuel }

@Composable
private fun Row2(c: Colors, badge: Badge, title: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (tile, ink) = when (badge) {
            Badge.Calendar -> (if (c === Notte) Color(0xFF2E3A55) else Color(0xFFDDE3F0)) to (if (c === Notte) Color(0xFFA9C1F2) else Color(0xFF3E5C8A))
            Badge.Person -> (if (c === Notte) Color(0xFF2B3B33) else Color(0xFFDCE7DF)) to (if (c === Notte) Color(0xFF9CC3A9) else Color(0xFF4F6B5A))
            Badge.Clock -> c.chip to c.muted
            Badge.Fuel -> (if (c === Notte) Color(0xFF2B3B33) else Color(0xFFDCE7DF)) to (if (c === Notte) Color(0xFF9CC3A9) else Color(0xFF2F6B45))
        }
        Box(
            Modifier.size(34.dp).clip(if (badge == Badge.Person) CircleShape else RoundedCornerShape(11.dp)).background(tile),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (badge) {
                Badge.Calendar -> SearchIcons.calendar
                Badge.Person -> SearchIcons.person
                Badge.Clock -> SearchIcons.clock
                Badge.Fuel -> SearchIcons.fuel
            }
            androidx.compose.foundation.Image(
                androidx.compose.ui.graphics.vector.rememberVectorPainter(icon), null, Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink),
            )
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            if (sub != null) BasicText(sub, style = TextStyle(color = c.muted, fontSize = 12.sp))
        }
    }
}

@Composable
private fun Heading(text: String, c: Colors) {
    BasicText(
        text.uppercase(Locale.getDefault()),
        Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp).semantics { heading() },
        style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
    )
}

@Composable
private fun Note(text: String, c: Colors) {
    BasicText(
        text, Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        style = TextStyle(color = c.muted, fontSize = 12.5.sp, lineHeight = 17.sp),
    )
}

@Composable
private fun Card(c: Colors, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Divider(c: Colors) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.line))
}

/** Simboli a tratto della suite (stessi disegni delle tavole), come in [EuriseMapBar]. */
private object SearchIcons {
    private fun icon(name: String, vararg paths: String) =
        androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = androidx.compose.ui.graphics.vector.addPathNodes(d),
                    stroke = androidx.compose.ui.graphics.SolidColor(Color.Black), strokeLineWidth = 2.3f,
                    strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                )
            }
        }.build()

    /** La pompa del carburante (benzinai col prezzo). */
    val fuel = icon("fuel", "M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15", "M4 20h11", "M7 8h5", "M14 10h2a1 1 0 0 1 1 1v5a1.5 1.5 0 0 0 3 0V9l-2.5-2.5")

    val calendar = icon("calendar", "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3.5 10h17M8 3v4M16 3v4")
    val person = icon("person", "M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8z", "M4.5 20a7.5 7.5 0 0 1 15 0")
    val clock = icon("clock", "M12 4a8 8 0 1 1 0 16 8 8 0 0 1 0-16z", "M12 8v4l3 2")
}
