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
    fun unchangedAppliedSubscriptionIsNotReparsed() {
        val settings = WhiteZiaSettings(
            subscriptionLink = "stormbundle://invalid-but-unchanged",
            amneziaWgConfig = "[Interface]",
        )

        assertEquals(settings, manager.applySubscriptionIfNeeded(settings, settings))
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
