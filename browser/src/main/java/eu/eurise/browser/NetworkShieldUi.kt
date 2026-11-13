package eu.eurise.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.eurise.core.suite.NetworkShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Come esce adesso il Browser in rete (VPN, Advanced Privacy con Tor), per le tacchette in più del semaforo. */
@Composable
internal fun rememberNetworkShield(): NetworkShield {
    val context = LocalContext.current
    val shield by produceState(NetworkShield.None) { value = withContext(Dispatchers.IO) { NetworkShield.current(context) } }
    return shield
}

/** L'etichetta delle tacchette guadagnate: «+1 VPN», «+2 Tor». */
@Composable
internal fun NetworkShield.label(): String? = when (this) {
    NetworkShield.None -> null
    NetworkShield.Vpn -> stringResource(R.string.brw_boost_vpn)
    NetworkShield.Tor -> stringResource(R.string.brw_boost_tor)
}
