package eu.eurise.desktop

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeersTest {
    private fun ok(ip: String) = Peers.allowed(InetAddress.getByName(ip))

    @Test
    fun homeNetworksAreAllowed() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "172.31.255.1", "127.0.0.1", "169.254.10.1",
            "100.64.0.1", "100.127.255.254", "fd12:3456::1", "fe80::1", "::1").forEach { assertTrue(it, ok(it)) }
    }

    @Test
    fun internetIsRefused() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "100.63.255.255", "2a00:1450:4002::200e", "93.184.216.34")
            .forEach { assertFalse(it, ok(it)) }
    }

    @Test
    fun phoneNamesLoseControlCharacters() {
        assertEquals("Pixel 8[2J", Peers.clean("Pixel 8\u001b[2J"))
        assertEquals("Fairphone", Peers.clean("Fair‮phone"))
        assertEquals("Telefono", Peers.clean("\n\t"))
        assertEquals(64, Peers.clean("x".repeat(200)).length)
    }
}
