package eu.eurise.feature.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.R

/**
 * Griglia e icone: colonne e righe dei preferiti, dimensione e nomi delle icone, colore d'accento.
 * Le modifiche si vedono subito sulla home dietro il foglio.
 */
@Composable
fun AppearanceSheet(
    settings: LauncherSettings,
    dark: Boolean,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onClose: () -> Unit,
    onEditCategories: () -> Unit,
    onAod: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
                Text(stringResource(R.string.appearance_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.card_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(6.dp),
                )
            }
            // Nome per il saluto della home.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.appearance_name), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    if (settings.userName.isEmpty()) Text(stringResource(R.string.appearance_name_hint), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.text.BasicTextField(
                        value = settings.userName,
                        onValueChange = { v -> onChange { it.copy(userName = v.take(30)) } },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Stepper(stringResource(R.string.appearance_columns), settings.columns, LauncherSettings.ColumnChoices) { v -> onChange { it.copy(columns = v) } }
            Stepper(stringResource(R.string.appearance_rows), settings.rows, LauncherSettings.RowChoices) { v -> onChange { it.copy(rows = v) } }
            Column {
                Row {
                    Text(stringResource(R.string.appearance_icon_size), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${settings.iconSize} dp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = settings.iconSize.toFloat(),
                    onValueChange = { v -> onChange { it.copy(iconSize = v.toInt()) } },
                    valueRange = LauncherSettings.IconSizes.first.toFloat()..LauncherSettings.IconSizes.last.toFloat(),
                )
            }
            listOf(
                Triple(R.string.appearance_pull_panel, settings.pullPanel) { v: Boolean -> onChange { it.copy(pullPanel = v) } },
                Triple(R.string.appearance_swipe_search, settings.swipeSearch) { v: Boolean -> onChange { it.copy(swipeSearch = v) } },
                Triple(R.string.appearance_left_handed, settings.leftHanded) { v: Boolean -> onChange { it.copy(leftHanded = v) } },
            ).forEach { (label, on, set) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = set)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appearance_labels), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = settings.showLabels, onCheckedChange = { v -> onChange { it.copy(showLabels = v) } })
            }
            IconThemeChooser(settings.iconTheme) { v -> onChange { it.copy(iconTheme = v) } }
            if (settings.iconTheme == eu.eurise.feature.launcher.apps.IconThemes.EURISE) Column {
                Text(stringResource(R.string.appearance_icon_colors), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(3.dp),
                ) {
                    // 0 = dall'app, 1 = per categoria, 2 = vario
                    val mode = when { settings.appIconColors -> 0; settings.variedIcons -> 2; else -> 1 }
                    listOf(0 to R.string.appearance_icons_app, 1 to R.string.appearance_icons_category, 2 to R.string.appearance_icons_varied).forEach { (value, label) ->
                        val on = mode == value
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(50))
                                .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable(role = Role.RadioButton) { onChange { it.copy(appIconColors = value == 0, variedIcons = value == 2) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(label), fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onEditCategories).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.categories_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.categories_summary, settings.categories.size),
                        fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onAod).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.aod_title), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.aod_summary), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Tema: segue lo sfondo oppure sempre chiaro / sempre scuro.
            Column {
                Text(stringResource(R.string.appearance_theme), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    listOf(R.string.theme_auto, R.string.theme_light, R.string.theme_dark).map { stringResource(it) },
                    settings.themeMode,
                ) { v -> onChange { it.copy(themeMode = v) } }
            }
            // Sfondo: automatico oppure uno scelto tra quelli disponibili.
            Column {
                Text(stringResource(R.string.wallpaper_pick), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val choices = listOf<Pair<String?, Int>>(
                    null to R.string.wallpaper_auto,
                    "season:Spring" to R.string.wp_spring, "season:Summer" to R.string.wp_summer,
                    "season:Autumn" to R.string.wp_autumn, "season:Winter" to R.string.wp_winter,
                    "fest:Christmas" to R.string.wp_christmas, "fest:NewYearsEve" to R.string.wp_newyear,
                    "fest:Epiphany" to R.string.wp_epiphany, "fest:Easter" to R.string.wp_easter,
                    "fest:Birthday" to R.string.wp_birthday, "fest:Anniversary" to R.string.wp_anniversary,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.forEach { (key, label) ->
                        val on = settings.wallpaper.fixed == key
                        Text(
                            stringResource(label), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .clickable(role = Role.RadioButton) { onChange { it.copy(wallpaper = it.wallpaper.copy(fixed = key)) } }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            // Lo sfondo cambia con: stagione, meteo, festività, momento della giornata.
            Column {
                Text(stringResource(R.string.wallpaper_changes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                listOf(
                    R.string.wallpaper_season to settings.wallpaper.season,
                    R.string.wallpaper_weather to settings.wallpaper.weather,
                    R.string.wallpaper_holidays to settings.wallpaper.holidays,
                    R.string.wallpaper_moment to settings.wallpaper.moment,
                ).forEachIndexed { i, (label, on) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { v ->
                            onChange {
                                it.copy(
                                    wallpaper = when (i) {
                                        0 -> it.wallpaper.copy(season = v)
                                        1 -> it.wallpaper.copy(weather = v)
                                        2 -> it.wallpaper.copy(holidays = v)
                                        else -> it.wallpaper.copy(moment = v)
                                    },
                                )
                            }
                        })
                    }
                }
            }
            AccentPicker(settings.accent, dark) { a -> onChange { it.copy(accent = a) } }
            Text(stringResource(R.string.appearance_hint), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, choices: IntRange, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(3.dp),
        ) {
            choices.forEach { option ->
                val on = option == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onPick(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$option", fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Le otto tinte della suite più un colore personale scelto con tonalità e luminosità. */
@Composable
private fun AccentPicker(accent: Accent, dark: Boolean, onPick: (Accent) -> Unit) {
    val custom = accent as? Accent.Custom
    var hue by remember { mutableFloatStateOf(custom?.let { hueOf(Color(it.argb)) } ?: 200f) }
    var light by remember { mutableFloatStateOf(0.42f) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.appearance_accent), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val names = tintNames()
        // Le otto tinte dei calendari, le cinque in più e il colore personale: due file da sette.
        val options: List<@Composable () -> Unit> =
            CalendarTint.entries.map { tint ->
                @Composable { Swatch(if (dark) tint.dark else tint.light, accent == Accent.Suite(tint), names[tint].orEmpty()) { onPick(Accent.Suite(tint)) } }
            } + ExtraTint.entries.map { tint ->
                @Composable {
                    Swatch(
                        if (dark) tint.dark else tint.light, accent == Accent.Extra(tint),
                        stringResource(extraName(tint)),
                    ) { onPick(Accent.Extra(tint)) }
                }
            } + listOf(
                @Composable {
                    Swatch(
                        color = custom?.color(dark) ?: Color.Transparent,
                        selected = custom != null,
                        label = stringResource(R.string.appearance_custom),
                        rainbow = custom == null,
                    ) { onPick(Accent.Custom(customColor(hue, light))) }
                },
            )
        options.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { it() }
                repeat(7 - row.size) { Spacer(Modifier.size(32.dp)) }
            }
        }
        if (custom != null) {
            Text(stringResource(R.string.appearance_hue), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient((0..6).map { Color.hsl(it * 60f, 0.45f, 0.5f) })),
            )
            Slider(value = hue, onValueChange = { hue = it; onPick(Accent.Custom(customColor(hue, light))) }, valueRange = 0f..360f)
            Text(stringResource(R.string.appearance_light), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = light, onValueChange = { light = it; onPick(Accent.Custom(customColor(hue, light))) }, valueRange = 0.2f..0.75f)
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, rainbow: Boolean = false, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(
                if (rainbow) Brush.sweepGradient((0..6).map { Color.hsl(it * 60f % 360f, 0.45f, 0.55f) })
                else Brush.linearGradient(listOf(color, color)),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun tintNames(): Map<CalendarTint, String> = mapOf(
    CalendarTint.BluNotte to stringResource(R.string.tint_blu_notte),
    CalendarTint.Salvia to stringResource(R.string.tint_salvia),
    CalendarTint.Ottone to stringResource(R.string.tint_ottone),
    CalendarTint.Borgogna to stringResource(R.string.tint_borgogna),
    CalendarTint.Petrolio to stringResource(R.string.tint_petrolio),
    CalendarTint.Prugna to stringResource(R.string.tint_prugna),
    CalendarTint.Terracotta to stringResource(R.string.tint_terracotta),
    CalendarTint.Grafite to stringResource(R.string.tint_grafite),
)

private fun extraName(tint: ExtraTint) = when (tint) {
    ExtraTint.Arancio -> R.string.tint_arancio
    ExtraTint.Viola -> R.string.tint_viola
    ExtraTint.Cuoio -> R.string.tint_cuoio
    ExtraTint.VerdeInglese -> R.string.tint_verde_inglese
    ExtraTint.AzzurroCostiera -> R.string.tint_azzurro_costiera
}

private fun hueOf(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        c.red -> ((c.green - c.blue) / d) % 6f
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    } * 60f
    return if (h < 0) h + 360f else h
}

/** Scelta tra poche voci, a pillola, come "Colore dei simboli". */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 13.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tema delle icone: i tondi della suite, le icone di sistema o un pacchetto di icone installato. */
@Composable
private fun IconThemeChooser(current: String, onChoose: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var packs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptyList<eu.eurise.feature.launcher.apps.IconTheme>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        packs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { eu.eurise.feature.launcher.apps.IconThemes.installed(context) }
    }
    val choices = listOf(
        eu.eurise.feature.launcher.apps.IconThemes.EURISE to stringResource(R.string.appearance_icon_theme_eurise),
        eu.eurise.feature.launcher.apps.IconThemes.SYSTEM to stringResource(R.string.appearance_icon_theme_system),
    ) + packs.map { it.packageName to it.label }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.appearance_icon_theme), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { (value, label) ->
                val on = value == current
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable(role = Role.RadioButton) { onChoose(value) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        label, fontSize = 13.5.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (packs.isEmpty()) Text(stringResource(R.string.appearance_icon_theme_hint), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
