@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.DateFormat
import java.time.LocalDateTime
import java.util.Date

private val PIN = Color(0xFFA4502E)

/** Pressione lunga sulla graffetta: sondaggio o posizione dal vivo. */
@Composable
internal fun AttachMoreSheet(onPoll: () -> Unit, onLive: () -> Unit, onApprove: () -> Unit = {}, onOuting: (() -> Unit)? = null,
                             more: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>> = emptyList(), onClose: () -> Unit) {
    Sheet(onClose) {
        Card {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); onPoll() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.chart(2f), null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ba_poll_new), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Divider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); onLive() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.place(2f), null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.lo_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Divider()
            // Approvazioni (tavola LavApprova).
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); onApprove() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.check(2.4f), null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ap_title), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            // Organizza un'uscita (tavola AmiUscita), nei gruppi.
            onOuting?.let { go ->
                Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); go() }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(EuriseIcons.calendar(2f), null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.us_new), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            // Le altre schede del lavoro insieme (Affida, Disponibilità, Elenchi…): stessa riga, stessa larghezza.
            more.forEach { (icon, label, go) ->
                Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClose(); go() }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** «Per quanto?»: 15 min, 1 ora, fino alle 23, finché arrivo. */
@Composable
internal fun LiveSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = remember { LocalDateTime.now() }
    val failed = stringResource(R.string.lo_failed)
    val denied = stringResource(R.string.lo_denied)
    Sheet(onClose) {
        Text(stringResource(R.string.lo_how_long), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Posizione.choices(now).forEach { u ->
                val label = when (u) {
                    Posizione.Until.Quarter -> stringResource(R.string.lo_15)
                    Posizione.Until.Hour -> stringResource(R.string.lo_60)
                    Posizione.Until.Evening -> stringResource(R.string.lo_evening)
                    Posizione.Until.Arrival -> stringResource(R.string.lo_arrival)
                }
                Chip(label, false) {
                    actions.askLocation { ok ->
                        if (!ok) { actions.toast(denied); return@askLocation }
                        model.startLive(roomId, u) { actions.toast(failed) }
                        onClose()
                    }
                }
            }
        }
        Text(stringResource(R.string.lo_explain), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * Una posizione nella conversazione: la scheda con il segnaposto e le coordinate (niente mappa scaricata: nessun server
 * saprebbe dove sei), «Posizione dal vivo · fino alle 23:00» e, sotto, Smetti (tua) o «aggiornata alle…» e Apri in Mappe.
 */
@Composable
internal fun LocationBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val info = remember(e.data) { Posizione.info(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(e.id) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val pos = model.lastPosition(roomId, e)
    val live = info != null && info.isLive(now)
    val mineLive = mine && live && model.live?.infoId == e.id
    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 310.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(18.dp)).background(scheme.surface)
                .combinedClickable(onClick = { pos?.let { openMap(actions, it, model.nameOf(e.sender)) } }, onLongClick = onLong),
        ) {
            Box(
                Modifier.fillMaxWidth().height(110.dp).background(Brush.linearGradient(listOf(Color(0xFFE7ECE3), Color(0xFFDCE3EC)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(EuriseIcons.place(2.4f), null, tint = if (live || info == null) PIN else PIN.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                    pos?.let { Text("%.4f, %.4f".format(java.util.Locale.ROOT, it.lat, it.lon), fontSize = 11.sp, color = Color(0xFF56564F)) }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = when {
                    info == null -> stringResource(R.string.lo_static)
                    live -> stringResource(R.string.lo_live_until, fmt.format(Date(info.endsAt)))
                    else -> stringResource(R.string.lo_ended)
                }
                if (!mine) Text(model.nameOf(e.sender), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tintFor(e.sender))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                val sub = when {
                    pos == null && live -> stringResource(R.string.lo_waiting)
                    pos == null -> stringResource(R.string.lo_no_point)
                    else -> stringResource(R.string.lo_updated, fmt.format(Date(pos.ts.takeIf { it > 0 } ?: e.time)))
                }
                Text(stringResource(R.string.lo_private) + " · " + sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (mineLive) Chip(stringResource(R.string.lo_stop), true) { model.stopLive() }
                    if (pos != null) Chip(stringResource(R.string.lo_open_map), false) { openMap(actions, pos, model.nameOf(e.sender)) }
                }
            }
        }
    }
}

/** Apre la posizione nell'app di mappe scelta (Mappe di Eurise, OsmAnd…), con il nome di chi la manda. */
private fun openMap(actions: ChatActions, p: Posizione.Pos, label: String) {
    val q = "%.6f,%.6f".format(java.util.Locale.ROOT, p.lat, p.lon)
    actions.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$q?q=$q(${Uri.encode(label)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
