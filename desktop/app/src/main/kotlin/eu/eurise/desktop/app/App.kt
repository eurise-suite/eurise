package eu.eurise.desktop.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Le sezioni della barra laterale, nell'ordine delle tavole «Eurise sul computer». */
enum class Section(val label: String, val icon: String) {
    Today("Oggi", Icons.TODAY),
    Calendar("Calendario", Icons.CALENDAR),
    Notes("Note", Icons.NOTE),
    Tasks("Attività", Icons.TASKS),
    Mail("Email", Icons.MAIL),
    Alfred("Alfred", Icons.STAR),
}

@Composable
fun EuriseApp(ponte: PonteState) {
    val c = LocalEurise.current
    var section by remember { mutableStateOf(Section.Today) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar()
        Row(Modifier.fillMaxSize()) {
            Sidebar(section, ponte) { section = it }
            Box(Modifier.fillMaxSize().padding(28.dp)) {
                if (ponte.pairingCode != null) Pairing(ponte.pairingCode!!) else Waiting(section)
            }
        }
    }
}

@Composable
private fun TopBar() {
    val c = LocalEurise.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(c.bar).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.width(198.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.vector(Icons.STAR, fill = true), null, tint = c.ottone, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Eurise", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.vector(Icons.SEARCH), null, tint = c.soft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Cerca in tutta la suite", color = c.soft, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            Text("Ctrl K", color = c.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Sidebar(active: Section, ponte: PonteState, onPick: (Section) -> Unit) {
    val c = LocalEurise.current
    Column(Modifier.width(216.dp).fillMaxHeight().background(c.sidebar).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in Section.entries) {
            val on = s == active
            val tint = tintOf(s)
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.selected else Color.Transparent).clickable { onPick(s) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.vector(s.icon, fill = s == Section.Alfred), s.label, tint = if (on) tint else c.soft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.label, color = if (on) tint else c.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        val phone = ponte.devices.firstOrNull()
        val live = phone != null && phone.id in ponte.connected
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.vector(Icons.PHONE), null, tint = if (live) c.salvia else c.soft, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(phone?.name ?: "Nessun telefono", color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        phone == null -> "collega dal telefono"
                        live -> "Ponte · stessa rete, cifrato"
                        else -> "abbinato · non in rete adesso"
                    },
                    color = c.soft, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
fun tintOf(s: Section): Color {
    val c = LocalEurise.current
    return when (s) {
        Section.Today -> c.ottone
        Section.Calendar -> c.blu
        Section.Notes -> c.ottone
        Section.Tasks -> c.salvia
        Section.Mail -> c.terracotta
        Section.Alfred -> c.prugna
    }
}

/** Finché le tavole non sono approvate (regola Metodo), ogni sezione dice cosa arriverà e cosa è già pronto sotto. */
@Composable
private fun Waiting(s: Section) {
    val c = LocalEurise.current
    val ready = when (s) {
        Section.Today -> "Oggi raccoglie impegni, cose da fare, email importanti e ciò che arriva dal telefono."
        Section.Calendar -> "CalDAV pronto nel nucleo: calendari di Murena e Nextcloud, etag, unione delle modifiche."
        Section.Notes -> "Le stesse note del telefono (cartella Eurise/Note), con l'unione a tre vie."
        Section.Tasks -> "Le attività arrivano dagli elenchi CalDAV, gli stessi del telefono."
        Section.Mail -> "IMAP e SMTP pronti nel nucleo, direttamente dal computer alla tua casella."
        Section.Alfred -> "Alfred userà il cervello del computer, del telefono o, se lo chiedi tu, uno online."
    }
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.label, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(ready, color = c.text, fontSize = 15.sp)
        Text("La schermata arriva quando la sua tavola è approvata (pagina «Eurise sul computer» della tela).", color = c.soft, fontSize = 13.sp)
    }
}

/** Il primo avvio senza telefono: il codice da inquadrare con Eurise › Telefono e computer › Collega un computer. */
@Composable
private fun Pairing(code: String) {
    val c = LocalEurise.current
    val matrix = remember(code) {
        QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Box(Modifier.size(280.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.minDimension / matrix.width
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Collega il telefono", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Sul telefono: Eurise › Telefono e computer › Collega un computer, poi inquadra il codice.", color = c.text, fontSize = 15.sp)
            Text(
                "Il collegamento è diretto sulla rete di casa e cifrato con le chiavi scambiate adesso: nessun account, nessun server nostro.",
                color = c.soft, fontSize = 13.sp,
            )
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.salvia))
        }
    }
}

/** Le icone della suite come tracciati SVG (gli stessi delle tavole), disegnate a tratto. */
object Icons {
    const val TODAY = "M12 3v2M12 19v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M3 12h2M19 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"
    const val CALENDAR = "M6 5 h12 a2 2 0 0 1 2 2 v11 a2 2 0 0 1 -2 2 H6 a2 2 0 0 1 -2-2 V7 a2 2 0 0 1 2-2 z M4 10 h16 M8.5 3 v4 M15.5 3 v4"
    const val NOTE = "M6.5 3.5 h8 l4 4 v11 a2 2 0 0 1 -2 2 h-10 a2 2 0 0 1 -2-2 v-13 a2 2 0 0 1 2-2 z M8 11 h8 M8 14.5 h8"
    const val TASKS = "M4 6.5 l2 2 3.5-3.5 M12 7 h8 M4 13.5 l2 2 3.5-3.5 M12 14 h8 M12 19.5 h8"
    const val MAIL = "M4 6 h16 v12 H4 z M4 7 l8 6 8-6"
    const val STAR = "M12 3 L13.6 10.4 21 12 13.6 13.6 12 21 10.4 13.6 3 12 10.4 10.4 Z"
    const val PHONE = "M8 3 h8 a1.5 1.5 0 0 1 1.5 1.5 v15 A1.5 1.5 0 0 1 16 21 H8 a1.5 1.5 0 0 1 -1.5-1.5 v-15 A1.5 1.5 0 0 1 8 3 z M11 18 h2"
    const val SEARCH = "M4.5 11 a6.5 6.5 0 1 0 13 0 a6.5 6.5 0 1 0 -13 0 M16 16 l4.5 4.5"

    private val cache = HashMap<Pair<String, Boolean>, ImageVector>()

    fun vector(path: String, fill: Boolean = false): ImageVector = cache.getOrPut(path to fill) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            if (fill) addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            else addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}
