package eu.eurise.office.home

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.office.R
import eu.eurise.office.data.Reports

private val Ink = Color(0xFF1F2A44)
private val Sand = Color(0xFFF3E6CC)
private val Brass = Color(0xFF8A6A1F)
private val Green = Color(0xFF4F6B5A)
private val Wine = Color(0xFF8C2F39)

/** «Cosa è successo» in una frase, dal tipo di chiusura e da chi si è chiuso. */
@Composable
fun reportWhat(r: Reports.Report): String {
    val who = stringResource(if (r.exit.engine) R.string.report_who_engine else R.string.report_who_office)
    val doing = when (r.docKind) {
        "Text" -> stringResource(R.string.report_doing_text)
        "Sheet" -> stringResource(R.string.report_doing_sheet)
        "Slides" -> stringResource(R.string.report_doing_slides)
        "" -> ""
        else -> stringResource(R.string.report_doing_other)
    }
    val base = when (Reports.kindOf(r.exit)) {
        Reports.Kind.Memory -> stringResource(R.string.report_what_memory, who)
        Reports.Kind.NotResponding -> stringResource(R.string.report_what_anr, who)
        else -> stringResource(R.string.report_what_crash, who)
    }
    return if (doing.isEmpty()) "$base." else "$base $doing."
}

@Composable
private fun whenText(time: Long): String {
    val context = LocalContext.current
    return DateUtils.getRelativeDateTimeString(context, time, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
}

/** Tavola OffRapporto: la scheda sopra i recenti, solo alla prima apertura dopo una chiusura improvvisa. */
@Composable
fun ReportCard(report: Reports.Report, onSee: () -> Unit, onIgnore: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Sand), contentAlignment = Alignment.Center) {
                Text("!", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Brass)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.report_card_title), fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)
                Text(
                    whenText(report.exit.time) + " · " + reportWhat(report) + " " + stringResource(R.string.report_card_untouched),
                    fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(EuriseIcons.lock(), contentDescription = null, tint = Green, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.report_stays_here), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Green)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSee, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)) {
                Text(stringResource(R.string.report_see), fontWeight = FontWeight.ExtraBold)
            }
            TextButton(onClick = onIgnore, modifier = Modifier.height(44.dp)) { Text(stringResource(R.string.report_ignore), fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Tavola OffRapportoVedi: cosa è successo, quando, dove; la parte tecnica a richiesta; cosa c'è dentro, con il nome del
 * file solo se lo si sceglie; «Condividi il rapporto…» (a chi si sceglie), «Copia il testo», «Elimina». Con più rapporti,
 * prima l'elenco (Impostazioni › Rapporti dei problemi).
 */
@Composable
fun ReportScreen(
    reports: List<Reports.Report>,
    first: Reports.Report?,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (Reports.Report) -> Unit,
    onClose: () -> Unit,
) {
    var current by remember(first) { mutableStateOf(first ?: reports.singleOrNull()) }
    val shown = current
    BackHandler { if (shown != null && first == null && reports.size > 1) current = null else onClose() }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (shown != null && first == null && reports.size > 1) current = null else onClose() }) {
                Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.report_back))
            }
            Text(stringResource(if (shown == null) R.string.report_list_title else R.string.report_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (shown == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (reports.isEmpty()) Text(stringResource(R.string.report_none), color = scheme.onSurfaceVariant, fontSize = 14.sp)
                else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                    reports.forEachIndexed { i, r ->
                        if (i > 0) CardDivider()
                        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { current = r }.padding(vertical = 10.dp)) {
                            Text(reportWhat(r), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(whenText(r.exit.time), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
        var withName by remember(shown) { mutableStateOf(false) }
        var technical by remember(shown) { mutableStateOf(false) }
        val what = reportWhat(shown)
        val whenLabel = whenText(shown.exit.time)
        val where = stringResource(R.string.report_where_value, shown.officeVersion, shown.engineVersion.ifEmpty { "–" }, shown.android, shown.device)
        val text = buildString {
            append("Rapporto del problema · Eurise Office\n\n")
            append(what).append('\n').append(whenLabel).append('\n').append(where).append("\n\n")
            if (withName && shown.fileName.isNotEmpty()) append("file: ").append(shown.fileName).append('\n')
            append(Reports.technical(shown))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                Field(stringResource(R.string.report_what_label), what)
                CardDivider()
                Field(stringResource(R.string.report_when_label), whenLabel)
                CardDivider()
                Field(stringResource(R.string.report_where_label), where)
                CardDivider()
                Text(
                    stringResource(R.string.report_technical), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ink,
                    modifier = Modifier.fillMaxWidth().clickable { technical = !technical }.padding(vertical = 12.dp),
                )
                if (technical) Text(
                    Reports.technical(shown), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.background).padding(10.dp),
                )
            }
            Text(stringResource(R.string.report_inside).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Green,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                Inside("✓", Green, stringResource(R.string.report_inside_yes))
                CardDivider()
                Inside("✕", Wine, stringResource(R.string.report_inside_no))
                if (shown.fileName.isNotEmpty()) {
                    CardDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.report_add_name), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.report_add_name_detail, shown.fileName), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                        Switch(checked = withName, onCheckedChange = { withName = it })
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_you_choose), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
            Button(onClick = { onShare(text) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White)) {
                Text(stringResource(R.string.report_share), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCopy(text) }, modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface)) {
                    Text(stringResource(R.string.report_copy), fontWeight = FontWeight.Bold, color = Ink)
                }
                TextButton(onClick = { onDelete(shown); current = null }, modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(scheme.surface)) {
                    Text(stringResource(R.string.report_delete), fontWeight = FontWeight.Bold, color = Wine)
                }
            }
        }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.5.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Inside(mark: String, color: Color, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(mark, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(text, fontSize = 13.5.sp)
    }
}
