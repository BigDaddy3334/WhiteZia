package shop.whitezia.client.fallback

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportRestartCoordinatorTest {
    @Test
    fun waitsForRuntimeStopAndSettleDelayBeforeStarting() = runBlocking {
        var delayedMillis = 0L
        var stopWaitCalled = false
        val coordinator = TransportRestartCoordinator { delayedMillis += it }

        val result = coordinator.awaitReady(
            awaitRuntimeStop = {
                stopWaitCalled = true
                true
            },
            settleDelayMillis = 3_000L,
            shouldContinue = { true },
        )

        assertEquals(TransportRestartResult.Ready, result)
        assertTrue(stopWaitCalled)
        assertEquals(3_000L, delayedMillis)
    }

    @Test
    fun doesNotDelayWhenRuntimeDidNotStop() = runBlocking {
        var delayedMillis = 0L
        val coordinator = TransportRestartCoordinator { delayedMillis += it }

        val result = coordinator.awaitReady(
            awaitRuntimeStop = { false },
            settleDelayMillis = 3_000L,
            shouldContinue = { true },
        )

        assertEquals(TransportRestartResult.RuntimeStopTimedOut, result)
        assertEquals(0L, delayedMillis)
    }

    @Test
    fun doesNotWaitWhenTransitionWasCancelled() = runBlocking {
        var stopWaitCalled = false
        val coordinator = TransportRestartCoordinator()

        val result = coordinator.awaitReady(
            awaitRuntimeStop = {
                stopWaitCalled = true
                true
            },
            settleDelayMillis = 3_000L,
            shouldContinue = { false },
        )

        assertEquals(TransportRestartResult.Cancelled, result)
        assertFalse(stopWaitCalled)
    }
}
