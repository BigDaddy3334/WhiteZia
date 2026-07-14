package shop.whitezia.client.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStopAwaiterTest {
    @Test
    fun waitsForTheFullStableWindowAfterRuntimeStops() = runBlocking {
        var nowMillis = 0L
        val awaiter = RuntimeStopAwaiter(
            timeoutMillis = 1_000L,
            pollIntervalMillis = 100L,
            stableWindowMillis = 300L,
            nowMillis = { nowMillis },
            delayMillis = { nowMillis += it },
        )

        val stopped = awaiter.awaitStopped(
            isRuntimeActive = { nowMillis < 200L },
            isProxyActive = { false },
        )

        assertTrue(stopped)
        assertEquals(500L, nowMillis)
    }

    @Test
    fun timesOutWhenTheRuntimeStaysActive() = runBlocking {
        var nowMillis = 0L
        val awaiter = RuntimeStopAwaiter(
            timeoutMillis = 1_000L,
            pollIntervalMillis = 100L,
            stableWindowMillis = 200L,
            nowMillis = { nowMillis },
            delayMillis = { nowMillis += it },
        )

        val stopped = awaiter.awaitStopped(
            isRuntimeActive = { true },
            isProxyActive = { false },
        )

        assertFalse(stopped)
        assertEquals(1_000L, nowMillis)
    }
}
