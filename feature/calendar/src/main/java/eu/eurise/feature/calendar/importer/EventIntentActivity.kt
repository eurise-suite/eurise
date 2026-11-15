package eu.eurise.feature.calendar.importer

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.data.EuriseData
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.calendar.EventEditorRoute
import eu.eurise.feature.calendar.EventEditorScreen
import eu.eurise.feature.calendar.EventEditorViewModel
import eu.eurise.feature.calendar.R
import java.time.Instant
import java.time.ZoneId

/**
 * Eurise Calendario per le altre app (15 novembre): «Aggiungi al calendario» da Email, Messaggi, browser, biglietti
 * (ACTION_INSERT ed EDIT con titolo, orari, luogo e descrizione già scritti) apre l'editor della suite già compilato;
 * «Apri» di un appuntamento (VIEW di `content://com.android.calendar/events/N`) lo apre nell'editor.
 */
class EventIntentActivity : ComponentActivity() {
    private val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    private fun granted() = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EuriseTheme {
                var allowed by remember { mutableStateOf(granted()) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    allowed = granted()
                    if (!allowed) finish()
                }
                LaunchedEffect(Unit) { if (!allowed) ask.launch(perms) }
                if (allowed) Editor(intent)
            }
        }
    }

    @Composable
    private fun Editor(i: Intent) {
        val data = remember { EuriseData.create(this) }
        val existing = i.data?.takeIf { i.action == Intent.ACTION_VIEW || i.action == Intent.ACTION_EDIT }
            ?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }?.takeIf { it > 0 }
        val begin = i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1).takeIf { it > 0 }
        if (existing != null) {
            // Un'occorrenza precisa serve all'editor; senza, la prima dell'appuntamento.
            EventEditorRoute(data.calendar, existing, begin ?: firstBegin(existing), null, onClose = { finish() })
            return
        }
        val zone = ZoneId.systemDefault()
        val vm = viewModel { EventEditorViewModel(data.calendar, null, null, begin, trash = data.trash) }
        val state by vm.state.collectAsStateWithLifecycle()
        var filled by remember { mutableStateOf(false) }
        LaunchedEffect(state.loaded) {
            if (!state.loaded || filled) return@LaunchedEffect
            filled = true
            i.getStringExtra(CalendarContract.Events.TITLE)?.let(vm::onTitle)
            i.getStringExtra(CalendarContract.Events.EVENT_LOCATION)?.let(vm::onLocation)
            i.getStringExtra(CalendarContract.Events.DESCRIPTION)?.let(vm::onDescription)
            val allDay = i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
            if (allDay) vm.onAllDay(true)
            i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1).takeIf { it > 0 && (begin == null || it > begin) }?.let { end ->
                // Il giorno intero arriva in UTC a mezzanotte: si legge in UTC per non scivolare al giorno prima.
                val t = Instant.ofEpochMilli(end).atZone(if (allDay) java.time.ZoneOffset.UTC else zone).toLocalDateTime()
                vm.onEndDate(if (allDay) t.toLocalDate().minusDays(1) else t.toLocalDate())
                if (!allDay) vm.onEndTime(t.toLocalTime())
            }
            if (allDay && begin != null) vm.onStartDate(Instant.ofEpochMilli(begin).atZone(java.time.ZoneOffset.UTC).toLocalDate())
        }
        val localName = stringResource(R.string.cal_local_name)
        EventEditorScreen(
            state = state,
            onTitle = vm::onTitle,
            onLocation = vm::onLocation,
            onDescription = vm::onDescription,
            onAllDay = vm::onAllDay,
            onCalendar = vm::onCalendar,
            onStartDate = vm::onStartDate,
            onStartTime = vm::onStartTime,
            onEndDate = vm::onEndDate,
            onEndTime = vm::onEndTime,
            onCreateLocal = { vm.createLocalCalendar(localName, CalendarTint.BluNotte.light.toArgb()) },
            onSave = { vm.save { setResult(RESULT_OK); finish() } },
            onDelete = { finish() },
            onClose = { finish() },
        )
    }

    private fun firstBegin(eventId: Long): Long? = runCatching {
        contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), arrayOf(CalendarContract.Events.DTSTART), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()
}
