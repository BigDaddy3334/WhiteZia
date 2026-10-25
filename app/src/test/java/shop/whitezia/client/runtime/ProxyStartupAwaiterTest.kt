package shop.whitezia.client.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.storm.CottenDnsEngineContract

class ProxyStartupAwaiterTest {
    @Test fun cottenCanFinishTcpFallbackAfterTheOldFifteenSecondLimit() = runBlocking {
        var now = 0L
        var pollsAfterUdp = 0
        awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
            startupFailure = { null }, isRunning = { true }, exitCode = { null },
            isPortReady = { if (now >= 15_000L) pollsAfterUdp++; now >= 24_000L },
            monotonicMillis = { now }, pause = { now += it })
        assertEquals(24_000L, now)
        assertTrue(pollsAfterUdp > 0)
    }

    @Test fun xrayStillUsesItsShortBudgetRatherThanCottenFleetBudget() = runBlocking {
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("Xray", 15_000L, { null }, { true }, { null }, { now >= 24_000L },
                monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("timed out"))
        assertEquals(15_000L, now)
    }

    @Test fun aLiveButNeverReadyCottenHasOneBoundedDeadline() = runBlocking {
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                { null }, { true }, { null }, { false }, monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("timed out"))
        assertEquals(CottenDnsEngineContract.SocksStartupTimeoutMillis, now)
    }

    @Test fun stopRequestAbortsInsteadOfWaitingForTheRemainingFleetBudget() = runBlocking {
        var now = 0L
        var requested = true
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                { null }, { true }, { null }, { false }, keepWaiting = { requested },
                monotonicMillis = { now }, pause = { now += it; if (now >= 16_000L) requested = false })
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(16_000L, now)
    }

    @Test fun nativeProcessExitAbortsWithItsExitCodeAtTheNextPoll() = runBlocking {
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                { null }, { now < 12_000L }, { 7 }, { false },
                monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("exit code 7"))
        assertEquals(12_000L, now)
    }

    @Test fun nativeReportedFailureAbortsWithoutConsumingTheRestOfTheBudget() = runBlocking {
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                { if (now >= 5_000L) "No usable resolver" else null }, { true }, { null }, { false },
                monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("No usable resolver"))
        assertEquals(5_000L, now)
    }

    @Test fun coroutineCancellationInterruptsTheWaitAtTheCurrentPoll() {
        var now = 0L
        val failure = runCatching {
            runBlocking {
                awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                    { null }, { true }, { null }, { false }, monotonicMillis = { now }, pause = {
                        now += it
                        if (now >= 16_000L) currentCoroutineContext().cancel()
                        yield()
                    })
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(16_000L, now)
    }

    @Test fun portBecomingReadyAfterTheDeadlineCannotPassTheWait() = runBlocking {
        var now = 0L
        val failure = runCatching {
            awaitProxyStartup("CottenDNS", CottenDnsEngineContract.SocksStartupTimeoutMillis,
                { null }, { true }, { null }, { now = 80_000L; true }, monotonicMillis = { now }, pause = { now += it })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().contains("timed out"))
    }
}
