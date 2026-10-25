package shop.whitezia.client.vpn

import java.net.InetAddress
import shop.whitezia.client.model.validateResolverText

/** One physical-network snapshot; cached private addresses never identify a different network. */
internal class AutomaticResolverSeeds(physicalDns: List<String>, excludedResolverText: String) {
    val physical = normalize(physicalDns).filter(::usable).distinct().take(PrimaryLimit)
    private val physicalEndpoints = physical.toSet()
    private val excluded = normalize(listOf(excludedResolverText)).toSet()

    fun eligibleAutomatic(resolver: String): Boolean {
        val endpoint = normalize(listOf(resolver)).singleOrNull() ?: return false
        if (!usable(endpoint)) return false
        if (endpoint in physicalEndpoints) return true
        if (endpoint in excluded && endpoint !in FallbackResolvers) return false
        return !networkScoped(endpoint)
    }

    fun eligiblePublic(resolver: String): Boolean {
        val endpoint = normalize(listOf(resolver)).singleOrNull() ?: return false
        return eligibleAutomatic(endpoint) && endpoint !in physicalEndpoints && !networkScoped(endpoint)
    }

    fun publicPool(preferred: List<String>): List<String> {
        val primary = normalize(preferred).filter(::eligiblePublic).filterNot { it in FallbackResolvers }
        val fallback = FallbackResolvers.filter(::eligiblePublic)
        return if (primary.isEmpty()) fallback.take(TotalLimit)
        else (primary.take(PrimaryLimit) + fallback.take(2)).distinct().take(TotalLimit)
    }

    /** Native adaptive MTU selection must never promote public DNS over a working physical pool. */
    fun operatorPool(verified: List<String>): List<String> =
        normalize(verified).filter(::eligiblePublic).distinct().take(PrimaryLimit)

    fun pools(preferred: List<String>, verifiedOperator: List<String> = emptyList()): List<List<String>> {
        val operator = operatorPool(verifiedOperator)
        // Startup readiness decides when to leave each pool. Do not let native MTU ranking mix tiers.
        return listOf(physical, operator, publicPool(normalize(preferred).filterNot(operator::contains)))
            .filter(List<String>::isNotEmpty).distinct()
    }

    private fun address(endpoint: String): InetAddress {
        val host = when {
            endpoint.startsWith('[') -> endpoint.substringAfter('[').substringBefore(']')
            endpoint.count { it == ':' } == 1 -> endpoint.substringBefore(':')
            else -> endpoint
        }.substringBefore('/')
        // validateResolverText has already accepted a numeric literal; this never performs name lookup.
        return InetAddress.getByName(host)
    }

    private fun usable(endpoint: String): Boolean = runCatching {
        // AUTO uses a bounded fleet of individual endpoints; explicit custom/scan CIDRs stay supported.
        if ('/' in endpoint) return@runCatching false
        val ip = address(endpoint)
        !ip.isAnyLocalAddress && !ip.isLoopbackAddress && !ip.isMulticastAddress &&
            !(ip.address.size == 4 && (ip.address[0].toInt() and 255) >= 224)
    }.getOrDefault(false)

    private fun networkScoped(endpoint: String): Boolean = runCatching {
        val ip = address(endpoint)
        val bytes = ip.address
        ip.isSiteLocalAddress || ip.isLinkLocalAddress ||
            (bytes.size == 4 && (bytes[0].toInt() and 255) == 100 && (bytes[1].toInt() and 255) in 64..127) ||
            (bytes.size == 16 && (bytes[0].toInt() and 254) == 252)
    }.getOrDefault(true)

    private fun normalize(values: List<String>) = validateResolverText(values.joinToString("\n")).normalizedResolvers

    companion object {
        private const val PrimaryLimit = 4
        private const val TotalLimit = 6
        val FallbackResolvers = listOf("77.88.8.8", "77.88.8.1", "77.88.8.2", "77.88.8.3", "77.88.8.7", "77.88.8.88")
    }
}
