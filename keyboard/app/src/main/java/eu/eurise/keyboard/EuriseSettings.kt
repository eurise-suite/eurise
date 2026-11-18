package eu.eurise.keyboard

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * Impostazioni della Tastiera nello stile di Eurise (tavola TastImpostazioniFrase, approvata il 18 novembre): le scelte
 * di tutti i giorni in schede, le schermate di HeliBoard dietro le righe e in «Tutte le impostazioni».
 */
@Composable
fun EuriseMainSettings(
    onBack: () -> Unit,
    onLanguages: () -> Unit,
    onTouch: () -> Unit,
    onAppearance: () -> Unit,
    onToolbar: () -> Unit,
    onDictionaries: () -> Unit,
    onPreferences: () -> Unit,
    onGesture: () -> Unit,
    onCorrection: () -> Unit,
    onLayouts: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    SearchSettingsScreen(onClickBack = onBack, title = stringResource(R.string.eurise_set_title), settings = emptyList()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.eurise_set_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            val languages = remember { SubtypeSettings.getEnabledSubtypes(true).joinToString(", ") { it.displayName() } }
            Section(stringResource(R.string.eurise_set_writing)) {
                NavRow(stringResource(R.string.eurise_set_languages), languages, onLanguages)
                Divider()
                PrefSwitch(stringResource(R.string.eurise_set_autocorrect), stringResource(R.string.eurise_set_autocorrect_detail), Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)
                Divider()
                PrefSwitch(stringResource(R.string.eurise_set_phrase), stringResource(R.string.eurise_set_phrase_detail), Phrases.PREF_ON, true)
            }
            Section(stringResource(R.string.eurise_set_precision)) {
                val learned = remember { touchPrefs(context).let { TouchModel.init(context); TouchModel.learned } }
                NavRow(stringResource(R.string.eurise_set_touch), stringResource(R.string.eurise_set_touch_detail, learned), onTouch)
                Divider()
                PrefSwitch(stringResource(R.string.eurise_set_popup), null, Settings.PREF_POPUP_ON, Defaults.PREF_POPUP_ON)
            }
            Section(stringResource(R.string.eurise_set_look)) {
                NavRow(stringResource(R.string.eurise_set_theme), stringResource(R.string.eurise_set_theme_detail), onAppearance)
            }
            Section(stringResource(R.string.eurise_set_bar_data)) {
                NavRow(stringResource(R.string.eurise_set_bar), stringResource(R.string.eurise_set_bar_detail), onToolbar)
                Divider()
                NavRow(stringResource(R.string.eurise_set_dictionaries), stringResource(R.string.eurise_set_dictionaries_detail), onDictionaries)
                Divider()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.eurise_set_privacy), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Tacche(10, stringResource(R.string.eurise_set_privacy_detail))
                    }
                }
            }
            var more by remember { mutableStateOf(false) }
            Section(stringResource(R.string.eurise_set_all)) {
                NavRow(stringResource(if (more) R.string.eurise_set_all_hide else R.string.eurise_set_all_show), null) { more = !more }
                if (more) {
                    listOf(
                        R.string.settings_screen_preferences to onPreferences,
                        R.string.settings_screen_gesture to onGesture,
                        R.string.settings_screen_correction to onCorrection,
                        R.string.settings_screen_secondary_layouts to onLayouts,
                        R.string.settings_screen_advanced to onAdvanced,
                        R.string.settings_screen_about to onAbout,
                    ).forEach { (label, go) -> Divider(); NavRow(stringResource(label), null, go) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** «Come tocchi»: dove cade il dito su ogni tasto, gli interruttori e «Ricomincia». */
@Composable
fun EuriseTouchSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { TouchModel.init(context); TouchOffset.init(context); 0 }
    var learned by remember { mutableIntStateOf(TouchModel.learned) }
    var asking by remember { mutableStateOf(false) }
    SearchSettingsScreen(onClickBack = onBack, title = stringResource(R.string.eurise_set_touch), settings = emptyList()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card {
                Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.eurise_touch_map), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                    TouchMap(learned)
                }
            }
            Section(stringResource(R.string.eurise_touch_uses)) {
                TouchSwitch(stringResource(R.string.eurise_touch_learn), stringResource(R.string.eurise_touch_learn_detail, learned), TouchModel.PREF_LEARN)
                Divider()
                TouchSwitch(stringResource(R.string.eurise_touch_lm), stringResource(R.string.eurise_touch_lm_detail), TouchModel.PREF_LM)
                Divider()
                TouchSwitch(stringResource(R.string.eurise_touch_band), stringResource(R.string.eurise_touch_band_detail), TouchModel.PREF_SPACE_BAND)
            }
            OutlinedButton(
                onClick = { asking = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.eurise_touch_reset), fontWeight = FontWeight.Bold) }
            Text(stringResource(R.string.eurise_touch_reset_detail), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            Section(stringResource(R.string.eurise_set_privacy)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.eurise_touch_on_phone), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Tacche(10, "10/10")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.eurise_touch_reset_title)) },
        text = { Text(stringResource(R.string.eurise_touch_reset_detail)) },
        confirmButton = {
            TextButton(onClick = {
                TouchModel.reset(); TouchOffset.reset(); learned = 0; asking = false
            }) { Text(stringResource(R.string.eurise_touch_reset)) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun touchPrefs(context: Context) = context.getSharedPreferences(TouchModel.PREFS, Context.MODE_PRIVATE)

/** Tre file di lettere; su ognuna il punto dove cade di solito il dito (dal modello del tocco). */
@Composable
private fun TouchMap(learned: Int) {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val keyColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dot = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEachIndexed { r, letters ->
            Row(Modifier.fillMaxWidth().padding(horizontal = (r * 14).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                letters.forEach { c ->
                    val shape = remember(learned) { TouchModel.model.shape(c.code) }
                    Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp)).background(keyColor), contentAlignment = Alignment.Center) {
                        Text(c.toString(), color = text, fontSize = 13.sp)
                        Canvas(Modifier.matchParentSizeCompat()) {
                            val cx = size.width * (0.5f + shape[0]); val cy = size.height * (0.5f + shape[1])
                            drawCircle(dot.copy(alpha = if (shape.size > 4 && shape[4] > 0f) 0.75f else 0.3f), radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat() = this.fillMaxWidth().height(34.dp)

@Composable
private fun TouchSwitch(title: String, detail: String, key: String) {
    val context = LocalContext.current
    val prefs = remember { touchPrefs(context) }
    var on by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    SwitchRow(title, detail, on) { on = it; prefs.edit().putBoolean(key, it).apply() }
}

@Composable
private fun PrefSwitch(title: String, detail: String?, key: String, default: Boolean) {
    val context = LocalContext.current
    val prefs = remember { context.prefs() }
    var on by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchRow(title, detail, on) { on = it; prefs.edit().putBoolean(key, it).apply() }
}

@Composable
private fun SwitchRow(title: String, detail: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Switch(on, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(title: String, detail: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (!detail.isNullOrEmpty()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
        NextScreenIcon()
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 6.dp))
        Card { Column { content() } }
    }
}

/** Il grado di privacy con dieci tacchette a semaforo (regola della suite, 13 novembre). */
@Composable
fun Tacche(level: Int, label: String) {
    val color = when { level <= 3 -> Color(0xFFB3412E); level <= 6 -> Color(0xFFC98A1A); else -> Color(0xFF3F7A55) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(78.dp).height(12.dp)) {
            val w = 6.dp.toPx(); val gap = (size.width - 10 * w) / 9
            repeat(10) { i ->
                drawRoundRect(if (i < level) color else Color(0xFFDAD9D2), Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
            }
        }
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
