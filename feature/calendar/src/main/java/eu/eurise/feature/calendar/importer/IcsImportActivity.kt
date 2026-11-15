package eu.eurise.feature.calendar.importer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.calendar.Ics
import eu.eurise.core.designsystem.component.ImportRow
import eu.eurise.core.designsystem.component.ImportScreen
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.calendar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * «Apri con › Calendario» per i file .ics (biglietti, inviti, calendari scaricati; 15 novembre): si vede cosa c'è nel
 * file, si sceglie il calendario e si aggiungono gli appuntamenti spuntati. Niente parte dal telefono.
 */
class IcsImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data ?: @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        if (uri == null) { finish(); return }
        setContent { EuriseTheme { Screen(uri) } }
    }

    private fun granted() = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @androidx.compose.runtime.Composable
    private fun Screen(uri: Uri) {
        val scheme = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        val data = remember { EuriseData.create(this) }
        var allowed by remember { mutableStateOf(granted()) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = granted() }
        LaunchedEffect(Unit) { if (!allowed) ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }
        val events by produceState<List<Ics.Event>?>(null, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()?.let { Ics.parse(it) } ?: emptyList()
            }
        }
        val calendars by remember(allowed) { if (allowed) data.calendar.observeCalendars() else emptyFlow() }.collectAsStateWithLifecycle(emptyList())
        val writable = calendars.filter { it.writable }.sortedBy { it.local }
        var target by remember { mutableStateOf<Long?>(null) }
        val chosenCalendar = writable.firstOrNull { it.id == target } ?: writable.firstOrNull()
        var chosen by remember(events) { mutableStateOf(events.orEmpty().indices.toSet()) }
        var busy by remember { mutableStateOf(false) }
        val list = events.orEmpty()
        val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        if (events != null && list.isEmpty()) {
            LaunchedEffect(Unit) { Toast.makeText(this@IcsImportActivity, R.string.ics_empty, Toast.LENGTH_LONG).show(); finish() }
            return
        }
        ImportScreen(
            icon = EuriseIcons.calendar(),
            title = stringResource(R.string.ics_title),
            subtitle = if (events == null) "…" else pluralStringResource(R.plurals.ics_found, list.size, list.size),
            rows = list.map { e ->
                val time = if (e.allDay) stringResource(R.string.ics_all_day) else e.start.format(hm) + "–" + e.end.format(hm)
                ImportRow(e.title, listOf(e.start.format(day) + " · " + time, e.location, if (e.repeats) stringResource(R.string.ics_repeats) else "")
                    .filter { it.isNotBlank() }.joinToString("\n"))
            },
            chosen = chosen,
            onToggle = { i -> chosen = if (i in chosen) chosen - i else chosen + i },
            action = if (!allowed) stringResource(R.string.ics_allow) else pluralStringResource(R.plurals.ics_add, chosen.size, chosen.size),
            busy = busy || (allowed && chosenCalendar == null),
            onAction = action@{
                if (!allowed) { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)); return@action }
                val cal = chosenCalendar ?: return@action
                busy = true
                scope.launch {
                    val added = withContext(Dispatchers.IO) {
                        chosen.sorted().mapNotNull { list.getOrNull(it) }.count { e ->
                            val notes = listOf(e.description, e.url).filter { it.isNotBlank() }.joinToString("\n\n")
                            runCatching { data.calendar.save(EventDraft(null, cal.id, e.title, e.location, notes, e.allDay, e.start, e.end)) }.getOrNull() != null
                        }
                    }
                    Toast.makeText(this@IcsImportActivity, resources.getQuantityString(R.plurals.ics_done, added, added, cal.name), Toast.LENGTH_LONG).show()
                    finish()
                }
            },
            onClose = { finish() },
        ) {
            if (allowed && writable.isNotEmpty()) {
                Text(stringResource(R.string.ics_where), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    writable.forEach { c ->
                        val on = c.id == chosenCalendar?.id
                        Row(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) scheme.primary.copy(alpha = 0.14f) else scheme.surface)
                                .then(if (on) Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                                .clickable { target = c.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.color)))
                            Spacer(Modifier.width(7.dp))
                            Text(c.name, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            } else if (allowed) {
                Text(stringResource(R.string.ics_no_calendar), fontSize = 13.sp, color = scheme.error)
            }
        }
    }
}
