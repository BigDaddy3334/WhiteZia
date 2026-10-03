package shop.whitezia.client.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.AmneziaWgCandidate
import shop.whitezia.client.model.StormDnsCandidate
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.XrayCandidate
import shop.whitezia.client.model.runtimeConnectionSettings

class RuntimeLaunchRequestStoreTest {
    @Test fun preservesAutoModeCandidatesAndOrchestrationSettings() {
        val settings = WhiteZiaSettings(
            connectionMode = "vpn",
            transportMode = WhiteZiaOptions.TransportAuto,
            amneziaWgConfig = "primary config",
            amneziaWgCandidates = listOf(
                AmneziaWgCandidate("awg-primary", "primary", "primary config"),
                AmneziaWgCandidate("awg-reserve", "reserve", "reserve config"),
            ),
            activeAmneziaWgNodeId = "awg-reserve",
            xrayUri = "vless://primary",
            xrayDailyLimitBytes = 50L,
            xrayCandidates = listOf(
                XrayCandidate("xray-primary", "primary", "vless://primary", 50L, "vless://direct"),
                XrayCandidate("xray-reserve", "reserve", "vless://reserve", 100L),
            ),
            activeXrayNodeId = "xray-reserve",
            stormDnsCandidates = listOf(
                StormDnsCandidate("dns-primary", "primary", "dns.example", "secret", 2),
                StormDnsCandidate("dns-reserve", "reserve", "reserve.example", "other", 3),
            ),
            activeStormDnsNodeId = "dns-reserve",
            manualMode = true,
            forceDnsTunnel = true,
            operatorCode = WhiteZiaOptions.OperatorMts,
            customResolversEnabled = true,
            customResolverText = "1.1.1.1:53\n8.8.8.8:53",
        ).runtimeConnectionSettings()
        val request = RuntimeLaunchRequest("session", null, settings)
        val decoded = RuntimeLaunchRequestStore.decode(JSONObject(RuntimeLaunchRequestStore.encode(request).toString()))
        assertEquals(request.id, decoded.id)
        assertEquals(settings.transportMode, decoded.settings.transportMode)
        assertEquals(settings.amneziaWgCandidates, decoded.settings.amneziaWgCandidates)
        assertEquals(settings.activeAmneziaWgNodeId, decoded.settings.activeAmneziaWgNodeId)
        assertEquals(settings.xrayCandidates, decoded.settings.xrayCandidates)
        assertEquals(settings.activeXrayNodeId, decoded.settings.activeXrayNodeId)
        assertEquals(settings.stormDnsCandidates, decoded.settings.stormDnsCandidates)
        assertEquals(settings.activeStormDnsNodeId, decoded.settings.activeStormDnsNodeId)
        assertTrue(decoded.settings.manualMode)
        assertTrue(decoded.settings.forceDnsTunnel)
        assertEquals(settings.operatorCode, decoded.settings.operatorCode)
        assertTrue(decoded.settings.customResolversEnabled)
        assertEquals(settings.customResolverText, decoded.settings.customResolverText)
        assertEquals(settings.resolverText, decoded.settings.resolverText)
    }

    @Test fun legacyRequestsKeepCompatibilityDefaults() {
        val decoded = RuntimeLaunchRequestStore.decode(JSONObject()
            .put("id", "legacy").put("settings", JSONObject()))
        assertEquals(WhiteZiaOptions.TransportAuto, decoded.settings.transportMode)
        assertTrue(decoded.settings.amneziaWgCandidates.isEmpty())
        assertTrue(decoded.settings.xrayCandidates.isEmpty())
        assertTrue(decoded.settings.stormDnsCandidates.isEmpty())
        assertEquals("", decoded.settings.activeAmneziaWgNodeId)
        assertEquals("", decoded.settings.activeXrayNodeId)
        assertEquals("", decoded.settings.activeStormDnsNodeId)
        assertFalse(decoded.settings.manualMode)
        assertFalse(decoded.settings.forceDnsTunnel)
        assertFalse(decoded.settings.customResolversEnabled)
        assertEquals(WhiteZiaOptions.OperatorMegafonYota, decoded.settings.operatorCode)
    }

    @Test fun malformedCandidateDoesNotDiscardOtherCandidates() {
        val settings = JSONObject().put("xrayCandidates", JSONArray()
            .put("not an object")
            .put(JSONObject().put("nodeId", "missing-uri"))
            .put(JSONObject().put("nodeId", "good").put("uri", "vless://good")))
        val decoded = RuntimeLaunchRequestStore.decode(JSONObject().put("settings", settings))
        assertEquals(listOf(XrayCandidate("good", "", "vless://good")), decoded.settings.xrayCandidates)
    }
}
