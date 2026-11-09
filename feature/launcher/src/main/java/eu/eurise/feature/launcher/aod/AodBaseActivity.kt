package eu.eurise.feature.launcher.aod

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import eu.eurise.core.data.EuriseData

/**
 * La schermata del display a riposo, sopra il blocco. Allo scadere del tempo diventa nera e torna ad Android il
 * compito di spegnere lo schermo: un'app normale non può farlo da sé.
 */
abstract class AodBaseActivity : ComponentActivity() {
    protected abstract val data: EuriseData

    private var dark by mutableStateOf(false)
    private var timer by mutableIntStateOf(0)
    private var config by mutableStateOf(AodConfig())
    private var charging by mutableStateOf(false)

    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val proximity: Sensor? by lazy { sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values[0] < (proximity?.maximumRange ?: 0f)) goDark()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this) { goDark() }
        refresh()
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            AodScreen(
                data = data,
                bubbles = visibleBubbles(config, charging),
                reduced = config.mode == AodMode.Always && !charging,
                dark = dark,
                seconds = if (config.staysOn(charging)) null else config.seconds,
                timerKey = timer,
                onTimeout = ::goDark,
                onTap = { timer++ },
                onDoubleTap = ::unlock,
                onTripleTap = { AodController.snoozed = true; goDark() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AodController.showing = true
        proximity?.let { sensors.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(proximityListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        refresh()
        timer++
    }

    override fun onDestroy() {
        AodController.showing = false
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun refresh() {
        config = AodStore(this).load()
        charging = getSystemService(BatteryManager::class.java).isCharging
        dark = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(if (config.mode == AodMode.Always && !charging) 0.03f else 0.35f)
    }

    private fun goDark() {
        dark = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(0.01f)
    }

    private fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) return finishAndRemoveTask()
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishAndRemoveTask()
        })
    }
}
