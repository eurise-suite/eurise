package eu.eurise.feature.launcher.notifications

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R

/** Apre la risposta rapida per un messaggio; la fornisce la Scrivania. */
val LocalQuickReply = androidx.compose.runtime.compositionLocalOf<(MessageItem) -> Unit> { {} }

/**
 * Bolla grande della conversazione: i messaggi arrivati nella notifica, il campo per rispondere e le azioni,
 * tutto senza aprire l'app. "Nascondi" la toglie da Eurise lasciandola da leggere nell'app (nessuna conferma di
 * lettura al mittente); "Apri chat" porta alla conversazione nell'app, come toccare la notifica.
 */
@Composable
fun QuickReplyDialog(
    message: MessageItem,
    onOpenChat: (MessageItem) -> Unit,
    onClose: () -> Unit,
    onTask: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val sentLabel = stringResource(R.string.reply_sent)
    val hiddenLabel = stringResource(R.string.comms_hidden)
    fun send() {
        val reply = message.reply ?: return
        if (text.isBlank()) return
        if (reply.send(context, text.trim())) {
            Toast.makeText(context, sentLabel, Toast.LENGTH_SHORT).show()
            NotificationStore.answered(message)
            onClose()
        }
    }
    val history = message.history.ifEmpty { listOf(null to message.text.ifBlank { context.getString(R.string.message_no_preview) }) }
    val scroll = rememberScrollState()
    // Si parte dall'ultimo messaggio, come in una chat.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .windowInsetsPadding(WindowInsets.ime)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(
                        when (message.kind) { CommsKind.Call -> EuriseIcons.phone(2f); CommsKind.Email -> EuriseIcons.mail(2f); else -> EuriseIcons.message(2f) },
                        null, tint = scheme.onPrimary, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(message.conversation.ifBlank { message.title }, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.reply_new_messages, message.appLabel, message.count), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.reply_open_chat), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenChat(message) }.padding(6.dp),
                )
            }
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                history.forEach { (sender, body) ->
                    Column(
                        Modifier.padding(end = 36.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                            .background(scheme.onSurface.copy(alpha = 0.07f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (!sender.isNullOrBlank()) Text(sender, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                        Text(body, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            if (message.reply != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(scheme.surface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (text.isEmpty()) Text(stringResource(R.string.reply_hint), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                        BasicTextField(
                            value = text, onValueChange = { text = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(scheme.primary)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.reply)) { send() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(EuriseIcons.send(2f), stringResource(R.string.reply), tint = scheme.onPrimary, modifier = Modifier.size(18.dp)) }
                }
            }
            val who = message.conversation.ifBlank { message.title }
            if (message.voice) {
                // Il vocale si ascolta qui solo se l'app lo mette nella notifica; altrimenti si apre la chat.
                val onlyInApp = stringResource(R.string.comms_audio_in_app)
                var playing by remember { mutableStateOf(false) }
                val player = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player.value?.release() } }
                Chip(EuriseIcons.play(2f), stringResource(if (message.audio != null) (if (playing) R.string.comms_audio_stop else R.string.comms_audio_play) else R.string.comms_audio_open), scheme.primary) {
                    val uri = message.audio
                    if (uri == null) { onOpenChat(message); return@Chip }
                    if (playing) { player.value?.release(); player.value = null; playing = false; return@Chip }
                    val p = runCatching {
                        android.media.MediaPlayer().apply {
                            setDataSource(context, uri); prepare(); start()
                            setOnCompletionListener { playing = false }
                        }
                    }.getOrNull()
                    if (p == null) { Toast.makeText(context, onlyInApp, Toast.LENGTH_SHORT).show(); onOpenChat(message) }
                    else { player.value = p; playing = true }
                }
            }
            if (message.kind == CommsKind.Call) {
                val callTask = stringResource(R.string.comms_task_call, who)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(EuriseIcons.phone(2f), stringResource(R.string.comms_call_back), scheme.primary) { CallBack.call(context, message); onClose() }
                    Chip(EuriseIcons.message(2f), stringResource(R.string.comms_sms), scheme.primary) { CallBack.sms(context, message); onClose() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(EuriseIcons.task(2f), stringResource(R.string.comms_call_later), scheme.primary) {
                        onTask(callTask)
                        if (message.clearable) NotificationStore.dismiss(message.key) else NotificationStore.hide(message)
                        onClose()
                    }
                    Chip(EuriseIcons.read(2f), stringResource(R.string.comms_seen), scheme.onSurface) {
                        if (message.clearable) NotificationStore.dismiss(message.key) else NotificationStore.hide(message)
                        onClose()
                    }
                }
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val taskText = stringResource(if (message.kind == CommsKind.Email) R.string.comms_task_mail else R.string.comms_task_chat, who)
                Chip(EuriseIcons.task(2f), stringResource(R.string.comms_todo), scheme.primary) { onTask(taskText); onClose() }
                message.archive?.let { a ->
                    Chip(EuriseIcons.folder(2f), stringResource(R.string.comms_archive), scheme.onSurface) { runCatching { a.intent.send() }; onClose() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.markRead?.let { a ->
                    Chip(EuriseIcons.read(2f), stringResource(R.string.comms_read), scheme.primary) { runCatching { a.intent.send() }; onClose() }
                }
                Chip(EuriseIcons.mask(1.8f), stringResource(R.string.comms_hide), scheme.onSurface) {
                    NotificationStore.hide(message)
                    Toast.makeText(context, hiddenLabel, Toast.LENGTH_SHORT).show()
                    onClose()
                }
                if (message.clearable) {
                    Chip(EuriseIcons.trash(2f), stringResource(R.string.delete), scheme.error) { NotificationStore.dismiss(message.key); onClose() }
                }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
