package shop.whitezia.client.ui.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import shop.whitezia.client.runtime.WhiteZiaRuntimeState

class RuntimeUiEventGuardTest {
    @Test fun readyFromEarlierAttemptDoesNotOverrideSameSessionRecovery() {
        val ready = WhiteZiaRuntimeState("same", "vpn", "ready", "profile", "127.0.0.1", 10886, 1)
        assertEquals(ready, currentReadyRuntimeSnapshot(ready, "same", "vpn"))
        assertNull(currentReadyRuntimeSnapshot(ready.copy(status = "starting", recoveryWaiting = true), "same", "vpn"))
        assertNull(currentReadyRuntimeSnapshot(ready, "new", "vpn"))
        assertNull(currentReadyRuntimeSnapshot(ready, "same", "proxy"))
        assertNull(currentReadyRuntimeSnapshot(null, "same", "vpn"))
    }

    @Test fun queuedOldReadyFailureOrProgressCannotOverwriteNewSession() = runBlocking {
        var current = "old"
        var visible = "connecting old"
        val queue = CompletableDeferred<Unit>()
        val oldEvents = listOf("ready old", "failed old", "checking old").map { value ->
            async {
                queue.await()
                applyCurrentRuntimeUiEvent("old", { it == current }) { visible = value }
            }
        }
        yield()
        current = "new"
        visible = "connecting new"
        queue.complete(Unit)
        oldEvents.forEach { it.await() }
        assertEquals("connecting new", visible)
        applyCurrentRuntimeUiEvent("new", { it == current }) { visible = "checking new" }
        assertEquals("checking new", visible)
    }

    @Test fun userStopWhileEventIsQueuedSuppressesItWithoutCreatingConnection() = runBlocking {
        var stopped = false
        var calls = 0
        val queue = CompletableDeferred<Unit>()
        val ready = async {
            queue.await()
            applyCurrentRuntimeUiEvent("same", { !stopped }) { calls++ }
        }
        yield()
        stopped = true
        queue.complete(Unit)
        ready.await()
        assertEquals(0, calls)
    }
}
