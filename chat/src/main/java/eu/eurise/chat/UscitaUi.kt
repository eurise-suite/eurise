@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package eu.eurise.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val AMBER = Color(0xFF85672B)

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surface).padding(horizontal = 12.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(hint, fontSize = 13.5.sp, color = scheme.onSurfaceVariant)
        BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(fontSize = 13.5.sp, color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary), modifier = Modifier.fillMaxWidth())
    }
}

private fun label(t: LocalDateTime): String =
    t.format(DateTimeFormatter.ofPattern("EEE d · HH:mm", Locale.getDefault())).replaceFirstChar { it.uppercase() }

/** Le date da proporre con un tocco: i prossimi venerdì e sabato sera, domenica a pranzo. */
private fun presets(now: LocalDateTime): List<LocalDateTime> {
    val d = now.toLocalDate()
    return listOf(
        d.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).atTime(21, 0),
        d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(20, 30),
        d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(13, 0),
        d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).plusWeeks(if (d.dayOfWeek == DayOfWeek.FRIDAY) 0 else 1).atTime(21, 0),
    ).filter { it.isAfter(now) }.distinct()
}

/** «Organizza un'uscita» (tavola AmiUscita): cosa, due o tre date, il luogo, quando si chiude la scelta. */
@Composable
internal fun NewOutingSheet(model: ChatModel, actions: ChatActions, roomId: String, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val now = remember { LocalDateTime.now() }
    var title by remember { mutableStateOf("") }
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<LocalDateTime>() }
    var place by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var closesTomorrow by remember { mutableStateOf(true) }
    val failed = stringResource(R.string.ch_send_failed)
    Sheet(onClose) {
        Text(stringResource(R.string.us_new), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
        Field(title, stringResource(R.string.us_what)) { title = it.take(80) }
        Text(stringResource(R.string.us_dates), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets(now).forEach { t -> Chip(label(t), t in chosen) { if (t in chosen) chosen.remove(t) else if (chosen.size < 3) chosen.add(t) } }
        }
        Field(place, stringResource(R.string.us_place)) { place = it.take(80) }
        Field(address, stringResource(R.string.us_address)) { address = it.take(120) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(R.string.us_closes_tomorrow), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = closesTomorrow, onCheckedChange = { closesTomorrow = it })
        }
        Pill(stringResource(R.string.us_send), EuriseIcons.send(2f), Modifier.fillMaxWidth(), strong = true) {
            if (title.isBlank() || chosen.isEmpty()) return@Pill
            val closes = if (closesTomorrow) Programma.toMs(now.toLocalDate().plusDays(1).atTime(12, 0)) else 0L
            model.createOuting(roomId, title, chosen.sorted().map { Uscita.Option(label(it), Programma.toMs(it)) }, place, address, closes) { actions.toast(failed) }
            onClose()
        }
    }
}

/** Il titolo del primo impegno nel calendario di questo telefono tra [start] e due ore dopo; null se libero o senza permesso. */
private fun busy(context: Context, start: Long): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, start); android.content.ContentUris.appendId(it, start + 7_200_000) }.build()
    return runCatching {
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY), null, null, null)?.use { c ->
            var t: String? = null
            while (c.moveToNext()) if (c.getInt(1) == 0) { t = c.getString(0).orEmpty(); break }
            t
        }
    }.getOrNull()
}

/**
 * La proposta di uscita nella chat: le date con i sì (e quanti sono liberi anche secondo il calendario, fra chi lo
 * condivide), il luogo con Mappe, i passaggi, e «Fissa» per chi l'ha proposta; fissata, «Aggiungi al calendario».
 */
