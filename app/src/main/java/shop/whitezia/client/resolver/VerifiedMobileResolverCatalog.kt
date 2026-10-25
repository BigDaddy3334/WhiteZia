package shop.whitezia.client.resolver

import java.net.URI
import java.time.Instant
import org.json.JSONObject
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.CottenDnsResolverTransport
import shop.whitezia.client.model.WhiteZiaOptions

/** APK-owned data. An open port, an unsigned registry response, or a SIM alone is not proof. */
internal class VerifiedMobileResolverCatalog private constructor(private val entries: List<Entry>) {
    fun resolvers(operatorCode: String?, mode: String, transport: String, nowMillis: Long): List<String> {
        if (operatorCode !in Operators) return emptyList()
        // Catalog policy for Auto requires UDP proof, even when the engine can also try TCP.
        // TCP-only records remain available through explicit TCP; Max proof is always independent.
        val protocol = if (CottenDnsResolverTransport.fromStoredValue(transport) == CottenDnsResolverTransport.Tcp) "tcp" else "udp"
        val tunnelCheck = if (CottenDnsMode.normalize(mode) == CottenDnsMode.Max) "cottenmax-tunnel" else "normal-tunnel"
        return entries.asSequence()
            .filter { it.operator == operatorCode && nowMillis in it.verifiedAt until it.expiresAt }
            .filter { "dns-recursion:$protocol" in it.checks && "$tunnelCheck:$protocol" in it.checks }
            .map(Entry::address).distinct().take(4).toList()
    }

    private data class Entry(
        val operator: String,
        val address: String,
        val verifiedAt: Long,
        val expiresAt: Long,
        val checks: Set<String>,
    )

