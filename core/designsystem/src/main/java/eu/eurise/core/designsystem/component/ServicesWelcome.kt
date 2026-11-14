package eu.eurise.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.R
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DESK = listOf(PrivacyService.Weather, PrivacyService.Alerts, PrivacyService.Pollen, PrivacyService.News, PrivacyService.Horoscope)
private val MAPS = listOf(PrivacyService.Routes, PrivacyService.RouteWeather, PrivacyService.MapSearch, PrivacyService.MapReports, PrivacyService.FuelPrices)
private val CLOSED = listOf(PrivacyService.Markets, PrivacyService.Videos)

/**
 * Il foglio del primo avvio (tavola PrivPrimoAvvio): tutti i servizi che possono far uscire dati, spenti, a gruppi, con
 * semaforo e paese; i servizi chiusi per ultimi. Toccare una riga, o accenderne l'interruttore, apre sul posto la
 * spiegazione (cosa riceve, dove va, chi lo vede, come cambia la privacy, come si torna indietro) con «Accendi»; spegnere
 * non chiede nulla. Compare una volta sola dopo l'installazione ([PrivacyCenter.welcomePending]); chi aggiorna non lo vede.
 * Va messo nelle schermate da cui si entra nella suite (app e Scrivania): si mostra da sé solo quando serve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesWelcome() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = withContext(Dispatchers.IO) { PrivacyCenter.welcomePending() } }
    if (!show) return

    val on = remember { mutableStateMapOf<PrivacyService, Boolean>() }
    var open by remember { mutableStateOf<PrivacyService?>(null) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme

    fun close() {
        show = false
        scope.launch(Dispatchers.IO) { PrivacyCenter.welcomeDone() }
    }
    fun turn(service: PrivacyService, value: Boolean) {
        on[service] = value
        ServiceStates.set(service, value)
        if (value) open = null
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheet, containerColor = scheme.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.ds_w_kicker).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.secondary)
            Text(stringResource(R.string.ds_w_title), fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
            Text(stringResource(R.string.ds_w_text), fontSize = 14.sp, lineHeight = 21.sp, color = scheme.onSurfaceVariant)

            val active = on.filterValues { it }.keys
            val now = active.minOfOrNull { it.score() } ?: 10
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.ds_w_now), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                PrivacyMeter(now)
                Text(stringResource(R.string.ds_w_score, now), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = privacyColor(now))
            }

            for ((title, list) in listOf(R.string.ds_w_desk to DESK, R.string.ds_w_maps to MAPS, R.string.ds_w_closed to CLOSED)) {
                Text(
                    stringResource(title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (list === CLOSED) scheme.error else scheme.secondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp)) {
                    list.forEachIndexed { i, service ->
                        if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        ServiceRow(
                            service, on = on[service] == true, expanded = open == service,
                            onToggleOpen = { open = if (open == service) null else service },
                            onSwitch = { value -> if (value) open = service else turn(service, false) },
                            onAccept = { turn(service, true) },
                        )
                    }
                }
            }

            Text(stringResource(R.string.ds_w_footer), fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            val count = active.size
            Button(onClick = ::close, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(
                    if (count == 0) stringResource(R.string.ds_w_done_none) else pluralStringResource(R.plurals.ds_w_done, count, count),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
            TextButton(
                onClick = { active.toList().forEach { turn(it, false) }; close() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text(stringResource(R.string.ds_w_all_off), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ServiceRow(
    service: PrivacyService,
    on: Boolean,
    expanded: Boolean,
    onToggleOpen: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    onAccept: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggleOpen),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(service.titleRes()), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                PrivacyMeter(service.score(), service.where())
            }
            Switch(checked = on, onCheckedChange = onSwitch)
        }
        AnimatedVisibility(expanded && !on) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.background).padding(horizontal = 12.dp)) {
                    Line(R.string.ds_on_read, stringResource(service.detailRes()))
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    Line(R.string.ds_on_where, stringResource(service.where().longName()))
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    Line(R.string.ds_on_who, stringResource(R.string.ds_on_who_d))
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.ds_on_privacy), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyMeter(10, Jurisdiction.Phone)
                            Text("→", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                            PrivacyMeter(service.score(), service.where())
                        }
                    }
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                    Line(R.string.ds_on_back, stringResource(R.string.ds_on_back_d))
                }
                service.closedWarningRes()?.let { warn ->
                    Text(
                        stringResource(warn), fontSize = 12.5.sp, lineHeight = 17.sp, color = scheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.errorContainer).padding(10.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onAccept, modifier = Modifier.weight(1f).height(44.dp)) {
                        Text(stringResource(R.string.ds_w_turn_on), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onToggleOpen, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(stringResource(R.string.ds_on_no), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: Int, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(label), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
