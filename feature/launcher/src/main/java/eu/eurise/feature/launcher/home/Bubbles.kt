package eu.eurise.feature.launcher.home

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.model.Note
import eu.eurise.core.data.model.Task
import eu.eurise.core.data.model.TaskList
import eu.eurise.core.suite.ClockContract
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NotificationStore
import eu.eurise.feature.launcher.route.Travel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayTimeFormat = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ITALIAN)

/** Per quanto un codice di accesso resta nella bolla (e negli appunti dopo averlo copiato). */
private const val CodeLifeMinutes = 10L

/**
 * Le azioni delle bolle nuove, fornite dalla Scrivania. Ogni bolla fa il suo lavoro lì dentro; l'app si apre solo
 * con le azioni "Apri".
 */
class BubbleActions(
    val setDone: (Task) -> Unit,
    val postpone: (Task) -> Unit,
    val quickTask: (String) -> Unit,
    val openTasks: () -> Unit,
    val openMedicines: () -> Unit,
    val openDay: (LocalDate) -> Unit,
    val navigate: (String) -> Unit,
    val meetingNote: (CalendarEvent) -> Unit,
    val openLink: (String) -> Unit,
    val setReadCodes: (Boolean) -> Unit,
    val openClock: () -> Unit,
    val capture: (String) -> Unit,
    val openNote: (Note) -> Unit,
    val grantNotifications: () -> Unit,
    /** Aggiunge un'attività per oggi, letta come nell'aggiunta rapida ("Richiamare Luca"). */
    val addTask: (String) -> Unit = quickTask,
    /** Richiama chi ha telefonato. */
    val callBack: (MessageItem) -> Unit = {},
)

// ---------------------------------------------------------------- pezzi comuni

