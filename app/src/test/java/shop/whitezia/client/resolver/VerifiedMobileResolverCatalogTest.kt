package shop.whitezia.client.resolver

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.WhiteZiaOptions

class VerifiedMobileResolverCatalogTest {
    @Test fun provenNormalUdpIsSelectedOnlyForTheDetectedOperatorAndSupportedTransport() {
        val catalog = parse()
        assertEquals(listOf(Address), catalog.resolvers(Mts, Normal, "udp", Now))
        assertEquals(listOf(Address), catalog.resolvers(Mts, Normal, "auto", Now))
        assertTrue(catalog.resolvers(Mts, Normal, "tcp", Now).isEmpty())
        assertTrue(catalog.resolvers(Mts, Max, "auto", Now).isEmpty())
        assertTrue(catalog.resolvers(WhiteZiaOptions.OperatorBeeline, Normal, "udp", Now).isEmpty())
        assertTrue(catalog.resolvers(null, Normal, "udp", Now).isEmpty())
        assertTrue(catalog.resolvers("unknown", Normal, "udp", Now).isEmpty())
    }

    @Test fun tcpAndMaxNeedTheirOwnDnsAndPayloadProof() {
        val record = entry().put("checks", checks("tcp", "cottenmax-tunnel"))
        val catalog = parse(record)
        assertEquals(listOf(Address), catalog.resolvers(Mts, Max, "tcp", Now))
        assertTrue(catalog.resolvers(Mts, Normal, "tcp", Now).isEmpty())
        assertTrue(catalog.resolvers(Mts, Max, "udp", Now).isEmpty())
        assertTrue(catalog.resolvers(Mts, Max, "auto", Now).isEmpty())
        record.put("checks", checks("tcp", "cottenmax-tunnel").put(check("dns-recursion", "udp")))
        assertTrue(parse(record).resolvers(Mts, Max, "auto", Now).isEmpty())
    }

    @Test fun anOpenPortOrOrdinaryDnsAloneNeverEntersTheCatalog() {
        for (proof in listOf(JSONArray(), JSONArray().put(check("dns-recursion", "udp")),
                JSONArray().put(check("normal-tunnel", "udp")),
                checks("udp", "normal-tunnel").put(0, check("dns-recursion", "udp").put("passed", false)),
                checks("udp", "normal-tunnel").put(1, check("normal-tunnel", "tcp")))) {
            assertTrue(parse(entry().put("checks", proof)).resolvers(Mts, Normal, "udp", Now).isEmpty())
        }
    }

    @Test fun invalidCatalogAndEntryTimestampsFailClosed() {
        for (mutate in listOf<(JSONObject) -> Unit>(
                { it.put("schemaVersion", 2) }, { it.put("generatedAt", "2026-10-25T00:00:00Z") },
                { it.put("expiresAt", "2026-10-23T00:00:00Z") },
                { it.put("expiresAt", "2027-01-01T00:00:00Z") }, { it.remove("generatedAt") })) {
            val raw = root(entry()).also(mutate).toString()
            assertTrue(VerifiedMobileResolverCatalog.parse(raw, Now).resolvers(Mts, Normal, "udp", Now).isEmpty())
        }
        for (record in listOf(entry().put("verifiedAt", "2026-10-25T00:00:00Z"),
                entry().put("expiresAt", "2026-10-23T00:00:00Z"),
                entry().put("verifiedAt", "2026-08-01T00:00:00Z"),
                entry().put("expiresAt", "2027-01-01T00:00:00Z"))) {
            assertTrue(parse(record).resolvers(Mts, Normal, "udp", Now).isEmpty())
        }
        val catalog = parse()
        assertTrue(catalog.resolvers(Mts, Normal, "udp", Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()).isEmpty())
        assertTrue(catalog.resolvers(Mts, Normal, "udp", Instant.parse("2026-10-22T00:00:00Z").toEpochMilli()).isEmpty())
    }

    @Test fun privateCgnatFakeDnsDocumentationAndMalformedIpsNeverBecomeGlobalSeeds() {
        val invalid = listOf("0.0.0.0", "10.20.0.53", "100.64.1.53", "100.127.255.254", "127.0.0.1",
            "169.254.1.53", "172.16.0.53", "172.31.255.254", "192.168.1.53", "192.0.0.53", "192.0.2.53",
            "192.88.99.53", "198.18.0.53", "198.19.255.254", "198.51.100.53", "203.0.113.53",
            "224.0.0.53", "239.1.1.1", "240.1.1.1", "255.255.255.255", "8.8.8.08", "8.8.8", "8.8.8.256",
            "1.1.1.1:53", "8.8.0.0/16", "localhost", "::1", "fd00::53", "2606:4700:4700::1111")
        invalid.forEach { ip -> assertNull(ip, VerifiedMobileResolverCatalog.publicIpv4Number(ip)) }
        listOf(Address, "1.1.1.1", "8.8.8.8", "100.128.0.53", "192.0.1.53").forEach {
            assertNotNull(it, VerifiedMobileResolverCatalog.publicIpv4Number(it))
        }
    }

