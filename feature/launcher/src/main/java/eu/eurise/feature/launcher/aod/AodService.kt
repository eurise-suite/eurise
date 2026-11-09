package eu.eurise.feature.launcher.aod

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import eu.eurise.feature.launcher.R

/**
 * Eccezione concordata alla regola "niente servizi sempre attivi": esiste solo con il display a riposo acceso,
 * perché Android annuncia lo spegnimento dello schermo solo a chi è in esecuzione.
 */
class AodService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val sensors by lazy { getSystemService(SensorManager::class.java) }

    /** Il gesto di sollevamento del telefono: sensore a basso consumo, presente solo su alcuni modelli. */
    private val lift: Sensor? by lazy { findLiftSensor(sensors) }
    private val liftListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) = AodController.request(this@AodService, AodTrigger.Lift)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    lift?.let { sensors.requestTriggerSensor(liftListener, it) }
                    // Si aspetta che l'Activity precedente abbia finito di chiudersi.
                    handler.postDelayed({ AodController.request(context, AodTrigger.ScreenOff) }, 400)
                }
                Intent.ACTION_SCREEN_ON -> {
                    lift?.let { sensors.cancelTriggerSensor(liftListener, it) }
                    val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    if (locked && !AodController.showing) AodController.request(context, AodTrigger.ScreenOn)
                }
                Intent.ACTION_USER_PRESENT -> AodController.snoozed = false
                Intent.ACTION_POWER_CONNECTED -> if (!isInteractive()) AodController.request(context, AodTrigger.Charger)
            }
        }
    }

    private fun isInteractive() = getSystemService(android.os.PowerManager::class.java).isInteractive

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.aod_title), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_aod_status)
            .setContentTitle(getString(R.string.aod_notification))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID, notification, type)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(receiver)
        lift?.let { sensors.cancelTriggerSensor(liftListener, it) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "aod"
        private const val ID = 7301

        /** Avvia o ferma il servizio secondo le impostazioni: spento il display a riposo, non resta nulla. */
        fun sync(context: Context) {
            val intent = Intent(context, AodService::class.java)
            if (AodStore(context).load().enabled) ContextCompat.startForegroundService(context, intent)
            else context.stopService(intent)
        }

        fun findLiftSensor(sensors: SensorManager): Sensor? =
            sensors.getSensorList(Sensor.TYPE_ALL).firstOrNull {
                it.stringType == "android.sensor.pick_up_gesture" && it.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT
            }
    }
}

/** Dopo il riavvio del telefono il display a riposo riprende, se era acceso. */
class AodBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AodService.sync(context)
    }
}
