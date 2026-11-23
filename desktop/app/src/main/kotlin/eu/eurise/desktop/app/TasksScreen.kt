package eu.eurise.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.sync.CalItem
import eu.eurise.core.sync.CalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Le liste furbe delle Attività (tavola PcAttivita); [list] è l'href di un elenco del cloud. */
sealed interface TaskFilter {
    data object Today : TaskFilter
    data object Late : TaskFilter
    data object Week : TaskFilter
    data object All : TaskFilter
    data class List(val href: String) : TaskFilter
}

/** Un'attività con l'elenco da cui viene. */
data class TaskRow(val uid: String, val list: String, val item: CalItem) {
    val due: LocalDate? get() = when (val e = item.end) {
        is CalTime.Day -> e.date
        is CalTime.At -> e.time.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        null -> null
    }
}

/** Le attività filtrate e raggruppate: In ritardo, Oggi, Domani, i giorni della settimana, Più avanti, Senza data, Fatte. */
object TaskGroups {
    fun rows(agenda: Agenda): kotlin.collections.List<TaskRow> = agenda.entries.filter { !it.deleted }.mapNotNull { e ->
        e.items.firstOrNull { it.todo }?.let { TaskRow(e.uid, e.calendar, it) }
    }

    fun filter(rows: kotlin.collections.List<TaskRow>, f: TaskFilter, today: LocalDate = LocalDate.now()) = rows.filter { r ->
        when (f) {
            TaskFilter.Today -> !r.item.completed && r.due != null && !r.due!!.isAfter(today)
            TaskFilter.Late -> !r.item.completed && r.due != null && r.due!!.isBefore(today)
            TaskFilter.Week -> !r.item.completed && r.due != null && !r.due!!.isAfter(today.plusDays(7))
            TaskFilter.All -> true
            is TaskFilter.List -> r.list == f.href
        }
    }

    fun group(rows: kotlin.collections.List<TaskRow>, today: LocalDate = LocalDate.now()): kotlin.collections.List<Pair<String, kotlin.collections.List<TaskRow>>> {
        val open = rows.filter { !it.item.completed }
        val label = { r: TaskRow ->
            val d = r.due
            when {
                d == null -> "Senza data"
                d.isBefore(today) -> "In ritardo"
                d == today -> "Oggi"
                d == today.plusDays(1) -> "Domani"
                !d.isAfter(today.plusDays(6)) -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar(Char::uppercase)
                else -> "Più avanti"
            }
        }
        val sorted = open.sortedWith(compareBy<TaskRow> { it.due ?: LocalDate.MAX }.thenBy { it.item.summary.lowercase() })
        val groups = sorted.groupBy(label).toList()
        val done = rows.filter { it.item.completed }.sortedByDescending { it.item.lastModified }
        return if (done.isEmpty()) groups else groups + ("Fatte" to done)
    }
}

/** Attività sul computer (tavola PcAttivita, approvata il 16 novembre): gli stessi elenchi del telefono, via CalDAV. */
@Composable
fun TasksScreen(agenda: Agenda) {
    val c = LocalEurise.current
    if (agenda.account == null) {
        Box(Modifier.fillMaxSize().padding(28.dp)) { CloudConnect(agenda, "le Attività") }
        return
    }
    var filter by remember { mutableStateOf<TaskFilter>(TaskFilter.Today) }
    var picked by remember { mutableStateOf<String?>(null) }
    val rows = remember(agenda.entries) { TaskGroups.rows(agenda) }
    val shown = remember(rows, filter) { TaskGroups.group(TaskGroups.filter(rows, filter)) }
    val writable = agenda.taskLists.filter { !it.readOnly }
    val target = (filter as? TaskFilter.List)?.href?.takeIf { h -> writable.any { it.href == h } } ?: writable.firstOrNull()?.href

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(250.dp).fillMaxHeight().padding(start = 20.dp, top = 20.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val smart = listOf(TaskFilter.Today to "Oggi", TaskFilter.Late to "In ritardo", TaskFilter.Week to "Prossimi 7 giorni", TaskFilter.All to "Tutte")
            for ((f, name) in smart) ListEntry(name, TaskGroups.filter(rows, f).count { !it.item.completed }, filter == f, if (f == TaskFilter.Late) c.borgogna else c.blu) { filter = f }
            Spacer(Modifier.height(14.dp))
            Text("Elenchi", color = c.soft, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
            for (l in agenda.taskLists) {
                val f = TaskFilter.List(l.href)
                ListEntry(l.name.ifBlank { "Attività" }, rows.count { it.list == l.href && !it.item.completed }, filter == f, calColor(l)) { filter = f }
            }
            Spacer(Modifier.height(14.dp))
            SyncLine(agenda)
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.card).padding(20.dp)) {
            val title = when (val f = filter) {
                TaskFilter.Today -> "Oggi"; TaskFilter.Late -> "In ritardo"; TaskFilter.Week -> "Prossimi 7 giorni"; TaskFilter.All -> "Tutte"
                is TaskFilter.List -> agenda.calendars.firstOrNull { it.href == f.href }?.name ?: "Elenco"
            }
            Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            QuickAdd(enabled = target != null) { text ->
                val due = if (filter == TaskFilter.Today || filter == TaskFilter.Week) CalTime.Day(LocalDate.now()) else null
                agenda.create(target!!, true, text, null, due)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (shown.isEmpty()) Text(if (filter == TaskFilter.Today) "Niente per oggi." else "Nessuna attività qui.", color = c.soft, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp))
                for ((group, list) in shown) {
                    Text(group, color = if (group == "In ritardo") c.borgogna else c.soft, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    for (r in list) TaskLine(r, agenda, r.uid == picked) { picked = r.uid }
                }
            }
        }
        rows.firstOrNull { it.uid == picked }?.let { r -> TaskPanel(r, agenda) { picked = null } }
    }
}

