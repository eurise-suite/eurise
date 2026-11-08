package eu.eurise.feature.launcher.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

/**
 * Richiamare o scrivere a chi ha chiamato: prima il "Richiama" della notifica, se c'è; poi il numero (scritto nella
 * notifica o trovato nei contatti, solo se il permesso è già dato), che si apre nel telefono pronto da chiamare.
 */
object CallBack {
    private val Number = Regex("""\+?\d[\d\s./-]{5,}\d""")

    fun number(context: Context, m: MessageItem): String? {
        Number.find(m.title)?.value?.let { return it.filter { c -> c.isDigit() || c == '+' } }
        Number.find(m.text)?.value?.let { return it.filter { c -> c.isDigit() || c == '+' } }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(m.title.trim()), null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun call(context: Context, m: MessageItem) {
        m.callBack?.let { a -> if (runCatching { a.intent.send() }.isSuccess) return }
        val n = number(context, m)
        if (n != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else {
            runCatching { m.contentIntent?.send() }
        }
    }

    fun sms(context: Context, m: MessageItem) {
        val n = number(context, m)
        if (n != null) runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        else runCatching { m.contentIntent?.send() }
    }
}
