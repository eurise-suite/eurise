package eu.eurise.feature.launcher.notifications

import android.os.Build
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un messaggio da mostrare nella scheda Comunicazioni. */
data class MessageItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** Apre la conversazione nell'app che ha inviato la notifica. */
    val contentIntent: PendingIntent?,
    /** Azioni della notifica che non chiedono di scrivere (per esempio "Segna come letto"). */
    val actions: List<NotificationAction> = emptyList(),
    val clearable: Boolean = true,
    val isMessage: Boolean = false,
    /** Notifica di servizio (in corso, download, sincronizzazioni…): nel Pannello finisce nella riga riassuntiva. */
    val service: Boolean = false,
    /** Nome della chat (gruppo o persona) quando l'app lo indica; altrimenti il titolo. */
    val conversation: String = title,
    /** I messaggi arrivati nella notifica, dal più vecchio: mittente (se c'è) e testo. */
    val history: List<Pair<String?, String>> = emptyList(),
    /** Risposta diretta offerta dall'app (WhatsApp, Signal, Telegram, Messaggi, molte mail). */
    val reply: ReplyAction? = null,
    /** "Segna come letto", se l'app lo offre. */
    val markRead: NotificationAction? = null,
    /** Chiamata, mail o chat: decide gli scorrimenti e le azioni della bolla. */
    val kind: CommsKind = CommsKind.Chat,
    /** "Archivia" (di solito delle app di posta), se c'è. */
    val archive: NotificationAction? = null,
    /** "Richiama" della notifica di chiamata persa, se l'app lo offre. */
    val callBack: NotificationAction? = null,
    /** L'audio di un messaggio vocale, quando l'app lo mette nella notifica (poche lo fanno). */
    val audio: android.net.Uri? = null,
    /** Vero se l'ultimo messaggio è un vocale (anche quando l'audio non è nella notifica). */
    val voice: Boolean = false,
) {
    /** Quanti messaggi rappresenta la notifica (almeno uno). */
    val count: Int get() = history.size.coerceAtLeast(1)
}

data class NotificationAction(val title: String, val intent: PendingIntent)

/** Che tipo di comunicazione è. */
enum class CommsKind { Chat, Email, Call }

/** Azione di risposta: il testo scritto si consegna all'app attraverso il suo [input]. */
data class ReplyAction(val title: String, val intent: PendingIntent, val input: android.app.RemoteInput) {
    /** Invia [text] come risposta, come farebbe la tendina di sistema. */
    fun send(context: Context, text: String): Boolean = runCatching {
        val fill = android.content.Intent()
        val results = android.os.Bundle().apply { putCharSequence(input.resultKey, text) }
        android.app.RemoteInput.addResultsToIntent(arrayOf(input), fill, results)
        intent.send(context, 0, fill)
    }.isSuccess
}

/** Il brano in riproduzione, da qualsiasi app che usi una sessione multimediale. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val appLabel: String,
    val playing: Boolean,
    val controller: MediaController,
)

/**
 * Stato delle notifiche visto dal launcher: numeri sulle icone, messaggi recenti e brano in ascolto.
 * Si riempie solo se l'utente concede l'accesso alle notifiche; nulla lascia il telefono.
 */
object NotificationStore {
    /** Quante comunicazioni tiene la bolla "Ti hanno cercato" (le altre restano nel Pannello). */
    const val MaxMessages = 20

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    /** Tutte le notifiche, per il Pannello Eurise. */
    private val _all = MutableStateFlow<List<MessageItem>>(emptyList())
    val all: StateFlow<List<MessageItem>> = _all.asStateFlow()

    private var service: java.lang.ref.WeakReference<NotificationListenerService>? = null

    /** Toglie una notifica, come lo scorrimento nella tendina di sistema. */
    fun dismiss(key: String) {
        service?.get()?.let { runCatching { it.cancelNotification(key) } }
    }

    /** Notifiche nascoste in Eurise: chiave → istante della notifica nascosta. Un messaggio nuovo la fa ricomparire. */
    private val hidden = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Nasconde [m] dalle bolle e dal Pannello senza segnarlo come letto: nell'app di messaggi resta da leggere,
     * quindi al mittente non arriva la conferma di lettura. Resta nella tendina di sistema.
     */
    fun hide(m: MessageItem) {
        hidden[m.key] = m.postedAt
        _messages.value = _messages.value.filterNot { it.key == m.key }
        _all.value = _all.value.filterNot { it.key == m.key }
    }

