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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
    Maps("Mappe", Icons.MAP),
    Alfred("Alfred", Icons.STAR),
}

/** Dove si trova la finestra: la sezione aperta e il campo «Scrivi come parli» (per «Nuovo» e Ctrl N). */
class Nav {
    var section by mutableStateOf(Section.Today)
    val input = FocusRequester()
    private var focusAsked by mutableStateOf(0)

    /** «Nuovo»: Oggi con il cursore nel campo. */
    fun new() { section = Section.Today; focusAsked++ }

    @Composable
    fun FocusWhenAsked() {
        LaunchedEffect(focusAsked) { if (focusAsked > 0) { delay(50); runCatching { input.requestFocus() } } }
    }

    /** Tasti della finestra: Ctrl N nuovo, Ctrl Spazio Alfred, Ctrl 1…7 le sezioni. */
    fun key(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return false
        when (e.key) {
            Key.N -> new()
            Key.Spacebar -> section = Section.Alfred
            else -> {
                val i = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven).indexOf(e.key)
                if (i < 0) return false
                section = Section.entries[i]
            }
        }
        return true
    }
}

@Composable
fun EuriseApp(ponte: PonteState, today: TodayFeed, nav: Nav) {
    val c = LocalEurise.current
    nav.FocusWhenAsked()
    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar(nav)
        Row(Modifier.fillMaxSize()) {
            Sidebar(nav.section, ponte, today) { nav.section = it }
            Box(Modifier.fillMaxSize()) {
                when {
                    ponte.pairingCode != null -> Box(Modifier.padding(28.dp)) { Pairing(ponte.pairingCode!!) }
                    nav.section == Section.Today -> TodayScreen(today, nav.input)
                    nav.section == Section.Maps -> MapsScreen(today)
                    else -> Box(Modifier.padding(28.dp)) { Waiting(nav.section) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(nav: Nav) {
    val c = LocalEurise.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(c.bar).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.width(198.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.vector(Icons.STAR, fill = true), null, tint = c.ottone, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Eurise", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(
            Modifier.weight(3f, fill = false).widthIn(max = 560.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.vector(Icons.SEARCH), null, tint = c.soft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Cerca in tutta la suite", color = c.soft, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            Text("Ctrl K", color = c.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Pill(Icons.PLUS, "Nuovo", null, c.onAccent, c.accent, c.onAccent) { nav.new() }
        Pill(Icons.MIC, "Alfred", "Ctrl Spazio", c.prugna, c.card, c.text) { nav.section = Section.Alfred }
    }
}

@Composable
private fun Pill(icon: String, label: String, keys: String?, iconTint: Color, bg: Color, fg: Color, onClick: () -> Unit) {
    val c = LocalEurise.current
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.vector(icon, width = 2.1f), null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(label, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        if (keys != null) Text(keys, color = c.soft, fontSize = 10.5.sp)
    }
}

@Composable
private fun Sidebar(active: Section, ponte: PonteState, today: TodayFeed, onPick: (Section) -> Unit) {
    val c = LocalEurise.current
    val tasksToday = today.summary?.let { TodayData.dueToday(it, java.time.LocalDate.now()).size } ?: 0
    Column(Modifier.width(216.dp).fillMaxHeight().background(c.sidebar).padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (s in Section.entries) {
            val on = s == active
            val tint = tintOf(s)
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.selected else Color.Transparent).clickable { onPick(s) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.vector(s.icon, width = if (on) 2.2f else 1.9f), s.label, tint = if (on) tint else c.soft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.label, color = if (on) tint else c.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (s == Section.Tasks && tasksToday > 0) Badge(tasksToday)
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
                Text(phone?.let { if (live) "${it.name} collegato" else it.name } ?: "Nessun telefono", color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
        // Il centro privacy del computer arriverà con le sue tavole; per ora il livello: tutto resta qui e sul telefono.
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.vector(Icons.SHIELD), null, tint = c.salvia, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Centro privacy", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PrivacyBars(10)
        }
    }
}

@Composable
private fun Badge(n: Int) {
    val c = LocalEurise.current
    Box(Modifier.height(20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(10.dp)).background(c.background).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(n.toString(), color = c.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Section.Maps -> c.petrolio
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
        Section.Maps -> "La strada per i tuoi impegni, con le stesse mappe del telefono e senza Internet; poi «Al telefono» per guidare."
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
    const val MAP = "M9 4 3.5 6v14L9 18l6 2 5.5-2V4L15 6z M9 4v14 M15 6v14"
    const val PHONE = "M8 3 h8 a1.5 1.5 0 0 1 1.5 1.5 v15 A1.5 1.5 0 0 1 16 21 H8 a1.5 1.5 0 0 1 -1.5-1.5 v-15 A1.5 1.5 0 0 1 8 3 z M11 18 h2"
    const val SEARCH = "M4.5 11 a6.5 6.5 0 1 0 13 0 a6.5 6.5 0 1 0 -13 0 M16 16 l4.5 4.5"
    const val PLUS = "M12 5v14M5 12h14"
    const val MIC = "M9 4.6 a3 3 0 0 1 6 0 V11 a3 3 0 0 1 -6 0 Z M5.5 11 a6.5 6.5 0 0 0 13 0 M12 17.5 V21"
    const val BELL = "M6 16 V11 a6 6 0 0 1 12 0 v5 l1.5 2 h-15 z M10 20.5 h4"
    const val HANDOFF = "M4 7 h11 v11 H4 z M8 4 h12 v11"
    const val SHIELD = "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"

    private val cache = HashMap<Triple<String, Boolean, Float>, ImageVector>()

    fun vector(path: String, fill: Boolean = false, width: Float = 1.9f): ImageVector = cache.getOrPut(Triple(path, fill, width)) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            if (fill) addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            else addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }
}
