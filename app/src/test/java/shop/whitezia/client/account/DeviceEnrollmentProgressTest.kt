package shop.whitezia.client.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnrollmentProgressTest {
    @Test fun elapsedTimeIsMonotonicAndFreezesAtCompletion() {
        val progress = DeviceEnrollmentProgress(DeviceEnrollmentStage.PREPARING, 10_000)
        assertEquals(0L, progress.elapsedSeconds(9_000))
        assertEquals(5L, progress.elapsedSeconds(15_900))
        assertEquals(8L, progress.copy(finishedAtMillis = 18_000).elapsedSeconds(99_000))
    }

    @Test fun pollingTracksActualAttemptsAndDoesNotPretendTheProfileIsReady() {
        val progress = DeviceEnrollmentProgress(DeviceEnrollmentStage.PROVISIONING, 0, polling = true)
        val failedPoll = progress.afterPoll("Сервис недоступен")
        assertEquals(1, failedPoll.pollAttempts)
        assertEquals("Сервис недоступен", failedPoll.pollError)
        assertEquals(DeviceEnrollmentStage.PROVISIONING, failedPoll.stage)
        assertFalse(failedPoll.canRetry)
        val pendingPoll = failedPoll.afterPoll()
        assertEquals(2, pendingPoll.pollAttempts)
        assertEquals("", pendingPoll.pollError)
        assertEquals(DeviceEnrollmentStage.PROVISIONING, pendingPoll.stage)
    }

    @Test fun pausedOrFailedEnrollmentCanBeRetriedButApplyingCannot() {
        val pending = DeviceEnrollmentProgress(DeviceEnrollmentStage.PROVISIONING, 0)
        assertTrue(pending.canRetry)
        assertTrue(pending.copy(stage = DeviceEnrollmentStage.FAILED).canRetry)
        assertFalse(pending.copy(stage = DeviceEnrollmentStage.APPLYING).canRetry)
        assertFalse(pending.copy(stage = DeviceEnrollmentStage.READY).canRetry)
    }
}
