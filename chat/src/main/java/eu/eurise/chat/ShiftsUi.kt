package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colori dei tipi di turno, nell'ordine in cui sono scritti (sfondo, testo), come nella tavola ChatTurni. */
private val TYPE_COLORS = listOf(
    Color(0xFFDDE3F0) to Color(0xFF3E5C8A), Color(0xFFF2E6CC) to Color(0xFF85672B), Color(0xFFE7DDE8) to Color(0xFF6A4A6B),
    Color(0xFFDCE7DF) to Color(0xFF4F6B5A), Color(0xFFF3DCD3) to Color(0xFFA4502E), Color(0xFFD9E9EA) to Color(0xFF2E6E73),
)
private val REST_BG = Color(0xFFEFEFEA)
private val REST_FG = Color(0xFF9A9A92)
private val OK = Color(0xFF4F6B5A)
private val WARN = Color(0xFFA4502E)
private val IT = Locale.ITALIAN
private val DAY = DateTimeFormatter.ofPattern("EEE d", IT)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)
private val HM = DateTimeFormatter.ofPattern("H:mm", IT)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", IT)

private fun colors(plan: Shifts.Plan, code: String): Pair<Color, Color> =
    plan.types.indexOfFirst { it.code == code }.takeIf { it >= 0 }?.let { TYPE_COLORS[it % TYPE_COLORS.size] } ?: (REST_BG to REST_FG)

/** «la mattina di martedì 18 novembre». */
private fun shiftName(plan: Shifts.Plan, date: LocalDate, code: String): String =
    "${plan.type(code)?.name?.lowercase(IT) ?: code} di ${date.format(LONG_DAY)}"

@Composable
private fun name(model: ChatModel, id: String): String =
    if (id == model.session?.userId) stringResource(R.string.sh_you) else model.shiftName(id)

@Composable
private fun problemText(p: Set<Shifts.Problem>): String = p.sortedBy { it.ordinal }.map {
    stringResource(when (it) {
        Shifts.Problem.Unavailable -> R.string.sh_p_unavailable
        Shifts.Problem.Overlap -> R.string.sh_p_overlap
        Shifts.Problem.Rest -> R.string.sh_p_rest
        Shifts.Problem.InARow -> R.string.sh_p_row
    })
}.joinToString(", ")

// --- Scheda «Turni» del gruppo (tavola ChatTurni) ---

private sealed interface ShiftSheet {
    data object Create : ShiftSheet
    data object Avail : ShiftSheet
    data object Hours : ShiftSheet
    data class Cell(val key: String) : ShiftSheet
    data class Add(val date: LocalDate, val person: String) : ShiftSheet
    /** Importa su [base]: il piano appena impostato in «Fai i turni», o quello del gruppo. */
    data class Import(val base: Shifts.Plan) : ShiftSheet
    data object Export : ShiftSheet
}

@Composable
internal fun ShiftsPane(model: ChatModel, actions: ChatActions, roomId: String) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val me = model.session?.userId.orEmpty()
    LaunchedEffect(roomId) { model.loadExpenses(roomId) }
    val plan = model.shiftPlan(roomId)
    val eff = model.shiftEffective(roomId)
    val avail = model.shiftAvail(roomId)
    val boss = model.shiftCoordinator(roomId)
    val canEdit = plan == null || boss == me
    var sheet by remember(roomId) { mutableStateOf<ShiftSheet?>(null) }
    var week by remember(roomId) { mutableStateOf(Shifts.mondayOf(LocalDate.now())) }
    var calendarOn by remember(roomId) { mutableStateOf(ShiftCalendar.enabled(context, roomId)) }
    // Il calendario «Turni» si riscrive quando cambiano i turni effettivi.
    LaunchedEffect(eff?.entries, calendarOn) { if (calendarOn && model.loadingHistory[roomId] != true) model.syncShiftCalendar(roomId) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.loadingHistory[roomId] == true) item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) }
            if (plan == null || eff == null) {
                if (model.loadingHistory[roomId] != true) item {
                    Card {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.sh_empty_title), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.sh_empty_text), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                item { WeekCard(model, plan, eff, week, me, canEdit, onWeek = { week = it }) { s -> sheet = s } }
                item { NowLine(model, plan, eff) }
                // Richieste per me: scambi chiesti a me, cessioni a me o a chiunque.
                val forMe = eff.changes.filter { a ->
                    a.state == Shifts.State.Waiting && a.change.by != me && a.change.kind != Shifts.Kind.Extra &&
                        (a.change.to == me || (a.change.to == null && a.change.kind == Shifts.Kind.Give))
                }
                if (forMe.isNotEmpty()) item { SectionTitle(stringResource(R.string.sh_requests)) }
                forMe.forEach { a -> item(key = "r" + a.change.id) { RequestCard(model, actions, roomId, plan, eff, avail, a) } }
                val mine = eff.changes.filter { it.state == Shifts.State.Waiting && it.change.by == me }
                if (mine.isNotEmpty()) item { SectionTitle(stringResource(R.string.sh_my_requests)) }
                mine.forEach { a -> item(key = "m" + a.change.id) { RequestCard(model, actions, roomId, plan, eff, avail, a) } }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(R.string.sh_hours), selected = false) { sheet = ShiftSheet.Hours }
                        Chip(stringResource(R.string.sh_export), selected = false) { sheet = ShiftSheet.Export }
                        if (canEdit) Chip(stringResource(R.string.sh_import), selected = false) { sheet = ShiftSheet.Import(plan) }
                        if (canEdit) Chip(stringResource(R.string.sh_settings), selected = false) { sheet = ShiftSheet.Create }
                    }
                }
                item {
                    Card {
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.sh_in_calendar), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            val denied = stringResource(R.string.sh_calendar_denied)
                            Switch(checked = calendarOn, onCheckedChange = { on ->
                                if (!on) {
                                    ShiftCalendar.setEnabled(context, roomId, false); calendarOn = false
                                    kotlin.concurrent.thread { ShiftCalendar.clear(context, roomId) }
                                } else actions.askCalendar { ok ->
                                    if (ok) { ShiftCalendar.setEnabled(context, roomId, true); calendarOn = true } else actions.toast(denied)
                                }
                            })
                        }
                        Text(stringResource(R.string.sh_in_calendar_info), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
        // I comandi in basso, il principale sotto il pollice (a destra).
        Row(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan != null) Pill(stringResource(R.string.sh_availability), EuriseIcons.calendar(2.2f), Modifier, strong = !canEdit) { sheet = ShiftSheet.Avail }
            if (canEdit) Pill(stringResource(R.string.sh_make), EuriseIcons.plus(2.4f), Modifier, strong = true) { sheet = ShiftSheet.Create }
        }
    }
    when (val s = sheet) {
        null -> Unit
        ShiftSheet.Create -> CreateSheet(model, actions, roomId, plan, avail, onImport = { sheet = ShiftSheet.Import(it) }) { sheet = null }
        is ShiftSheet.Import -> ImportSheet(model, actions, roomId, s.base) { sheet = null }
        ShiftSheet.Export -> if (plan != null && eff != null) ExportSheet(model, actions, roomId, plan, eff) { sheet = null }
        ShiftSheet.Avail -> if (plan != null) AvailSheet(model, actions, roomId, plan, avail[me]) { sheet = null }
        ShiftSheet.Hours -> if (plan != null && eff != null) HoursSheet(model, actions, roomId, plan, eff, boss) { sheet = null }
        is ShiftSheet.Cell -> if (plan != null && eff != null) {
            val e = eff.entries.firstOrNull { it.key == s.key }
            if (e != null) CellSheet(model, actions, roomId, plan, eff, avail, e, canEdit) { sheet = null }
            else LaunchedEffect(s) { sheet = null }
        }
        is ShiftSheet.Add -> if (plan != null) AddSheet(model, actions, roomId, plan, s.date, s.person) { sheet = null }
    }
}

