package eu.eurise.core.suite

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Da dove passa la rete della suite: diretta, una VPN, Tor (Advanced Privacy di /e/OS). */
enum class NetworkShield { None, Vpn, Tor }

/**
 * Una VPN conosciuta. La VPN vede tutti i siti che visiti e il tuo indirizzo: è un servizio da valutare come gli altri.
 * [country]: sigla ISO della sede legale; [verifiedNoLogs]: «niente registri» verificato da terzi; [publicReport]: il
 * rapporto si legge da tutti (non solo dai clienti); [openSourceApps]: app Android a codice aperto; [anonymousAccount]:
 * nessuna email o nome per abbonarsi; [operations]: dove lavora chi ha accesso ai server (se diverso dalla sede legale).
 * Dati verificati il 13 novembre 2026; criteri approvati dal proprietario il 14 novembre.
 */
data class VpnProvider(
    val id: String, val name: String, val packages: List<String>, val country: String,
    val verifiedNoLogs: Boolean, val publicReport: Boolean, val openSourceApps: Boolean, val anonymousAccount: Boolean,
    val operations: String = country,
) {
    /**
     * Conta dove lavorano le persone che hanno accesso ai server, non la sede legale: sono loro che un'autorità può
     * obbligare a collaborare. In Europa (UE, SEE, Svizzera): GDPR o legge equivalente.
     */
    val european: Boolean get() = operations in EUROPE

    /**
     * Il suo semaforo (criteri del 14 novembre). Parte da 5: vede a quali siti vai (non cosa scrivi, che resta cifrato).
     * +1 se l'assenza di registri è verificata da terzi con un rapporto che tutti possono leggere (0 se solo i clienti),
     * +1 app aperte, +1 account senza nome, +1 se chi ha accesso ai server lavora in Europa, −1 altrove.
     * Massimo 9: solo il telefono arriva a 10.
     */
    val score: Int
        get() = (5 + (if (verifiedNoLogs && publicReport) 1 else 0) + (if (openSourceApps) 1 else 0) +
            (if (anonymousAccount) 1 else 0) + (if (european) 1 else -1)).coerceIn(1, 9)

    /** Le tacchette che dà agli altri servizi, che non vedono più il tuo indirizzo: +2 se merita fiducia (7 o più), altrimenti +1. */
    val bonus: Int get() = if (score >= 7) 2 else 1

    companion object {
        private val EUROPE = setOf(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU", "IE", "IT", "LT", "LU",
            "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK", "IS", "LI", "NO", "CH",
        )

        val CATALOG = listOf(
            // Proton AG, Ginevra. Registri verificati ogni anno da Securitum (rapporti pubblici), app aperte, email per l'account.
            VpnProvider("proton", "Proton VPN", listOf("ch.protonvpn.android"), "CH",
                verifiedNoLogs = true, publicReport = true, openSourceApps = true, anonymousAccount = false),
            // Mullvad VPN AB, Göteborg. App aperte e verificate, account con un numero a caso, pagamento anche in contanti.
            VpnProvider("mullvad", "Mullvad", listOf("net.mullvad.mullvadvpn"), "SE",
                verifiedNoLogs = true, publicReport = true, openSourceApps = true, anonymousAccount = true),
            // NordVPN S.A., Panamá; gruppo Nord Security (Paesi Bassi), lavoro a Vilnius. Registri verificati da Deloitte
            // (sesta verifica, fine 2025), ma il rapporto lo leggono solo i clienti; app Android chiusa.
            VpnProvider("nord", "NordVPN", listOf("com.nordvpn.android"), "PA",
                verifiedNoLogs = true, publicReport = false, openSourceApps = false, anonymousAccount = false, operations = "LT"),
        )

        fun byId(id: String?): VpnProvider? = CATALOG.firstOrNull { it.id == id }
    }
}

/**
 * Come esce adesso la suite in rete, per il semaforo. [vpn] è la VPN riconosciuta (null: sconosciuta o nessuna).
 * [bonus]: tacchette in più ai servizi che vedono solo il tuo indirizzo. Tor +2; una VPN conosciuta secondo il suo voto
 * (+2 o +1); una VPN che non conosciamo nessuna, perché non sappiamo cosa fa di ciò che vede.
 */
data class NetworkExit(val kind: NetworkShield, val vpn: VpnProvider? = null) {
    val bonus: Int get() = when (kind) {
        NetworkShield.None -> 0
        NetworkShield.Tor -> 2
        NetworkShield.Vpn -> vpn?.bonus ?: 0
    }

    companion object {
        val DIRECT = NetworkExit(NetworkShield.None)
        const val ADVANCED_PRIVACY = "foundation.e.advancedprivacy"
        private const val PREFS = "privacy_center"
        private const val KEY = "vpn_kind"
        private const val TOR = "tor"
        private const val OTHER = "other"

        /**
         * La rete di adesso, per questa app: se in Advanced Privacy la suite è tra le app con l'indirizzo vero, qui non c'è
         * VPN. Android non dice a chi appartiene una VPN: se sul telefono c'è una sola app che può farla (Advanced Privacy
         * o una del catalogo) è lei; se ce ne sono più, vale la scelta fatta nel centro privacy.
         */
        fun current(context: Context): NetworkExit {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return DIRECT
            val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return DIRECT
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return DIRECT
            val chosen = prefs(context).getString(KEY, null)
            if (chosen != null) return fromChoice(chosen)
            val apps = vpnApps(context)
            if (apps.size != 1) return NetworkExit(NetworkShield.Vpn)
            val only = apps.single()
            return if (only == ADVANCED_PRIVACY) NetworkExit(NetworkShield.Tor)
            else NetworkExit(NetworkShield.Vpn, VpnProvider.CATALOG.firstOrNull { only in it.packages })
        }

        /** Le scelte possibili in questo telefono: Advanced Privacy se c'è, le VPN del catalogo installate, «un'altra». */
        fun choices(context: Context): List<NetworkExit> {
            val apps = vpnApps(context)
            return buildList {
                if (ADVANCED_PRIVACY in apps || hasAdvancedPrivacy(context)) add(NetworkExit(NetworkShield.Tor))
                VpnProvider.CATALOG.filter { p -> p.packages.any { it in apps } }.forEach { add(NetworkExit(NetworkShield.Vpn, it)) }
                add(NetworkExit(NetworkShield.Vpn))
            }
        }

        /** La persona dice quale VPN sta usando (null: lo capisce la suite). */
        fun choose(context: Context, exit: NetworkExit?) {
            val value = when {
                exit == null -> null
                exit.kind == NetworkShield.Tor -> TOR
                else -> exit.vpn?.id ?: OTHER
            }
            prefs(context).edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
        }

        fun hasAdvancedPrivacy(context: Context): Boolean =
            runCatching { context.packageManager.getPackageInfo(ADVANCED_PRIVACY, 0); true }.getOrDefault(false)

        private fun fromChoice(value: String) = when (value) {
            TOR -> NetworkExit(NetworkShield.Tor)
            else -> NetworkExit(NetworkShield.Vpn, VpnProvider.byId(value))
        }

        /** I pacchetti che offrono una VPN (servizio `android.net.VpnService`), tranne la suite stessa. */
        private fun vpnApps(context: Context): Set<String> = runCatching {
            context.packageManager.queryIntentServices(Intent("android.net.VpnService"), 0)
                .map { it.serviceInfo.packageName }.filter { it != context.packageName }.toSet()
        }.getOrDefault(emptySet())

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