    private fun StatusBarNotification.isHidden() = hidden[key] == postTime

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal fun update(service: NotificationListenerService) {
        this.service = java.lang.ref.WeakReference(service)
        val raw = runCatching { service.activeNotifications }.getOrNull().orEmpty().filter { it.packageName != service.packageName }
        // Il riepilogo di un gruppo si scarta solo se il gruppo ha anche le notifiche singole: alcune app
        // (per esempio Proton Mail) a volte lasciano solo il riepilogo.
        val groupsWithChildren = raw.filter { !it.notification.isGroupSummary() }.mapNotNull { it.groupKey }.toSet()
        val shown = raw.filter { !it.notification.isGroupSummary() || it.groupKey !in groupsWithChildren }
        hidden.keys.retainAll(shown.map { it.key }.toSet())
        val visible = shown.filterNot { it.isHidden() }
        _all.value = visible
            .filter { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null || it.isMessage() }
            .sortedByDescending { it.postTime }
            .take(80)
            .map { it.toMessage(service) }
        val active = shown.filter { it.isClearable }
        _counts.value = active.groupingBy { it.packageName }.eachCount()
        // Le comunicazioni si prendono da tutte le notifiche visibili: alcune app (WhatsApp tra queste)
        // marcano certe notifiche come non cancellabili o le raggruppano in modo diverso dalle altre.
        _messages.value = visible
            .filter { it.isMessage() }
            .distinctBy { it.key }
            .sortedByDescending { it.postTime }
            .take(MaxMessages)
            .map { it.toMessage(service) }
        updateMedia(service)
    }

