package eu.eurise.feature.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.feature.clock.R
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.clock.system.Notifications
import eu.eurise.feature.clock.system.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** L'app: quattro sezioni nella barra satinata, con il + che crea nella sezione aperta. */
@Composable
fun ClockApp(tab: Int, onTab: (Int) -> Unit) {
    val haze = remember { HazeState() }
    var adding by remember { mutableStateOf(false) }
    val items = listOf(
        DockItem(stringResource(R.string.tab_alarms)) { EuriseIcons.alarm(it) },
        DockItem(stringResource(R.string.tab_timers)) { EuriseIcons.clock(it) },
        DockItem(stringResource(R.string.tab_stopwatch)) { EuriseIcons.repeat(it) },
        DockItem(stringResource(R.string.tab_zones)) { EuriseIcons.globe(it) },
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when (tab) {
                0 -> AlarmsScreen(adding, onAdded = { adding = false })
                1 -> TimersScreen(adding, onAdded = { adding = false })
                2 -> StopwatchScreen()
                else -> ZonesScreen(adding, onAdded = { adding = false })
            }
        }
        EuriseDock(
            items = items,
            selected = tab,
            onSelect = { onTab(it); adding = false },
            hazeState = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
            actionLabel = stringResource(R.string.action_new),
            onAction = if (tab == 2) null else ({ adding = true }),
        )
    }
}

/**
 * L'Orologio come sezione dell'app Eurise: la barra in basso è già quella dell'app, quindi le quattro parti si
 * scelgono con le pillole in alto e il + arriva da fuori con [addRequests].
 */
@Composable
fun ClockSection(tab: Int, onTab: (Int) -> Unit, addRequests: Int) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var handled by rememberSaveable { mutableIntStateOf(addRequests) }
    LaunchedEffect(addRequests) { if (addRequests != handled) { handled = addRequests; adding = true } }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Notifications.channels(context)
            Scheduler.rescheduleAll(context)
        }
    }
    val labels = listOf(R.string.tab_alarms, R.string.tab_timers, R.string.tab_stopwatch, R.string.tab_zones)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(labels) { i, label ->
                FilterPill(stringResource(label), null, tab == i) { onTab(i); adding = false }
            }
        }
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            when (tab) {
                0 -> AlarmsScreen(adding, onAdded = { adding = false })
                1 -> TimersScreen(adding, onAdded = { adding = false })
                2 -> StopwatchScreen()
                else -> ZonesScreen(adding, onAdded = { adding = false })
            }
        }
    }
}

/** Elenco di una sezione: titolo grande in alto e spazio in fondo per la barra. */
@Composable
internal fun ScreenList(title: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = GlassDefaults.PillHeight + 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
        }
        content()
    }
}

/** Riga con interruttore e una riga di spiegazione. */
@Composable
internal fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.padding(4.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Pulsantino a capsula: pieno per l'azione principale. */
@Composable
internal fun Chip(label: String, filled: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) scheme.onPrimary else scheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (filled) scheme.primary else scheme.primary.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** L'istante attuale, aggiornato ogni [stepMs] (per i conti alla rovescia). */
@Composable
internal fun rememberNow(stepMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepMs) { while (true) { now = System.currentTimeMillis(); delay(stepMs - now % stepMs) } }
    return now
}
