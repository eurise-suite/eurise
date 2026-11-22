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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)

// --- Viaggio di gruppo (tavola AmiViaggio) ---

/** La scheda «Viaggio»: la prossima tappa in cima, l'itinerario, e i numeri del viaggio (spese, foto, biglietti, valigia). */
@Composable
internal fun TripPane(model: ChatModel, actions: ChatActions, roomId: String, onExpenses: () -> Unit, onLists: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    LaunchedEffect(roomId) { model.loadExpenses(roomId) }
    val stops = Amici.sortedStops(model.trip(roomId))
    val today = LocalDate.now()
    val now = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
    val next = Amici.next(stops, today.toEpochDay(), now)
    var adding by remember(roomId) { mutableStateOf(false) }
    val failed = stringResource(R.string.ch_send_failed)
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val all = model.timeline(roomId)
    val expenses = model.expenses(roomId)
    val people = Expenses.people(expenses, model.settlements(roomId), model.members[roomId].orEmpty())
    val mine = people.firstOrNull { it.id == me }?.balance ?: 0L
    val lists = model.lists(roomId)
    val bag = lists.firstOrNull { it.name.equals(stringResource(R.string.vi_bag), true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        next?.let { s ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.primary).padding(14.dp)) {
                Text((if (s.day == today.toEpochDay()) stringResource(R.string.vi_today) else LocalDate.ofEpochDay(s.day).format(fmt)) +
                    (if (s.time.isNotBlank()) " · " + s.time else "") + " · " + s.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
                if (s.note.isNotBlank() || s.place.isNotBlank()) Text(listOf(s.place, s.note).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.5.sp,
                    color = scheme.onPrimary.copy(alpha = 0.85f))
                if (s.place.isNotBlank()) Text(stringResource(R.string.vi_maps), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary,
                    modifier = Modifier.padding(top = 6.dp).clickable(role = Role.Button) { actions.launch(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(s.place)))) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vi_itinerary), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("+ " + stringResource(R.string.vi_add), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary, modifier = Modifier.clickable(role = Role.Button) { adding = true }.padding(4.dp))
        }
        if (stops.isEmpty()) Text(stringResource(R.string.vi_empty), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        else Card {
            stops.forEachIndexed { i, s ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(LocalDate.ofEpochDay(s.day).format(fmt) + (if (s.time.isNotBlank()) ", " + s.time else "") + " · " + s.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        if (s.note.isNotBlank() || s.place.isNotBlank()) Text(listOf(s.place, s.note).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    Text("✕", fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.clickable(role = Role.Button) {
                        model.saveTrip(roomId, stops.filterNot { it.id == s.id }) { actions.toast(failed) } }.padding(6.dp))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Expenses.money(expenses.sumOf { it.cents }), if (mine >= 0) stringResource(R.string.vi_owed, Expenses.money(mine)) else stringResource(R.string.vi_owe, Expenses.money(-mine)), onExpenses)
            Tile(all.count { it.kind == Event.Kind.Image }.toString(), stringResource(R.string.vi_photos)) {}
            Tile(all.count { it.kind == Event.Kind.File }.toString(), stringResource(R.string.vi_tickets)) {}
            Tile(stringResource(R.string.vi_bag), bag?.let { stringResource(R.string.vi_bag_left, Elenchi.open(it)) } ?: stringResource(R.string.vi_bag_make)) {
                if (bag == null) model.saveLists(roomId, Elenchi.addList(lists, actions.text(R.string.vi_bag), System.currentTimeMillis())) { actions.toast(failed) }
                onLists()
            }
        }
        Text(stringResource(R.string.vi_note), fontSize = 11.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 90.dp))
    }
    if (adding) StopSheet(model, actions, roomId) { adding = false }
}

@Composable
private fun Tile(big: String, small: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).clickable(role = Role.Button, onClick = onClick).padding(12.dp)) {
        Text(big, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Text(small, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun StopSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.vi_new), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        SmallField(title, stringResource(R.string.vi_what)) { title = it.take(80) }
        SmallField(day, stringResource(R.string.fs_when_hint)) { day = it.take(20) }
        SmallField(time, stringResource(R.string.vi_time)) { time = it.take(5) }
        SmallField(place, stringResource(R.string.vi_place)) { place = it.take(100) }
        SmallField(note, stringResource(R.string.vi_stop_note)) { note = it.take(160) }
        Pill(stringResource(R.string.fs_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            val d = parseDay(day) ?: return@Pill actions.toast(actions.text(R.string.fs_bad_date))
            if (title.isBlank()) return@Pill
            val t = Regex("^(\\d{1,2})[:.](\\d{2})$").matchEntire(time.trim())?.let { "%02d:%s".format(it.groupValues[1].toInt(), it.groupValues[2]) }.orEmpty()
            model.saveTrip(roomId, model.trip(roomId) + Amici.Stop("t" + System.nanoTime(), d.toEpochDay(), t, title.trim(), note.trim(), place.trim(), model.session?.userId.orEmpty())) {
                actions.toast(failed) }
            onClose()
        }
    }
}

// --- Regalo collettivo (tavola AmiRegalo) ---

/** La scheda «Regalo»: per chi e quando, le idee da votare, la quota e chi ha versato, chi compra, il biglietto di tutti. */
@Composable
internal fun GiftPane(model: ChatModel, actions: ChatActions, roomId: String) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val failed = stringResource(R.string.ch_send_failed)
    val g = model.gift(roomId)
    var setup by remember(roomId) { mutableStateOf(g == null) }
    var idea by remember(roomId) { mutableStateOf("") }
    var price by remember(roomId) { mutableStateOf("") }
    var line by remember(roomId) { mutableStateOf("") }
    var mail by remember(roomId) { mutableStateOf<String?>(null) }
    if (setup || g == null) { GiftSetup(model, actions, roomId, g) { setup = false }; return }
    val everyone = (model.members[roomId].orEmpty() + g.people).distinct()
    val quota = Amici.quota(g, everyone.size)
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    fun save(next: Amici.Gift) = model.saveGift(roomId, next) { actions.toast(failed) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.primary).padding(14.dp)) {
            Text(stringResource(R.string.rg_for, g.forName), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onPrimary)
            if (g.date > 0) Text(LocalDate.ofEpochDay(g.date).format(fmt), fontSize = 12.5.sp, color = scheme.onPrimary.copy(alpha = 0.85f))
            if (g.buyer.isNotBlank()) Text(stringResource(R.string.rg_buyer, if (g.buyer == me) stringResource(R.string.fa_you) else model.nameOf(g.buyer).substringBefore(' '),
                if (g.buyBy > 0) LocalDate.ofEpochDay(g.buyBy).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) else "—"), fontSize = 12.5.sp, color = scheme.onPrimary)
        }
        Text(stringResource(R.string.rg_ideas), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        Card {
            g.ideas.sortedByDescending { it.votes.size }.forEachIndexed { i, x ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { save(Amici.vote(g, x.id, me)) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.text + if (x.cents > 0) " · " + Expenses.money(x.cents) else "", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.rg_by, model.nameOf(x.by).substringBefore(' ')), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    Text(x.votes.size.toString(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (me in x.votes) GREEN else scheme.onSurface)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallField(idea, stringResource(R.string.rg_idea_hint)) { idea = it.take(80) } }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(80.dp)) { SmallField(price, "€") { price = it.take(8) } }
            Spacer(Modifier.width(6.dp))
            Chip(stringResource(R.string.el_add), true) {
                if (idea.isBlank()) return@Chip
                save(g.copy(ideas = g.ideas + Amici.Idea("g" + System.nanoTime(), idea.trim(), Expenses.parse(price) ?: 0L, me))); idea = ""; price = ""
            }
        }
        Text(stringResource(R.string.rg_quota, Expenses.money(quota), g.paid.size, everyone.size), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            everyone.forEach { u -> Chip((if (u == me) stringResource(R.string.fa_you) else model.nameOf(u).substringBefore(' ')) + if (u in g.paid) " ✓" else "", u in g.paid) {
                if (u == me || me == g.buyer) save(Amici.togglePaid(g, u)) } }
        }
        val late = everyone.filter { it !in g.paid }
        if (late.isNotEmpty()) Chip(stringResource(R.string.rg_remind, late.size), false) {
            model.send(roomId, actions.text(R.string.rg_remind_body).format(late.filter { !eu.eurise.core.insieme.Scheda.isMailPerson(it) }.joinToString(", ") { model.nameOf(it).substringBefore(' ') },
                Expenses.money(quota), if (g.buyer.isNotBlank()) model.nameOf(g.buyer).substringBefore(' ') else "—")) { actions.toast(failed) }
            model.mailGift(roomId, quota) { actions.toast(actions.text(R.string.af_mail_failed)) }
        }
        if (g.iban.isNotBlank()) Text(stringResource(R.string.rg_iban, g.iban), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("+ " + stringResource(R.string.se_mail_add), false) { mail = "" }
            Chip(stringResource(R.string.fc_menu_change), false) { setup = true }
        }
        mail?.let { m -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallField(m, stringResource(R.string.fa_mail_hint)) { mail = it.trim().take(80) } }
            Spacer(Modifier.width(6.dp))
            Chip(stringResource(R.string.el_add), true) {
                if (m.contains('@') && m.contains('.')) save(g.copy(people = (g.people + eu.eurise.core.insieme.Scheda.mailPerson(m)).distinct()))
                mail = null
            }
        } }
        Text(stringResource(R.string.rg_card, g.lines.size, everyone.size), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
        g.lines.forEach { (who, text) -> Text("«$text» — " + model.nameOf(who).substringBefore(' '), fontSize = 13.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SmallField(line, stringResource(R.string.rg_line_hint)) { line = it.take(140) } }
            Spacer(Modifier.width(6.dp))
            Chip(stringResource(R.string.rg_write), true) { if (line.isNotBlank()) { save(g.copy(lines = g.lines + (me to line.trim()))); line = "" } }
        }
        if (g.lines.isNotEmpty()) Chip(stringResource(R.string.rg_share_card), false) {
            actions.shareText(actions.text(R.string.rg_card_title).format(g.forName) + "\n\n" + g.lines.entries.joinToString("\n") { "«" + it.value + "» — " + model.nameOf(it.key).substringBefore(' ') })
        }
        Text(stringResource(R.string.rg_note), fontSize = 11.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 90.dp))
    }
}

