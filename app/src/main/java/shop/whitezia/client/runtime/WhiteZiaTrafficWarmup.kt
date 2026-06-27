package shop.whitezia.client.runtime

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.math.min
import shop.whitezia.client.model.ResolvedWhiteZiaSettings

data class WhiteZiaThroughputProbeResult(
    val bytesRead: Long,
    val durationMillis: Long,
    val latencyMillis: Long,
) {
    val bytesPerSecond: Long
        get() = if (durationMillis > 0) {
            (bytesRead * 1_000L) / durationMillis
        } else {
            0L
        }
}

object WhiteZiaTrafficWarmup {
    fun runProbe(settings: ResolvedWhiteZiaSettings): Boolean {
        if (!settings.trafficWarmupEnabled) {
            return false
        }
        return runSocksHttpProbe(settings)
    }

    fun verifySocksRoute(settings: ResolvedWhiteZiaSettings): Boolean {
        return runSocksHttpProbe(settings)
    }

    fun measureDownloadThroughput(settings: ResolvedWhiteZiaSettings): WhiteZiaThroughputProbeResult? {
        return listOf(ThroughputProbeTarget, FallbackProbeTarget)
            .asSequence()
            .mapNotNull { target ->
                runCatching {
                    runSocksHttpDownloadProbe(settings, target)
                }.getOrNull()
            }
            .firstOrNull { it.bytesRead > 0L }
    }

