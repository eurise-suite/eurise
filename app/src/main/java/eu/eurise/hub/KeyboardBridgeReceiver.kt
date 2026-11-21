package eu.eurise.hub

import eu.eurise.core.suite.HubBridge
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import eu.eurise.core.data.model.Capture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Riceve da Eurise Tastiera una frase da trasformare in attività o un testo da salvare come nota.
 * Solo le app Eurise firmate con la stessa chiave possono mandarli (permesso della suite nel manifest).
 */
class KeyboardBridgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_EVENT) { event(context, intent); return }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val data = (context.applicationContext as EuriseApplication).data
                val message = when (intent.action) {
                    // Data e ora già decise (Ricordamelo della Chat): niente interpretazione del testo.
                    ACTION_TASK -> if (intent.hasExtra(HubBridge.EXTRA_DUE)) {
                        val due = runCatching { java.time.LocalDate.parse(intent.getStringExtra(HubBridge.EXTRA_DUE)) }.getOrNull()
                        val time = runCatching { java.time.LocalTime.parse(intent.getStringExtra(HubBridge.EXTRA_TIME)) }.getOrNull()
                        data.tasks.add(text, due = due, time = time, notes = intent.getStringExtra(HubBridge.EXTRA_NOTES).orEmpty(),
                            remindBefore = if (time != null) 0 else null)
                        context.getString(R.string.keyboard_task_created, text)
                    } else {
                        val r = Capture.parse(text, LocalDateTime.now())
                        val q = r.quick
                        data.tasks.add(
                            r.title.ifBlank { text }, due = q.due, time = q.time, repeat = q.repeat, priority = q.priority,
                            remindBefore = if (q.time != null) 0 else null,
                        )
                        context.getString(R.string.keyboard_task_created, r.title.ifBlank { text })
                    }
                    ACTION_NOTE -> {
                        data.notes.save(null, text.substringBefore('\n').trim(), text.substringAfter('\n', "").trim())
                        context.getString(R.string.keyboard_note_created)
                    }
                    else -> null
                }
                if (message != null) withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Un appuntamento dal computer (Ponte, Firefox con l'estensione Eurise): va nel primo calendario modificabile,
     * preferendo quelli sincronizzati a quello solo sul telefono. Senza il permesso del Calendario lo dice.
     */
    private fun event(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val start = parse(intent.getStringExtra("start")) ?: return
        val allDay = intent.getStringExtra("start")?.contains('T') == false
        val end = parse(intent.getStringExtra("end"))?.takeIf { it.isAfter(start) }
            ?: if (allDay) start.plusDays(1) else start.plusHours(1)
        val notes = listOfNotNull(intent.getStringExtra("notes"), intent.getStringExtra("url")).joinToString("\n")
        val pending = goAsync()
        scope.launch {
            try {
                val data = (context.applicationContext as EuriseApplication).data
                val message = if (!data.calendar.hasPermission()) context.getString(R.string.ponte_event_no_permission) else {
                    val calendars = runCatching { kotlinx.coroutines.withTimeout(3_000) { data.calendar.observeCalendars().first() } }.getOrDefault(emptyList())
                    val target = calendars.filter { it.writable }.sortedBy { it.local }.firstOrNull()
                    val id = target?.let {
                        data.calendar.save(
                            eu.eurise.core.data.calendar.EventDraft(null, it.id, title, intent.getStringExtra("place").orEmpty(), notes, allDay,
                                if (allDay) start.toLocalDate().atStartOfDay() else start,
                                if (allDay) end.toLocalDate().atStartOfDay().let { e -> if (e.isAfter(start)) e else start.toLocalDate().plusDays(1).atStartOfDay() } else end),
                        )
                    }
                    if (id != null) context.getString(R.string.ponte_event_created, title) else context.getString(R.string.ponte_event_failed)
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            } finally {
                pending.finish()
            }
        }
    }

    /** `2026-11-20T15:30` oppure `2026-11-20` (mezzanotte). */
    private fun parse(s: String?): LocalDateTime? = s?.trim()?.let {
        runCatching { LocalDateTime.parse(it) }.getOrNull() ?: runCatching { java.time.LocalDate.parse(it).atStartOfDay() }.getOrNull()
    }

    companion object {
        const val ACTION_EVENT = "eu.eurise.action.PONTE_EVENT"
        const val ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK"
        const val ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
