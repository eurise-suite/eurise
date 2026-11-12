package app.organicmaps.eurise.ui

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.eurise.EuriseReportActivity
import app.organicmaps.maplayer.MapButtonsController.MapButtonClickListener
import app.organicmaps.maplayer.MapButtonsController.MapButtons
import app.organicmaps.sdk.Framework
import app.organicmaps.util.ThemeUtils

/**
 * La schermata della mappa di Eurise Mappe, passo a) del piano (tavole della suite): in basso la barra satinata con
 * Cerca, Livelli, Posizione, Salvati e Menu, e a parte il «+» satinato per segnalare su OpenStreetMap. Prende il posto
 * dei pulsanti di CoMaps in basso; i comandi passano da [MapButtonClickListener] (MwmActivity), come prima: il motore
 * non cambia. Compose vive solo in questo [ComposeView].
 */
object EuriseMapBar {

    @JvmStatic
    fun attach(view: View?, listener: MapButtonClickListener) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent {
            Bar(
                night = ThemeUtils.isNightTheme(),
                onSearch = { listener.onMapButtonClick(MapButtons.search) },
                onLayers = { listener.onMapButtonClick(MapButtons.toggleMapLayer) },
                onPosition = { listener.onMapButtonClick(MapButtons.myPosition) },
                onSaved = { listener.onMapButtonClick(MapButtons.bookmarks) },
                onMenu = { listener.onMapButtonClick(MapButtons.menu) },
                onReport = { report(compose) },
            )
        }
    }

    /** «Segnala qui»: il punto al centro della mappa (dove si sta guardando). */
    private fun report(view: View) {
        val activity = view.context as? Activity ?: return
        val center = runCatching { Framework.nativeGetScreenRectCenter() }.getOrNull() ?: return
        if (center.size < 2) return
        EuriseReportActivity.open(activity, center[0], center[1], null)
    }
}

private class Palette(val glass: Color, val edge: Color, val ink: Color, val muted: Color, val accent: Color, val onAccent: Color)

private val Perla = Palette(Color(0xE6FFFFFF), Color(0x1F1E1D1B), Color(0xFF1F2A44), Color(0xFF56564F), Color(0xFF1F2A44), Color.White)
private val Notte = Palette(Color(0xE61C2333), Color(0x29FFFFFF), Color(0xFFE8ECF4), Color(0xFFA9B4C4), Color(0xFFA9C1F2), Color(0xFF14192A))

@Composable
private fun Bar(
    night: Boolean,
    onSearch: () -> Unit,
    onLayers: () -> Unit,
    onPosition: () -> Unit,
    onSaved: () -> Unit,
    onMenu: () -> Unit,
    onReport: () -> Unit,
) {
    val p = if (night) Notte else Perla
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f).widthIn(max = 420.dp).height(64.dp)
                .shadow(14.dp, RoundedCornerShape(32.dp), clip = false)
                .clip(RoundedCornerShape(32.dp)).background(p.glass).border(1.dp, p.edge, RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Item(Icons.search, R.string.eurise_bar_search, p, active = true, onSearch)
            Item(Icons.layers, R.string.eurise_bar_layers, p, active = false, onLayers)
            Item(Icons.position, R.string.eurise_bar_position, p, active = false, onPosition)
            Item(Icons.star, R.string.eurise_bar_saved, p, active = false, onSaved)
            Item(Icons.menu, R.string.eurise_bar_menu, p, active = false, onMenu)
        }
        val plus = androidx.compose.ui.res.stringResource(R.string.eurise_bar_report)
        Box(
            Modifier.size(64.dp).shadow(14.dp, CircleShape, clip = false).clip(CircleShape).background(p.glass)
                .border(1.dp, p.edge, CircleShape).clickable(onClick = onReport)
                .semantics { contentDescription = plus; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { Glyph(Icons.plus, p.ink, 26) }
    }
}

/**
 * Una voce della barra: simbolo ed etichetta; la voce principale (Cerca) ha tratto più marcato ed etichetta in
 * grassetto, senza alone, come nelle altre app della suite.
 */
@Composable
private fun Item(icon: ImageVector, label: Int, p: Palette, active: Boolean, onClick: () -> Unit) {
    val text = androidx.compose.ui.res.stringResource(label)
    Column(
        Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Glyph(icon, if (active) p.accent else p.ink, 22)
        BasicText(
            text,
            style = TextStyle(
                color = if (active) p.accent else p.muted, fontSize = 11.sp,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color, size: Int) {
    Image(rememberVectorPainter(icon), null, Modifier.size(size.dp), colorFilter = ColorFilter.tint(tint))
}

/** Simboli a tratto della suite (gli stessi disegni di EuriseIcons), qui senza dipendere dal modulo della suite. */
private object Icons {
    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val search = icon("search", 2.4f, "M11 4.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z", "M16 16l4.5 4.5")
    val layers = icon("layers", 2.1f, "M12 3l9 5-9 5-9-5z", "M3 13l9 5 9-5")
    val position = icon("position", 2.1f, "M12 5a7 7 0 1 1 0 14 7 7 0 0 1 0-14z", "M12 2v3M12 19v3M2 12h3M19 12h3", "M12 10a2 2 0 1 1 0 4 2 2 0 0 1 0-4z")
    val star = icon("star", 2.1f, "M12 3.8l2.5 5.1 5.6.8-4 3.9.9 5.6-5-2.6-5 2.6.9-5.6-4-3.9 5.6-.8z")
    val menu = icon("menu", 2.3f, "M5 7h14M5 12h14M5 17h14")
    val plus = icon("plus", 2.4f, "M12 5v14M5 12h14")
}
