package shop.whitezia.client.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuntimeStopGuardTest {
    @Test fun nextStartRequiresBothStoppedStatusAndClosedListener() = runBlocking {
        var now = 0L
        var stops = 0
        val guard = RuntimeStopGuard(
            readStates = { listOf(state(if (now < 100) "stopping" else "stopped")) },
            readPorts = { setOf(10886) }, probePort = { now < 300 }, stopServices = { stops++ },
            log = {}, debug = {},
            awaiter = RuntimeStopAwaiter(1_000, 100, 200, { now }, { now += it }),
        )
        assertTrue(guard.prepare())
        assertEquals(1, stops)
        assertEquals(500L, now)
    }

    @Test fun lingeringNativeListenerBlocksStartEvenAfterFailedStatus() = runBlocking {
        var now = 0L
        val guard = RuntimeStopGuard(
            readStates = { listOf(state("failed")) }, readPorts = { setOf(10886) },
            probePort = { true }, stopServices = {}, log = {}, debug = {},
            awaiter = RuntimeStopAwaiter(500, 100, 100, { now }, { now += it }),
        )
        assertFalse(guard.prepare())
        assertEquals(500L, now)
    }

    private fun state(status: String) = WhiteZiaRuntimeState("fixture", "vpn", status, "profile", "127.0.0.1", 10886, 0L)
}
