package eu.eurise.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.R
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * I servizi esterni del centro privacy descritti in un posto solo: nome, cosa ricevono, dove stanno, quante tacchette e,
 * per quelli chiusi, l'avviso con le alternative. Li usano il centro privacy e le schede che dipendono da un servizio
 * (Scrivania: meteo, notizie, borsa, video, oroscopo). Regola del 14 novembre: tutti spenti di base; si accendono con
 * l'avviso [ServiceConsentSheet], si spengono senza conferme.
 */

fun PrivacyService.titleRes(): Int = when (this) {
    PrivacyService.Weather -> R.string.ds_s_weather
    PrivacyService.Pollen -> R.string.ds_s_pollen
    PrivacyService.Alerts -> R.string.ds_s_alerts
    PrivacyService.News -> R.string.ds_s_news
    PrivacyService.Markets -> R.string.ds_s_markets
    PrivacyService.Horoscope -> R.string.ds_s_horoscope
    PrivacyService.Videos -> R.string.ds_s_videos
    PrivacyService.Routes -> R.string.ds_s_routes
    PrivacyService.RouteWeather -> R.string.ds_s_route_weather
    PrivacyService.MapSearch -> R.string.ds_s_map_search
    PrivacyService.MapReports -> R.string.ds_s_map_reports
    PrivacyService.FuelPrices -> R.string.ds_s_fuel
}

fun PrivacyService.detailRes(): Int = when (this) {
    PrivacyService.Weather -> R.string.ds_s_weather_d
    PrivacyService.Pollen -> R.string.ds_s_pollen_d
    PrivacyService.Alerts -> R.string.ds_s_alerts_d
    PrivacyService.News -> R.string.ds_s_news_d
    PrivacyService.Markets -> R.string.ds_s_markets_d
    PrivacyService.Horoscope -> R.string.ds_s_horoscope_d
    PrivacyService.Videos -> R.string.ds_s_videos_d
    PrivacyService.Routes -> R.string.ds_s_routes_d
    PrivacyService.RouteWeather -> R.string.ds_s_route_weather_d
    PrivacyService.MapSearch -> R.string.ds_s_map_search_d
    PrivacyService.MapReports -> R.string.ds_s_map_reports_d
    PrivacyService.FuelPrices -> R.string.ds_s_fuel_d
}

/** I servizi chiusi: avviso su limiti e pericoli, con l'alternativa (regola del 12 novembre). */
fun PrivacyService.closedWarningRes(): Int? = when (this) {
    PrivacyService.Markets -> R.string.ds_s_markets_warn
    PrivacyService.Videos -> R.string.ds_s_videos_warn
    else -> null
}

/** Dove sta chi riceve i dati del servizio (il simbolo accanto al semaforo). */
fun PrivacyService.where(): Jurisdiction = when (this) {
    PrivacyService.Weather, PrivacyService.Pollen, PrivacyService.RouteWeather -> Jurisdiction.CH // Open-Meteo GmbH, Bürglen
    PrivacyService.Alerts -> Jurisdiction.EU // EUMETNET, Bruxelles
    PrivacyService.Routes -> Jurisdiction.EU // server OSRM di FOSSGIS, Germania
    PrivacyService.MapSearch -> Jurisdiction.EU // Photon di komoot, Germania
    PrivacyService.MapReports -> Jurisdiction.UK // OpenStreetMap Foundation, Regno Unito
    PrivacyService.FuelPrices -> Jurisdiction.EU // Ministero delle Imprese e del Made in Italy, Roma
    PrivacyService.News, PrivacyService.Horoscope -> Jurisdiction.Various
    PrivacyService.Markets, PrivacyService.Videos -> Jurisdiction.US
}