    companion object {
        const val AssetName = "verified-mobile-resolvers.json"
        const val MaxBytes = 256 * 1024
        val Empty = VerifiedMobileResolverCatalog(emptyList())
        private const val MaxAgeMillis = 30L * 24 * 60 * 60 * 1000
        private const val FutureToleranceMillis = 5L * 60 * 1000
        private val Operators = setOf(WhiteZiaOptions.OperatorMts, WhiteZiaOptions.OperatorBeeline,
            WhiteZiaOptions.OperatorMegafonYota, WhiteZiaOptions.OperatorTele2)
        private val CheckKinds = setOf("dns-recursion", "normal-tunnel", "cottenmax-tunnel")

        fun parse(raw: String, nowMillis: Long): VerifiedMobileResolverCatalog = runCatching {
            require(raw.toByteArray(Charsets.UTF_8).size <= MaxBytes)
            val root = JSONObject(raw)
            require(root.getInt("schemaVersion") == 1)
            val generatedAt = timestamp(root.getString("generatedAt"))
            val expiresAt = timestamp(root.getString("expiresAt"))
            require(generatedAt >= 0 && generatedAt <= nowMillis + FutureToleranceMillis)
            require(expiresAt > generatedAt && expiresAt - generatedAt <= MaxAgeMillis && nowMillis < expiresAt)
            val operators = root.getJSONArray("operators")
            require(operators.length() <= 32)
            val entries = mutableListOf<Entry>()
            repeat(operators.length()) { operatorIndex ->
                val operator = operators.optJSONObject(operatorIndex) ?: return@repeat
                val code = operator.optString("code")
                if (code !in Operators) return@repeat
                val asnArray = operator.optJSONArray("asns") ?: return@repeat
                val asns = (0 until asnArray.length()).map { asnArray.optLong(it) }.filter { it in 1..4_294_967_295L }.toSet()
                val resolvers = operator.optJSONArray("resolvers") ?: return@repeat
                require(resolvers.length() <= 1024)
                repeat(resolvers.length()) resolverLoop@ { resolverIndex ->
                    val resolver = resolvers.optJSONObject(resolverIndex) ?: return@resolverLoop
                    runCatching {
                        val address = resolver.getString("address")
                        val ip = publicIpv4Number(address) ?: return@runCatching
                        require(resolver.getInt("port") == 53)
                        val source = resolver.getJSONObject("source")
                        require(source.getString("kind") == "bgp-origin-masscan")
                        val asn = source.getLong("asn")
                        require(asn in asns)
                        val url = URI(source.getString("url"))
                        require(url.scheme == "https" && url.host == "bgp.he.net" && url.userInfo == null &&
                            url.port == -1 && url.path == "/AS$asn")
                        val prefixSource = URI(source.getString("prefixSourceUrl"))
                        require(prefixSource.scheme == "https" && prefixSource.host == "stat.ripe.net" &&
                            prefixSource.userInfo == null && prefixSource.port == -1 &&
                            prefixSource.path == "/data/ris-prefixes/data.json")
                        val sourceQuery = prefixSource.rawQuery.orEmpty().split('&').associate {
                            it.substringBefore('=') to it.substringAfter('=', "")
                        }
                        require(sourceQuery["resource"] == "AS$asn" && sourceQuery["types"] == "o" && sourceQuery["af"] == "v4")
                        require(source.getString("prefixSnapshotSha256").matches(Regex("[a-f0-9]{64}")))
                        require(prefixContains(source.getString("prefix"), ip))
                        val verifiedAt = timestamp(resolver.getString("verifiedAt"))
                        val entryExpiresAt = timestamp(resolver.getString("expiresAt"))
                        require(verifiedAt <= generatedAt && verifiedAt <= nowMillis)
                        require(entryExpiresAt > verifiedAt && entryExpiresAt - verifiedAt <= MaxAgeMillis)
                        require(entryExpiresAt <= expiresAt)
                        val checks = resolver.getJSONArray("checks")
                        require(checks.length() <= 6)
                        val successful = buildSet {
                            repeat(checks.length()) { checkIndex ->
                                val check = checks.optJSONObject(checkIndex) ?: return@repeat
                                val kind = check.optString("kind")
                                val protocol = check.optString("transport")
                                if (kind in CheckKinds && protocol in setOf("udp", "tcp") &&
                                    check.opt("passed") == true) add("$kind:$protocol")
                            }
                        }
                        entries += Entry(code, address, verifiedAt, entryExpiresAt, successful)
                    }
                }
            }
            VerifiedMobileResolverCatalog(entries)
        }.getOrDefault(Empty)

        private fun timestamp(raw: String): Long {
            require(raw.endsWith('Z'))
            return Instant.parse(raw).toEpochMilli()
        }

        private fun ipv4Number(raw: String): Long? {
            val octets = raw.split('.')
            if (octets.size != 4 || octets.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } ||
                    (it.length > 1 && it.startsWith('0')) }) return null
            val values = octets.map { it.toIntOrNull() ?: return null }
            if (values.any { it !in 0..255 }) return null
            return values.fold(0L) { value, octet -> (value shl 8) or octet.toLong() }
        }

        internal fun publicIpv4Number(raw: String): Long? {
            val ip = ipv4Number(raw) ?: return null
            val a = ((ip ushr 24) and 255).toInt()
            val b = ((ip ushr 16) and 255).toInt()
            val c = ((ip ushr 8) and 255).toInt()
            if (a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 100 && b in 64..127) || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 192 && b == 0 && (c == 0 || c == 2)) || (a == 192 && b == 88 && c == 99) ||
                (a == 198 && b in 18..19) || (a == 198 && b == 51 && c == 100) ||
                (a == 203 && b == 0 && c == 113)) return null
            return ip
        }

        private fun prefixContains(prefix: String, ip: Long): Boolean {
            val pieces = prefix.split('/')
            if (pieces.size != 2) return false
            val network = ipv4Number(pieces[0]) ?: return false
            val bits = pieces[1].toIntOrNull()?.takeIf { it in 1..32 } ?: return false
            val mask = (0xffff_ffffL shl (32 - bits)) and 0xffff_ffffL
            return network and mask == network && ip and mask == network
        }
    }
}
