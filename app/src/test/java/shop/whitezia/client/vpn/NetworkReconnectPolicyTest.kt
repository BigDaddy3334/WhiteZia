package shop.whitezia.client.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkReconnectPolicyTest {
    @Test fun `initial network validation is retained until coverage loss`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103", validated = true) }
        policy.capabilitiesChanged("mobile-103", false)
        assertTrue(policy.shouldReconnect("mobile-103"))
        assertFalse(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `validation from the old network is not inherited by a new network`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103", validated = true) }
        assertTrue(policy.shouldReconnect("wifi-105"))
        policy.capabilitiesChanged("wifi-105", false)
        assertFalse(policy.shouldReconnect("wifi-105"))
    }

    @Test fun `coverage loss with the same network id restarts VPN`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        policy.capabilitiesChanged("mobile-103", true)
        policy.capabilitiesChanged("mobile-103", false)
        policy.capabilitiesChanged("mobile-103", true)
        assertTrue(policy.shouldReconnect("mobile-103"))
        assertFalse(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `a suspended network is not used until it recovers`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        repeat(5) { assertFalse(policy.shouldReconnect("mobile-103", usable = false)) }
        assertTrue(policy.shouldReconnect("mobile-103", usable = true))
        assertFalse(policy.shouldReconnect("mobile-103", usable = true))
    }

    @Test fun `initial unvalidated capabilities do not cause restart loops`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        repeat(5) {
            policy.capabilitiesChanged("mobile-103", false)
            assertFalse(policy.shouldReconnect("mobile-103"))
        }
    }

    @Test fun `validation loss on another network does not restart VPN`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        policy.capabilitiesChanged("wifi-105", true)
        policy.capabilitiesChanged("wifi-105", false)
        assertFalse(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `recovery retry delays are bounded and reset after success`() {
        val backoff = NetworkReconnectBackoff()
        org.junit.Assert.assertEquals(
            listOf(3_000L, 6_000L, 12_000L, 24_000L, 30_000L, 30_000L),
            List(6) { backoff.afterFailureMillis() },
        )
        backoff.reset()
        org.junit.Assert.assertEquals(3_000L, backoff.afterFailureMillis())
    }

    @Test fun `initial callbacks and repeated capabilities do not restart VPN`() {
        val policy = NetworkReconnectPolicy().apply { reset("wifi-101") }
        repeat(5) { assertFalse(policy.shouldReconnect("wifi-101")) }
    }

    @Test fun `changing network of the same transport restarts once`() {
        val policy = NetworkReconnectPolicy().apply { reset("wifi-101") }
        assertTrue(policy.shouldReconnect("wifi-102"))
        assertFalse(policy.shouldReconnect("wifi-102"))
        assertTrue(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `returning to the same network after loss restarts VPN`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        assertFalse(policy.shouldReconnect(null))
        assertFalse(policy.shouldReconnect(null))
        assertTrue(policy.shouldReconnect("mobile-103"))
        assertFalse(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `loss before debounce still restarts the recovered network`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        policy.networkLost("wifi-104")
        assertFalse(policy.shouldReconnect("mobile-103"))
        policy.networkLost("mobile-103")
        assertTrue(policy.shouldReconnect("mobile-103"))
    }

    @Test fun `new connection clears network loss from the previous session`() {
        val policy = NetworkReconnectPolicy().apply { reset("mobile-103") }
        policy.shouldReconnect(null)
        policy.reset("wifi-105")
        assertFalse(policy.shouldReconnect("wifi-105"))
    }
}