@Composable
private fun WeekCard(
    model: ChatModel, plan: Shifts.Plan, eff: Shifts.Effective, week: LocalDate, me: String, canEdit: Boolean,
    onWeek: (LocalDate) -> Unit, open: (ShiftSheet) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val days = (0..6).map { week.plusDays(it.toLong()) }
    val today = LocalDate.now()
    val people = (listOfNotNull(plan.people.firstOrNull { it.id == me }?.id) + plan.people.map { it.id } +
        eff.entries.filter { !it.date.isBefore(week) && it.date.isBefore(week.plusDays(7)) }.flatMap { it.people }).distinct()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.calendar(2.2f), null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sh_week, week.get(WeekFields.ISO.weekOfWeekBasedYear())), fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            RoundIcon(EuriseIcons.chevronLeft(2.2f), stringResource(R.string.sh_prev_week), flat = true) { onWeek(week.minusDays(7)) }
            RoundIcon(EuriseIcons.chevronRight(2.2f), stringResource(R.string.sh_next_week), flat = true) { onWeek(week.plusDays(7)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(48.dp))
            days.forEach { d ->
                Text(d.format(DAY).replaceFirstChar { it.uppercase() }, fontSize = 10.5.sp, textAlign = TextAlign.Center, maxLines = 1,
                    fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (d == today) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        people.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name(model, p), fontSize = 12.5.sp, fontWeight = if (p == me) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp))
                days.forEach { d ->
                    val here = eff.entries.filter { it.date == d && p in it.people }
                    val (bg, fg) = here.firstOrNull()?.let { colors(plan, it.type) } ?: (REST_BG to REST_FG)
                    val label = if (here.isEmpty()) "·" else here.joinToString("") { e ->
                        e.type + (if (e.phone == p && e.people.size > 1) "☎" else "") + (if (e.split != null) "½" else "")
                    }
                    Box(
                        Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(bg)
                            .then(if (d == today) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(role = Role.Button) {
                                val e = here.firstOrNull()
                                if (e != null) open(ShiftSheet.Cell(e.key)) else if (canEdit) open(ShiftSheet.Add(d, p))
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1) }
                }
            }
        }
        // Caselle scoperte: servono più persone di quante ce ne sono.
        val holes = days.map { d -> Shifts.typesOn(plan, d).sumOf { t -> (t.people - (eff.entries.firstOrNull { it.date == d && it.type == t.code }?.people?.size ?: 0)).coerceAtLeast(0) } }
        if (holes.any { it > 0 }) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sh_uncovered_row), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WARN, modifier = Modifier.width(48.dp), maxLines = 1)
            holes.forEach { n -> Text(if (n > 0) "$n" else "", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = WARN, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plan.types.forEach { t ->
                Text("${t.code} ${t.start.format(HM)}–${t.end.format(HM)}", fontSize = 11.sp, color = colors(plan, t.code).second, fontWeight = FontWeight.Bold)
            }
            Text("· " + stringResource(R.string.sh_rest), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowLine(model: ChatModel, plan: Shifts.Plan, eff: Shifts.Effective) {
    val (current, next) = Shifts.now(plan, eff.entries, LocalDateTime.now())
    val nowNames = current.map { s ->
        stringResource(R.string.sh_now_until, name(model, s.person), plan.type(s.entry.type)?.name?.lowercase(IT) ?: s.entry.type, s.end.format(HM))
    }
    val nextNames = next.map { it.person }.distinct().filter { p -> current.none { it.person == p } }.take(2).map { name(model, it) }
    val text = if (nowNames.isEmpty()) stringResource(R.string.sh_now_none) else nowNames.joinToString(", ")
    val then = if (nextNames.isEmpty()) "" else " · " + stringResource(R.string.sh_then, nextNames.joinToString(", "))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1F3E5C8A)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.clock(2.2f), null, tint = Color(0xFF3E5C8A), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text((if (nowNames.isEmpty()) "" else stringResource(R.string.sh_now) + " ") + text + then, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// --- Richieste: scambio o cessione, con il controllo delle regole (tavola ChatTurni) ---

@Composable
private fun RequestCard(
    model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, eff: Shifts.Effective,
    avail: Map<String, Shifts.Avail>, a: Shifts.Applied,
) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val c = a.change
    val failed = stringResource(R.string.ch_send_failed)
    val mine = eff.entries.firstOrNull { it.date == c.date && it.type == c.type }
    val theirs = if (c.kind == Shifts.Kind.Swap) eff.entries.firstOrNull { it.date == c.withDate && it.type == c.withType } else null
    // Il controllo per chi risponde (io): prendo il turno di chi chiede, lasciando il mio se è uno scambio.
    val problems = if (mine != null && c.by != me) Shifts.check(plan, eff.entries, avail[me], me, mine, c.by, theirs) else emptySet()
    val yesFmt = stringResource(R.string.sh_yes_body)
    val noFmt = stringResource(R.string.sh_no_body)
    val undoFmt = stringResource(R.string.sh_undo_body)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(if (c.kind == Shifts.Kind.Swap) R.string.sh_wants_swap else R.string.sh_wants_give, name(model, c.by)),
            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Text(when {
            c.kind == Shifts.Kind.Swap && c.withDate != null && c.withType != null ->
                stringResource(R.string.sh_swap_text, shiftName(plan, c.withDate, c.withType), shiftName(plan, c.date, c.type))
            c.to == null -> stringResource(R.string.sh_give_anyone, shiftName(plan, c.date, c.type))
            else -> stringResource(R.string.sh_give_text, shiftName(plan, c.date, c.type))
        }, fontSize = 14.sp, lineHeight = 20.sp)
        if (c.by == me) {
            Pill(stringResource(R.string.sh_cancel_request), EuriseIcons.undo(2.2f), Modifier, strong = false) {
                model.sendShift(roomId, Shifts.content(Shifts.Reply(c.id, me, false, undo = true), String.format(undoFmt, model.shiftName(me)))) { actions.toast(failed) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(if (c.to == null) R.string.sh_take else R.string.sh_accept), EuriseIcons.check(2.2f), Modifier, strong = problems.isEmpty()) {
                    model.sendShift(roomId, Shifts.content(Shifts.Reply(c.id, me, true), String.format(yesFmt, model.shiftName(me)))) { actions.toast(failed) }
                }
                if (c.to != null) Pill(stringResource(R.string.sh_refuse), EuriseIcons.close(2.2f), Modifier, strong = false) {
                    model.sendShift(roomId, Shifts.content(Shifts.Reply(c.id, me, false), String.format(noFmt, model.shiftName(me)))) { actions.toast(failed) }
                }
            }
            Text(if (problems.isEmpty()) stringResource(R.string.sh_checked_ok) else stringResource(R.string.sh_checked_bad, problemText(problems)),
                fontSize = 11.5.sp, lineHeight = 16.sp, color = if (problems.isEmpty()) scheme.onSurfaceVariant else WARN)
        }
    }
}

// --- Una casella: chi c'è, scambio, cessione, straordinario; per chi coordina, modifica ---

private enum class CellMode { Main, Swap, Give, Extra, Edit }

@Composable
private fun CellSheet(
    model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, eff: Shifts.Effective,
    avail: Map<String, Shifts.Avail>, e: Shifts.Entry, canEdit: Boolean, onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val t = plan.type(e.type)
    val failed = stringResource(R.string.ch_send_failed)
    val sent = stringResource(R.string.sh_sent)
    var mode by remember(e.key) { mutableStateOf(CellMode.Main) }
    val swapFmt = stringResource(R.string.sh_swap_body)
    val giveFmt = stringResource(R.string.sh_give_body)
    val giveAnyFmt = stringResource(R.string.sh_give_any_body)
    val extraFmt = stringResource(R.string.sh_extra_body)
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val (bg, fg) = colors(plan, e.type)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text(e.type, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.date.format(LONG_DAY).replaceFirstChar { it.uppercase() }, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    if (t != null) Text("${t.name} · ${t.start.format(HM)}–${t.end.format(HM)}", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
            when (mode) {
                CellMode.Main -> {
                    Card {
                        Shifts.slots(plan, e).forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name(model, s.person), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                val notes = listOfNotNull(
                                    if (e.split != null && e.people.size > 1) (if (i == 0) stringResource(R.string.sh_until, s.end.format(HM)) else stringResource(R.string.sh_from, s.start.format(HM))) else null,
                                    if (e.phone == s.person && e.people.size > 1) "☎ " + stringResource(R.string.sh_phone_holder) else null,
                                )
                                Text(notes.joinToString(" · "), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                    val future = !e.date.isBefore(LocalDate.now())
                    if (me in e.people) {
                        if (future) {
                            Pill(stringResource(R.string.sh_ask_swap), EuriseIcons.repeat(2.2f), Modifier.fillMaxWidth(), strong = true) { mode = CellMode.Swap }
                            Pill(stringResource(R.string.sh_ask_give), EuriseIcons.send(2.2f), Modifier.fillMaxWidth(), strong = false) { mode = CellMode.Give }
                        }
                        Pill(stringResource(R.string.sh_overtime), EuriseIcons.clock(2.2f), Modifier.fillMaxWidth(), strong = false) { mode = CellMode.Extra }
                    }
                    if (canEdit) Pill(stringResource(R.string.sh_edit), EuriseIcons.pen(2.2f), Modifier.fillMaxWidth(), strong = false) { mode = CellMode.Edit }
                }
                CellMode.Swap -> {
                    Text(stringResource(R.string.sh_pick_swap), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    val today = LocalDate.now()
                    val options = eff.entries.filter { o -> !o.date.isBefore(today) && o.date.isBefore(today.plusDays(28)) && me !in o.people }
                        .flatMap { o -> o.people.map { p -> o to p } }
                        .map { (o, p) ->
                            // Tutti e due devono restare nelle regole: io nel suo turno, lei nel mio.
                            val mineOk = Shifts.check(plan, eff.entries, avail[me], me, o, p, e)
                            val theirsOk = Shifts.check(plan, eff.entries, avail[p], p, e, me, o)
                            Triple(o, p, mineOk + theirsOk)
                        }.sortedWith(compareBy<Triple<Shifts.Entry, String, Set<Shifts.Problem>>> { it.third.isNotEmpty() }.thenBy { it.first.date })
                    if (options.isEmpty()) Text(stringResource(R.string.sh_no_swaps), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    Card {
                        options.take(30).forEachIndexed { i, (o, p, probs) ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                                val c = Shifts.Change(UUID.randomUUID().toString(), Shifts.Kind.Swap, me, e.date, e.type, to = p, withDate = o.date, withType = o.type)
                                model.sendShift(roomId, Shifts.content(c, String.format(swapFmt, model.shiftName(me), model.shiftName(p),
                                    "${e.date.format(DAY)} ${e.type}", "${o.date.format(DAY)} ${o.type}"))) { actions.toast(failed) }
                                actions.toast(sent); onClose()
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(colors(plan, o.type).first), contentAlignment = Alignment.Center) {
                                    Text(o.type, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors(plan, o.type).second)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${o.date.format(DAY).replaceFirstChar { it.uppercase() }} · ${model.shiftName(p)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(if (probs.isEmpty()) stringResource(R.string.sh_checked_ok) else stringResource(R.string.sh_checked_bad, problemText(probs)),
                                        fontSize = 11.5.sp, lineHeight = 15.sp, color = if (probs.isEmpty()) OK else WARN, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                CellMode.Give -> {
                    Text(stringResource(R.string.sh_pick_give), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    val label = "${e.date.format(DAY)} ${e.type}"
                    Pill(stringResource(R.string.sh_anyone), EuriseIcons.send(2.2f), Modifier.fillMaxWidth(), strong = true) {
                        val c = Shifts.Change(UUID.randomUUID().toString(), Shifts.Kind.Give, me, e.date, e.type)
                        model.sendShift(roomId, Shifts.content(c, String.format(giveAnyFmt, model.shiftName(me), label))) { actions.toast(failed) }
                        actions.toast(sent); onClose()
                    }
                    Card {
                        val others = (plan.people.map { it.id } + model.members[roomId].orEmpty()).distinct().filter { it != me && it !in e.people }
                        others.forEachIndexed { i, p ->
                            if (i > 0) Divider()
                            val probs = Shifts.check(plan, eff.entries, avail[p], p, e, me)
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                                val c = Shifts.Change(UUID.randomUUID().toString(), Shifts.Kind.Give, me, e.date, e.type, to = p)
                                model.sendShift(roomId, Shifts.content(c, String.format(giveFmt, model.shiftName(me), model.shiftName(p), label))) { actions.toast(failed) }
                                actions.toast(sent); onClose()
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val role = plan.person(p)?.role
                                    Text(model.shiftName(p) + if (role == Shifts.Role.External) " · " + stringResource(R.string.sh_role_ext) else "", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(if (probs.isEmpty()) stringResource(R.string.sh_checked_ok) else stringResource(R.string.sh_checked_bad, problemText(probs)),
                                        fontSize = 11.5.sp, lineHeight = 15.sp, color = if (probs.isEmpty()) OK else WARN, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                CellMode.Extra -> {
                    var minutes by remember { mutableStateOf(15) }
                    val reasons = listOf(R.string.sh_extra_r1, R.string.sh_extra_r2, R.string.sh_extra_r3, R.string.sh_extra_r4).map { stringResource(it) }
                    var reason by remember { mutableStateOf(reasons[0]) }
                    Text(stringResource(R.string.sh_extra_title), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundIcon(EuriseIcons.minus(2.4f), "−15") { minutes = (minutes - 15).coerceAtLeast(15) }
                        Text(Shifts.duration(minutes.toLong()), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center)
                        RoundIcon(EuriseIcons.plus(2.4f), "+15") { minutes = (minutes + 15).coerceAtMost(12 * 60) }
                    }
                    Text(stringResource(R.string.sh_extra_why), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { r -> Chip(r, selected = reason == r) { reason = r } }
                    }
                    Pill(stringResource(R.string.sh_extra_save), EuriseIcons.check(2.2f), Modifier.fillMaxWidth(), strong = true) {
                        val c = Shifts.Change(UUID.randomUUID().toString(), Shifts.Kind.Extra, me, e.date, e.type, minutes = minutes, reason = reason)
                        model.sendShift(roomId, Shifts.content(c, String.format(extraFmt, model.shiftName(me), Shifts.duration(minutes.toLong()), e.date.format(DAY), reason))) { actions.toast(failed) }
                        onClose()
                    }
                }
                CellMode.Edit -> EditEntry(model, actions, roomId, plan, plan.entries.firstOrNull { it.key == e.key } ?: e, onClose)
            }
        }
    }
}

/** Chi coordina corregge una casella a mano: persone (anche reperibili esterni), telefono, turno diviso, fissato. */
@Composable
private fun EditEntry(model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, e: Shifts.Entry, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val t = plan.type(e.type)
    val people = remember(e.key) { mutableStateListOf<String>().apply { addAll(e.people) } }
    var phone by remember(e.key) { mutableStateOf(e.phone) }
    var split by remember(e.key) { mutableStateOf(e.split) }
    var locked by remember(e.key) { mutableStateOf(true) }
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.sh_plan_body)
    Text(stringResource(R.string.sh_cell_people), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (plan.people.map { it.id } + model.members[roomId].orEmpty()).distinct().forEach { p ->
            Chip(model.shiftName(p), selected = p in people) {
                if (p in people) people.remove(p) else if (people.size < 3) people.add(p)
                if (phone !in people) phone = people.firstOrNull()
            }
        }
    }
    if (people.size > 1) {
        Text(stringResource(R.string.sh_who_phone), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            people.forEach { p -> Chip(model.shiftName(p), selected = phone == p) { phone = p } }
        }
        if (t != null) {
            Text(stringResource(R.string.sh_split), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            // Gli orari del cambio: ogni ora dentro il turno (es. sabato 10–14 e 14–20, festivi 8–13 e 13–20).
            val hours = (1 until t.minutes / 60).map { t.start.plusHours(it.toLong()) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.sh_split_none), selected = split == null) { split = null }
                hours.forEach { h -> Chip(h.format(HM), selected = split == h) { split = h } }
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sh_locked), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = locked, onCheckedChange = { locked = it })
    }
    fun save(entries: List<Shifts.Entry>) {
        val p = plan.copy(entries = entries)
        model.sendShift(roomId, Shifts.content(p, String.format(bodyFmt, model.shiftName(model.session?.userId.orEmpty()), entries.size))) { actions.toast(failed) }
        onClose()
    }
    Pill(stringResource(R.string.sh_save), EuriseIcons.check(2.2f), Modifier.fillMaxWidth(), strong = people.isNotEmpty()) {
        if (people.isEmpty()) return@Pill
        val n = e.copy(people = people.toList(), phone = phone?.takeIf { people.size > 1 && it in people }, split = split?.takeIf { people.size > 1 }, locked = locked)
        save(plan.entries.filter { it.key != e.key } + n)
    }
    Pill(stringResource(R.string.sh_remove), EuriseIcons.trash(2.2f), Modifier.fillMaxWidth(), strong = false) {
        save(plan.entries.filter { it.key != e.key })
    }
}

/** Chi coordina tocca una casella vuota: sceglie il turno del giorno in cui mettere quella persona. */
@Composable
private fun AddSheet(model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, date: LocalDate, person: String, onClose: () -> Unit) {
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.sh_plan_body)
    Sheet(onClose) {
        Text(date.format(LONG_DAY).replaceFirstChar { it.uppercase() } + " · " + name(model, person), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.sh_add_here), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val types = Shifts.typesOn(plan, date).ifEmpty { plan.types }
        types.forEach { t ->
            Pill("${t.code} · ${t.name} ${t.start.format(HM)}–${t.end.format(HM)}", EuriseIcons.plus(2.4f), Modifier.fillMaxWidth(), strong = false) {
                val old = plan.entries.firstOrNull { it.date == date && it.type == t.code }
                val n = (old ?: Shifts.Entry(date, t.code, emptyList())).let { it.copy(people = (it.people + person).distinct().take(3), locked = true) }
                val entries = plan.entries.filter { it.key != n.key } + n
                model.sendShift(roomId, Shifts.content(plan.copy(entries = entries), String.format(bodyFmt, model.shiftName(model.session?.userId.orEmpty()), entries.size))) { actions.toast(failed) }
                onClose()
            }
        }
    }
}

// --- Le mie disponibilità e preferenze ---

@Composable
private fun AvailSheet(model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, current: Shifts.Avail?, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val start = Shifts.mondayOf(LocalDate.now())
    val days = (0 until 42).map { start.plusDays(it.toLong()) }
    val off = remember { mutableStateListOf<LocalDate>().apply { addAll(days.filter { d -> current?.off?.any { it.types.isEmpty() && it.covers(d, "") } == true }) } }
    val prefer = remember { mutableStateListOf<String>().apply { addAll(current?.prefer.orEmpty()) } }
    val avoid = remember { mutableStateListOf<String>().apply { addAll(current?.avoid.orEmpty()) } }
    val avoidDays = remember { mutableStateListOf<Int>().apply { addAll(current?.avoidDays.orEmpty()) } }
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.sh_avail_body)
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.sh_avail_title), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.sh_avail_days), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEach { Text(it, fontSize = 11.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            days.chunked(7).forEach { w ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    w.forEach { d ->
                        val on = d in off
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) WARN.copy(alpha = 0.18f) else scheme.surface)
                                .clickable(role = Role.Checkbox) { if (on) off.remove(d) else off.add(d) }
                                .semantics { contentDescription = d.format(LONG_DAY) },
                            contentAlignment = Alignment.Center,
                        ) { Text("${d.dayOfMonth}", fontSize = 13.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) WARN else scheme.onSurface) }
                    }
                }
            }
            Text(stringResource(R.string.sh_prefer), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.types.forEach { t -> Chip(t.name, selected = t.code in prefer) { if (t.code in prefer) prefer.remove(t.code) else { prefer.add(t.code); avoid.remove(t.code) } } }
            }
            Text(stringResource(R.string.sh_avoid), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.types.forEach { t -> Chip(t.name, selected = t.code in avoid) { if (t.code in avoid) avoid.remove(t.code) else { avoid.add(t.code); prefer.remove(t.code) } } }
            }
            Text(stringResource(R.string.sh_avoid_days), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { k ->
                    val label = java.time.DayOfWeek.of(k).getDisplayName(java.time.format.TextStyle.SHORT, IT)
                    Chip(label, selected = k in avoidDays) { if (k in avoidDays) avoidDays.remove(k) else avoidDays.add(k) }
                }
            }
            Text(stringResource(R.string.sh_avail_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            Pill(stringResource(R.string.sh_save), EuriseIcons.check(2.2f), Modifier.fillMaxWidth(), strong = true) {
                // I giorni segnati diventano periodi; restano quelli fuori dalle sei settimane e quelli solo per certi turni.
                val ranges = ArrayList<Shifts.Off>()
                off.sorted().forEach { d ->
                    val last = ranges.lastOrNull()
                    if (last != null && last.to.plusDays(1) == d) ranges[ranges.size - 1] = last.copy(to = d) else ranges += Shifts.Off(d, d)
                }
                val kept = current?.off.orEmpty().filter { it.types.isNotEmpty() || it.to.isBefore(start) || it.from.isAfter(days.last()) }
                val a = Shifts.Avail(me, kept + ranges, prefer.toSet(), avoid.toSet(), avoidDays.toSet())
                model.sendShift(roomId, Shifts.content(a, String.format(bodyFmt, model.shiftName(me)))) { actions.toast(failed) }
                onClose()
            }
        }
    }
}

// --- Fai i turni (tavola ChatTurniCrea): chi, che turni, come girano ---

private enum class How { Generate, Repeat, File, Manual }

private class TypeDraft(code: String, name: String, start: String, end: String, people: Int, days: Set<Int>) {
    var code by mutableStateOf(code); var name by mutableStateOf(name)
    var start by mutableStateOf(start); var end by mutableStateOf(end)
    var people by mutableStateOf(people); var days by mutableStateOf(days)
    fun build(): Shifts.Type? {
        val s = parseTime(start) ?: return null
        val e = parseTime(end) ?: return null
        val c = code.trim().uppercase().take(3).ifEmpty { return null }
        return Shifts.Type(c, name.trim().ifEmpty { c }, s, e, people.coerceIn(1, 3), days.ifEmpty { (1..7).toSet() })
    }
    companion object {
        fun of(t: Shifts.Type) = TypeDraft(t.code, t.name, t.start.format(HM), t.end.format(HM), t.people, t.days)
    }
}

private class PersonDraft(val id: String, role: Shifts.Role, target: String, replaces: String?, from: String, to: String) {
    var role by mutableStateOf(role); var target by mutableStateOf(target)
    var replaces by mutableStateOf(replaces); var from by mutableStateOf(from); var to by mutableStateOf(to)
    fun build(): Shifts.Person = Shifts.Person(id, role, (target.replace(',', '.').toDoubleOrNull()?.times(60))?.toInt() ?: 0,
        replaces.takeIf { role == Shifts.Role.Substitute }, parseDay(from), parseDay(to))
}

private val DM = DateTimeFormatter.ofPattern("dd/MM")

private fun parseTime(s: String): LocalTime? {
    val t = s.trim().replace('.', ':')
    val parts = t.split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}

/** «24/12» → la data più vicina con quel giorno (quest'anno, o il prossimo se è passata da più di sei mesi). */
private fun parseDay(s: String): LocalDate? {
    val p = s.trim().split('/', '-', '.').mapNotNull { it.toIntOrNull() }
    if (p.size < 2) return null
    val today = LocalDate.now()
    return runCatching {
        val d = LocalDate.of(if (p.size >= 3) (if (p[2] < 100) 2000 + p[2] else p[2]) else today.year, p[1], p[0])
        if (p.size < 3 && d.isBefore(today.minusMonths(6))) d.plusYears(1) else d
    }.getOrNull()
}

@Composable
private fun CreateSheet(
    model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan?, avail: Map<String, Shifts.Avail>,
    onImport: (Shifts.Plan) -> Unit, onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val scope = rememberCoroutineScope()
    val members = model.members[roomId].orEmpty().ifEmpty { listOf(me) }
    val people = remember {
        mutableStateListOf<PersonDraft>().apply {
            val ids = (plan?.people?.map { it.id }.orEmpty() + members).distinct()
            ids.forEach { id ->
                val p = plan?.person(id)
                add(PersonDraft(id, p?.role ?: Shifts.Role.Holder, p?.target?.takeIf { it > 0 }?.let { (it / 60).toString() } ?: "",
                    p?.replaces, p?.from?.format(DM).orEmpty(), p?.to?.format(DM).orEmpty()))
            }
        }
    }
    // Chi è nel gruppo ma non ha mai avuto turni parte escluso solo se il piano esiste già (lo si riaccende toccandolo).
    val included = remember { mutableStateListOf<String>().apply { addAll(plan?.people?.map { it.id } ?: members) } }
    val types = remember { mutableStateListOf<TypeDraft>().apply { addAll((plan?.types ?: Shifts.ward()).map(TypeDraft::of)) } }
    var holidays by remember { mutableStateOf(plan?.holidays ?: false) }
    var how by remember { mutableStateOf(How.Generate) }
    var weeks by remember { mutableStateOf(4) }
    val thisWeek = Shifts.mondayOf(LocalDate.now())
    var start by remember { mutableStateOf(if (plan == null) thisWeek else thisWeek.plusDays(7)) }
    var reminder by remember { mutableStateOf(plan?.reminder ?: true) }
    var working by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(plan?.title ?: model.room(roomId)?.name.orEmpty()) }
    var rest by remember { mutableStateOf(plan?.rest ?: Shifts.REST_MINUTES) }
    var inARow by remember { mutableStateOf(plan?.maxInARow ?: Shifts.MAX_IN_A_ROW) }
    var newPerson by remember { mutableStateOf("") }
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.sh_plan_body)
    val uncoveredFmt = stringResource(R.string.sh_uncovered)
    val badTypes = stringResource(R.string.sh_bad_types)
    fun build(): Shifts.Plan? {
        val ts = types.map { it.build() ?: return null }.distinctBy { it.code }
        if (ts.isEmpty()) return null
        val ps = people.filter { it.id in included }.map { it.build() }
        return Shifts.Plan(plan?.id ?: UUID.randomUUID().toString(), ts, ps,
            plan?.entries.orEmpty().filter { e -> ts.any { it.code == e.type } }, rest = rest, holidays = holidays, reminder = reminder,
            maxInARow = inARow, title = title.trim())
    }
    fun publish(p: Shifts.Plan) {
        // Il piano resta piccolo: le caselle più vecchie di quattro mesi escono (restano nei messaggi già mandati).
        val trimmed = p.copy(entries = p.entries.filter { !it.date.isBefore(LocalDate.now().minusDays(120)) })
        model.sendShift(roomId, Shifts.content(trimmed, String.format(bodyFmt, model.shiftName(me), trimmed.entries.size))) { actions.toast(failed) }
    }
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.sh_create_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.sh_create_hint), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            // 1 · Chi: ruolo (tocca per cambiarlo), monte ore, per i sostituti chi sostituiscono e quando.
            Text(stringResource(R.string.sh_step_who), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Card {
                people.forEachIndexed { i, p ->
                    if (i > 0) Divider()
                    val on = p.id in included
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.1f))
                                    .clickable(role = Role.Checkbox) { if (on) included.remove(p.id) else included.add(p.id) },
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(EuriseIcons.check(2.6f), null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Text(name(model, p.id), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (on) Chip(stringResource(when (p.role) {
                                Shifts.Role.Holder -> R.string.sh_role_holder
                                Shifts.Role.Substitute -> R.string.sh_role_sub
                                Shifts.Role.External -> R.string.sh_role_ext
                            }), selected = p.role != Shifts.Role.Holder) {
                                p.role = Shifts.Role.entries[(p.role.ordinal + 1) % Shifts.Role.entries.size]
                            }
                        }
                        if (on && p.role != Shifts.Role.External) Field(stringResource(R.string.sh_target), p.target, KeyboardType.Number) { p.target = it }
                        if (on && p.role == Shifts.Role.Substitute) {
                            Text(stringResource(R.string.sh_replaces), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                people.filter { it.id != p.id && it.role == Shifts.Role.Holder && it.id in included }.forEach { h ->
                                    Chip(model.shiftName(h.id), selected = p.replaces == h.id) { p.replaces = if (p.replaces == h.id) null else h.id }
                                }
                            }
                            if (p.replaces != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_period_from), p.from, KeyboardType.Text) { p.from = it } }
                                Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_period_to), p.to, KeyboardType.Text) { p.to = it } }
                            }
                        }
                    }
                }
            }
            // Chi fa i turni ma non usa la Chat (volontari, colleghi di un altro servizio): basta il nome.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_add_person), newPerson, KeyboardType.Text) { newPerson = it } }
                RoundIcon(EuriseIcons.plus(2.4f), stringResource(R.string.sh_add_person)) {
                    val n = newPerson.trim()
                    if (n.isNotEmpty()) {
                        val id = ShiftsIO.EXTERNAL + n
                        if (people.none { it.id == id }) people.add(PersonDraft(id, Shifts.Role.Holder, "", null, "", ""))
                        if (id !in included) included.add(id)
                        newPerson = ""
                    }
                }
            }
            // 2 · Che turni: modelli pronti per contesti diversi, poi sigla, nome, orari, quante persone e in che giorni.
            Text(stringResource(R.string.sh_step_types), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Shifts.Preset.Ward to R.string.sh_preset_ward, Shifts.Preset.TwelveHours to R.string.sh_preset_12,
                    Shifts.Preset.Shop to R.string.sh_preset_shop, Shifts.Preset.Continuity to R.string.sh_preset_ca,
                    Shifts.Preset.Blank to R.string.sh_preset_blank,
                ).forEach { (p, label) ->
                    Chip(stringResource(label), selected = false) {
                        types.clear(); types.addAll(Shifts.preset(p).map(TypeDraft::of)); holidays = p == Shifts.Preset.Continuity
                    }
                }
            }
            types.forEachIndexed { i, t ->
                val (bg, fg) = TYPE_COLORS[i % TYPE_COLORS.size]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg.copy(alpha = 0.55f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(64.dp)) { Field(stringResource(R.string.sh_type_code), t.code, KeyboardType.Text) { t.code = it } }
                        Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_type_name), t.name, KeyboardType.Text) { t.name = it } }
                        RoundIcon(EuriseIcons.trash(2.2f), stringResource(R.string.sh_remove), flat = true) { types.remove(t) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_type_start), t.start, KeyboardType.Text) { t.start = it } }
                        Box(Modifier.weight(1f)) { Field(stringResource(R.string.sh_type_end), t.end, KeyboardType.Text) { t.end = it } }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.sh_type_people), fontSize = 10.5.sp, color = scheme.onSurfaceVariant)
                            Chip("${t.people}", selected = t.people > 1) { t.people = t.people % 3 + 1 }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("L", "M", "M", "G", "V", "S", "D").forEachIndexed { k, l ->
                            val d = k + 1
                            val on = d in t.days
                            Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (on) fg else Color.White.copy(alpha = 0.6f))
                                .clickable(role = Role.Checkbox) { t.days = if (on) t.days - d else t.days + d }, contentAlignment = Alignment.Center) {
                                Text(l, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else fg)
                            }
                        }
                    }
                }
            }
            Chip("+ " + stringResource(R.string.sh_type_add), selected = false) { types.add(TypeDraft("", "", "8:00", "14:00", 1, (1..7).toSet())) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_holidays), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Switch(checked = holidays, onCheckedChange = { holidays = it })
            }
            // Regole: riposo minimo tra due turni e giorni di fila al massimo (cambiano da un contesto all'altro).
            Text(stringResource(R.string.sh_rules), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 8, 11, 12).forEach { h -> Chip(if (h == 0) stringResource(R.string.sh_rest_none) else stringResource(R.string.sh_rest_h, h), selected = rest == h * 60) { rest = h * 60 } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 6, 7, 14).forEach { n -> Chip(stringResource(R.string.sh_in_a_row, n), selected = inARow == n) { inARow = n } }
            }
            Field(stringResource(R.string.sh_doc_title), title, KeyboardType.Text) { title = it }
            // 3 · Come girano.
            Text(stringResource(R.string.sh_step_how), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.sh_how_generate), selected = how == How.Generate) { how = How.Generate }
                if (plan?.entries?.isNotEmpty() == true) Chip(stringResource(R.string.sh_how_repeat), selected = how == How.Repeat) { how = How.Repeat }
                Chip(stringResource(R.string.sh_how_file), selected = how == How.File) { how = How.File }
                Chip(stringResource(R.string.sh_how_manual), selected = how == How.Manual) { how = How.Manual }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.sh_from_this), selected = start == thisWeek) { start = thisWeek }
                Chip(stringResource(R.string.sh_from_next), selected = start == thisWeek.plusDays(7)) { start = thisWeek.plusDays(7) }
                listOf(1, 2, 4, 8).forEach { w -> Chip(stringResource(R.string.sh_weeks, w), selected = weeks == w) { weeks = w } }
            }
            Text(stringResource(R.string.sh_how_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_reminder), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Switch(checked = reminder, onCheckedChange = { reminder = it })
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            Pill(if (how == How.File) stringResource(R.string.sh_import_go_file) else stringResource(R.string.sh_create_go, weeks),
                EuriseIcons.calendar(2.2f), Modifier.fillMaxWidth(), strong = !working) {
                if (working) return@Pill
                val base = build() ?: run { actions.toast(badTypes); return@Pill }
                // Da un file: le impostazioni vanno con l'importazione, nello stesso messaggio.
                if (how == How.File) { onImport(base); return@Pill }
                working = true
                scope.launch {
                    // «Genera» può richiedere qualche istante con molte persone: fuori dal thread principale.
                    val (entries, holes) = withContext(Dispatchers.Default) {
                        when (how) {
                            How.Generate -> Shifts.generate(base, avail, start, weeks * 7).let { it.entries to it.uncovered.size }
                            How.Repeat -> Shifts.repeatLastWeek(base, start, weeks) to 0
                            How.Manual, How.File -> base.entries to 0
                        }
                    }
                    publish(base.copy(entries = entries))
                    if (holes > 0) actions.toast(String.format(uncoveredFmt, holes))
                    working = false
                    onClose()
                }
            }
            if (plan != null) Pill(stringResource(R.string.sh_save_settings), EuriseIcons.check(2.2f), Modifier.fillMaxWidth(), strong = false) {
                val base = build() ?: run { actions.toast(badTypes); return@Pill }
                publish(base); onClose()
            }
        }
    }
}

