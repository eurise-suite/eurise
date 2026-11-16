package eu.eurise.messages.system

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Un SMS chiesto da un'altra app della suite (Eurise Mappe: «Avvisa: arrivo alle…» e l'arrivo condiviso, tavole
 * MapBollaRitardo e MapBollaArrivo). Arriva solo da app firmate con la stessa chiave (permesso della suite nel
 * manifesto). Extra: `to` (numero, può mancare), `name` (per cercare il numero in Rubrica), `text`.
 *
 * Risponde con `RESULT_OK` e il numero, oppure `RESULT_CANCELED` e il motivo: «no_number», «no_permission», «failed».
 * Se Messaggi è l'app predefinita il messaggio si salva nella conversazione come gli altri; se no parte con il permesso
 * degli SMS e lo salva il sistema.
 */
class SuiteSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val text = intent.getStringExtra("text")?.trim().orEmpty()
        val name = intent.getStringExtra("name")?.trim().orEmpty()
        val given = intent.getStringExtra("to")?.trim().orEmpty()
        if (text.isEmpty()) {
            setResult(Activity.RESULT_CANCELED, "failed", null)
            return
        }
        val pending = goAsync()
        receiverScope.launch {
            try {
                val number = given.ifEmpty { lookup(context, name) }
                when {
                    number.isNullOrEmpty() -> pending.setResult(Activity.RESULT_CANCELED, "no_number", null)
                    !send(context, number, text) -> pending.setResult(Activity.RESULT_CANCELED, "no_permission", null)
                    else -> pending.setResult(Activity.RESULT_OK, number, null)
                }
            } catch (e: RuntimeException) {
                pending.setResult(Activity.RESULT_CANCELED, "failed", null)
            } finally {
                pending.finish()
            }
        }
    }

    private fun send(context: Context, number: String, text: String): Boolean {
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
            Sender.sendSms(context, listOf(number), text)
            return true
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        @Suppress("DEPRECATION")
        val sms = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(SubscriptionManager.getDefaultSmsSubscriptionId())
        } else SmsManager.getDefault()
        val parts = sms.divideMessage(text)
        if (parts.size > 1) sms.sendMultipartTextMessage(number, null, parts, null, null)
        else sms.sendTextMessage(number, null, text, null, null)
        return true
    }

    /** Il numero di [name] in Rubrica: nome uguale, poi che comincia così; il cellulare prima degli altri. */
    private fun lookup(context: Context, name: String): String? {
        if (name.isEmpty()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
        )
        for (selection in listOf("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?")) {
            val arg = if (selection.endsWith("= ?")) name else "$name%"
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, arrayOf(arg), null)?.use { c ->
                var best: String? = null
                while (c.moveToNext()) {
                    val number = c.getString(1)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    if (c.getInt(2) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) return number
                    if (best == null) best = number
                }
                if (best != null) return best
            }
        }
        return null
    }

    companion object {
        const val ACTION = "eu.eurise.action.SEND_SMS"
    }
}
