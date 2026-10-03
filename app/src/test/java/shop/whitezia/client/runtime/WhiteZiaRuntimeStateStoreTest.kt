package shop.whitezia.client.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteZiaRuntimeStateStoreTest {
    @Test fun typedStateRoundTripsWithoutMessageInference() {
        val state = WhiteZiaRuntimeState(
            sessionId = "session", mode = "vpn", status = "starting", connectionProfileId = "profile",
            listenIp = "127.0.0.1", listenPort = 10886, updatedAtMillis = 100_000L,
            message = "localized", transportMode = WhiteZiaRuntimeStateStore.TransportAmneziaWg,
            recoveryWaiting = true, schemaVersion = WhiteZiaRuntimeStateStore.CurrentSchemaVersion,
        )
        assertEquals(state, WhiteZiaRuntimeStateStore.decode(
            JSONObject(WhiteZiaRuntimeStateStore.encode(state).toString()),
        ))
    }

    @Test fun legacyStateUsesCompatibilityDefaults() {
        val decoded = WhiteZiaRuntimeStateStore.decode(JSONObject().put("message", "AmneziaWG VPN routing started"))
        assertEquals(1, decoded.schemaVersion)
        assertEquals("", decoded.transportMode)
        assertFalse(decoded.recoveryWaiting)
    }

    @Test fun staleServiceCannotReplaceNewSessionWithTerminalState() {
        val current = sessionState("new-session")
        assertFalse(shouldApplyRuntimeModeUpdate(current, "old-session"))
        assertTrue(shouldApplyRuntimeModeUpdate(current, "new-session"))
    }

    @Test fun modeUpdatesRetainBlankSessionCompatibility() {
        assertTrue(shouldApplyRuntimeModeUpdate(null, "first-session"))
        assertTrue(shouldApplyRuntimeModeUpdate(sessionState(""), "first-session"))
        assertTrue(shouldApplyRuntimeModeUpdate(sessionState("current"), ""))
    }

    private fun sessionState(sessionId: String) = WhiteZiaRuntimeState(
        sessionId = sessionId, mode = "vpn", status = "ready", connectionProfileId = "profile",
        listenIp = "127.0.0.1", listenPort = 10886, updatedAtMillis = 100_000L,
    )
}
