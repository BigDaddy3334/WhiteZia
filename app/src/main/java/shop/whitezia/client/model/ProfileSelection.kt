package shop.whitezia.client.model

fun WhiteZiaSettings.normalizedConnectionProfiles(): List<ConnectionProfile> {
    val resolverIds = resolverProfiles.map { it.id }.toSet()
    val source = connectionProfiles.ifEmpty {
        listOf(ConnectionProfile.fromSettings(this))
    }
    val normalizedProfiles = source
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .mapIndexed { index, profile ->
            profile.copy(
                name = profile.name.ifBlank { "Connection ${index + 1}" },
                serverMode = "custom",
                customServerEncryptionMethod = profile.customServerEncryptionMethod.coerceIn(0, 5),
                resolverProfileId = profile.resolverProfileId.takeIf { it in resolverIds }.orEmpty(),
                connectionMode = when (profile.connectionMode) {
                    "proxy", "vpn" -> profile.connectionMode
                    else -> "proxy"
                },
            )
        }

    val customProfiles = normalizedProfiles
        .mapIndexed { index, profile ->
            profile.copy(
                id = profile.id,
                name = profile.name.ifBlank { "Connection ${index + 1}" },
                serverMode = "custom",
            )
        }
        .distinctBy { it.id }

    return customProfiles.ifEmpty {
        listOf(ConnectionProfile.defaultProfile())
    }
}

fun WhiteZiaSettings.normalizedResolverProfiles(): List<ResolverProfile> {
    val normalizedCustomResolverText = normalizeResolverText(customResolverText)
    val customResolverProfile = if (customResolversEnabled && normalizedCustomResolverText.isNotBlank()) {
        ResolverProfile(
            id = ResolverProfile.CustomId,
            name = ResolverProfile.CustomName,
            resolverText = normalizedCustomResolverText,
        )
    } else {
        null
    }
    val profiles = resolverProfiles
        .filter { it.id.isNotBlank() && it.id != ResolverProfile.CustomId }
        .distinctBy { it.id }
        .mapIndexed { index, profile ->
            profile.copy(
                name = when {
                    profile.id == ResolverProfile.DefaultId -> ResolverProfile.DefaultName
                    else -> profile.name.ifBlank { "Resolvers ${index + 1}" }
                },
                resolverText = normalizeResolverText(profile.resolverText),
            )
        }
        .filter { it.resolverText.isNotBlank() }
    val defaultProfile = profiles.firstOrNull { it.id == ResolverProfile.DefaultId }
    val customProfiles = profiles.filter { it.id != ResolverProfile.DefaultId }
    return listOfNotNull(defaultProfile, customResolverProfile) + customProfiles
}

fun WhiteZiaSettings.normalizedAdvancedProfiles(): List<AdvancedSettingsProfile> {
    val customProfiles = advancedProfiles
        .filter { it.id.isNotBlank() && it.id != AdvancedSettingsProfile.DefaultId }
        .distinctBy { it.id }
        .mapIndexed { index, profile ->
            profile.copy(name = profile.name.ifBlank { "Advanced ${index + 1}" })
        }
    return listOf(AdvancedSettingsProfile.defaultProfile()) + customProfiles
}

fun WhiteZiaSettings.selectedConnectionProfile(): ConnectionProfile {
    val profiles = normalizedConnectionProfiles()
    return profiles.firstOrNull { it.id == selectedConnectionProfileId } ?: profiles.first()
}

fun WhiteZiaSettings.selectedResolverProfile(): ResolverProfile? {
    return normalizedResolverProfiles().firstOrNull { it.id == selectedResolverProfileId }
}

fun WhiteZiaSettings.selectedAdvancedProfile(): AdvancedSettingsProfile {
    val profiles = normalizedAdvancedProfiles()
    return profiles.firstOrNull { it.id == selectedAdvancedProfileId } ?: profiles.first()
}

fun WhiteZiaSettings.matchesAdvancedProfile(profile: AdvancedSettingsProfile): Boolean {
    return AdvancedSettingsProfile.fromSettings(this, id = profile.id, name = profile.name) == profile
}

