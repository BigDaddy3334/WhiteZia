package shop.whitezia.client.xray

import java.net.InetAddress
import java.net.URLEncoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.resolve

class XrayTransportBootstrapTest {
    @Test fun explicitLoopbackAndPrivateAndPublicIpv4LiteralsNeedNoDnsOrPhysicalProbe() {
        listOf("127.0.0.1", "10.1.2.3", "172.31.0.1", "192.168.0.1", "8.8.8.8").forEach { host ->
            val client = XrayClientConfigParser.parseVlessUri(uri().replace("origin.example", host))
            val addresses = XrayTransportBootstrap.resolve(client,
                { error("Explicit literal must not invoke DNS") },
                { _, _ -> error("Explicit literal must not invoke a physical-network TCP probe") })
            assertEquals(host, addresses.getValue(XrayTransportEndpoint(host, 443)))
        }
    }

    @Test fun explicitIpv6LiteralsNeedNoDnsOrPhysicalProbe() {
        listOf("::1", "fd00::1", "2001:4860:4860::8888").forEach { host ->
            val client = XrayClientConfigParser.parseVlessUri(uri().replace("origin.example", "[$host]"))
            val addresses = XrayTransportBootstrap.resolve(client,
                { error("Explicit IPv6 literal must not invoke DNS") },
                { _, _ -> error("Explicit IPv6 literal must not invoke a physical-network TCP probe") })
            assertEquals(InetAddress.getByName(host).hostAddress, addresses.values.single())
        }
    }

    @Test fun explicitSyntheticLiteralsAreRejectedWithoutDnsOrPhysicalProbe() {
        listOf("198.18.0.48", "198.19.255.255", "[::ffff:198.18.0.48]").forEach { host ->
            val client = XrayClientConfigParser.parseVlessUri(uri().replace("origin.example", host))
            assertThrows(IllegalArgumentException::class.java) {
                XrayTransportBootstrap.resolve(client,
                    { error("Synthetic literal must not invoke DNS") },
                    { _, _ -> error("Synthetic literal must not invoke a physical-network TCP probe") })
            }
        }
    }

