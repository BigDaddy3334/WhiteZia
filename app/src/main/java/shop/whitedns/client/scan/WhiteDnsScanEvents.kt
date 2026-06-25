package shop.whitedns.client.scan

import java.util.concurrent.CopyOnWriteArraySet
import shop.whitedns.client.model.WhiteDnsScanState

sealed class WhiteDnsScanEvent {
    data class Log(val sessionId: String, val message: String) : WhiteDnsScanEvent()
    data class State(val state: WhiteDnsScanState) : WhiteDnsScanEvent()
}

object WhiteDnsScanEvents {
    private val listeners = CopyOnWriteArraySet<(WhiteDnsScanEvent) -> Unit>()

    fun addListener(listener: (WhiteDnsScanEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (WhiteDnsScanEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun log(sessionId: String, message: String) {
        emit(WhiteDnsScanEvent.Log(sessionId, message))
    }

    fun state(state: WhiteDnsScanState) {
        emit(WhiteDnsScanEvent.State(state))
    }

    private fun emit(event: WhiteDnsScanEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }
}