    @Test fun addressMustBelongToItsCitedOriginPrefixAndAsn() {
        for (source in listOf(source().put("prefix", "176.59.126.0/24"), source().put("prefix", "176.59.127.1/24"),
                source().put("prefix", "0.0.0.0/0"), source().put("prefix", "176.59.127.0/33"),
                source().put("asn", 12345), source().put("kind", "port-scan"),
                source().put("prefixSnapshotSha256", ""),
                source().put("prefixSourceUrl", "https://stat.ripe.net/data/ris-prefixes/data.json?resource=AS12345&types=o&af=v4"),
                source().put("url", "http://bgp.he.net/AS15378"), source().put("url", "https://other.example/AS15378"),
                source().put("url", "https://bgp.he.net/AS12345"))) {
            assertTrue(source.toString(), parse(entry().put("source", source)).resolvers(Mts, Normal, "udp", Now).isEmpty())
        }
        assertTrue(parse(entry().put("port", 5353)).resolvers(Mts, Normal, "udp", Now).isEmpty())
    }

    @Test fun malformedOrOversizedDataDoesNotBreakVpnStartup() {
        for (raw in listOf("", "{broken", "[]", "x".repeat(VerifiedMobileResolverCatalog.MaxBytes + 1))) {
            assertTrue(VerifiedMobileResolverCatalog.parse(raw, Now).resolvers(Mts, Normal, "auto", Now).isEmpty())
        }
    }

    @Test fun catalogWithRegionalAndMvnoFamiliesRetainsKnownMajorOperatorsWithoutGuessingUnknownMappings() {
        val raw = root(entry()).apply {
            val operators = getJSONArray("operators")
            for (unknown in listOf("motiv", "letai", "sbermobile", "future_mobile_family")) {
                operators.put(JSONObject().put("code", unknown).put("asns", JSONArray().put(15378))
                    .put("resolvers", JSONArray().put(entry())))
            }
        }.toString()
        val catalog = VerifiedMobileResolverCatalog.parse(raw, Now)
        assertEquals(listOf(Address), catalog.resolvers(Mts, Normal, "udp", Now))
        assertTrue(catalog.resolvers("sbermobile", Normal, "udp", Now).isEmpty())
    }

    @Test fun invalidEntryCannotDisplaceValidSiblingsAndReturnedAddressesAreDeduplicatedAndBounded() {
        val records = JSONArray().put(entry().put("address", "10.20.0.53")).put(entry()).put(entry())
        repeat(10) { records.put(entry().put("address", "176.59.127.${150 + it}")) }
        val raw = root(entry()).apply { getJSONArray("operators").getJSONObject(0).put("resolvers", records) }.toString()
        assertEquals(listOf(Address, "176.59.127.150", "176.59.127.151", "176.59.127.152"),
            VerifiedMobileResolverCatalog.parse(raw, Now).resolvers(Mts, Normal, "udp", Now))
    }

    private fun parse(record: JSONObject = entry()) = VerifiedMobileResolverCatalog.parse(root(record).toString(), Now)
    private fun root(record: JSONObject) = JSONObject().put("schemaVersion", 1)
        .put("generatedAt", "2026-10-24T00:00:00Z").put("expiresAt", "2026-10-27T00:00:00Z")
        .put("operators", JSONArray().put(JSONObject().put("code", Mts).put("asns", JSONArray().put(15378))
            .put("resolvers", JSONArray().put(record))))
    private fun entry() = JSONObject().put("address", Address).put("port", 53).put("source", source())
        .put("verifiedAt", "2026-10-23T23:00:00Z").put("expiresAt", "2026-10-27T00:00:00Z")
        .put("checks", checks("udp", "normal-tunnel"))
    private fun source() = JSONObject().put("kind", "bgp-origin-masscan").put("asn", 15378)
        .put("prefix", "176.59.127.0/24").put("url", "https://bgp.he.net/AS15378#_prefixes")
        .put("prefixSourceUrl", "https://stat.ripe.net/data/ris-prefixes/data.json?resource=AS15378&types=o&af=v4")
        .put("prefixSnapshotSha256", "a".repeat(64))
    private fun checks(protocol: String, tunnel: String) = JSONArray().put(check("dns-recursion", protocol)).put(check(tunnel, protocol))
    private fun check(kind: String, protocol: String) = JSONObject().put("kind", kind).put("transport", protocol).put("passed", true)
    private companion object {
        val Now = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
        const val Address = "176.59.127.146"
        const val Mts = WhiteZiaOptions.OperatorMts
        const val Normal = CottenDnsMode.Normal
        const val Max = CottenDnsMode.Max
    }
}
