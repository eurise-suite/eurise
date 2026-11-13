package eu.eurise.core.suite

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Come esce la suite in rete, per il semaforo: [bonus] tacchette in più ai servizi che vedono solo il tuo indirizzo di
 * rete. Una VPN lo nasconde ai servizi ma lo vede chi la gestisce (+1); «Nascondi l'indirizzo IP» di Advanced Privacy
 * (/e/OS) passa da Tor, dove nessuno vede insieme chi sei e dove vai (+2).
 */
enum class NetworkShield(val bonus: Int) {
    None(0), Vpn(1), Tor(2);

    companion object {
        const val ADVANCED_PRIVACY = "foundation.e.advancedprivacy"
        private const val PREFS = "privacy_center"
        private const val KEY_KIND = "vpn_kind"

        /**
         * La protezione di adesso. Vale per questa app: se in Advanced Privacy la suite è tra le app con l'indirizzo vero,
         * qui non c'è VPN. Advanced Privacy compare ad Android come una VPN: se è installata e nessun'altra app offre una
         * VPN, la VPN è la sua (Tor); altrimenti decide la scelta fatta nel centro privacy, e di base si conta VPN.
         */
        fun current(context: Context): NetworkShield {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return None
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return None
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return None
            return when (prefs(context).getString(KEY_KIND, null)) {
                "tor" -> Tor
                "vpn" -> Vpn
                else -> if (hasAdvancedPrivacy(context) && !otherVpnApps(context)) Tor else Vpn
            }
        }

        fun hasAdvancedPrivacy(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(ADVANCED_PRIVACY, 0); true }.getOrDefault(false)

        /** La persona dice di che VPN si tratta (null: lo capisce la suite). */
        fun choose(context: Context, kind: NetworkShield?) {
            prefs(context).edit().apply { if (kind == null) remove(KEY_KIND) else putString(KEY_KIND, if (kind == Tor) "tor" else "vpn") }.apply()
        }

        private fun otherVpnApps(context: Context): Boolean = runCatching {
            context.packageManager.queryIntentServices(Intent("android.net.VpnService"), 0)
                .any { it.serviceInfo.packageName != ADVANCED_PRIVACY && it.serviceInfo.packageName != context.packageName }
        }.getOrDefault(true)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
