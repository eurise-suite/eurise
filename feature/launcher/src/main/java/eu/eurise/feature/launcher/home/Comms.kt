package eu.eurise.feature.launcher.home

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import eu.eurise.feature.launcher.notifications.CommsKind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.notifications.MessageItem
import eu.eurise.feature.launcher.notifications.NotificationStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Altezza di una riga di comunicazione, linea compresa: serve a decidere quante righe si vedono. */
internal val RowHeight = 47.dp

/** Scorciatoie verso i contatti recenti e preferiti, offerte dalla Scrivania a ogni bolla delle comunicazioni. */
class PeopleShortcuts(val onRecent: () -> Unit, val onStarred: () -> Unit)

val LocalPeopleShortcuts = compositionLocalOf<PeopleShortcuts?> { null }

/** Le azioni delle bolle (attività, richiamate…), fornite dalla Scrivania. */
val LocalBubbleActions = compositionLocalOf<BubbleActions?> { null }

/** I due tondi in alto nella bolla: contatti recenti e preferiti. */
@Composable
fun CommsButtons(onRecent: () -> Unit, onStarred: () -> Unit) {
    SmallRound(EuriseIcons.clock(2f), stringResource(R.string.people_recent), onRecent)
    Spacer(Modifier.width(6.dp))
    SmallRound(EuriseIcons.star(2f), stringResource(R.string.people_starred), onStarred)
    Spacer(Modifier.width(2.dp))
}

/**
 * Contenuto unico della bolla "Ti hanno cercato", sia nella home sia tra le schede del Riepilogo.
 *
 * Ogni riga: toccandola o scorrendola verso destra si apre la bolla grande della conversazione (rispondere,
 * segnare come letto, nascondere, eliminare); scorrendola verso sinistra compare Elimina. A destra resta una
 * striscia libera per scorrere l'elenco col dito, con la barra di scorrimento quando le righe non entrano.
 *
 * @param rows quante righe si vedono senza scorrere.
 * @param onOpen apre la conversazione nell'app (per le notifiche che non hanno nulla da mostrare in bolla).
 * @param onBubble apre la bolla grande della conversazione.
 */
@Composable
fun CommsBody(
    messages: List<MessageItem>,
    enabled: Boolean,
    rows: Int,
    onOpen: (MessageItem) -> Unit,
    onBubble: (MessageItem) -> Unit,
    onGrant: () -> Unit,
    onAppInfo: () -> Unit,
    onHub: () -> Unit,
) {
    when {
        !enabled -> {
            PillLine(EuriseIcons.chat(), stringResource(R.string.setup_notifications), stringResource(R.string.grant), onGrant)
            // Da Android 13 il permesso può essere "con restrizioni": la via passa da Informazioni sull'app.
            if (Build.VERSION.SDK_INT >= 33) {
                Text(
                    stringResource(R.string.app_info), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 25.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAppInfo).padding(vertical = 2.dp),
                )
            }
        }
        messages.isEmpty() -> PillLine(EuriseIcons.chat(), stringResource(R.string.card_messages_empty), null, onHub)
        else -> ScrollWithBar(RowHeight * rows) {
            messages.take(NotificationStore.MaxMessages).forEachIndexed { i, m ->
                // Linea appena percettibile tra un messaggio e l'altro.
                if (i > 0) PillDivider()
                androidx.compose.runtime.key(m.key) {
                    val (right, left) = commsSwipes(m, onBubble, LocalBubbleActions.current)
                    SwipeRow(right, left) {
                        CommsRow(m) { if (m.hasBubble() || m.kind == CommsKind.Call) onBubble(m) else onOpen(m) }
                    }
                }
            }
        }
    }
}

/** Linea appena percettibile tra una riga e l'altra di una bolla: la stessa in tutte le bolle. */
@Composable
internal fun PillDivider() = HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

/** Le notifiche con qualcosa da leggere o da fare si aprono in bolla; le altre (per esempio certe mail) nell'app. */
private fun MessageItem.hasBubble() = reply != null || history.isNotEmpty() || markRead != null || text.isNotBlank()