@Composable
private fun ListEntry(name: String, count: Int, on: Boolean, tint: Color, onClick: () -> Unit) {
    val c = LocalEurise.current
    Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(if (on) pale(tint) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(10.dp))
        Text(name, color = c.text, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count > 0) Text(count.toString(), color = c.soft, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Il campo «Aggiungi un'attività»: Invio la crea nell'elenco scelto (o nel primo). */
@Composable
private fun QuickAdd(enabled: Boolean, onAdd: (String) -> Unit) {
    val c = LocalEurise.current
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.background).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+", color = c.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(if (enabled) "Aggiungi un'attività e premi Invio" else "Nessun elenco in cui scrivere", color = c.soft, fontSize = 14.sp)
            BasicTextField(text, { text = it.replace("\n", "") }, enabled = enabled, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) { if (text.isNotBlank()) { onAdd(text.trim()); text = "" }; true } else false
                })
        }
    }
}

@Composable
private fun TaskLine(r: TaskRow, agenda: Agenda, on: Boolean, onClick: () -> Unit) {
    val c = LocalEurise.current
    val done = r.item.completed
    val tint = calColor(agenda.calendars.firstOrNull { it.href == r.list })
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(if (on) pale(c.blu) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (done) tint else Color.Transparent).border(2.dp, tint, CircleShape).clickable { agenda.complete(r.uid, !done) },
            contentAlignment = Alignment.Center) { if (done) Text("✓", color = c.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(r.item.summary.ifBlank { "(senza titolo)" }, color = if (done) c.soft else c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            textDecoration = if (done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        r.due?.let { d -> Text(dueText(d), color = if (!done && d.isBefore(LocalDate.now())) c.borgogna else c.soft, fontSize = 12.5.sp) }
    }
}

private fun dueText(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "oggi"; today.plusDays(1) -> "domani"; today.minusDays(1) -> "ieri"
        else -> "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}"
    }
}

/** I dettagli dell'attività scelta: titolo, scadenza, note; Fatto ed Elimina. */
@Composable
private fun TaskPanel(r: TaskRow, agenda: Agenda, onClose: () -> Unit) {
    val c = LocalEurise.current
    var title by remember(r.uid) { mutableStateOf(r.item.summary) }
    var due by remember(r.uid) { mutableStateOf(r.due?.let { "${it.dayOfMonth}/${it.monthValue}/${it.year}" } ?: "") }
    var notes by remember(r.uid) { mutableStateOf(r.item.description) }
    val dueDay = if (due.isBlank()) null else EventDraft.parseDay(due)
    val readOnly = agenda.calendars.firstOrNull { it.href == r.list }?.readOnly == true
    val changed = title != r.item.summary || notes != r.item.description || dueDay != r.due
    Column(Modifier.width(320.dp).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(agenda.calendars.firstOrNull { it.href == r.list }?.name.orEmpty(), color = c.soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("✕", color = c.soft, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
        }
        FormField("Attività", title) { title = it }
        FormField("Scadenza (g/m/aaaa, vuoto = nessuna)", due) { due = it }
        FormField("Note", notes) { notes = it }
        if (r.item.rrule != null) Text("Si ripete", color = c.soft, fontSize = 12.5.sp)
        Spacer(Modifier.weight(1f))
        if (!readOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (changed) PillButton("Salva", c.accent, c.onAccent, enabled = title.isNotBlank() && (due.isBlank() || dueDay != null)) {
                agenda.update(r.uid, title.trim(), r.item.start, dueDay?.let { CalTime.Day(it) }, r.item.location, notes.trim())
            } else PillButton(if (r.item.completed) "Da fare" else "Fatto", c.accent, c.onAccent) { agenda.complete(r.uid, !r.item.completed) }
            PillButton("Elimina", c.card, c.borgogna, border = true) { agenda.delete(r.uid); onClose() }
        }
    }
}
