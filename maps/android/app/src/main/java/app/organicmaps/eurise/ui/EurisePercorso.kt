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

    @JvmStatic
    fun attach(view: View?, start: View?) {
        val compose = view as? ComposeView ?: return
        startButton = start
        attached = true
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
    }

    /**
     * «Avvia la navigazione»: lo stesso percorso di «Inizia» di CoMaps (MwmActivity.onRoutingStart: punto di partenza,
     * avviso la prima volta, voce), chiamato direttamente. Il clic sul pulsante nascosto dipendeva dallo stato di CoMaps e
     * a volte non partiva (prova del 13 novembre); resta solo come ripiego.
     */
    internal fun start(context: Context) {
        val listener = context.activity() as? app.organicmaps.routing.RoutingBottomMenuListener
        if (listener != null && RoutingController.get().isPlanning) listener.onRoutingStart()
        else startButton?.performClick()
    }
}

@Composable
private fun Panel(night: Boolean, @Suppress("UNUSED_PARAMETER") version: Int) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    val rc = RoutingController.get()
    val end: MapObject? = rc.endPoint
    val info = rc.cachedRoutingInfo
    val built = rc.isBuilt && info != null
    val router = rc.lastRouterType
    val destinations by produceState<Destinations?>(null, end) { value = loadDestinations(context.applicationContext) }
    if (router == Router.Ruler) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Auto · Bici · A piedi (i mezzi pubblici restano nella barra in alto di CoMaps).
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val minutes = if (built) info!!.totalTimeInSeconds else -1
            Mode(c, R.string.eurise_pc_car, Router.Vehicle, router, minutes, Modifier.weight(1f))
            Mode(c, R.string.eurise_pc_bike, Router.Bicycle, router, minutes, Modifier.weight(1f))
            Mode(c, R.string.eurise_pc_walk, Router.Pedestrian, router, minutes, Modifier.weight(1f))
        }

        // L'impegno con questo luogo: quando partire.
        val d = if (end != null) destinations?.items?.let { matching(it, end) } else null
        if (d != null) {
            val seconds = if (built) info!!.totalTimeInSeconds else 0
            val leave = d.start - seconds * 1000L - EuriseMargin
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            val now = System.currentTimeMillis()
            val line = when {
                !built -> stringResource(R.string.eurise_pc_event_at, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.start)))
                leave > now -> stringResource(R.string.eurise_pc_leave_by, fmt.format(Date(leave)))
                else -> stringResource(R.string.eurise_pc_leave_now, fmt.format(Date(now + seconds * 1000L)))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (night) Color(0xFF2E3A55) else Color(0xFFDDE3F0)))
                Column(Modifier.weight(1f)) {
                    BasicText(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold))
                    BasicText(line, style = TextStyle(color = c.muted, fontSize = 12.sp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Soft(c, night, stringResource(R.string.eurise_pc_send_arrival), Modifier.weight(1f), enabled = built) {
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
                .clickable(enabled = built) { EurisePercorso.start(context) }.semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(R.string.eurise_pc_start), style = TextStyle(color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        BasicText(stringResource(R.string.eurise_pc_offline), style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}

/** Margine prima dell'impegno: si arriva qualche minuto prima. */
private const val EuriseMargin = 10 * 60_000L

@Composable
private fun Mode(c: Colors, label: Int, mode: Router, current: Router, seconds: Int, modifier: Modifier) {
    val on = mode == current
    val text = stringResource(label).let { name ->
        if (on && seconds > 0) stringResource(R.string.eurise_pc_mode_time, name, duration(seconds)) else name
    }
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else c.card)
            .clickable(enabled = !on) { RoutingController.get().setRouterType(mode); EurisePercorso.update() }
            .semantics { role = Role.Tab; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = if (on) c.onAccent else c.ink, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold))
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

/** «18 min», «1 h 5 min». */
private fun duration(seconds: Int): String {
    val m = (seconds + 30) / 60
    return if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}
