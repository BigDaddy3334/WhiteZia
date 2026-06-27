package shop.whitezia.client.scan

import java.util.concurrent.CopyOnWriteArraySet
import shop.whitezia.client.model.WhiteZiaScanState

sealed class WhiteZiaScanEvent {
    data class Log(val sessionId: String, val message: String) : WhiteZiaScanEvent()
    data class State(val state: WhiteZiaScanState) : WhiteZiaScanEvent()
}

object WhiteZiaScanEvents {
    private val listeners = CopyOnWriteArraySet<(WhiteZiaScanEvent) -> Unit>()

    fun addListener(listener: (WhiteZiaScanEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (WhiteZiaScanEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun log(sessionId: String, message: String) {
        emit(WhiteZiaScanEvent.Log(sessionId, message))
    }

    fun state(state: WhiteZiaScanState) {
        emit(WhiteZiaScanEvent.State(state))
    }

    private fun emit(event: WhiteZiaScanEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }
}
