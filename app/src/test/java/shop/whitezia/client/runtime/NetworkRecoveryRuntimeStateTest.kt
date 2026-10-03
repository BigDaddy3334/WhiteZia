package shop.whitezia.client.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRecoveryRuntimeStateTest {
    private val recovery = WhiteZiaRuntimeState(
        sessionId = "session", mode = "vpn", status = "starting", connectionProfileId = "profile",
        listenIp = "127.0.0.1", listenPort = 10886, updatedAtMillis = 100_000L,
        message = "Reconnecting Xray VPN: waiting for network recovery",
    )

    @Test fun freshHeartbeatKeepsWaitingConnectionAlive() {
        assertTrue(recovery.isLiveNetworkRecovery(105_000L))
        assertTrue(recovery.isLiveNetworkRecovery(115_000L))
    }

    @Test fun abandonedRecoveryDoesNotRestoreAnInfiniteSpinner() {
        assertFalse(recovery.isLiveNetworkRecovery(115_001L))
        assertFalse(recovery.isLiveNetworkRecovery(99_999L))
    }

    @Test fun onlyExplicitVpnRecoveryIsAccepted() {
        assertFalse(recovery.copy(message = "Starting Xray VPN").isLiveNetworkRecovery(105_000L))
        assertFalse(recovery.copy(status = "stopped").isLiveNetworkRecovery(105_000L))
        assertFalse(recovery.copy(mode = "proxy").isLiveNetworkRecovery(105_000L))
        assertFalse(recovery.copy(sessionId = "").isLiveNetworkRecovery(105_000L))
    }

    @Test fun typedRecoveryDoesNotDependOnMessageLanguage() {
        val typed = recovery.copy(message = "localized recovery", recoveryWaiting = true, schemaVersion = 2)
        assertTrue(typed.isLiveNetworkRecovery(105_000L))
        assertFalse(typed.isLiveNetworkRecovery(115_001L))
        assertFalse(typed.copy(status = "ready").isLiveNetworkRecovery(105_000L))
    }

    @Test fun typedFalseOverridesLegacyLookingMessage() {
        assertFalse(recovery.copy(schemaVersion = 2, recoveryWaiting = false).isLiveNetworkRecovery(105_000L))
    }

    @Test fun initialTypedPreparationIsLiveWithoutRecoveryFlagOrTun() {
        val preparing = recovery.copy(schemaVersion = 2, message = "Preparing", recoveryWaiting = false)
        assertTrue(preparing.isLiveRuntimeStarting(100_000L))
        assertTrue(preparing.isLiveRuntimeStarting(115_000L))
        assertTrue(preparing.copy(mode = "proxy").isLiveRuntimeStarting(105_000L))
        assertFalse(preparing.isLiveRuntimeStarting(115_001L))
        assertFalse(preparing.isLiveRuntimeStarting(99_999L))
        assertFalse(preparing.copy(status = "ready").isLiveRuntimeStarting(105_000L))
        assertFalse(preparing.copy(sessionId = "").isLiveRuntimeStarting(105_000L))
        assertFalse(preparing.copy(mode = "unknown").isLiveRuntimeStarting(105_000L))
        assertFalse(preparing.copy(schemaVersion = 1).isLiveRuntimeStarting(105_000L))
    }
}
