package shop.whitezia.client.runtime

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dedicated test CA only; no trust-all manager and no external network/DNS. */
class OwnedHttpTransportTlsTest {
    @Test(timeout = 8_000)
    fun trustedLocalhostCertificatePassesWithHostnameVerificationAndSni() {
        LocalTlsPeer(serverContext()).use { peer ->
            val result = request(peer, "localhost")
            assertEquals(200, result.status)
            assertEquals("tls-ok", result.body.toString(Charsets.US_ASCII))
            assertEquals(6L, result.bytesRead)
            assertTrue(result.complete)
            assertNull(result.failure)
            assertTrue(peer.finished.await(2, TimeUnit.SECONDS))
            assertNull(peer.failure.get())
            assertEquals("localhost", peer.socksHost.get())
            assertEquals("localhost", peer.sniHost.get())
        }
    }

    @Test(timeout = 8_000)
    fun trustedCaDoesNotAllowTheSameCertificateForTheWrongHostname() {
        LocalTlsPeer(serverContext()).use { peer ->
            val error = assertThrows(IOException::class.java) { request(peer, "wrong-host.invalid") }
            val chain = generateSequence(error as Throwable?) { it.cause }
                .joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message}" }
            assertTrue("the trusted chain must be rejected for hostname mismatch: $chain", chain.contains("wrong-host.invalid"))
            assertEquals("wrong-host.invalid", peer.socksHost.get())
        }
    }

    @Test(timeout = 8_000)
    fun maxMarkerChangesOnlySocksDestinationWhileTlsSniAndHttpHostStayReal() {
        LocalTlsPeer(serverContext("health-marker-server.p12")).use { peer ->
            val result = request(peer, "www.cloudflare.com", reserve = true)
            assertEquals(200, result.status)
            assertEquals("tls-ok", result.body.toString(Charsets.US_ASCII))
            assertTrue(result.complete)
            assertNull(result.failure)
            assertTrue(peer.finished.await(2, TimeUnit.SECONDS))
            assertNull(peer.failure.get())
            assertEquals("www.cloudflare.com.whitezia-health.invalid", peer.socksHost.get())
            assertEquals("www.cloudflare.com", peer.sniHost.get())
            assertTrue(peer.httpHeaders.get().contains("Host: www.cloudflare.com\r\n"))
            assertTrue(!peer.httpHeaders.get().contains("whitezia-health.invalid"))
        }
    }

    @Test(timeout = 8_000)
    fun maxReserveStillRejectsACertificateForTheWrongRealHostname() {
        LocalTlsPeer(serverContext()).use { peer ->
            assertThrows(IOException::class.java) { request(peer, "www.cloudflare.com", reserve = true) }
            assertEquals("www.cloudflare.com.whitezia-health.invalid", peer.socksHost.get())
        }
    }

    @Test(timeout = 8_000)
    fun ordinaryGoogleOrCloudflareTrafficDoesNotReceiveTheReserveMarker() {
        LocalTlsPeer(serverContext("health-marker-server.p12")).use { peer ->
            val response = request(peer, "www.cloudflare.com")
            assertEquals(200, response.status)
            assertEquals("www.cloudflare.com", peer.socksHost.get())
            assertEquals("www.cloudflare.com", peer.sniHost.get())
        }
    }

    private fun request(peer: LocalTlsPeer, host: String, reserve: Boolean = false): ProbeHttpResponse =
        OwnedHttpTransport(sslSocketFactory = clientContext().socketFactory).newCall(ProbeHttpRequest(
            url = "https://$host/health",
            socksProxyPort = peer.port,
            connectTimeoutMillis = 2_000,
            readTimeoutMillis = 2_000,
            totalTimeoutMillis = 4_000,
            maxBodyBytes = 64,
            socksDestinationHost = if (reserve) CottenMaxHealthDestination.forUrl(java.net.URL("https://$host/health")) else null,
        )).execute()

    private class LocalTlsPeer(private val context: SSLContext) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        private val current = AtomicReference<Socket?>()
        val port: Int get() = listener.localPort
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val socksHost = AtomicReference<String?>()
        val sniHost = AtomicReference<String?>()
        val httpHeaders = AtomicReference<String>("")
        private val worker = Thread({
            try {
                listener.accept().use { plain ->
                    current.set(plain)
                    plain.soTimeout = 3_000
                    val greeting = exact(plain, 2)
                    check(greeting[0].toInt() == 5)
                    check(exact(plain, greeting[1].toInt() and 255).contains(0))
                    plain.getOutputStream().write(byteArrayOf(5, 0))
                    check(exact(plain, 4).contentEquals(byteArrayOf(5, 1, 0, 3)))
                    val count = exact(plain, 1)[0].toInt() and 255
                    socksHost.set(exact(plain, count).toString(Charsets.US_ASCII))
                    check(exact(plain, 2).contentEquals(byteArrayOf(1, -69))) // port443
                    plain.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 1, -69))
                    val tls = context.socketFactory.createSocket(plain, "localhost", 443, true) as SSLSocket
                    tls.use {
                        tls.useClientMode = false
                        tls.soTimeout = 3_000
                        tls.startHandshake()
                        val session = tls.session as ExtendedSSLSession
                        sniHost.set(session.requestedServerNames.filterIsInstance<SNIHostName>().single().asciiName)
                        val headers = ByteArrayOutputStream()
                        while (!headers.toString("US-ASCII").endsWith("\r\n\r\n")) {
                            val byte = tls.getInputStream().read()
                            if (byte < 0) throw EOFException()
                            headers.write(byte)
                            check(headers.size() <= 16_384)
                        }
                        httpHeaders.set(headers.toString("US-ASCII"))
                        tls.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\ntls-ok".toByteArray(Charsets.US_ASCII))
                        tls.getOutputStream().flush()
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }, "owned-http-local-tls-fixture").apply { isDaemon = true; start() }

        override fun close() {
            listener.close()
            current.getAndSet(null)?.runCatching { close() }
            worker.join(1_000)
        }
    }

    private companion object {
        const val Password = "whitezia-http-test-fixture"
        const val Resources = "/shop/whitezia/client/runtime/tls-fixture/"

        fun serverContext(keystoreName: String = "localhost-server.p12"): SSLContext {
            val keys = KeyStore.getInstance("PKCS12")
            OwnedHttpTransportTlsTest::class.java.getResourceAsStream(Resources + keystoreName).use { stream ->
                checkNotNull(stream) { "missing dedicated test server keystore" }
                keys.load(stream, Password.toCharArray())
            }
            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            manager.init(keys, Password.toCharArray())
            return SSLContext.getInstance("TLS").apply { init(manager.keyManagers, null, null) }
        }

        fun clientContext(): SSLContext {
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val certificate = OwnedHttpTransportTlsTest::class.java.getResourceAsStream(Resources + "test-ca.crt").use { stream ->
                checkNotNull(stream) { "missing dedicated test CA certificate" }
                CertificateFactory.getInstance("X.509").generateCertificate(stream)
            }
            trust.setCertificateEntry("local-fixture-ca", certificate)
            val manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            manager.init(trust)
            return SSLContext.getInstance("TLS").apply { init(null, manager.trustManagers, null) }
        }

        fun exact(socket: Socket, length: Int): ByteArray {
            val value = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = socket.getInputStream().read(value, offset, length - offset)
                if (read < 0) throw EOFException()
                offset += read
            }
            return value
        }
    }
}
