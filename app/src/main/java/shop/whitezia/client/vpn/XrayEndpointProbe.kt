package shop.whitezia.client.vpn

import android.net.Network
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import shop.whitezia.client.xray.XrayClientConfigParser
import kotlin.coroutines.resume

internal class XrayEndpointProbe(private val network: Network) {
    suspend fun probe(candidate: VpnConnectionCandidate): XrayRouteProbeResult {
        val config = XrayClientConfigParser.parseVlessUri(candidate.settings.xrayUri)
        return suspendCancellableCoroutine { continuation ->
            val cancelled = AtomicBoolean(false)
            val currentSocket = AtomicReference<Socket?>()
            val future = executor.submit {
                val started = System.nanoTime()
                var result = XrayRouteProbeResult(null, "unreachable")
                try {
                    val addresses = network.getAllByName(config.address)
                    for (address in addresses) {
                        if (cancelled.get()) break
                        val remaining = (3_000 - (System.nanoTime() - started) / 1_000_000).toInt()
                        if (remaining <= 0) break
                        try {
                            val socket = network.socketFactory.createSocket()
                            currentSocket.set(socket)
                            socket.use rawSocket@ { raw ->
                                if (cancelled.get()) return@rawSocket
                                raw.soTimeout = remaining
                                raw.connect(InetSocketAddress(address, config.port), remaining)
                                if (config.security == "tls") {
                                    val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                                        .createSocket(raw, config.serverName, config.port, true) as SSLSocket
                                    currentSocket.set(tls)
                                    tls.use tlsSocket@ {
                                        if (cancelled.get()) return@tlsSocket
                                        tls.soTimeout = (3_000 - (System.nanoTime() - started) / 1_000_000)
                                            .toInt().coerceAtLeast(1)
                                        tls.sslParameters = tls.sslParameters.apply {
                                            endpointIdentificationAlgorithm = "HTTPS"
                                            serverNames = listOf(SNIHostName(config.serverName))
                                        }
                                        tls.startHandshake()
                                    }
                                }
                                if (!cancelled.get()) result = XrayRouteProbeResult(
                                    (System.nanoTime() - started) / 1_000_000,
                                )
                            }
                            if (result.latencyMillis != null) break
                        } catch (error: Exception) {
                            result = XrayRouteProbeResult(null, error.javaClass.simpleName)
                        } finally {
                            currentSocket.getAndSet(null)?.runCatching { close() }
                        }
                    }
                } catch (error: Exception) {
                    result = XrayRouteProbeResult(null, error.javaClass.simpleName)
                }
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation {
                cancelled.set(true)
                currentSocket.getAndSet(null)?.runCatching { close() }
                future.cancel(true)
            }
        }
    }

    private companion object {
        // DNS can block on older Android. Limit workers and detach cancelled lookups from connection flow.
        val executor = Executors.newFixedThreadPool(4) { task ->
            Thread(task, "whitezia-route-probe").apply { isDaemon = true }
        }
    }
}
