package shop.whitezia.client.model

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionProfileManagerTest {
    private val manager = SubscriptionProfileManager()

    @Test
    fun importProfileRejectsUnsupportedLink() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.importProfile(WhiteZiaSettings(), "https://example.com", true)
        }
    }

    @Test
    fun importProfileValidatesXrayBeforeReturningSettings() {
        val bundle = stormBundle("vless://not-a-uuid@example.com:443?type=xhttp")

        assertThrows(IllegalArgumentException::class.java) {
            manager.importProfile(WhiteZiaSettings(), bundle, true)
        }
    }

    @Test
    fun importProfileValidatesEveryXrayCandidate() {
        val validUri =
            "vless://83e9b6a4-6285-4eae-bc8a-da10897a4288@origin.biba.su:443" +
                "?encryption=none&security=tls&type=xhttp&path=%2Fapi-test&host=origin.biba.su&sni=origin.biba.su"
        val payload = JSONObject()
            .put("schema", "whitezia.bundle")
            .put("version", 2)
            .put(
                "profile",
                JSONObject().put(
                    "xray",
                    JSONObject()
                        .put("uri", validUri)
                        .put(
                            "candidates",
                            org.json.JSONArray()
                                .put(
                                    JSONObject()
                                        .put("node_id", "xray-primary")
                                        .put("role", "primary")
                                        .put("uri", validUri),
                                )
                                .put(
                                    JSONObject()
                                        .put("node_id", "xray-standby")
                                        .put("role", "standby")
                                        .put("uri", "vless://invalid-standby"),
                                ),
                        ),
                ),
            )
            .toString()
        val bundle =
            "stormbundle://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"

        assertThrows(IllegalArgumentException::class.java) {
            manager.importProfile(WhiteZiaSettings(), bundle, true)
        }
    }

    @Test
    fun unchangedAppliedSubscriptionIsNotReparsed() {
        val settings = WhiteZiaSettings(
            subscriptionLink = "stormbundle://invalid-but-unchanged",
            amneziaWgConfig = "[Interface]",
        )

        assertEquals(settings, manager.applySubscriptionIfNeeded(settings, settings))
    }

    @Test
    fun refreshedSubscriptionPreservesManualXrayMode() {
        val xrayUri =
            "vless://83e9b6a4-6285-4eae-bc8a-da10897a4288@origin.biba.su:443" +
                "?encryption=none&security=tls&type=xhttp&path=%2Fapi-test&host=origin.biba.su&sni=origin.biba.su"
        val settings = WhiteZiaSettings(
            manualMode = true,
            transportMode = WhiteZiaOptions.TransportXray,
        )

        val imported = manager.importProfile(
            settings = settings,
            rawLink = stormBundle(xrayUri),
            rememberSubscriptionLink = true,
        )

        assertEquals(true, imported.manualMode)
        assertEquals(WhiteZiaOptions.TransportXray, imported.transportMode)
        assertEquals(xrayUri, imported.xrayUri)
    }

    private fun stormBundle(xrayUri: String): String {
        val payload = JSONObject()
            .put("schema", "whitezia.bundle")
            .put("version", 2)
            .put(
                "profile",
                JSONObject().put("xray", JSONObject().put("uri", xrayUri)),
            )
            .toString()
        return "stormbundle://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"
    }
}