@Composable
private fun GiftSetup(model: ChatModel, actions: ChatActions, roomId: String, g: Amici.Gift?, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    var who by remember { mutableStateOf(g?.forName.orEmpty()) }
    var date by remember { mutableStateOf(g?.date?.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(it).let { d -> "${d.dayOfMonth}/${d.monthValue}" } }.orEmpty()) }
    var buyBy by remember { mutableStateOf(g?.buyBy?.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(it).let { d -> "${d.dayOfMonth}/${d.monthValue}" } }.orEmpty()) }
    var buyer by remember { mutableStateOf(g?.buyer?.ifBlank { me } ?: me) }
    var quota by remember { mutableStateOf(g?.quota?.takeIf { it > 0 }?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty()) }
    var iban by remember { mutableStateOf(g?.iban.orEmpty()) }
    val failed = stringResource(R.string.ch_send_failed)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.rg_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(R.string.rg_setup_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        SmallField(who, stringResource(R.string.rg_who)) { who = it.take(40) }
        SmallField(date, stringResource(R.string.rg_when)) { date = it.take(20) }
        SmallField(buyBy, stringResource(R.string.rg_buy_by)) { buyBy = it.take(20) }
        Text(stringResource(R.string.rg_who_buys), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            model.members[roomId].orEmpty().forEach { u -> Chip(if (u == me) stringResource(R.string.fa_you) else model.nameOf(u).substringBefore(' '), buyer == u) { buyer = u } }
        }
        SmallField(quota, stringResource(R.string.rg_quota_hint)) { quota = it.take(8) }
        SmallField(iban, stringResource(R.string.rg_iban_hint)) { iban = it.take(60) }
        Pill(stringResource(R.string.fs_save), EuriseIcons.gift(2f), Modifier.fillMaxWidth(), strong = true) {
            if (who.isBlank()) return@Pill
            val base = g ?: Amici.Gift(who.trim(), 0, 0, buyer, 0)
            model.saveGift(roomId, base.copy(forName = who.trim(), date = parseDay(date)?.toEpochDay() ?: 0L, buyBy = parseDay(buyBy)?.toEpochDay() ?: 0L, buyer = buyer,
                quota = Expenses.parse(quota) ?: 0L, iban = iban.trim())) { actions.toast(failed) }
            onDone()
        }
        Spacer(Modifier.heightIn(min = 90.dp))
    }
}

