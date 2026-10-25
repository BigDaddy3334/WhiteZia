package shop.whitezia.client.runtime

import java.util.Locale
import java.util.UUID
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class HealthProbeSummary(
    val successes: Int,
    val strongSuccesses: Int,
    val relayEgressSuccesses: Int = 0,
) {
    fun isHealthy(requireRelayEgress: Boolean, requireDnsContent: Boolean = false): Boolean = when {
        requireRelayEgress -> successes >= 2 && relayEgressSuccesses >= 1
        requireDnsContent -> successes >= 2 && strongSuccesses >= 1
        else -> successes >= 1
    }
}

internal enum class HealthResponseKind { Connectivity204, CloudflareTrace, Generic }

internal fun acceptsHealthResponse(kind: HealthResponseKind, status: Int, body: String = ""): Boolean = when (kind) {
    HealthResponseKind.Connectivity204 -> status == 204
    HealthResponseKind.CloudflareTrace -> status == 200 && body.lineSequence().any { line ->
        val address = line.trim().removePrefix("ip=")
        line.startsWith("ip=") && (address.split('.').let { parts ->
            parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
        } || (address.contains(':') && address.all { it in "0123456789abcdefABCDEF:" }))
    }
    HealthResponseKind.Generic -> status in 200..399
}

internal data class SpeedBenchmarkSummary(
    val bestBytesPerSecond: Long,
    val successfulSamples: Int,
)

internal class ConnectionProbeClient {
    private val transport = OwnedHttpTransport()
    val healthEndpointCount: Int
        get() = HealthEndpoints.size

    suspend fun measureCloudflareSpeed(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
    ): Long = measureCloudflare(
        onLog = onLog,
        socksProxyPort = socksProxyPort,
        downloadBytes = SpeedDownloadBytes,
        attempts = SpeedAttempts,
        connectTimeoutMillis = SpeedConnectTimeoutMillis,
        readTimeoutMillis = SpeedReadTimeoutMillis,
        logPrefix = "Cloudflare попытка",
        useMbpsLog = true,
    )

    suspend fun measurePostConnectSpeed(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
        downloadBytes: List<Long> = PostConnectDownloadBytes,
        attempts: Int = PostConnectAttempts,
        connectTimeoutMillis: Int = PostConnectConnectTimeoutMillis,
        readTimeoutMillis: Int = PostConnectReadTimeoutMillis,
        logPrefix: String = "Cloudflare check",
    ): Long = measureCloudflare(
        onLog = onLog,
        socksProxyPort = socksProxyPort,
        downloadBytes = downloadBytes,
        attempts = attempts,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        logPrefix = logPrefix,
        useMbpsLog = false,
    )

    suspend fun measureBenchmarkSpeed(
        onLog: (String) -> Unit,
        logPrefix: String,
        socksProxyPort: Int? = null,
    ): SpeedBenchmarkSummary {
        var bestSpeed = 0L
        var successfulSamples = 0
        BenchmarkDownloadBytes.forEachIndexed { sampleIndex, bytes ->
            val result = measureDownload(
                downloadUrl = cloudflareUrl(bytes),
                maxBytes = bytes,
                socksProxyPort = socksProxyPort,
                connectTimeoutMillis = BenchmarkConnectTimeoutMillis,
                readTimeoutMillis = BenchmarkReadTimeoutMillis,
            )
            if (result.bytesPerSecond > 0L) {
                successfulSamples += 1
                bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                val suffix = if (result.message == "ok") "" else " (${result.message})"
                onLog(
                    "$logPrefix Cloudflare sample ${sampleIndex + 1}/${BenchmarkDownloadBytes.size}: " +
                        "${formatTrafficSpeed(result.bytesPerSecond)}$suffix",
                )
            } else {
                onLog("$logPrefix Cloudflare sample ${sampleIndex + 1}/${BenchmarkDownloadBytes.size}: ${result.message}")
            }
        }
        return SpeedBenchmarkSummary(bestSpeed, successfulSamples)
    }

