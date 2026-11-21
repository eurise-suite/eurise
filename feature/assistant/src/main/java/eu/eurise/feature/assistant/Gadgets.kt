package eu.eurise.feature.assistant

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import eu.eurise.core.contacts.Reach
import eu.eurise.core.data.modes.Mode
import eu.eurise.core.data.modes.Modes
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Le impostazioni del telefono che Alfred cambia subito, senza scheda (tavola AlfAzioni, «Telefono» e «Musica»):
 * torcia, Non disturbare, volume, suoneria, modalità della suite, tasti della musica. Niente esce dal telefono;
 * il Non disturbare e il silenzioso vogliono l'accesso «Non disturbare» di Android, chiesto la prima volta.
 */
internal object Gadgets {

    sealed interface Result {
        data class Done(val line: Int, val arg: String? = null) : Result
        /** Serve l'accesso «Non disturbare»: si apre la pagina di Android. */
        data object NeedsQuietAccess : Result
        data object NoTorch : Result
    }

    suspend fun run(context: Context, c: Command.Device, now: LocalDateTime = LocalDateTime.now()): Result = when (c.action) {
        DeviceAction.TorchOn, DeviceAction.TorchOff -> torch(context, c.action == DeviceAction.TorchOn)
        DeviceAction.QuietOn -> quiet(context, c, now)
        DeviceAction.QuietOff -> quietOff(context)
        DeviceAction.VolumeMin, DeviceAction.VolumeMax, DeviceAction.VolumeUp, DeviceAction.VolumeDown, DeviceAction.Mute -> volume(context, c.action)
        DeviceAction.Silent, DeviceAction.Vibrate, DeviceAction.Sound -> ringer(context, c.action)
        DeviceAction.ModeOn -> {
            val mode = when (c.mode) { "lavoro" -> Mode.Work; "casa" -> Mode.Home; "viaggio" -> Mode.Travel; else -> Mode.Night }
            Modes.choose(context, mode)
            Result.Done(R.string.alfred_dev_mode_on, c.mode.orEmpty().replaceFirstChar { it.uppercase() })
        }
        DeviceAction.ModeOff -> { Modes.choose(context, null); Result.Done(R.string.alfred_dev_mode_off) }
        DeviceAction.Play -> media(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        DeviceAction.Pause -> media(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        DeviceAction.Next -> media(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        DeviceAction.Previous -> media(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun torch(context: Context, on: Boolean): Result {
        val cm = context.getSystemService(CameraManager::class.java) ?: return Result.NoTorch
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return Result.NoTorch
        return if (runCatching { cm.setTorchMode(id, on) }.isSuccess) {
            Result.Done(if (on) R.string.alfred_dev_torch_on else R.string.alfred_dev_torch_off)
        } else Result.NoTorch
    }

    private fun canQuiet(context: Context) =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    /** «Non disturbare fino alle 8» o «per un'ora»: lascia passare sveglie e persone importanti, poi si spegne da sé. */
    private fun quiet(context: Context, c: Command.Device, now: LocalDateTime): Result {
        if (!canQuiet(context)) return Result.NeedsQuietAccess
        val nm = context.getSystemService(NotificationManager::class.java)!!
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        val end = c.until?.let { t -> if (t.isAfter(now.toLocalTime())) now.toLocalDate().atTime(t) else now.toLocalDate().plusDays(1).atTime(t) }
            ?: c.minutes?.let { now.plusMinutes(it.toLong()) }
        cancelQuietEnd(context)
        if (end == null) return Result.Done(R.string.alfred_dev_quiet_on)
        val at = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, quietEnd(context))
        return Result.Done(R.string.alfred_dev_quiet_until, end.toLocalTime().let { "%d:%02d".format(it.hour, it.minute) })
    }

    private fun quietOff(context: Context): Result {
        if (!canQuiet(context)) return Result.NeedsQuietAccess
        context.getSystemService(NotificationManager::class.java)?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        cancelQuietEnd(context)
        return Result.Done(R.string.alfred_dev_quiet_off)
    }

    private fun quietEnd(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7301, Intent(context, QuietEndReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelQuietEnd(context: Context) = runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(quietEnd(context)) }

    /** Il volume della musica (quello che si sente): «al minimo» è il primo scatto, non il muto. */
    private fun volume(context: Context, action: DeviceAction): Result {
        val am = context.getSystemService(AudioManager::class.java) ?: return Result.Done(R.string.alfred_failed)
        val s = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(s).coerceAtLeast(1)
        when (action) {
            DeviceAction.VolumeMin -> am.setStreamVolume(s, 1, AudioManager.FLAG_SHOW_UI)
            DeviceAction.VolumeMax -> am.setStreamVolume(s, max, AudioManager.FLAG_SHOW_UI)
            DeviceAction.Mute -> am.setStreamVolume(s, 0, AudioManager.FLAG_SHOW_UI)
            DeviceAction.VolumeUp -> am.setStreamVolume(s, (am.getStreamVolume(s) + (max / 5).coerceAtLeast(1)).coerceAtMost(max), AudioManager.FLAG_SHOW_UI)
            else -> am.setStreamVolume(s, (am.getStreamVolume(s) - (max / 5).coerceAtLeast(1)).coerceAtLeast(0), AudioManager.FLAG_SHOW_UI)
        }
        val pct = am.getStreamVolume(s) * 100 / max
        return Result.Done(if (pct == 0) R.string.alfred_dev_muted else R.string.alfred_dev_volume, "$pct")
    }

    private fun ringer(context: Context, action: DeviceAction): Result {
        val am = context.getSystemService(AudioManager::class.java) ?: return Result.Done(R.string.alfred_failed)
        val mode = when (action) {
            DeviceAction.Silent -> AudioManager.RINGER_MODE_SILENT
            DeviceAction.Vibrate -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        // Entrare o uscire dal silenzioso tocca il Non disturbare: Android lo permette solo con quell'accesso.
        if ((mode == AudioManager.RINGER_MODE_SILENT || am.ringerMode == AudioManager.RINGER_MODE_SILENT) && !canQuiet(context)) {
            return Result.NeedsQuietAccess
        }
        return if (runCatching { am.ringerMode = mode }.isSuccess) Result.Done(when (mode) {
            AudioManager.RINGER_MODE_SILENT -> R.string.alfred_dev_silent
            AudioManager.RINGER_MODE_VIBRATE -> R.string.alfred_dev_vibrate
            else -> R.string.alfred_dev_sound
        }) else Result.NeedsQuietAccess
    }

    /**
     * Un tasto della musica all'app che suona o all'ultima usata. «Metti musica» senza nessuna app pronta apre l'app
     * della musica del telefono.
     */
    private suspend fun media(context: Context, key: Int): Result {
        val am = context.getSystemService(AudioManager::class.java) ?: return Result.Done(R.string.alfred_failed)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0))
        return when (key) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                kotlinx.coroutines.delay(700)
                if (am.isMusicActive) Result.Done(R.string.alfred_dev_play)
                else if (Reach.open(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    Result.Done(R.string.alfred_dev_play_app)
                } else Result.Done(R.string.alfred_dev_play_none)
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> Result.Done(R.string.alfred_dev_pause)
            KeyEvent.KEYCODE_MEDIA_NEXT -> Result.Done(R.string.alfred_dev_next)
            else -> Result.Done(R.string.alfred_dev_previous)
        }
    }

    /** La pagina di Android dove si dà l'accesso «Non disturbare» a Eurise. */
    fun quietAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Alla fine di «Non disturbare fino alle…»: si torna a far passare tutto. */
class QuietEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.isNotificationPolicyAccessGranted) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
    }
}