    internal fun updateMedia(context: Context) {
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, EuriseNotificationListener::class.java)
        val controller = runCatching { msm.getActiveSessions(component) }.getOrNull()?.firstOrNull()
        _nowPlaying.value = controller?.let { c ->
            val meta = c.metadata
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            if (title.isBlank()) null else NowPlaying(
                title = title,
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                appLabel = labelOf(context, c.packageName),
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                controller = c,
            )
        }
    }

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) {
            _counts.value = emptyMap(); _messages.value = emptyList(); _all.value = emptyList(); _nowPlaying.value = null
        }
    }

    /** Vero se l'utente ha concesso a Eurise l'accesso alle notifiche. */
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(context, EuriseNotificationListener::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun Notification.isGroupSummary() = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

    /**
     * Una comunicazione: per categoria dichiarata, per stile "conversazione", oppure perché viene da un'app
     * di posta, chat, SMS o telefono nota (alcune, come Proton Mail, non dichiarano la categoria).
     */
    private fun StatusBarNotification.isMessage(): Boolean {
        val cat = notification.category
        if (cat == Notification.CATEGORY_MESSAGE || cat == Notification.CATEGORY_EMAIL ||
            cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_MISSED_CALL
        ) return true
        if (notification.extras.containsKey(Notification.EXTRA_MESSAGES)) return true
        if (notification.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MessagingStyle") == true) return true
        return CommunicationPackages.any { packageName.startsWith(it) }
    }

    private val CommunicationPackages = listOf(
        "ch.protonmail", "me.proton", "com.infomaniak.mail", "foundation.e.mail", "com.fsck.k9", "net.thunderbird",
        "eu.faircode.email", "com.tutao.tutanota", "de.tutao", "com.google.android.gm", "com.microsoft.office.outlook",
        "com.whatsapp", "org.telegram", "org.thunderdog.challegram", "org.thoughtcrime.securesms", "im.vector", "io.element",
        "chat.simplex", "eu.siacs.conversations", "com.android.dialer", "com.google.android.dialer", "org.fossify.phone",
        "foundation.e.message", "com.android.messaging", "org.fossify.messages", "com.google.android.apps.messaging",
        "com.moez.QKSMS", "dev.octoshrimpy.quik",
    )

    private fun StatusBarNotification.toMessage(context: Context): MessageItem {
        val extras = notification.extras
        val all = notification.actions.orEmpty().filter { it.actionIntent != null }
        val replyAction = all.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true &&
                (Build.VERSION.SDK_INT < 28 || a.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY || a.semanticAction == Notification.Action.SEMANTIC_ACTION_NONE)
        }
        val readAction = all.firstOrNull { a ->
            a.remoteInputs.isNullOrEmpty() && (
                (Build.VERSION.SDK_INT >= 28 && a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) ||
                    a.title?.toString()?.lowercase()?.let { t -> "lett" in t || "read" in t } == true
                )
        }
        val history = messageHistory(extras)
        val titleOf = { a: Notification.Action -> a.title?.toString()?.lowercase().orEmpty() }
        val archiveAction = all.firstOrNull { a -> a.remoteInputs.isNullOrEmpty() && ("archiv" in titleOf(a)) }
        val callAction = all.firstOrNull { a ->
            a.remoteInputs.isNullOrEmpty() && (Build.VERSION.SDK_INT >= 28 && a.semanticAction == Notification.Action.SEMANTIC_ACTION_CALL ||
                listOf("richiam", "call back", "callback", "chiama").any { it in titleOf(a) })
        }
        val audio = lastAudio(extras)
        val cat = notification.category
        val isMsg = isMessage()
        return MessageItem(
            key = key,
            packageName = packageName,
            appLabel = labelOf(context, packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.takeIf { it.isNotBlank() } ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: labelOf(context, packageName),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.takeIf { it.isNotBlank() } ?: lastMessageText(extras),
            postedAt = postTime,
            contentIntent = notification.contentIntent,
            actions = notification.actions.orEmpty()
                .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null && !it.title.isNullOrBlank() }
                .take(2)
                .map { NotificationAction(it.title.toString(), it.actionIntent) },
            clearable = isClearable,
            isMessage = isMsg,
            service = !isMsg && (isOngoing || !isClearable || cat in ServiceCategories ||
                extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0),
            conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            history = history,
            reply = replyAction?.let { a ->
                val input = a.remoteInputs!!.first { it.allowFreeFormInput }
                ReplyAction(a.title?.toString().orEmpty(), a.actionIntent, input)
            },
            markRead = readAction?.let { NotificationAction(it.title.toString(), it.actionIntent) },
            kind = kindOf(),
            archive = archiveAction?.let { NotificationAction(it.title.toString(), it.actionIntent) },
            callBack = callAction?.let { NotificationAction(it.title.toString(), it.actionIntent) },
            audio = audio,
            voice = audio != null || VoiceText.containsMatchIn(
                (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString() + " " + lastMessageText(extras),
            ),
        )
    }

    private fun StatusBarNotification.kindOf(): CommsKind {
        val cat = notification.category
        return when {
            cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_MISSED_CALL ||
                listOf("dialer", ".phone", "telecom", "contacts").any { it in packageName } -> CommsKind.Call
            cat == Notification.CATEGORY_EMAIL ||
                listOf("mail", "k9", "thunderbird", "faircode.email", "tutanota", "tutao", "outlook", "com.google.android.gm").any { it in packageName } -> CommsKind.Email
            else -> CommsKind.Chat
        }
    }

    /** "Messaggio vocale", "🎤 0:12", "Voice message", "Audio"… */
    private val VoiceText = Regex("""(?iu)(🎤|🎙|messaggio vocale|nota vocale|vocale \(|voice message|voice note|audio message|^audio\b)""")

    /** L'audio dell'ultimo messaggio, se l'app lo allega alla notifica (campo "uri" con tipo audio). */
    private fun lastAudio(extras: android.os.Bundle): android.net.Uri? = runCatching {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return@runCatching null
        messages.mapNotNull { it as? android.os.Bundle }.lastOrNull { it.getString("type")?.startsWith("audio/") == true }?.let {
            @Suppress("DEPRECATION")
            it.getParcelable<android.net.Uri>("uri")
        }
    }.getOrNull()

    private val ServiceCategories = setOf(
        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_STATUS,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_RECOMMENDATION,
    )

    /** I messaggi di una notifica in stile conversazione, con il mittente quando c'è. */
    private fun messageHistory(extras: android.os.Bundle): List<Pair<String?, String>> = runCatching {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return@runCatching emptyList()
        messages.mapNotNull { it as? android.os.Bundle }.mapNotNull { b ->
            val text = b.getCharSequence("text")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            @Suppress("DEPRECATION")
            val person = if (Build.VERSION.SDK_INT >= 28) (b.getParcelable("sender_person") as? android.app.Person)?.name?.toString() else null
            (person ?: b.getCharSequence("sender")?.toString()) to text
        }
    }.getOrDefault(emptyList())

    /** Ultimo messaggio di una notifica in stile conversazione (WhatsApp, Signal…), se il testo semplice manca. */
    private fun lastMessageText(extras: android.os.Bundle): String = runCatching {
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return@runCatching ""
        val last = messages.lastOrNull() as? android.os.Bundle ?: return@runCatching ""
        last.getCharSequence("text")?.toString().orEmpty()
    }.getOrDefault("")

    private fun labelOf(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Riceve le notifiche per conto del launcher. Attivo solo con il permesso concesso dall'utente. */
class EuriseNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationStore.setConnected(true)
        NotificationStore.update(this)
    }

    override fun onListenerDisconnected() {
        NotificationStore.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = NotificationStore.update(this)

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = NotificationStore.update(this)
}
