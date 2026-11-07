package eu.eurise.feature.launcher.notifications

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Apre la risposta rapida per un messaggio; la fornisce la Scrivania. */
val LocalQuickReply = androidx.compose.runtime.compositionLocalOf<(MessageItem) -> Unit> { {} }

/** Azione che compare scorrendo di lato una notifica. */
data class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

/**
 * Le azioni di una notifica per lo scorrimento laterale: Rispondi (se l'app consente la risposta diretta),
 * Segna come letto (se l'app lo offre) ed Elimina (se la notifica si può togliere).
 */
@Composable
fun messageActions(m: MessageItem, onReply: (MessageItem) -> Unit): List<SwipeAction> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val list = mutableListOf<SwipeAction>()
    if (m.reply != null) list += SwipeAction(stringResource(R.string.reply), EuriseIcons.reply(2f), MaterialTheme.colorScheme.primary) { onReply(m) }
    m.markRead?.let { a ->
        list += SwipeAction(stringResource(R.string.mark_read), EuriseIcons.read(2f), if (dark) Color(0xFF8FA9D9) else Color(0xFF3E5C8A)) {
            runCatching { a.intent.send() }
        }
    }
    if (m.clearable) {
        list += SwipeAction(stringResource(R.string.delete), EuriseIcons.trash(2f), if (dark) Color(0xFFE8939B) else Color(0xFF8C2F39)) {
            NotificationStore.dismiss(m.key)
        }
    }
    return list
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Riga che scorre verso sinistra e scopre le [actions] tonde a destra. Si apre se trascinata oltre metà,
 * si richiude toccando un'azione o trascinando indietro. Senza azioni è una riga ferma.
 */
@Composable
fun SwipeActionsRow(actions: List<SwipeAction>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val revealPx = with(density) { (actions.size * 60).dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val drag = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f)) } }
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        // Le azioni esistono solo mentre la riga è spostata: a riposo non si vedono e non si toccano.
        if (offset.value < -1f) Row(
            Modifier.matchParentSize().padding(end = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { a ->
                Column(
                    Modifier.width(60.dp).clickable(role = Role.Button, onClickLabel = a.label) {
                        scope.launch { offset.animateTo(0f) }
                        a.onClick()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Tondi dello stesso vetro delle pillole; il simbolo prende il colore dell'azione.
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(a.icon, a.label, tint = a.color, modifier = Modifier.size(17.dp))
                    }
                    Text(a.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer { alpha = if (offset.value < -1f) 0.55f else 1f }
                .draggable(
                    drag, Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val open = offset.value < -revealPx / 2 || velocity < -800f
                        offset.animateTo(if (open) -revealPx else 0f)
                    },
                ),
        ) { content() }
    }
}

/**
 * Risposta rapida: la conversazione arrivata nella notifica e il campo per rispondere, senza aprire l'app.
 * "Apri chat" porta alla conversazione nell'app, come toccare la notifica.
 */
@Composable
fun QuickReplyDialog(message: MessageItem, onOpenChat: (MessageItem) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val sentLabel = stringResource(R.string.reply_sent)
    fun send() {
        val reply = message.reply ?: return
        if (text.isBlank()) return
        if (reply.send(context, text.trim())) {
            Toast.makeText(context, sentLabel, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .windowInsetsPadding(WindowInsets.ime),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.message(2f), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(message.conversation.ifBlank { message.title }, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.reply_new_messages, message.appLabel, message.count),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.reply_open_chat), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenChat(message) }.padding(6.dp),
                )
            }
            val history = message.history.ifEmpty { listOf(null to message.text) }
            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState(), reverseScrolling = false),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                history.forEach { (sender, body) ->
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (!sender.isNullOrBlank()) Text(sender, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(body, fontSize = 13.5.sp, lineHeight = 19.sp)
                    }
                }
            }
            if (message.reply != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        if (text.isEmpty()) Text(stringResource(R.string.reply_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = text, onValueChange = { text = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.reply)) { send() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.send(2f), stringResource(R.string.reply), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.markRead?.let { a ->
                    Chip(stringResource(R.string.mark_read)) { runCatching { a.intent.send() }; onClose() }
                }
                Chip(stringResource(R.string.close), onClose)
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}
