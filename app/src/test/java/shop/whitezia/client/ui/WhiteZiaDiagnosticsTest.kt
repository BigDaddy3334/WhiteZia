package shop.whitezia.client.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.proxyVerification
import shop.whitezia.client.model.exportStormDnsProfileLink
import shop.whitezia.client.model.importStormDnsProfileLink
import shop.whitezia.client.model.selectedConnectionProfile

class WhiteZiaDiagnosticsTest {
    @Test fun ordinaryExportContainsNoProfileCredentialsOrUnsafeOldLogs() {
        val settings = WhiteZiaSettings(
            connectionMode = "vpn",
            customServerEncryptionKey = "private-dns-key",
            connectionProfiles = listOf(ConnectionProfile("default", "My profile", customServerDomain = "dns.example", customServerEncryptionKey = "private-dns-key", connectionMode = "vpn")),
        )
        val state = WhiteZiaUiState(
            settings = settings,
            connectionLogs = listOf("old diagnostic key=private-dns-key", "token=private-token", "TLS timeout"),
            connectionVerification = proxyVerification("SOCKS passed", 100),
        )
        val report = buildConnectionDiagnosticsText("1.2.3", "16", 36, state)
        assertFalse(report.contains("private-dns-key"))
        assertFalse(report.contains("private-token"))
        assertFalse(report.contains("Encryption key:"))
        assertTrue(report.contains("Credentials: excluded"))
        assertTrue(report.contains("local SOCKS proxy only"))
        assertTrue(report.contains("TUN/FakeDNS coverage: not independently measured"))
        assertTrue(report.contains("TLS timeout"))
        // An operational export does not mutate the user's saved connection credentials.
        assertTrue(settings.customServerEncryptionKey == "private-dns-key")
        val imported = WhiteZiaSettings().importStormDnsProfileLink(settings.exportStormDnsProfileLink())
        assertTrue(imported.selectedConnectionProfile().customServerEncryptionKey == "private-dns-key")
    }

    @Test fun absentMeasurementNeverClaimsVpnVerification() {
        val report = buildConnectionDiagnosticsText("1", "16", 36, WhiteZiaUiState())
        assertTrue(report.contains("Verification coverage: not recorded"))
        assertFalse(report.contains("TUN/FakeDNS coverage: verified"))
    }

    @Test fun maxSelectionIsReportedAsConfiguredWhileXrayIsActive() {
        val report = buildConnectionDiagnosticsText("1", "16", 36, WhiteZiaUiState(
            settings = WhiteZiaSettings(cottenDnsMode = CottenDnsMode.Max),
            connectionStatus = ConnectionStatus.CONNECTED,
            activeTransportMode = WhiteZiaOptions.TransportXray,
        ))
        assertTrue(report.contains("Configured DNS engine: CottenDNS CottenMax (beta)"))
        assertTrue(report.contains("DNS engine activity: inactive (active transport: xray)"))
        assertTrue(report.contains("CottenMax application traffic: TCP only; application UDP is unsupported"))
        assertFalse(report.contains("Normal (native framing)"))
    }

    @Test fun connectedDnsReportsNormalOrMaxWithoutChangingTheSelectedMode() {
        for ((mode, label) in listOf(
            CottenDnsMode.Normal to "Normal (native framing)",
            CottenDnsMode.Max to "CottenMax (beta)",
        )) {
            val state = WhiteZiaUiState(
                settings = WhiteZiaSettings(cottenDnsMode = mode),
                connectionStatus = ConnectionStatus.CONNECTED,
                activeTransportMode = WhiteZiaOptions.TransportDns,
            )
            val report = buildConnectionDiagnosticsText("1", "16", 36, state)
            assertTrue(report.contains("Configured DNS engine: CottenDNS $label"))
            assertTrue(report.contains("DNS engine activity: active (DNS transport)"))
            assertTrue(state.settings.cottenDnsMode == mode)
        }
    }

    @Test fun stoppedDnsDoesNotClaimAnActiveEngine() {
        val report = buildConnectionDiagnosticsText("1", "16", 36, WhiteZiaUiState(
            settings = WhiteZiaSettings(cottenDnsMode = CottenDnsMode.Max),
            activeTransportMode = WhiteZiaOptions.TransportDns,
        ))
        assertTrue(report.contains("DNS engine activity: not connected"))
        assertFalse(report.contains("DNS engine activity: active"))
    }
}
