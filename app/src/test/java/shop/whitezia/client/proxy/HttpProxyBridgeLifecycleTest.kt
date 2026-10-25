package shop.whitezia.client.proxy

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class HttpProxyBridgeLifecycleTest {
    @Test fun stopClosesAnEstablishedIdleConnectTunnel() {
        withBridge { bridge, proxyPort, socks ->
            val peerClosed = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val worker = thread(isDaemon = true) {
                try {
                    socks.accept().use { peer ->
                        peer.soTimeout = 5_000
                        val input = peer.getInputStream()
                        val output = peer.getOutputStream()
                        repeat(3) { check(input.read() >= 0) }
                        output.write(byteArrayOf(5, 0)); output.flush()
                        val header = ByteArray(4)
                        input.readNBytes(header, 0, 4)
                        when (header[3].toInt()) {
                            1 -> input.readNBytes(4)
                            3 -> input.readNBytes(input.read())
                            4 -> input.readNBytes(16)
                            else -> error("Unexpected SOCKS address")
                        }
                        input.readNBytes(2)
                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); output.flush()
                        if (input.read() == -1) peerClosed.countDown()
                    }
                } catch (error: Throwable) { failure.set(error) }
            }
            Socket("127.0.0.1", proxyPort).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write("CONNECT fixture.example:443 HTTP/1.1\r\nHost: fixture.example\r\n\r\n".toByteArray())
                val reader = client.getInputStream().bufferedReader()
                assertEquals("HTTP/1.1 200 Connection Established", reader.readLine())
                assertEquals("", reader.readLine())
                bridge.stop()
                assertEquals(-1, reader.read())
                assertTrue(peerClosed.await(5, TimeUnit.SECONDS))
            }
            worker.join(5_000)
            failure.get()?.let { throw AssertionError("SOCKS fixture failed", it) }
        }
    }

    @Test fun partialSocksHandshakeEofClosesUpstream() {
        withBridge { _, proxyPort, socks ->
            val peerClosed = CountDownLatch(1)
            val worker = thread(isDaemon = true) {
                socks.accept().use { peer ->
                    peer.soTimeout = 5_000
                    peer.getInputStream().readNBytes(3)
                    peer.getOutputStream().write(5)
                    peer.shutdownOutput()
                    if (peer.getInputStream().read() == -1) peerClosed.countDown()
                }
            }
            Socket("127.0.0.1", proxyPort).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write("CONNECT fixture.example:443 HTTP/1.1\r\n\r\n".toByteArray())
                assertTrue(client.getInputStream().bufferedReader().readLine().startsWith("HTTP/1.1 502"))
                assertTrue("Handshake failure leaked upstream", peerClosed.await(5, TimeUnit.SECONDS))
            }
            worker.join(5_000)
        }
    }

    private fun withBridge(block: (HttpProxyBridge, Int, ServerSocket) -> Unit) {
        ServerSocket(0).use { socks ->
            val proxyPort = ServerSocket(0).use { it.localPort }
            val bridge = HttpProxyBridge()
            try {
                bridge.start("127.0.0.1", proxyPort, "127.0.0.1", socks.localPort)
                block(bridge, proxyPort, socks)
            } finally { bridge.stop() }
        }
    }
}
