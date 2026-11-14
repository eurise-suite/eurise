package eu.eurise.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.model.Recurrence
import eu.eurise.core.data.model.StepProgress
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.designsystem.format.dueLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.LocalEuriseColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import eu.eurise.core.designsystem.component.CardDivider

internal val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Colore della tinta con questo nome nel tema attuale. */
@Composable
@ReadOnlyComposable
internal fun tintColor(name: String?): Color = eu.eurise.core.designsystem.theme.tintTone(name)

/** Colore dell'importanza alta, con il suo fondo tenue. */
@Composable
@ReadOnlyComposable
internal fun importantColors(): Pair<Color, Color> {
    val dark = LocalEuriseColors.current.isDark
    val ink = tintColor(CalendarTint.Borgogna.name)
    return ink to if (dark) lerp(Color(0xFF14171C), ink, 0.28f) else lerp(Color.White, ink, 0.14f)
}

/** Casella tonda da 44 dp (il cerchio visibile è di 20): spunta l'attività. */
@Composable
internal fun CheckCircle(done: Boolean, tint: Color, label: String, onToggle: () -> Unit, size: Int = 20) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(Modifier.size(size.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.check(3f), null, tint = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.size((size * 0.62f).dp))
            }
        } else {
            Box(Modifier.size(size.dp).border(2.dp, tint, CircleShape))
        }
    }
}

/** Testo breve di una ripetizione: "ogni giorno", "ogni lunedì", "nei giorni feriali"… */
@Composable
internal fun repeatLabel(rule: String?): String? {
    val r = Recurrence.parse(rule) ?: return null
    return when {
        r.frequency == Recurrence.Frequency.Weekly && r.days == Recurrence.WORKDAYS -> stringResource(R.string.repeat_workdays)
        r.frequency == Recurrence.Frequency.Weekly && r.days.size == 1 && r.interval == 1 ->
            stringResource(R.string.repeat_every_weekday, r.days.first().getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN))
        r.interval > 1 -> when (r.frequency) {
            Recurrence.Frequency.Daily -> stringResource(R.string.repeat_every_n_days, r.interval)
            Recurrence.Frequency.Weekly -> stringResource(R.string.repeat_every_n_weeks, r.interval)
            Recurrence.Frequency.Monthly -> stringResource(R.string.repeat_every_n_months, r.interval)
            Recurrence.Frequency.Yearly -> stringResource(R.string.repeat_every_n_years, r.interval)
        }
        else -> when (r.frequency) {
            Recurrence.Frequency.Daily -> stringResource(R.string.repeat_daily)
            Recurrence.Frequency.Weekly -> stringResource(R.string.repeat_weekly)
            Recurrence.Frequency.Monthly -> stringResource(R.string.repeat_monthly)
            Recurrence.Frequency.Yearly -> stringResource(R.string.repeat_yearly)
        }
    }
}

/**
 * Una riga di attività dentro una scheda bianca: casella nella tinta dell'elenco, titolo e una riga di dettagli
 * (ora o giorno, ripetizione, passi, elenco, nota), più il segno "Importante".
 *
 * @param showDay se scrivere anche il giorno (no nelle sezioni che sono già un giorno).
 * @param divider se tracciare la linea sopra (tutte le righe tranne la prima della scheda).
 */
@Composable
internal fun TaskItem(
    task: Task,
    list: TaskList?,
    progress: StepProgress?,
    today: LocalDate,
    showDay: Boolean,
    divider: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        if (divider) CardDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(onClick = onOpen)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(2.dp))
            val label = stringResource(if (task.done) R.string.tasks_mark_open else R.string.tasks_mark_done, task.title)
            CheckCircle(task.done, tintColor(list?.tint), label, onToggle)
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.done) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildList {
                    val due = task.due
                    val time = task.time
                    if (due != null && (showDay || due.isBefore(today)) && !task.done) {
                        val d = dueLabel(due, today)
                        add(if (time != null) "${d.text} ${time.format(TimeFormat)}" else d.text)
                    } else if (time != null && !task.done) {
                        add(time.format(TimeFormat))
                    }
                    repeatLabel(task.repeat)?.let { add(it) }
                    progress?.takeIf { it.total > 0 }?.let { add(stringResource(R.string.tasks_steps_progress, it.done, it.total)) }
                    if (list != null && list.id != TaskList.MAIN) add(list.name)
                    if (task.noteId != null) add(stringResource(R.string.tasks_from_note))
                }
                if (details.isNotEmpty()) {
                    val overdue = !task.done && task.due?.isBefore(today) == true
                    Text(
                        details.joinToString(" · "),
                        fontSize = 12.sp,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (overdue) importantColors().first else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (task.important && !task.done) ImportantBadge()
        }
    }
}

@Composable
internal fun ImportantBadge() {
    val (ink, fill) = importantColors()
    Row(
        Modifier.height(24.dp).clip(CircleShape).background(fill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(EuriseIcons.flag(2.4f), null, tint = ink, modifier = Modifier.size(12.dp))
        Text(stringResource(R.string.tasks_important), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

