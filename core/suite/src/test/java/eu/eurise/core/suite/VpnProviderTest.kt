package eu.eurise.core.suite

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnProviderTest {
    private fun vpn(id: String) = VpnProvider.byId(id)!!

    @Test fun scores() {
        assertEquals(8, vpn("proton").score)
        assertEquals(9, vpn("mullvad").score)
        assertEquals(5, vpn("nord").score)
    }

    @Test fun bonusFollowsTrust() {
        assertEquals(2, NetworkExit(NetworkShield.Vpn, vpn("proton")).bonus)
        assertEquals(1, NetworkExit(NetworkShield.Vpn, vpn("nord")).bonus)
        assertEquals(0, NetworkExit(NetworkShield.Vpn).bonus)
        assertEquals(2, NetworkExit(NetworkShield.Tor).bonus)
        assertEquals(0, NetworkExit.DIRECT.bonus)
    }
}
