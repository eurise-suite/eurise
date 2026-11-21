@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package eu.eurise.chat

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.core.data.model.QuickAdd
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.BrainContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GREEN = Color(0xFF4F6B5A)
private val PLUM = Color(0xFF6A4A6B)
private val BLUE = Color(0xFF3E5C8A)

/** Le proposte di un messaggio che contano qui: appuntamenti e cose da fare (tavola ChatProposta). */
internal fun proposals(text: String, time: Long): List<MessageInsights.Insight> =
    MessageInsights.analyze(text, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(time), ZoneId.systemDefault()))
        .filter { it is MessageInsights.Insight.Event || it is MessageInsights.Insight.Task }

/** Il titolo dell'evento: «Giovedì 19, 15:00». */
private fun eventLabel(i: MessageInsights.Insight.Event): String =
    i.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN)).replaceFirstChar { it.uppercase() } +
        (i.time?.let { ", " + Programma.time(it) } ?: "")

/** Apre Calendario per creare l'evento (un'ora, o tutto il giorno senza ora). */
private fun addEvent(context: Context, actions: ChatActions, i: MessageInsights.Insight.Event, title: String, note: String) {
    val zone = ZoneId.systemDefault()
    val start = (i.time?.let { i.date.atTime(it) } ?: i.date.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
    val end = if (i.time != null) start + 3_600_000 else start + 86_400_000
    actions.launch(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, i.time == null)
        .putExtra(CalendarContract.Events.TITLE, title).putExtra(CalendarContract.Events.DESCRIPTION, note)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Libero o no in Calendario a quell'ora (serve il permesso del calendario; senza, non si dice). */
private fun busyAt(context: Context, i: MessageInsights.Insight.Event): String? {
    if (i.time == null) return null
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
    val zone = ZoneId.systemDefault()
    val start = i.date.atTime(i.time).atZone(zone).toInstant().toEpochMilli()
    val end = start + 3_600_000
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { android.content.ContentUris.appendId(it, start); android.content.ContentUris.appendId(it, end) }.build()
    return runCatching {
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY), null, null, null)?.use { c ->
            var title: String? = null
            while (c.moveToNext()) if (c.getInt(1) == 0) { title = c.getString(0).orEmpty(); break }
            title ?: ""
        }
    }.getOrNull()
}

/**
 * Sotto un messaggio ricevuto (tavola ChatProposta): «Giovedì 19, 15:00 · con Anna — libero in Calendario — Aggiungi» e
 * «Manda il preventivo ad Anna — Attività · entro venerdì — Aggiungi». Capito sul telefono; un tocco o lo si ignora.
 */
