@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)
private val RED = Color(0xFFA4502E)

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        action?.let { Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary, modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp)) }
    }
}

private fun dayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { d ->
    if (d.year == LocalDate.now().year) d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) else d.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
}

/** La scheda «Casa» del gruppo: scadenze e documenti, il menu della settimana, i farmaci di un genitore. */
@Composable
internal fun HomePane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val today = LocalDate.now()
    val failed = stringResource(R.string.ch_send_failed)
    val deadlines = model.deadlines(roomId)
    var adding by remember(roomId) { mutableStateOf(false) }
    var menuEdit by remember(roomId) { mutableStateOf(false) }
    var medsEdit by remember(roomId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Scadenze e documenti (tavole FamScadenze, FamDocumenti).
        Section(stringResource(R.string.fs_title), "+ " + stringResource(R.string.fs_add)) { adding = true }
        val shown = Casa.sorted(deadlines, today).filter { it.paidAt == 0L || System.currentTimeMillis() - it.paidAt < 7 * 86_400_000L }
        if (shown.isEmpty()) Text(stringResource(R.string.fs_empty), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        else Card {
            shown.forEachIndexed { i, d ->
                if (i > 0) Divider()
                val warn = Casa.warning(d, today)
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.title + if (d.cents > 0) " · " + Expenses.money(d.cents) else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(if (d.monthly) stringResource(R.string.fs_monthly, LocalDate.ofEpochDay(d.due).dayOfMonth) else dayLabel(d.due), fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold, color = if (d.paidAt > 0) GREEN else if (warn) AMBER else scheme.onSurfaceVariant)
                    }
                    val payer = d.payer.takeIf { it.isNotBlank() }?.let { if (it == me) stringResource(R.string.fa_you) else model.nameOf(it).substringBefore(' ') }
                    Text(listOfNotNull(d.source.takeIf { it.isNotBlank() }, payer?.let { stringResource(R.string.fs_pays, it) },
                        if (d.split) stringResource(R.string.fs_split) else null,
                        if (d.kind == Casa.Kind.Contratto) stringResource(R.string.fs_notice, if (d.notice > 0) d.notice else 60) else null,
                        if (d.paidAt > 0) stringResource(R.string.fs_paid) else null).joinToString(" · "), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    if (warn && d.kind == Casa.Kind.Documento) Text(stringResource(R.string.fs_doc_warn), fontSize = 11.5.sp, color = AMBER)
                    if (d.paidAt == 0L) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (d.kind != Casa.Kind.Documento && d.kind != Casa.Kind.Garanzia) Chip(stringResource(R.string.fs_pay), warn) { model.payDeadline(roomId, d.id) { actions.toast(failed) } }
                        Chip(stringResource(R.string.fs_task), false) { actions.toast(actions.text(if (model.remindDeadline(d, false, actions::launch)) R.string.fs_task_ok else R.string.fs_no_hub)) }
                        Chip(stringResource(R.string.fs_calendar), false) { model.remindDeadline(d, true, actions::launch) }
                        Chip("✕", false) { model.saveDeadlines(roomId, deadlines.filterNot { it.id == d.id }) { actions.toast(failed) } }
                    }
                }
            }
        }
        Text(stringResource(R.string.fs_note), fontSize = 11.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant)

        // Il menu della settimana (tavola FamCena).
        val menu = model.menu(roomId)
        Section(stringResource(R.string.fc_menu), stringResource(if (menu.isEmpty()) R.string.fc_menu_write else R.string.fc_menu_change)) { menuEdit = true }
        if (menu.isNotEmpty()) {
            Card {
                menu.forEachIndexed { i, d ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(DayOfWeek.of(d.day).getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                        Text(d.name, fontSize = 13.sp)
                    }
                }
            }
            val missing = Casa.missing(menu, model.lists(roomId).firstOrNull { it.name.equals(stringResource(R.string.el_spesa), true) })
            if (missing.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fc_missing, missing.size), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(missing.joinToString(", "), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
                Chip(stringResource(R.string.fc_to_list), true) { model.menuToShopping(roomId) { actions.toast(failed) } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fc_daily), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = model.dinnerDaily(roomId), onCheckedChange = { on -> if (on) model.askDinner(roomId, true) { actions.toast(failed) } else model.stopDinner(roomId) { actions.toast(failed) } })
        }

        // I farmaci di un genitore (tavola FamGenitori).
        val meds = model.meds(roomId)
        Section(stringResource(R.string.md_title), stringResource(if (meds == null) R.string.md_setup else R.string.md_change)) { medsEdit = true }
        if (meds != null) {
            Text(stringResource(R.string.md_of, meds.person), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Card {
                Casa.today(meds, today).forEachIndexed { i, (m, taken) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.time, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(taken?.let { stringResource(R.string.md_taken_at, eu.eurise.core.insieme.Disponibilita.label(it, java.time.ZoneId.systemDefault()).substringAfter(" · ")) }
                                ?: stringResource(R.string.md_not_yet), fontSize = 11.5.sp, color = if (taken != null) GREEN else scheme.onSurfaceVariant)
                        }
                        if (taken == null) Chip(stringResource(R.string.md_take), true) { model.takeMed(roomId, m.id) { actions.toast(failed) } }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Casa.week(meds, today) { d, t -> Programma.toMs(d.atTime(MedsWorker.time(Casa.Med("", "", t)))) }.forEach { (d, n, late) ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(when {
                        meds.meds.isEmpty() -> scheme.surface
                        n == meds.meds.size && !late -> GREEN.copy(alpha = 0.7f)
                        n == meds.meds.size -> AMBER.copy(alpha = 0.7f)
                        d == today -> scheme.surface
                        else -> RED.copy(alpha = 0.5f)
                    }), contentAlignment = Alignment.Center) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(stringResource(if (meds.phone.isNotBlank()) R.string.md_sms_note else R.string.md_app_note, meds.person), fontSize = 11.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.heightIn(min = 90.dp))
    }
    if (adding) DeadlineSheet(model, actions, roomId) { adding = false }
    if (menuEdit) MenuSheet(model, actions, roomId) { menuEdit = false }
    if (medsEdit) MedsSheet(model, actions, roomId) { medsEdit = false }
}

/** Una scadenza nuova: cosa, il tipo, quando, l'importo, chi paga, se si divide, il promemoria. */
@Composable
private fun DeadlineSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Casa.Kind.Bolletta) }
    var date by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var payer by remember { mutableStateOf(me) }
    var split by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(3) }
    val failed = stringResource(R.string.ch_send_failed)
    val members = model.members[roomId].orEmpty().ifEmpty { listOf(me) }
    Sheet(onClose) {
        Text(stringResource(R.string.fs_new), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(title, stringResource(R.string.fs_what)) { title = it.take(80) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Casa.Kind.entries.forEach { k -> Chip(stringResource(kindLabel(k)), kind == k) { kind = k } }
            }
            SmallField(date, stringResource(R.string.fs_when_hint)) { date = it.take(20) }
            if (kind != Casa.Kind.Documento && kind != Casa.Kind.Garanzia) SmallField(amount, stringResource(R.string.fs_amount)) { amount = it.take(12) }
            if (kind != Casa.Kind.Documento && kind != Casa.Kind.Garanzia) {
                Text(stringResource(R.string.fs_who_pays), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { members.forEach { u -> Chip(if (u == me) stringResource(R.string.fa_you) else model.nameOf(u).substringBefore(' '), payer == u) { payer = u } } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fs_split_ask), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = split, onCheckedChange = { split = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1, 3, 7).forEach { n -> Chip(stringResource(R.string.fs_before, n), remind == n) { remind = n } } }
            }
        }
        Pill(stringResource(R.string.fs_save), eu.eurise.core.designsystem.icon.EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            val due = parseDay(date) ?: return@Pill actions.toast(actions.text(R.string.fs_bad_date))
            if (title.isBlank()) return@Pill
            val d = Casa.Deadline("s" + System.nanoTime(), title.trim(), kind, due.toEpochDay(), Expenses.parse(amount) ?: 0L, payer, remind, split,
                monthly = kind == Casa.Kind.Rata, source = actions.text(R.string.fs_by_hand), by = me)
            model.saveDeadlines(roomId, model.deadlines(roomId) + d) { actions.toast(failed) }
            onClose()
        }
    }
}

