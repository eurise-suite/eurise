package eu.eurise.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** I pulsanti della notifica e della bolla che non aprono la schermata: rifiuta, chiudi, muto. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val item = CallManager.primary() ?: return
        when (intent.action) {
            ACTION_DECLINE -> CallManager.reject(item.call)
            ACTION_HANGUP -> CallManager.hangup(item.call)
            ACTION_MUTE -> CallManager.setMuted(CallManager.audio.value?.isMuted != true)
        }
    }

    companion object {
        const val ACTION_DECLINE = "eu.eurise.phone.DECLINE"
        const val ACTION_HANGUP = "eu.eurise.phone.HANGUP"
        const val ACTION_MUTE = "eu.eurise.phone.MUTE"
    }
}