@Composable
internal fun ProposalCards(model: ChatModel, actions: ChatActions, e: Event, insights: List<MessageInsights.Insight>) {
    val context = LocalContext.current
    var done by remember(e.id) { mutableStateOf(setOf<Int>()) }
    val who = model.nameOf(e.sender)
    val noHub = stringResource(R.string.cr_no_hub)
    val inTasks = stringResource(R.string.cr_in_tasks)
    Column(Modifier.widthIn(max = 320.dp).padding(start = 4.dp, top = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        insights.forEachIndexed { n, i ->
            when (i) {
                is MessageInsights.Insight.Event -> {
                    val busy by produceState<String?>(null, i) { value = withContext(Dispatchers.IO) { busyAt(context, i) } }
                    ProposalRow(
                        EuriseIcons.calendar(2.2f), GREEN, stringResource(R.string.pp_event, eventLabel(i), who),
                        when (busy) { null -> stringResource(R.string.pp_calendar); "" -> stringResource(R.string.pp_free); else -> stringResource(R.string.pp_busy, busy!!) },
                        n in done,
                    ) { addEvent(context, actions, i, context.getString(R.string.pp_event_title, who), e.text); done = done + n }
                }
                is MessageInsights.Insight.Task -> {
                    val due = QuickAdd.parse(i.text, LocalDateTime.now()).due
                    ProposalRow(
                        EuriseIcons.check(2.2f), PLUM, i.text.replaceFirstChar { it.uppercase() },
                        due?.let { stringResource(R.string.pp_task_due, it.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))) } ?: stringResource(R.string.pp_task),
                        n in done,
                    ) { actions.toast(if (model.sendToHub(i.text + " (" + who + ")", task = true)) inTasks else noHub); done = done + n }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ProposalRow(icon: ImageVector, tint: Color, title: String, sub: String, done: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.11f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(if (done) R.string.pp_added else R.string.pp_add), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (done) tint else MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = !done, role = Role.Button, onClick = onAdd).padding(8.dp))
    }
}

/**
 * Il riassunto di quello che ti sei perso (tavola ChatRiassunto): sopra i messaggi di un gruppo, quando entrando ce n'erano
 * tanti da leggere. Lo fa Eurise AI sul telefono, con il modello scaricato: i messaggi non escono. Sotto, le proposte
 * trovate in quei messaggi.
 */
@Composable
internal fun SummaryCard(model: ChatModel, actions: ChatActions, roomId: String, unread: List<Event>, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var text by remember(roomId) { mutableStateOf("") }
    var state by remember(roomId) { mutableStateOf(BrainAsk.State.Idle) }
    val brain = remember { BrainAsk(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { brain.close() } }
    val found = remember(unread.size) { unread.flatMap { e -> proposals(e.text, e.time).map { e to it } }.take(4) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.spark(2.2f), null, tint = BLUE, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.rs_title, unread.size), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = BLUE, modifier = Modifier.weight(1f))
            Icon(EuriseIcons.close(2.2f), stringResource(R.string.rs_close), tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClose).padding(6.dp))
        }
        when (state) {
            BrainAsk.State.Idle -> Chip(stringResource(R.string.rs_go), true) {
                state = BrainAsk.State.Working
                val lines = unread.filter { it.kind == Event.Kind.Text || it.kind == Event.Kind.Notice }.takeLast(150)
                    .joinToString("\n") { model.nameOf(it.sender) + ": " + shownText(model, it).replace('\n', ' ').take(300) }
                brain.ask(context.getString(R.string.rs_prompt) + "\n\n" + lines, onPiece = { text += it }) { s -> state = s }
            }
            BrainAsk.State.Working -> Text(text.ifEmpty { stringResource(R.string.rs_working) }, fontSize = 14.sp, lineHeight = 21.sp)
            BrainAsk.State.Done -> Text(text.trim(), fontSize = 14.sp, lineHeight = 21.sp)
            BrainAsk.State.NoModel -> Text(stringResource(R.string.rs_no_model), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            BrainAsk.State.NoApp -> Text(stringResource(R.string.rs_no_app), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            BrainAsk.State.Failed -> Text(stringResource(R.string.rs_failed), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        if (found.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            found.forEach { (e, i) ->
                when (i) {
                    is MessageInsights.Insight.Event -> Chip(stringResource(R.string.rs_event, eventLabel(i)), false) {
                        addEvent(context, actions, i, model.room(roomId)?.name ?: "", e.text)
                    }
                    is MessageInsights.Insight.Task -> Chip(stringResource(R.string.rs_task, i.text.take(28)), false) {
                        actions.toast(context.getString(if (model.sendToHub(i.text, task = true)) R.string.cr_in_tasks else R.string.cr_no_hub))
                    }
                    else -> Unit
                }
            }
        }
        Text(stringResource(R.string.rs_local), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
    }
}

/** Una domanda a Eurise AI ([BrainContract], sul telefono): la risposta arriva a pezzi. */
internal class BrainAsk(context: Context) {
    enum class State { Idle, Working, Done, NoModel, NoApp, Failed }

    private val app = context.applicationContext
    private var conn: ServiceConnection? = null

    fun ask(text: String, onPiece: (String) -> Unit, onState: (State) -> Unit) {
        val pkg = BrainContract.PACKAGES.firstOrNull { p -> runCatching { app.packageManager.getPackageInfo(p, 0) }.isSuccess }
            ?: return onState(State.NoApp)
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                val reply = Messenger(Handler(Looper.getMainLooper()) { m ->
                    val err = m.data.getString(BrainContract.KEY_ERROR)
                    when {
                        err == BrainContract.ERROR_NO_MODEL -> onState(State.NoModel)
                        err != null -> onState(State.Failed)
                        m.what == BrainContract.MSG_PIECE -> m.data.getString(BrainContract.KEY_TEXT)?.let(onPiece)
                        m.what == BrainContract.MSG_DONE -> onState(State.Done)
                    }
                    true
                })
                val msg = Message.obtain(null, BrainContract.MSG_ASK).apply {
                    data = Bundle().apply { putString(BrainContract.KEY_TEXT, text) }
                    replyTo = reply
                }
                if (runCatching { Messenger(binder).send(msg) }.isFailure) onState(State.Failed)
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        conn = c
        val ok = runCatching { app.bindService(Intent(BrainContract.ACTION_BRAIN).setPackage(pkg), c, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!ok) { conn = null; onState(State.NoApp) }
    }

    fun close() { conn?.let { runCatching { app.unbindService(it) } }; conn = null }
}