private fun kindLabel(k: Casa.Kind): Int = when (k) {
    Casa.Kind.Bolletta -> R.string.fs_k_bill; Casa.Kind.Bollo -> R.string.fs_k_tax; Casa.Kind.Revisione -> R.string.fs_k_check
    Casa.Kind.Assicurazione -> R.string.fs_k_insurance; Casa.Kind.Rata -> R.string.fs_k_rate; Casa.Kind.Garanzia -> R.string.fs_k_warranty
    Casa.Kind.Contratto -> R.string.fs_k_contract; Casa.Kind.Documento -> R.string.fs_k_document; Casa.Kind.Altro -> R.string.fs_k_other
}

/** «5/12», «5/12/2026», «2027-03-15», «12 dic»: il giorno (il prossimo, se l'anno non c'è). */
internal fun parseDay(s: String, today: LocalDate = LocalDate.now()): LocalDate? {
    val t = s.trim().lowercase()
    runCatching { return LocalDate.parse(t) }
    Regex("^(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?$").matchEntire(t)?.let { m ->
        val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
        val d = runCatching { LocalDate.of(y ?: today.year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() ?: return null
        return if (y == null && d.isBefore(today)) d.plusYears(1) else d
    }
    eu.eurise.core.insieme.Scadenze.date(t, today)?.let { return it }
    return null
}

/** Il menu della settimana: un piatto per giorno, con gli ingredienti dopo i due punti. */
@Composable
private fun MenuSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val current = model.menu(roomId)
    val lines = remember { androidx.compose.runtime.mutableStateListOf<String>().apply {
        (1..7).forEach { d -> add(current.firstOrNull { it.day == d }?.let { it.name + if (it.ingredients.isNotEmpty()) ": " + it.ingredients.joinToString(", ") else "" }.orEmpty()) }
    } }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.fc_menu), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.fc_menu_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        (1..7).forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                Box(Modifier.weight(1f)) { SmallField(lines[d - 1], stringResource(R.string.fc_dish_hint)) { lines[d - 1] = it.take(120) } }
            }
        }
        Pill(stringResource(R.string.as_save), eu.eurise.core.designsystem.icon.EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            model.saveMenu(roomId, (1..7).mapNotNull { Casa.parseDish(it, lines[it - 1]) }) { actions.toast(failed) }
            onClose()
        }
    }
}

