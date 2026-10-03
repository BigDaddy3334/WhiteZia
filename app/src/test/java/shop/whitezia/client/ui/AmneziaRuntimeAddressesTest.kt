package shop.whitezia.client.ui

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaRuntimeAddressesTest {
    @Test fun includesPrimaryStandbyAndCurrentConfiguredAddresses() {
        val addresses = configuredAmneziaVpnAddresses(listOf(
            config("10.8.0.2/32"),
            config("10.9.0.2/32"),
            config("10.10.0.2/32"),
        ))
        assertEquals(setOf(ip("10.8.0.2"), ip("10.9.0.2"), ip("10.10.0.2")), addresses)
        assertFalse(ip("192.168.77.2") in addresses)
    }

    @Test fun matchesAllInterfaceAddressesIncludingNormalizedIpv6() {
        val addresses = configuredAmneziaVpnAddresses(listOf(config("10.8.0.2/32, fd00:1::2/128")))
        assertEquals(setOf(ip("10.8.0.2"), ip("fd00:0001:0000:0000:0000:0000:0000:0002")), addresses)
    }

    @Test fun invalidCandidatesDoNotHideHealthyStandbyAndDuplicatesAreCollapsed() {
        val valid = config("10.9.0.2/32")
        assertEquals(setOf(ip("10.9.0.2")), configuredAmneziaVpnAddresses(listOf("", "invalid", valid, valid)))
        assertTrue(configuredAmneziaVpnAddresses(listOf("", "invalid")).isEmpty())
    }

    @Test fun dnsServersAndPeerRoutesAreNotLocalInterfaceAddresses() {
        val configText = config("10.9.0.2/32") + "\n" + """

            DNS = 1.1.1.1
            [Peer]
            PublicKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            AllowedIPs = 10.99.0.0/16
        """.trimIndent()
        assertEquals(setOf(ip("10.9.0.2")), configuredAmneziaVpnAddresses(listOf(configText)))
    }

    private fun ip(address: String): InetAddress = InetAddress.getByName(address)

    private fun config(addresses: String): String = """
        [Interface]
        PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        Address = $addresses
    """.trimIndent()
}
