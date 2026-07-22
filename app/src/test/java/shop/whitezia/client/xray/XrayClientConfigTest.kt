package shop.whitezia.client.xray

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaRuntimeProxy
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.runtimeConnectionSettings

class XrayClientConfigTest {
    @Test
    fun parseVlessXhttpUri() {
        val config = XrayClientConfigParser.parseVlessUri(TestVlessUri)

        assertEquals("83e9b6a4-6285-4eae-bc8a-da10897a4288", config.uuid)
        assertEquals("origin.biba.su", config.address)
        assertEquals(443, config.port)
        assertEquals("none", config.encryption)
        assertEquals("tls", config.security)
        assertEquals("xhttp", config.network)
        assertEquals("/api-test", config.path)
        assertEquals("origin.biba.su", config.host)
        assertEquals("packet-up", config.mode)
        assertEquals("origin.biba.su", config.serverName)
        assertEquals("safari", config.fingerprint)
        assertEquals(listOf("h2"), config.alpn)
        assertEquals("X-Cache", config.xPaddingHeader)
        assertEquals("dc", config.xPaddingKey)
        assertEquals("tokenish", config.xPaddingMethod)
        assertEquals(true, config.xPaddingObfsMode)
        assertEquals("queryInHeader", config.xPaddingPlacement)
    }

    @Test
    fun renderXrayClientConfigForLocalSocksAndVlessXhttp() {
        val settings = WhiteZiaSettings(
            connectionMode = "vpn",
            xrayUri = TestVlessUri,
            logLevel = "DEBUG",
        ).runtimeConnectionSettings()
        val json = JSONObject(
            XrayConfigRenderer.renderClientJson(
                xrayUri = settings.xrayUri,
                resolvedSettings = settings.resolve(),
            ),
        )

        val log = json.getJSONObject("log")
        assertEquals("none", log.getString("access"))
        assertEquals(false, log.getBoolean("dnsLog"))
        assertEquals("warning", log.getString("loglevel"))

        val inbound = json.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks", inbound.getString("protocol"))
        assertEquals(WhiteZiaRuntimeProxy.ListenIp, inbound.getString("listen"))
        assertEquals(WhiteZiaRuntimeProxy.ListenPortInt, inbound.getInt("port"))

        val outbound = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("protocol"))
        val server = outbound
            .getJSONObject("settings")
            .getJSONArray("vnext")
            .getJSONObject(0)
        assertEquals("origin.biba.su", server.getString("address"))
        assertEquals(443, server.getInt("port"))
        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("origin.biba.su", tls.getString("serverName"))
        assertEquals("safari", tls.getString("fingerprint"))
        assertEquals("h2", tls.getJSONArray("alpn").getString(0))
        val xhttp = stream.getJSONObject("xhttpSettings")
        assertEquals("/api-test", xhttp.getString("path"))
        assertEquals(1000000, xhttp.getInt("scMaxEachPostBytes"))
        assertEquals(30, xhttp.getInt("scMinPostsIntervalMs"))
        val extra = xhttp.getJSONObject("extra")
        assertEquals("X-Cache", extra.getString("xPaddingHeader"))
        assertEquals("dc", extra.getString("xPaddingKey"))
        assertEquals("tokenish", extra.getString("xPaddingMethod"))
        assertEquals(true, extra.getBoolean("xPaddingObfsMode"))
        assertEquals("queryInHeader", extra.getString("xPaddingPlacement"))
    }

    @Test
    fun renderXrayClientConfigForLocalHttpConnectBootstrap() {
        val settings = WhiteZiaSettings(
            connectionMode = "proxy",
            protocolType = "HTTP",
            xrayUri = TestVlessUri,
            listenIp = "127.0.0.1",
            listenPort = "23456",
        ).runtimeConnectionSettings()
        val json = JSONObject(
            XrayConfigRenderer.renderClientJson(
                xrayUri = settings.xrayUri,
                resolvedSettings = settings.resolve(),
            ),
        )

        val inbound = json.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("http", inbound.getString("protocol"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(23456, inbound.getInt("port"))
        assertEquals("xhttp", json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getString("network"))
    }

    @Test
    fun xrayRuntimeLogsHidePerConnectionAccessLines() {
        assertFalse(
            XrayProcessManager.shouldForwardOutput(
                "2026/07/09 17:12:03 from tcp:127.0.0.1:34580 accepted tcp:www.google.com:443",
            ),
        )
        assertFalse(
            XrayProcessManager.shouldForwardOutput(
                "2026/07/09 17:12:03 from udp:127.0.0.1:34580 accepted udp:1.1.1.1:53",
            ),
        )
        assertTrue(XrayProcessManager.shouldForwardOutput("[Warning] core: Xray started"))
    }

    @Test
    fun rejectsMalformedXhttpExtraInsteadOfSilentlyDroppingIt() {
        val malformed = TestVlessUri.replace(
            "%7B%22scMaxBufferedPosts%22%3A30%2C%22uplinkHTTPMethod%22%3A%22OPTIONS%22%7D",
            "%7Bbroken",
        )

        assertThrows(IllegalArgumentException::class.java) {
            XrayClientConfigParser.parseVlessUri(malformed)
        }
    }

    @Test
    fun rejectsInvalidUuidAndUnsafeXhttpLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            XrayClientConfigParser.parseVlessUri(TestVlessUri.replace(
                "83e9b6a4-6285-4eae-bc8a-da10897a4288",
                "not-a-uuid",
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            XrayClientConfigParser.parseVlessUri(
                TestVlessUri.replace("scMaxEachPostBytes=1000000", "scMaxEachPostBytes=-1"),
            )
        }
    }

    private companion object {
        const val TestVlessUri =
            "vless://83e9b6a4-6285-4eae-bc8a-da10897a4288@origin.biba.su:443" +
                "?encryption=none&security=tls&type=xhttp&network=xhttp&path=%2Fapi-test" +
                "&host=origin.biba.su&sni=origin.biba.su&fp=safari&alpn=h2&mode=packet-up" +
                "&scMaxEachPostBytes=1000000&scMinPostsIntervalMs=30" +
                "&extra=%7B%22scMaxBufferedPosts%22%3A30%2C%22uplinkHTTPMethod%22%3A%22OPTIONS%22%7D" +
                "&xPaddingHeader=X-Cache&xPaddingKey=dc&xPaddingMethod=tokenish" +
                "&xPaddingObfsMode=true&xPaddingPlacement=queryInHeader#WhiteZia-Xray-Test-1d"
    }
}