/** I farmaci di un genitore: chi, il suo telefono (se semplice), farmaci e orari; avvisi a te se non conferma. */
@Composable
private fun MedsSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val me = model.session?.userId.orEmpty()
    val current = model.meds(roomId)
    var person by remember { mutableStateOf(current?.person.orEmpty()) }
    var phone by remember { mutableStateOf(current?.phone.orEmpty()) }
    var user by remember { mutableStateOf(current?.user.orEmpty()) }
    val lines = remember { androidx.compose.runtime.mutableStateListOf<String>().apply {
        current?.meds?.forEach { add(it.time + " " + it.name) }; while (size < 4) add("")
    } }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.md_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        SmallField(person, stringResource(R.string.md_person)) { person = it.take(40) }
        Text(stringResource(R.string.md_with_app), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            model.members[roomId].orEmpty().filter { it != me }.forEach { u -> Chip(model.nameOf(u).substringBefore(' '), user == u) { user = if (user == u) "" else u } }
        }
        SmallField(phone, stringResource(R.string.md_phone)) { phone = it.take(20) }
        lines.indices.forEach { i -> SmallField(lines[i], stringResource(R.string.md_line_hint)) { lines[i] = it.take(60) } }
        Pill(stringResource(R.string.as_save), eu.eurise.core.designsystem.icon.EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            if (person.isBlank()) return@Pill
            val meds = lines.mapIndexedNotNull { i, l ->
                val m = Regex("^(\\d{1,2})[:.](\\d{2})\\s+(.+)$").find(l.trim()) ?: return@mapIndexedNotNull null
                Casa.Med(current?.meds?.getOrNull(i)?.id ?: ("m" + System.nanoTime() + i), m.groupValues[3].trim(), "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2]))
            }
            model.saveMeds(roomId, Casa.Meds(person.trim(), phone.trim(), user, meds, current?.taken.orEmpty(), current?.by?.ifBlank { null } ?: me)) { actions.toast(failed) }
            onClose()
        }
        if (current != null) Pill(stringResource(R.string.md_remove), eu.eurise.core.designsystem.icon.EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) {
            model.saveMeds(roomId, null) { actions.toast(failed) }; onClose()
        }
    }
}

