package eu.eurise.feature.weather

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.weather.Alert
import eu.eurise.core.data.weather.MeteoRepository
import eu.eurise.core.data.weather.MeteoState
import eu.eurise.core.data.weather.MeteoText
import eu.eurise.core.data.weather.Sky
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Un impegno toccato da un'allerta: evento del Calendario o cosa da fare. */
data class Touched(val title: String, val start: LocalDateTime?, val isTask: Boolean, val open: () -> Unit)

/**
 * Meteo (tavole MeteoOggi e MeteoAllerta): adesso, le prossime ore, vento, pollini, sole, i giorni, un breve
 * aggiornamento scritto sul telefono e, se c'è, l'allerta ufficiale con gli impegni che cadono in quelle ore.
 * [touched] dà eventi e cose da fare tra due istanti (li fornisce l'app: Meteo non legge il Calendario da sé).
 */
@Composable
fun WeatherRoute(
    onBack: () -> Unit,
    touched: suspend (LocalDateTime, LocalDateTime) -> List<Touched> = { _, _ -> emptyList() },
) {
    val context = LocalContext.current
    val repo = remember { MeteoRepository(context) }
    var state by remember { mutableStateOf<MeteoState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var openAlert by remember { mutableStateOf<Alert?>(null) }
    val scope = rememberCoroutineScope()
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) reload++ }

    LaunchedEffect(reload) {
        loading = true
        if (state == null) state = repo.cached()
        state = repo.load(force = reload > 0)
        loading = false
    }

    val alert = openAlert
    if (alert != null) {
        BackHandler { openAlert = null }
        AlertScreen(alert, touched, onBack = { openAlert = null })
        return
    }
    BackHandler(onBack = onBack)

    val s = state
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("head") { Header(s, loading, topInset = insets.calculateTopPadding(), onBack = onBack, onRefresh = { scope.launch { reload++ } }) }
            if (s == null || s.forecast == null) {
                item("empty") {
                    Card(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(
                                when {
                                    s?.needsLocation == true -> R.string.weather_need_location
                                    loading -> R.string.weather_loading
                                    else -> R.string.weather_offline
                                },
                            ),
                            fontSize = 14.sp, lineHeight = 20.sp,
                        )
                        if (s?.needsLocation == true) {
                            Spacer(Modifier.height(10.dp))
                            Pill(stringResource(R.string.weather_use_location), strong = true) { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        }
                    }
                }
                return@LazyColumn
            }
            val f = s.forecast!!
            val now = LocalDateTime.now(f.zone)
            s.alerts.firstOrNull()?.let { a -> item("alert") { AlertBanner(a, now) { openAlert = a } } }
            item("hours") {
                Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 12.dp)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                        items(f.hours, key = { it.time.toString() }) { h ->
                            Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (h.time.hour == now.hour && h.time.toLocalDate() == now.toLocalDate()) stringResource(R.string.weather_now) else "${h.time.hour}",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(skyIcon(h.sky), null, tint = skyTint(h.sky), modifier = Modifier.size(20.dp))
                                Text("${h.temp}°", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (h.rainChance >= 10) "${h.rainChance}%" else "", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item("tiles") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tile(stringResource(R.string.weather_wind), Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // La freccia indica dove va il vento.
                            Icon(EuriseIcons.upload(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).rotate(f.now.windFrom + 180f))
                            Spacer(Modifier.width(4.dp))
                            Text("${f.now.windKmh}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Small(stringResource(R.string.weather_wind_detail, MeteoText.windFrom(f.now.windFrom), f.now.gustKmh))
                    }
                    Tile(stringResource(R.string.weather_pollen), Modifier.weight(1f)) {
                        val p = s.pollen
                        Text(
                            p?.let { MeteoText.pollen(it.level) } ?: "—", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                            color = when (p?.level) { 3 -> Color(0xFFB4441A); 2 -> Color(0xFF9A6B12); else -> Color(0xFF2F6B45) },
                        )
                        Small(p?.top?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: stringResource(if (p == null) R.string.weather_pollen_none else R.string.weather_pollen_low))
                    }
                    Tile(stringResource(R.string.weather_sun), Modifier.weight(1f)) {
                        val today = f.days.firstOrNull()
                        Text(today?.sunrise?.format(HourMin) ?: "—", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Small(stringResource(R.string.weather_sunset, today?.sunset?.format(HourMin) ?: "—"))
                    }
                }
            }
            item("days") {
                Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                    f.days.drop(1).forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(MeteoText.dayName(d.date, now.toLocalDate()), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(84.dp))
                            Icon(skyIcon(d.sky), null, tint = skyTint(d.sky), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                MeteoText.sky(d.sky) + if (d.rainChance >= 30) " · ${d.rainChance}%" else "",
                                fontSize = 14.sp, color = if (d.sky == Sky.Storm) Color(0xFF8A3A06) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (d.sky == Sky.Storm) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text("${d.min}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${d.max}°", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(38.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
            item("story") {
                val story = remember(s) { MeteoText.story(f, s.alerts, now) }
                Card(Modifier.padding(horizontal = 16.dp)) {
                    Caption(stringResource(R.string.weather_update, java.time.Instant.ofEpochMilli(s.updated).atZone(f.zone).toLocalTime().format(HourMin)))
                    Spacer(Modifier.height(4.dp))
                    Text(story.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(story.body, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item("credits") {
                Text(
                    stringResource(R.string.weather_credits), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(6.dp))
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

private val HourMin: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** Fascia in alto: luogo con bandiera, temperatura grande, cielo, massima, minima e percepita. Il colore segue il cielo. */
@Composable
private fun Header(s: MeteoState?, loading: Boolean, topInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit, onRefresh: () -> Unit) {
    val f = s?.forecast
    val (top, bottom) = skyGradient(f?.now?.sky, f?.now?.day ?: true)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .padding(top = topInset + 6.dp, start = 12.dp, end = 12.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(EuriseIcons.chevronLeft(), stringResource(R.string.weather_back), onBack)
            Spacer(Modifier.weight(1f))
            RoundButton(EuriseIcons.refresh(), stringResource(R.string.weather_refresh), onRefresh, dim = loading)
        }
        Column(Modifier.padding(horizontal = 8.dp)) {
            val place = s?.place
            Text(
                listOfNotNull(place?.label ?: stringResource(R.string.weather_here), place?.flag).joinToString("  "),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            if (f != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.now.temp}°", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 76.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(skyIcon(f.now.sky, f.now.day), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(84.dp))
                }
                Text(MeteoText.sky(f.now.sky), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val today = f.days.firstOrNull()
                if (today != null) {
                    Text(
                        stringResource(R.string.weather_minmax, today.max, today.min, f.now.feels),
                        color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, dim: Boolean = false) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White.copy(alpha = if (dim) 0.5f else 1f), modifier = Modifier.size(20.dp)) }
}

/** Le tinte dell'allerta: gialla, arancione, rossa (fondo, segno, testo). */
private fun alertColors(level: Int): Triple<Color, Color, Color> = when (level) {
    4 -> Triple(Color(0xFFF8DAD6), Color(0xFFC8372D), Color(0xFF7A1A12))
    3 -> Triple(Color(0xFFFBE6D6), Color(0xFFE07A1F), Color(0xFF8A3A06))
    else -> Triple(Color(0xFFFBF1CF), Color(0xFFD9A514), Color(0xFF6B5205))
}

@Composable
private fun AlertBanner(a: Alert, now: LocalDateTime, onOpen: () -> Unit) {
    val (bg, mark, fg) = alertColors(a.level)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(12.dp).height(34.dp).clip(RoundedCornerShape(6.dp)).background(mark))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.weather_alert_title, MeteoText.level(a.level), a.event.lowercase()), color = fg, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
            Text(span(a, now) + if (a.source.isNotBlank()) " · ${a.source}" else "", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        Icon(EuriseIcons.chevronRight(), null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

/** «Domani dalle 14 alle 22». */
private fun span(a: Alert, now: LocalDateTime): String {
    val on = a.onset
    val off = a.expires
    val day = on?.let { MeteoText.dayName(it.toLocalDate(), now.toLocalDate()) }
    return buildString {
        if (on == null || !on.isAfter(now)) append("In corso") else append("$day dalle ${on.hour}")
        if (off != null) append(if (off.toLocalDate() == (on ?: now).toLocalDate()) " alle ${off.hour}" else " a ${MeteoText.dayName(off.toLocalDate(), now.toLocalDate()).lowercase()} alle ${off.hour}")
    }
}

/** Allerta aperta: livello, fenomeno, quando e dove; gli impegni in quelle ore; cosa fare. */
@Composable
private fun AlertScreen(a: Alert, touched: suspend (LocalDateTime, LocalDateTime) -> List<Touched>, onBack: () -> Unit) {
    val context = LocalContext.current
    val now = LocalDateTime.now()
    var list by remember(a) { mutableStateOf<List<Touched>?>(null) }
    LaunchedEffect(a) {
        val from = (a.onset ?: now).minusHours(1)
        val to = a.expires ?: from.plusHours(12)
        list = runCatching { touched(from, to) }.getOrDefault(emptyList())
    }
    val (bg, mark, fg) = alertColors(a.level)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.weather_back), modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                val day = a.onset?.let { MeteoText.dayName(it.toLocalDate(), now.toLocalDate()).lowercase() } ?: "oggi"
                Text(stringResource(R.string.weather_alert_of, day), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(mark))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.weather_alert_level, MeteoText.level(a.level)).uppercase(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp)
                }
                Text(a.event.replaceFirstChar { it.titlecase() }, color = fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(span(a, now) + " · " + a.area, color = fg, fontSize = 13.sp, lineHeight = 19.sp)
                if (a.description.isNotBlank()) Text(a.description, color = fg, fontSize = 13.sp, lineHeight = 19.sp)
                Text(stringResource(R.string.weather_alert_source, a.source.ifBlank { "MeteoAlarm" }), color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Section(stringResource(R.string.weather_alert_yours)) }
        item {
            Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                val items = list
                when {
                    items == null -> Text(stringResource(R.string.weather_loading), fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                    items.isEmpty() -> Text(stringResource(R.string.weather_alert_free), fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
                    else -> items.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (t.isTask) EuriseIcons.check() else EuriseIcons.calendar(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    t.title + (t.start?.let { " · ${it.format(HourMin)}" } ?: ""),
                                    fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                val start = t.start
                                if (start != null && !t.isTask) {
                                    val remind = start.minusMinutes(50)
                                    Text(stringResource(R.string.weather_alert_advice), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (remind.isAfter(now)) Pill(stringResource(R.string.weather_remind_at, remind.format(HourMin)), strong = true) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(AlarmClock.ACTION_SET_ALARM)
                                                        .putExtra(AlarmClock.EXTRA_HOUR, remind.hour)
                                                        .putExtra(AlarmClock.EXTRA_MINUTES, remind.minute)
                                                        .putExtra(AlarmClock.EXTRA_MESSAGE, t.title)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                        }
                                        Pill(stringResource(R.string.weather_open), strong = false, onClick = t.open)
                                    }
                                } else {
                                    Text(stringResource(R.string.weather_alert_task), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Section(stringResource(R.string.weather_alert_todo)) }
        item {
            Card(Modifier.padding(horizontal = 16.dp)) {
                advice(a.event).forEach { line ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", fontSize = 13.sp, modifier = Modifier.width(14.dp))
                        Text(line, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.weather_alert_privacy), fontSize = 11.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Consigli brevi secondo il fenomeno (indicazioni generali della Protezione Civile). */
private fun advice(event: String): List<String> {
    val e = event.lowercase()
    return when {
        "temporal" in e || "thunder" in e || "idro" in e || "flood" in e || "pioggia" in e || "rain" in e -> listOf(
            "Evita sottopassi e strade vicino a fiumi e torrenti.",
            "Metti al riparo vasi e oggetti sul balcone.",
            "Controlla gli aggiornamenti prima di partire.",
        )
        "vento" in e || "wind" in e -> listOf(
            "Fissa o ritira gli oggetti su balconi e terrazzi.",
            "Stai lontano da alberi, impalcature e cartelloni.",
            "Attenzione in auto sui viadotti e con mezzi leggeri.",
        )
        "neve" in e || "snow" in e || "ghiaccio" in e || "ice" in e -> listOf(
            "Parti solo se serve, con gomme invernali o catene.",
            "Tieni la distanza e evita frenate brusche.",
            "Attenzione al ghiaccio sui marciapiedi al mattino.",
        )
        "caldo" in e || "heat" in e || "temperature" in e -> listOf(
            "Evita di uscire nelle ore più calde.",
            "Bevi spesso e tieni d'occhio le persone fragili.",
            "Arieggia la casa nelle ore fresche.",
        )
        else -> listOf("Segui le indicazioni del tuo Comune e della Protezione Civile.", "Controlla gli aggiornamenti prima di partire.")
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(14.dp), content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(padding),
    ) { content() }
}

@Composable
private fun Tile(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Caption(title)
        content()
    }
}

@Composable
private fun Caption(text: String) = Text(
    text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Small(text: String) = Text(text, fontSize = 11.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)

@Composable
private fun Section(text: String) = Box(Modifier.padding(start = 20.dp, top = 8.dp)) { Caption(text) }

@Composable
private fun Pill(text: String, strong: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
        color = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun skyIcon(s: Sky, day: Boolean = true): ImageVector = when (s) {
    Sky.Clear -> if (day) EuriseIcons.sun() else EuriseIcons.moon()
    Sky.PartlyCloudy, Sky.Cloudy -> EuriseIcons.cloud()
    Sky.Fog -> EuriseIcons.fog()
    Sky.Drizzle, Sky.Rain, Sky.Showers -> EuriseIcons.rain()
    Sky.Snow -> EuriseIcons.snow()
    Sky.Storm -> EuriseIcons.storm()
}

@Composable
private fun skyTint(s: Sky): Color = when (s) {
    Sky.Clear -> Color(0xFFD9A514)
    Sky.Storm -> Color(0xFFB4441A)
    Sky.Rain, Sky.Showers, Sky.Drizzle -> Color(0xFF3E5C8A)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Colori della fascia in alto: azzurro col sereno, grigio-blu con nuvole e pioggia, ardesia col temporale, blu notte di sera. */
private fun skyGradient(s: Sky?, day: Boolean): Pair<Color, Color> = when {
    !day -> Color(0xFF1B2540) to Color(0xFF3A4A6B)
    s == Sky.Clear || s == Sky.PartlyCloudy -> Color(0xFF4F86C6) to Color(0xFF9CC3E6)
    s == Sky.Storm -> Color(0xFF4A5363) to Color(0xFF8792A3)
    s == Sky.Snow || s == Sky.Fog -> Color(0xFF8C97A6) to Color(0xFFC7CED8)
    else -> Color(0xFF6F7F96) to Color(0xFFA9B4C3)
}
