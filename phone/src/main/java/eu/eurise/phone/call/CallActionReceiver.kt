package eu.eurise.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * I pulsanti della notifica e della bolla che non aprono la schermata: rifiuta, chiudi, muto, e «Richiama» dalla
 * notifica delle chiamate perse. Il ricevitore non è esportato: solo le nostre notifiche possono chiamare da qui.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CALL_BACK) {
            val number = intent.data?.schemeSpecificPart.orEmpty()
            MissedCalls.seen(context)
            if (number.isNotBlank() && context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                runCatching {
                    context.getSystemService(android.telecom.TelecomManager::class.java)
                        .placeCall(android.net.Uri.fromParts("tel", eu.eurise.core.contacts.Reach.digits(number), null), android.os.Bundle())
                }
            }
            return
        }
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
        const val ACTION_CALL_BACK = "eu.eurise.phone.CALL_BACK"
    }
}
