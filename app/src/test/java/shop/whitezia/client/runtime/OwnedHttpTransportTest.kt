package shop.whitezia.client.runtime

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedHttpTransportTest {
    @Test(timeout = 5_000)
    fun socksUsesRemoteDomainAndAnOwnedHttpConnection() {
        val destination = AtomicReference<String>()
        val requestHeaders = AtomicReference<String>()
        SocksPeer { socket ->
            destination.set(acceptConnect(socket))
            requestHeaders.set(readHeaders(socket))
            reply(socket, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello")
        }.use { peer ->
            val result = OwnedHttpTransport().newCall(request(peer.port)).execute()
            assertEquals(200, result.status)
            assertEquals("hello", result.body.toString(StandardCharsets.UTF_8))
            assertEquals(5L, result.bytesRead)
            assertTrue(result.complete)
            assertEquals("probe.invalid:80", destination.get())
            assertTrue(requestHeaders.get().contains("Host: probe.invalid", ignoreCase = true))
            assertTrue(requestHeaders.get().contains("Accept-Encoding: identity", ignoreCase = true))
        }
    }

    @Test(timeout = 5_000)
    fun greetingBlackholeIsBoundedAndSocketIsClosed() {
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            readGreeting(socket)
            if (socket.getInputStream().read() == -1) closed.countDown()
        }.use { peer ->
            val started = System.nanoTime()
            assertFailure { OwnedHttpTransport().newCall(request(peer.port, total = 300)).execute() }
            assertBounded(started)
            assertTrue("deadline must close the owned socket", closed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun connectReplyBlackholeIsBoundedAndSocketIsClosed() {
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            readGreeting(socket)
            socket.getOutputStream().write(byteArrayOf(5, 0))
            readConnect(socket)
            if (socket.getInputStream().read() == -1) closed.countDown()
        }.use { peer ->
            val started = System.nanoTime()
            assertFailure { OwnedHttpTransport().newCall(request(peer.port, total = 300)).execute() }
            assertBounded(started)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun missingTlsServerHelloCannotHoldTheProbe() {
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            acceptConnect(socket)
            val input = socket.getInputStream()
            while (input.read() >= 0) Unit
            closed.countDown()
        }.use { peer ->
            val started = System.nanoTime()
            assertFailure {
                OwnedHttpTransport().newCall(request(peer.port, total = 350).copy(url = "https://probe.invalid/")).execute()
            }
            assertBounded(started)
            assertTrue("TLS deadline must close the underlying plain socket", closed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun activeReadIsCancelledWithoutWaitingForTheReadTimeout() = runBlocking {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reading.countDown()
            if (socket.getInputStream().read() == -1) closed.countDown()
        }.use { peer ->
            val call = OwnedHttpTransport().newCall(request(peer.port, total = 20_000).copy(readTimeoutMillis = 20_000))
            val job = async(Dispatchers.Default) { call.await() }
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            val started = System.nanoTime()
            job.cancelAndJoin()
            assertBounded(started)
            assertTrue(job.isCancelled)
            assertTrue("coroutine cancellation must close the connection", closed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 6_000)
    fun cancellingHealthScoreClosesAllFourEndpointSockets() = runBlocking {
        val entered = CountDownLatch(4)
        val closed = CountDownLatch(4)
        SocksPeer { socket ->
            acceptConnect(socket)
            entered.countDown()
            while (socket.getInputStream().read() >= 0) Unit
            closed.countDown()
        }.use { peer ->
            val job = async(Dispatchers.Default) {
                ConnectionProbeClient().measureHttpHealthScore({}, "test", socksProxyPort = peer.port)
            }
            assertTrue("all four endpoint requests must reach the SOCKS listener", entered.await(2, TimeUnit.SECONDS))
            val started = System.nanoTime()
            job.cancelAndJoin()
            assertBounded(started)
            assertTrue(job.isCancelled)
            assertTrue("health cancellation must close every endpoint socket", closed.await(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 6_000)
    fun maxHealthReserveMarksAllFourSocksDomainsAndKeepsCancellationOwned() = runBlocking {
        val entered = CountDownLatch(4)
        val closed = CountDownLatch(4)
        val destinations = CopyOnWriteArrayList<String>()
        SocksPeer { socket ->
            destinations += acceptConnect(socket)
            entered.countDown()
            while (socket.getInputStream().read() >= 0) Unit
            closed.countDown()
        }.use { peer ->
            val job = async(Dispatchers.Default) {
                ConnectionProbeClient().measureHttpHealthScore({}, "test", socksProxyPort = peer.port, useCottenMaxHealthReserve = true)
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(setOf("connectivitycheck.gstatic.com", "www.google.com", "www.cloudflare.com", "ya.ru")
                .map { "$it.whitezia-health.invalid:443" }.toSet(), destinations.toSet())
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(closed.await(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 6_000)
    fun cancellingBenchmarkClosesItsTlsBlackholeSocket() = runBlocking {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            acceptConnect(socket)
            entered.countDown()
            while (socket.getInputStream().read() >= 0) Unit
            closed.countDown()
        }.use { peer ->
            val job = async(Dispatchers.Default) {
                ConnectionProbeClient().measureBenchmarkSpeed({}, "test", socksProxyPort = peer.port)
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val started = System.nanoTime()
            job.cancelAndJoin()
            assertBounded(started)
            assertTrue(job.isCancelled)
            assertTrue("benchmark cancellation must close the raw TLS socket", closed.await(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun cancellationBeforeWorkerStartCannotCreateALateSocket() = runBlocking {
        val queued = AtomicReference<Runnable>()
        val executor = Executor { task -> check(queued.compareAndSet(null, task)) }
        SocksPeer { socket -> acceptConnect(socket) }.use { peer ->
            val call = OwnedHttpTransport(executor = executor).newCall(request(peer.port))
            val job = async { call.await() }
            yield()
            assertNotNull("the IO task must be queued", queued.get())
            job.cancelAndJoin()
            queued.get().run()
            assertFalse("cancelled work connected after dispatch", peer.connected.await(150, TimeUnit.MILLISECONDS))
            assertEquals("cancelled queued work must not open a socket", 0, peer.accepted.get())
        }
    }

    @Test(timeout = 5_000)
    fun slowlyDrippingHeadersStillObeyTheAbsoluteDeadline() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            val output = socket.getOutputStream()
            for (byte in "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello".toByteArray()) {
                output.write(byte.toInt())
                output.flush()
                Thread.sleep(40)
            }
        }.use { peer ->
            val started = System.nanoTime()
            assertFailure { OwnedHttpTransport().newCall(request(peer.port, total = 300)).execute() }
            assertBounded(started)
        }
    }

    @Test(timeout = 5_000)
    fun redirectsShareOneAbsoluteBudget() {
        val requests = AtomicInteger()
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            requests.incrementAndGet()
            Thread.sleep(180)
            reply(socket, "HTTP/1.1 302 Found\r\nLocation: http://probe.invalid/next\r\nContent-Length: 0\r\n\r\n")
        }.use { peer ->
            val started = System.nanoTime()
            assertFailure { OwnedHttpTransport().newCall(request(peer.port, total = 300)).execute() }
            assertBounded(started)
            assertTrue("a redirect must not reset the deadline", requests.get() in 1..2)
        }
    }

    @Test(timeout = 5_000)
    fun chunkedBodyIsDecodedAndPreviewDoesNotLimitByteAccounting() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n")
        }.use { peer ->
            val result = OwnedHttpTransport().newCall(request(peer.port).copy(captureBodyBytes = 3)).execute()
            assertEquals(200, result.status)
            assertTrue(result.complete)
            assertEquals(5L, result.bytesRead)
            assertEquals("abc", result.body.toString(StandardCharsets.UTF_8))
        }
    }

    @Test(timeout = 5_000)
    fun contentLengthTruncationCannotReportACompleteDownload() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nshort")
        }.use { peer ->
            assertFailure { OwnedHttpTransport().newCall(request(peer.port)).execute() }
        }
    }

    @Test(timeout = 5_000)
    fun oversizedChunkCannotOverflowOrWaitForAnUnboundedBody() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nffffffffffffffffffffffff\r\n")
        }.use { peer ->
            assertFailure { OwnedHttpTransport().newCall(request(peer.port)).execute() }
        }
    }

    @Test(timeout = 5_000)
    fun bodyCaptureAndTransferCapStayBounded() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n" + "x".repeat(100))
        }.use { peer ->
            val result = OwnedHttpTransport().newCall(request(peer.port).copy(maxBodyBytes = 7, captureBodyBytes = 3)).execute()
            assertEquals(7L, result.bytesRead)
            assertEquals(3, result.body.size)
        }
    }

    @Test(timeout = 5_000)
    fun connectivity204CompletesWithoutWaitingForAnEofBody() {
        val closed = CountDownLatch(1)
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 204 No Content\r\nConnection: keep-alive\r\n\r\n")
            if (socket.getInputStream().read() == -1) closed.countDown()
        }.use { peer ->
            val started = System.nanoTime()
            val response = OwnedHttpTransport().newCall(request(peer.port)).execute()
            assertEquals(204, response.status)
            assertTrue(response.complete)
            assertEquals(0L, response.bytesRead)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 5_000)
    fun ambiguousFramingDoesNotReportSuccessfulBodyCompletion() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n")
        }.use { peer ->
            assertFailure { OwnedHttpTransport().newCall(request(peer.port)).execute() }
        }
    }

    @Test(timeout = 5_000)
    fun unboundedHeaderLineIsRejected() {
        SocksPeer { socket ->
            acceptConnect(socket)
            readHeaders(socket)
            reply(socket, "HTTP/1.1 200 OK\r\nX-Padding: " + "x".repeat(9_000) + "\r\nContent-Length: 0\r\n\r\n")
        }.use { peer ->
            assertFailure { OwnedHttpTransport().newCall(request(peer.port)).execute() }
        }
    }

    private fun request(port: Int, total: Long = 2_000) = ProbeHttpRequest(
        url = "http://probe.invalid/",
        socksProxyPort = port,
        connectTimeoutMillis = 1_000,
        readTimeoutMillis = 1_000,
        totalTimeoutMillis = total,
        maxBodyBytes = 4_096,
    )

    private fun assertFailure(block: () -> ProbeHttpResponse) {
        try {
            val response = block()
            assertFalse("an incomplete or timed-out exchange cannot report complete", response.complete)
        } catch (_: IOException) {
            // Failing before a response is also a valid bounded failure outcome.
        }
    }

    private fun assertBounded(started: Long) {
        assertTrue("the absolute deadline/cancellation was not bounded", (System.nanoTime() - started) / 1_000_000 < 2_000)
    }

    private class SocksPeer(private val handle: (Socket) -> Unit) : Closeable {
        private val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val tasks = CopyOnWriteArrayList<Thread>()
        val accepted = AtomicInteger()
        val connected = CountDownLatch(1)
        val port: Int get() = server.localPort
        private val listener = Thread({
            try {
                while (!server.isClosed) {
                    val socket = server.accept().apply { soTimeout = 2_000 }
                    sockets += socket
                    accepted.incrementAndGet()
                    connected.countDown()
                    val task = Thread({
                        socket.use {
                            try { handle(it) } catch (_: IOException) { /* expected when the caller closes */ }
                        }
                    }, "owned-http-test-peer").apply { isDaemon = true }
                    tasks += task
                    task.start()
                }
            } catch (_: IOException) {
                // close() releases the listener.
            }
        }, "owned-http-test-listener").apply { isDaemon = true; start() }

        override fun close() {
            server.close()
            sockets.forEach { runCatching { it.close() } }
            listener.join(500)
            tasks.forEach { it.join(500) }
        }
    }

    private companion object {
        fun readExact(socket: Socket, count: Int): ByteArray {
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val got = socket.getInputStream().read(bytes, offset, count - offset)
                if (got < 0) throw EOFException()
                offset += got
            }
            return bytes
        }

        fun readGreeting(socket: Socket) {
            val header = readExact(socket, 2)
            check(header[0].toInt() == 5)
            check(readExact(socket, header[1].toInt() and 255).any { it.toInt() == 0 })
        }

        fun readConnect(socket: Socket): String {
            val header = readExact(socket, 4)
            check(header.contentEquals(byteArrayOf(5, 1, 0, 3))) { "SOCKS must request remote hostname resolution" }
            val length = readExact(socket, 1)[0].toInt() and 255
            val domain = readExact(socket, length).toString(StandardCharsets.US_ASCII)
            val port = readExact(socket, 2)
            return "$domain:${((port[0].toInt() and 255) shl 8) or (port[1].toInt() and 255)}"
        }

        fun acceptConnect(socket: Socket): String {
            readGreeting(socket)
            socket.getOutputStream().write(byteArrayOf(5, 0))
            val destination = readConnect(socket)
            socket.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80))
            return destination
        }

        fun readHeaders(socket: Socket): String {
            val out = ByteArrayOutputStream()
            var end = 0
            while (out.size() < 16_384) {
                val byte = socket.getInputStream().read()
                if (byte < 0) throw EOFException()
                out.write(byte)
                end = when {
                    (end == 0 || end == 2) && byte == 13 -> end + 1
                    (end == 1 || end == 3) && byte == 10 -> end + 1
                    byte == 13 -> 1
                    else -> 0
                }
                if (end == 4) return out.toString("US-ASCII")
            }
            throw SocketTimeoutException("test request headers too long")
        }

        fun reply(socket: Socket, text: String) {
            socket.getOutputStream().write(text.toByteArray(StandardCharsets.US_ASCII))
            socket.getOutputStream().flush()
        }
    }
}