// --- Traduzione per colleghi stranieri (tavola SpaTraduci) ---

/** Sotto un messaggio in un'altra lingua: la traduzione (da sola se l'hai chiesto per questa chat), o «Traduci». */
@Composable
internal fun TranslationLine(model: ChatModel, actions: ChatActions, roomId: String, e: Event) {
    val scheme = MaterialTheme.colorScheme
    val t = model.translations[e.id]
    val auto = model.translateRead(roomId)
    var showOriginal by remember(e.id) { mutableStateOf(false) }
    val foreign = remember(e.id) { eu.eurise.core.data.messages.MessageInsights.language(e.text)?.let { it != "it" } == true }
    LaunchedEffect(e.id, auto) { if (auto && foreign) model.translateEvent(e) }
    if (t == null) {
        if (!auto && foreign) Text(stringResource(R.string.tr_do), fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold, color = scheme.primary, modifier = Modifier.padding(start = 12.dp).clickable(role = Role.Button) {
                model.translateEvent(e, force = true) { actions.toast(actions.text(it)) } }.padding(4.dp))
        return
    }
    Column(Modifier.padding(start = 12.dp, end = 40.dp, top = 2.dp)) {
        Text(if (showOriginal) e.text else t.first, fontSize = 14.sp, lineHeight = 19.sp, color = scheme.onSurface)
        Text(stringResource(R.string.tr_from, eu.eurise.core.insieme.Traduci.name(t.second)) + " · " + stringResource(if (showOriginal) R.string.tr_translation else R.string.tr_original),
            fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.clickable(role = Role.Button) { showOriginal = !showOriginal })
    }
}

