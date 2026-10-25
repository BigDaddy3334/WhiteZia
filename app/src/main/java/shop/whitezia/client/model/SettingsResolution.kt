package shop.whitezia.client.model

fun WhiteZiaSettings.resolve(): ResolvedWhiteZiaSettings {
    fun boundedInt(raw: String, defaultValue: Int, minValue: Int, maxValue: Int): Int {
        return raw.trim().toIntOrNull()?.coerceIn(minValue, maxValue) ?: defaultValue
    }

    fun positiveDouble(raw: String, defaultValue: Double): Double {
        val value = raw.trim().toDoubleOrNull() ?: return defaultValue
        return if (value > 0.0) value else defaultValue
    }

    fun boundedDouble(raw: String, defaultValue: Double, minValue: Double, maxValue: Double): Double {
        val value = raw.trim().toDoubleOrNull() ?: return defaultValue
        return value.coerceIn(minValue, maxValue)
    }

    fun boundedPositiveDouble(raw: String, defaultValue: Double, minValue: Double, maxValue: Double): Double {
        val value = raw.trim().toDoubleOrNull() ?: return defaultValue
        if (value <= 0.0) {
            return defaultValue
        }
        return value.coerceIn(minValue, maxValue)
    }

    val resolvers = validateResolverText(resolverText).normalizedResolvers

    val resolvedRxTxWorkers = boundedInt(rxTxWorkers, defaultValue = 4, minValue = 1, maxValue = 64)
    val resolvedTunnelProcessWorkers = boundedInt(
        tunnelProcessWorkers,
        defaultValue = 4,
        minValue = 1,
        maxValue = 64,
    ).coerceAtLeast(resolvedRxTxWorkers)
    val resolvedSessionRetryBaseSeconds = boundedDouble(
        sessionInitRetryBaseSeconds,
        defaultValue = 1.0,
        minValue = 0.1,
        maxValue = 60.0,
    )
    val resolvedMinUploadMtu = boundedInt(minUploadMtu, defaultValue = 40, minValue = 1, maxValue = 65535)
    val resolvedMinDownloadMtu = boundedInt(minDownloadMtu, defaultValue = 300, minValue = 1, maxValue = 65535)
    val resolvedMaxUploadMtu = boundedInt(maxUploadMtu, defaultValue = 140, minValue = 1, maxValue = 65535)
        .coerceAtLeast(resolvedMinUploadMtu)
    val resolvedMaxDownloadMtu = boundedInt(maxDownloadMtu, defaultValue = 3000, minValue = 1, maxValue = 65535)
        .coerceAtLeast(resolvedMinDownloadMtu)

    return ResolvedWhiteZiaSettings(
        cottenDnsMode = CottenDnsMode.normalize(cottenDnsMode),
        cottenDnsResolverTransport = CottenDnsResolverTransport.normalize(cottenDnsResolverTransport),
        connectionMode = when (connectionMode) {
            "proxy", "vpn" -> connectionMode
            else -> "proxy"
        },
        protocolType = protocolType,
        resolverEntries = resolvers,
        listenIp = listenIp.trim().ifEmpty { "127.0.0.1" },
        listenPort = boundedInt(listenPort, defaultValue = 10886, minValue = 1, maxValue = 65535),
        httpProxyEnabled = httpProxyEnabled,
        httpProxyPort = boundedInt(httpProxyPort, defaultValue = 10887, minValue = 1, maxValue = 65535),
        socks5Authentication = socks5Authentication,
        socksUsername = socksUsername.take(255),
        socksPassword = socksPassword.take(255),
        balancingStrategy = listOf(1, 2, 3, 4).firstOrNull { it == balancingStrategy } ?: 4,
        uploadDuplication = boundedInt(uploadDuplication, defaultValue = 2, minValue = 1, maxValue = 30),
        downloadDuplication = boundedInt(downloadDuplication, defaultValue = 4, minValue = 1, maxValue = 30),
        uploadCompression = uploadCompression.coerceIn(0, 3),
        downloadCompression = downloadCompression.coerceIn(0, 3),
        baseEncodeData = baseEncodeData,
        minUploadMtu = resolvedMinUploadMtu,
        minDownloadMtu = resolvedMinDownloadMtu,
        maxUploadMtu = resolvedMaxUploadMtu,
        maxDownloadMtu = resolvedMaxDownloadMtu,
        mtuTestRetriesResolvers = boundedInt(mtuTestRetriesResolvers, defaultValue = 3, minValue = 1, maxValue = 100),
        mtuTestTimeoutResolvers = positiveDouble(mtuTestTimeoutResolvers, defaultValue = 2.5),
        mtuTestParallelismResolvers = boundedInt(mtuTestParallelismResolvers, defaultValue = 100, minValue = 1, maxValue = 1024),
        mtuTestRetriesLogs = boundedInt(mtuTestRetriesLogs, defaultValue = 5, minValue = 1, maxValue = 100),
        mtuTestTimeoutLogs = positiveDouble(mtuTestTimeoutLogs, defaultValue = 2.5),
        mtuTestParallelismLogs = boundedInt(mtuTestParallelismLogs, defaultValue = 32, minValue = 1, maxValue = 1024),
        rxTxWorkers = resolvedRxTxWorkers,
        tunnelProcessWorkers = resolvedTunnelProcessWorkers,
        tunnelPacketTimeoutSeconds = boundedDouble(
            tunnelPacketTimeoutSeconds,
            defaultValue = 10.0,
            minValue = 0.5,
            maxValue = 120.0,
        ),
        dispatcherIdlePollIntervalSeconds = boundedDouble(
            dispatcherIdlePollIntervalSeconds,
            defaultValue = 0.020,
            minValue = 0.001,
            maxValue = 1.0,
        ),
        txChannelSize = boundedInt(txChannelSize, defaultValue = 2048, minValue = 64, maxValue = 65536),
        rxChannelSize = boundedInt(rxChannelSize, defaultValue = 2048, minValue = 64, maxValue = 65536),
        resolverUdpConnectionPoolSize = boundedInt(
            resolverUdpConnectionPoolSize,
            defaultValue = 64,
            minValue = 1,
            maxValue = 1024,
        ),
        streamQueueInitialCapacity = boundedInt(
            streamQueueInitialCapacity,
            defaultValue = 128,
            minValue = 8,
            maxValue = 65536,
        ),
        orphanQueueInitialCapacity = boundedInt(
            orphanQueueInitialCapacity,
            defaultValue = 32,
            minValue = 4,
            maxValue = 4096,
        ),
        dnsResponseFragmentStoreCapacity = boundedInt(
            dnsResponseFragmentStoreCapacity,
            defaultValue = 256,
            minValue = 16,
            maxValue = 16384,
        ),
        maxActiveStreams = boundedInt(
            maxActiveStreams,
            defaultValue = 2048,
            minValue = 1,
            maxValue = 65535,
        ),
        localHandshakeTimeoutSeconds = boundedPositiveDouble(
            localHandshakeTimeoutSeconds,
            defaultValue = 5.0,
            minValue = 0.5,
            maxValue = 60.0,
        ),
        socksUdpAssociateReadTimeoutSeconds = boundedDouble(
            socksUdpAssociateReadTimeoutSeconds,
            defaultValue = 30.0,
            minValue = 1.0,
            maxValue = 3600.0,
        ),
        clientTerminalStreamRetentionSeconds = boundedDouble(
            clientTerminalStreamRetentionSeconds,
            defaultValue = 45.0,
            minValue = 1.0,
            maxValue = 3600.0,
        ),
        clientCancelledSetupRetentionSeconds = boundedDouble(
            clientCancelledSetupRetentionSeconds,
            defaultValue = 120.0,
            minValue = 1.0,
            maxValue = 3600.0,
        ),
        sessionInitRetryBaseSeconds = resolvedSessionRetryBaseSeconds,
        sessionInitRetryStepSeconds = boundedDouble(
            sessionInitRetryStepSeconds,
            defaultValue = 1.0,
            minValue = 0.0,
            maxValue = 60.0,
        ),
        sessionInitRetryLinearAfter = boundedInt(
            sessionInitRetryLinearAfter,
            defaultValue = 5,
            minValue = 0,
            maxValue = 1000,
        ),
        sessionInitRetryMaxSeconds = boundedDouble(
            sessionInitRetryMaxSeconds,
            defaultValue = 60.0,
            minValue = resolvedSessionRetryBaseSeconds,
            maxValue = 3600.0,
        ),
        sessionInitBusyRetryIntervalSeconds = boundedDouble(
            sessionInitBusyRetryIntervalSeconds,
            defaultValue = 60.0,
            minValue = 1.0,
            maxValue = 3600.0,
        ),
        localDnsEnabled = localDnsEnabled,
        localDnsPort = boundedInt(localDnsPort, defaultValue = 53, minValue = 1, maxValue = 65535),
        startupMode = when (startupMode) {
            "ask", "resolvers", "logs" -> startupMode
            else -> "resolvers"
        },
        pingWatchdogSeconds = boundedInt(pingWatchdogSeconds, defaultValue = 300, minValue = 0, maxValue = 3600),
        trafficWarmupEnabled = trafficWarmupEnabled,
        trafficWarmupProbeCount = boundedInt(trafficWarmupProbeCount, defaultValue = 4, minValue = 0, maxValue = 10),
        trafficKeepaliveIntervalSeconds = boundedInt(
            trafficKeepaliveIntervalSeconds,
            defaultValue = 5,
            minValue = 2,
            maxValue = 300,
        ),
        autoTuneEnabled = autoTuneEnabled,
        splitTunnelMode = normalizeSplitTunnelMode(splitTunnelMode),
        splitTunnelPackages = normalizePackageNames(splitTunnelPackages),
        logLevel = when (logLevel) {
            "DEBUG", "INFO", "WARN", "ERROR" -> logLevel
            else -> "DEBUG"
        },
    )
}