/** Pulsantino di un'azione in bolla: pieno per quella principale, tenue per le altre. */
@Composable
internal fun ActionChip(label: String, filled: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) MaterialTheme.colorScheme.onPrimary else color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) color else color.copy(alpha = 0.13f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Riga di una bolla: simbolo, testo principale e secondario, e a destra le azioni. */
@Composable
private fun BubbleRow(
    icon: ImageVector,
    main: String,
    sub: String?,
    tint: Color = MaterialTheme.colorScheme.primary,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(main, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) {
                Text(sub, fontSize = 11.5.sp, lineHeight = 14.sp, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

@Composable
private fun RowDivider() = PillDivider()

/** Elenco di righe con la linea lieve tra l'una e l'altra; oltre [rows] righe scorre con la barra a destra. */
@Composable
private fun <T> RowList(items: List<T>, rows: Int, key: (T) -> Any, row: @Composable (T) -> Unit) {
    val body: @Composable () -> Unit = {
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            androidx.compose.runtime.key(key(item)) { row(item) }
        }
    }
    if (items.size > rows) ScrollWithBar(RowHeight * rows) { body() } else Column { body() }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

/** Campo di scrittura dentro una bolla, con il pulsante di invio (e, se c'è, uno accanto). */
@Composable
private fun BubbleField(
    hint: String,
    onSend: (String) -> Unit,
    text: String,
    onText: (String) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    fun send() {
        if (text.isNotBlank()) { onSend(text.trim()); onText("") }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(scheme.surface.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (text.isEmpty()) Text(hint, fontSize = 13.sp, color = scheme.onSurfaceVariant)
            BasicTextField(
                value = text, onValueChange = onText,
                textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        if (text.isBlank() && extra != null) {
            extra()
        } else {
            SmallRound(EuriseIcons.send(2f), stringResource(R.string.bubble_add)) { send() }
        }
    }
}

// ---------------------------------------------------------------- Da fare oggi

/**
 * Da fare oggi: le attività di oggi e quelle scadute (non i farmaci, che hanno la loro bolla). Il cerchio spunta,
 * "Domani" rimanda, il + apre un campo che legge l'attività "come si parla".
 */
@Composable
fun TodayTasksPill(
    tasks: List<Task>,
    today: LocalDate,
    compact: Boolean,
    hazeState: HazeState,
    actions: BubbleActions,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val list = tasks.filter { it.listId != TaskList.MEDICINES && it.parentId == null }
    var adding by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    Pill(
        hazeState, modifier, title = stringResource(R.string.bubble_today_title, list.size),
        trailing = { SmallRound(EuriseIcons.plus(2f), stringResource(R.string.bubble_add)) { adding = !adding }; Spacer(Modifier.width(2.dp)) },
    ) {
        if (adding) BubbleField(stringResource(R.string.bubble_today_hint), actions.quickTask, text, { text = it })
        if (list.isEmpty()) {
            if (!adding) BubbleRow(EuriseIcons.task(), stringResource(R.string.bubble_today_empty), null, onClick = { adding = true })
        } else {
            RowList(list, if (compact) 2 else 3, { it.id }) { t ->
                val overdue = t.due?.isBefore(today) == true
                val sub = buildList {
                    if (overdue) add(stringResource(R.string.bubble_overdue))
                    t.time?.let { add(stringResource(R.string.bubble_by, it.format(TimeFormat))) }
                    if (t.listId == TaskList.DATES) add(stringResource(R.string.bubble_dates))
                }.joinToString(" · ")
                val scheme = MaterialTheme.colorScheme
                SwipeRow(
                    Swipe(stringResource(R.string.bubble_done), EuriseIcons.check(2.4f), scheme.primary) { actions.setDone(t) },
                    listOf(
                        Swipe(stringResource(R.string.bubble_tomorrow), EuriseIcons.repeat(2f), scheme.primary) { actions.postpone(t) },
                        Swipe(stringResource(R.string.bubble_open), EuriseIcons.task(2f), scheme.onSurfaceVariant) { actions.openTasks() },
                    ),
                ) {
                    BubbleRow(
                        EuriseIcons.task(), t.title, sub,
                        subColor = if (overdue) scheme.error else scheme.onSurfaceVariant,
                        onClick = actions.openTasks,
                        leading = { TaskCheck(t.title) { actions.setDone(t) } },
                        trailing = { ActionChip(stringResource(R.string.bubble_tomorrow)) { actions.postpone(t) } },
                    )
                }
            }
        }
    }
}

/** Cerchio da spuntare, con 36 dp toccabili. */
@Composable
private fun TaskCheck(title: String, onDone: () -> Unit) {
    val label = stringResource(R.string.bubble_mark_done, title)
    Box(
        Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Checkbox, onClick = onDone).semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(17.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
    }
}

// ---------------------------------------------------------------- Prossimo impegno

/** Il prossimo appuntamento con orario (o, se non ce ne sono, il primo di tutto il giorno). */
fun nextEvent(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent? =
    events.firstOrNull { !it.allDay && it.end.isAfter(now) } ?: events.firstOrNull { it.allDay && it.occursOn(now.toLocalDate()) }

/**
 * Prossimo impegno: quando, dove, quanto ci vuole e i gesti che servono prima di arrivare: Naviga, Avvisa che tardo
 * (un SMS già scritto), Collegati (il link della videochiamata trovato nell'appuntamento), Appunti (una nota legata).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NextEventPill(
    events: List<CalendarEvent>,
    calendarConnected: Boolean,
    travel: Travel?,
    now: LocalDateTime,
    hazeState: HazeState,
    actions: BubbleActions,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    val e = nextEvent(events, now)
    Pill(hazeState, modifier, title = stringResource(R.string.bubble_next_title), trailing = {
        if (e != null && !e.allDay) {
            val minutes = Duration.between(now, e.start).toMinutes()
            val label = when {
                minutes <= 0 -> stringResource(R.string.bubble_now)
                minutes < 60 -> stringResource(R.string.welcome_in_min, minutes)
                e.start.toLocalDate() == now.toLocalDate() -> stringResource(R.string.welcome_at, e.start.format(TimeFormat))
                else -> e.start.format(DayTimeFormat)
            }
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }) {
        when {
            !calendarConnected -> BubbleRow(EuriseIcons.calendar(), stringResource(R.string.today_connect_calendar), null, onClick = onConnect)
            e == null -> EmptyLine(stringResource(R.string.bubble_next_empty))
            else -> {
                val minutes = travel?.takeIf { it.destination == e.location }?.minutes
                val sub = buildList {
                    if (e.location.isNotBlank()) add(e.location)
                    if (minutes != null && !e.allDay) {
                        add(stringResource(R.string.welcome_route_minutes, minutes))
                        add(stringResource(R.string.welcome_leave_by, e.start.minusMinutes((minutes + 5).toLong()).format(TimeFormat)))
                    }
                }.joinToString(" · ")
                val time = if (e.allDay) stringResource(R.string.today_all_day) else e.start.format(TimeFormat)
                BubbleRow(
                    if (e.location.isNotBlank()) EuriseIcons.map() else EuriseIcons.calendar(),
                    "$time  ${e.title}", sub, onClick = { actions.openDay(e.start.toLocalDate()) },
                )
                val link = BubbleText.meetingLink(e.location + " " + e.description)
                val lateText = stringResource(R.string.bubble_late_sms, e.title)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (e.location.isNotBlank() && link == null) ActionChip(stringResource(R.string.bubble_navigate), filled = true) { actions.navigate(e.location) }
                    if (link != null) ActionChip(stringResource(R.string.bubble_join), filled = true) { actions.openLink(link) }
                    if (!e.allDay) ActionChip(stringResource(R.string.bubble_late)) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", lateText).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    ActionChip(stringResource(R.string.bubble_meeting_note)) { actions.meetingNote(e) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Codici e consegne

/** Un codice di accesso trovato in una notifica. */
private data class FoundCode(val code: String, val item: MessageItem)

/**
 * Codici e consegne: il codice arrivato per SMS o da un'app si copia con un tocco e sparisce dopo dieci minuti;
 * i pacchi in arrivo si seguono senza cercare l'app. Legge i testi delle notifiche, quindi si accende solo
 * dall'utente, dopo una spiegazione; spegnerla non chiede nulla.
 */
@Composable
fun CodesPill(
    notifications: List<MessageItem>,
    notificationsOn: Boolean,
    enabled: Boolean,
    now: LocalDateTime,
    compact: Boolean,
    hazeState: HazeState,
    actions: BubbleActions,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    Pill(hazeState, modifier, title = stringResource(R.string.bubble_codes_title), trailing = {
        if (enabled && notificationsOn) {
            Text(
                stringResource(R.string.bubble_turn_off), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { actions.setReadCodes(false) }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }) {
        when {
            !notificationsOn -> BubbleRow(EuriseIcons.key(), stringResource(R.string.setup_notifications), stringResource(R.string.grant), onClick = actions.grantNotifications)
            !enabled -> BubbleRow(
                EuriseIcons.key(), stringResource(R.string.bubble_codes_off), stringResource(R.string.bubble_codes_off_sub),
                trailing = { ActionChip(stringResource(R.string.bubble_turn_on), filled = true) { explain = true } },
            )
            else -> {
                val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val codes = notifications
                    .filter { nowMillis - it.postedAt < CodeLifeMinutes * 60_000 }
                    .mapNotNull { m -> BubbleText.findCode(m.title + " " + m.text)?.let { FoundCode(it, m) } }
                    .distinctBy { it.code }
                val deliveries = notifications.filter { m ->
                    codes.none { it.item.key == m.key } && BubbleText.isDelivery(m.packageName, m.title + " " + m.text)
                }
                val rows: List<Any> = codes + deliveries
                if (rows.isEmpty()) EmptyLine(stringResource(R.string.bubble_codes_empty))
                else RowList(rows, if (compact) 2 else 3, { (it as? FoundCode)?.item?.key?.let { k -> "c$k" } ?: (it as MessageItem).key }) { r ->
                    if (r is FoundCode) {
                        val ago = ((nowMillis - r.item.postedAt) / 60_000).coerceAtLeast(0)
                        val left = (CodeLifeMinutes - ago).coerceAtLeast(1)
                        val copied = stringResource(R.string.bubble_code_copied)
                        val copy = { copySecret(context, r.code); Toast.makeText(context, copied, Toast.LENGTH_SHORT).show() }
                        SwipeRow(
                            Swipe(stringResource(R.string.bubble_copy), EuriseIcons.key(2f), MaterialTheme.colorScheme.primary) { copy() },
                            listOf(Swipe(stringResource(R.string.comms_hide), EuriseIcons.mask(1.8f), MaterialTheme.colorScheme.onSurfaceVariant) { NotificationStore.hide(r.item) }),
                        ) {
                            BubbleRow(
                                EuriseIcons.key(), "${r.code.chunked(3).joinToString(" ")} · ${r.item.appLabel}",
                                stringResource(R.string.bubble_code_sub, ago, left),
                                trailing = { ActionChip(stringResource(R.string.bubble_copy), filled = true) { copy() } },
                            )
                        }
                    } else {
                        val m = r as MessageItem
                        SwipeRow(
                            Swipe(stringResource(R.string.bubble_track), EuriseIcons.parcel(2f), MaterialTheme.colorScheme.primary) { m.contentIntent?.sendSafely() },
                            listOf(Swipe(stringResource(R.string.comms_hide), EuriseIcons.mask(1.8f), MaterialTheme.colorScheme.onSurfaceVariant) { NotificationStore.hide(m) }),
                        ) {
                            BubbleRow(
                                EuriseIcons.parcel(), m.title, m.text.ifBlank { m.appLabel },
                                trailing = { ActionChip(stringResource(R.string.bubble_track)) { m.contentIntent?.sendSafely() } },
                            )
                        }
                    }
                }
            }
        }
    }
    if (explain) {
        PrivacyExplain(
            title = stringResource(R.string.bubble_codes_privacy_title),
            lines = listOf(
                stringResource(R.string.bubble_codes_privacy_read),
                stringResource(R.string.bubble_codes_privacy_where),
                stringResource(R.string.bubble_codes_privacy_who),
                stringResource(R.string.bubble_codes_privacy_back),
            ),
            onConfirm = { explain = false; actions.setReadCodes(true) },
            onDismiss = { explain = false },
        )
    }
}

/** Spiegazione breve prima di accendere una funzione che cambia la privacy: cosa, dove, chi, come tornare indietro. */
@Composable
internal fun PrivacyExplain(title: String, lines: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(EuriseIcons.shield(2f), null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it, fontSize = 14.sp, lineHeight = 19.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.bubble_turn_on), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * Copia [code] negli appunti segnandolo come riservato (la tastiera non lo mostra in anteprima) e lo toglie dopo
 * dieci minuti, se nel frattempo non è stato copiato altro.
 */
private fun copySecret(context: Context, code: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("code", code)
    if (Build.VERSION.SDK_INT >= 24) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            val now = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (now == code && Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
        }
    }, CodeLifeMinutes * 60_000)
}

private fun PendingIntent.sendSafely() {
    runCatching {
        if (Build.VERSION.SDK_INT >= 34) {
            send(null, 0, null, null, null, null, android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle())
        } else {
            send()
        }
    }
}

// ---------------------------------------------------------------- Farmaci

/** Farmaci: le dosi di oggi e quelle non segnate di ieri; "Presa" le spunta e spegne il promemoria. */
@Composable
fun MedsPill(
    tasks: List<Task>,
    today: LocalDate,
    compact: Boolean,
    hazeState: HazeState,
    actions: BubbleActions,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val doses = tasks.filter { it.listId == TaskList.MEDICINES }.sortedWith(compareBy({ it.due }, { it.time }))
    Pill(hazeState, modifier, title = stringResource(R.string.bubble_meds_title), trailing = {
        SmallRound(EuriseIcons.medical(2f), stringResource(R.string.bubble_meds_open), actions.openMedicines)
        Spacer(Modifier.width(2.dp))
    }) {
        if (doses.isEmpty()) EmptyLine(stringResource(R.string.bubble_meds_empty))
        else RowList(doses, if (compact) 2 else 3, { it.id }) { t ->
            val late = t.due?.isBefore(today) == true
            SwipeRow(
                Swipe(stringResource(R.string.bubble_meds_taken), EuriseIcons.check(2.4f), MaterialTheme.colorScheme.primary) { actions.setDone(t) },
                listOf(Swipe(stringResource(R.string.bubble_open), EuriseIcons.medical(2f), MaterialTheme.colorScheme.onSurfaceVariant) { actions.openMedicines() }),
            ) {
            BubbleRow(
                EuriseIcons.medical(), t.title,
                when {
                    late -> stringResource(R.string.bubble_meds_missed)
                    t.time != null -> stringResource(R.string.bubble_meds_at, t.time!!.format(TimeFormat))
                    else -> t.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: stringResource(R.string.bubble_meds_today)
                },
                tint = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                subColor = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = actions.openMedicines,
                trailing = { ActionChip(stringResource(R.string.bubble_meds_taken), filled = true) { actions.setDone(t) } },
            )
            }
        }
    }
}

// ---------------------------------------------------------------- Timer e sveglie

/**
 * Timer e sveglie: i timer in corso nell'app Orologio con i loro comandi (quelli che l'app mette nella notifica),
 * la prossima sveglia e i timer rapidi, che partono senza aprire l'Orologio.
 */
@Composable
fun ClockPill(
    notifications: List<MessageItem>,
    nextAlarm: LocalDateTime?,
    now: LocalDateTime,
    hazeState: HazeState,
    actions: BubbleActions,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    val running = notifications.filter { BubbleText.isClock(it.packageName) }.take(3)
    val started = stringResource(R.string.bubble_timer_started)
    Pill(hazeState, modifier, title = stringResource(R.string.bubble_clock_title), trailing = {
        SmallRound(EuriseIcons.clock(2f), stringResource(R.string.bubble_clock_open), actions.openClock)
        Spacer(Modifier.width(2.dp))
    }) {
        running.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            // Verso destra il primo comando del timer (di solito +1 min), verso sinistra gli altri.
            val scheme = MaterialTheme.colorScheme
            SwipeRow(
                m.actions.firstOrNull()?.let { a -> Swipe(a.title, EuriseIcons.plus(2f), scheme.primary) { a.intent.sendSafely() } },
                m.actions.drop(1).map { a -> Swipe(a.title, EuriseIcons.close(2f), scheme.onSurfaceVariant) { a.intent.sendSafely() } },
            ) {
                BubbleRow(
                    EuriseIcons.alarm(), m.title, m.text,
                    onClick = { m.contentIntent?.sendSafely() },
                    trailing = { m.actions.take(2).forEach { a -> ActionChip(a.title) { a.intent.sendSafely() } } },
                )
            }
        }
        if (running.isNotEmpty()) RowDivider()
        if (nextAlarm != null) {
            val day = when (nextAlarm.toLocalDate()) {
                now.toLocalDate() -> stringResource(R.string.bubble_alarm_today)
                now.toLocalDate().plusDays(1) -> stringResource(R.string.bubble_alarm_tomorrow)
                else -> nextAlarm.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))
            }
            // "Salta" c'è quando la sveglia è di Eurise Orologio: con le altre app non si può saltare da fuori.
            val ours = remember(nextAlarm) { ClockContract.nextAlarmIsOurs(context) }
            val skipped = stringResource(R.string.bubble_alarm_skipped)
            BubbleRow(
                EuriseIcons.clock(), stringResource(R.string.bubble_alarm_main, nextAlarm.format(TimeFormat)), day,
                onClick = actions.openClock,
                trailing = {
                    if (ours) ActionChip(stringResource(R.string.bubble_alarm_skip)) {
                        ClockContract.skipNext(context)
                        Toast.makeText(context, skipped, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            RowDivider()
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bubble_timer), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(1, 5, 10, 25).forEach { min ->
                ActionChip(stringResource(R.string.bubble_minutes, min)) {
                    val ok = runCatching {
                        // Nell'Orologio di Eurise, che sta nella stessa app: nessuna scelta da fare.
                        context.startActivity(ClockContract.timerIntent(context, min * 60))
                    }.isSuccess
                    if (ok) Toast.makeText(context, started.format(min), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Appunto al volo

/**
 * Appunto al volo: una riga scritta o dettata diventa un'attività se dice quando ("domani alle 10"), altrimenti
 * una nota. Sotto, l'ultima nota, da riaprire con un tocco.
 */
@Composable
fun QuickNotePill(
    lastNote: Note?,
    hazeState: HazeState,
    actions: BubbleActions,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val noRecognizer = stringResource(R.string.bubble_no_voice)
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
            text = if (text.isBlank()) heard else "$text $heard"
        }
    }
    val dictateLabel = stringResource(R.string.bubble_dictate)
    Pill(hazeState, modifier, title = stringResource(R.string.bubble_memo_title)) {
        BubbleField(stringResource(R.string.bubble_memo_hint), actions.capture, text, { text = it }) {
            SmallRound(EuriseIcons.mic(2f), dictateLabel) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                if (runCatching { dictate.launch(intent) }.isFailure) Toast.makeText(context, noRecognizer, Toast.LENGTH_SHORT).show()
            }
        }
        if (lastNote != null) {
            RowDivider()
            val ago = Duration.between(Instant.ofEpochMilli(lastNote.updatedAt), Instant.now()).toMinutes()
            BubbleRow(
                EuriseIcons.note(), lastNote.title.ifBlank { lastNote.body.lineSequence().firstOrNull().orEmpty() },
                stringResource(R.string.bubble_memo_last, agoText(ago)),
                onClick = { actions.openNote(lastNote) },
                trailing = { ActionChip(stringResource(R.string.bubble_open)) { actions.openNote(lastNote) } },
            )
        }
    }
}

@Composable
private fun agoText(minutes: Long): String = when {
    minutes < 1 -> stringResource(R.string.time_now)
    minutes < 60 -> "$minutes min"
    minutes < 24 * 60 -> "${minutes / 60} h"
    else -> "${minutes / (24 * 60)} g"
}
