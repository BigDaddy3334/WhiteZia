package shop.whitezia.client.proxy

import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class ProxyBridgeSessionTest {
    @Test fun stopClosesListenerAndAllOwnedSockets() {
        val listener = ServerSocket(0)
        val session = ProxyBridgeSession(listener)
        val client = Socket()
        val upstream = Socket()
        assertTrue(session.admitClient(client))
        assertTrue(session.register(upstream))
        session.close()
        assertTrue(listener.isClosed)
        assertTrue(client.isClosed)
        assertTrue(upstream.isClosed)
        val late = Socket()
        assertFalse(session.register(late))
        assertTrue(late.isClosed)
    }

    @Test fun sessionBoundsClientsAndReturnsPermitAfterRelease() {
        ProxyBridgeSession(ServerSocket(0), maxClients = 1).use { session ->
            val first = Socket()
            assertTrue(session.admitClient(first))
            val overflow = Socket()
            assertFalse(session.admitClient(overflow))
            assertTrue(overflow.isClosed)
            session.releaseClient(first)
            assertTrue(session.admitClient(Socket()))
        }
    }

    @Test fun retiringOldGenerationDoesNotCloseNewListener() {
        val previous = ProxyBridgeSession(ServerSocket(0))
        ProxyBridgeSession(ServerSocket(0)).use { next ->
            previous.close()
            assertFalse(next.listener.isClosed)
            assertTrue(next.admitClient(Socket()))
        }
    }
}