// --- Ore del mese, cambi, turni effettivi da condividere ---

@Composable
private fun HoursSheet(model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, eff: Shifts.Effective, boss: String?, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val hours = Shifts.hours(plan, eff, month)
    val failed = stringResource(R.string.ch_send_failed)
    val undoFmt = stringResource(R.string.sh_undo_body)
    val headFmt = stringResource(R.string.sh_share_head)
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_hours_title) + " · " + month.format(MONTH), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundIcon(EuriseIcons.chevronLeft(2.2f), "−", flat = true) { month = month.minusMonths(1) }
                RoundIcon(EuriseIcons.chevronRight(2.2f), "+", flat = true) { month = month.plusMonths(1) }
            }
            Card {
                hours.forEachIndexed { i, h ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row {
                            Text(name(model, h.person), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.sh_hours_count, h.shifts, h.nights), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.sh_hours_line, Shifts.duration(h.worked), Shifts.duration(h.planned)), fontSize = 12.5.sp)
                        if (h.extra > 0) Text(stringResource(R.string.sh_hours_extra, Shifts.duration(h.extra)), fontSize = 12.5.sp, color = WARN)
                        if (h.target > 0) {
                            Text(stringResource(R.string.sh_hours_target, Shifts.duration(h.target.toLong())), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.08f))) {
                                Box(Modifier.fillMaxWidth(((h.worked + h.extra).toFloat() / h.target).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.primary))
                            }
                        }
                    }
                }
            }
            SectionTitle(stringResource(R.string.sh_changes))
            val changes = eff.changes.filter { YearMonth.from(it.change.date) == month }
            if (changes.isEmpty()) Text(stringResource(R.string.sh_no_changes), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            changes.forEach { a ->
                val c = a.change
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (c.kind) {
                            Shifts.Kind.Swap -> "${name(model, c.by)} ⇄ ${name(model, c.to ?: "")} · ${c.date.format(DAY)} ${c.type} / ${c.withDate?.format(DAY)} ${c.withType}"
                            Shifts.Kind.Give -> "${name(model, c.by)} → ${a.taker?.let { name(model, it) } ?: "?"} · ${c.date.format(DAY)} ${c.type}"
                            Shifts.Kind.Extra -> "${name(model, c.by)} · +${Shifts.duration(c.minutes.toLong())} · ${c.date.format(DAY)} ${c.type} (${c.reason})"
                        }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(when (a.state) {
                            Shifts.State.Waiting -> R.string.sh_waiting
                            Shifts.State.Accepted -> R.string.sh_accepted
                            Shifts.State.Refused -> R.string.sh_refused
                            Shifts.State.Undone -> R.string.sh_undone
                            Shifts.State.Stale -> R.string.sh_stale
                        }), fontSize = 11.5.sp, color = if (a.state == Shifts.State.Accepted) OK else scheme.onSurfaceVariant)
                    }
                    // Un cambio si annulla con un tocco: chi l'ha chiesto, chi l'ha preso o chi coordina.
                    if (a.state == Shifts.State.Accepted && (c.by == me || a.taker == me || boss == me)) {
                        Chip(stringResource(R.string.sh_undo), selected = false) {
                            model.sendShift(roomId, Shifts.content(Shifts.Reply(c.id, me, false, undo = true), String.format(undoFmt, model.shiftName(me)))) { actions.toast(failed) }
                        }
                    }
                }
            }
            val group = model.room(roomId)?.name.orEmpty()
            Pill(stringResource(R.string.sh_share_effective), EuriseIcons.share(2.2f), Modifier.fillMaxWidth(), strong = true) {
                // Per chi non usa la Chat (o per l'ufficio): i turni svolti, giorno per giorno, con gli straordinari.
                val lines = eff.entries.filter { YearMonth.from(it.date) == month }.sortedWith(compareBy<Shifts.Entry> { it.date }.thenBy { plan.type(it.type)?.start })
                    .map { e ->
                        val t = plan.type(e.type)
                        val who = Shifts.slots(plan, e).joinToString(", ") { s ->
                            model.shiftName(s.person) + (if (e.split != null) " ${s.start.format(HM)}–${s.end.format(HM)}" else "") +
                                eff.extras.filter { it.date == e.date && it.type == e.type && it.by == s.person }.joinToString("") { " +" + Shifts.duration(it.minutes.toLong()) }
                        }
                        "${e.date.format(DAY)} · ${e.type}${t?.let { " ${it.start.format(HM)}–${it.end.format(HM)}" } ?: ""} · $who"
                    }
                val totals = hours.joinToString("\n") { h -> "${model.shiftName(h.person)}: ${Shifts.duration(h.worked)}" + if (h.extra > 0) " + ${Shifts.duration(h.extra)}" else "" }
                actions.shareText(String.format(headFmt, group, month.format(MONTH)) + "\n\n" + lines.joinToString("\n") + "\n\n" + totals)
            }
        }
    }
}

