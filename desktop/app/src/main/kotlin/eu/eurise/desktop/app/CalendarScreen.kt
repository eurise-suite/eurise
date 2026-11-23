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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.sync.CalTime
import eu.eurise.core.sync.DavCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ita = Locale.ITALIAN
private val hm = DateTimeFormatter.ofPattern("H:mm")

/** Le viste del Calendario (tavola PcCalendario). */
enum class CalView(val label: String) { Day("Giorno"), Three("3 giorni"), Week("Settimana"), Month("Mese"), List("Agenda") }

/** Calendario sul computer (tavola PcCalendario, approvata il 16 novembre): gli stessi calendari del telefono, via CalDAV. */
@Composable
fun CalendarScreen(agenda: Agenda) {
    val c = LocalEurise.current
    if (agenda.account == null) {
        Box(Modifier.fillMaxSize().padding(28.dp)) { CloudConnect(agenda, "il Calendario") }
        return
    }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var view by remember { mutableStateOf(CalView.Week) }
    var hidden by remember { mutableStateOf(setOf<String>()) }
    var picked by remember { mutableStateOf<Occurrences.Occurrence?>(null) }
    var editing by remember { mutableStateOf<EventDraft?>(null) }
    val (from, to) = calRange(anchor, view)
    val occurrences = remember(agenda.entries, from, to, hidden) { occurrencesIn(agenda, from, to, hidden) }

    Row(Modifier.fillMaxSize()) {
        // Colonna sinistra: mese piccolo, calendari, stato della sincronizzazione.
        Column(Modifier.width(250.dp).fillMaxHeight().padding(start = 20.dp, top = 20.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniMonth(anchor) { anchor = it }
            Text("Calendari", color = c.soft, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (cal in agenda.eventCalendars) {
                    val on = cal.href !in hidden
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { hidden = if (on) hidden + cal.href else hidden - cal.href }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(if (on) calColor(cal) else Color.Transparent).border(2.dp, calColor(cal), RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(cal.name.ifBlank { "Calendario" }, color = c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (cal.readOnly) "sola lettura" else agenda.account!!.label, color = c.soft, fontSize = 11.sp)
                    }
                }
            }
            SyncLine(agenda)
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp, end = 16.dp)) {
            // Testata: Oggi, frecce, intervallo, viste, nuovo appuntamento.
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Oggi", c.card, c.text, border = true) { anchor = LocalDate.now() }
                PillButton("‹", c.card, c.text) { anchor = step(anchor, view, -1) }
                PillButton("›", c.card, c.text) { anchor = step(anchor, view, 1) }
                Text(title(from, to, view), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(19.dp)).background(c.card).padding(3.dp)) {
                    for (v in CalView.entries) {
                        val on = v == view
                        Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) pale(c.blu) else Color.Transparent)
                            .clickable { view = v }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(v.label, color = if (on) c.blu else c.text, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold)
                        }
                    }
                }
                PillButton("Nuovo appuntamento", c.accent, c.onAccent, enabled = agenda.eventCalendars.any { !it.readOnly }) {
                    editing = EventDraft.new(anchor, agenda.eventCalendars.firstOrNull { !it.readOnly }?.href)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)) {
                when (view) {
                    CalView.Month -> MonthGrid(anchor, occurrences, agenda) { picked = it }
                    CalView.List -> AgendaList(occurrences, agenda) { picked = it }
                    else -> DaysGrid(from, to, occurrences, agenda, onPick = { picked = it }) { day, hour ->
                        editing = EventDraft.new(day, agenda.eventCalendars.firstOrNull { !it.readOnly }?.href, hour)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        picked?.let { o ->
            EventPanel(o, agenda, onEdit = { editing = EventDraft.from(o, agenda) }, onClose = { picked = null })
        }
    }
    editing?.let { d -> EventEditor(d, agenda, onDone = { editing = null; picked = null }) }
}

/** L'intervallo di giorni della vista: da lunedì per la settimana, dal primo lunedì visibile per il mese. */
fun calRange(anchor: LocalDate, view: CalView): Pair<LocalDate, LocalDate> = when (view) {
    CalView.Day -> anchor to anchor
    CalView.Three -> anchor to anchor.plusDays(2)
    CalView.Week -> anchor.with(DayOfWeek.MONDAY).let { it to it.plusDays(6) }
    CalView.Month -> anchor.withDayOfMonth(1).with(DayOfWeek.MONDAY).let { it to it.plusDays(41) }
    CalView.List -> anchor to anchor.plusDays(30)
}

private fun step(anchor: LocalDate, view: CalView, dir: Long): LocalDate = when (view) {
    CalView.Day -> anchor.plusDays(dir)
    CalView.Three -> anchor.plusDays(3 * dir)
    CalView.Week -> anchor.plusWeeks(dir)
    CalView.Month -> anchor.plusMonths(dir)
    CalView.List -> anchor.plusDays(30 * dir)
}

private fun title(from: LocalDate, to: LocalDate, view: CalView): String {
    val month = { d: LocalDate -> d.month.getDisplayName(TextStyle.FULL, ita) }
    return when (view) {
        CalView.Day -> from.dayOfWeek.getDisplayName(TextStyle.FULL, ita) + " " + from.dayOfMonth + " " + month(from)
        CalView.Month -> from.plusDays(15).let { month(it).replaceFirstChar(Char::uppercase) + " " + it.year }
        else -> if (from.month == to.month) "${from.dayOfMonth} – ${to.dayOfMonth} ${month(to)}" else "${from.dayOfMonth} ${month(from)} – ${to.dayOfMonth} ${month(to)}"
    }
}

/** Le occorrenze dei calendari visibili nell'intervallo, con le ripetizioni svolte. */
private fun occurrencesIn(agenda: Agenda, from: LocalDate, to: LocalDate, hidden: Set<String>): List<Occurrences.Occurrence> =
    agenda.entries.filter { !it.deleted && it.calendar !in hidden }.flatMap { e ->
        val (ex, over) = runCatching { Occurrences.exceptions(e.ics) }.getOrDefault(emptySet<LocalDate>() to emptySet())
        Occurrences.of(e.items, e.calendar, from, to, ex, over)
    }

/** Il colore del calendario: quello del cloud, o il blu della suite. */
@Composable
fun calColor(cal: DavCalendar?): Color {
    val hex = cal?.color?.removePrefix("#")?.take(6)
    return hex?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: LocalEurise.current.blu
}

@Composable
private fun MiniMonth(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalEurise.current
    var month by remember(selected.withDayOfMonth(1)) { mutableStateOf(selected.withDayOfMonth(1)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.month.getDisplayName(TextStyle.FULL, ita).replaceFirstChar(Char::uppercase) + " " + month.year, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("‹", color = c.soft, fontSize = 16.sp, modifier = Modifier.clickable { month = month.minusMonths(1) }.padding(horizontal = 8.dp))
            Text("›", color = c.soft, fontSize = 16.sp, modifier = Modifier.clickable { month = month.plusMonths(1) }.padding(horizontal = 8.dp))
        }
        Row { listOf("L", "M", "M", "G", "V", "S", "D").forEach { Text(it, color = c.soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) } }
        val first = month.with(DayOfWeek.MONDAY).let { if (it.isAfter(month)) it.minusWeeks(1) else it }
        for (w in 0 until 6) Row {
            for (d in 0 until 7) {
                val day = first.plusDays((w * 7 + d).toLong())
                val today = day == LocalDate.now()
                val on = day == selected
                Box(Modifier.weight(1f).height(28.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent).clickable { onPick(day) }, contentAlignment = Alignment.Center) {
                    Text(day.dayOfMonth.toString(), fontSize = 12.sp,
                        color = when { on -> c.onAccent; day.month != month.month -> c.soft.copy(alpha = 0.5f); today -> c.blu; else -> c.text },
                        fontWeight = if (today || on) FontWeight.ExtraBold else FontWeight.Medium)
                }
            }
        }
    }
}

private const val FIRST_HOUR = 7
private const val LAST_HOUR = 22
private val HOUR = 52.dp

/** Giorno, tre giorni e settimana: colonne con le ore; tocco su un'ora vuota = nuovo appuntamento lì. */
@Composable
private fun DaysGrid(from: LocalDate, to: LocalDate, occ: List<Occurrences.Occurrence>, agenda: Agenda, onPick: (Occurrences.Occurrence) -> Unit, onEmpty: (LocalDate, Int) -> Unit) {
    val c = LocalEurise.current
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 52.dp)) {
            for (d in days) {
                val today = d == LocalDate.now()
                Column(Modifier.weight(1f).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, ita) + " " + d.dayOfMonth, color = if (today) c.blu else c.text, fontSize = 13.sp, fontWeight = if (today) FontWeight.ExtraBold else FontWeight.Bold)
                    // Tutto il giorno.
                    occ.filter { it.day == d && it.allDay }.take(3).forEach { o -> Chip(o, agenda, onPick) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState(initial = 0))) {
            Column(Modifier.width(52.dp)) {
                for (h in FIRST_HOUR..LAST_HOUR) Box(Modifier.height(HOUR).fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.TopEnd) {
                    Text("$h:00", color = c.soft, fontSize = 11.sp)
                }
            }
            for (d in days) {
                Box(Modifier.weight(1f).height(HOUR * (LAST_HOUR - FIRST_HOUR + 1))) {
                    Column {
                        for (h in FIRST_HOUR..LAST_HOUR) Box(Modifier.height(HOUR).fillMaxWidth().border(0.5.dp, c.line).clickable { onEmpty(d, h) })
                    }
                    for (o in occ.filter { it.day == d && !it.allDay }) {
                        val start = if (o.start!!.toLocalDate().isBefore(d)) LocalTime.of(FIRST_HOUR, 0) else o.start.toLocalTime()
                        val end = o.end?.let { e -> if (e.toLocalDate().isAfter(d)) LocalTime.of(LAST_HOUR, 59) else e.toLocalTime() } ?: start.plusHours(1)
                        val top = ((start.toSecondOfDay() / 3600f) - FIRST_HOUR).coerceAtLeast(0f)
                        val len = ((end.toSecondOfDay() - start.toSecondOfDay()) / 3600f).coerceAtLeast(0.5f)
                        val color = calColor(agenda.calendars.firstOrNull { it.href == o.calendar })
                        Column(
                            Modifier.offset(y = HOUR * top).padding(horizontal = 3.dp).fillMaxWidth().height(HOUR * len - 2.dp)
                                .clip(RoundedCornerShape(8.dp)).background(pale(color)).clickable { onPick(o) }.padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(o.item.summary.ifBlank { "(senza titolo)" }, color = c.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(o.start.toLocalTime().format(hm) + (o.item.location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = c.soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(o: Occurrences.Occurrence, agenda: Agenda, onPick: (Occurrences.Occurrence) -> Unit) {
    val c = LocalEurise.current
    val color = calColor(agenda.calendars.firstOrNull { it.href == o.calendar })
    Text(o.item.summary.ifBlank { "(senza titolo)" }, color = c.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp, start = 3.dp, end = 3.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(pale(color)).clickable { onPick(o) }.padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun MonthGrid(anchor: LocalDate, occ: List<Occurrences.Occurrence>, agenda: Agenda, onPick: (Occurrences.Occurrence) -> Unit) {
    val c = LocalEurise.current
    val (from, _) = calRange(anchor, CalView.Month)
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row { listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom").forEach { Text(it, color = c.soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(6.dp)) } }
        for (w in 0 until 6) Row(Modifier.weight(1f)) {
            for (d in 0 until 7) {
                val day = from.plusDays((w * 7 + d).toLong())
                Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, c.line).padding(4.dp)) {
                    Text(day.dayOfMonth.toString(), fontSize = 12.sp, color = if (day == LocalDate.now()) c.blu else if (day.month != anchor.month) c.soft.copy(alpha = 0.5f) else c.text, fontWeight = FontWeight.Bold)
                    val today = occ.filter { it.day == day }
                    today.take(3).forEach { o -> Chip(o, agenda, onPick) }
                    if (today.size > 3) Text("+${today.size - 3}", color = c.soft, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AgendaList(occ: List<Occurrences.Occurrence>, agenda: Agenda, onPick: (Occurrences.Occurrence) -> Unit) {
    val c = LocalEurise.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (occ.isEmpty()) Text("Niente nei prossimi trenta giorni.", color = c.soft, fontSize = 14.sp)
        occ.groupBy { it.day }.forEach { (day, list) ->
            Text(day.dayOfWeek.getDisplayName(TextStyle.FULL, ita) + " " + day.dayOfMonth + " " + day.month.getDisplayName(TextStyle.FULL, ita),
                color = if (day == LocalDate.now()) c.blu else c.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            list.forEach { o ->
                val color = calColor(agenda.calendars.firstOrNull { it.href == o.calendar })
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(o) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(12.dp))
                    Text(if (o.allDay) "tutto il giorno" else o.start!!.toLocalTime().format(hm), color = c.soft, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                    Text(o.item.summary.ifBlank { "(senza titolo)" }, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (o.item.location.isNotBlank()) Text(o.item.location, color = c.soft, fontSize = 12.5.sp)
                }
            }
        }
    }
}

/** La scheda dell'appuntamento scelto, a destra: quando, dove, note; Modifica ed Elimina. */
@Composable
private fun EventPanel(o: Occurrences.Occurrence, agenda: Agenda, onEdit: () -> Unit, onClose: () -> Unit) {
    val c = LocalEurise.current
    val cal = agenda.calendars.firstOrNull { it.href == o.calendar }
    Column(Modifier.width(320.dp).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(calColor(cal)))
            Spacer(Modifier.width(8.dp))
            Text(cal?.name.orEmpty(), color = c.soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("✕", color = c.soft, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(4.dp))
        }
        Text(o.item.summary.ifBlank { "(senza titolo)" }, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(whenText(o), color = c.text, fontSize = 13.5.sp)
        if (o.item.location.isNotBlank()) Text(o.item.location, color = c.soft, fontSize = 13.sp)
        if (o.item.rrule != null) Text("Si ripete", color = c.soft, fontSize = 12.5.sp)
        if (o.item.description.isNotBlank()) Text(o.item.description, color = c.text, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.weight(1f))
        if (cal?.readOnly != true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Modifica", c.accent, c.onAccent, onClick = onEdit)
            PillButton("Elimina", c.card, c.borgogna, border = true) { agenda.delete(o.uid); onClose() }
        }
        if (o.item.rrule != null) Text("Modifica ed Elimina valgono per tutte le ripetizioni.", color = c.soft, fontSize = 11.5.sp)
    }
}

private fun whenText(o: Occurrences.Occurrence): String {
    val d = o.day.dayOfWeek.getDisplayName(TextStyle.FULL, ita).replaceFirstChar(Char::uppercase) + " " + o.day.dayOfMonth + " " + o.day.month.getDisplayName(TextStyle.FULL, ita)
    return if (o.allDay) "$d · tutto il giorno" else "$d · ${o.start!!.toLocalTime().format(hm)} – ${o.end?.toLocalTime()?.format(hm) ?: ""}"
}

/** Un appuntamento da scrivere: nuovo o da modificare. */
data class EventDraft(val uid: String?, val calendar: String?, val title: String, val day: String, val from: String, val to: String,
                      val allDay: Boolean, val location: String, val notes: String) {
    companion object {
        private val dmy = DateTimeFormatter.ofPattern("d/M/yyyy")
        fun new(day: LocalDate, calendar: String?, hour: Int = 9) =
            EventDraft(null, calendar, "", day.format(dmy), "%d:00".format(hour), "%d:00".format((hour + 1).coerceAtMost(23)), false, "", "")
        fun from(o: Occurrences.Occurrence, agenda: Agenda): EventDraft {
            val first = agenda.entries.firstOrNull { it.uid == o.uid }?.items?.firstOrNull() ?: o.item
            val zone = ZoneId.systemDefault()
            val start = (first.start as? CalTime.At)?.time?.withZoneSameInstant(zone)
            val end = (first.end as? CalTime.At)?.time?.withZoneSameInstant(zone)
            val day = (first.start as? CalTime.Day)?.date ?: start?.toLocalDate() ?: o.day
            return EventDraft(o.uid, o.calendar, first.summary, day.format(dmy), start?.toLocalTime()?.format(hm) ?: "9:00",
                end?.toLocalTime()?.format(hm) ?: "10:00", first.start is CalTime.Day, first.location, first.description)
        }
        fun parseDay(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim(), dmy) }.getOrNull()
        fun parseTime(s: String): LocalTime? = runCatching { LocalTime.parse(s.trim().let { if (it.length <= 2) "$it:00" else it }, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }
}

/** La finestra per scrivere o cambiare un appuntamento. */
@Composable
private fun EventEditor(start: EventDraft, agenda: Agenda, onDone: () -> Unit) {
    val c = LocalEurise.current
    var d by remember { mutableStateOf(start) }
    val day = EventDraft.parseDay(d.day)
    val from = EventDraft.parseTime(d.from)
    val to = EventDraft.parseTime(d.to)
    val ok = d.title.isNotBlank() && day != null && (d.allDay || (from != null && to != null)) && d.calendar != null
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDone), contentAlignment = Alignment.Center) {
        Column(Modifier.width(460.dp).clip(RoundedCornerShape(18.dp)).background(c.card).clickable(enabled = false) {}.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (d.uid == null) "Nuovo appuntamento" else "Modifica l'appuntamento", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            FormField("Titolo", d.title) { d = d.copy(title = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1.4f)) { FormField("Giorno (g/m/aaaa)", d.day) { d = d.copy(day = it) } }
                if (!d.allDay) {
                    Box(Modifier.weight(1f)) { FormField("Dalle", d.from) { d = d.copy(from = it) } }
                    Box(Modifier.weight(1f)) { FormField("Alle", d.to) { d = d.copy(to = it) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { d = d.copy(allDay = !d.allDay) }) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(if (d.allDay) c.accent else Color.Transparent).border(2.dp, c.accent, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Tutto il giorno", color = c.text, fontSize = 13.5.sp)
            }
            FormField("Dove", d.location) { d = d.copy(location = it) }
            FormField("Note", d.notes) { d = d.copy(notes = it) }
            if (d.uid == null) {
                Text("Calendario", color = c.soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (cal in agenda.eventCalendars.filter { !it.readOnly }) {
                        val on = cal.href == d.calendar
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) pale(calColor(cal)) else c.background).clickable { d = d.copy(calendar = cal.href) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(cal.name, color = c.text, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Salva", c.accent, c.onAccent, enabled = ok) {
                    val zone = ZoneId.systemDefault()
                    val s: CalTime = if (d.allDay) CalTime.Day(day!!) else CalTime.At(day!!.atTime(from!!).atZone(zone))
                    val e: CalTime = if (d.allDay) CalTime.Day(day.plusDays(1)) else CalTime.At(day.atTime(to!!).atZone(zone).let { if (to.isBefore(from)) it.plusDays(1) else it })
                    if (d.uid == null) agenda.create(d.calendar!!, false, d.title.trim(), s, e, d.location.trim(), d.notes.trim())
                    else agenda.update(d.uid!!, d.title.trim(), s, e, d.location.trim(), d.notes.trim())
                    onDone()
                }
                PillButton("Annulla", c.card, c.text, border = true, onClick = onDone)
            }
        }
    }
}

@Composable
fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalEurise.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = c.soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.background).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            androidx.compose.foundation.text.BasicTextField(value, onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.text), modifier = Modifier.fillMaxWidth())
        }
    }
}

