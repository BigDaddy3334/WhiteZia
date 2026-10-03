package shop.whitezia.client.vpn

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.updateManualResolverText
import shop.whitezia.client.model.validateResolverText
import shop.whitezia.client.resolver.ResolverBenchmarkPolicy
import shop.whitezia.client.resolver.ResolverBenchmarkSchedule
import shop.whitezia.client.resolver.ResolverBenchmarkScore
import shop.whitezia.client.resolver.ResolverCacheStore
import shop.whitezia.client.runtime.ConnectionProbeClient

internal class ServiceResolverCoordinator(
    private val cache: ResolverCacheStore,
    private val measureScore: suspend (WhiteZiaSettings, String, (String) -> Unit) -> ResolverBenchmarkScore,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val physicalDnsResolvers: () -> List<String> = { emptyList() },
) {
    constructor(context: Context) : this(
        cache = ResolverCacheStore(context, incrementLaunchCount = false),
        measureScore = ServiceResolverMeasurement(context.applicationContext)::measure,
        physicalDnsResolvers = {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            connectivity?.physicalInternetNetwork()?.let { network ->
                connectivity.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }
            }.orEmpty()
        },
    )

    fun prepare(settings: WhiteZiaSettings): WhiteZiaSettings {
        if (settings.customResolversEnabled) {
            return settings.updateManualResolverText(normalize(settings.customResolverText).joinToString("\n"))
        }
        val excluded = normalize(settings.customResolverText).toSet()
        fun eligible(resolver: String) = isLocal(resolver) && resolver !in excluded
        val local = cache.readCachedResolvers(settings.operatorCode) { isLocal(it) && it !in excluded }
            .take(TargetResolverCount)
            .ifEmpty { localResolvers(settings) }
            .ifEmpty {
                normalize(physicalDnsResolvers().joinToString("\n")).filter(::eligible)
                    .take(TargetResolverCount).also { discovered ->
                        cache.mergeResolvers(discovered, settings.operatorCode, ::eligible)
                    }
            }
        if (local.isEmpty()) {
            return settings.updateManualResolverText(YandexResolvers.joinToString("\n"))
        }

        val winner = cache.readBenchmarkWinner(settings.operatorCode, local)
        val bucket = cache.readBenchmarkLastLaunchBucket(settings.operatorCode, local)
        if (winner != null && bucket == null) {
            // Older cached winners have no launch metadata; keep them until the next bucket.
            cache.markBenchmarkAttempted(settings.operatorCode, local)
        }
        val due = ResolverBenchmarkSchedule.isDue(
            cache.readBenchmarkLastLaunchBucket(settings.operatorCode, local),
            cache.launchCount,
        )
        val resolvers = if (due) local else when (winner) {
            WinnerYandex -> YandexResolvers
            WinnerLocal, null -> local
            else -> normalize(winner).filter { it in YandexResolvers || it !in excluded }.ifEmpty { local }
        }
        return settings.updateManualResolverText(resolvers.joinToString("\n"))
    }

    /** The prepared DNS candidate is running. Callbacks complete after readiness/shutdown or throw. */
    suspend fun optimize(
        settings: WhiteZiaSettings,
        start: suspend (WhiteZiaSettings) -> Unit,
        stop: suspend () -> Unit,
        log: (String) -> Unit = {},
    ): WhiteZiaSettings {
        if (settings.customResolversEnabled) return settings
        val local = localResolvers(settings)
        if (local.isEmpty()) return settings
        val operator = settings.operatorCode
        val bucket = cache.readBenchmarkLastLaunchBucket(operator, local)
        if (bucket == null && cache.readBenchmarkWinner(operator, local) != null) {
            cache.markBenchmarkAttempted(operator, local)
            return settings
        }
        if (!ResolverBenchmarkSchedule.isDue(bucket, cache.launchCount)) return settings
        val yandex = YandexResolvers

        suspend fun stopAndSettle() {
            currentCoroutineContext().ensureActive()
            stop()
            delayMillis(ReconnectDelayMillis)
            currentCoroutineContext().ensureActive()
        }

        suspend fun reconnect(next: WhiteZiaSettings) {
            stopAndSettle()
            start(next)
        }

        val localSettings = settings.updateManualResolverText(local.joinToString("\n"))
        cache.markBenchmarkAttempted(operator, local)
        if (normalize(settings.resolverText) != local) reconnect(localSettings)
        log("Resolver benchmark: comparing local and Yandex")
        val localScore = measureScore(localSettings, "Local DNS", log)

        suspend fun restoreLocal(error: Exception): WhiteZiaSettings {
            currentCoroutineContext().ensureActive()
            log("Yandex resolver failed: ${error.message ?: error.javaClass.simpleName}; restoring local DNS")
            reconnect(localSettings)
            if (ResolverBenchmarkPolicy.isReliableWinner(localScore)) {
                cache.saveBenchmarkWinner(operator, local, WinnerLocal, local)
            }
            return localSettings
        }

        delayMillis(SwitchDelayMillis)
        currentCoroutineContext().ensureActive()
        val yandexSettings = settings.updateManualResolverText(yandex.joinToString("\n"))
        stopAndSettle()
        val yandexScore = try {
            start(yandexSettings)
            measureScore(yandexSettings, "Yandex DNS", log)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return restoreLocal(error)
        }
        currentCoroutineContext().ensureActive()

        val decision = ResolverBenchmarkPolicy.decide(localScore, yandexScore)
        val preferYandex = decision.preferYandex
        val winnerSettings = if (preferYandex) yandexSettings else localSettings
        val winnerId = if (preferYandex) WinnerYandex else WinnerLocal
        log("Resolver benchmark winner: $winnerId")
        // Reconnect even when Yandex won so both outcomes use the winner transition.
        stopAndSettle()
        try {
            start(winnerSettings)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!preferYandex) throw error
            return restoreLocal(error)
        }
        if (preferYandex || ResolverBenchmarkPolicy.shouldCacheLocal(localScore, yandexScore)) {
            cache.saveBenchmarkWinner(operator, local, winnerId, if (preferYandex) yandex else local)
        }
        return winnerSettings
    }

    private fun localResolvers(settings: WhiteZiaSettings): List<String> {
        val excluded = normalize(settings.customResolverText).toSet()
        fun eligible(resolver: String) = isLocal(resolver) && resolver !in excluded
        return normalize(settings.resolverText).filter(::eligible).ifEmpty {
            cache.readBenchmarkLocalResolvers(settings.operatorCode).filter(::eligible).ifEmpty {
                cache.readCachedResolvers(settings.operatorCode, ::eligible).take(TargetResolverCount)
            }
        }
    }

    private fun isLocal(resolver: String): Boolean = runCatching {
        val host = resolverEndpoint(resolver).host.removeSurrounding("[", "]")
        val address = InetAddress.getByName(host)
        host !in PublicResolvers && !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress
    }.getOrDefault(false)

    private fun normalize(text: String): List<String> = validateResolverText(text).normalizedResolvers

    private companion object {
        const val TargetResolverCount = 4
        const val WinnerLocal = "local"
        const val WinnerYandex = "yandex"
        const val SwitchDelayMillis = 3_000L
        const val ReconnectDelayMillis = 3_000L
        val YandexResolvers = listOf("77.88.8.8", "77.88.8.1", "77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88")
        val PublicResolvers = setOf(
            "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220", "114.114.114.114",
        ) + YandexResolvers
    }
}

