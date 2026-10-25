package shop.whitezia.client.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPresentationTest {
    @Test fun stagesDistinguishInternetCheckFromRecoveryAndInitialStart() {
        val checking = connectionStatusMessage(ConnectionStatus.CONNECTING, runtimeProgressState("checking"))
        val recovering = connectionStatusMessage(ConnectionStatus.CONNECTING, runtimeProgressState("reconnecting"))
        val starting = connectionStatusMessage(ConnectionStatus.CONNECTING, runtimeProgressState("starting"))
        assertTrue(checking.contains("Проверяю"))
        assertTrue(checking.contains("прокси"))
        assertTrue(recovering.contains("Восстанавливаю"))
        assertFalse(starting.contains("Восстанавливаю"))
        assertEquals("starting", runtimeProgressState("untrusted-phase").phase)
        val action = runtimeProgressState("waiting-action", "Для StormDNS отключите Wi-Fi")
        assertEquals("Для StormDNS отключите Wi-Fi", connectionStatusMessage(ConnectionStatus.CONNECTING, action))
    }

    @Test fun proxySuccessDoesNotGrantTunCoverage() {
        val state = proxyVerification("SOCKS passed", 42)
        assertEquals(ConnectionVerificationStatus.Verified, state.status)
        assertEquals(ConnectionVerificationCoverage.SocksProxy, state.coverage)
        assertEquals(ConnectionVerificationCoverage.Unknown, ConnectionVerificationState().coverage)
        assertEquals(42L, state.checkedAtMillis)
    }

    @Test fun failurePreservesUsefulReasonButMasksCredentials() {
        assertEquals("Разрешите VPN в системном окне Android", connectionActionMessage("VPN permission is required"))
        assertEquals("VPN отключён в настройках Android. Нажмите «Подключиться» для повторного запуска",
            connectionActionMessage("VPN permission was revoked by Android"))
        val message = connectionActionMessage("TLS timeout token=private-value")
        assertTrue(message.contains("TLS timeout"))
        assertFalse(message.contains("private-value"))
    }
}