/**
 * Sono arrivato (tavola FamArrivato): i posti (segnati da dove sei ora), quando vale, l'avviso a mezzanotte. Esce solo la frase,
 * nella chat; il posto si riconosce sul telefono. Spento di base; accenderlo chiede la posizione «sempre».
 */
@Composable
internal fun ArrivalSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val current = model.arrival(roomId)
    val places = remember { androidx.compose.runtime.mutableStateListOf<Arrivo.Place>().apply { current?.places?.let { addAll(it) } } }
    var whenever by remember { mutableStateOf(current?.whenever ?: Arrivo.When.Tonight) }
    var midnight by remember { mutableStateOf(current?.midnight ?: false) }
    var name by remember { mutableStateOf("") }
    val noFix = stringResource(R.string.ar_no_fix)
    Sheet(onClose) {
        Text(stringResource(R.string.ar_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(stringResource(R.string.ar_note), fontSize = 12.5.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ar_where), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                places.forEach { p -> Chip(p.name + "  ✕", true) { places.remove(p) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SmallField(name, stringResource(R.string.ar_place_hint)) { name = it.take(30) } }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.ar_here), false) {
                    actions.askLocation { ok ->
                        val here = if (ok) model.hereNow() else null
                        if (here == null) { actions.toast(noFix); return@askLocation }
                        places.add(Arrivo.Place(name.ifBlank { actions.text(R.string.ar_home) }, here.first, here.second)); name = ""
                    }
                }
            }
            Text(stringResource(R.string.ar_when), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.ar_tonight), whenever == Arrivo.When.Tonight) { whenever = Arrivo.When.Tonight }
                Chip(stringResource(R.string.ar_school), whenever == Arrivo.When.SchoolDays) { whenever = Arrivo.When.SchoolDays }
                Chip(stringResource(R.string.ar_always), whenever == Arrivo.When.Always) { whenever = Arrivo.When.Always }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ar_midnight), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = midnight, onCheckedChange = { midnight = it })
            }
            Text(stringResource(R.string.ar_privacy), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        }
        Pill(stringResource(if (current == null) R.string.ar_on else R.string.as_save), eu.eurise.core.designsystem.icon.EuriseIcons.place(2f), Modifier.fillMaxWidth(), strong = true) {
            if (places.isEmpty()) return@Pill
            actions.askBackgroundLocation { ok ->
                if (!ok) actions.toast(actions.text(R.string.ar_need_always))
                model.saveArrival(roomId, Arrivo.Rule(current?.id ?: ("a" + System.nanoTime()), model.account.name, roomId, model.room(roomId)?.encrypted == true,
                    places.toList(), whenever, midnight, System.currentTimeMillis()))
                onClose()
            }
        }
        if (current != null) Pill(stringResource(R.string.ar_off), eu.eurise.core.designsystem.icon.EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) {
            model.saveArrival(roomId, null); onClose()
        }
    }
}

/** In cima alla chat: chi ha l'avviso di arrivo acceso (gli altri lo vedono, non la posizione). */
@Composable
internal fun ArrivalBar(model: ChatModel, roomId: String, onMine: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val me = model.session?.userId.orEmpty()
    val on = model.arrivalsOn(roomId)
    if (on.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(on.joinToString(" · ") { (who, places) -> (if (who == me) context.getString(R.string.fa_you) else model.nameOf(who).substringBefore(' ')) + ": " +
        context.getString(R.string.ar_bar, places.joinToString(", ") { it.lowercase() }) }, fontSize = 11.5.sp, color = scheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clickable(role = Role.Button, onClick = onMine))
}
