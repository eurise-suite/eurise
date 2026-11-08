package eu.eurise.feature.clock.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.feature.clock.R
import eu.eurise.feature.clock.data.ClockStore
import eu.eurise.feature.clock.logic.ClockTimer
import eu.eurise.feature.clock.logic.Pomodoro
import eu.eurise.feature.clock.logic.formatDuration
import eu.eurise.feature.clock.system.Quiet
import eu.eurise.feature.clock.system.Scheduler
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val Hm = DateTimeFormatter.ofPattern("HH:mm")

// ---------------------------------------------------------------- Timer

/** Timer: quelli in corso (+1 min, pausa, togli), il Pomodoro e l'avvio rapido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimersScreen(adding: Boolean, onAdded: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val timers by store.timers.collectAsStateWithLifecycle()
    val now = rememberNow(1_000)
    var custom by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    LaunchedEffect(adding) { if (adding) { custom = true; onAdded() } }
    fun start(label: String, minutes: Int, pomodoro: Pomodoro? = null) {
        store.saveTimer(ClockTimer(store.nextId(), label, minutes * 60_000L, pomodoro = pomodoro).start(System.currentTimeMillis()))
        if (pomodoro?.quietDuringWork == true) Quiet.apply(context, true)
        Scheduler.rescheduleAll(context)
    }
    fun update(t: ClockTimer?) { if (t != null) store.saveTimer(t); Scheduler.rescheduleAll(context) }

    ScreenList(stringResource(R.string.tab_timers)) {
        val active = timers.filter { it.running || it.remainingMs > 0 }
        if (active.isNotEmpty()) item(key = "count") {
            Text(stringResource(R.string.timers_running, active.count { it.running }), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        items(active.size, key = { active[it].id }) { i ->
            val t = active[i]
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val title = t.label.ifBlank { stringResource(R.string.timer_default) }
                    Text(
                        if (t.pomodoro != null) stringResource(if (t.isWork) R.string.pomodoro_work else R.string.pomodoro_rest, title) else title,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(formatDuration(t.remaining(now)), fontSize = 40.sp, fontWeight = FontWeight.Medium)
                    t.endsAt?.let { end ->
                        Text(stringResource(R.string.timer_rings_at, LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneId.systemDefault()).format(Hm)), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.timer_plus1)) { update(t.plus(60_000, System.currentTimeMillis())) }
                        Chip(stringResource(if (t.running) R.string.timer_pause else R.string.timer_resume), filled = !t.running) {
                            update(if (t.running) t.pause(System.currentTimeMillis()) else t.start(System.currentTimeMillis()))
                        }
                        Chip(stringResource(R.string.timer_stop)) {
                            if (t.pomodoro != null) Quiet.apply(context, false)
                            store.deleteTimer(t.id); Scheduler.rescheduleAll(context)
                        }
                    }
                }
            }
        }
        item(key = "pomodoro") {
            val p = Pomodoro(quietDuringWork = quiet)
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pomodoro), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.pomodoro_sub, p.workMinutes, p.restMinutes, p.rounds), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Chip(stringResource(R.string.pomodoro_start), filled = true) { start(context.getString(R.string.pomodoro), p.workMinutes, p) }
                    }
                    CardDivider()
                    val nm = context.getSystemService(NotificationManager::class.java)
                    SwitchRow(stringResource(R.string.pomodoro_quiet), stringResource(R.string.pomodoro_quiet_sub), quiet) { on ->
                        if (on && nm?.isNotificationPolicyAccessGranted != true) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                        }
                        quiet = on
                    }
                }
            }
        }
        item(key = "quick") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.quick_start))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 15, 30).forEach { m -> Chip(stringResource(R.string.minutes_short, m)) { start("", m) } }
                    Chip(stringResource(R.string.quick_egg)) { start(context.getString(R.string.quick_egg_label), 7) }
                    Chip(stringResource(R.string.quick_pasta)) { start(context.getString(R.string.quick_pasta_label), 10) }
                    Chip(stringResource(R.string.quick_custom), filled = true) { custom = true }
                }
            }
        }
    }
    if (custom) CustomTimerDialog(onStart = { label, m -> start(label, m); custom = false }, onDismiss = { custom = false })
}

@Composable
private fun CustomTimerDialog(onStart: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.timer_minutes)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(label, { label = it.take(30) }, label = { Text(stringResource(R.string.timer_label)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let { onStart(label.trim(), it) } }) {
                Text(stringResource(R.string.pomodoro_start), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------- Cronometro

/** Cronometro: continua anche ad app chiusa (si salva l'istante di partenza), con i giri. */
@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val s by store.settings.collectAsStateWithLifecycle()
    val now = rememberNow(if (s.stopwatchStart != null) 50 else 1_000)
    val elapsed = s.stopwatchAccumulated + (s.stopwatchStart?.let { now - it } ?: 0)
    ScreenList(stringResource(R.string.tab_stopwatch)) {
        item(key = "time") {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(stopwatchText(elapsed), fontSize = 56.sp, fontWeight = FontWeight.Medium)
            }
        }
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                if (s.stopwatchStart != null) {
                    Chip(stringResource(R.string.sw_lap)) { store.updateSettings { it.copy(laps = it.laps + elapsed) } }
                    Chip(stringResource(R.string.sw_pause), filled = true) {
                        store.updateSettings { it.copy(stopwatchStart = null, stopwatchAccumulated = elapsed) }
                    }
                } else {
                    if (elapsed > 0) Chip(stringResource(R.string.sw_reset)) { store.updateSettings { it.copy(stopwatchAccumulated = 0, laps = emptyList()) } }
                    Chip(stringResource(if (elapsed > 0) R.string.sw_resume else R.string.sw_start), filled = true) {
                        store.updateSettings { it.copy(stopwatchStart = System.currentTimeMillis()) }
                    }
                }
            }
        }
        val laps = s.laps
        if (laps.isNotEmpty()) item(key = "laps") {
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    laps.indices.reversed().forEachIndexed { i, idx ->
                        if (i > 0) CardDivider()
                        val lap = laps[idx] - (laps.getOrNull(idx - 1) ?: 0)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.sw_lap_n, idx + 1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("+" + stopwatchText(lap), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("   " + stopwatchText(laps[idx]), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun stopwatchText(ms: Long): String {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60_000) % 60
    val h = ms / 3_600_000
    return if (h > 0) "%d:%02d:%02d,%02d".format(h, m, s, cs) else "%02d:%02d,%02d".format(m, s, cs)
}

// ---------------------------------------------------------------- Fusi

/** Fusi orari: l'ora nelle città scelte, con la differenza da qui. */
@Composable
fun ZonesScreen(adding: Boolean, onAdded: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val s by store.settings.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(adding) { if (adding) { picking = true; onAdded() } }
    val here = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    ScreenList(stringResource(R.string.tab_zones)) {
        item(key = "here") {
            Text(stringResource(R.string.zones_here, here.format(Hm), cityName(ZoneId.systemDefault().id)), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (s.zones.isNotEmpty()) item(key = "list") {
            GroupCard {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    s.zones.forEachIndexed { i, id ->
                        if (i > 0) CardDivider()
                        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@forEachIndexed
                        val there = here.withZoneSameInstant(zone)
                        val diffMin = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
                        val dayShift = there.toLocalDate().compareTo(here.toLocalDate())
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cityName(id), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        diffText(diffMin),
                                        when { dayShift > 0 -> stringResource(R.string.day_tomorrow); dayShift < 0 -> stringResource(R.string.day_yesterday); else -> null },
                                    ).joinToString(" · "),
                                    fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(there.format(Hm), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "  ✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { store.updateSettings { st -> st.copy(zones = st.zones - id) } }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (picking) ZonePicker(onPick = { id -> store.updateSettings { it.copy(zones = (it.zones + id).distinct()) }; picking = false }, onDismiss = { picking = false })
}

private fun cityName(id: String) = id.substringAfterLast('/').replace('_', ' ')

@Composable
private fun diffText(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.zone_same)
    minutes % 60 == 0 -> stringResource(R.string.zone_diff_h, "%+d".format(minutes / 60))
    else -> stringResource(R.string.zone_diff_hm, "%+d".format(minutes / 60), kotlin.math.abs(minutes % 60))
}

@Composable
private fun ZonePicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sortedBy { cityName(it) } }
    val shown = remember(q) { all.filter { q.isBlank() || cityName(it).contains(q, ignoreCase = true) || it.contains(q, ignoreCase = true) }.take(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zones_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text(stringResource(R.string.zones_search)) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it }) { id ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 10.dp)) {
                            Text(cityName(id), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(id.substringBefore('/'), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
