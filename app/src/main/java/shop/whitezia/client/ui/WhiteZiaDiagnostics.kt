package shop.whitezia.client.ui

import shop.whitezia.client.diagnostics.redactDiagnostics
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.CottenDnsMode
import shop.whitezia.client.model.WhiteZiaUiState
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.resolve
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedResolverProfile
import shop.whitezia.client.storm.CottenDnsEngineContract

/** Safe operational export. Credential-bearing profile exports are a separate user action. */
internal fun buildConnectionDiagnosticsText(
    appVersion: String,
    androidVersion: String,
    androidSdk: Int,
    uiState: WhiteZiaUiState,
): String {
    val settings = uiState.settings.runtimeConnectionSettings()
    val resolvedSettings = settings.resolve()
    val selectedProfile = settings.selectedConnectionProfile()
    val resolverProfile = settings.selectedResolverProfile()
    val verification = uiState.connectionVerification
    val configuredDnsMode = if (CottenDnsMode.normalize(resolvedSettings.cottenDnsMode) == CottenDnsMode.Max) {
        "CottenMax (beta)"
    } else {
        "Normal (native framing)"
    }
    val dnsEngineActivity = when {
        uiState.connectionStatus != ConnectionStatus.CONNECTED -> "not connected"
        uiState.activeTransportMode == WhiteZiaOptions.TransportDns -> "active (DNS transport)"
        else -> "inactive (active transport: ${uiState.activeTransportMode})"
    }

    return uiState.settings.redactDiagnostics(buildString {
        appendLine("WhiteZia diagnostics")
        appendLine("App version: ${appVersion.ifBlank { "unknown" }}")
        appendLine("Android: ${androidVersion} (SDK ${androidSdk})")
        appendLine("Configured DNS engine: ${CottenDnsEngineContract.DisplayName} $configuredDnsMode")
        appendLine("DNS engine activity: $dnsEngineActivity")
        appendLine("DNS engine source revision: ${CottenDnsEngineContract.SourceRevision}")
        appendLine("Configured DNS resolver transport: ${resolvedSettings.cottenDnsResolverTransport}")
        if (CottenDnsMode.normalize(resolvedSettings.cottenDnsMode) == CottenDnsMode.Max) {
            appendLine("CottenMax application traffic: TCP only; application UDP is unsupported")
        }
        appendLine("Status: ${uiState.connectionStatus}")
        appendLine("Connection stage: ${uiState.connectionProgress.phase}")
        if (uiState.connectionProgress.detail.isNotBlank()) appendLine("Last route event: ${uiState.connectionProgress.detail}")
        appendLine("Active transport: ${uiState.activeTransportMode}")
        appendLine("Mode: ${WhiteZiaOptions.connectionModeLabel(resolvedSettings.connectionMode)}")
        appendLine("Profile: ${selectedProfile.name.ifBlank { selectedProfile.id }}")
        appendLine("Server: ${selectedProfile.customServerDomain.ifBlank { "not configured" }}")
        appendLine("Credentials: excluded from diagnostics")
        appendLine("Resolver profile: ${resolverProfile?.name ?: "Manual resolvers"}")
        appendLine("Resolvers: ${resolvedSettings.resolverEntries.size}")
        appendLine("Split tunnel: ${WhiteZiaOptions.splitTunnelModeLabel(resolvedSettings.splitTunnelMode)}")
        appendLine("Split tunnel packages: ${resolvedSettings.splitTunnelPackages.size}")
        appendLine("Notifications enabled: ${uiState.notificationsEnabled}")
        appendLine("Battery optimization ignored: ${uiState.batteryOptimizationIgnored}")
        appendLine("Listen IP: ${resolvedSettings.listenIp}")
        appendLine("Listen port: ${resolvedSettings.listenPort}")
        appendLine("HTTP proxy: ${if (resolvedSettings.httpProxyEnabled) resolvedSettings.httpProxyPort else "off"}")
        appendLine("SOCKS auth: ${if (resolvedSettings.socks5Authentication) "on" else "off"}")
        appendLine("Traffic total: ${formatDataSize(uiState.connectionStats.totalDataUsageBytes)}")
        appendLine("Traffic down: ${formatDataSpeed(uiState.connectionStats.downloadSpeedBytesPerSecond)}")
        appendLine("Traffic up: ${formatDataSpeed(uiState.connectionStats.uploadSpeedBytesPerSecond)}")
        appendLine("Connected apps: ${uiState.connectionStats.connectedApps}")
        appendLine("Active resolvers: ${uiState.resolverRuntimeState.activeResolvers.size}")
        appendLine("Valid resolvers: ${uiState.resolverRuntimeState.validResolvers.size}")
        appendLine("Verification: ${verification.status}")
        appendLine("Verification coverage: ${if (verification.coverage == shop.whitezia.client.model.ConnectionVerificationCoverage.SocksProxy) "local SOCKS proxy only" else "not recorded"}")
        appendLine("VPN TUN/FakeDNS coverage: ${if (resolvedSettings.connectionMode == "vpn") "not independently measured; app UID bypasses VPN" else "not applicable in proxy mode"}")
        if (verification.message.isNotBlank()) {
            appendLine("Verification message: ${verification.message}")
        }
        appendLine()
        appendLine("Recent logs:")
        uiState.connectionLogs.forEach { log ->
            appendLine(log)
        }
    }.trimEnd())
}
