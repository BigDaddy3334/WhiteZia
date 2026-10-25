package shop.whitezia.client.model

fun WhiteZiaSettings.applyAdvancedProfile(profile: AdvancedSettingsProfile): WhiteZiaSettings {
    return copy(
        selectedAdvancedProfileId = profile.id,
        cottenDnsResolverTransport = CottenDnsResolverTransport.normalize(profile.cottenDnsResolverTransport),
        listenIp = profile.listenIp,
        listenPort = profile.listenPort,
        httpProxyEnabled = profile.httpProxyEnabled,
        httpProxyPort = profile.httpProxyPort,
        socks5Authentication = profile.socks5Authentication,
        socksUsername = profile.socksUsername,
        socksPassword = profile.socksPassword,
        balancingStrategy = profile.balancingStrategy,
        uploadDuplication = profile.uploadDuplication,
        downloadDuplication = profile.downloadDuplication,
        uploadCompression = profile.uploadCompression,
        downloadCompression = profile.downloadCompression,
        baseEncodeData = profile.baseEncodeData,
        minUploadMtu = profile.minUploadMtu,
        minDownloadMtu = profile.minDownloadMtu,
        maxUploadMtu = profile.maxUploadMtu,
        maxDownloadMtu = profile.maxDownloadMtu,
        mtuTestRetriesResolvers = profile.mtuTestRetriesResolvers,
        mtuTestTimeoutResolvers = profile.mtuTestTimeoutResolvers,
        mtuTestParallelismResolvers = profile.mtuTestParallelismResolvers,
        mtuTestRetriesLogs = profile.mtuTestRetriesLogs,
        mtuTestTimeoutLogs = profile.mtuTestTimeoutLogs,
        mtuTestParallelismLogs = profile.mtuTestParallelismLogs,
        rxTxWorkers = profile.rxTxWorkers,
        tunnelProcessWorkers = profile.tunnelProcessWorkers,
        tunnelPacketTimeoutSeconds = profile.tunnelPacketTimeoutSeconds,
        dispatcherIdlePollIntervalSeconds = profile.dispatcherIdlePollIntervalSeconds,
        txChannelSize = profile.txChannelSize,
        rxChannelSize = profile.rxChannelSize,
        resolverUdpConnectionPoolSize = profile.resolverUdpConnectionPoolSize,
        streamQueueInitialCapacity = profile.streamQueueInitialCapacity,
        orphanQueueInitialCapacity = profile.orphanQueueInitialCapacity,
        dnsResponseFragmentStoreCapacity = profile.dnsResponseFragmentStoreCapacity,
        maxActiveStreams = profile.maxActiveStreams,
        localHandshakeTimeoutSeconds = profile.localHandshakeTimeoutSeconds,
        socksUdpAssociateReadTimeoutSeconds = profile.socksUdpAssociateReadTimeoutSeconds,
        clientTerminalStreamRetentionSeconds = profile.clientTerminalStreamRetentionSeconds,
        clientCancelledSetupRetentionSeconds = profile.clientCancelledSetupRetentionSeconds,
        sessionInitRetryBaseSeconds = profile.sessionInitRetryBaseSeconds,
        sessionInitRetryStepSeconds = profile.sessionInitRetryStepSeconds,
        sessionInitRetryLinearAfter = profile.sessionInitRetryLinearAfter,
        sessionInitRetryMaxSeconds = profile.sessionInitRetryMaxSeconds,
        sessionInitBusyRetryIntervalSeconds = profile.sessionInitBusyRetryIntervalSeconds,
        localDnsEnabled = profile.localDnsEnabled,
        localDnsPort = profile.localDnsPort,
        startupMode = profile.startupMode,
        pingWatchdogSeconds = profile.pingWatchdogSeconds,
        trafficWarmupEnabled = profile.trafficWarmupEnabled,
        trafficWarmupProbeCount = profile.trafficWarmupProbeCount,
        trafficKeepaliveIntervalSeconds = profile.trafficKeepaliveIntervalSeconds,
        autoTuneEnabled = this.autoTuneEnabled,
        logLevel = profile.logLevel,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.selectAdvancedProfile(profileId: String): WhiteZiaSettings {
    val selectedProfile = normalizedAdvancedProfiles().firstOrNull { it.id == profileId }
        ?: AdvancedSettingsProfile.defaultProfile()
    return applyAdvancedProfile(selectedProfile)
}

fun WhiteZiaSettings.saveSelectedAdvancedProfile(): WhiteZiaSettings {
    if (selectedAdvancedProfileId == AdvancedSettingsProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val customProfiles = normalizedAdvancedProfiles().filter { it.id != AdvancedSettingsProfile.DefaultId }
    if (customProfiles.none { it.id == selectedAdvancedProfileId }) {
        return syncSelectedConnectionProfileFields()
    }
    val currentProfile = selectedAdvancedProfile()
    val updatedProfile = AdvancedSettingsProfile.fromSettings(
        settings = this,
        id = currentProfile.id,
        name = currentProfile.name,
    )
    return copy(
        advancedProfiles = customProfiles.map { profile ->
            if (profile.id == updatedProfile.id) updatedProfile else profile
        },
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.saveCurrentAdvancedProfileAs(name: String): WhiteZiaSettings {
    val profileName = name.trim().ifBlank { "Advanced Settings" }
    val profileId = AdvancedSettingsProfile.newId()
    val profile = AdvancedSettingsProfile.fromSettings(
        settings = this,
        id = profileId,
        name = profileName,
    )
    return copy(
        selectedAdvancedProfileId = profileId,
        advancedProfiles = normalizedAdvancedProfiles().filter { it.id != AdvancedSettingsProfile.DefaultId } + profile,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.upsertAdvancedProfile(profile: AdvancedSettingsProfile): WhiteZiaSettings {
    if (profile.id == AdvancedSettingsProfile.DefaultId) {
        return selectAdvancedProfile(AdvancedSettingsProfile.DefaultId)
    }
    val normalizedProfile = profile.copy(
        id = profile.id.ifBlank { AdvancedSettingsProfile.newId() },
        name = profile.name.ifBlank { "Advanced Settings" },
    )
    val customProfiles = normalizedAdvancedProfiles().filter { it.id != AdvancedSettingsProfile.DefaultId }
    val updatedProfiles = if (customProfiles.any { it.id == normalizedProfile.id }) {
        customProfiles.map { existing ->
            if (existing.id == normalizedProfile.id) normalizedProfile else existing
        }
    } else {
        customProfiles + normalizedProfile
    }
    return copy(
        selectedAdvancedProfileId = normalizedProfile.id,
        advancedProfiles = updatedProfiles,
    ).applyAdvancedProfile(normalizedProfile)
}

fun WhiteZiaSettings.moveAdvancedProfileToIndex(profileId: String, targetIndex: Int): WhiteZiaSettings {
    val customProfiles = normalizedAdvancedProfiles().filter { it.id != AdvancedSettingsProfile.DefaultId }
    val fromIndex = customProfiles.indexOfFirst { it.id == profileId }
    if (fromIndex == -1) {
        return copy(advancedProfiles = customProfiles).syncSelectedConnectionProfileFields()
    }
    val toIndex = targetIndex.coerceIn(0, customProfiles.lastIndex)
    if (fromIndex == toIndex) {
        return copy(advancedProfiles = customProfiles).syncSelectedConnectionProfileFields()
    }
    return copy(
        advancedProfiles = customProfiles.moved(fromIndex, toIndex),
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.deleteAdvancedProfile(profileId: String): WhiteZiaSettings {
    if (profileId == AdvancedSettingsProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val customProfiles = normalizedAdvancedProfiles().filter { it.id != AdvancedSettingsProfile.DefaultId }
    if (customProfiles.none { it.id == profileId }) {
        return syncSelectedConnectionProfileFields()
    }
    val remainingProfiles = customProfiles.filterNot { it.id == profileId }
    val deletingSelectedProfile = selectedAdvancedProfileId == profileId
    val nextSelectedId = if (deletingSelectedProfile) {
        AdvancedSettingsProfile.DefaultId
    } else {
        selectedAdvancedProfileId
    }
    val updatedSettings = copy(
        advancedProfiles = remainingProfiles,
        selectedAdvancedProfileId = nextSelectedId,
    )
    return if (deletingSelectedProfile) {
        updatedSettings.selectAdvancedProfile(nextSelectedId)
    } else {
        updatedSettings.syncSelectedConnectionProfileFields()
    }
}

fun WhiteZiaSettings.resetAdvancedSettings(): WhiteZiaSettings {
    val defaults = WhiteZiaSettings()
    return copy(
        selectedAdvancedProfileId = AdvancedSettingsProfile.DefaultId,
        cottenDnsResolverTransport = defaults.cottenDnsResolverTransport,
        listenIp = defaults.listenIp,
        listenPort = defaults.listenPort,
        httpProxyEnabled = defaults.httpProxyEnabled,
        httpProxyPort = defaults.httpProxyPort,
        socks5Authentication = defaults.socks5Authentication,
        socksUsername = defaults.socksUsername,
        socksPassword = defaults.socksPassword,
        balancingStrategy = defaults.balancingStrategy,
        uploadDuplication = defaults.uploadDuplication,
        downloadDuplication = defaults.downloadDuplication,
        uploadCompression = defaults.uploadCompression,
        downloadCompression = defaults.downloadCompression,
        baseEncodeData = defaults.baseEncodeData,
        minUploadMtu = defaults.minUploadMtu,
        minDownloadMtu = defaults.minDownloadMtu,
        maxUploadMtu = defaults.maxUploadMtu,
        maxDownloadMtu = defaults.maxDownloadMtu,
        mtuTestRetriesResolvers = defaults.mtuTestRetriesResolvers,
        mtuTestTimeoutResolvers = defaults.mtuTestTimeoutResolvers,
        mtuTestParallelismResolvers = defaults.mtuTestParallelismResolvers,
        mtuTestRetriesLogs = defaults.mtuTestRetriesLogs,
        mtuTestTimeoutLogs = defaults.mtuTestTimeoutLogs,
        mtuTestParallelismLogs = defaults.mtuTestParallelismLogs,
        rxTxWorkers = defaults.rxTxWorkers,
        tunnelProcessWorkers = defaults.tunnelProcessWorkers,
        tunnelPacketTimeoutSeconds = defaults.tunnelPacketTimeoutSeconds,
        dispatcherIdlePollIntervalSeconds = defaults.dispatcherIdlePollIntervalSeconds,
        txChannelSize = defaults.txChannelSize,
        rxChannelSize = defaults.rxChannelSize,
        resolverUdpConnectionPoolSize = defaults.resolverUdpConnectionPoolSize,
        streamQueueInitialCapacity = defaults.streamQueueInitialCapacity,
        orphanQueueInitialCapacity = defaults.orphanQueueInitialCapacity,
        dnsResponseFragmentStoreCapacity = defaults.dnsResponseFragmentStoreCapacity,
        maxActiveStreams = defaults.maxActiveStreams,
        localHandshakeTimeoutSeconds = defaults.localHandshakeTimeoutSeconds,
        socksUdpAssociateReadTimeoutSeconds = defaults.socksUdpAssociateReadTimeoutSeconds,
        clientTerminalStreamRetentionSeconds = defaults.clientTerminalStreamRetentionSeconds,
        clientCancelledSetupRetentionSeconds = defaults.clientCancelledSetupRetentionSeconds,
        sessionInitRetryBaseSeconds = defaults.sessionInitRetryBaseSeconds,
        sessionInitRetryStepSeconds = defaults.sessionInitRetryStepSeconds,
        sessionInitRetryLinearAfter = defaults.sessionInitRetryLinearAfter,
        sessionInitRetryMaxSeconds = defaults.sessionInitRetryMaxSeconds,
        sessionInitBusyRetryIntervalSeconds = defaults.sessionInitBusyRetryIntervalSeconds,
        localDnsEnabled = defaults.localDnsEnabled,
        localDnsPort = defaults.localDnsPort,
        startupMode = defaults.startupMode,
        pingWatchdogSeconds = defaults.pingWatchdogSeconds,
        trafficWarmupEnabled = defaults.trafficWarmupEnabled,
        trafficWarmupProbeCount = defaults.trafficWarmupProbeCount,
        trafficKeepaliveIntervalSeconds = defaults.trafficKeepaliveIntervalSeconds,
        autoTuneEnabled = defaults.autoTuneEnabled,
        logLevel = defaults.logLevel,
    ).syncSelectedConnectionProfileFields()
}