private class ServiceResolverMeasurement(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val probes = ConnectionProbeClient()

    suspend fun measure(settings: WhiteZiaSettings, label: String, log: (String) -> Unit): ResolverBenchmarkScore =
        withContext(Dispatchers.IO) {
            val port = settings.resolve().listenPort
            val health = probes.measureHttpHealthScore(log, "$label HTTP", socksProxyPort = port)
            currentCoroutineContext().ensureActive()
            val speed = probes.measureBenchmarkSpeed(log, label, socksProxyPort = port)
            currentCoroutineContext().ensureActive()
            val network = connectivity?.physicalInternetNetwork()
            var attempts = 0
            var successes = 0
            var latencySum = 0L
            for (resolver in validateResolverText(settings.resolverText).normalizedResolvers) {
                var resolverSuccesses = 0
                repeat(3) {
                    currentCoroutineContext().ensureActive()
                    attempts += 1
                    val latency = runCatching {
                        DatagramSocket().use { socket ->
                            network?.bindSocket(socket)
                            socket.soTimeout = 650
                            val query = DnsQuery
                            val endpoint = resolverEndpoint(resolver)
                            val host = endpoint.host.removeSurrounding("[", "]")
                            val dnsPort = endpoint.port.takeIf { it > 0 } ?: 53
                            socket.connect(InetAddress.getByName(host), dnsPort)
                            val started = System.nanoTime()
                            socket.send(DatagramPacket(query, query.size))
                            val response = DatagramPacket(ByteArray(512), 512)
                            socket.receive(response)
                            val bytes = response.data
                            val valid = response.length >= 12 && bytes[0] == query[0] && bytes[1] == query[1] &&
                                (bytes[2].toInt() and 0x80) != 0 && (bytes[3].toInt() and 0x0F) == 0
                            if (valid) ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(1L) else null
                        }
                    }.getOrNull()
                    if (latency != null) {
                        successes += 1
                        resolverSuccesses += 1
                        latencySum += latency
                    }
                }
                log("$label DNS $resolver: $resolverSuccesses/3")
            }
            ResolverBenchmarkScore(
                label = label,
                speedBytesPerSecond = speed.bestBytesPerSecond,
                speedSuccessfulSamples = speed.successfulSamples,
                healthSuccesses = health.successes,
                resolverSuccesses = successes,
                resolverAttempts = attempts,
                averageResolverLatencyMillis = if (successes > 0) latencySum / successes else 0L,
            )
        }

    private companion object {
        val DnsQuery = byteArrayOf(
            0x42, 0x24, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            10, 99, 108, 111, 117, 100, 102, 108, 97, 114, 101, 3, 99, 111, 109,
            0x00, 0x00, 0x01, 0x00, 0x01,
        )
    }
}

private fun resolverEndpoint(resolver: String): URI {
    val authority = if (resolver.count { it == ':' } > 1 && !resolver.startsWith('[')) "[$resolver]" else resolver
    return URI("dns://$authority")
}