/** Per questa persona: leggere i suoi messaggi in italiano, mandarle i tuoi nella sua lingua con l'italiano sotto. */
@Composable
internal fun TranslateSheet(model: ChatModel, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var read by remember { mutableStateOf(model.translateRead(roomId)) }
    var send by remember { mutableStateOf(model.translateSend(roomId)) }
    val name = model.room(roomId)?.name.orEmpty()
    Sheet(onClose) {
        Text(stringResource(R.string.tr_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tr_read, name), fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = read, onCheckedChange = { read = it })
            }
            Text(stringResource(R.string.tr_send), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.tr_italian), send == null) { send = null }
                eu.eurise.core.insieme.Traduci.LANGUAGES.forEach { l -> Chip(eu.eurise.core.insieme.Traduci.name(l), send == l) { send = l } }
            }
            Text(stringResource(R.string.tr_privacy), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant)
        }
        Pill(stringResource(R.string.fs_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) { model.setTranslate(roomId, read, send); onClose() }
    }
}

// --- Stato e assenza (tavola LavStato) ---

@Composable
internal fun StatusSheet(model: ChatModel, actions: ChatActions, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val cur = model.myStatus
    var now by remember { mutableStateOf(cur?.now ?: eu.eurise.core.insieme.Assenza.Now.Libero) }
    var until by remember { mutableStateOf(cur?.until.orEmpty()) }
    fun label(d: Long) = if (d > 0) LocalDate.ofEpochDay(d).let { "${it.dayOfMonth}/${it.monthValue}" } else ""
    var from by remember { mutableStateOf(label(cur?.from ?: 0)) }
    var to by remember { mutableStateOf(label(cur?.to ?: 0)) }
    var sub by remember { mutableStateOf(cur?.substitute.orEmpty()) }
    var msg by remember { mutableStateOf(cur?.message.orEmpty()) }
    var chat by remember { mutableStateOf(cur?.chat ?: true) }
    var email by remember { mutableStateOf(cur?.email ?: true) }
    Sheet(onClose) {
        Text(stringResource(R.string.ls_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ls_now), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(eu.eurise.core.insieme.Assenza.Now.Libero to R.string.ls_free, eu.eurise.core.insieme.Assenza.Now.Cantiere to R.string.ls_site,
                    eu.eurise.core.insieme.Assenza.Now.Riunione to R.string.ls_meeting, eu.eurise.core.insieme.Assenza.Now.Viaggio to R.string.ls_travel).forEach { (n, l) ->
                    Chip(stringResource(l), now == n) { now = n }
                }
            }
            if (now != eu.eurise.core.insieme.Assenza.Now.Libero) SmallField(until, stringResource(R.string.ls_until_hint)) { until = it.take(5) }
            Text(stringResource(R.string.ls_absence), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { SmallField(from, stringResource(R.string.ls_from)) { from = it.take(10) } }
                Box(Modifier.weight(1f)) { SmallField(to, stringResource(R.string.ls_to)) { to = it.take(10) } }
            }
            SmallField(sub, stringResource(R.string.ls_sub)) { sub = it.take(60) }
            SmallField(msg, stringResource(R.string.ls_msg)) { msg = it.take(300) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ls_chat), fontSize = 13.sp, modifier = Modifier.weight(1f)); androidx.compose.material3.Switch(checked = chat, onCheckedChange = { chat = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ls_email), fontSize = 13.sp, modifier = Modifier.weight(1f)); androidx.compose.material3.Switch(checked = email, onCheckedChange = { email = it })
            }
        }
        Pill(stringResource(R.string.ls_save), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
            val a = parseDay(from)
            val b = parseDay(to)
            model.setStatus(eu.eurise.core.insieme.Assenza.State(now, until.trim(), a?.toEpochDay() ?: 0L, b?.toEpochDay() ?: 0L, sub.trim(), msg.trim(), chat, email))
            onClose()
        }
        if (cur != null) Pill(stringResource(R.string.ls_clear), EuriseIcons.close(2f), Modifier.fillMaxWidth(), strong = false) { model.setStatus(null); onClose() }
    }
}

