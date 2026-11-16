package eu.eurise.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.ponte.Kind
import eu.eurise.desktop.Linux
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Oggi sul computer, dalle tavole PcOggi (Perla) e PcOggiNotte (Notte): saluto e riassunto, «Scrivi come parli», poi
 * tre colonne: appuntamenti e ciò che arriva dal telefono; da fare e note recenti; email importanti e la riga della
 * privacy. Note ed email compaiono quando le loro sezioni del computer saranno pronte: finché mancano lo si dice in una
 * riga, senza dati inventati.
 */
@Composable
fun TodayScreen(feed: TodayFeed, input: FocusRequester) {
    val c = LocalEurise.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now() } }
    DisposableEffect(Unit) {
        feed.visible = true
        feed.poke()
        onDispose { feed.visible = false }
    }
    val s = feed.summary
    val today = now.toLocalDate()
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(TodayData.dateLine(today), color = c.soft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val head = TodayData.greeting(now, feed.name) + " " + when {
                s != null -> TodayData.headline(s.events, now)
                feed.phone == TodayFeed.Phone.Off -> "Il riassunto del telefono è spento."
                else -> "Il telefono non è sulla rete di casa."
            }
            Text(head, color = c.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        }
        QuickInput(feed, input)
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card("I tuoi appuntamenti") {
                    when {
                        s == null -> Empty(phoneLine(feed.phone))
                        s.events.isEmpty() -> Empty("Nessun altro impegno oggi.")
                        else -> s.events.forEachIndexed { i, e ->
                            val next = TodayData.countdown(e, now)
                            Item(i > 0, Icons.CALENDAR, c.blu, TodayData.eventTitle(e, today), TodayData.eventSub(e), next, c.blu)
                        }
                    }
                }
                Card("Dal telefono") {
                    if (feed.recent.isEmpty()) Empty("Link, appunti e avvisi che passano dal telefono compaiono qui.")
                    feed.recent.forEachIndexed { i, r ->
                        val icon = when (r.kind) { Kind.Notify.wire -> Icons.BELL; Kind.Dictation.wire -> Icons.MIC; else -> Icons.HANDOFF }
                        val tint = if (r.kind == Kind.Notify.wire) c.petrolio else c.blu
                        val sub = (if (r.url != null) "aperto sul telefono " else "") + TodayData.ago(r.at) +
                            (if (r.url != null) " · Continua qui" else r.sub.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                        Item(i > 0, icon, tint, r.title, sub, null, tint, onClick = r.url?.let { u -> { Linux.open(u) } })
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val due = s?.let { TodayData.dueToday(it, today) }.orEmpty()
                val shown = if (due.isNotEmpty()) due else s?.tasks.orEmpty()
                Card(if (due.isNotEmpty()) "Da fare oggi · ${due.size}" else "Da fare" + (s?.tasksOpen?.takeIf { it > 0 }?.let { " · $it" } ?: "")) {
                    when {
                        s == null -> Empty(phoneLine(feed.phone))
                        shown.isEmpty() -> Empty("Niente da fare. Goditi la giornata.")
                        else -> shown.forEachIndexed { i, t -> Todo(i > 0, t.title, TodayData.dueLine(t, today), TodayData.overdue(t, today)) }
                    }
                }
                Card("Note recenti") { Empty("Le tue note compaiono qui quando la sezione Note del computer è collegata al cloud delle note.") }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card("Email importanti") { Empty("Le email delle persone, non le newsletter, quando colleghi la tua casella nella sezione Email.") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.vector(Icons.SHIELD), null, tint = c.salvia, modifier = Modifier.size(15.dp))
                    Text("Tutto su questo computer e sul tuo telefono, sulla rete di casa.", color = c.soft, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    PrivacyBars(10)
                }
            }
        }
    }
}

private fun phoneLine(p: TodayFeed.Phone) = when (p) {
    TodayFeed.Phone.Off -> "Sul telefono: Eurise › Telefono e computer › accendi «Oggi sul computer»."
    TodayFeed.Phone.Unknown -> "Chiedo al telefono…"
    else -> "Arrivano dal telefono quando è sulla rete di casa, con «Oggi sul computer» acceso."
}

