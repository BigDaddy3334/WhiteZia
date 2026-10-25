package shop.whitezia.client.model

/** Pure preparation: no persistence, UI mutation or process launch. */
internal object SubscriptionConnectionPreparer {
    private val subscriptionProfiles = SubscriptionProfileManager()

    fun prepare(
        settings: WhiteZiaSettings,
        rawLink: String,
        operatorCode: String,
        transportMode: String = settings.selectedTransportMode(),
        connectionMode: String = "vpn",
        cachedResolverEntries: List<String> = emptyList(),
    ): WhiteZiaSettings {
        val importedSettings = subscriptionProfiles.importProfile(
            settings = settings,
            rawLink = rawLink,
            rememberSubscriptionLink = true,
        )
        val selectedProfileId = importedSettings.selectedConnectionProfileId
        val vpnProfiles = importedSettings.normalizedConnectionProfiles().map { profile ->
            if (profile.id == selectedProfileId) {
                profile.copy(connectionMode = connectionMode)
            } else {
                profile
            }
        }
        val selectedOperatorCode = normalizeOperatorCode(operatorCode)
        val cachedResolvers = cachedResolverEntries.take(4)
        val cachedResolverText = cachedResolvers
            .joinToString(separator = "\n")
        val previousSettings = settings
        val customResolvers = validateResolverText(previousSettings.customResolverText).normalizedResolvers
        val customResolverText = customResolvers.joinToString(separator = "\n")
        val useCustomResolvers = previousSettings.customResolversEnabled && customResolvers.isNotEmpty()
        val resolverText = when {
            useCustomResolvers -> customResolverText
            importedSettings.resolverText.isNotBlank() -> importedSettings.resolverText
            else -> cachedResolverText
        }
        val preparedBaseSettings = importedSettings.copy(
            connectionProfiles = vpnProfiles,
            connectionMode = connectionMode,
            protocolType = "SOCKS5",
            resolverText = resolverText,
            customResolversEnabled = previousSettings.customResolversEnabled,
            customResolverText = previousSettings.customResolverText,
            customConnectionSettingsEnabled = previousSettings.customConnectionSettingsEnabled,
            selectedResolverProfileId = "",
            subscriptionLink = rawLink.trim(),
            manualMode = previousSettings.manualMode,
            forceDnsTunnel = previousSettings.forceDnsTunnel,
            transportMode = transportMode,
            amneziaWgConfig = importedSettings.amneziaWgConfig,
            xrayUri = importedSettings.xrayUri,
            xrayDailyLimitBytes = importedSettings.xrayDailyLimitBytes,
            operatorCode = selectedOperatorCode,
            listenIp = "127.0.0.1",
            listenPort = "10886",
            httpProxyEnabled = true,
            httpProxyPort = "10887",
            socks5Authentication = false,
            socksUsername = "",
            socksPassword = "",
            localDnsEnabled = false,
            localDnsPort = "10888",
            balancingStrategy = 4,
            uploadDuplication = "2",
            downloadDuplication = "4",
            uploadCompression = 2,
            downloadCompression = 2,
            baseEncodeData = false,
            minUploadMtu = "40",
            minDownloadMtu = "300",
            maxUploadMtu = "140",
            maxDownloadMtu = "3000",
            mtuTestRetriesResolvers = "3",
            mtuTestTimeoutResolvers = "2.0",
            mtuTestParallelismResolvers = "100",
            mtuTestRetriesLogs = "5",
            mtuTestTimeoutLogs = "2.0",
            mtuTestParallelismLogs = "32",
            rxTxWorkers = "4",
            tunnelProcessWorkers = "4",
            tunnelPacketTimeoutSeconds = "10.0",
            dispatcherIdlePollIntervalSeconds = "0.02",
            txChannelSize = "2048",
            rxChannelSize = "2048",
            resolverUdpConnectionPoolSize = "64",
            streamQueueInitialCapacity = "128",
            orphanQueueInitialCapacity = "32",
            dnsResponseFragmentStoreCapacity = "256",
            maxActiveStreams = "2048",
            localHandshakeTimeoutSeconds = "5.0",
            socksUdpAssociateReadTimeoutSeconds = "30.0",
            clientTerminalStreamRetentionSeconds = "45.0",
            clientCancelledSetupRetentionSeconds = "120.0",
            sessionInitRetryBaseSeconds = "1.0",
            sessionInitRetryStepSeconds = "1.0",
            sessionInitRetryLinearAfter = "5",
            sessionInitRetryMaxSeconds = "60.0",
            sessionInitBusyRetryIntervalSeconds = "60.0",
            autoTuneEnabled = false,
            parallelTestSelectedConfigIds = WhiteZiaParallelTest.defaultConfigIds,
            parallelTestAggressivePresetsEnabled = false,
            startupMode = "resolvers",
            pingWatchdogSeconds = "300",
            trafficWarmupEnabled = false,
            trafficKeepaliveIntervalSeconds = "5",
            logLevel = previousSettings.logLevel,
            splitTunnelMode = importedSettings.splitTunnelMode
                .takeIf {
                    it == WhiteZiaOptions.SplitTunnelModeInclude ||
                        it == WhiteZiaOptions.SplitTunnelModeExclude ||
                        it == WhiteZiaOptions.SplitTunnelModeOff
                }
                ?: WhiteZiaOptions.SplitTunnelModeOff,
            splitTunnelPackages = importedSettings.splitTunnelPackages,
        )
        val preparedSettings = when {
            previousSettings.customConnectionSettingsEnabled -> {
                preparedBaseSettings
                    .copyStormRuntimeSettingsFrom(previousSettings)
                    .syncSelectedConnectionProfileFields()
            }
            else -> {
                preparedBaseSettings
                    .applyOperatorRuntimeSettings(selectedOperatorCode)
                    .applyFastStartupRuntimeSettings(useCachedResolvers = cachedResolvers.isNotEmpty())
                    .syncSelectedConnectionProfileFields()
            }
        }

        return preparedSettings
    }
}

