package shop.whitezia.client.resolver

import android.net.ConnectivityManager
import android.net.Uri
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import shop.whitezia.client.controlplane.readUtf8Limited
import shop.whitezia.client.model.normalizeOperatorCode
import shop.whitezia.client.scan.WhiteZiaScannerResultStore

internal data class DnsDiscoveryNetwork(
    val network: android.net.Network?,
    val dnsResolvers: List<String>,
    val seedIps: List<String>,
)


internal class DnsResolverDiscovery {
    suspend fun discoverDnsResolvers(
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> = coroutineScope {
        if (discoveryNetworks.isEmpty()) {
            onLog("Local DNS discovery skipped: cellular сеть не найдена")
            return@coroutineScope emptyList()
        }
        val localDnsResolvers = discoveryNetworks
            .flatMap { it.dnsResolvers }
            .filter(::isCacheableLocalResolver)
            .distinct()
        if (localDnsResolvers.isEmpty()) {
            onLog("Локальный DNS не найден в cellular LinkProperties")
        } else {
            onLog("Локальный DNS: ${localDnsResolvers.joinToString()}")
        }

        val foundResolvers = linkedSetOf<String>()
        discoveryNetworks.forEach { discoveryNetwork ->
            discoveryNetwork.dnsResolvers.forEach { resolver ->
                if (
                    foundResolvers.size < TargetResolverCount &&
                    isCacheableLocalResolver(resolver) &&
                    probeDnsResolver(resolver, discoveryNetwork.network)
                ) {
                    foundResolvers += resolver
                }
            }
        }
        if (foundResolvers.size >= TargetResolverCount) {
            return@coroutineScope foundResolvers.take(TargetResolverCount)
        }

        val seedTargets = discoveryNetworks
            .flatMap { discoveryNetwork ->
                discoveryNetwork.seedIps.map { seedIp -> seedIp to discoveryNetwork.network }
            }
            .distinctBy { it.first }
        if (seedTargets.isEmpty()) {
            return@coroutineScope foundResolvers.toList()
        }

        val scannedPrefixes = linkedSetOf<String>()
        seedTargets.forEach { (seedIp, network) ->
            val subnetPrefixes = neighboringSubnetPrefixes(seedIp)
            subnetPrefixes.forEachIndexed { index, prefix ->
                if (foundResolvers.size >= TargetResolverCount || !scannedPrefixes.add(prefix)) {
                    return@forEachIndexed
                }
                onLog(
                    if (index == 0) {
                        "Сканирую подсеть $prefix.0/24 от $seedIp"
                    } else {
                        "Сканирую соседнюю подсеть $prefix.0/24 от $seedIp"
                    },
                )
                scanDnsSubnet(prefix, network, foundResolvers)
            }
        }
        foundResolvers.take(TargetResolverCount)
    }

    suspend fun discoverRegistryResolvers(
        operatorCode: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> {
        val candidates = fetchRegistryResolverCandidates(operatorCode, discoveryNetworks, onLog)
        if (candidates.isEmpty()) {
            onLog("База не вернула кандидатов для оператора")
            return emptyList()
        }
        onLog("Проверяю resolver'ы из базы: ${candidates.take(12).joinToString()}")
        val responsiveResolvers = probeResolverCandidates(
            candidates = candidates,
            discoveryNetworks = discoveryNetworks,
        )
        if (responsiveResolvers.isEmpty()) {
            onLog("Resolver'ы из базы не ответили в этой сети")
        } else {
            onLog("База дала рабочие resolver'ы: ${responsiveResolvers.joinToString()}")
        }
        return responsiveResolvers
    }

    private suspend fun probeResolverCandidates(
        candidates: List<String>,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
    ): List<String> = coroutineScope {
        val normalizedCandidates = WhiteZiaScannerResultStore.normalizeResolverEntries(candidates)
            .distinct()
            .take(RegistryResolverProbeLimit)
        val networks = discoveryNetworks.ifEmpty {
            listOf(DnsDiscoveryNetwork(network = null, dnsResolvers = emptyList(), seedIps = emptyList()))
        }
        val foundResolvers = linkedSetOf<String>()
        normalizedCandidates
            .filter(::isUsableIpv4Address)
            .chunked(DnsScanBatchSize)
            .forEach { batch ->
                if (foundResolvers.size >= TargetResolverCount) {
                    return@forEach
                }
                val responsiveResolvers = batch
                    .map { candidate ->
                        async(Dispatchers.IO) {
                            candidate.takeIf {
                                networks.any { discoveryNetwork ->
                                    probeDnsResolver(it, discoveryNetwork.network)
                                }
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                responsiveResolvers.forEach { resolver ->
                    if (foundResolvers.size < TargetResolverCount && isCacheableLocalResolver(resolver)) {
                        foundResolvers += resolver
                    }
                }
            }
        foundResolvers.toList()
    }

    private fun fetchRegistryResolverCandidates(
        operatorCode: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        onLog: (String) -> Unit,
    ): List<String> {
        return runCatching {
            val registryUrl = Uri.parse(ResolverRegistryUrl)
                .buildUpon()
                .appendQueryParameter("operator", normalizeOperatorCode(operatorCode))
                .appendQueryParameter("local_dns", discoveryNetworks.flatMap { it.dnsResolvers }.joinToString(","))
                .build()
                .toString()
            val connection = openRegistryConnection(registryUrl, discoveryNetworks).apply {
                requestMethod = "GET"
                connectTimeout = RegistryConnectTimeoutMillis
                readTimeout = RegistryReadTimeoutMillis
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", RegistryUserAgent)
            }
            try {
                connection.inputStream.use { input ->
                    parseRegistryResolvers(input.readUtf8Limited(MaxRegistryResponseBytes), operatorCode)
                }
            } finally {
                connection.disconnect()
            }

        }.getOrElse { error ->
            onLog("База resolver'ов недоступна: ${error.readableNetworkMessage()}")
            emptyList()
        }
    }

    private fun openRegistryConnection(
        rawUrl: String,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
    ): HttpURLConnection {
        val url = URL(rawUrl)
        val network = discoveryNetworks.firstOrNull { it.network != null }?.network
        return ((network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection)
    }

    internal fun parseRegistryResolvers(rawJson: String, operatorCode: String): List<String> {
        val root = JSONObject(rawJson)
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val resolvers = mutableListOf<String>()

        fun addArray(array: JSONArray?) {
            if (array == null) {
                return
            }
            repeat(array.length()) { index ->
                array.optString(index)
                    .takeIf(String::isNotBlank)
                    ?.let(resolvers::add)
            }
        }

        addArray(root.optJSONArray("resolvers"))
        val operators = root.optJSONObject("operators")
        val operatorValue = operators?.opt(normalizedOperatorCode)
        when (operatorValue) {
            is JSONArray -> addArray(operatorValue)
            is JSONObject -> {
                addArray(operatorValue.optJSONArray("resolvers"))
                addArray(operatorValue.optJSONArray("default"))
            }
        }
        addArray(root.optJSONArray("default"))

        return WhiteZiaScannerResultStore.normalizeResolverEntries(resolvers)
            .distinct()
            .take(RegistryResolverProbeLimit)
    }

    fun dnsDiscoveryNetworks(connectivityManager: ConnectivityManager): List<DnsDiscoveryNetwork> {
        val activeNetwork = connectivityManager.activeNetwork
        val networks = (listOfNotNull(activeNetwork) + connectivityManager.allNetworks.toList())
            .distinct()
            .sortedWith(
                compareByDescending<android.net.Network> { it == activeNetwork }
                    .thenByDescending { network ->
                        connectivityManager.getNetworkCapabilities(network)
                            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    },
            )
        return networks
            .mapNotNull { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
                val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                val isVpn = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (!isCellular || isVpn || !hasInternet) {
                    return@mapNotNull null
                }
                val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
                val dnsResolvers = linkProperties.dnsServers
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .filter(::isUsableIpv4Address)
                    .filter(::isCacheableLocalResolver)
                    .distinct()
                val linkAddresses = linkProperties.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .mapNotNull { it.hostAddress }
                    .filter(::isUsableIpv4Address)
                    .distinct()
                DnsDiscoveryNetwork(
                    network = network,
                    dnsResolvers = dnsResolvers,
                    seedIps = (dnsResolvers + linkAddresses).distinct(),
                )
            }
    }

    private suspend fun scanDnsSubnet(
        subnetPrefix: String,
        activeNetwork: android.net.Network?,
        foundResolvers: LinkedHashSet<String>,
    ) = coroutineScope {
        val candidates = (1..254)
            .map { host -> "$subnetPrefix.$host" }
            .filterNot { it in foundResolvers }
        candidates
            .chunked(DnsScanBatchSize)
            .forEach { batch ->
                if (foundResolvers.size >= TargetResolverCount) {
                    return@forEach
                }
                val responsiveResolvers = batch
                    .map { candidate ->
                        async(Dispatchers.IO) {
                            candidate.takeIf { probeDnsResolver(it, activeNetwork) }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                responsiveResolvers.forEach { resolver ->
                    if (foundResolvers.size < TargetResolverCount) {
                        foundResolvers += resolver
                    }
                }
            }
    }

    private fun probeDnsResolver(
        resolverIp: String,
        activeNetwork: android.net.Network?,
    ): Boolean {
        return runCatching {
            DatagramSocket().use { socket ->
                activeNetwork?.bindSocket(socket)
                socket.soTimeout = DnsProbeTimeoutMillis
                val query = buildDnsProbeQuery()
                val address = InetAddress.getByName(resolverIp)
                socket.send(DatagramPacket(query, query.size, address, 53))
                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                response.length >= 12 &&
                    buffer[0] == query[0] &&
                    buffer[1] == query[1] &&
                    (buffer[3].toInt() and 0x0F) == 0
            }
        }.getOrDefault(false)
    }

    private fun buildDnsProbeQuery(): ByteArray {
        val query = mutableListOf<Byte>()
        query += 0x42.toByte()
        query += 0x24.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x00.toByte()
        "cloudflare.com".split('.').forEach { label ->
            query += label.length.toByte()
            label.encodeToByteArray().forEach(query::add)
        }
        query += 0x00.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        query += 0x00.toByte()
        query += 0x01.toByte()
        return query.toByteArray()
    }

    private fun neighboringSubnetPrefixes(seedIp: String): List<String> {
        val octets = seedIp.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4) {
            return emptyList()
        }
        val first = octets[0]
        val second = octets[1]
        val third = octets[2]
        return buildList {
            add(third)
            for (distance in 1..NeighborSubnetDistance) {
                add(third - distance)
                add(third + distance)
            }
        }
            .filter { it in 0..255 }
            .distinct()
            .map { "$first.$second.$it" }
    }

    fun postRegistryResolvers(
        operatorCode: String,
        resolvers: List<String>,
        discoveryNetworks: List<DnsDiscoveryNetwork>,
        socksProxyPort: Int,
    ): String {
        val normalizedOperatorCode = normalizeOperatorCode(operatorCode)
        val normalizedResolvers = resolvers
            .mapNotNull(::registryReportableResolverOrNull)
            .distinct()
            .take(TargetResolverCount)
        if (normalizedResolvers.isEmpty()) {
            return "Registry report skipped: нет resolver'ов для отправки"
        }
        val payload = JSONObject()
            .put("operator", normalizedOperatorCode)
            .put("resolvers", JSONArray(normalizedResolvers))
            .put("local_dns", JSONArray(discoveryNetworks.flatMap { it.dnsResolvers }.distinct()))
            .put("seed_ips", JSONArray(discoveryNetworks.flatMap { it.seedIps }.distinct().take(16)))
        val socksProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", socksProxyPort),
        )
        val connection = (URL(ResolverRegistryReportUrl).openConnection(socksProxy) as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = RegistryConnectTimeoutMillis
            readTimeout = RegistryReadTimeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", RegistryUserAgent)
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                stream.use { it.readUtf8Limited(MaxRegistryResponseBytes) }
            }.getOrElse { error -> if (error is java.io.IOException) throw error else "" }
            if (responseCode !in 200..299) {
                error("HTTP $responseCode ${responseBody.take(120)}".trim())
            }
        } finally {
            connection.disconnect()
        }
        return "Registry report accepted via tunnel: operator=$normalizedOperatorCode, resolvers=${normalizedResolvers.joinToString()}"
    }


    private fun Throwable.readableNetworkMessage(): String = message?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    private companion object {
        const val ResolverRegistryUrl = "http://ns.alq.su/stormdns/resolvers"
        const val ResolverRegistryReportUrl = "http://ns.alq.su/stormdns/resolvers/report"
        const val RegistryConnectTimeoutMillis = 3_000
        const val RegistryReadTimeoutMillis = 4_000
        const val RegistryResolverProbeLimit = 64
        const val RegistryUserAgent = "CottenDNS-Android/1.0"
        const val DnsScanBatchSize = 32
        const val DnsProbeTimeoutMillis = 450
        const val NeighborSubnetDistance = 2
        const val TargetResolverCount = 4
        const val MaxRegistryResponseBytes = 256 * 1024
    }
}

internal fun registryReportableResolverOrNull(resolver: String): String? {
    val normalizedResolver = registryResolverHostOrNull(resolver) ?: return null
    if (normalizedResolver in ResolverDnsPolicy.Yandex || normalizedResolver == "114.114.114.114") {
        return null
    }
    val octets = normalizedResolver.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size != 4) {
        return null
    }
    if (octets[0] == 192 && octets[1] == 168) {
        return null
    }
    if (octets[0] == 172 && octets[1] in 16..31) {
        return null
    }
    return normalizedResolver.takeIf(::isUsableIpv4Address)
}

internal fun registryResolverHostOrNull(resolver: String): String? {
    val text = resolver.trim()
    val host = when {
        text.startsWith("[") && "]:" in text -> text.substringAfter("[").substringBefore("]")
        text.count { it == ':' } == 1 && "." in text -> text.substringBefore(":")
        else -> text
    }.trim()
    return host.takeIf(::isUsableIpv4Address)
}

internal fun isCacheableLocalResolver(resolver: String): Boolean {
    val host = registryResolverHostOrNull(resolver) ?: return false
    return host !in ResolverDnsPolicy.Public
}


internal fun isUsableIpv4Address(ip: String): Boolean {
    val octets = ip.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size != 4) {
        return false
    }
    return octets.all { it in 0..255 } &&
        octets[0] !in setOf(0, 127, 169, 224, 255) &&
        !(octets[0] == 169 && octets[1] == 254)
}

internal object ResolverDnsPolicy {
    val Yandex = listOf(
        "77.88.8.8",
        "77.88.8.1",
        "77.88.8.2",
        "77.88.8.3",
        "77.88.8.7",
        "77.88.8.88",
    )
    val Public = setOf(
        "1.1.1.1",
        "1.0.0.1",
        "8.8.8.8",
        "8.8.4.4",
        "9.9.9.9",
        "149.112.112.112",
        "208.67.222.222",
        "208.67.220.220",
        "114.114.114.114",
    ) + Yandex
}
