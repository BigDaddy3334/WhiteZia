package shop.whitezia.client.model

fun WhiteZiaSettings.resetTransportCandidateSelection(): WhiteZiaSettings {
    val awg = amneziaWgCandidates.firstOrNull { it.role == "primary" } ?: amneziaWgCandidates.firstOrNull()
    val xray = xrayCandidates.firstOrNull { it.role == "primary" } ?: xrayCandidates.firstOrNull()
    val storm = stormDnsCandidates.firstOrNull { it.role == "primary" } ?: stormDnsCandidates.firstOrNull()
    var updated = copy(
        amneziaWgConfig = awg?.config ?: amneziaWgConfig,
        activeAmneziaWgNodeId = awg?.nodeId.orEmpty(),
        xrayUri = xray?.uri ?: xrayUri,
        xrayDailyLimitBytes = xray?.dailyLimitBytes ?: xrayDailyLimitBytes,
        activeXrayNodeId = xray?.nodeId.orEmpty(),
        activeStormDnsNodeId = storm?.nodeId.orEmpty(),
    )
    if (storm != null) {
        val selected = updated.selectedConnectionProfile()
        updated = updated.copy(
            connectionProfiles = updated.normalizedConnectionProfiles().map { profile ->
                if (profile.id == selected.id) {
                    profile.copy(
                        customServerDomain = storm.domain,
                        customServerEncryptionKey = storm.encryptionKey,
                        customServerEncryptionMethod = storm.encryptionMethod,
                    )
                } else {
                    profile
                }
            },
            customServerDomain = storm.domain,
            customServerEncryptionKey = storm.encryptionKey,
            customServerEncryptionMethod = storm.encryptionMethod,
        )
    }
    return updated.syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.activateNextAmneziaWgCandidate(): WhiteZiaSettings? {
    val candidates = amneziaWgCandidates.filter { it.config.isNotBlank() }
    val currentIndex = candidates.indexOfFirst { it.nodeId == activeAmneziaWgNodeId }
        .takeIf { it >= 0 }
        ?: candidates.indexOfFirst { it.config == amneziaWgConfig }
    val next = candidates.getOrNull(currentIndex + 1) ?: return null
    return copy(amneziaWgConfig = next.config, activeAmneziaWgNodeId = next.nodeId)
        .syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.activateNextXrayCandidate(): WhiteZiaSettings? {
    val candidates = xrayCandidates.filter { it.uri.isNotBlank() }
    val currentIndex = candidates.indexOfFirst { it.nodeId == activeXrayNodeId }
        .takeIf { it >= 0 }
        ?: candidates.indexOfFirst { it.uri == xrayUri }
    val next = candidates.getOrNull(currentIndex + 1) ?: return null
    return copy(
        xrayUri = next.uri,
        xrayDailyLimitBytes = next.dailyLimitBytes,
        activeXrayNodeId = next.nodeId,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.activateNextStormDnsCandidate(): WhiteZiaSettings? {
    val candidates = stormDnsCandidates.filter { it.domain.isNotBlank() && it.encryptionKey.isNotBlank() }
    val selected = selectedConnectionProfile()
    val currentIndex = candidates.indexOfFirst { it.nodeId == activeStormDnsNodeId }
        .takeIf { it >= 0 }
        ?: candidates.indexOfFirst {
            it.domain.equals(selected.customServerDomain, ignoreCase = true) &&
                it.encryptionKey == selected.customServerEncryptionKey
        }
    val next = candidates.getOrNull(currentIndex + 1) ?: return null
    val updatedProfiles = normalizedConnectionProfiles().map { profile ->
        if (profile.id == selected.id) {
            profile.copy(
                customServerDomain = next.domain,
                customServerEncryptionKey = next.encryptionKey,
                customServerEncryptionMethod = next.encryptionMethod,
            )
        } else {
            profile
        }
    }
    return copy(
        connectionProfiles = updatedProfiles,
        customServerDomain = next.domain,
        customServerEncryptionKey = next.encryptionKey,
        customServerEncryptionMethod = next.encryptionMethod,
        activeStormDnsNodeId = next.nodeId,
    ).syncSelectedConnectionProfileFields()
}

internal enum class UserTransportSelection(val storedValue: String) {
    Automatic(WhiteZiaOptions.TransportAuto),
    XrayOnly(WhiteZiaOptions.TransportXray),
    DnsOnly(WhiteZiaOptions.TransportDns),
}

internal fun WhiteZiaSettings.userTransportSelection(): UserTransportSelection = when {
    !manualMode -> UserTransportSelection.Automatic
    forceDnsTunnel -> UserTransportSelection.DnsOnly
    transportMode == WhiteZiaOptions.TransportXray -> UserTransportSelection.XrayOnly
    else -> UserTransportSelection.Automatic
}

fun WhiteZiaSettings.selectedTransportMode(): String = userTransportSelection().storedValue

fun WhiteZiaSettings.withForceDnsTunnel(enabled: Boolean): WhiteZiaSettings = copy(
    forceDnsTunnel = enabled,
    transportMode = if (enabled || transportMode != WhiteZiaOptions.TransportXray) {
        WhiteZiaOptions.TransportAuto
    } else {
        WhiteZiaOptions.TransportXray
    },
)

fun WhiteZiaSettings.runtimeConnectionSettings(): WhiteZiaSettings {
    val settings = syncSelectedConnectionProfileFields()
    fun profileScopedPort(rawValue: String, defaultValue: String, runtimeValue: String): String {
        return if (rawValue.trim() == defaultValue) runtimeValue else rawValue
    }

    return if (settings.connectionMode == "vpn") {
        settings.copy(
            listenIp = WhiteZiaRuntimeProxy.ListenIp,
            listenPort = WhiteZiaRuntimeProxy.ListenPort,
            httpProxyEnabled = false,
            httpProxyPort = WhiteZiaRuntimeProxy.HttpProxyPort,
            socks5Authentication = false,
            socksUsername = "",
            socksPassword = "",
            localDnsEnabled = false,
            localDnsPort = WhiteZiaRuntimeProxy.LocalDnsPort,
        )
    } else {
        settings.copy(
            listenPort = profileScopedPort(
                rawValue = settings.listenPort,
                defaultValue = WhiteZiaRuntimeProxy.DefaultListenPort,
                runtimeValue = WhiteZiaRuntimeProxy.ListenPort,
            ),
            httpProxyPort = profileScopedPort(
                rawValue = settings.httpProxyPort,
                defaultValue = WhiteZiaRuntimeProxy.DefaultHttpProxyPort,
                runtimeValue = WhiteZiaRuntimeProxy.HttpProxyPort,
            ),
            localDnsEnabled = true,
            localDnsPort = WhiteZiaRuntimeProxy.LocalDnsPort,
        )
    }
}
