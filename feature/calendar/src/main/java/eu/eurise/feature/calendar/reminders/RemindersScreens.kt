package eu.eurise.feature.calendar.reminders

import android.Manifest
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.dates.ImportantDate
import eu.eurise.core.data.meds.Medicine
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.calendar.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val Italian = Locale.ITALIAN

@Composable
private fun Page(title: String, onBack: () -> Unit, action: String?, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.chevronLeft(), stringResource(R.string.back), modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (action != null) {
                Box(
                    Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onAction).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(action, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp) }
            }
        }
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Italian), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Field(value: String, hint: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Chiede il permesso per gli avvisi (Android 13+) quando si aggiunge un promemoria. */
@Composable
private fun rememberNotificationAsk(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

// ---------------------------------------------------------------- Date importanti

/** Elenco delle date importanti: le proprie in cima, poi le altre in ordine di calendario. */
@Composable
fun ImportantDatesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EuriseData.create(context) }
    val dates by data.dates.dates.collectAsState()
    var editing by remember { mutableStateOf<ImportantDate?>(null) }
    val askNotify = rememberNotificationAsk()
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) data.dates.addAll(importBirthdays(context))
    }
    val today = LocalDate.now()
    val editor = editing
    if (editor != null) {
        DateEditor(editor, onSave = {
            data.dates.save(it); editing = null; askNotify(); Reminders.refresh(context)
        }, onDelete = { data.dates.delete(editor.id); editing = null; Reminders.refresh(context) }, onBack = { editing = null })
        return
    }
    Page(stringResource(R.string.dates_title), onBack, stringResource(R.string.dates_add), {
        editing = ImportantDate(0, "", today.monthValue, today.dayOfMonth, null, ImportantDate.Kind.Birthday, false)
    }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.dates_intro), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val mine = dates.filter { it.mine }
            if (mine.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.dates_mine)) }
                items(mine, key = { it.id }) { d -> DateRow(d, today) { editing = d } }
            }
            item { SectionLabel(stringResource(R.string.dates_others)) }
            items(dates.filterNot { it.mine }.sortedBy { it.nextOn(today) }, key = { it.id }) { d -> DateRow(d, today) { editing = d } }
            item {
                Text(
                    stringResource(R.string.dates_import), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DateRow(d: ImportantDate, today: LocalDate, onClick: () -> Unit) {
    val next = d.nextOn(today)
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, next)
    val when_ = when (days) {
        0L -> stringResource(R.string.dates_today)
        1L -> stringResource(R.string.dates_tomorrow)
        else -> stringResource(R.string.dates_in_days, days)
    }
    val years = d.yearsOn(next)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1F2A44)), contentAlignment = Alignment.Center) {
            Icon(
                if (d.kind == ImportantDate.Kind.Birthday) EuriseIcons.star(2f) else EuriseIcons.heart(2f), null,
                tint = if (d.kind == ImportantDate.Kind.Birthday) Color(0xFFD2B06A) else Color(0xFFE8939B), modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val date = next.format(DateTimeFormatter.ofPattern("d MMMM", Italian))
            val kind = stringResource(kindLabel(d.kind)) + (years?.let { " · $it" } ?: "")
            Text("$date · $kind · $when_", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (d.mine) Text(stringResource(R.string.dates_wallpaper), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        else if (d.giftReminder) Icon(EuriseIcons.task(2f), stringResource(R.string.dates_gift), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

private fun kindLabel(kind: ImportantDate.Kind) = when (kind) {
    ImportantDate.Kind.Birthday -> R.string.kind_birthday
    ImportantDate.Kind.Wedding -> R.string.kind_wedding
    ImportantDate.Kind.Engagement -> R.string.kind_engagement
    ImportantDate.Kind.Anniversary -> R.string.kind_anniversary
}

private fun relationLabel(r: ImportantDate.Relation) = when (r) {
    ImportantDate.Relation.Partner -> R.string.rel_partner
    ImportantDate.Relation.Child -> R.string.rel_child
    ImportantDate.Relation.Mother -> R.string.rel_mother
    ImportantDate.Relation.Father -> R.string.rel_father
    ImportantDate.Relation.Sibling -> R.string.rel_sibling
    ImportantDate.Relation.Grandparent -> R.string.rel_grandparent
    ImportantDate.Relation.Friend -> R.string.rel_friend
    ImportantDate.Relation.Other -> R.string.rel_other
    ImportantDate.Relation.Self -> R.string.rel_self
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateEditor(initial: ImportantDate, onSave: (ImportantDate) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var day by remember { mutableStateOf(initial.day.toString()) }
    var month by remember { mutableStateOf(initial.month) }
    var year by remember { mutableStateOf(initial.year?.toString().orEmpty()) }
    var kind by remember { mutableStateOf(initial.kind) }
    var relation by remember { mutableStateOf(initial.relation) }
    var gift by remember { mutableStateOf(initial.giftReminder) }
    var giftTouched by remember { mutableStateOf(initial.id != 0L) }
    var notify by remember { mutableStateOf(initial.notify) }
    val valid = name.isNotBlank() && (day.toIntOrNull() ?: 0) in 1..java.time.Month.of(month).maxLength()
    Page(stringResource(if (initial.id == 0L) R.string.dates_new else R.string.dates_edit), onBack, stringResource(R.string.save), {
        if (valid) onSave(
            initial.copy(
                name = name.trim(), day = day.toInt(), month = month, year = year.toIntOrNull()?.takeIf { it in 1900..2200 },
                kind = kind, relation = relation, mine = relation == ImportantDate.Relation.Self,
                giftReminder = gift && relation != ImportantDate.Relation.Self, notify = notify,
            ),
        )
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, stringResource(R.string.dates_name), { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(80.dp)) { Field(day, stringResource(R.string.dates_day), { day = it.filter(Char::isDigit).take(2) }, KeyboardType.Number) }
                Box(Modifier.width(110.dp)) { Field(year, stringResource(R.string.dates_year), { year = it.filter(Char::isDigit).take(4) }, KeyboardType.Number) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..12).forEach { m ->
                    Chip(java.time.Month.of(m).getDisplayName(JTextStyle.SHORT_STANDALONE, Italian), m == month) { month = m }
                }
            }
            SectionLabel(stringResource(R.string.dates_what))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImportantDate.Kind.entries.forEach { k -> Chip(stringResource(kindLabel(k)), k == kind) { kind = k } }
            }
            SectionLabel(stringResource(R.string.dates_who))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImportantDate.Relation.entries.forEach { r ->
                    Chip(stringResource(relationLabel(r)), r == relation) {
                        relation = r
                        if (!giftTouched) gift = r.closeFamily
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
                if (relation != ImportantDate.Relation.Self) {
                    ToggleRow(stringResource(R.string.dates_gift), stringResource(R.string.dates_gift_sub), gift) { gift = it; giftTouched = true }
                }
                ToggleRow(stringResource(R.string.dates_notify), null, notify) { notify = it }
            }
            Text(stringResource(R.string.dates_gift_hint), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (initial.id != 0L) {
                Text(
                    stringResource(R.string.delete), color = Color(0xFF8C2F39), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Compleanni già salvati nei Contatti (evento "compleanno"). */
private fun importBirthdays(context: android.content.Context): List<ImportantDate> = runCatching {
    val out = mutableListOf<ImportantDate>()
    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE, ContactsContract.CommonDataKinds.Event.TYPE),
        "${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE), null,
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0) ?: continue
            val raw = c.getString(1) ?: continue
            val type = c.getInt(2)
            // Formati: "1988-02-02" oppure "--02-02" (senza anno).
            val parts = raw.removePrefix("--").split('-')
            val (y, m, d) = when (parts.size) {
                3 -> Triple(parts[0].toIntOrNull(), parts[1].toIntOrNull(), parts[2].take(2).toIntOrNull())
                2 -> Triple(null, parts[0].toIntOrNull(), parts[1].take(2).toIntOrNull())
                else -> continue
            }
            if (m == null || d == null) continue
            val kind = if (type == ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY) ImportantDate.Kind.Anniversary else ImportantDate.Kind.Birthday
            out += ImportantDate(0, name, m, d, y, kind, false, ImportantDate.Relation.Other, giftReminder = false)
        }
    }
    out
}.getOrDefault(emptyList())

// ---------------------------------------------------------------- Farmaci

/** Farmaci: elenco con le dosi di oggi (prese o da prendere) e l'editor. */
@Composable
fun MedicinesRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EuriseData.create(context) }
    val medicines by data.medicines.medicines.collectAsState()
    val today = LocalDate.now()
    val todayTasks by data.tasks.observeDueBetween(today, today).collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Medicine?>(null) }
    val askNotify = rememberNotificationAsk()
    val editor = editing
    if (editor != null) {
        MedicineEditor(editor, onSave = { data.medicines.save(it); editing = null; askNotify(); Reminders.refresh(context) },
            onDelete = { data.medicines.delete(editor.id); editing = null; Reminders.refresh(context) }, onBack = { editing = null })
        return
    }
    Page(stringResource(R.string.meds_title), onBack, stringResource(R.string.dates_add), {
        editing = Medicine(0, "", "", listOf(LocalTime.of(8, 0)))
    }) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(stringResource(R.string.meds_intro), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SectionLabel(stringResource(R.string.meds_today)) }
            if (medicines.isEmpty()) item { Text(stringResource(R.string.meds_empty), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(medicines, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).clickable { editing = m }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1F2A44)), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.heart(2f), null, tint = Color(0xFF93BBA0), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(scheduleText(m), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.dueOn(today)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.times.forEach { t ->
                                val title = listOf(m.name, m.dose).filter { it.isNotBlank() }.joinToString(" · ")
                                val taken = todayTasks.any { it.title == title && it.done && it.notes.contains(t.toString()) }
                                Text(
                                    t.toString() + if (taken) " ✓" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (taken) Color(0xFF4F6B5A) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clip(RoundedCornerShape(50))
                                        .background(if (taken) Color(0xFF4F6B5A).copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun scheduleText(m: Medicine): String {
    val days = if (m.days.isEmpty()) stringResource(R.string.meds_every_day)
    else m.days.sorted().joinToString(", ") { it.getDisplayName(JTextStyle.SHORT_STANDALONE, Italian) }
    val until = m.until?.let { " · " + stringResource(R.string.meds_until, it.format(DateTimeFormatter.ofPattern("d MMMM", Italian))) }.orEmpty()
    return listOf(m.dose, days).filter { it.isNotBlank() }.joinToString(" · ") + until
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineEditor(initial: Medicine, onSave: (Medicine) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var times by remember { mutableStateOf(initial.times.toSet()) }
    var newTime by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(initial.days) }
    var until by remember { mutableStateOf(initial.until?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")).orEmpty()) }
    val presets = listOf(LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(20, 0))
    Page(stringResource(if (initial.id == 0L) R.string.meds_new else R.string.meds_edit), onBack, stringResource(R.string.save), {
        if (name.isNotBlank() && times.isNotEmpty()) onSave(
            initial.copy(
                name = name.trim(), dose = dose.trim(), times = times.sorted(), days = days,
                until = runCatching { LocalDate.parse(until, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull(),
            ),
        )
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, stringResource(R.string.meds_name), { name = it })
            Field(dose, stringResource(R.string.meds_dose), { dose = it })
            SectionLabel(stringResource(R.string.meds_times))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (presets + times).distinct().sorted().forEach { t -> Chip(t.toString(), t in times) { times = if (t in times) times - t else times + t } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(120.dp)) { Field(newTime, "hh:mm", { newTime = it.take(5) }, KeyboardType.Number) }
                Text(
                    stringResource(R.string.meds_add_time), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        val digits = newTime.filter(Char::isDigit)
                        val t = runCatching {
                            if (newTime.contains(':')) LocalTime.parse(newTime.padStart(5, '0'))
                            else LocalTime.of(digits.dropLast(2).ifBlank { digits }.toInt(), if (digits.length > 2) digits.takeLast(2).toInt() else 0)
                        }.getOrNull()
                        if (t != null) { times = times + t; newTime = "" }
                    }.padding(8.dp),
                )
            }
            SectionLabel(stringResource(R.string.meds_days))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.meds_every_day), days.isEmpty()) { days = emptySet() }
                DayOfWeek.values().forEach { d ->
                    Chip(d.getDisplayName(JTextStyle.SHORT_STANDALONE, Italian), d in days) { days = if (d in days) days - d else days + d }
                }
            }
            SectionLabel(stringResource(R.string.meds_until_label))
            Box(Modifier.width(160.dp)) { Field(until, "gg/mm/aaaa", { until = it.take(10) }) }
            if (initial.id != 0L) {
                Text(
                    stringResource(R.string.delete), color = Color(0xFF8C2F39), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
