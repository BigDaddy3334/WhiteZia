package shop.whitezia.client.proxy

import java.util.concurrent.CopyOnWriteArraySet

sealed class WhiteZiaProxyEvent {
    data class Log(val sessionId: String, val message: String) : WhiteZiaProxyEvent()
    data class Ready(val sessionId: String, val message: String) : WhiteZiaProxyEvent()
    data class Failed(val sessionId: String, val message: String) : WhiteZiaProxyEvent()
}

object WhiteZiaProxyEvents {
    private val listeners = CopyOnWriteArraySet<(WhiteZiaProxyEvent) -> Unit>()

    fun addListener(listener: (WhiteZiaProxyEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (WhiteZiaProxyEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun log(sessionId: String, message: String) {
        emit(WhiteZiaProxyEvent.Log(sessionId, message))
    }

    fun ready(sessionId: String, message: String) {
        emit(WhiteZiaProxyEvent.Ready(sessionId, message))
    }

    fun failed(sessionId: String, message: String) {
        emit(WhiteZiaProxyEvent.Failed(sessionId, message))
    }

    private fun emit(event: WhiteZiaProxyEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }
}