    suspend fun isHttpHealthy(
        onLog: (String) -> Unit,
        socksProxyPort: Int? = null,
        requireRelayEgress: Boolean = false,
        requireDnsContent: Boolean = false,
        useCottenMaxHealthReserve: Boolean = false,
    ): Boolean = measureHttpHealthScore(
        onLog = onLog,
        logPrefix = "HTTP",
        socksProxyPort = socksProxyPort,
        useCottenMaxHealthReserve = useCottenMaxHealthReserve,
    ).isHealthy(requireRelayEgress, requireDnsContent)

    suspend fun measureHttpHealthScore(
        onLog: (String) -> Unit,
        logPrefix: String,
        connectTimeoutMillis: Int = HealthConnectTimeoutMillis,
        readTimeoutMillis: Int = HealthReadTimeoutMillis,
        socksProxyPort: Int? = null,
        useCottenMaxHealthReserve: Boolean = false,
    ): HealthProbeSummary = coroutineScope {
        require(!useCottenMaxHealthReserve || socksProxyPort != null) { "CottenMax health reserve requires a SOCKS listener" }
        val results = HealthEndpoints.map { endpoint ->
            async {
                endpoint to checkHealthEndpoint(
                    endpoint,
                    connectTimeoutMillis,
                    readTimeoutMillis,
                    socksProxyPort,
                    useCottenMaxHealthReserve,
                )
            }
        }.awaitAll()
        results.forEach { (endpoint, result) -> onLog("$logPrefix ${endpoint.label}: ${result.message}") }
        HealthProbeSummary(
            successes = results.count { it.second.ok },
            strongSuccesses = results.count { (endpoint, result) -> endpoint.strongSignal && result.ok },
            relayEgressSuccesses = results.count { (endpoint, result) -> endpoint.relayEgress && result.ok },
        )
    }

    private suspend fun measureCloudflare(
        onLog: (String) -> Unit,
        socksProxyPort: Int?,
        downloadBytes: List<Long>,
        attempts: Int,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        logPrefix: String,
        useMbpsLog: Boolean,
    ): Long {
        var bestSpeed = 0L
        repeat(attempts) { attemptIndex ->
            downloadBytes.forEachIndexed { sizeIndex, bytes ->
                val result = measureDownload(
                    downloadUrl = cloudflareUrl(bytes),
                    maxBytes = bytes,
                    socksProxyPort = socksProxyPort,
                    connectTimeoutMillis = connectTimeoutMillis,
                    readTimeoutMillis = readTimeoutMillis,
                )
                if (result.bytesPerSecond > 0L) {
                    bestSpeed = maxOf(bestSpeed, result.bytesPerSecond)
                    val speed = if (useMbpsLog) {
                        "%.2f Мбит/с".format(Locale.US, result.bytesPerSecond * 8.0 / 1_000_000.0)
                    } else {
                        formatTrafficSpeed(result.bytesPerSecond)
                    }
                    val suffix = if (result.message == "ok") "" else " (${result.message})"
                    onLog("$logPrefix ${attemptIndex + 1}.${sizeIndex + 1}: $speed$suffix")
                    return bestSpeed
                }
                onLog("$logPrefix ${attemptIndex + 1}.${sizeIndex + 1}: ${result.message}")
            }
        }
        return bestSpeed
    }

    private suspend fun checkHealthEndpoint(
        endpoint: HealthEndpoint,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        socksProxyPort: Int?,
        useCottenMaxHealthReserve: Boolean,
    ): HealthProbeResult {
        return try {
            val bodyLimit = if (endpoint.kind == HealthResponseKind.CloudflareTrace) 4096 else 0
            val response = transport.newCall(ProbeHttpRequest(
                url = endpoint.url, socksProxyPort = socksProxyPort,
                connectTimeoutMillis = connectTimeoutMillis, readTimeoutMillis = readTimeoutMillis,
                totalTimeoutMillis = connectTimeoutMillis.toLong() + readTimeoutMillis,
                maxBodyBytes = bodyLimit.toLong(), captureBodyBytes = bodyLimit,
                headers = mapOf("User-Agent" to HealthUserAgent, "Cache-Control" to "no-cache"),
                socksDestinationHost = if (useCottenMaxHealthReserve) CottenMaxHealthDestination.forUrl(URL(endpoint.url)) else null,
            )).await()
            if (response.failure != null) return HealthProbeResult(false, response.failure)
            val accepted = acceptsHealthResponse(endpoint.kind, response.status, response.body.toString(Charsets.UTF_8))
            HealthProbeResult(accepted, "HTTP ${response.status}" + if (accepted) "" else " (unexpected probe response)")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            HealthProbeResult(false, error.readableMessage())
        }
    }