/** Nel gruppo di lavoro: chi è in ferie nei prossimi 30 giorni, e i giorni in cui siete fuori in due o più. */
@Composable
internal fun TeamHolidays(model: ChatModel, roomId: String) {
    val scheme = MaterialTheme.colorScheme
    val me = model.session?.userId.orEmpty()
    val states = model.members[roomId].orEmpty().mapNotNull { u -> (if (u == me) model.myStatus else model.statusOf(roomId, u))?.takeIf { it.from > 0 }?.let { u to it } }
    val today = LocalDate.now()
    val soon = states.filter { it.second.to >= today.toEpochDay() && it.second.from <= today.plusDays(30).toEpochDay() }
    if (soon.isEmpty()) return
    Text(stringResource(R.string.ls_team), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
    soon.forEach { (u, s) ->
        Text((if (u == me) stringResource(R.string.fa_you) else model.nameOf(u).substringBefore(' ')) + " · " + LocalDate.ofEpochDay(s.from).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) +
            "–" + LocalDate.ofEpochDay(s.to).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), fontSize = 12.5.sp)
    }
    (0..30).map { today.plusDays(it.toLong()) }.firstOrNull { d -> eu.eurise.core.insieme.Assenza.outOn(soon.map { it.second }, d) >= 2 }?.let { d ->
        Text(stringResource(R.string.ls_team_two, d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())), eu.eurise.core.insieme.Assenza.outOn(soon.map { it.second }, d)),
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF85672B))
    }
}
