package eu.eurise.postazione.data

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Un messaggio, una mail o una chiamata persa ancora tra le notifiche: [apri] è ciò che farebbe il tocco sulla notifica. */
class Avviso(val chiave: String, val pacchetto: String, val titolo: String, val testo: String, val quando: Long, val apri: PendingIntent?)

/** Quello che sta suonando (o è in pausa) in un'app di musica o video. */
data class Brano(val titolo: String, val artista: String, val pacchetto: String, val suona: Boolean)

/**
 * «Da rispondere» e la musica che suona: servono l'accesso alle notifiche di Android, **spento di base**. Si accende
 * dalla Postazione dopo la spiegazione, si spegne da lì o dalle impostazioni di Android. Di ogni notifica si guardano
 * solo messaggi, posta e chiamate perse, e si tengono in memoria titolo, testo e ora finché la notifica c'è: niente
 * viene scritto su disco e la Postazione non ha il permesso di rete.
 */
class AscoltoService : NotificationListenerService() {
    override fun onListenerConnected() = aggiorna()

    override fun onListenerDisconnected() { avvisi.value = emptyList() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = aggiorna()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = aggiorna()

    private fun aggiorna() {
        val attive = runCatching { activeNotifications }.getOrNull() ?: return
        avvisi.value = attive.mapNotNull { leggi(it) }.sortedByDescending { it.quando }.take(MAX)
    }

    private fun leggi(sbn: StatusBarNotification): Avviso? {
        val n = sbn.notification ?: return null
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val categoria = n.category ?: return null
        if (categoria !in CATEGORIE) return null
        val titolo = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val testo = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (titolo.isBlank() && testo.isBlank()) return null
        return Avviso(sbn.key, sbn.packageName, titolo, testo, sbn.postTime, n.contentIntent)
    }

    companion object {
        private const val MAX = 6
        private val CATEGORIE = setOf(Notification.CATEGORY_MESSAGE, Notification.CATEGORY_EMAIL, Notification.CATEGORY_CALL, "missed_call")

        /** Solo in memoria: si svuota quando l'accesso si spegne o l'app si chiude. */
        val avvisi = MutableStateFlow<List<Avviso>>(emptyList())
    }
}

object Ascolto {
    fun acceso(context: Context): Boolean =
        runCatching { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }.getOrDefault(false)

    /** La pagina di Android dove l'accesso si accende e si spegne. */
    fun impostazioni(context: Context) {
        val nome = ComponentName(context, AscoltoService::class.java).flattenToString()
        val preciso = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, nome).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(preciso) }.isFailure) {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Come toccare la notifica. */
    fun apri(context: Context, avviso: Avviso): Boolean {
        val pi = avviso.apri ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val opzioni = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
                pi.send(context, 0, null, null, null, null, opzioni)
            } else {
                pi.send()
            }
        }.isSuccess
    }
}

object Musica {
    enum class Comando { SuonaPausa, Avanti, Indietro }

    private fun controllo(context: Context): MediaController? {
        val gestore = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessioni = runCatching { gestore.getActiveSessions(ComponentName(context, AscoltoService::class.java)) }.getOrNull().orEmpty()
        return sessioni.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessioni.firstOrNull { it.metadata != null }
    }

    fun brano(context: Context): Brano? {
        val c = controllo(context) ?: return null
        val m = c.metadata ?: return null
        val titolo = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artista = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        return Brano(titolo, artista, c.packageName, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    fun comando(context: Context, comando: Comando) {
        val c = controllo(context) ?: return
        val t = c.transportControls
        when (comando) {
            Comando.SuonaPausa -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            Comando.Avanti -> t.skipToNext()
            Comando.Indietro -> t.skipToPrevious()
        }
    }
}
