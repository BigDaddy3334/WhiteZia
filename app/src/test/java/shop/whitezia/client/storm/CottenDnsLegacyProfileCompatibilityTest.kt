package shop.whitezia.client.storm

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.exportStormDnsProfileLink
import shop.whitezia.client.model.importStormDnsProfileLink
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.runtime.RuntimeLaunchRequest
import shop.whitezia.client.runtime.RuntimeLaunchRequestStore

class CottenDnsLegacyProfileCompatibilityTest {
    @Test fun oldStormdnsLinkKeepsCredentialAndStillRendersNativeCottenConfig() {
        val profile = JSONObject().put("name", "Saved DNS profile").put("server", server())
        val imported = WhiteZiaSettings().importStormDnsProfileLink(link("stormdns", "whitezia.profile", 1, profile), 1000)
        val saved = imported.selectedConnectionProfile()
        assertEquals("saved.example.test", saved.customServerDomain)
        assertEquals("saved-fixture-key", saved.customServerEncryptionKey)
        assertEquals(2, saved.customServerEncryptionMethod)
        val exported = imported.exportStormDnsProfileLink()
        assertTrue(exported.startsWith("stormdns://"))
        val reimported = WhiteZiaSettings().importStormDnsProfileLink(exported, 2000)
        assertEquals(saved.customServerEncryptionKey, reimported.selectedConnectionProfile().customServerEncryptionKey)
        assertTrue(StormDnsConfigRenderer.renderClientToml(saved, imported).contains("LEGACY_SESSION_ID = false"))
    }

    @Test fun oldBundleAndRuntimeSnapshotKeepDnsCandidateKeysWithoutChangingXray() {
        val uri = "vless://11111111-1111-4111-8111-111111111111@cdn.example.test:443?encryption=none&security=tls&type=xhttp&sni=cdn.example.test"
        val dns = server().put("candidates", JSONArray().put(server().put("node_id", "dns-primary").put("role", "primary")))
        val profile = JSONObject().put("name", "Saved bundle").put("stormdns", dns)
            .put("xray", JSONObject().put("uri", uri))
        val imported = WhiteZiaSettings().importStormDnsProfileLink(link("stormbundle", "whitezia.bundle", 2, profile), 1000)
        val encoded = RuntimeLaunchRequestStore.encode(RuntimeLaunchRequest("fixture", null, imported))
        assertTrue(encoded.getJSONObject("settings").has("stormDnsCandidates"))
        assertTrue(encoded.getJSONObject("settings").has("activeStormDnsNodeId"))
        val decoded = RuntimeLaunchRequestStore.decode(JSONObject(encoded.toString()))
        assertEquals(imported.stormDnsCandidates, decoded.settings.stormDnsCandidates)
        assertEquals("dns-primary", decoded.settings.activeStormDnsNodeId)
        assertEquals(uri, decoded.settings.xrayUri)
        assertEquals(imported.customServerEncryptionKey, decoded.settings.customServerEncryptionKey)
    }

    private fun server() = JSONObject().put("domain", "saved.example.test")
        .put("encryption_key", "saved-fixture-key").put("encryption_method", 2)

    private fun link(scheme: String, schema: String, version: Int, profile: JSONObject): String {
        val json = JSONObject().put("schema", schema).put("version", version).put("profile", profile)
        return "$scheme://" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }
}
