package eu.eurise.messages.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.contacts.Avatar
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.Reach
import eu.eurise.core.data.messages.MessageInsights
import eu.eurise.core.data.messages.MessageInsights.Insight
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.suite.HubBridge
import eu.eurise.messages.BuildConfig
import eu.eurise.messages.R
import eu.eurise.messages.data.Message
import eu.eurise.messages.data.SmsStore
import eu.eurise.messages.system.Notifications
import eu.eurise.messages.system.Scheduled
import eu.eurise.messages.system.Sender
import eu.eurise.messages.system.TranslateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una conversazione (anteprime MsgConversazione): i messaggi e, sotto quelli recenti, ciò che si capisce sul telefono.
 * Un appuntamento confermato dall'altra parte diventa «Crea l'evento»; un numero dà Chiama, SMS, WhatsApp, Telegram,
 * Aggiungi in rubrica; un testo in un'altra lingua si traduce con Eurise Leggi; una cosa da fare va in Attività, un
 * appunto in Note. Sotto, il campo per scrivere con l'allegato (MMS) e l'invio programmato.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ConversationScreen(threadId: Long, addresses: List<String>, draft: String, attachment: Uri?, mime: String?, isDefault: Boolean, tick: Int, onBecomeDefault: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var contacts by remember { mutableStateOf<Map<String, Contact>>(emptyMap()) }
    var text by remember(threadId) { mutableStateOf(draft) }
    var file by remember(threadId) { mutableStateOf(attachment) }
    var fileType by remember(threadId) { mutableStateOf(mime) }
    var schedule by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val translations = remember { mutableStateMapOf<String, String>() }
    val now = remember { LocalDateTime.now() }

    LaunchedEffect(threadId, tick) {
        if (threadId >= 0) messages = SmsStore.messages(context, threadId)
        contacts = Contacts.names(context, addresses)
        if (threadId >= 0) { SmsStore.markRead(context, threadId); Notifications.cancel(context, threadId) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { file = uri; fileType = context.contentResolver.getType(uri) } }

    val main = addresses.firstOrNull().orEmpty()
    val contact = contacts[main]
    val title = if (addresses.size > 1) addresses.joinToString(", ") { contacts[it]?.name?.substringBefore(' ') ?: Contacts.format(it) }
    else contact?.name ?: Contacts.format(main)
    val insights = remember(messages) {
        messages.associate { m -> m.key to MessageInsights.analyze(m.body, LocalDateTime.ofInstant(Instant.ofEpochMilli(m.date), ZoneId.systemDefault())) }
    }
    // Le proposte si mostrano sotto gli ultimi messaggi, non su tutta la storia.
    val recentKeys = remember(messages) { messages.takeLast(6).map { it.key }.toSet() }

    fun send(body: String = text, at: Long? = null) {
        val to = addresses.filter { it.isNotBlank() }
        if (to.isEmpty() || (body.isBlank() && file == null)) return
        if (at != null) { Scheduled.send(context, to, body, at); text = ""; error = context.getString(R.string.ms_scheduled_ok); return }
        val f = file
        val t = fileType
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { if (f != null) Sender.sendMms(context, to, body, f, t) else Sender.sendSms(context, to, body) }.isSuccess
            }
            if (ok) { if (body == text) text = ""; file = null; fileType = null; error = null } else error = context.getString(R.string.ms_send_failed)
            messages = SmsStore.messages(context, threadId.takeIf { it >= 0 } ?: SmsStore.threadId(context, to.toSet()))
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("", onBack) {}
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(contact?.name ?: title, contact?.photo, 38.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (contact != null && addresses.size == 1) Text(Contacts.format(main), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (addresses.size == 1 && main.any(Char::isDigit)) IconCircle(EuriseIcons.phone(), stringResource(R.string.ms_call)) { Reach.open(context, Reach.dial(main)) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val list = messages.reversed()
            list.forEachIndexed { index, m ->
                val ins = insights[m.key].orEmpty()
                if (m.key in recentKeys) {
                    val cards = ins.filter { it !is Insight.Foreign && it !is Insight.Code && it != Insight.Delivery }
                    if (cards.isNotEmpty() || (m.incoming && MessageInsights.isSuspicious(m.body))) item(key = m.key + "-i") {
                        InsightCard(m, cards, confirmed(messages, m), contact, scope, onReply = { send(it) })
                    }
                }
                item(key = m.key) {
                    Bubble(m, ins, translations[m.key], onTranslate = { lang ->
                        scope.launch {
                            translations[m.key] = context.getString(R.string.ms_translating)
                            translations[m.key] = when (val r = TranslateClient.translate(context, m.body, lang, "it")) {
                                is TranslateClient.Result.Done -> r.text
                                TranslateClient.Result.Missing -> { TranslateClient.openLanguages(context); context.getString(R.string.ms_translate_missing) }
                                TranslateClient.Result.NoApp -> context.getString(R.string.ms_translate_no_app)
                                TranslateClient.Result.Failed -> context.getString(R.string.ms_translate_failed)
                            }
                        }
                    }, onRetry = { send(m.body) }, onDelete = { scope.launch { SmsStore.deleteMessage(context, m); messages = messages - m } })
                }
                val day = day(m.date)
                val older = list.getOrNull(index + 1)
                if (older == null || day(older.date) != day) item(key = m.key + "-d") {
                    Text(dayLabel(day), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        if (!isDefault) Banner(stringResource(R.string.ms_need_default_to_send), stringResource(R.string.ms_become_default_ok), onBecomeDefault)
        error?.let { Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(R.string.ms_schedule), light = true) { if (text.isNotBlank()) schedule = true }
            }
            file?.let { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Thumb(f, 64)
                    Text(stringResource(R.string.ms_attachment), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    IconCircle(EuriseIcons.close(), stringResource(R.string.ms_remove_attachment)) { file = null }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconCircle(EuriseIcons.plus(), stringResource(R.string.ms_attach)) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 11.dp)) {
                    if (text.isEmpty()) Text(stringResource(if (file != null) R.string.ms_mms_to else R.string.ms_sms_to, contact?.name?.substringBefore(' ') ?: title), fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(text, { text = it }, textStyle = TextStyle(fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(role = Role.Button, onClickLabel = stringResource(R.string.ms_send)) { send() }, contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.send(), stringResource(R.string.ms_send), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (schedule) ModalBottomSheet(onDismissRequest = { schedule = false }, containerColor = MaterialTheme.colorScheme.background) {
        val today = LocalDate.now()
        val options = listOfNotNull(
            stringResource(R.string.ms_in_one_hour) to LocalDateTime.now().plusHours(1),
            LocalTime.of(20, 0).takeIf { LocalTime.now().isBefore(it) }?.let { stringResource(R.string.ms_tonight) to today.atTime(it) },
            stringResource(R.string.ms_tomorrow_morning) to today.plusDays(1).atTime(8, 0),
            stringResource(R.string.ms_tomorrow_9) to today.plusDays(1).atTime(9, 0),
        )
        Column(Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ms_schedule), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            options.forEach { (label, at) ->
                Text(label + " · " + at.format(DateTimeFormatter.ofPattern("EEE d, HH:mm", Locale.ITALIAN)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { schedule = false; send(at = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }.padding(14.dp))
            }
            Text(stringResource(R.string.ms_schedule_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Vero se dopo [m] l'altra parte ha confermato («ok», «va bene», «perfetto»…). */
private fun confirmed(all: List<Message>, m: Message): Boolean {
    val after = all.filter { it.date > m.date }
    return after.any { it.incoming != m.incoming && MessageInsights.isConfirmation(it.body) }
}

private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun dayLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> stringResource(R.string.ms_today)
    LocalDate.now().minusDays(1) -> stringResource(R.string.ms_yesterday)
    else -> d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Message, insights: List<Insight>, translation: String?, onTranslate: (String) -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val mine = !m.incoming
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 6.dp, bottomEnd = if (mine) 6.dp else 18.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(onClick = { if (m.status == Message.Status.Failed) onRetry() }, onLongClick = { menu = !menu })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            m.attachments.filter { it.type.startsWith("image/") }.forEach { Thumb(it.uri, 240) }
            m.attachments.filterNot { it.type.startsWith("image/") }.forEach { a ->
                val tint = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Row(Modifier.clickable { Reach.open(context, Intent(Intent.ACTION_VIEW).setDataAndType(a.uri, a.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(EuriseIcons.link(), null, tint = tint, modifier = Modifier.size(16.dp))
                    Text(a.name ?: a.type, fontSize = 13.sp, color = tint)
                }
            }
            if (m.body.isNotEmpty()) Text(m.body, fontSize = 14.5.sp, lineHeight = 21.sp, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            translation?.let { Text(it, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary) }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(time(m.date) + statusLabel(m), fontSize = 11.sp, color = if (m.status == Message.Status.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            insights.filterIsInstance<Insight.Foreign>().firstOrNull()?.let { f ->
                if (translation == null) Text(stringResource(R.string.ms_translate_from, Locale(f.language).getDisplayLanguage(Locale.ITALIAN)), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onTranslate(f.language) }.padding(horizontal = 6.dp, vertical = 3.dp))
            }
            insights.filterIsInstance<Insight.Code>().firstOrNull()?.let { c ->
                Text(stringResource(R.string.ms_copy_code_short, c.code), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { copy(context, c.code) }.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        if (menu) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
            Pill(stringResource(R.string.ms_copy), light = true) { copy(context, m.body); menu = false }
            Pill(stringResource(R.string.ms_share), light = true) {
                Reach.open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.body), null)); menu = false
            }
            Pill(stringResource(R.string.ms_delete), light = true) { onDelete(); menu = false }
        }
    }
}

@Composable
private fun statusLabel(m: Message): String = when (m.status) {
    Message.Status.Sending -> " · " + stringResource(R.string.ms_sending)
    Message.Status.Failed -> " · " + stringResource(R.string.ms_failed_retry)
    Message.Status.Delivered -> " · " + stringResource(R.string.ms_delivered)
    else -> ""
}

private fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("H:mm"))

private fun copy(context: android.content.Context, text: String) =
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Eurise", text))

/** Ciò che si capisce da un messaggio, con le azioni (anteprima MsgConversazione, «Capito sul telefono»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightCard(m: Message, insights: List<Insight>, confirmed: Boolean, contact: Contact?, scope: kotlinx.coroutines.CoroutineScope, onReply: (String) -> Unit) {
    val context = LocalContext.current
    val suspicious = m.incoming && MessageInsights.isSuspicious(m.body)
    var done by remember { mutableStateOf<String?>(null) }
    val permission = BuildConfig.SUITE_PERMISSION
    Column(
        Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(18.dp))
            .background(if (suspicious) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (suspicious) {
            Text(stringResource(R.string.ms_suspicious), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.ms_suspicious_d), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            return@Column
        }
        Text(stringResource(R.string.ms_understood), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        val who = contact?.name ?: Contacts.format(m.address)
        insights.forEach { i ->
            when (i) {
                is Insight.Event -> {
                    val label = i.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)).replaceFirstChar { it.uppercase() } +
                        (i.time?.let { ", " + it.format(DateTimeFormatter.ofPattern("H:mm")) } ?: "")
                    Text((if (confirmed) stringResource(R.string.ms_confirmed) + " · " else "") + label, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(stringResource(R.string.ms_create_event), light = !confirmed) {
                            val title = context.getString(R.string.ms_event_title, who)
                            Reach.open(context, i.time?.let { t -> Reach.event(title, i.date.atTime(t), description = m.body) } ?: Reach.allDayEvent(title, i.date))
                        }
                        if (!confirmed && m.incoming) {
                            val ok = stringResource(R.string.ms_reply_ok)
                            Pill(stringResource(R.string.ms_reply_quoted, ok), light = true) { onReply(ok) }
                        }
                    }
                }
                is Insight.Phone -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(i.number, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = 7.dp, end = 4.dp))
                    Pill(stringResource(R.string.ms_call)) { Reach.open(context, Reach.dial(i.number)) }
                    Pill(stringResource(R.string.ms_sms), light = true) { Reach.open(context, Reach.sms(i.number)) }
                    Reach.whatsapp(context, i.number)?.let { w -> Pill("WhatsApp", light = true) { Reach.open(context, w) } }
                    Reach.whatsappBusiness(context, i.number)?.let { w -> Pill("WA Business", light = true) { Reach.open(context, w) } }
                    Reach.telegram(context, i.number)?.let { t -> Pill("Telegram", light = true) { Reach.open(context, t) } }
                    Pill(stringResource(R.string.ms_add_contact), light = true) { Reach.open(context, Reach.addContact(i.number)) }
                }
                is Insight.Email -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.ms_write_email, i.address), light = true) { Reach.open(context, Reach.email(i.address)) }
                }
                is Insight.Link -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.ms_open_link, i.url.removePrefix("https://").removePrefix("http://").take(32)), light = true) { Reach.open(context, Reach.web(i.url)) }
                }
                is Insight.Address -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.ms_open_map, i.text), light = true) { Reach.open(context, Reach.map(i.text)) }
                }
                is Insight.Task -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.ms_to_tasks), light = true) {
                        done = if (HubBridge.send(context, BuildConfig.HUB_PACKAGE, permission, HubBridge.ACTION_TASK, i.text)) context.getString(R.string.ms_sent_to_tasks) else context.getString(R.string.ms_no_hub)
                    }
                }
                is Insight.Note -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.ms_to_notes), light = true) {
                        val noteText = context.getString(R.string.ms_note_title, who) + "\n" + i.text
                        done = if (HubBridge.send(context, BuildConfig.HUB_PACKAGE, permission, HubBridge.ACTION_NOTE, noteText)) context.getString(R.string.ms_sent_to_notes) else context.getString(R.string.ms_no_hub)
                    }
                }
                else -> Unit
            }
        }
        done?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}

/** Anteprima di un'immagine (allegato), ridotta alla misura mostrata. */
@Composable
private fun Thumb(uri: Uri, sizeDp: Int) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val target = sizeDp * context.resources.displayMetrics.density
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val img = image
    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
        .clickable { Reach.open(context, Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) })
    else Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}
