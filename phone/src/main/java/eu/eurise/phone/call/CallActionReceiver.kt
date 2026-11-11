package eu.eurise.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** I pulsanti della notifica della chiamata che non aprono la schermata: rifiuta e chiudi. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val item = CallManager.primary() ?: return
        when (intent.action) {
            ACTION_DECLINE -> CallManager.reject(item.call)
            ACTION_HANGUP -> CallManager.hangup(item.call)
        }
    }

    companion object {
        const val ACTION_DECLINE = "eu.eurise.phone.DECLINE"
        const val ACTION_HANGUP = "eu.eurise.phone.HANGUP"
    }
}