/** Il grado di privacy effettivo, da ciò che il servizio riceve (vedi [privacyScore]); [hiddenIp] dalla rete. */
fun PrivacyService.score(hiddenIp: Int = 0): Int = when (this) {
    PrivacyService.Alerts, PrivacyService.News, PrivacyService.Horoscope, PrivacyService.FuelPrices ->
        privacyScore(DataOut.Anonymous, where(), hiddenIp = hiddenIp)
    PrivacyService.Markets, PrivacyService.Videos -> privacyScore(DataOut.Minimal, where(), closed = true) // Yahoo, Google
    else -> privacyScore(DataOut.Minimal, where(), hiddenIp = hiddenIp)
}

/**
 * Lo stato dei servizi visto da Compose. [version] cresce a ogni cambio fatto da qui o dal centro privacy, così le schede
 * aperte si ridisegnano senza riaprire l'app. Le preferenze si leggono fuori dal thread principale.
 */
object ServiceStates {
    var version by mutableIntStateOf(0)
        private set

    fun set(service: PrivacyService, on: Boolean) {
        PrivacyCenter.set(service, on)
        version++
    }
}

/** Acceso o spento, letto fuori dal thread principale; null finché non si sa. */
@Composable
fun rememberServiceOn(service: PrivacyService): Boolean? {
    val v = ServiceStates.version
    val on by produceState<Boolean?>(null, service, v) { value = withContext(Dispatchers.IO) { PrivacyCenter.allowed(service) } }
    return on
}

/**
 * La scheda che dipende da un servizio esterno: se è spento mostra [ServiceOffNotice] (nulla esce, e «Accendi» con
 * l'avviso), altrimenti il contenuto. [onTurnedOn] serve a caricare subito i dati dopo l'accensione.
 */
@Composable
fun ServiceGate(service: PrivacyService, onTurnedOn: () -> Unit = {}, content: @Composable () -> Unit) {
    when (rememberServiceOn(service)) {
        null -> Unit
        false -> ServiceOffNotice(service, onTurnedOn)
        true -> content()
    }
}

/** Una riga breve dentro la scheda: il servizio è spento, nulla esce; «Accendi…» apre l'avviso. */
@Composable
fun ServiceOffNotice(service: PrivacyService, onTurnedOn: () -> Unit = {}, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { asking = true }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.ds_off_line), fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.ds_off_turn_on), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    if (asking) ServiceConsentSheet(service, onDismiss = { asking = false }, onAccept = { asking = false; onTurnedOn() })
}

/**
 * L'avviso prima di accendere un servizio (regola fissa): cosa viene letto, dove va, chi lo vede, come cambia la privacy
 * (da 10, tutto sul telefono, alle tacchette del servizio), come si torna indietro; per i servizi chiusi anche limiti e
 * alternative. «Accendi» salva la scelta e chiama [onAccept]; «Non ora» chiude e basta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceConsentSheet(service: PrivacyService, hiddenIp: Int = 0, onDismiss: () -> Unit, onAccept: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.background) {
        Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ds_on_title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.secondary)
            Text(stringResource(service.titleRes()), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                Labeled(R.string.ds_on_read, stringResource(service.detailRes()))
                Divider()
                Labeled(R.string.ds_on_where, stringResource(service.where().longName()))
                Divider()
                Labeled(R.string.ds_on_who, stringResource(R.string.ds_on_who_d))
                Divider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ds_on_privacy), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    val now = service.score(hiddenIp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyMeter(10, Jurisdiction.Phone)
                        Text("→", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                        PrivacyMeter(now, service.where())
                    }
                    Text(stringResource(R.string.ds_on_privacy_d, now), fontSize = 13.sp, lineHeight = 17.sp)
                }
                Divider()
                Labeled(R.string.ds_on_back, stringResource(R.string.ds_on_back_d))
            }
            service.closedWarningRes()?.let { warn ->
                Text(
                    stringResource(warn), fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.errorContainer).padding(12.dp),
                )
            }
            Button(onClick = { ServiceStates.set(service, true); onAccept() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(stringResource(R.string.ds_on_ok), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(stringResource(R.string.ds_on_no), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

@Composable
private fun Labeled(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(stringResource(label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.5.sp)
    }
}