    private fun runSocksHttpProbe(settings: ResolvedWhiteZiaSettings): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.soTimeout = SocketTimeoutMillis
                socket.tcpNoDelay = true
                socket.connect(
                    InetSocketAddress(selectLocalSocksHost(settings.listenIp), settings.listenPort),
                    SocketTimeoutMillis,
                )
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                if (!negotiateSocks(input, output, settings)) {
                    return@runCatching false
                }
                if (!connectToProbeTarget(input, output)) {
                    return@runCatching false
                }
                output.write(ProbeHttpRequest)
                output.flush()
                runCatching {
                    input.read(ByteArray(ProbeReadBufferSize))
                }
                true
            }
        }.getOrDefault(false)
    }

    private fun runSocksHttpDownloadProbe(
        settings: ResolvedWhiteZiaSettings,
        target: HttpProbeTarget,
    ): WhiteZiaThroughputProbeResult? {
        Socket().use { socket ->
            val connectStartedNanos = System.nanoTime()
            socket.soTimeout = ThroughputSocketTimeoutMillis
            socket.tcpNoDelay = true
            socket.connect(
                InetSocketAddress(selectLocalSocksHost(settings.listenIp), settings.listenPort),
                SocketTimeoutMillis,
            )
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            if (!negotiateSocks(input, output, settings)) {
                return null
            }
            val connected = if (target.useProbeIpv4Target) {
                connectToProbeTarget(input, output)
            } else {
                connectToTarget(input, output, target.host, target.port)
            }
            if (!connected) {
                return null
            }
            val latencyMillis = ((System.nanoTime() - connectStartedNanos) / 1_000_000L).coerceAtLeast(1L)
            val startedNanos = System.nanoTime()
            output.write(target.requestBytes())
            output.flush()
            val bytesRead = readUntilLimit(
                input = input,
                maxBytes = ThroughputProbeMaxBytes,
                maxDurationMillis = ThroughputProbeMaxDurationMillis,
            )
            val durationMillis = ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(1L)
            return if (bytesRead > 0L) {
                WhiteZiaThroughputProbeResult(
                    bytesRead = bytesRead,
                    durationMillis = durationMillis,
                    latencyMillis = latencyMillis,
                )
            } else {
                null
            }
        }
    }

    private fun negotiateSocks(
        input: InputStream,
        output: OutputStream,
        settings: ResolvedWhiteZiaSettings,
    ): Boolean {
        val method = if (settings.socks5Authentication) MethodUsernamePassword else MethodNoAuthentication
        output.write(byteArrayOf(SocksVersion, 1, method))
        output.flush()
        if (input.read() != SocksVersion.toInt()) {
            return false
        }
        return when (input.read()) {
            MethodNoAuthentication.toInt() -> true
            MethodUsernamePassword.toInt() -> authenticate(input, output, settings)
            else -> false
        }
    }

    private fun authenticate(
        input: InputStream,
        output: OutputStream,
        settings: ResolvedWhiteZiaSettings,
    ): Boolean {
        val username = settings.socksUsername.toByteArray(Charsets.UTF_8).limitedToSocksField()
        val password = settings.socksPassword.toByteArray(Charsets.UTF_8).limitedToSocksField()
        output.write(AuthVersion.toInt())
        output.write(username.size)
        output.write(username)
        output.write(password.size)
        output.write(password)
        output.flush()
        return input.read() == AuthVersion.toInt() && input.read() == AuthSuccess
    }

    private fun connectToTarget(
        input: InputStream,
        output: OutputStream,
        host: String,
        port: Int,
    ): Boolean {
        val hostBytes = host.toByteArray(Charsets.US_ASCII).limitedToSocksField()
        output.write(
            byteArrayOf(
                SocksVersion,
                CommandConnect,
                Reserved,
                AddressTypeDomain,
                hostBytes.size.toByte(),
            ),
        )
        output.write(hostBytes)
        output.write((port shr 8) and 0xFF)
        output.write(port and 0xFF)
        output.flush()
        return readSocksConnectReply(input)
    }

    private fun connectToProbeTarget(
        input: InputStream,
        output: OutputStream,
    ): Boolean {
        output.write(
            byteArrayOf(
                SocksVersion,
                CommandConnect,
                Reserved,
                AddressTypeIpv4,
                ProbeTargetIpA,
                ProbeTargetIpB,
                ProbeTargetIpC,
                ProbeTargetIpD,
                ProbeTargetPortHigh,
                ProbeTargetPortLow,
            ),
        )
        output.flush()
        return readSocksConnectReply(input)
    }

    private fun readSocksConnectReply(input: InputStream): Boolean {
        if (input.read() != SocksVersion.toInt()) {
            return false
        }
        val reply = input.read()
        if (input.read() < 0) {
            return false
        }
        val addressType = input.read()
        val addressLength = when (addressType) {
            AddressTypeIpv4.toInt() -> 4
            AddressTypeDomain.toInt() -> input.read().takeIf { it >= 0 } ?: return false
            AddressTypeIpv6.toInt() -> 16
            else -> return false
        }
        if (!readAndDiscard(input, addressLength + PortLength)) {
            return false
        }
        return reply == ReplySucceeded
    }

    private fun readUntilLimit(
        input: InputStream,
        maxBytes: Long,
        maxDurationMillis: Long,
    ): Long {
        val startedNanos = System.nanoTime()
        val buffer = ByteArray(ThroughputReadBufferSize)
        var total = 0L
        while (total < maxBytes) {
            val elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L
            if (elapsedMillis >= maxDurationMillis) {
                break
            }
            val nextReadSize = min(buffer.size.toLong(), maxBytes - total).toInt()
            val read = try {
                input.read(buffer, 0, nextReadSize)
            } catch (_: SocketTimeoutException) {
                break
            }
            if (read <= 0) {
                break
            }
            total += read
        }
        return total
    }

    private fun readAndDiscard(input: InputStream, length: Int): Boolean {
        repeat(length) {
            if (input.read() < 0) {
                return false
            }
        }
        return true
    }

    private fun ByteArray.limitedToSocksField(): ByteArray {
        return copyOf(min(size, MaxSocksFieldLength))
    }

    private fun selectLocalSocksHost(listenIp: String): String {
        return when (listenIp.trim().removeSurrounding("[", "]")) {
            "", "0.0.0.0" -> "127.0.0.1"
            "::" -> "::1"
            else -> listenIp.trim().removeSurrounding("[", "]")
        }
    }

    private data class HttpProbeTarget(
        val host: String,
        val port: Int,
        val path: String,
        val useProbeIpv4Target: Boolean = false,
    ) {
        fun requestBytes(): ByteArray {
            return (
                "GET $path HTTP/1.1\r\n" +
                    "Host: $host\r\n" +
                    "Connection: close\r\n" +
                    "User-Agent: WhiteZia/1\r\n" +
                    "\r\n"
                ).toByteArray(Charsets.US_ASCII)
        }
    }

    private const val SocketTimeoutMillis = 4_000
    private const val ThroughputSocketTimeoutMillis = 5_000
    private const val ThroughputProbeMaxBytes = 512L * 1024L
    private const val ThroughputProbeMaxDurationMillis = 5_000L
    private const val ProbeReadBufferSize = 256
    private const val ThroughputReadBufferSize = 8192
    private const val MaxSocksFieldLength = 255
    private const val PortLength = 2
    private const val AuthSuccess = 0
    private const val SocksVersion: Byte = 5
    private const val AuthVersion: Byte = 1
    private const val MethodNoAuthentication: Byte = 0
    private const val MethodUsernamePassword: Byte = 2
    private const val CommandConnect: Byte = 1
    private const val Reserved: Byte = 0
    private const val AddressTypeIpv4: Byte = 1
    private const val AddressTypeDomain: Byte = 3
    private const val AddressTypeIpv6: Byte = 4
    private const val ReplySucceeded = 0
    private const val ProbeTargetIpA: Byte = 1
    private const val ProbeTargetIpB: Byte = 1
    private const val ProbeTargetIpC: Byte = 1
    private const val ProbeTargetIpD: Byte = 1
    private const val ProbeTargetPortHigh: Byte = 0
    private const val ProbeTargetPortLow: Byte = 80
    private val ThroughputProbeTarget = HttpProbeTarget(
        host = "speed.cloudflare.com",
        port = 80,
        path = "/__down?bytes=$ThroughputProbeMaxBytes",
    )
    private val FallbackProbeTarget = HttpProbeTarget(
        host = "1.1.1.1",
        port = 80,
        path = "/cdn-cgi/trace",
        useProbeIpv4Target = true,
    )
    private val ProbeHttpRequest = (
        "HEAD / HTTP/1.1\r\n" +
            "Host: 1.1.1.1\r\n" +
            "Connection: close\r\n" +
            "User-Agent: WhiteZia/1\r\n" +
            "\r\n"
        ).toByteArray(Charsets.US_ASCII)
}