internal fun normalizeOperatorCode(operatorCode: String): String {
    return when (operatorCode) {
        WhiteZiaOptions.OperatorMegafonYota,
        WhiteZiaOptions.OperatorMts,
        WhiteZiaOptions.OperatorBeeline,
        WhiteZiaOptions.OperatorTele2,
        -> operatorCode
        else -> WhiteZiaOptions.OperatorMegafonYota
    }
}

private fun WhiteZiaSettings.applyOperatorRuntimeSettings(operatorCode: String): WhiteZiaSettings {
    return when (normalizeOperatorCode(operatorCode)) {
        WhiteZiaOptions.OperatorMts -> copy(
            balancingStrategy = 4,
            uploadDuplication = "2",
            downloadDuplication = "4",
            uploadCompression = 2,
            downloadCompression = 2,
            baseEncodeData = false,
            minUploadMtu = "40",
            minDownloadMtu = "100",
            maxUploadMtu = "140",
            maxDownloadMtu = "3000",
            mtuTestRetriesResolvers = "3",
            mtuTestTimeoutResolvers = "2.0",
            mtuTestParallelismResolvers = "6",
            mtuTestRetriesLogs = "5",
            mtuTestTimeoutLogs = "2.0",
            mtuTestParallelismLogs = "32",
            rxTxWorkers = "4",
            tunnelProcessWorkers = "4",
            tunnelPacketTimeoutSeconds = "10.0",
            dispatcherIdlePollIntervalSeconds = "0.02",
            txChannelSize = "2048",
            rxChannelSize = "2048",
            resolverUdpConnectionPoolSize = "64",
            streamQueueInitialCapacity = "128",
            orphanQueueInitialCapacity = "32",
            dnsResponseFragmentStoreCapacity = "256",
            maxActiveStreams = "2048",
            localHandshakeTimeoutSeconds = "5.0",
            socksUdpAssociateReadTimeoutSeconds = "30.0",
            clientTerminalStreamRetentionSeconds = "45.0",
            clientCancelledSetupRetentionSeconds = "120.0",
            sessionInitRetryBaseSeconds = "1.0",
            sessionInitRetryStepSeconds = "1.0",
            sessionInitRetryLinearAfter = "5",
            sessionInitRetryMaxSeconds = "60.0",
            sessionInitBusyRetryIntervalSeconds = "60.0",
            autoTuneEnabled = false,
            trafficWarmupEnabled = false,
            trafficKeepaliveIntervalSeconds = "5",
        )
        else -> this
    }
}