// --- Nella conversazione ---

@Composable
internal fun ShiftBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val json = e.data?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
    if (json?.optString("msgtype") == Shifts.CHANGE) {
        val c = Shifts.change(json, e.sender, e.time)
        val plan = model.shiftPlan(roomId)
        val eff = model.shiftEffective(roomId)
        val a = eff?.changes?.firstOrNull { it.change.id == c?.id }
        if (c != null && plan != null && eff != null && a != null && c.kind != Shifts.Kind.Extra && a.state == Shifts.State.Waiting &&
            (c.by == model.session?.userId || c.to == model.session?.userId || c.to == null)) {
            Box(Modifier.padding(vertical = 3.dp)) { RequestCard(model, actions, roomId, plan, eff, model.shiftAvail(roomId), a) }
            return
        }
    }
    // Il resto (piano aggiornato, disponibilità, risposte, cambi chiusi): una riga al centro.
    val text = if (json?.optString("msgtype") == Shifts.PLAN) stringResource(R.string.sh_line_plan, model.shiftName(e.sender)) else e.text
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(EuriseIcons.calendar(2.2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

// --- Importa: PDF, foglio di calcolo o testo incollato, con anteprima e abbinamento dei nomi ---

@Composable
private fun ImportSheet(model: ChatModel, actions: ChatActions, roomId: String, base: Shifts.Plan, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var table by remember { mutableStateOf<ShiftsIO.Table?>(null) }
    var pasted by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf(true) }
    val aliases = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(model.shiftAliases(roomId, codes = false)) } }
    val codeAliases = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(model.shiftAliases(roomId, codes = true)) } }
    val unreadable = stringResource(R.string.sh_import_unreadable)
    val failed = stringResource(R.string.ch_send_failed)
    val bodyFmt = stringResource(R.string.sh_plan_body)
    val names = model.shiftNames(roomId, base)
    val result = table?.let { ShiftsIO.read(it, ShiftsIO.Matcher(base, names, aliases.toMap(), codeAliases.toMap()), month) }
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.sh_import_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.sh_import_hint), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_import_month, month.format(MONTH)), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundIcon(EuriseIcons.chevronLeft(2.2f), "−", flat = true) { month = month.minusMonths(1) }
                RoundIcon(EuriseIcons.chevronRight(2.2f), "+", flat = true) { month = month.plusMonths(1) }
            }
            Pill(stringResource(R.string.sh_import_file), EuriseIcons.attach(2.2f), Modifier.fillMaxWidth(), strong = table == null) {
                actions.pickFile(arrayOf("application/pdf", "text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.oasis.opendocument.spreadsheet", "application/octet-stream", "*/*")) { uri ->
                    if (uri != null) {
                        reading = true
                        model.readShiftFile(uri) { t -> reading = false; if (t == null || t.rows.isEmpty()) actions.toast(unreadable) else table = t }
                    }
                }
            }
            // Il testo incollato (da un'email, da WhatsApp, da una pagina) va su più righe: ogni riga conta.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.background).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.sh_import_paste), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                androidx.compose.foundation.text.BasicTextField(
                    value = pasted, onValueChange = { pasted = it; if (it.isNotBlank()) table = ShiftsIO.text(it) },
                    minLines = 3, maxLines = 8,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (result != null) {
                Card {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (result.entries.isEmpty()) Text(stringResource(R.string.sh_import_none), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = WARN)
                        else Text(stringResource(R.string.sh_import_found, result.entries.sumOf { it.people.size },
                            result.from!!.format(DAY), result.to!!.format(DAY)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(when (result.layout) {
                            ShiftsIO.Layout.PersonRows -> R.string.sh_layout_person
                            ShiftsIO.Layout.TypeColumns -> R.string.sh_layout_types
                            ShiftsIO.Layout.DayRows -> R.string.sh_layout_days
                            ShiftsIO.Layout.Nothing -> R.string.sh_layout_none
                        }) + if (result.rowsSkipped > 0) " · " + stringResource(R.string.sh_import_skipped, result.rowsSkipped) else "",
                            fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        // Anteprima: quanti turni a testa e di che tipo.
                        result.entries.flatMap { e -> e.people.map { it to e.type } }.groupBy({ it.first }, { it.second }).forEach { (p, codes) ->
                            Text("${name(model, p)} · " + codes.groupingBy { it }.eachCount().entries.joinToString(" ") { "${it.key} ${it.value}" },
                                fontSize = 13.sp)
                        }
                    }
                }
                // Nomi che non si riconoscono: a chi corrispondono? Una volta sola: l'abbinamento resta per il gruppo.
                if (result.unknownNames.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.sh_import_who))
                    val choices = (base.people.map { it.id } + model.members[roomId].orEmpty()).distinct()
                    result.unknownNames.take(20).forEach { n ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("«$n»", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                choices.forEach { id -> Chip(name(model, id), selected = false) { aliases[ShiftsIO.norm(n)] = id } }
                                Chip(stringResource(R.string.sh_import_new_person), selected = false) { aliases[ShiftsIO.norm(n)] = ShiftsIO.EXTERNAL + n.trim() }
                                Chip(stringResource(R.string.sh_import_ignore), selected = false) { aliases[ShiftsIO.norm(n)] = ShiftsIO.IGNORE }
                            }
                        }
                    }
                }
                if (result.unknownCodes.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.sh_import_codes))
                    result.unknownCodes.take(12).forEach { c ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("«$c»", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                base.types.forEach { t -> Chip("${t.code} · ${t.name}", selected = false) { codeAliases[ShiftsIO.norm(c)] = t.code } }
                                Chip(stringResource(R.string.sh_import_rest), selected = false) { codeAliases[ShiftsIO.norm(c)] = ShiftsIO.IGNORE }
                            }
                        }
                    }
                }
                if (result.entries.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(R.string.sh_import_replace), selected = replace) { replace = true }
                        Chip(stringResource(R.string.sh_import_add), selected = !replace) { replace = false }
                    }
                    Pill(stringResource(R.string.sh_import_go, result.entries.sumOf { it.people.size }), EuriseIcons.check(2.2f), Modifier.fillMaxWidth(), strong = true) {
                        model.saveShiftAliases(roomId, codes = false, aliases.toMap())
                        model.saveShiftAliases(roomId, codes = true, codeAliases.toMap())
                        // Chi entra con l'importazione e non c'era (anche senza Chat) si aggiunge al piano come reperibile.
                        val newcomers = result.entries.flatMap { it.people }.distinct().filter { id -> base.people.none { it.id == id } }
                            .map { Shifts.Person(it, Shifts.Role.External) }
                        val plan = base.copy(people = base.people + newcomers, entries = ShiftsIO.merge(base, result.entries, replace))
                        model.sendShift(roomId, Shifts.content(plan, String.format(bodyFmt, model.shiftName(me), plan.entries.size))) { actions.toast(failed) }
                        onClose()
                    }
                }
            }
        }
    }
}

