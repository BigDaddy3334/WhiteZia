package shop.whitezia.client.account

import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnrollmentPolicyTest {
    @Test fun missingDeviceDoesNotFetchOrCreateProfile() {
        var reads = 0
        val result = readExistingDeviceProfile(findDevice = { null }, fetchBundle = { reads++; "bundle" })
        assertNull(result)
        assertEquals(0, reads)
    }

    @Test fun disabledAndFailedDevicesCannotUseStaleReadyProfile() {
        for (status in listOf("disabled", "failed")) {
            var reads = 0
            val result = readExistingDeviceProfile(
                findDevice = { device(status, ready = true) },
                fetchBundle = { reads++; "stale" },
            )
            assertNull(result?.bundle)
            assertEquals(0, reads)
        }
    }

    @Test fun existingActiveDeviceFetchesProfileWithoutEnrollment() {
        val result = readExistingDeviceProfile({ device("active", ready = true) }, { "bundle" })
        assertEquals("existing", result?.device?.id)
        assertEquals("bundle", result?.bundle)
    }

    @Test fun provisioningKeepsExistingBindingAndWaitsForProfile() {
        val result = readExistingDeviceProfile(
            { device("provisioning", ready = false) },
            { error("Profile is not ready yet") },
        )
        assertEquals("existing", result?.device?.id)
        assertNull(result?.bundle)
    }

    @Test fun pendingResponseDoesNotReplaceExistingDevice() {
        val result = readExistingDeviceProfile(
            { device("active", ready = true) },
            { throw AccountApiException(409, DeviceProfilePendingMessage) },
        )
        assertEquals("existing", result?.device?.id)
        assertNull(result?.bundle)
    }

    @Test fun rejectedProofDoesNotAutomaticallyReenroll() {
        val error = assertThrows(AccountApiException::class.java) {
            readExistingDeviceProfile(
                { device("active", ready = true) },
                { throw AccountApiException(401, "invalid proof") },
            )
        }
        assertEquals(401, error.statusCode)
    }

    @Test fun deviceDeletedWhileFetchingCannotUseSavedProfile() {
        for (status in listOf(401, 404)) {
            var lookups = 0
            assertThrows(AccountDeviceNotBoundException::class.java) {
                readExistingDeviceProfile(
                    findDevice = { if (lookups++ == 0) device("active", ready = true) else null },
                    fetchBundle = { throw AccountApiException(status, "device proof rejected") },
                )
            }
            assertEquals(2, lookups)
        }
    }

    @Test fun slowNodeAndTransientNetworkFailuresKeepWaiting() {
        assertTrue(shouldRetryDeviceEnrollment(SocketTimeoutException("Read timed out")))
        assertTrue(shouldRetryDeviceEnrollment(AccountApiException(409, DeviceProfilePendingMessage)))
        assertTrue(shouldRetryDeviceEnrollment(AccountApiException(503, "temporarily unavailable")))
        assertTrue(shouldRetryDeviceEnrollment(AccountApiException(429, "rate limited")))
        assertTrue(shouldRetryDeviceEnrollment(AccountApiException(408, "request timed out")))
    }

    @Test fun genuineRejectionsDoNotKeepPolling() {
        assertFalse(shouldRetryDeviceEnrollment(AccountDeviceNotBoundException()))
        assertFalse(shouldRetryDeviceEnrollment(AccountApiException(401, "invalid proof")))
        assertFalse(shouldRetryDeviceEnrollment(AccountApiException(403, "forbidden")))
        assertFalse(shouldRetryDeviceEnrollment(AccountApiException(409, "device limit reached")))
        assertFalse(shouldRetryDeviceEnrollment(IllegalArgumentException("invalid bundle")))
    }

    private fun device(status: String, ready: Boolean) = AccountDevice(
        id = "existing", name = "Phone", status = status, platform = "android",
        bundleReady = ready, createdAt = "2026-10-01T00:00:00Z",
    )
}
