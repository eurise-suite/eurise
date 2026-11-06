package eu.eurise.core.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.eurise.core.designsystem.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Etichetta di una scadenza, con l'indicazione se è già passata. */
data class DueLabel(val text: String, val overdue: Boolean)

/**
 * Descrive una scadenza rispetto a [today]: "Oggi", "Domani", "Ieri", il giorno della settimana
 * per i prossimi sei giorni, altrimenti la data ("12 nov").
 */
@Composable
fun dueLabel(due: LocalDate, today: LocalDate): DueLabel {
    val days = due.toEpochDay() - today.toEpochDay()
    val text = when (days) {
        0L -> stringResource(R.string.ds_today)
        1L -> stringResource(R.string.ds_tomorrow)
        -1L -> stringResource(R.string.ds_yesterday)
        in 2L..6L -> due.format(WeekdayFormat).replaceFirstChar { it.titlecase(Locale.ITALIAN) }
        else -> due.format(ShortDateFormat)
    }
    return DueLabel(text, overdue = days < 0)
}

/** Data lunga per i titoli, per esempio "Venerdì 6 novembre". */
fun longDate(date: LocalDate): String =
    date.format(LongDateFormat).replaceFirstChar { it.titlecase(Locale.ITALIAN) }

/** Ora o data breve di una modifica, per le liste: "08:31" se è di oggi, altrimenti "4 nov". */
fun editedAt(epochMillis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (moment.toLocalDate() == today) moment.format(TimeFormat) else moment.toLocalDate().format(ShortDateFormat)
}

private val WeekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.ITALIAN)
private val ShortDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
private val LongDateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val TimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.ITALIAN)
