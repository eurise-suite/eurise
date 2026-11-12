package eu.eurise.feature.launcher.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager

/** Le app predefinite per chiamare, scrivere SMS e mandare email: per mostrarne l'icona (a tema) nelle tendine. */
object DefaultApps {
    fun packageFor(context: Context, kind: ContactAction.Kind): String? = runCatching {
        when (kind) {
            ContactAction.Kind.Call -> context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            ContactAction.Kind.Sms -> Telephony.Sms.getDefaultSmsPackage(context)
            ContactAction.Kind.Email -> context.packageManager
                .resolveActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)?.activityInfo?.packageName
                ?.takeIf { it != "android" }
            ContactAction.Kind.Chat -> null
        }
    }.getOrNull()
}
