package shop.whitezia.client.proxy

import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore

/** Listener generation owns every client/upstream, including pending handshakes. */
internal class ProxyBridgeSession(val listener: ServerSocket, maxClients: Int = 64) : Closeable {
    private val permits = Semaphore(maxClients)
    private val sockets = mutableSetOf<Socket>()
    @Volatile var isActive: Boolean = true
        private set

    fun admitClient(socket: Socket): Boolean {
        if (!permits.tryAcquire()) {
            socket.close()
            return false
        }
        if (register(socket)) return true
        permits.release()
        return false
    }

    @Synchronized
    fun register(socket: Socket): Boolean {
        if (!isActive) {
            socket.close()
            return false
        }
        sockets += socket
        return true
    }

    fun releaseClient(socket: Socket) {
        release(socket)
        permits.release()
    }

    @Synchronized
    fun release(socket: Socket) {
        sockets -= socket
        runCatching { socket.close() }
    }

    @Synchronized
    override fun close() {
        isActive = false
        runCatching { listener.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
    }
}
