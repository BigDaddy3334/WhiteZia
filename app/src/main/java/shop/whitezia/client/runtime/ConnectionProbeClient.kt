package shop.whitezia.client.runtime

import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class HealthProbeSummary(
    val successes: Int,
    val strongSuccesses: Int,
)

internal data class SpeedBenchmarkSummary(
    val bestBytesPerSecond: Long,
    val successfulSamples: Int,
)

internal class ConnectionProbeClient {
    val healthEndpointCount: Int
        get() = HealthEndpoints.size

    fun measureCloudflareSpeed(
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

    fun measurePostConnectSpeed(
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

    fun measureBenchmarkSpeed(
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
    ): Boolean = measureHttpHealthScore(
        onLog = onLog,
        logPrefix = "HTTP",
        socksProxyPort = socksProxyPort,
    ).successes >= HealthSuccessThreshold

    suspend fun measureHttpHealthScore(
        onLog: (String) -> Unit,
        logPrefix: String,
        connectTimeoutMillis: Int = HealthConnectTimeoutMillis,
        readTimeoutMillis: Int = HealthReadTimeoutMillis,
        socksProxyPort: Int? = null,
    ): HealthProbeSummary = coroutineScope {
        val results = HealthEndpoints.map { endpoint ->
            async(Dispatchers.IO) {
                endpoint to checkHealthEndpoint(
                    endpoint,
                    connectTimeoutMillis,
                    readTimeoutMillis,
                    socksProxyPort,
                )
            }
        }.awaitAll()
        results.forEach { (endpoint, result) -> onLog("$logPrefix ${endpoint.label}: ${result.message}") }
        HealthProbeSummary(
            successes = results.count { it.second.ok },
            strongSuccesses = results.count { (endpoint, result) -> endpoint.strongSignal && result.ok },
        )
    }

    private fun measureCloudflare(
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

    private fun checkHealthEndpoint(
        endpoint: HealthEndpoint,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        socksProxyPort: Int?,
    ): HealthProbeResult {
        val connection = runCatching {
            openConnection(endpoint.url, socksProxyPort).apply {
                instanceFollowRedirects = true
                useCaches = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                setRequestProperty("User-Agent", HealthUserAgent)
                setRequestProperty("Cache-Control", "no-cache")
            }
        }.getOrElse { return HealthProbeResult(false, it.readableMessage()) }
        return try {
            val responseCode = connection.responseCode
            HealthProbeResult(responseCode in 200..399, "HTTP $responseCode")
        } catch (error: Exception) {
            HealthProbeResult(false, error.readableMessage())
        } finally {
            connection.disconnect()
        }
    }

    private fun measureDownload(
        downloadUrl: String,
        maxBytes: Long,
        socksProxyPort: Int?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): SpeedProbeResult {
        val startedAt = System.currentTimeMillis()
        var bytesRead = 0L
        var readError: Throwable? = null
        val connection = runCatching {
            openConnection(downloadUrl, socksProxyPort).apply {
                useCaches = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("User-Agent", SpeedUserAgent)
                setRequestProperty("Cache-Control", "no-cache")
            }
        }.getOrElse { return SpeedProbeResult(0L, it.readableMessage()) }
        try {
            runCatching {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) return SpeedProbeResult(0L, "HTTP $responseCode")
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (bytesRead < maxBytes) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        bytesRead += read
                    }
                }
            }.onFailure { readError = it }
        } finally {
            connection.disconnect()
        }
        if (bytesRead <= 0L) return SpeedProbeResult(0L, readError?.readableMessage() ?: "скачано 0 байт")
        val speed = bytesRead * 1000L / (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        return readError?.let { SpeedProbeResult(speed, "partial: ${it.readableMessage()}") }
            ?: SpeedProbeResult(speed, "ok")
    }

    private fun openConnection(url: String, socksProxyPort: Int?): HttpURLConnection {
        val proxy = socksProxyPort?.let { Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it)) }
        return ((proxy?.let { URL(url).openConnection(it) } ?: URL(url).openConnection()) as HttpURLConnection)
    }

    private fun cloudflareUrl(bytes: Long) =
        "https://speed.cloudflare.com/__down?bytes=$bytes&cacheBust=${UUID.randomUUID()}"

    private fun Throwable.readableMessage() = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private data class HealthEndpoint(val label: String, val url: String, val strongSignal: Boolean = true)
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
        const val HealthSuccessThreshold = 1
        const val HealthConnectTimeoutMillis = 4_000
        const val HealthReadTimeoutMillis = 5_000
        const val SpeedUserAgent = "StormDNS-Android/1.0"
        const val HealthUserAgent =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        val HealthEndpoints = listOf(
            HealthEndpoint("Android 204", "https://connectivitycheck.gstatic.com/generate_204", false),
            HealthEndpoint("Google 204", "https://www.google.com/generate_204", false),
            HealthEndpoint("Cloudflare trace", "https://www.cloudflare.com/cdn-cgi/trace"),
            HealthEndpoint("Yandex", "https://ya.ru/"),
        )
    }
}
