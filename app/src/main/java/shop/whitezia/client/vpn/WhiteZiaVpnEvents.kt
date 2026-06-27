package shop.whitezia.client.vpn

import java.util.concurrent.CopyOnWriteArraySet

sealed class WhiteZiaVpnEvent {
    data class Log(val sessionId: String, val message: String) : WhiteZiaVpnEvent()
    data class Ready(val sessionId: String, val message: String) : WhiteZiaVpnEvent()
    data class Failed(val sessionId: String, val message: String) : WhiteZiaVpnEvent()
}

object WhiteZiaVpnEvents {
    private val listeners = CopyOnWriteArraySet<(WhiteZiaVpnEvent) -> Unit>()

    fun addListener(listener: (WhiteZiaVpnEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (WhiteZiaVpnEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun log(sessionId: String, message: String) {
        emit(WhiteZiaVpnEvent.Log(sessionId, message))
    }

    fun ready(sessionId: String, message: String) {
        emit(WhiteZiaVpnEvent.Ready(sessionId, message))
    }

    fun failed(sessionId: String, message: String) {
        emit(WhiteZiaVpnEvent.Failed(sessionId, message))
    }

    private fun emit(event: WhiteZiaVpnEvent) {
        listeners.forEach { listener ->
            runCatching { listener(event) }
        }
    }
}