@Composable
internal fun OutingBubble(model: ChatModel, actions: ChatActions, roomId: String, e: Event, mine: Boolean, onLong: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = model.relationsTick
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val card = remember(e.data) { Uscita.card(e.data?.let { runCatching { JSONObject(it) }.getOrNull() }) } ?: return
    val me = model.session?.userId.orEmpty()
    val st = model.outingState(roomId, e, card)
    val failed = stringResource(R.string.ch_send_failed)
    val myVotes = st.votes[me].orEmpty()
    val sharing = st.calFree.containsKey(me)
    val busy = remember(card.id, sharing) { card.options.map { busy(context, it.at) } }
    var offering by remember { mutableStateOf(false) }
    var needing by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("") }
    val members = model.members[roomId].orEmpty()
    androidx.compose.runtime.LaunchedEffect(roomId) { model.loadMembers(roomId) }
    fun vote(yes: Set<Int>, share: Boolean) {
        val cal = if (share) card.options.indices.filter { it in yes && busy(context, card.options[it].at) == null }.toSet() else null
        val body = context.getString(R.string.us_vote_body, yes.sorted().joinToString(", ") { card.options[it].label }.ifEmpty { "—" })
        model.sayOuting(roomId, e, card.id, Uscita.Say.Dates(yes, cal), body) { actions.toast(failed) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 330.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface).border(1.5.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .combinedClickable(onClick = {}, onLongClick = onLong).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                Text(card.title, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.us_from, card.from), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
            }
            card.options.forEachIndexed { i, o ->
                val on = i in myVotes
                val fixed = st.fixed == i
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (fixed) GREEN.copy(alpha = 0.16f) else if (on) scheme.primary.copy(alpha = 0.12f) else scheme.background)
                    .clickable(enabled = st.fixed == null, role = Role.Button) { vote(if (on) myVotes - i else myVotes + i, sharing) }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((if (on) "✓ " else "") + o.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${st.count(i)}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (fixed) GREEN else scheme.primary)
                    }
                    if (st.sharing() > 0 && st.calendarFree(i) > 0) Text(stringResource(R.string.us_cal_free, st.calendarFree(i), st.sharing()), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    busy[i]?.let { Text(stringResource(R.string.us_you_busy, it.ifBlank { stringResource(R.string.us_busy_generic) }), fontSize = 11.sp, color = AMBER) }
                }
            }
            if (st.fixed == null) {
                val missing = (members.size - st.votes.size).coerceAtLeast(0)
                val closes = if (card.closes > 0) " · " + stringResource(R.string.us_closes, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(card.closes))) else ""
                if (members.isNotEmpty()) Text(stringResource(R.string.us_missing, missing) + closes, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                // Condividere i liberi del calendario: spento di base; il calendario lo legge solo questo telefono.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.us_share_cal), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sharing, onCheckedChange = { on ->
                        if (on) actions.askCalendar { ok -> if (ok) vote(myVotes, true) } else vote(myVotes, false)
                    })
                }
            }
            if (st.place.isNotBlank()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(EuriseIcons.place(2f), null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.place, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    if (st.address.isNotBlank()) Text(st.address, fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
                Chip(stringResource(R.string.us_maps), false) {
                    runCatching { actions.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(st.place + " " + st.address))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            // Passaggi in macchina.
            Text(stringResource(R.string.us_rides), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
            st.cars.forEach { car ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.us_car, if (car.driver == me) stringResource(R.string.fa_you) else model.nameOf(car.driver).substringBefore(' '), car.seats),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(car.riders.joinToString(", ") { model.nameOf(it).substringBefore(' ') }.ifEmpty { stringResource(R.string.us_nobody) } +
                            (if (car.free > 0) " · " + stringResource(R.string.us_free_seats, car.free) else ""), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                    if (car.driver != me && me !in car.riders && car.free > 0) Chip(stringResource(R.string.us_join), false) {
                        model.sayOuting(roomId, e, card.id, Uscita.Say.Join(car.driver), context.getString(R.string.us_join_body, model.nameOf(car.driver).substringBefore(' '))) { actions.toast(failed) }
                    }
                }
            }
            st.needs.forEach { (who, where) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.us_needs, model.nameOf(who).substringBefore(' '), where), fontSize = 12.5.sp, color = AMBER, modifier = Modifier.weight(1f))
                    val myCar = st.cars.firstOrNull { it.driver == me }
                    if (myCar != null && myCar.free > 0 && who != me) Chip(stringResource(R.string.us_pick), true) {
                        model.sayOuting(roomId, e, card.id, Uscita.Say.Pick(who), context.getString(R.string.us_pick_body, model.nameOf(who).substringBefore(' '))) { actions.toast(failed) }
                    }
                }
            }
            val inCar = st.cars.any { it.driver == me || me in it.riders } || st.needs.containsKey(me)
            when {
                offering || needing -> {
                    Field(from, stringResource(R.string.us_from_where)) { from = it.take(60) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(stringResource(R.string.ap_cancel), false) { offering = false; needing = false }
                        if (offering) listOf(1, 2, 3, 4).forEach { n ->
                            Chip("$n", false) {
                                model.sayOuting(roomId, e, card.id, Uscita.Say.Offer(n, from), context.getString(R.string.us_offer_body, n)) { actions.toast(failed) }
                                offering = false
                            }
                        } else Chip(stringResource(R.string.us_ask), true) {
                            model.sayOuting(roomId, e, card.id, Uscita.Say.Need(from), context.getString(R.string.us_need_body, from)) { actions.toast(failed) }
                            needing = false
                        }
                    }
                    if (offering) Text(stringResource(R.string.us_how_many), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                }
                inCar -> Text(stringResource(R.string.us_no_ride), fontSize = 12.sp, color = scheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(role = Role.Button) { model.sayOuting(roomId, e, card.id, Uscita.Say.NoRide, context.getString(R.string.us_no_ride_body)) { actions.toast(failed) } })
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.us_offer), false) { offering = true }
                    Chip(stringResource(R.string.us_need), false) { needing = true }
                }
            }
            // Fissata: tutti la mettono nel loro calendario; chi l'ha proposta fissa la data migliore.
            val fixedAt = st.fixed?.let { card.options[it] }
            if (fixedAt != null) Pill(stringResource(R.string.us_add_cal, fixedAt.label), EuriseIcons.calendar(2f), Modifier.fillMaxWidth(), strong = true) {
                actions.launch(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, fixedAt.at).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fixedAt.at + 3 * 3_600_000)
                    .putExtra(CalendarContract.Events.TITLE, card.title).putExtra(CalendarContract.Events.EVENT_LOCATION, listOf(st.place, st.address).filter { it.isNotBlank() }.joinToString(", "))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else if (mine) Uscita.best(card, st)?.let { best ->
                Pill(stringResource(R.string.us_fix, card.options[best].label), EuriseIcons.check(2.4f), Modifier.fillMaxWidth(), strong = true) {
                    model.sayOuting(roomId, e, card.id, Uscita.Say.Fix(best), context.getString(R.string.us_fix_body, card.title, card.options[best].label)) { actions.toast(failed) }
                }
            }
        }
    }
}
