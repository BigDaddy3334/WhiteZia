package shop.whitezia.client.account

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRecoveryTest {
    @Test
    fun parsesValidatedRecoveryBundle() {
        val result = parseRecoveryDeviceBundle(
            """{"device_id":"device-1","bundle":"stormbundle://payload","updated_at":"2026-07-22T12:00:00Z"}""",
        )

        assertEquals("device-1", result.deviceId)
        assertEquals("stormbundle://payload", result.bundle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonBundleRecoveryPayload() {
        parseRecoveryDeviceBundle(
            """{"device_id":"device-1","bundle":"vless://unexpected"}""",
        )
    }

    @Test
    fun attemptsRecoveryOnlyForTransportFailures() {
        assertTrue(shouldAttemptAccountRecovery(IOException("offline")))
        assertTrue(shouldAttemptAccountRecovery(AccountApiException(503, "unavailable")))
        assertTrue(shouldAttemptAccountRecovery(AccountApiException(451, "blocked")))
        assertFalse(shouldAttemptAccountRecovery(AccountApiException(401, "invalid session")))
        assertFalse(shouldAttemptAccountRecovery(AccountApiException(404, "not found")))
    }

    @Test
    fun triesStableAndLegacyInstallationIdentitiesOnce() {
        assertEquals(
            listOf("stable", "legacy"),
            recoveryInstallationCandidates(storedId = "legacy", stableId = "stable"),
        )
        assertEquals(
            listOf("same"),
            recoveryInstallationCandidates(storedId = "same", stableId = "same"),
        )
    }
}
