package shop.whitezia.client.model

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import shop.whitezia.client.vpn.VpnCandidatePlanner

class SubscriptionSnapshotTest {
    private val manager = SubscriptionProfileManager()

    @Test fun xrayOnlyBundleDoesNotPlanOldDnsCredentials() {
        val previous = manager.importProfile(WhiteZiaSettings(), dnsBundle(), true)
        val imported = manager.importProfile(previous, xrayBundle(), true)
        assertEquals("", imported.customServerDomain)
        assertEquals("", imported.customServerEncryptionKey)
        assertTrue(imported.stormDnsCandidates.isEmpty())
        assertEquals(listOf(WhiteZiaOptions.TransportXray), VpnCandidatePlanner.plan(imported).map { it.settings.transportMode })
        // Retained manual profiles do not become fallback candidates for the new subscription.
        assertTrue(imported.connectionProfiles.any { it.customServerDomain == "old.example.test" })
    }

    @Test fun dnsOnlyBundleClearsOldXrayCredentials() {
        val previous = manager.importProfile(WhiteZiaSettings(), xrayBundle(), true)
        val imported = manager.importProfile(previous, dnsBundle(), true)
        assertEquals("", imported.xrayUri)
        assertTrue(imported.xrayCandidates.isEmpty())
        assertEquals(listOf(WhiteZiaOptions.TransportDns), VpnCandidatePlanner.plan(imported).map { it.settings.transportMode })
    }

    @Test fun repeatedXrayRefreshReusesItsEmptyDnsProfile() {
        val initial = manager.importProfile(WhiteZiaSettings(), xrayBundle(), true)
        val refreshed = manager.importProfile(initial, xrayBundle(), true)
        assertEquals(initial.selectedConnectionProfileId, refreshed.selectedConnectionProfileId)
        assertEquals(initial.connectionProfiles.size, refreshed.connectionProfiles.size)
    }

    @Test fun preparationPreservesCustomRuntimeSettingsAndManualSelection() {
        val settings = WhiteZiaSettings(manualMode = true, forceDnsTunnel = true,
            customConnectionSettingsEnabled = true, minUploadMtu = "77",
            customResolversEnabled = true, customResolverText = "10.1.2.3")
        val prepared = SubscriptionConnectionPreparer.prepare(settings, dnsBundle(), WhiteZiaOptions.OperatorMts)
        assertEquals(WhiteZiaOptions.TransportDns, prepared.selectedTransportMode())
        assertEquals("77", prepared.minUploadMtu)
        assertEquals("10.1.2.3", prepared.resolve().resolverEntries.single())
        assertTrue(prepared.customResolversEnabled)
    }

    @Test fun preparationUsesCachedResolversAndMtsDefaultsWhenCustomSettingsAreOff() {
        val prepared = SubscriptionConnectionPreparer.prepare(WhiteZiaSettings(), dnsBundle(),
            WhiteZiaOptions.OperatorMts, cachedResolverEntries = listOf("10.1.2.3"))
        assertEquals("100", prepared.minDownloadMtu)
        assertEquals("1", prepared.mtuTestRetriesResolvers)
        assertEquals("1.0", prepared.mtuTestTimeoutResolvers)
        assertEquals(WhiteZiaOptions.TransportAuto, prepared.selectedTransportMode())
    }

    private fun dnsBundle() = bundle(JSONObject().put("name", "Previous DNS").put("stormdns",
        JSONObject().put("domain", "old.example.test").put("encryption_key", "fixture-key").put("encryption_method", 0)))

    private fun xrayBundle() = bundle(JSONObject().put("xray", JSONObject().put("uri",
        "vless://11111111-1111-4111-8111-111111111111@new.example.test:443?encryption=none&security=tls&type=xhttp&sni=new.example.test")))

    private fun bundle(profile: JSONObject): String {
        val root = JSONObject().put("schema", "whitezia.bundle").put("version", 2).put("profile", profile)
        return "stormbundle://" + Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().toByteArray())
    }
}
