package shop.whitezia.client.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRecoveryMonitorTest {
    @Test fun pollsEvenWhenAndroidDoesNotSendAnotherCallback() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        var checks = 0
        withTimeout(2_000L) {
            monitorNetworkRecovery(changes, 10L, 1L, onError = { throw it }) {
                if (++checks == 3) changes.close()
            }
        }
        assertEquals(3, checks)
    }

    @Test fun failedObservationDoesNotStopFutureRecovery() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        var checks = 0
        val errors = mutableListOf<Exception>()
        changes.trySend(Unit)
        withTimeout(2_000L) {
            monitorNetworkRecovery(changes, 10L, 1L, onError = errors::add) {
                if (++checks == 1) error("temporary network failure")
                changes.close()
            }
        }
        assertEquals(2, checks)
        assertEquals(1, errors.size)
    }

    @Test fun cancellationIsNotConvertedToRetry() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        changes.trySend(Unit)
        var errors = 0
        val error = runCatching {
            monitorNetworkRecovery(changes, 10L, 1L, onError = { errors++ }) {
                throw CancellationException("service stopped")
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(0, errors)
    }

    @Test fun closedMonitorDoesNotRestartTunnel() = runBlocking {
        val changes = Channel<Unit>(Channel.CONFLATED)
        changes.close()
        var checks = 0
        monitorNetworkRecovery(changes, 10L, 1L, onError = { throw it }) { checks++ }
        assertEquals(0, checks)
    }
}
