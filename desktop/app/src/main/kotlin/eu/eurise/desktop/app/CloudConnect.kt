package eu.eurise.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.Instant

/**
 * «Collega il cloud» per Calendario e Attività: Murena in un tocco, o l'indirizzo del proprio Nextcloud; poi il browser.
 * Prima di collegare si dice cosa succede: i dati vanno solo al cloud scelto, la password per le app nel portachiavi.
 */
@Composable
fun CloudConnect(agenda: Agenda, what: String) {
    val c = LocalEurise.current
    var server by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(18.dp)).background(c.card).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Collega $what", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Gli stessi calendari ed elenchi del telefono, dal tuo cloud (CalDAV). Si apre il browser: entri come sempre e " +
                "approvi. Il computer riceve una password per le app, che sta nel portachiavi del sistema e che puoi revocare " +
                "dal cloud. I dati vanno solo al cloud che scegli.",
            color = c.soft, fontSize = 13.5.sp, lineHeight = 20.sp,
        )
        val waiting = agenda.connecting
        if (waiting != null) {
            Text("Approva l'accesso nel browser… ($waiting)", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            PillButton("Annulla", c.card, c.text, border = true) { agenda.cancelConnect() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Collega Murena", c.accent, c.onAccent) { agenda.connect("murena.io") }
                Text("privacy alta · server in Europa", color = c.soft, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(c.background).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (server.isEmpty()) Text("Il tuo Nextcloud: cloud.esempio.it", color = c.soft, fontSize = 13.5.sp)
                    BasicTextField(server, { server = it.trim() }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 13.5.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth())
                }
                PillButton("Collega", c.card, c.text, border = true, enabled = server.contains('.')) { agenda.connect(server) }
            }
        }
        agenda.error?.let { Text(it, color = c.borgogna, fontSize = 13.sp) }
    }
}

/** La riga dello stato sotto i calendari: con chi, quando, e se la password è solo in memoria. */
@Composable
fun SyncLine(agenda: Agenda) {
    val c = LocalEurise.current
    val acc = agenda.account ?: return
    val text = when {
        agenda.syncing -> "Sincronizzo con ${acc.label}…"
        agenda.error != null -> agenda.error!!
        agenda.lastSync != null -> "Sincronizzato con ${acc.label} (CalDAV) ${agoText(agenda.lastSync!!)}. Gli stessi del telefono."
        else -> "Collegato a ${acc.label}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = if (agenda.error != null) c.borgogna else c.soft, fontSize = 11.5.sp, lineHeight = 16.sp)
        if (!agenda.inKeyring) Text("Portachiavi non disponibile: il collegamento vale fino alla chiusura.", color = c.ottone, fontSize = 11.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sincronizza ora", color = c.blu, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { Thread { agenda.sync() }.start() })
            Text("Scollega", color = c.soft, fontSize = 11.5.sp, modifier = Modifier.clickable { agenda.disconnect() })
        }
    }
}

fun agoText(t: Instant): String {
    val m = Duration.between(t, Instant.now()).toMinutes()
    return when {
        m < 1 -> "adesso"
        m == 1L -> "1 minuto fa"
        m < 60 -> "$m minuti fa"
        m < 120 -> "un'ora fa"
        else -> "${m / 60} ore fa"
    }
}

@Composable
fun PillButton(label: String, bg: Color, fg: Color, border: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalEurise.current
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(bg)
            .then(if (border) Modifier.border(1.dp, c.line, RoundedCornerShape(19.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) fg else c.soft, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}
