package eu.eurise.feature.launcher.notifications

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
)

data class NotificationAction(val title: String, val intent: PendingIntent)

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

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal fun update(service: NotificationListenerService) {
        this.service = java.lang.ref.WeakReference(service)
        val visible = runCatching { service.activeNotifications }.getOrNull().orEmpty()
            .filter { !it.notification.isGroupSummary() && it.packageName != service.packageName }
        _all.value = visible
            .filter { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null }
            .sortedByDescending { it.postTime }
            .take(30)
            .map { it.toMessage(service) }
        val active = visible.filter { it.isClearable }
        _counts.value = active.groupingBy { it.packageName }.eachCount()
        _messages.value = active
            .filter { it.isMessage() }
            .sortedByDescending { it.postTime }
            .take(8)
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

    private fun StatusBarNotification.isMessage(): Boolean {
        val cat = notification.category
        return cat == Notification.CATEGORY_MESSAGE || cat == Notification.CATEGORY_EMAIL ||
            cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_MISSED_CALL
    }

    private fun StatusBarNotification.toMessage(context: Context): MessageItem {
        val extras = notification.extras
        return MessageItem(
            key = key,
            packageName = packageName,
            appLabel = labelOf(context, packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            postedAt = postTime,
            contentIntent = notification.contentIntent,
            actions = notification.actions.orEmpty()
                .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null && !it.title.isNullOrBlank() }
                .take(2)
                .map { NotificationAction(it.title.toString(), it.actionIntent) },
            clearable = isClearable,
            isMessage = isMessage(),
        )
    }

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
