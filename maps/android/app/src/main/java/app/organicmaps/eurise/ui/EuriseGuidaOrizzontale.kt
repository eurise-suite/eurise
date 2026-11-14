package app.organicmaps.eurise.ui

import android.app.Activity
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.eurise.EuriseGuideReportActivity
import app.organicmaps.sdk.routing.RoutingInfo
import app.organicmaps.sdk.sound.TtsPlayer
import app.organicmaps.util.ThemeUtils
import java.text.DateFormat
import java.util.Date

/**
 * La guida in orizzontale (tavola MapOrizzontaleGuida, approvata): a sinistra la barra della guida in colonna (Segnala,
 * Sosta, Voce, Insieme, Fine), a destra la colonna della manovra (cartello, scena dell'incrocio, corsie, arrivo), così la
 * strada davanti resta libera. I comandi della barra usano gli stessi pulsanti di CoMaps e della suite (che restano nel
 * layout, nascosti): il comportamento non cambia tra verticale e orizzontale.
 */
object EuriseGuidaOrizzontale {
    private val info = mutableStateOf<RoutingInfo?>(null)
    private val ttsTick = mutableIntStateOf(0)

    @JvmStatic
    fun attachBar(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Bar(ThemeUtils.isNightTheme(), ttsTick.intValue) { compose.context.activity() } }
    }

    /**
     * In verticale (tavola MapGuidaVertB): la scheda satinata in basso con l'arrivo, la strada fatta e gli stessi comandi
     * della barra in orizzontale, in fila.
     */
    @JvmStatic
    fun attachCard(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Card(ThemeUtils.isNightTheme(), info.value, ttsTick.intValue) { compose.context.activity() } }
    }

    /** «Comandi della guida a destra» (Impostazioni › Navigazione), acceso di base: la barra sotto il pollice destro. */
    const val PREF_SIDE = "eurise_guide_right"

    /**
     * In orizzontale (tavola MapGuidaOrizA e la sua versione specchiata): con i comandi a destra la barra va a destra e la
     * colonna della manovra, con velocità e limite, a sinistra; altrimenti come nella tavola, barra a sinistra.
     */
    @JvmStatic
    fun applySide(frame: View?) {
        val bar = frame?.findViewById<View>(R.id.eurise_guida_bar) ?: return
        val right = androidx.preference.PreferenceManager.getDefaultSharedPreferences(frame.context).getBoolean(PREF_SIDE, true)
        (bar.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
            lp.gravity = if (right) android.view.Gravity.END else android.view.Gravity.START
            val m = lp.marginStart.coerceAtLeast(lp.marginEnd)
            lp.marginStart = if (right) 0 else m
            lp.marginEnd = if (right) m else 0
            bar.layoutParams = lp
        }
        val column = frame.findViewById<View>(R.id.eurise_guide_column) ?: return
        (column.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { lp ->
            // I margini (14 dp più fotocamera e barre di sistema) li mette NavigationController su tutti e due i lati.
            if (right) {
                lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            } else {
                lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            }
            column.layoutParams = lp
        }
        // Velocità e limite accanto alla barra, dal suo lato.
        val speed = frame.findViewById<View>(R.id.nav_current_speed)
        val limit = frame.findViewById<View>(R.id.nav_speed_limit)
        (speed?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { lp ->
            val m = lp.marginStart.coerceAtLeast(lp.marginEnd)
            if (right) {
                lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                lp.marginEnd = m; lp.marginStart = 0
            } else {
                lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                lp.marginStart = m; lp.marginEnd = 0
            }
            speed.layoutParams = lp
        }
        (limit?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { lp ->
            if (right) {
                lp.endToStart = R.id.nav_current_speed
                lp.startToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                lp.marginEnd = lp.marginStart.coerceAtLeast(lp.marginEnd); lp.marginStart = 0
            } else {
                lp.startToEnd = R.id.nav_current_speed
                lp.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                lp.marginStart = lp.marginStart.coerceAtLeast(lp.marginEnd); lp.marginEnd = 0
            }
            limit.layoutParams = lp
        }
    }

    @JvmStatic
    fun attachArrival(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Arrival(ThemeUtils.isNightTheme(), info.value) }
    }

    /** A ogni aggiornamento della guida (NavigationController.update). */
    @JvmStatic
    fun update(routing: RoutingInfo?) {
        info.value = routing
    }

    /** «Voce» cambiata altrove (menu della guida): la barra si ridisegna. */
    @JvmStatic
    fun refreshVoice() {
        ttsTick.intValue++
    }
}

/** Tocca un pulsante di CoMaps o della suite che resta nel layout (nascosto in orizzontale). */
private fun Activity.press(id: Int) {
    findViewById<View>(id)?.performClick()
}

@Composable
private fun Bar(night: Boolean, @Suppress("UNUSED_PARAMETER") tick: Int, activity: () -> Activity?) {
    val glass = if (night) Color(0xE61C2333) else Color(0xE0FFFFFF)
    val edge = if (night) Color(0x29FFFFFF) else Color(0x1F1E1D1B)
    val ink = if (night) Color(0xFFE8ECF4) else Color(0xFF1F2A44)
    val muted = if (night) Color(0xFFA9B4C4) else Color(0xFF56564F)
    val stop = if (night) Color(0xFFE8939B) else Color(0xFF9A3B2C)
    val voiceOn = runCatching { TtsPlayer.isEnabled() }.getOrDefault(true)
    Column(
        Modifier.width(68.dp).fillMaxHeight().shadow(14.dp, RoundedCornerShape(32.dp), clip = false)
            .clip(RoundedCornerShape(32.dp)).background(glass).border(1.dp, edge, RoundedCornerShape(32.dp))
            .padding(vertical = 8.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Item(GuideIcons.report, stringResource(R.string.eurise_bar_report_short), ink, ink, bold = true) {
            activity()?.let { EuriseGuideReportActivity.open(it) }
        }
        Item(GuideIcons.stop, stringResource(R.string.eurise_go_stop), ink, muted) { activity()?.press(R.id.btn_search) }
        Item(if (voiceOn) GuideIcons.voice else GuideIcons.mute,
            stringResource(if (voiceOn) R.string.eurise_go_voice else R.string.eurise_go_muted), ink, muted) {
            activity()?.press(R.id.tts_volume)
            EuriseGuidaOrizzontale.refreshVoice()
        }
        Item(GuideIcons.together, stringResource(R.string.eurise_go_together), ink, muted) {
            activity()?.press(R.id.eurise_share_arrival)
        }
        Item(GuideIcons.end, stringResource(R.string.eurise_go_end), stop, stop) { activity()?.press(R.id.stop) }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, tint: Color, text: Color, bold: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.width(58.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(tint))
        BasicText(label, maxLines = 1, style = TextStyle(color = text, fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold))
    }
}

/** La scheda in basso in verticale: arrivo e strada fatta sopra, i cinque comandi in fila sotto, satinata. */
@Composable
private fun Card(night: Boolean, info: RoutingInfo?, @Suppress("UNUSED_PARAMETER") tick: Int, activity: () -> Activity?) {
    val glass = if (night) Color(0xEB1C2333) else Color(0xEBFFFFFF)
    val edge = if (night) Color(0x29FFFFFF) else Color(0x1A1E1D1B)
    val ink = if (night) Color(0xFFE8ECF4) else Color(0xFF1F2A44)
    val muted = if (night) Color(0xFFA9B4C4) else Color(0xFF56564F)
    val stop = if (night) Color(0xFFE8939B) else Color(0xFF9A3B2C)
    val track = if (night) Color(0x33FFFFFF) else Color(0xFFE2E0D8)
    val voiceOn = runCatching { TtsPlayer.isEnabled() }.getOrDefault(true)
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .shadow(14.dp, RoundedCornerShape(28.dp), clip = false).clip(RoundedCornerShape(28.dp)).background(glass)
            .border(1.dp, edge, RoundedCornerShape(28.dp)).padding(top = 4.dp, bottom = 2.dp),
    ) {
        Arrival(night, info)
        val done = ((info?.completionPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(done).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Item(GuideIcons.report, stringResource(R.string.eurise_bar_report_short), ink, ink, bold = true) {
                activity()?.let { EuriseGuideReportActivity.open(it) }
            }
            Item(GuideIcons.stop, stringResource(R.string.eurise_go_stop), ink, muted) { activity()?.press(R.id.btn_search) }
            Item(if (voiceOn) GuideIcons.voice else GuideIcons.mute,
                stringResource(if (voiceOn) R.string.eurise_go_voice else R.string.eurise_go_muted), ink, muted) {
                activity()?.press(R.id.tts_volume)
                EuriseGuidaOrizzontale.refreshVoice()
            }
            Item(GuideIcons.together, stringResource(R.string.eurise_go_together), ink, muted) {
                activity()?.press(R.id.eurise_share_arrival)
            }
            Item(GuideIcons.end, stringResource(R.string.eurise_go_end), stop, stop) { activity()?.press(R.id.stop) }
        }
    }
}

/** In fondo alla colonna della manovra: l'ora d'arrivo grande, poi il tempo e la strada che restano. */
@Composable
private fun Arrival(night: Boolean, info: RoutingInfo?) {
    if (info == null) return
    val ink = if (night) Color(0xFFECE9E3) else Color(0xFF1E1D1B)
    val muted = if (night) Color(0xFFA4A9B2) else Color(0xFF3A3A35)
    val seconds = info.totalTimeInSeconds.coerceAtLeast(0)
    val eta = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + seconds * 1000L))
    val minutes = (seconds + 30) / 60
    val time = if (minutes < 60) "$minutes min" else if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
    val context = androidx.compose.ui.platform.LocalContext.current
    val distance = runCatching { info.distToTarget.toString(context) }.getOrDefault("")
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(eta, style = TextStyle(color = ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
        BasicText(listOf(time, distance).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1,
            style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** I simboli della tavola, a tratto come gli altri della suite. */
private object GuideIcons {
    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    val report = icon("report", 2.4f, "M12 5v14M5 12h14")
    val stop = icon("stop", 2.1f, "M11 4.5a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13z", "M16 16l4.5 4.5")
    val voice = icon("voice", 2.1f, "M4 9v6h4l5 4V5L8 9z", "M16.5 9a4 4 0 0 1 0 6")
    val mute = icon("mute", 2.1f, "M4 9v6h4l5 4V5L8 9z", "M16 9.5l5 5M21 9.5l-5 5")
    val together = icon("together", 2.1f, "M4 18L10 6l4 8 2-3 4 7z")
    val end = icon("end", 2.2f, "M6 6l12 12M18 6L6 18")
}