@Composable
private fun CommsRow(m: MessageItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (m.kind) {
            CommsKind.Call -> EuriseIcons.phone()
            CommsKind.Email -> EuriseIcons.mail()
            CommsKind.Chat -> if (m.voice) EuriseIcons.mic() else EuriseIcons.message()
        }
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${m.title} · ${m.appLabel}", fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                m.text.ifBlank { stringResource(R.string.message_no_preview) }, fontSize = 11.5.sp, lineHeight = 14.sp,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                Instant.ofEpochMilli(m.postedAt).atZone(ZoneId.systemDefault()).format(TimeFormat),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant,
            )
            // Quanti messaggi raccoglie la notifica, se più d'uno.
            if (m.count > 1) {
                Text(
                    m.count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary,
                    modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** Un'azione di scorrimento: verso destra la principale (scatta oltre la soglia), verso sinistra quelle che si scoprono. */
class Swipe(val label: String, val icon: ImageVector, val color: Color, val run: () -> Unit)

/**
 * Riga che si trascina di lato, uguale in tutte le bolle. Verso destra, oltre la soglia, esegue [right] (il simbolo
 * si accende man mano); verso sinistra scopre i tondi di [left]. Le stesse azioni sono offerte a TalkBack.
 */
@Composable
internal fun SwipeRow(right: Swipe?, left: List<Swipe>, content: @Composable () -> Unit) {
    if (right == null && left.isEmpty()) { content(); return }
    val density = LocalDensity.current
    val leftPx = with(density) { (left.size * 60).dp.toPx() }
    val openPx = with(density) { 72.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxRight = if (right != null) openPx * 1.25f else 0f
    val drag = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-leftPx, maxRight)) } }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).semantics {
            customActions = (listOfNotNull(right) + left).map { a -> CustomAccessibilityAction(a.label) { a.run(); true } }
        },
    ) {
        val x = offset.value
        if (x > 1f && right != null) {
            Row(Modifier.matchParentSize().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val alpha = (x / openPx).coerceIn(0.25f, 1f)
                Icon(right.icon, null, tint = right.color, modifier = Modifier.size(20.dp).graphicsLayer { this.alpha = alpha })
                Text(right.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = right.color, modifier = Modifier.graphicsLayer { this.alpha = alpha })
            }
        }
        if (x < -1f) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                left.forEach { a ->
                    Column(
                        Modifier.width(60.dp).clickable(role = Role.Button, onClickLabel = a.label) {
                            scope.launch { offset.snapTo(0f) }
                            a.run()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(a.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(a.icon, a.label, tint = a.color, modifier = Modifier.size(16.dp))
                        }
                        Text(a.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .draggable(
                    drag, Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        when {
                            right != null && (offset.value > openPx || velocity > 1400f) -> {
                                right.run()
                                offset.animateTo(0f)
                            }
                            left.isNotEmpty() && (offset.value < -leftPx / 2 || velocity < -800f) -> offset.animateTo(-leftPx)
                            else -> offset.animateTo(0f)
                        }
                    },
                ),
        ) { content() }
    }
}

/**
 * Gli scorrimenti di una comunicazione, secondo il tipo:
 * - chiamata: verso destra Richiama; verso sinistra Vista e Richiamo (un promemoria tra le attività);
 * - mail: verso destra Leggi (la bolla); verso sinistra Da fare, Archivia (se l'app lo offre) ed Elimina;
 * - chat: verso destra la conversazione; verso sinistra Da fare ed Elimina.
 */
@Composable
private fun commsSwipes(m: MessageItem, onBubble: (MessageItem) -> Unit, actions: BubbleActions?): Pair<Swipe?, List<Swipe>> {
    val scheme = MaterialTheme.colorScheme
    val who = m.conversation.ifBlank { m.title }
    val remove = Swipe(stringResource(R.string.delete), EuriseIcons.trash(2f), scheme.error) {
        if (m.clearable) NotificationStore.dismiss(m.key) else NotificationStore.hide(m)
    }
    return when (m.kind) {
        CommsKind.Call -> {
            val seen = Swipe(stringResource(R.string.comms_seen), EuriseIcons.read(2f), scheme.onSurfaceVariant) {
                if (m.clearable) NotificationStore.dismiss(m.key) else NotificationStore.hide(m)
            }
            val callTask = stringResource(R.string.comms_task_call, who)
            val later = Swipe(stringResource(R.string.comms_call_later), EuriseIcons.task(2f), scheme.primary) {
                actions?.addTask?.invoke(callTask)
                if (m.clearable) NotificationStore.dismiss(m.key) else NotificationStore.hide(m)
            }
            Swipe(stringResource(R.string.comms_call_back), EuriseIcons.phone(2f), scheme.primary) { actions?.callBack?.invoke(m) } to listOf(seen, later)
        }
        CommsKind.Email -> {
            val mailTask = stringResource(R.string.comms_task_mail, who)
            val todo = Swipe(stringResource(R.string.comms_todo), EuriseIcons.task(2f), scheme.primary) { actions?.addTask?.invoke(mailTask) }
            val archive = m.archive?.let { a -> Swipe(stringResource(R.string.comms_archive), EuriseIcons.folder(2f), scheme.onSurfaceVariant) { runCatching { a.intent.send() } } }
            Swipe(stringResource(R.string.comms_read_mail), EuriseIcons.mail(2f), scheme.primary) { onBubble(m) } to listOfNotNull(todo, archive, remove)
        }
        CommsKind.Chat -> {
            val chatTask = stringResource(R.string.comms_task_chat, who)
            val todo = Swipe(stringResource(R.string.comms_todo), EuriseIcons.task(2f), scheme.primary) { actions?.addTask?.invoke(chatTask) }
            Swipe(stringResource(R.string.comms_open_bubble_short), EuriseIcons.chat(2f), scheme.primary) { onBubble(m) } to listOf(todo, remove)
        }
    }
}

/**
 * Elenco che scorre in verticale fino a [maxHeight]. A destra lascia una striscia libera dove il dito scorre senza
 * toccare le righe, e ci disegna una barra sottile quando c'è altro da vedere.
 */
@Composable
internal fun ScrollWithBar(maxHeight: Dp, independent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // In una bolla di altezza scelta scorre già tutta la bolla: una seconda barra confonderebbe.
    if (!independent && LocalBubbleFixedHeight.current) {
        Column(content = content)
        return
    }
    val state = rememberScrollState()
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Box(
        Modifier.heightIn(max = maxHeight).drawWithContent {
            drawContent()
            val max = state.maxValue
            if (max in 1 until Int.MAX_VALUE) {
                val h = size.height
                val thumb = (h * h / (h + max)).coerceAtLeast(18.dp.toPx())
                val y = (h - thumb) * state.value / max
                val w = 3.dp.toPx()
                drawRoundRect(bar, Offset(size.width - w - 3.dp.toPx(), y), Size(w, thumb), CornerRadius(w / 2))
            }
        },
    ) {
        Row(Modifier.verticalScroll(state)) {
            Column(Modifier.weight(1f), content = content)
            Spacer(Modifier.width(14.dp))
        }
    }
}