private fun WhiteZiaSettings.copyStormRuntimeSettingsFrom(source: WhiteZiaSettings): WhiteZiaSettings {
    return copy(
        listenIp = source.listenIp,
        listenPort = source.listenPort,
        httpProxyEnabled = source.httpProxyEnabled,
        httpProxyPort = source.httpProxyPort,
        socks5Authentication = source.socks5Authentication,
        socksUsername = source.socksUsername,
        socksPassword = source.socksPassword,
        localDnsEnabled = source.localDnsEnabled,
        localDnsPort = source.localDnsPort,
        balancingStrategy = source.balancingStrategy,
        uploadDuplication = source.uploadDuplication,
        downloadDuplication = source.downloadDuplication,
        uploadCompression = source.uploadCompression,
        downloadCompression = source.downloadCompression,
        baseEncodeData = source.baseEncodeData,
        minUploadMtu = source.minUploadMtu,
        minDownloadMtu = source.minDownloadMtu,
        maxUploadMtu = source.maxUploadMtu,
        maxDownloadMtu = source.maxDownloadMtu,
        mtuTestRetriesResolvers = source.mtuTestRetriesResolvers,
        mtuTestTimeoutResolvers = source.mtuTestTimeoutResolvers,
        mtuTestParallelismResolvers = source.mtuTestParallelismResolvers,
        mtuTestRetriesLogs = source.mtuTestRetriesLogs,
        mtuTestTimeoutLogs = source.mtuTestTimeoutLogs,
        mtuTestParallelismLogs = source.mtuTestParallelismLogs,
        rxTxWorkers = source.rxTxWorkers,
        tunnelProcessWorkers = source.tunnelProcessWorkers,
        tunnelPacketTimeoutSeconds = source.tunnelPacketTimeoutSeconds,
        dispatcherIdlePollIntervalSeconds = source.dispatcherIdlePollIntervalSeconds,
        txChannelSize = source.txChannelSize,
        rxChannelSize = source.rxChannelSize,
        resolverUdpConnectionPoolSize = source.resolverUdpConnectionPoolSize,
        streamQueueInitialCapacity = source.streamQueueInitialCapacity,
        orphanQueueInitialCapacity = source.orphanQueueInitialCapacity,
        dnsResponseFragmentStoreCapacity = source.dnsResponseFragmentStoreCapacity,
        maxActiveStreams = source.maxActiveStreams,
        localHandshakeTimeoutSeconds = source.localHandshakeTimeoutSeconds,
        socksUdpAssociateReadTimeoutSeconds = source.socksUdpAssociateReadTimeoutSeconds,
        clientTerminalStreamRetentionSeconds = source.clientTerminalStreamRetentionSeconds,
        clientCancelledSetupRetentionSeconds = source.clientCancelledSetupRetentionSeconds,
        sessionInitRetryBaseSeconds = source.sessionInitRetryBaseSeconds,
        sessionInitRetryStepSeconds = source.sessionInitRetryStepSeconds,
        sessionInitRetryLinearAfter = source.sessionInitRetryLinearAfter,
        sessionInitRetryMaxSeconds = source.sessionInitRetryMaxSeconds,
        sessionInitBusyRetryIntervalSeconds = source.sessionInitBusyRetryIntervalSeconds,
        startupMode = source.startupMode,
        pingWatchdogSeconds = source.pingWatchdogSeconds,
        trafficWarmupEnabled = source.trafficWarmupEnabled,
        trafficWarmupProbeCount = source.trafficWarmupProbeCount,
        trafficKeepaliveIntervalSeconds = source.trafficKeepaliveIntervalSeconds,
        autoTuneEnabled = source.autoTuneEnabled,
        parallelTestSelectedConfigIds = source.parallelTestSelectedConfigIds,
        parallelTestAggressivePresetsEnabled = source.parallelTestAggressivePresetsEnabled,
        logLevel = source.logLevel,
    )
}

private fun WhiteZiaSettings.applyFastStartupRuntimeSettings(useCachedResolvers: Boolean): WhiteZiaSettings {
    if (!useCachedResolvers) {
        return this
    }
    return copy(
        mtuTestRetriesResolvers = "1",
        mtuTestTimeoutResolvers = "1.0",
        localHandshakeTimeoutSeconds = "3.0",
        sessionInitRetryBaseSeconds = "0.5",
        sessionInitRetryStepSeconds = "0.5",
    )
}
