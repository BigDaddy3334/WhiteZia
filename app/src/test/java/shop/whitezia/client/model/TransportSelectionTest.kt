package shop.whitezia.client.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransportSelectionTest {
    @Test fun automaticModeIgnoresAllStaleManualSelections() {
        for (transport in listOf("auto", "dns", "xray")) {
            assertEquals("auto", WhiteZiaSettings(transportMode = transport, forceDnsTunnel = true).selectedTransportMode())
        }
    }

    @Test fun disabledManualSwitchesAlwaysSelectAutomaticTransport() {
        assertEquals("auto", WhiteZiaSettings(manualMode = true, transportMode = "dns").selectedTransportMode())
    }

    @Test fun enabledDnsSwitchAndXraySelectionAreExplicit() {
        assertEquals("dns", WhiteZiaSettings(manualMode = true, forceDnsTunnel = true, transportMode = "xray").selectedTransportMode())
        assertEquals("xray", WhiteZiaSettings(manualMode = true, transportMode = "xray").selectedTransportMode())
    }

    @Test fun disablingDnsClearsItsStaleTransport() {
        val settings = WhiteZiaSettings(manualMode = true, forceDnsTunnel = true, transportMode = "dns").withForceDnsTunnel(false)
        assertFalse(settings.forceDnsTunnel)
        assertEquals("auto", settings.transportMode)
        assertEquals("auto", settings.selectedTransportMode())
    }

    @Test fun enablingDnsReplacesXrayWithoutRestoringItWhenDisabled() {
        val settings = WhiteZiaSettings(manualMode = true, transportMode = "xray").withForceDnsTunnel(true)
        assertEquals("dns", settings.selectedTransportMode())
        assertEquals("auto", settings.withForceDnsTunnel(false).selectedTransportMode())
    }
}