// --- Esporta: prospetto e fogli presenze in PDF, foglio di calcolo, calendario, testo ---

@Composable
private fun ExportSheet(model: ChatModel, actions: ChatActions, roomId: String, plan: Shifts.Plan, eff: Shifts.Effective, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = model.session?.userId.orEmpty()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var effective by remember { mutableStateOf(true) }
    var onlyMine by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val group = model.room(roomId)?.name.orEmpty()
    val title = plan.title.ifBlank { group }
    val names = model.shiftNames(roomId, plan)
    val nameOf = { id: String -> names[id] ?: ShiftsIO.display(id) }
    val entries = if (effective) eff.entries else plan.entries
    val extras = if (effective) eff.extras else emptyList()
    val headFmt = stringResource(R.string.sh_share_head)
    val failedFile = stringResource(R.string.sh_export_failed)
    fun file(kind: String, ext: String) = java.io.File(java.io.File(context.cacheDir, "chat/turni").apply { mkdirs() },
        ShiftsIO.norm("$kind $title ${month.format(MONTH)}").replace(' ', '-').take(70) + ".$ext")
    fun export(kind: String, ext: String, mime: String, write: (java.io.File) -> Unit) {
        if (working) return
        working = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { file(kind, ext).also(write) }.getOrNull() }
            working = false
            if (f != null) actions.shareFile(f, mime) else actions.toast(failedFile)
        }
    }
    Sheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.sh_export_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(month.format(MONTH).replaceFirstChar { it.uppercase() }, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundIcon(EuriseIcons.chevronLeft(2.2f), "−", flat = true) { month = month.minusMonths(1) }
                RoundIcon(EuriseIcons.chevronRight(2.2f), "+", flat = true) { month = month.plusMonths(1) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.sh_export_effective), selected = effective) { effective = true }
                Chip(stringResource(R.string.sh_export_planned), selected = !effective) { effective = false }
            }
            Text(stringResource(R.string.sh_export_header, title), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            val people = ShiftsIO.people(plan, entries, month)
            Pill(stringResource(R.string.sh_export_grid_pdf), EuriseIcons.pdf(2.2f), Modifier.fillMaxWidth(), strong = true) {
                export("turni", "pdf", "application/pdf") { ShiftPdf.grid(it, title, plan, entries.filter { e -> YearMonth.from(e.date) == month }, month, people, nameOf, effective) }
            }
            Pill(stringResource(R.string.sh_export_presence_pdf), EuriseIcons.document(2.2f), Modifier.fillMaxWidth(), strong = false) {
                export("presenze", "pdf", "application/pdf") { f ->
                    ShiftPdf.presence(f, title, plan, entries, extras, month, if (onlyMine) listOf(me) else people, nameOf)
                }
            }
            Pill(stringResource(R.string.sh_export_grid_csv), EuriseIcons.table(2.2f), Modifier.fillMaxWidth(), strong = false) {
                export("prospetto", "csv", "text/csv") { it.writeText(ShiftsIO.csvGrid(plan, entries, month, nameOf)) }
            }
            Pill(stringResource(R.string.sh_export_list_csv), EuriseIcons.table(2.2f), Modifier.fillMaxWidth(), strong = false) {
                export("elenco", "csv", "text/csv") { it.writeText(ShiftsIO.csvList(plan, entries, extras, month, nameOf)) }
            }
            Pill(stringResource(R.string.sh_export_ics), EuriseIcons.calendar(2.2f), Modifier.fillMaxWidth(), strong = false) {
                export("calendario", "ics", "text/calendar") {
                    it.writeText(ShiftsIO.ics(plan, entries, month.atDay(1), group, nameOf, if (onlyMine) me else null,
                        java.time.ZoneId.systemDefault(), java.time.Instant.now()))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sh_export_only_mine), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Switch(checked = onlyMine, onCheckedChange = { onlyMine = it })
            }
            Pill(stringResource(R.string.sh_export_text), EuriseIcons.share(2.2f), Modifier.fillMaxWidth(), strong = false) {
                val lines = entries.filter { YearMonth.from(it.date) == month }.sortedWith(compareBy<Shifts.Entry> { it.date }.thenBy { plan.type(it.type)?.start })
                    .filter { !onlyMine || me in it.people }
                    .map { e -> "${e.date.format(DAY)} · ${e.type} · " + Shifts.slots(plan, e).joinToString(", ") { nameOf(it.person) } }
                actions.shareText(String.format(headFmt, title, month.format(MONTH)) + "\n\n" + lines.joinToString("\n"))
            }
            Text(stringResource(R.string.sh_export_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
    }
}
