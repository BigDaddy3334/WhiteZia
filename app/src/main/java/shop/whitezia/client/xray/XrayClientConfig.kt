package shop.whitezia.client.xray

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import shop.whitezia.client.model.ResolvedWhiteZiaSettings

data class XrayClientConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String,
    val security: String,
    val network: String,
    val path: String,
    val host: String,
    val mode: String,
    val serverName: String,
    val fingerprint: String,
    val alpn: List<String>,
    val allowInsecure: Boolean,
    val scMaxConcurrentPosts: Int,
    val scMaxEachPostBytes: Int,
    val scMinPostsIntervalMs: Int,
    val scMaxBufferedPosts: Int,
    val uplinkHTTPMethod: String,
    val xPaddingHeader: String,
    val xPaddingKey: String,
    val xPaddingMethod: String,
    val xPaddingObfsMode: Boolean,
    val xPaddingPlacement: String,
    val xhttpExtra: JSONObject,
)

object XrayClientConfigParser {
    fun parseVlessUri(uriText: String): XrayClientConfig {
        val trimmed = uriText.trim()
        require(trimmed.startsWith("vless://", ignoreCase = true)) {
            "Only VLESS Xray links are supported"
        }
        val uri = URI(trimmed)
        val uuid = uri.rawUserInfo
            ?.substringBefore(':')
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("VLESS UUID is missing")
        val address = uri.host
            ?: throw IllegalArgumentException("VLESS server host is missing")
        val port = uri.port.takeIf { it > 0 }
            ?: throw IllegalArgumentException("VLESS server port is missing")
        val query = parseQuery(uri.rawQuery.orEmpty())
        val security = query["security"].orEmpty().ifBlank { "none" }
        val network = query["type"].orEmpty().ifBlank {
            query["network"].orEmpty().ifBlank { "tcp" }
        }
        val xhttpExtra = query["extra"]
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject()
        val alpn = query["alpn"]
            ?.split(',', ';')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("h2")
        return XrayClientConfig(
            uuid = uuid,
            address = address,
            port = port,
            encryption = query["encryption"].orEmpty().ifBlank { "none" },
            security = security,
            network = network,
            path = query["path"].orEmpty(),
            host = query["host"].orEmpty(),
            mode = query["mode"].orEmpty().ifBlank { xhttpExtra.optionalString("mode") },
            serverName = query["sni"].orEmpty().ifBlank {
                query["host"].orEmpty().ifBlank { address }
            },
            fingerprint = query["fp"].orEmpty().ifBlank { "safari" },
            alpn = alpn,
            allowInsecure = query["allowInsecure"].orEmpty().toBooleanStrictOrNull() ?: false,
            scMaxConcurrentPosts = query.optionalInt("scMaxConcurrentPosts") ?: 10,
            scMaxEachPostBytes = query.optionalInt("scMaxEachPostBytes") ?: xhttpExtra.optionalInt("scMaxEachPostBytes") ?: 1000000,
            scMinPostsIntervalMs = query.optionalInt("scMinPostsIntervalMs") ?: xhttpExtra.optionalInt("scMinPostsIntervalMs") ?: 30,
            scMaxBufferedPosts = query.optionalInt("scMaxBufferedPosts") ?: xhttpExtra.optionalInt("scMaxBufferedPosts") ?: 30,
            uplinkHTTPMethod = query["uplinkHTTPMethod"].orEmpty().ifBlank { xhttpExtra.optionalString("uplinkHTTPMethod") },
            xPaddingHeader = query["xPaddingHeader"].orEmpty().ifBlank { xhttpExtra.optionalString("xPaddingHeader") },
            xPaddingKey = query["xPaddingKey"].orEmpty().ifBlank { xhttpExtra.optionalString("xPaddingKey") },
            xPaddingMethod = query["xPaddingMethod"].orEmpty().ifBlank { xhttpExtra.optionalString("xPaddingMethod") },
            xPaddingObfsMode = query["xPaddingObfsMode"].orEmpty().toBooleanStrictOrNull() ?: xhttpExtra.optionalBoolean("xPaddingObfsMode") ?: false,
            xPaddingPlacement = query["xPaddingPlacement"].orEmpty().ifBlank { xhttpExtra.optionalString("xPaddingPlacement") },
            xhttpExtra = xhttpExtra,
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) {
            return emptyMap()
        }
        return rawQuery
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=').trim()
                if (key.isBlank()) {
                    return@mapNotNull null
                }
                val value = part.substringAfter('=', "")
                key to URLDecoder.decode(value, Charsets.UTF_8.name())
            }
            .toMap()
    }
}

