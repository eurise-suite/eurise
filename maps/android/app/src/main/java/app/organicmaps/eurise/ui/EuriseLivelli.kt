package app.organicmaps.eurise.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import app.organicmaps.R
import app.organicmaps.eurise.EuriseRouteWeather
import app.organicmaps.eurise.EuriseSky
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.settings.SpeedCameraMode
import app.organicmaps.util.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Livelli e fonti» (tavola MapLivelli, approvata): sotto i livelli della mappa di CoMaps, le voci che cambiano cosa
 * mostra la guida e cosa esce dal telefono, ognuna con il suo interruttore e accanto cosa esce. Ci sono solo le voci che
 * esistono già (velocità e limiti, autovelox, meteo sul percorso); orizzonte col meteo, lettura dei cartelli, prezzi dei
 * carburanti e traffico entrano quando arrivano le loro funzioni.
 */
object EuriseLivelli {
    @JvmStatic
    fun attach(view: View?) {
        val compose = view as? ComposeView ?: return
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Sheet(ThemeUtils.isNightTheme()) }
    }

    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)

    /** L'orizzonte col meteo e l'ora: in guida nel motore, esplorando la fascia in alto (stesso interruttore). */
    internal fun horizon(c: Context) = prefs(c).getBoolean(EuriseSky.PREF, true)
    internal fun setHorizon(c: Context, on: Boolean) = prefs(c).edit().putBoolean(EuriseSky.PREF, on).apply()

    internal fun speedLimits(c: Context) = prefs(c).getBoolean(c.getString(R.string.pref_speedlimit), true)
    internal fun setSpeedLimits(c: Context, on: Boolean) =
        prefs(c).edit().putBoolean(c.getString(R.string.pref_speedlimit), on).apply()

    /** Autovelox: «dove la legge lo permette» è la scelta automatica di CoMaps; spento è «mai». */
    internal fun cameras(c: Context) =
        prefs(c).getString(c.getString(R.string.pref_tts_speed_cameras), SpeedCameraMode.AUTO.name) != SpeedCameraMode.NEVER.name
    internal fun setCameras(c: Context, on: Boolean) {
        val mode = if (on) SpeedCameraMode.AUTO else SpeedCameraMode.NEVER
        prefs(c).edit().putString(c.getString(R.string.pref_tts_speed_cameras), mode.name).apply()
        Framework.setSpeedCamerasMode(mode)
    }
}

@Composable
private fun Sheet(night: Boolean) {
    val c = if (night) Notte else Perla
    val context = LocalContext.current
    var limits by remember { mutableStateOf(EuriseLivelli.speedLimits(context)) }
    var cameras by remember { mutableStateOf(EuriseLivelli.cameras(context)) }
    var where by remember { mutableStateOf(EuriseDoveSei.enabled(context)) }
    var horizon by remember { mutableStateOf(EuriseLivelli.horizon(context)) }
    // Prezzi dei carburanti: lo stato è nel centro privacy dell'app Eurise (null: app assente, la voce non si mostra).
    var fuel by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { fuel = withContext(Dispatchers.IO) { EuriseFuelData.privacy(context) } }
    // null: l'app Eurise non c'è o non ha ancora risposto (la voce del meteo non si mostra).
    var weather by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { EuriseRouteWeather.status(context) { weather = it } }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(stringResource(R.string.eurise_lv_lead), style = TextStyle(color = c.muted, fontSize = 12.5.sp))
        Section(c, stringResource(R.string.eurise_lv_phone))
        Card(c) {
            Toggle(c, stringResource(R.string.eurise_lv_horizon), stringResource(R.string.eurise_lv_horizon_sub), horizon) {
                horizon = it
                EuriseLivelli.setHorizon(context, it)
            }
            Toggle(c, stringResource(R.string.eurise_lv_speed), stringResource(R.string.eurise_lv_speed_sub), limits, divider = true) {, stringResource(R.string.eurise_lv_speed_sub), limits) {
                limits = it
                EuriseLivelli.setSpeedLimits(context, it)
            }
            Toggle(c, stringResource(R.string.eurise_lv_cameras), stringResource(R.string.eurise_lv_cameras_sub), cameras, divider = true) {
                cameras = it
                EuriseLivelli.setCameras(context, it)
            }
            Toggle(c, stringResource(R.string.eurise_lv_where), stringResource(R.string.eurise_lv_where_sub), where, divider = true) {
                where = it
                EuriseDoveSei.setEnabled(context, it)
            }
        }
        if (weather != null || fuel != null) {
            Section(c, stringResource(R.string.eurise_lv_network))
            Card(c) {
                weather?.let { on ->
                    Toggle(c, stringResource(R.string.eurise_lv_weather), stringResource(R.string.eurise_lv_weather_sub), on) { want ->
                        EuriseRouteWeather.set(context, want) { weather = it }
                    }
                }
                fuel?.let { on ->
                    Toggle(c, stringResource(R.string.eurise_lv_fuel), stringResource(R.string.eurise_lv_fuel_sub), on, divider = weather != null) { want ->
                        scope.launch {
                            fuel = withContext(Dispatchers.IO) {
                                EuriseFuelData.privacy(context, if (want) "enable" else "disable") ?: EuriseFuelData.privacy(context)
                            }
                        }
                    }
                }
                // Traffico e cantieri: nella tavola, ma una fonte libera per l'Italia non c'è ancora; la voce lo dice.
                Toggle(c, stringResource(R.string.eurise_lv_traffic), stringResource(R.string.eurise_lv_traffic_sub), false, divider = true, enabled = false) {}
            }
        }
        BasicText(stringResource(R.string.eurise_lv_note), style = TextStyle(color = c.muted, fontSize = 11.5.sp, lineHeight = 17.sp))
    }
}

@Composable
private fun Section(c: Colors, title: String) {
    BasicText(title.uppercase(), Modifier.padding(start = 4.dp, top = 6.dp),
        style = TextStyle(color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp))
}

@Composable
private fun Card(c: Colors, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun Toggle(c: Colors, title: String, sub: String, on: Boolean, divider: Boolean = false, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    if (divider) Box(Modifier.fillMaxWidth().padding(vertical = 0.dp).heightIn(min = 1.dp, max = 1.dp).background(c.ink.copy(alpha = 0.07f)))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(enabled = enabled) { onChange(!on) }
            .alpha(if (enabled) 1f else 0.5f)
            .semantics(mergeDescendants = true) { role = Role.Switch; stateDescription = if (on) "on" else "off" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            BasicText(title, style = TextStyle(color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold))
            BasicText(sub, style = TextStyle(color = c.muted, fontSize = 12.sp))
        }
        // L'interruttore della tavola: 44 × 26, pieno nell'accento se acceso.
        Box(
            Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) c.accent else Color(0xFFC9C9C2)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(3.dp).size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
        }
    }
}