fun WhiteZiaSettings.syncSelectedConnectionProfileFields(): WhiteZiaSettings {
    val disabledCustomSelection = !customResolversEnabled && (
        selectedResolverProfileId == ResolverProfile.CustomId ||
            connectionProfiles.firstOrNull { it.id == selectedConnectionProfileId }
                ?.resolverProfileId == ResolverProfile.CustomId
        )
    val resolverProfiles = normalizedResolverProfiles()
    val resolverIds = resolverProfiles.map { it.id }.toSet()
    val profiles = normalizedConnectionProfiles()
    val advancedProfiles = normalizedAdvancedProfiles()
    val selected = profiles.firstOrNull { it.id == selectedConnectionProfileId } ?: profiles.first()
    val selectedConnectionMode = normalizeConnectionMode(connectionMode)
    val selectedThemeMode = normalizeThemeMode(themeMode)
    val selectedLanguageCode = normalizeLanguageCode(languageCode)
    val modeSyncedProfiles = profiles.map { profile ->
        if (profile.id == selected.id) {
            profile.copy(connectionMode = selectedConnectionMode)
        } else {
            profile
        }
    }
    val customResolverSelected = customResolversEnabled && normalizeResolverText(customResolverText).isNotBlank()
    val selectedResolverId = if (customResolverSelected) {
        ResolverProfile.CustomId
    } else {
        selected.resolverProfileId
            .takeIf { it in resolverIds }
            ?: selectedResolverProfileId.takeIf { it in resolverIds }
            ?: ResolverProfile.DefaultId.takeIf { disabledCustomSelection && it in resolverIds }
            ?: ""
    }
    val selectedResolver = resolverProfiles.firstOrNull { it.id == selectedResolverId }
    val resolverSyncedProfiles = if (customResolverSelected || disabledCustomSelection) {
        modeSyncedProfiles.map { profile ->
            if (profile.id == selected.id) {
                profile.copy(resolverProfileId = selectedResolverId)
            } else {
                profile
            }
        }
    } else {
        modeSyncedProfiles
    }
    val selectedAdvancedId = selectedAdvancedProfileId
        .takeIf { profileId -> advancedProfiles.any { it.id == profileId } }
        ?: AdvancedSettingsProfile.DefaultId
    return copy(
        selectedConnectionProfileId = selected.id,
        connectionProfiles = resolverSyncedProfiles,
        selectedResolverProfileId = selectedResolverId,
        resolverProfiles = resolverProfiles,
        selectedAdvancedProfileId = selectedAdvancedId,
        advancedProfiles = advancedProfiles.filter { it.id != AdvancedSettingsProfile.DefaultId },
        resolverText = selectedResolver?.resolverText ?: if (disabledCustomSelection) "" else resolverText,
        serverMode = selected.serverMode,
        customServerDomain = selected.customServerDomain,
        customServerEncryptionKey = selected.customServerEncryptionKey,
        customServerEncryptionMethod = selected.customServerEncryptionMethod,
        connectionMode = selectedConnectionMode,
        themeMode = selectedThemeMode,
        languageCode = selectedLanguageCode,
        cottenDnsResolverTransport = CottenDnsResolverTransport.normalize(cottenDnsResolverTransport),
        splitTunnelMode = normalizeSplitTunnelMode(splitTunnelMode),
        splitTunnelPackages = normalizePackageNames(splitTunnelPackages),
        customResolverText = normalizeResolverText(customResolverText),
        customResolversEnabled = customResolversEnabled && normalizeResolverText(customResolverText).isNotBlank(),
        forceDnsTunnel = forceDnsTunnel,
        transportMode = normalizeTransportMode(transportMode),
        amneziaWgConfig = amneziaWgConfig.trim(),
        amneziaWgCandidates = amneziaWgCandidates.filter { it.nodeId.isNotBlank() && it.config.isNotBlank() }.distinctBy { it.nodeId },
        activeAmneziaWgNodeId = activeAmneziaWgNodeId.trim(),
        xrayUri = xrayUri.trim(),
        xrayDailyLimitBytes = xrayDailyLimitBytes.coerceAtLeast(0L),
        xrayCandidates = xrayCandidates.filter { it.nodeId.isNotBlank() && it.uri.isNotBlank() }.distinctBy { it.nodeId },
        activeXrayNodeId = activeXrayNodeId.trim(),
        stormDnsCandidates = stormDnsCandidates.filter { it.nodeId.isNotBlank() && it.domain.isNotBlank() && it.encryptionKey.isNotBlank() }.distinctBy { it.nodeId },
        activeStormDnsNodeId = activeStormDnsNodeId.trim(),
    )
}
