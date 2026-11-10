package eu.eurise.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import eu.eurise.core.data.model.Capture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Riceve da Eurise Tastiera una frase da trasformare in attività o un testo da salvare come nota.
 * Solo le app Eurise firmate con la stessa chiave possono mandarli (permesso della suite nel manifest).
 */
class KeyboardBridgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val data = (context.applicationContext as EuriseApplication).data
                val message = when (intent.action) {
                    ACTION_TASK -> {
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

    companion object {
        const val ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK"
        const val ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
