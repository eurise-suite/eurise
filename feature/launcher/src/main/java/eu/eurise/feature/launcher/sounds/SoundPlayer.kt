package eu.eurise.feature.launcher.sounds

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.LocalTime

/**
 * Suona un suono di Eurise. Rispetta sempre il telefono: in silenzioso non suona, in vibrazione vibra soltanto; con
 * Non disturbare tace, tranne per le persone a cui l'utente l'ha permesso.
 */
object SoundPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var current: MediaPlayer? = null
    private val stop = Runnable { release() }

    /** Prova di un suono nelle schermate di Suoni: sempre udibile, sul volume dei contenuti. */
    fun preview(context: Context, id: String, onEnd: () -> Unit = {}) {
        play(context, id, AudioAttributes.USAGE_MEDIA, maxMs = 30_000, onEnd = onEnd)
    }

    fun stopPreview() = main.post { release() }

    /** Per una notifica. [dnd] vero se il telefono è in Non disturbare. */
    fun alert(context: Context, id: String, breakDnd: Boolean, dnd: Boolean) {
        if (dnd) {
            // Non disturbare: tace, tranne per chi l'utente ha scelto («suona anche col Non disturbare»).
            if (breakDnd) play(context, id, AudioAttributes.USAGE_ALARM, maxMs = 8_000)
            return
        }
        when (context.getSystemService(AudioManager::class.java).ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> Unit
            AudioManager.RINGER_MODE_VIBRATE -> vibrate(context)
            else -> play(context, id, AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT, maxMs = 8_000)
        }
    }

    private fun vibrate(context: Context) = runCatching {
        val v = context.getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120), -1))
    }

    private fun play(context: Context, id: String, usage: Int, maxMs: Long, onEnd: () -> Unit = {}) {
        val file = SoundLibrary.file(context, id) ?: return
        main.post {
            release()
            val mp = MediaPlayer()
            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(usage)
                        .setContentType(if (usage == AudioAttributes.USAGE_MEDIA) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { release(); onEnd() }
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
                current = mp
                main.postDelayed(stop, maxMs)
            }.onFailure { mp.release(); onEnd() }
        }
    }

    private fun release() {
        main.removeCallbacks(stop)
        current?.let { runCatching { it.stop() }; it.release() }
        current = null
    }
}

/**
 * Dall'ascolto delle notifiche: per le app che non sono di Eurise (o i cui suoni si scelgono qui) decide il suono con
 * [SoundDecider] e lo fa sentire. Suona solo se l'app ha tolto il suono dal suo canale: altrimenti si sentirebbero due
 * suoni, e l'app viene segnata come «suona ancora da sola» per la schermata Chi suona come.
 */
object SoundRouter {
    private val lastPlayed = HashMap<String, Long>()

    fun onPosted(service: NotificationListenerService, sbn: StatusBarNotification) {
        val settings = SoundStore.get(service)
        if (settings.isEmpty) return
        val n = sbn.notification
        if (sbn.packageName == service.packageName || sbn.isOngoing) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val cat = n.category
        if (cat == Notification.CATEGORY_CALL) return
        val use = useOf(sbn) ?: return
        val extras = n.extras
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val choice = SoundDecider.pick(settings, sbn.packageName, use, senderOf(extras), isGroup, LocalTime.now().hour)
        if (choice == SoundChoice.Untouched) return

        val ranking = NotificationListenerService.Ranking()
        val ranked = service.currentRanking?.getRanking(sbn.key, ranking) == true
        val channel = if (ranked) ranking.channel else null
        val appSounds = channel != null && channel.sound != null && channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
        SoundStore.noteAppSound(service, sbn.packageName, if (appSounds) channel!!.id else null)
        if (appSounds || choice !is SoundChoice.Play) return
        // Un aggiornamento che l'app vuole silenzioso, o una raffica dello stesso mittente: un suono solo.
        if ((n.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0 && lastPlayed.containsKey(sbn.key)) return
        val now = System.currentTimeMillis()
        if (now - (lastPlayed[sbn.packageName] ?: 0) < 1_500) return
        lastPlayed[sbn.packageName] = now
        lastPlayed[sbn.key] = now
        val dnd = service.currentInterruptionFilter.let { it != NotificationListenerService.INTERRUPTION_FILTER_ALL && it != NotificationListenerService.INTERRUPTION_FILTER_UNKNOWN }
        SoundPlayer.alert(service, choice.sound, choice.breakDnd, dnd)
    }

    private fun useOf(sbn: StatusBarNotification): SoundUse? {
        val n = sbn.notification
        val pkg = sbn.packageName
        return when {
            n.category == Notification.CATEGORY_EMAIL || listOf("mail", "k9", "thunderbird", "faircode.email", "tutanota", "tutao", "outlook").any { it in pkg } -> SoundUse.Email
            n.category == Notification.CATEGORY_MESSAGE || n.extras.containsKey(Notification.EXTRA_MESSAGES) -> SoundUse.Messages
            n.category == Notification.CATEGORY_MISSED_CALL -> null
            else -> SoundUse.Other
        }
    }

    private fun senderOf(extras: Bundle): Sender {
        val names = ArrayList<String>()
        val phones = ArrayList<String>()
        val emails = ArrayList<String>()
        fun uri(u: String?) {
            when {
                u == null -> Unit
                u.startsWith("tel:") -> phones += u.removePrefix("tel:")
                u.startsWith("mailto:") -> emails += u.removePrefix("mailto:")
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            val last = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
            last?.getCharSequence("sender")?.toString()?.let(names::add)
            if (Build.VERSION.SDK_INT >= 28) {
                @Suppress("DEPRECATION")
                (last?.getParcelable("sender_person") as? Person)?.let { p -> p.name?.toString()?.let(names::add); uri(p.uri) }
            }
        }
        if (Build.VERSION.SDK_INT >= 28) runCatching {
            @Suppress("DEPRECATION")
            extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)?.forEach { p -> uri(p.uri) }
        }
        runCatching { @Suppress("DEPRECATION") extras.getStringArray(Notification.EXTRA_PEOPLE)?.forEach(::uri) }
        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let { t ->
            names += t
            // «Anna Bianchi <anna@…>» o un indirizzo come titolo, nelle app di posta.
            Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+").find(t)?.let { emails += it.value }
            if (t.count(Char::isDigit) >= 6 && t.none(Char::isLetter)) phones += t
        }
        return Sender(names, phones, emails)
    }
}
