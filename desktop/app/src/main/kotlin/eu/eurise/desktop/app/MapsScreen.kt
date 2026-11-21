package eu.eurise.desktop.app

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.ponte.Kind
import eu.eurise.desktop.Control
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Mappe sul computer (tavola PcMappe, approvata il 16 novembre). A sinistra come nella tavola: «Dove devi andare?», il
 * prossimo impegno con un luogo con «Al telefono, per guidare» (il luogo arriva in Mappe col Ponte) e gli altri impegni
 * con un luogo. La mappa vera è CoMaps per Linux (Flathub, `app.comaps.comaps`), con le stesse mappe offline del
 * telefono: Eurise la apre sul luogo con un link `geo:` attraverso il sistema (xdg-open, anche dal Flatpak). Dentro la
 * finestra di Eurise non c'è ancora: CoMaps per Linux è un'app Qt e su Wayland non si può incastrare in un'altra.
 */
@Composable
fun MapsScreen(feed: TodayFeed) {
    val c = LocalEurise.current
    DisposableEffect(Unit) {
        feed.visible = true
        feed.poke()
        onDispose { feed.visible = false }
    }
    var status by remember { mutableStateOf<String?>(null) }
    val now = LocalDateTime.now()
    val places = MapsData.withPlace(feed.summary, now)
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(360.dp).fillMaxHeight().background(c.background).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Search { q -> status = openMap(q) }
            val next = places.firstOrNull()
            if (next != null) NextCard(next, now, onPhone = { thread(isDaemon = true) { status = toPhone(next) } }, onMap = { status = openMap(next.place) })
            Label("I tuoi impegni con un luogo")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal = 14.dp)) {
                val rest = places.drop(1)
                when {
                    feed.summary == null -> Hint(phoneHint(feed.phone))
                    places.isEmpty() -> Hint("Nessun impegno con un luogo, oggi e domani.")
                    rest.isEmpty() -> Hint("Nessun altro impegno con un luogo.")
                }
                rest.forEachIndexed { i, e ->
                    Row2(i > 0, Icons.CALENDAR, c.blu, "${MapsData.whenText(e, now.toLocalDate())} · ${e.title}", e.place) { status = openMap(e.place) }
                }
            }
            if (status != null) Text(status!!, color = c.soft, fontSize = 12.5.sp, lineHeight = 18.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.vector(Icons.SHIELD), null, tint = c.salvia, modifier = Modifier.size(15.dp))
                Text("Mappe sul computer senza Internet, in CoMaps.", color = c.soft, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                PrivacyBars(10)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Box(Modifier.fillMaxSize().background(lerpBg()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(18.dp)).background(c.card).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.vector(Icons.MAP, width = 2.1f), null, tint = c.petrolio, modifier = Modifier.size(28.dp))
                Text("La mappa si apre in CoMaps, accanto a Eurise", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Le stesse mappe del telefono, scaricate sul computer e senza Internet. Scegli un impegno o cerca un luogo: " +
                        "CoMaps si apre lì. Per guidare, «Al telefono» lo manda a Mappe.",
                    color = c.soft, fontSize = 13.sp, lineHeight = 19.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button("Apri la mappa", Icons.MAP, primary = true) { status = openMap(null) }
                    Button("Installa CoMaps", Icons.PLUS, primary = false) {
                        eu.eurise.desktop.Linux.open("https://flathub.org/apps/app.comaps.comaps")
                    }
                }
                Text("CoMaps per Linux è su Flathub; Eurise non manda nulla in rete per mostrarti la mappa.", color = c.soft, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun lerpBg(): Color = androidx.compose.ui.graphics.lerp(LocalEurise.current.background, LocalEurise.current.petrolio, 0.06f)

@Composable
private fun Search(onGo: (String) -> Unit) {
    val c = LocalEurise.current
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(c.card).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.vector(Icons.SEARCH), null, tint = c.soft, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Dove devi andare?", color = c.soft, fontSize = 14.sp)
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onGo(text) }),
                modifier = Modifier.fillMaxWidth().onEnter { if (text.isNotBlank()) onGo(text) },
            )
        }
    }
}

