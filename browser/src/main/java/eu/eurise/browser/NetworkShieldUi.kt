package eu.eurise.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.eurise.core.suite.NetworkExit
import eu.eurise.core.suite.NetworkShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Come esce adesso il Browser in rete (VPN e quale, Advanced Privacy con Tor), per le tacchette in più del semaforo. */
@Composable
internal fun rememberNetworkShield(): NetworkExit {
    val context = LocalContext.current
    val exit by produceState(NetworkExit.DIRECT) { value = withContext(Dispatchers.IO) { NetworkExit.current(context) } }
    return exit
}

/** L'etichetta delle tacchette guadagnate: «+2 Proton VPN», «+2 Tor». */
@Composable
internal fun NetworkExit.label(): String? = when {
    bonus == 0 -> null
    kind == NetworkShield.Tor -> stringResource(R.string.brw_boost_tor)
    else -> stringResource(R.string.brw_boost_vpn, bonus, vpn?.name ?: "VPN")
}
