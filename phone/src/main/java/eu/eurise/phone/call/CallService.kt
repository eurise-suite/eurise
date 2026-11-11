package eu.eurise.phone.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Il collegamento con il sistema quando Eurise Telefono è l'app per le chiamate: Android passa qui ogni chiamata.
 * Le chiamate vanno a [CallManager]; la notifica (con lo schermo intero per quelle in arrivo) la tiene [CallNotifications].
 * La suoneria la fa il sistema.
 */
class CallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = CallNotifications.update(this@CallService)
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifications.cancel(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        call.registerCallback(callback)
        CallNotifications.update(this)
        // In uscita o con lo schermo acceso: la schermata della chiamata si apre subito; in arrivo a schermo spento la
        // apre la notifica a schermo intero.
        if (CallManager.stateOf(call) != Call.STATE_RINGING || isInteractive()) openCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        CallManager.remove(call)
        CallNotifications.update(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.setAudio(audioState)
    }

    private fun isInteractive() = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive

    private fun openCallScreen() {
        runCatching {
            startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
}