/** Invio sul campo (sul computer la tastiera non ha il tasto «Cerca»). */
private fun Modifier.onEnter(action: () -> Unit): Modifier = this.then(
    Modifier.onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) { action(); true } else false
    },
)

@Composable
private fun NextCard(e: TodayData.Event, now: LocalDateTime, onPhone: () -> Unit, onMap: () -> Unit) {
    val c = LocalEurise.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.vector(Icons.CALENDAR), null, tint = c.blu, modifier = Modifier.size(15.dp))
            Text("Dal Calendario · ${MapsData.whenText(e, now.toLocalDate())}", color = c.blu, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(e.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(e.place, color = c.soft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        MapsData.startsIn(e, now)?.let { Text(it, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Al telefono, per guidare", Icons.PHONE, primary = true, onClick = onPhone)
            Button("Mappa", Icons.MAP, primary = false, onClick = onMap)
        }
        Text("Sul telefono si apre in Mappe, pronta per la guida e per Android Auto.", color = c.soft, fontSize = 11.5.sp)
    }
}

@Composable
private fun Button(label: String, icon: String, primary: Boolean, onClick: () -> Unit) {
    val c = LocalEurise.current
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (primary) c.accentStrong() else c.background)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.vector(icon, width = 2.1f), null, tint = if (primary) Color.White else c.text, modifier = Modifier.size(15.dp))
        Text(label, color = if (primary) Color.White else c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Il blu notte dei pulsanti principali della suite (su Notte, dove il testo è chiaro, il blu della suite). */
private fun EuriseColors.accentStrong(): Color = if (text.red > 0.5f) blu else Color(0xFF1F2A44)

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = LocalEurise.current.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalEurise.current.soft, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun Row2(line: Boolean, icon: String, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    val c = LocalEurise.current
    if (line) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(pale(tint)), contentAlignment = Alignment.Center) {
            Icon(Icons.vector(icon, width = 2.1f), null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = c.soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun phoneHint(p: TodayFeed.Phone) = when (p) {
    TodayFeed.Phone.Off -> "Sul telefono «Oggi sul computer» è spento: accendilo nel Ponte per vedere qui gli impegni."
    TodayFeed.Phone.Away -> "Il telefono non è sulla rete di casa: gli impegni arrivano quando torna."
    else -> "Chiedo gli impegni al telefono…"
}

/** Apre CoMaps (o l'app scelta dal sistema per i link geo:) sul luogo; senza luogo apre solo la mappa. */
private fun openMap(place: String?): String {
    val uri = place?.let(MapsData::geo) ?: "geo:0,0"
    if (uri != "geo:0,0" && !MapsData.isGeo(uri)) return "Luogo non valido."
    return runCatching {
        val p = ProcessBuilder("xdg-open", uri).redirectErrorStream(true).start()
        if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() != 0) "Nessuna app per le mappe: installa CoMaps da Flathub."
        else if (place == null) "Apro CoMaps…" else "Apro «${place.take(40)}» in CoMaps…"
    }.getOrElse { "Non riesco ad aprire le mappe: installa CoMaps da Flathub." }
}

/** Manda il luogo dell'impegno a Mappe sul telefono, col Ponte (si apre dalla notifica «Continua qui»). */
private fun toPhone(e: TodayData.Event): String {
    val uri = MapsData.geo(e.place) ?: return "L'impegno non ha un luogo."
    val lines = Control.ask(Kind.Handoff, mapOf("url" to uri, "title" to "${e.title} · ${e.place}".take(120)))
        ?: return "Il Ponte non è acceso: riapri Eurise."
    return when {
        lines.any { it.startsWith("errore") } -> "Non mandato: ${lines.first()}"
        lines.isEmpty() -> "Nessun telefono abbinato."
        lines.all { "arrivato" in it } -> "Mandato al telefono: tocca la notifica per aprirlo in Mappe."
        else -> "In coda: parte quando il telefono torna sulla rete di casa."
    }
}