object XrayConfigRenderer {
    fun renderClientJson(
        xrayUri: String,
        resolvedSettings: ResolvedWhiteZiaSettings,
    ): String {
        val client = XrayClientConfigParser.parseVlessUri(xrayUri)
        return JSONObject()
            .put(
                "log",
                JSONObject()
                    .put("access", "none")
                    .put("dnsLog", false)
                    .put("loglevel", mapRuntimeLogLevel(resolvedSettings.logLevel)),
            )
            .put(
                "inbounds",
                JSONArray()
                    .put(renderSocksInbound(resolvedSettings)),
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(renderVlessOutbound(client)),
            )
            .toString(2)
    }

    private fun mapRuntimeLogLevel(value: String): String {
        return when (value.trim().lowercase()) {
            "error" -> "error"
            "none" -> "none"
            else -> "warning"
        }
    }

    private fun renderSocksInbound(resolvedSettings: ResolvedWhiteZiaSettings): JSONObject {
        return JSONObject()
            .put("tag", "whitezia-socks")
            .put("listen", resolvedSettings.listenIp)
            .put("port", resolvedSettings.listenPort)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "noauth")
                    .put("udp", true),
            )
    }

    private fun renderVlessOutbound(client: XrayClientConfig): JSONObject {
        return JSONObject()
            .put("tag", "whitezia-xray")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject()
                    .put(
                        "vnext",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("address", client.address)
                                    .put("port", client.port)
                                    .put(
                                        "users",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("id", client.uuid)
                                                    .put("encryption", client.encryption),
                                            ),
                                    ),
                            ),
                    ),
            )
            .put("streamSettings", renderStreamSettings(client))
    }

    private fun renderStreamSettings(client: XrayClientConfig): JSONObject {
        val streamSettings = JSONObject()
            .put("network", client.network)
            .put("security", client.security)

        if (client.security == "tls") {
            streamSettings.put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", client.serverName)
                    .put("allowInsecure", client.allowInsecure)
                    .put("fingerprint", client.fingerprint)
                    .put("alpn", JSONArray(client.alpn)),
            )
        }
        if (client.network == "xhttp") {
            streamSettings.put(
                "xhttpSettings",
                JSONObject().apply {
                    if (client.path.isNotBlank()) {
                        put("path", client.path)
                    }
                    if (client.host.isNotBlank()) {
                        put("host", client.host)
                    }
                    if (client.mode.isNotBlank()) {
                        put("mode", client.mode)
                    }
                    put("scMaxConcurrentPosts", client.scMaxConcurrentPosts)
                    put("scMaxEachPostBytes", client.scMaxEachPostBytes)
                    put("scMinPostsIntervalMs", client.scMinPostsIntervalMs)
                    put("extra", JSONObject(client.xhttpExtra.toString()).apply {
                        if (client.mode.isNotBlank() && !has("mode")) {
                            put("mode", client.mode)
                        }
                        if (!has("scMaxEachPostBytes")) {
                            put("scMaxEachPostBytes", client.scMaxEachPostBytes)
                        }
                        if (!has("scMinPostsIntervalMs")) {
                            put("scMinPostsIntervalMs", client.scMinPostsIntervalMs)
                        }
                        if (!has("scMaxBufferedPosts")) {
                            put("scMaxBufferedPosts", client.scMaxBufferedPosts)
                        }
                        if (client.uplinkHTTPMethod.isNotBlank() && !has("uplinkHTTPMethod")) {
                            put("uplinkHTTPMethod", client.uplinkHTTPMethod)
                        }
                        if (client.xPaddingHeader.isNotBlank() && !has("xPaddingHeader")) {
                            put("xPaddingHeader", client.xPaddingHeader)
                        }
                        if (client.xPaddingKey.isNotBlank() && !has("xPaddingKey")) {
                            put("xPaddingKey", client.xPaddingKey)
                        }
                        if (client.xPaddingMethod.isNotBlank() && !has("xPaddingMethod")) {
                            put("xPaddingMethod", client.xPaddingMethod)
                        }
                        if (client.xPaddingObfsMode && !has("xPaddingObfsMode")) {
                            put("xPaddingObfsMode", true)
                        }
                        if (client.xPaddingPlacement.isNotBlank() && !has("xPaddingPlacement")) {
                            put("xPaddingPlacement", client.xPaddingPlacement)
                        }
                    })
                },
            )
        }
        return streamSettings
    }
}
private fun Map<String, String>.optionalInt(key: String): Int? =
    get(key)?.trim()?.toIntOrNull()

private fun JSONObject.optionalString(key: String): String =
    optString(key, "").trim()

private fun JSONObject.optionalInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}

private fun JSONObject.optionalBoolean(key: String): Boolean? = when (val value = opt(key)) {
    is Boolean -> value
    is String -> value.trim().toBooleanStrictOrNull()
    else -> null
}
