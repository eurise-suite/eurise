package app.organicmaps.eurise.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.organicmaps.R
import app.organicmaps.eurise.OpeningRows
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.bookmarks.data.Metadata
import app.organicmaps.sdk.editor.OhState
import app.organicmaps.sdk.editor.OpeningHours
import app.organicmaps.sdk.editor.data.Timetable
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/**
 * «Orari» della scheda aperta (tavola MapLuogoAperta, approvata) al posto degli orari di CoMaps: oggi in grassetto, in
 * verde se è aperto adesso, e i due giorni dopo; toccando la scheda tutta la settimana, con la pressione lunga si copia.
 * Gli orari sono quelli di OpenStreetMap nelle mappe sul telefono, letti con il lettore di CoMaps.
 */
internal object EuriseOrari {
    /** Le tabelle di CoMaps in minuti; vuota se il testo di OSM non si legge (allora si mostra com'è). */
    fun tables(oh: String): List<OpeningRows.Table> {
        val tt: Array<Timetable> = runCatching { OpeningHours.nativeTimetablesFromString(oh) }.getOrNull() ?: return emptyList()
        return tt.map { t ->
            OpeningRows.Table(
                t.weekdays, t.isFullday, minutes(t.workingTimespan.start), minutes(t.workingTimespan.end),
                t.closedTimespans.map { minutes(it.start) }.toIntArray(), t.closedTimespans.map { minutes(it.end) }.toIntArray(),
            )
        }
    }

    private fun minutes(hm: app.organicmaps.sdk.editor.data.HoursMinutes) = (hm.hours * 60 + hm.minutes).toInt()

    /** Aperto adesso (vero), chiuso (falso) o non si sa (null), con la regola di CoMaps. */
    fun openNow(oh: String): Boolean? {
        val tt = runCatching { OpeningHours.nativeTimetablesFromString(oh) }.getOrNull() ?: return null
        if (tt.isEmpty()) return null
        return when (runCatching { OpeningHours.nativeCurrentState(tt).state }.getOrNull()) {
            OhState.State.Open -> true
            OhState.State.Closed -> false
            else -> null
        }
    }

    /** «venerdì» nella lingua del telefono (1 domenica … 7 sabato). */
    fun dayName(day: Int): String = DateFormatSymbols.getInstance(Locale.getDefault()).weekdays[day]

    fun capital(s: String) = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    /** Tutta la settimana come testo, da copiare: una riga per giorno a partire da oggi. */
    fun weekText(c: Context, tables: List<OpeningRows.Table>, today: Int): String {
        val h24 = DateFormat.is24HourFormat(c)
        return OpeningRows.daysFrom(today, 7).joinToString("\n") { d ->
            capital(dayName(d)) + " " + OpeningRows.format(OpeningRows.shifts(tables, d), h24,
                c.getString(R.string.eurise_oh_all_day), c.getString(R.string.eurise_oh_closed))
        }
    }
}

/** Verde «aperto» della tavola (#2F6B45) e, di sera, quello della suite. */
private fun openColor(c: Colors) = if (c === Notte) c.ok else Color(0xFF2F6B45)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Orari(c: Colors, place: MapObject) {
    val context = LocalContext.current
    val oh = remember(place) { place.getMetadata(Metadata.MetadataType.FMD_OPEN_HOURS).orEmpty().trim() }
    if (oh.isEmpty()) return
    val tables = remember(oh) { EuriseOrari.tables(oh) }
    val open = remember(oh) { EuriseOrari.openNow(oh) }
    val today = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    var all by remember(oh) { mutableStateOf(false) }
    val h24 = DateFormat.is24HourFormat(context)
    val allDay = stringResource(R.string.eurise_oh_all_day)
    val closed = stringResource(R.string.eurise_oh_closed)
    val todayLabel = stringResource(R.string.eurise_oh_today, EuriseOrari.dayName(today))
    val same = tables.isNotEmpty() && OpeningRows.sameEveryDay(tables)

    Heading(c, stringResource(R.string.eurise_oh_title))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
            .combinedClickable(
                enabled = true,
                onClick = { if (tables.isNotEmpty() && !same) all = !all },
                onLongClick = {
                    EuriseLuogoAperta.copy(context, if (tables.isEmpty()) oh else EuriseOrari.weekText(context, tables, today))
                },
            )
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            // Testo di OSM che il lettore non capisce: si mostra com'è («off» e «closed» diventano «chiuso»).
            tables.isEmpty() -> DayRow(c, todayLabel, if (oh == "off" || oh == "closed") closed else oh, first = true, valueColor = c.ink)
            same -> {
                val value = OpeningRows.format(OpeningRows.shifts(tables, today), h24, allDay, closed)
                DayRow(c, todayLabel, value, first = true, valueColor = if (open == true) openColor(c) else c.ink)
                DayRow(c, stringResource(R.string.eurise_oh_every_day), value, first = false, valueColor = c.muted)
            }
            else -> {
                OpeningRows.daysFrom(today, if (all) 7 else 3).forEachIndexed { i, d ->
                    val value = OpeningRows.format(OpeningRows.shifts(tables, d), h24, allDay, closed)
                    if (i == 0) DayRow(c, todayLabel, value, first = true, valueColor = if (open == true) openColor(c) else c.ink)
                    else DayRow(c, EuriseOrari.capital(EuriseOrari.dayName(d)), value, first = false, valueColor = c.muted)
                }
                BasicText(
                    stringResource(if (all) R.string.eurise_oh_less else R.string.eurise_oh_week),
                    Modifier.padding(top = 2.dp),
                    style = TextStyle(color = c.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun DayRow(c: Colors, day: String, value: String, first: Boolean, valueColor: Color) {
    val size = if (first) 14.sp else 13.sp
    val weight = if (first) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        BasicText(day, style = TextStyle(color = if (first) c.ink else c.muted, fontSize = size, fontWeight = weight))
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
            BasicText(value, style = TextStyle(color = valueColor, fontSize = size, fontWeight = weight, textAlign = TextAlign.End))
        }
    }
}
