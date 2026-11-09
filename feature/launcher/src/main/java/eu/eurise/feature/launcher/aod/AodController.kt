package eu.eurise.feature.launcher.aod

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.LocalTime

/** Decide se accendere il display a riposo e lo fa, dopo aver controllato che il telefono non sia in tasca. */
object AodController {
    /** L'Activity si trova nel modulo app, che conosce i dati della suite. */
    private const val ACTIVITY = "eu.eurise.hub.AodActivity"
    const val EXTRA_TRIGGER = "eu.eurise.extra.AOD_TRIGGER"

    @Volatile var showing: Boolean = false

    /** Con il triplo tocco resta spento fino al prossimo sblocco. */
    @Volatile var snoozed: Boolean = false

    private val main = Handler(Looper.getMainLooper())

    fun request(context: Context, trigger: AodTrigger) {
        val app = context.applicationContext
        val config = AodStore(app).load()
        if (snoozed || !Settings.canDrawOverlays(app)) return
        val battery = app.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (!shouldShowAod(config, trigger, LocalTime.now().toSecondOfDay() / 60, level, battery.isCharging)) return
        whenNotCovered(app) { show(app, trigger) }
    }

    private fun show(app: Context, trigger: AodTrigger) {
        val power = app.getSystemService(PowerManager::class.java)
        // Riaccende lo schermo: l'Activity già aperta non lo farebbe da sola.
        @Suppress("DEPRECATION")
        power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "eurise:aod").acquire(1_000L)
        val intent = Intent().setClassName(app.packageName, ACTIVITY)
            .putExtra(EXTRA_TRIGGER, trigger.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { app.startActivity(intent) }
    }

    /** Una sola lettura del sensore di prossimità: al massimo 300 ms, poi si procede comunque. */
    private fun whenNotCovered(app: Context, proceed: () -> Unit) {
        val sensors = app.getSystemService(SensorManager::class.java)
        val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return proceed()
        var done = false
        lateinit var listener: SensorEventListener
        val finish = { covered: Boolean ->
            if (!done) {
                done = true
                sensors.unregisterListener(listener)
                if (!covered) proceed()
            }
        }
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = finish(event.values[0] < proximity.maximumRange)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_FASTEST, main)
        main.postDelayed({ finish(false) }, 300)
    }

    /** Arriva dal servizio delle notifiche: conta solo ciò che farebbe suonare o illuminare il telefono. */
    fun onNotification(service: NotificationListenerService, sbn: StatusBarNotification) {
        val notification = sbn.notification
        val skip = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_ONLY_ALERT_ONCE or Notification.FLAG_GROUP_SUMMARY
        if (sbn.packageName == service.packageName || notification.flags and skip != 0) return
        val ranking = NotificationListenerService.Ranking()
        // Rispetta "Non disturbare" e le notifiche silenziose.
        if (service.currentRanking?.getRanking(sbn.key, ranking) == true &&
            (!ranking.matchesInterruptionFilter() || ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT)
        ) return
        val power = service.getSystemService(PowerManager::class.java)
        if (!power.isInteractive || showing) request(service, AodTrigger.Notification)
    }
}