/** «Scrivi come parli» con Evento, Da fare, Nota (Evento di base, come nella tavola); Invio manda al telefono. */
@Composable
private fun QuickInput(feed: TodayFeed, focus: FocusRequester) {
    val c = LocalEurise.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.Event) }
    var said by remember { mutableStateOf<String?>(null) }
    fun send() {
        val t = text.trim().ifBlank { return }
        val fields = when (kind) {
            Kind.Event -> TodayData.eventFields(t, LocalDateTime.now()) ?: return
            else -> mapOf("text" to t)
        }
        val k = kind
        text = ""
        scope.launch { said = withContext(Dispatchers.IO) { feed.send(k, fields) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x10000000), spotColor = Color(0x10000000))
                .clip(RoundedCornerShape(24.dp)).background(c.card).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.vector(Icons.PLUS), null, tint = c.soft, modifier = Modifier.size(17.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(
                    "Scrivi come parli: «dentista giovedì alle 15», «comprare il toner», «idea per il sito»",
                    color = c.soft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; said = null },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { send(); true } else false
                    },
                )
            }
            for ((k, label) in listOf(Kind.Event to "Evento", Kind.Task to "Da fare", Kind.Note to "Nota")) {
                val on = k == kind
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) c.accent else c.card)
                        .clickable { kind = k }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) c.onAccent else c.text, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) }
            }
        }
        val hint = said ?: if (kind == Kind.Event && text.isNotBlank()) TodayData.eventPreview(text, LocalDateTime.now())?.let { "Appuntamento: $it · Invio per mandarlo al telefono" } else null
        if (hint != null) Text(hint, color = c.soft, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalEurise.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), color = c.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        Column(content = content)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = LocalEurise.current.soft, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 6.dp))
}

/** Una riga con il riquadro colorato, titolo, sottotitolo e, se c'è, una scritta a destra nella tinta. */
@Composable
private fun Item(line: Boolean, icon: String, tint: Color, title: String, sub: String, end: String?, endTint: Color, onClick: (() -> Unit)? = null) {
    val c = LocalEurise.current
    if (line) Divider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).then(if (onClick != null) Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(pale(tint)), contentAlignment = Alignment.Center) {
            Icon(Icons.vector(icon, width = 2.1f), null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = c.soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (end != null) Text(end, color = endTint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Una cosa da fare. La casella è disegnata ma non si spunta dal computer: il Ponte non ha ancora un tipo per
 * «fatta» (prossimo passo, insieme ad Attività su CalDAV).
 */
@Composable
private fun Todo(line: Boolean, title: String, sub: String, late: Boolean) {
    val c = LocalEurise.current
    if (line) Divider()
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).border(1.6.dp, c.salvia, RoundedCornerShape(5.dp)))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = if (late) c.borgogna else c.soft, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalEurise.current.line))

/** Il riquadro chiaro dietro un'icona: la tinta stemperata nel fondo della scheda (Perla e Notte). */
@Composable
fun pale(tint: Color): Color = lerp(LocalEurise.current.card, tint, 0.17f)

/** Le dieci tacchette a semaforo (CLAUDE.md, «Privacy sempre chiara»): accese da sinistra fino a [level]. */
@Composable
fun PrivacyBars(level: Int) {
    val c = LocalEurise.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        PRIVACY.forEachIndexed { i, col ->
            Box(Modifier.width(4.dp).height(12.dp).clip(RoundedCornerShape(2.dp)).background(if (i < level) col else c.line))
        }
    }
}

private val PRIVACY = listOf(
    0xFFB5473A, 0xFFC2603A, 0xFFC97F32, 0xFFC99B2E, 0xFFB5A82E, 0xFF93A33A, 0xFF6E9A45, 0xFF4F8E4E, 0xFF3F8150, 0xFF2F7350,
).map { Color(it) }
