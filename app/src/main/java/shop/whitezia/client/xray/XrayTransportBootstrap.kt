package shop.whitezia.client.xray

import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale
import org.json.JSONObject

data class XrayTransportEndpoint(val host: String, val port: Int)

/** Resolves only the outer transport. Destination domains inside SOCKS remain remote DNS. */
internal object XrayTransportBootstrap {
    fun endpoints(client: XrayClientConfig): List<XrayTransportEndpoint> = buildList {
        add(endpoint(client.address, client.port))
        visitDownloads(client.xhttpExtra) { stream ->
            val address = stream.optString("address", "").trim()
            require(address.isNotEmpty()) { "Xray download transport address is missing" }
            add(endpoint(address, stream.optInt("port", 0)))
        }
    }.distinct()

    fun resolve(
        client: XrayClientConfig,
        lookup: (String) -> List<InetAddress>,
        canConnect: (InetAddress, Int) -> Boolean,
    ): Map<XrayTransportEndpoint, String> {
        val answers = mutableMapOf<String, List<InetAddress>>()
        return endpoints(client).associateWith { endpoint ->
            literalAddress(endpoint.host)?.let { address ->
                // An explicit profile literal is intentional, including local/LAN
                // test endpoints. It needs neither DNS nor a network-bound probe.
                require(!isSyntheticAddress(address)) { "Xray transport address is synthetic" }
                return@associateWith requireNotNull(address.hostAddress).substringBefore('%')
            }
            val addresses = answers.getOrPut(endpoint.host) {
                val public = lookup(endpoint.host).filter(::isPublicAddress).distinctBy { it.hostAddress }
                val ipv4 = public.filterIsInstance<Inet4Address>()
                val ipv6 = public.filterNot { it is Inet4Address }
                // Prefer IPv4, but let IPv6 run before a long list of unreachable A
                // answers can consume the whole startup deadline on IPv6-only links.
                buildList {
                    repeat(maxOf(ipv4.size, ipv6.size)) { index ->
                        ipv4.getOrNull(index)?.let(::add)
                        ipv6.getOrNull(index)?.let(::add)
                    }
                }
            }
            require(addresses.isNotEmpty()) { "Xray physical DNS returned no public transport address" }
            val selected = addresses.take(8).firstOrNull { address ->
                check(!Thread.currentThread().isInterrupted) { "Xray bootstrap interrupted" }
                canConnect(address, endpoint.port)
            } ?: error("Xray transport is unreachable on the physical network")
            requireNotNull(selected.hostAddress).substringBefore('%')
        }
    }

    fun endpoint(host: String, port: Int): XrayTransportEndpoint {
        require(port in 1..65535) { "Xray transport port is invalid" }
        return XrayTransportEndpoint(host.trim().removeSurrounding("[", "]").trimEnd('.').lowercase(Locale.ROOT), port)
    }

    fun pinDownloads(extra: JSONObject, addresses: Map<XrayTransportEndpoint, String>) {
        visitDownloads(extra) { stream ->
            val original = stream.getString("address")
            val endpoint = endpoint(original, stream.optInt("port", 0))
            val pinned = addresses[endpoint] ?: error("Xray download transport was not bootstrapped")
            // The upstream derives implicit SNI/HTTP Host from the destination. Keep those
            // names after replacing only the socket destination with its physical address.
            if (stream.optString("security").equals("tls", ignoreCase = true)) {
                val tls = stream.optJSONObject("tlsSettings") ?: JSONObject().also { stream.put("tlsSettings", it) }
                if (tls.optString("serverName").isBlank()) tls.put("serverName", original.removeSurrounding("[", "]"))
            }
            val xhttp = stream.optJSONObject("xhttpSettings") ?: stream.optJSONObject("splithttpSettings")
            val tlsName = stream.optJSONObject("tlsSettings")?.optString("serverName").orEmpty()
            val realityName = stream.optJSONObject("realitySettings")?.optString("serverName").orEmpty()
            if (xhttp != null && xhttp.optString("host").isBlank() && tlsName.isBlank() && realityName.isBlank()) {
                xhttp.put("host", original.removeSurrounding("[", "]"))
            }
            stream.put("address", pinned)
        }
    }

    /** Hostname answers must never redirect the outer transport into local address spaces. */
    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = addressBytes(address)
        if (bytes.size == 4) {
            val a = bytes[0]; val b = bytes[1]
            return a != 0 && a != 10 && a != 127 && a < 224 &&
                !(a == 100 && b in 64..127) && !(a == 169 && b == 254) &&
                !(a == 172 && b in 16..31) && !(a == 192 && b == 168) &&
                !(a == 198 && b in 18..19)
        }
        return bytes.size == 16 && (bytes[0] and 0xfe) != 0xfc &&
            !(bytes[0] == 0xfe && (bytes[1] and 0xc0) == 0x80)
    }

    private fun literalAddress(host: String): InetAddress? {
        if (':' in host) {
            require(host.all { it in "0123456789abcdefABCDEF:." }) { "Xray IPv6 literal is invalid" }
            // A validated colon-containing numeric value cannot enter hostname DNS.
            return InetAddress.getByName(host)
        }
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.any { digit -> digit !in '0'..'9' } }) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return InetAddress.getByAddress(octets.map(Int::toByte).toByteArray())
    }

    private fun isSyntheticAddress(address: InetAddress): Boolean {
        val bytes = addressBytes(address)
        return bytes.size == 4 && bytes[0] == 198 && bytes[1] in 18..19
    }

    private fun addressBytes(address: InetAddress): List<Int> {
        val bytes = address.address.map { it.toInt() and 255 }
        return if (bytes.size == 16 && bytes.take(10).all { it == 0 } && bytes[10] == 255 && bytes[11] == 255) {
            bytes.takeLast(4)
        } else bytes
    }

    private fun visitDownloads(extra: JSONObject, depth: Int = 0, visit: (JSONObject) -> Unit) {
        require(depth <= 4) { "Too many nested Xray download transports" }
        if (!extra.has("downloadSettings") || extra.isNull("downloadSettings")) return
        val stream = extra.optJSONObject("downloadSettings")
            ?: throw IllegalArgumentException("Xray downloadSettings must be an object")
        visit(stream)
        val xhttp = stream.optJSONObject("xhttpSettings") ?: stream.optJSONObject("splithttpSettings")
        xhttp?.let { visitDownloads(it.optJSONObject("extra") ?: it, depth + 1, visit) }
    }
}
