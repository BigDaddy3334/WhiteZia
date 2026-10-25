package shop.whitezia.client.resolver

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.controlplane.readUtf8Limited
import shop.whitezia.client.model.WhiteZiaOptions
import java.io.ByteArrayInputStream
import java.io.IOException

class DnsRegistryParsingTest {
    @Test fun registryCandidatesAreNormalizedDeduplicatedAndBounded() {
        val entries = JSONArray()
        repeat(100) { entries.put("10.1.0.${it + 1}") }
        entries.put("bad address").put("10.1.0.1")
        val parsed = DnsResolverDiscovery().parseRegistryResolvers(
            JSONObject().put("resolvers", entries).toString(), WhiteZiaOptions.OperatorMts)
        assertEquals(64, parsed.size)
        assertEquals(64, parsed.distinct().size)
    }

    @Test fun oversizedRegistryResponseIsRejectedBeforeJsonParsing() {
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(ByteArray(256 * 1024 + 1)).readUtf8Limited(256 * 1024)
        }
    }
}
