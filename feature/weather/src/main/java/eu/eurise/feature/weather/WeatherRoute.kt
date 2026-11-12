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
    var openDay by remember { mutableStateOf<java.time.LocalDate?>(null) }
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
    val dayOpen = openDay
    val fc = state?.forecast
    if (dayOpen != null && fc != null) {
        BackHandler { openDay = null }
        DayScreen(fc, dayOpen, onBack = { openDay = null })
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
                        Row(Modifier.fillMaxWidth().height(48.dp).clickable { openDay = d.date }, verticalAlignment = Alignment.CenterVertically) {
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
                            Spacer(Modifier.width(6.dp))
                            Icon(EuriseIcons.chevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
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

/** Un'ombra leggera sotto i testi bianchi: si leggono anche sopra le nuvole chiare e la neve. */
private val Readable = androidx.compose.ui.text.TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), androidx.compose.ui.geometry.Offset(0f, 1.5f), 6f),
)

/** Fascia in alto: luogo con bandiera, temperatura grande, cielo, massima, minima e percepita. Il colore segue il cielo. */
@Composable
private fun Header(s: MeteoState?, loading: Boolean, topInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit, onRefresh: () -> Unit) {
    val f = s?.forecast
    // Il tempo che fa, disegnato (pioggia, neve, sole, nuvole…), come negli sfondi della Scrivania.
    Box(Modifier.fillMaxWidth()) {
    SkyScene(f?.now?.sky, f?.now?.day ?: true, Modifier.matchParentSize())
    Column(
        Modifier.fillMaxWidth()
            .padding(top = topInset + 6.dp, start = 12.dp, end = 12.dp, bottom = 64.dp),
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
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = Readable,
            )
            if (f != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.now.temp}°", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 76.sp, style = Readable)
                    Spacer(Modifier.weight(1f))
                    Icon(skyIcon(f.now.sky, f.now.day), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(84.dp))
                }
                Text(MeteoText.sky(f.now.sky), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = Readable)
                val today = f.days.firstOrNull()
                if (today != null) {
                    Text(
                        stringResource(R.string.weather_minmax, today.max, today.min, f.now.feels),
                        color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, style = Readable,
                    )
                }
            } else {
                Spacer(Modifier.height(80.dp))
            }
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


/**
 * Un giorno della settimana aperto (richiesta del proprietario, 12 novembre): il tempo disegnato, massima e minima, ora per
 * ora, quando piove, vento e sole. Le ore vengono dalla stessa previsione di Open-Meteo: nessuna richiesta in più.
 */
@Composable
private fun DayScreen(f: eu.eurise.core.data.weather.Forecast, date: java.time.LocalDate, onBack: () -> Unit) {
    val d = f.days.firstOrNull { it.date == date } ?: return onBack()
    val today = LocalDateTime.now(f.zone).toLocalDate()
    val hours = f.allHours.filter { it.time.toLocalDate() == date }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("head") {
            Box(Modifier.fillMaxWidth()) {
                SkyScene(d.sky, day = true, modifier = Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding() + 6.dp, start = 12.dp, end = 12.dp, bottom = 64.dp)) {
                    RoundButton(EuriseIcons.chevronLeft(), stringResource(R.string.weather_back), onBack)
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            MeteoText.dayName(date, today) + " " + date.dayOfMonth,
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = Readable,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.max}°", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 68.sp, style = Readable)
                            Spacer(Modifier.width(10.dp))
                            Text("${d.min}°", color = Color.White.copy(alpha = 0.85f), fontSize = 28.sp, fontWeight = FontWeight.Bold, style = Readable)
                        }
                        Text(MeteoText.sky(d.sky), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = Readable)
                    }
                }
            }
        }
        if (hours.isNotEmpty()) item("hours") {
            Card(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 12.dp)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(hours, key = { it.time.toString() }) { h ->
                        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${h.time.hour}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val dayHour = d.sunrise?.let { r -> d.sunset?.let { s -> !h.time.isBefore(r) && h.time.isBefore(s) } } ?: (h.time.hour in 7..18)
                            Icon(skyIcon(h.sky, dayHour), null, tint = skyTint(h.sky), modifier = Modifier.size(20.dp))
                            Text("${h.temp}°", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (h.rainChance >= 10) "${h.rainChance}%" else "", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        item("story") {
            Card(Modifier.padding(horizontal = 16.dp)) {
                Text(dayStory(d, hours), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        item("tiles") {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(stringResource(R.string.weather_day_rain), Modifier.weight(1f)) {
                    Text("${d.rainChance}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Small(stringResource(R.string.weather_day_rain_sub))
                }
                Tile(stringResource(R.string.weather_wind), Modifier.weight(1f)) {
                    Text("${d.gustKmh}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Small(stringResource(R.string.weather_day_gusts))
                }
                Tile(stringResource(R.string.weather_sun), Modifier.weight(1f)) {
                    Text(d.sunrise?.format(HourMin) ?: "—", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Small(stringResource(R.string.weather_sunset, d.sunset?.format(HourMin) ?: "—"))
                }
            }
        }
    }
}

/** Una frase sul giorno: quando piove (le ore con probabilità alta, unite in fasce) o che resta asciutto. */
@Composable
private fun dayStory(d: eu.eurise.core.data.weather.Day, hours: List<eu.eurise.core.data.weather.Hour>): String {
    val wet = setOf(Sky.Drizzle, Sky.Rain, Sky.Showers, Sky.Storm, Sky.Snow)
    val rainy = hours.filter { it.rainChance >= 40 || (it.sky in wet && it.rainChance >= 25) }.map { it.time.hour }
    if (rainy.isEmpty()) return stringResource(if (d.rainChance >= 30) R.string.weather_day_maybe else R.string.weather_day_dry, d.max, d.min)
    val spans = mutableListOf<IntRange>()
    rainy.forEach { h -> if (spans.isNotEmpty() && spans.last().last == h - 1) spans[spans.size - 1] = spans.last().first..h else spans += h..h }
    val text = spans.joinToString(", ") { r -> if (r.first == r.last) "verso le ${r.first}" else "dalle ${r.first} alle ${r.last + 1}" }
    val what = if (hours.any { it.sky == Sky.Snow && it.time.hour in rainy }) R.string.weather_day_snow else if (hours.any { it.sky == Sky.Storm && it.time.hour in rainy }) R.string.weather_day_storm else R.string.weather_day_wet
    return stringResource(what, text)
}
