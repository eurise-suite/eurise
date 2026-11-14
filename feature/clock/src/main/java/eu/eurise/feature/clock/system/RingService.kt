package eu.eurise.feature.clock.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import eu.eurise.feature.clock.R
import eu.eurise.feature.clock.data.ClockStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Che cosa sta suonando, per lo schermo della sveglia. [alfred] c'è quando sveglia Alfred (tavola AlfSveglia). */
data class Ringing(
    val alarmId: Int?, val timerId: Int?, val title: String, val snoozesLeft: Int, val challenge: Boolean, val routine: Boolean,
    val alfred: AlfredScreen? = null,
    val snoozeMinutes: Int = 10,
)

/** Ciò che lo schermo della sveglia di Alfred mostra: le sue parole e dove suona il brano (null: la suoneria). */
data class AlfredScreen(val wake: eu.eurise.core.data.alfred.AlfredWakeUp, val music: String?, val madam: Boolean?)

/**
 * Fa suonare una sveglia o un timer: suono che cresce piano, vibrazione, schermo intero sopra il blocco.
 * Vive solo mentre suona (servizio in primo piano); dopo dieci minuti senza risposta la sveglia si rimanda da sola.
 */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_ALARM -> startAlarm(intent.getIntExtra(EXTRA_ID, -1), intent.getIntExtra(EXTRA_SNOOZES, 0))
            START_TIMER -> startTimer(intent.getIntExtra(EXTRA_ID, -1), chime = false)
            CHIME -> startTimer(intent.getIntExtra(EXTRA_ID, -1), chime = true)
            SNOOZE -> snoozeNow()
            DISMISS -> dismissNow()
            else -> stopAll()
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(id: Int, snoozes: Int) {
        val alarm = ClockStore.get(this).alarm(id) ?: return stopAll()
        val title = alarm.label.ifBlank { getString(R.string.alarm_default) }
        val left = (alarm.maxSnoozes - snoozes).coerceAtLeast(0)
        _state.value = Ringing(id, null, title, left, alarm.challenge, alarm.routine)
        snoozeCount = snoozes
        foreground(Notifications.ring(this, title, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), left > 0, !alarm.challenge, null))
        handler.postDelayed({ if (left > 0) snoozeNow() else dismissNow() }, 10 * 60_000L)
        if (alarm.alfred) { startAlfred(alarm, title, left); return }
        play(alarm.sound?.let(Uri::parse), alarm.rampSeconds, alarm.vibrate)
    }

    private var alfred: AlfredRing? = null

    /**
     * Sveglia di Alfred: le sue parole si preparano fuori dal thread principale (meteo dal disco), poi parla e parte
     * il brano. La suoneria resta la rete di sicurezza ([AlfredRing]); la vibrazione parte con la suoneria.
     */
    private fun startAlfred(alarm: eu.eurise.feature.clock.logic.Alarm, title: String, left: Int) {
        val ring = AlfredRing(
            this,
            fallback = { handler.post { if (player == null) play(alarm.sound?.let(Uri::parse), alarm.rampSeconds, alarm.vibrate) } },
            playLocal = { uri -> handler.post { play(uri, alarm.rampSeconds, vibrate = false) } },
        )
        alfred = ring
        Thread {
            val wake = runCatching { ring.compose() }.getOrNull()
            val music = runCatching { ring.musicLabel() }.getOrNull()
            val address = runCatching { eu.eurise.core.data.alfred.AlfredPrefs(this).address }.getOrNull()
            val madam = when (address) {
                eu.eurise.core.data.alfred.Address.Madam -> true
                eu.eurise.core.data.alfred.Address.Sir -> false
                else -> null
            }
            handler.post {
                if (alfred !== ring) return@post
                if (wake == null) { play(alarm.sound?.let(Uri::parse), alarm.rampSeconds, alarm.vibrate); return@post }
                _state.value = Ringing(alarm.id, null, title, left, alarm.challenge, routine = false, alfred = AlfredScreen(wake, music, madam), snoozeMinutes = alarm.snoozeMinutes)
                ring.start(wake)
            }
        }.start()
    }

    private fun startTimer(id: Int, chime: Boolean) {
        val t = ClockStore.get(this).timer(id)
        val title = t?.label?.ifBlank { null } ?: getString(R.string.timer_default)
        if (chime) {
            // Cambio di fase del Pomodoro: un suono breve, poi si continua.
            if (_state.value != null) return
            foreground(Notifications.ring(this, title, getString(if (t?.isWork == true) R.string.pomodoro_back_to_work else R.string.pomodoro_break), false, false, id))
            play(null, 0, vibrate = true)
            handler.postDelayed({ stopAll() }, 4_000)
            return
        }
        _state.value = Ringing(null, id, title, 0, challenge = false, routine = false)
        foreground(Notifications.ring(this, title, getString(R.string.timer_done), false, true, id))
        play(null, 0, vibrate = true)
        handler.postDelayed({ stopAll() }, 5 * 60_000L)
    }

    private var snoozeCount = 0

    private fun snoozeNow() {
        val r = _state.value
        if (r?.alfred != null) alfred?.pauseMusic()
        val id = r?.alarmId
        if (id != null) {
            val alarm = ClockStore.get(this).alarm(id)
            Scheduler.snooze(this, id, alarm?.snoozeMinutes ?: 10, snoozeCount + 1)
        }
        stopAll()
    }

    private fun dismissNow() {
        val routine = _state.value?.routine == true
        stopAll()
        if (routine) Routine.run(applicationContext)
    }

    private fun foreground(n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notifications.ID_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(Notifications.ID_RING, n)
        if (wake == null) {
            wake = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eurise:ring")?.apply { acquire(11 * 60_000L) }
        }
        runCatching { startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Suono della sveglia (o quello predefinito) sul canale sveglia, che cresce in [rampSeconds] secondi. */
    private fun play(uri: Uri?, rampSeconds: Int, vibrate: Boolean) {
        player?.release()
        val source = uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@RingService, source)
                isLooping = true
                prepare()
                setVolume(if (rampSeconds > 0) 0.05f else 1f, if (rampSeconds > 0) 0.05f else 1f)
                start()
            }
        }.getOrNull()
        if (rampSeconds > 0) {
            val steps = 20
            for (i in 1..steps) handler.postDelayed({ val v = 0.05f + 0.95f * i / steps; runCatching { player?.setVolume(v, v) } }, rampSeconds * 1000L * i / steps)
        }
        if (vibrate) {
            val v = getSystemService(Vibrator::class.java)
            runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()) }
        }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        // Il brano nell'app di musica continua dopo «Sono sveglia»: è il punto della sveglia di Alfred.
        alfred?.stop(); alfred = null
        runCatching { player?.stop() }
        player?.release(); player = null
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
        wake?.let { if (it.isHeld) it.release() }; wake = null
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        alfred?.stop(); alfred = null
        player?.release()
        _state.value = null
        super.onDestroy()
    }

    companion object {
        private const val START_ALARM = "start_alarm"
        private const val START_TIMER = "start_timer"
        private const val CHIME = "chime"
        private const val SNOOZE = "snooze"
        private const val DISMISS = "dismiss"
        private const val STOP = "stop"
        private const val EXTRA_ID = "id"
        private const val EXTRA_SNOOZES = "snoozes"

        private val _state = MutableStateFlow<Ringing?>(null)
        val state: StateFlow<Ringing?> = _state

        private fun send(context: Context, action: String, id: Int = -1, snoozes: Int = 0) {
            val i = Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_SNOOZES, snoozes)
            runCatching {
                if (action == START_ALARM || action == START_TIMER || action == CHIME) context.startForegroundService(i) else context.startService(i)
            }
        }

        fun startAlarm(context: Context, id: Int, snoozes: Int) = send(context, START_ALARM, id, snoozes)
        fun startTimer(context: Context, id: Int) = send(context, START_TIMER, id)
        fun chime(context: Context, id: Int) = send(context, CHIME, id)
        fun snooze(context: Context) = send(context, SNOOZE)
        fun dismiss(context: Context) = send(context, DISMISS)
        fun stop(context: Context) { if (_state.value != null) send(context, STOP) }
    }
}