    @Test fun aHostnameResolvedToLoopbackStillCannotDialTheLocalEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            XrayTransportBootstrap.resolve(client(), { listOf(ip(127, 0, 0, 1)) }) { _, _ ->
                error("A hostname must not bootstrap through a local DNS answer")
            }
        }
    }

    @Test fun rejectsFakeDnsBeforeItCanBecomeAnOuterTransportDestination() {
        var attempted = false
        assertThrows(IllegalArgumentException::class.java) {
            XrayTransportBootstrap.resolve(client(), { listOf(ip(198, 18, 0, 48), ip(198, 19, 255, 255)) }) { _, _ ->
                attempted = true; true
            }
        }
        assertFalse(attempted)
    }

    @Test fun skipsFakeAndPrivateAnswersAndTriesAnotherPhysicalAddress() {
        val attempted = mutableListOf<String>()
        val result = XrayTransportBootstrap.resolve(client(), {
            listOf(ip(198, 18, 0, 48), ip(10, 0, 0, 1), ip(8, 8, 8, 8), ip(1, 1, 1, 1))
        }) { address, port ->
            assertEquals(443, port)
            attempted += address.hostAddress!!
            address == ip(1, 1, 1, 1)
        }
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), attempted)
        assertEquals("1.1.1.1", result.getValue(XrayTransportEndpoint("origin.example", 443)))
    }

    @Test fun prefersPhysicalIpv4AndKeepsIpv6FallbackForIpv6OnlyNetworks() {
        val ipv6 = InetAddress.getByAddress(byteArrayOf(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0x88.toByte(), 0x88.toByte()))
        val attempted = mutableListOf<InetAddress>()
        val first = XrayTransportBootstrap.resolve(client(), { listOf(ipv6, ip(8, 8, 8, 8)) }) { address, _ ->
            attempted += address; true
        }
        assertEquals(listOf(ip(8, 8, 8, 8)), attempted)
        assertEquals("8.8.8.8", first.values.single())
        val onlyV6 = XrayTransportBootstrap.resolve(client(), { listOf(ipv6) }) { _, _ -> true }
        assertEquals(ipv6.hostAddress, onlyV6.values.single())
    }

    @Test fun everyRuntimeRefreshesPhysicalAnswersInsteadOfKeepingAStaleAddress() {
        var lookups = 0
        val resolve = {
            XrayTransportBootstrap.resolve(client(), {
                lookups += 1
                listOf(if (lookups == 1) ip(8, 8, 8, 8) else ip(1, 1, 1, 1))
            }) { _, _ -> true }
        }
        assertEquals("8.8.8.8", resolve().values.single())
        assertEquals("1.1.1.1", resolve().values.single())
        assertEquals(2, lookups)
    }

    @Test fun anUnreachableIpv4DoesNotStarveTheIpv6Fallback() {
        val ipv6 = InetAddress.getByAddress(byteArrayOf(0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, 0x88.toByte(), 0x88.toByte()))
        val attempted = mutableListOf<InetAddress>()
        XrayTransportBootstrap.resolve(client(), { listOf(ip(8, 8, 8, 8), ip(1, 1, 1, 1), ipv6) }) { address, _ ->
            attempted += address
            address == ipv6
        }
        assertEquals(listOf(ip(8, 8, 8, 8), ipv6), attempted)
    }

    @Test fun newXhttpConnectionsKeepPinnedAddressAfterTheSystemDnsCacheBecomesFake() {
        var lookups = 0
        var cachedAnswer = ip(8, 8, 8, 8)
        val addresses = XrayTransportBootstrap.resolve(client(), { lookups++; listOf(cachedAnswer) }) { _, _ -> true }
        cachedAnswer = ip(198, 18, 0, 48)
        repeat(4) {
            // Xray's newly opened HTTP connections read this literal runtime address;
            // unlike the source URI they never need another process-wide DNS lookup.
            val runtime = render(uri(), addresses)
            val outbound = runtime.getJSONArray("outbounds").getJSONObject(0)
            assertEquals("8.8.8.8", outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getString("address"))
            assertEquals("front.example", outbound.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"))
            assertEquals("host.example", outbound.getJSONObject("streamSettings").getJSONObject("xhttpSettings").getString("host"))
        }
        assertEquals(1, lookups)
    }

    @Test fun pinningPreservesEveryExistingCdnParameterAndDoesNotMutateTheProfile() {
        val extra = JSONObject().put("mode", "packet-up").put("uplinkHTTPMethod", "GET")
            .put("uplinkDataPlacement", "header").put("uplinkDataKey", "X-Upload")
            .put("xmux", JSONObject().put("maxConnections", 4).put("hMaxRequestTimes", "600-900"))
        val sourceUri = uri(extra)
        val before = render(sourceUri)
        val after = render(sourceUri, mapOf(XrayTransportEndpoint("origin.example", 443) to "8.8.8.8"))
        after.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0).put("address", "origin.example")
        assertEquals(before.toString(), after.toString())
        assertEquals(sourceUri, uri(extra))
    }

    @Test fun pinsSeparateDownloadTransportAndPreservesItsTlsNameAndUploadFields() {
        val extra = downloadExtra("download.example", JSONObject().put("serverName", "download-front.example"))
        val sourceUri = uri(extra)
        val parsed = XrayClientConfigParser.parseVlessUri(sourceUri)
        val hosts = mutableListOf<String>()
        val addresses = XrayTransportBootstrap.resolve(parsed, { host ->
            hosts += host
            listOf(if (host == "origin.example") ip(8, 8, 8, 8) else ip(1, 1, 1, 1))
        }) { _, _ -> true }
        assertEquals(listOf("origin.example", "download.example"), hosts)
        val download = download(render(sourceUri, addresses))
        assertEquals("1.1.1.1", download.getString("address"))
        assertEquals("download-front.example", download.getJSONObject("tlsSettings").getString("serverName"))
        assertEquals("download-host.example", download.getJSONObject("xhttpSettings").getString("host"))
        assertEquals("download.example", extra.getJSONObject("downloadSettings").getString("address"))
        assertEquals("download.example", parsed.xhttpExtra.getJSONObject("downloadSettings").getString("address"))
    }

    @Test fun downloadPinningMakesImplicitSniExplicitWithoutChangingItsName() {
        val extra = downloadExtra("download.example", JSONObject())
        extra.getJSONObject("downloadSettings").getJSONObject("xhttpSettings").remove("host")
        val pinned = download(render(uri(extra), mapOf(
            XrayTransportEndpoint("origin.example", 443) to "8.8.8.8",
            XrayTransportEndpoint("download.example", 443) to "1.1.1.1",
        )))
        assertEquals("download.example", pinned.getJSONObject("tlsSettings").getString("serverName"))
        assertFalse(pinned.getJSONObject("xhttpSettings").has("host"))
    }

    @Test fun plaintextDownloadAndUploadKeepImplicitHttpHost() {
        val extra = downloadExtra("download.example", JSONObject())
        extra.getJSONObject("downloadSettings").put("security", "none").remove("tlsSettings")
        extra.getJSONObject("downloadSettings").getJSONObject("xhttpSettings").remove("host")
        val sourceUri = uri(extra).replace("security=tls", "security=none").replace("&host=host.example", "")
        val runtime = render(sourceUri, mapOf(
            XrayTransportEndpoint("origin.example", 443) to "8.8.8.8",
            XrayTransportEndpoint("download.example", 443) to "1.1.1.1",
        ))
        val stream = runtime.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("origin.example", stream.getJSONObject("xhttpSettings").getString("host"))
        assertEquals("download.example", download(runtime).getJSONObject("xhttpSettings").getString("host"))
    }

    @Test fun refusesUnbootstrappedSecondaryTransportInsteadOfLeakingToSystemDns() {
        assertThrows(IllegalStateException::class.java) {
            render(uri(downloadExtra("download.example", JSONObject())), mapOf(XrayTransportEndpoint("origin.example", 443) to "8.8.8.8"))
        }
    }

    @Test fun nestedSecondaryTransportWithoutExtraIsAlsoPinned() {
        val extra = downloadExtra("download.example", JSONObject())
        val nested = extra.getJSONObject("downloadSettings").getJSONObject("xhttpSettings")
        nested.put("downloadSettings", JSONObject().put("address", "third.example").put("port", 8443))
        val parsed = XrayClientConfigParser.parseVlessUri(uri(extra))
        assertEquals(listOf(XrayTransportEndpoint("origin.example", 443), XrayTransportEndpoint("download.example", 443),
            XrayTransportEndpoint("third.example", 8443)), XrayTransportBootstrap.endpoints(parsed))
        val runtime = render(uri(extra), mapOf(XrayTransportEndpoint("origin.example", 443) to "8.8.8.8",
            XrayTransportEndpoint("download.example", 443) to "1.1.1.1", XrayTransportEndpoint("third.example", 8443) to "9.9.9.9"))
        assertEquals("9.9.9.9", download(runtime).getJSONObject("xhttpSettings").getJSONObject("downloadSettings").getString("address"))
    }

    @Test fun secondaryTransportsShareDnsAnswersButKeepIndependentPhysicalPorts() {
        val extra = downloadExtra("ORIGIN.example.", JSONObject())
        extra.getJSONObject("downloadSettings").put("port", 8443)
        var lookups = 0
        val ports = mutableListOf<Int>()
        val pinned = XrayTransportBootstrap.resolve(XrayClientConfigParser.parseVlessUri(uri(extra)), {
            lookups++; listOf(ip(8, 8, 8, 8))
        }) { _, port -> ports += port; true }
        assertEquals(1, lookups)
        assertEquals(listOf(443, 8443), ports)
        assertEquals(2, pinned.size)
    }

    @Test fun privateAndSyntheticAddressRangesAreNotOuterTransportAddresses() {
        listOf(ip(0, 0, 0, 0), ip(127, 0, 0, 1), ip(10, 1, 2, 3), ip(172, 31, 0, 1),
            ip(192, 168, 0, 1), ip(169, 254, 1, 2), ip(100, 64, 0, 1), ip(198, 18, 0, 48),
            ip(198, 19, 1, 2), ip(224, 1, 2, 3), ip(255, 255, 255, 255)).forEach {
            assertFalse(it.hostAddress, XrayTransportBootstrap.isPublicAddress(it))
        }
        assertTrue(XrayTransportBootstrap.isPublicAddress(ip(1, 1, 1, 1)))
        assertTrue(XrayTransportBootstrap.isPublicAddress(ip(198, 20, 0, 1)))
    }

    private fun ip(vararg values: Int) = InetAddress.getByAddress(values.map(Int::toByte).toByteArray())
    private fun client() = XrayClientConfigParser.parseVlessUri(uri())
    private fun uri(extra: JSONObject = JSONObject()): String =
        "vless://83e9b6a4-6285-4eae-bc8a-da10897a4288@origin.example:443" +
            "?security=tls&type=xhttp&path=%2Fapi-test&host=host.example&sni=front.example&mode=packet-up" +
            "&extra=" + URLEncoder.encode(extra.toString(), Charsets.UTF_8.name())
    private fun render(uri: String, addresses: Map<XrayTransportEndpoint, String> = emptyMap()) =
        JSONObject(XrayConfigRenderer.renderClientJson(uri, WhiteZiaSettings().resolve(), addresses))
    private fun download(runtime: JSONObject) = runtime.getJSONArray("outbounds").getJSONObject(0)
        .getJSONObject("streamSettings").getJSONObject("xhttpSettings").getJSONObject("extra").getJSONObject("downloadSettings")
    private fun downloadExtra(host: String, tls: JSONObject) = JSONObject().put("downloadSettings", JSONObject()
        .put("address", host).put("port", 443).put("network", "xhttp").put("security", "tls")
        .put("tlsSettings", tls).put("xhttpSettings", JSONObject().put("host", "download-host.example").put("path", "/download")))
}