    private suspend fun measureDownload(
        downloadUrl: String,
        maxBytes: Long,
        socksProxyPort: Int?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): SpeedProbeResult {
        val response = try {
            transport.newCall(ProbeHttpRequest(
                url = downloadUrl, socksProxyPort = socksProxyPort,
                connectTimeoutMillis = connectTimeoutMillis, readTimeoutMillis = readTimeoutMillis,
                totalTimeoutMillis = connectTimeoutMillis.toLong() + readTimeoutMillis,
                maxBodyBytes = maxBytes, captureBodyBytes = 0,
                headers = mapOf("User-Agent" to SpeedUserAgent, "Cache-Control" to "no-cache"),
            )).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return SpeedProbeResult(0L, error.readableMessage())
        }
        if (response.status !in 200..299) return SpeedProbeResult(0L, "HTTP ${response.status}")
        if (response.bytesRead <= 0L) return SpeedProbeResult(0L, response.failure ?: "скачано 0 байт")
        val speed = response.bytesRead * 1000L / response.elapsedMillis.coerceAtLeast(1L)
        return SpeedProbeResult(speed, response.failure?.let { "partial: $it" } ?: "ok")
    }

    private fun cloudflareUrl(bytes: Long) =
        "https://speed.cloudflare.com/__down?bytes=$bytes&cacheBust=${UUID.randomUUID()}"

    private fun Throwable.readableMessage() = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private data class HealthEndpoint(
        val label: String,
        val url: String,
        val strongSignal: Boolean = true,
        val kind: HealthResponseKind = HealthResponseKind.Generic,
        val relayEgress: Boolean = false,
    )
    private data class HealthProbeResult(val ok: Boolean, val message: String)
    private data class SpeedProbeResult(val bytesPerSecond: Long, val message: String)

    private companion object {
        val SpeedDownloadBytes = listOf(2_000_000L, 5_000_000L, 10_000_000L)
        const val SpeedAttempts = 3
        const val SpeedConnectTimeoutMillis = 8_000
        const val SpeedReadTimeoutMillis = 30_000
        val BenchmarkDownloadBytes = listOf(512_000L)
        const val BenchmarkConnectTimeoutMillis = 6_000
        const val BenchmarkReadTimeoutMillis = 15_000
        val PostConnectDownloadBytes = listOf(512_000L, 1_000_000L)
        const val PostConnectAttempts = 1
        const val PostConnectConnectTimeoutMillis = 6_000
        const val PostConnectReadTimeoutMillis = 8_000
        const val HealthConnectTimeoutMillis = 4_000
        const val HealthReadTimeoutMillis = 5_000
        const val SpeedUserAgent = "CottenDNS-Android/1.0"
        const val HealthUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        val HealthEndpoints = listOf(
            HealthEndpoint("Android 204", "https://connectivitycheck.gstatic.com/generate_204", false, HealthResponseKind.Connectivity204),
            HealthEndpoint("Google 204", "https://www.google.com/generate_204", false, HealthResponseKind.Connectivity204, true),
            HealthEndpoint("Cloudflare trace", "https://www.cloudflare.com/cdn-cgi/trace", kind = HealthResponseKind.CloudflareTrace, relayEgress = true),
            HealthEndpoint("Yandex", "https://ya.ru/"),
        )
    }
}
