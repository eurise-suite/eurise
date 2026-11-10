package eu.eurise.feature.launcher.settings

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.modes.Interruptions
import eu.eurise.core.data.modes.Mode
import eu.eurise.core.data.modes.ModeConfig
import eu.eurise.core.data.modes.Modes
import eu.eurise.core.data.modes.Ringer
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.apps.BarCategory
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Simbolo e nome di una modalità, comuni al Pannello e alle impostazioni. */
fun Mode.icon(): ImageVector = when (this) {
    Mode.Work -> EuriseIcons.work()
    Mode.Home -> EuriseIcons.home()
    Mode.Travel -> EuriseIcons.plane()
    Mode.Night -> EuriseIcons.moon()
}

fun Mode.label(): Int = when (this) {
    Mode.Work -> R.string.mode_work
    Mode.Home -> R.string.mode_home
    Mode.Travel -> R.string.mode_travel
    Mode.Night -> R.string.mode_night
}

/** Ordine in cui si mostrano (diverso dalla precedenza). */
val ModeOrder = listOf(Mode.Work, Mode.Home, Mode.Travel, Mode.Night)

/**
 * Impostazioni delle modalità: che cosa cambia ciascuna (categoria della Scrivania, notifiche, suoneria, calendari ed
 * elenchi visibili) e quando si accende da sola (orario o eventi di un calendario). Mai la posizione.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModesSheet(categories: List<BarCategory>, start: Mode = Mode.Work, onClose: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EuriseData.create(context) }
    val state by Modes.state.collectAsStateWithLifecycle()
    val calendars by remember { data.calendar.observeCalendars() }.collectAsStateWithLifecycle(emptyList())
    val lists by remember { data.tasks.observeLists() }.collectAsStateWithLifecycle(emptyList())
    var mode by remember { mutableStateOf(start) }
    var askPolicy by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true = inizio, false = fine
    val config = state.configs.firstOrNull { it.mode == mode } ?: ModeConfig.defaults(mode)
    fun save(c: ModeConfig) = Modes.save(context, c)
    val policyGranted = Build.VERSION.SDK_INT < 23 ||
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.modes_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.card_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOrder.forEach { m -> Pill(stringResource(m.label()), m == mode, m.icon(), Modifier.weight(1f)) { mode = m } }
            }

            // Quando si accende.
            Section(stringResource(R.string.modes_when))
            SwitchRow(stringResource(R.string.modes_schedule), config.schedule.enabled) { save(config.copy(schedule = config.schedule.copy(enabled = it))) }
            if (config.schedule.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.modes_from, config.schedule.start.toString()), false, null, Modifier.weight(1f)) { pickTime = true }
                    Pill(stringResource(R.string.modes_to, config.schedule.end.toString()), false, null, Modifier.weight(1f)) { pickTime = false }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in config.schedule.days
                        Pill(d.getDisplayName(TextStyle.NARROW, Locale.ITALIAN), on, null, Modifier.weight(1f)) {
                            save(config.copy(schedule = config.schedule.copy(days = if (on) config.schedule.days - d else config.schedule.days + d)))
                        }
                    }
                }
                SwitchRow(stringResource(R.string.modes_skip_holidays), config.schedule.skipHolidays) {
                    save(config.copy(schedule = config.schedule.copy(skipHolidays = it)))
                }
                Hint(stringResource(R.string.modes_skip_hint))
            }
            Text(stringResource(R.string.modes_calendar_trigger), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.modes_none), config.calendarTrigger == null, null) { save(config.copy(calendarTrigger = null)) }
                calendars.forEach { c -> Pill(c.name, config.calendarTrigger == c.id, null) { save(config.copy(calendarTrigger = c.id)) } }
            }
            Hint(stringResource(R.string.modes_no_location))

            // Che cosa cambia.
            Section(stringResource(R.string.modes_what))
            Text(stringResource(R.string.modes_home_category), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.modes_home_page), config.homeCategory == null, null) { save(config.copy(homeCategory = null)) }
                categories.forEach { c -> Pill(c.label, config.homeCategory == c.id, c.iconVector(2f)) { save(config.copy(homeCategory = c.id)) } }
            }
            Text(stringResource(R.string.modes_notifications), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Interruptions.entries.forEach { i ->
                    Pill(stringResource(i.label()), config.interruptions == i, null) {
                        if (i != Interruptions.Unchanged && !policyGranted) askPolicy = true
                        save(config.copy(interruptions = i))
                    }
                }
            }
            if (config.interruptions != Interruptions.Unchanged && !policyGranted) {
                Hint(stringResource(R.string.modes_policy_missing))
            }
            Text(stringResource(R.string.modes_ringer), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Ringer.entries.forEach { r -> Pill(stringResource(r.label()), config.ringer == r, null) { save(config.copy(ringer = r)) } }
            }
            if (calendars.isNotEmpty()) {
                Text(stringResource(R.string.modes_calendars), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                calendars.forEach { c ->
                    CheckRow(c.name, c.id !in config.hiddenCalendars) { on ->
                        save(config.copy(hiddenCalendars = if (on) config.hiddenCalendars - c.id else config.hiddenCalendars + c.id))
                    }
                }
            }
            if (lists.isNotEmpty()) {
                Text(stringResource(R.string.modes_lists), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                lists.forEach { l ->
                    CheckRow(l.list.name, l.list.id !in config.hiddenLists) { on ->
                        save(config.copy(hiddenLists = if (on) config.hiddenLists - l.list.id else config.hiddenLists + l.list.id))
                    }
                }
            }
        }
    }

    pickTime?.let { isStart ->
        val current = if (isStart) config.schedule.start else config.schedule.end
        TimeDialog(current, onPick = { t ->
            save(config.copy(schedule = if (isStart) config.schedule.copy(start = t) else config.schedule.copy(end = t)))
            pickTime = null
        }, onDismiss = { pickTime = null })
    }

    if (askPolicy) {
        // Interruttore con spiegazione: che cosa cambia, chi lo vede, come si torna indietro.
        AlertDialog(
            onDismissRequest = { askPolicy = false },
            title = { Text(stringResource(R.string.modes_policy_title)) },
            text = { Text(stringResource(R.string.modes_policy_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askPolicy = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(stringResource(R.string.modes_policy_open)) }
            },
            dismissButton = { TextButton(onClick = { askPolicy = false }) { Text(stringResource(R.string.modes_policy_later)) } },
        )
    }
}

private fun Interruptions.label(): Int = when (this) {
    Interruptions.Unchanged -> R.string.modes_unchanged
    Interruptions.All -> R.string.modes_int_all
    Interruptions.Priority -> R.string.modes_int_priority
    Interruptions.Alarms -> R.string.modes_int_alarms
    Interruptions.None -> R.string.modes_int_none
}

private fun Ringer.label(): Int = when (this) {
    Ringer.Unchanged -> R.string.modes_unchanged
    Ringer.Sound -> R.string.ringer_sound
    Ringer.Vibrate -> R.string.ringer_vibrate
    Ringer.Silent -> R.string.ringer_silent
}

@Composable
private fun Section(text: String) =
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Hint(text: String) =
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = on, onCheckedChange = onChange)
        Text(label, fontSize = 15.sp)
    }
}

@Composable
internal fun Pill(text: String, selected: Boolean, icon: ImageVector?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Box(Modifier.width(6.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(picker.hour, picker.minute)) }) { Text(stringResource(R.string.card_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.modes_policy_later)) } },
        text = { TimePicker(state = picker) },
    )
}
