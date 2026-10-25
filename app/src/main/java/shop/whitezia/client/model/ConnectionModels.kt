package shop.whitezia.client.model

import java.io.Serializable

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

data class Choice<T>(
    val value: T,
    val label: String,
)

data class StormDnsServerProfile(
    val id: String,
    val label: String,
    val domain: String,
    val encryptionKey: String,
    val encryptionMethod: Int,
)

data class AmneziaWgCandidate(
    val nodeId: String,
    val role: String,
    val config: String,
) : Serializable

data class XrayCandidate(
    val nodeId: String,
    val role: String,
    val uri: String,
    val dailyLimitBytes: Long = 0L,
    val directUri: String = "",
) : Serializable

data class StormDnsCandidate(
    val nodeId: String,
    val role: String,
    val domain: String,
    val encryptionKey: String,
    val encryptionMethod: Int,
) : Serializable

data class ConnectionProfile(
    val id: String,
    val name: String,
    val serverMode: String = "custom",
    val customServerDomain: String = "",
    val customServerEncryptionKey: String = "",
    val customServerEncryptionMethod: Int = 1,
    val resolverProfileId: String = "",
    val connectionMode: String = "proxy",
) : Serializable {
    companion object {
        const val DefaultId = "default"

        fun defaultProfile(): ConnectionProfile {
            return ConnectionProfile(
                id = DefaultId,
                name = "Connection",
                serverMode = "custom",
            )
        }

        fun fromSettings(settings: WhiteZiaSettings): ConnectionProfile {
            return ConnectionProfile(
                id = DefaultId,
                name = "Connection",
                serverMode = "custom",
                customServerDomain = settings.customServerDomain,
                customServerEncryptionKey = settings.customServerEncryptionKey,
                customServerEncryptionMethod = settings.customServerEncryptionMethod,
                resolverProfileId = settings.selectedResolverProfileId,
                connectionMode = settings.connectionMode,
            )
        }
    }
}

data class ResolverProfile(
    val id: String,
    val name: String,
    val resolverText: String,
) : Serializable {
    companion object {
        const val DefaultId = "resolver-default"
        const val DefaultName = "Default Resolver"
        const val CustomId = "resolver-custom"
        const val CustomName = "Custom Resolver"

        fun newId(): String = "resolver-${System.currentTimeMillis()}"

        fun defaultProfile(resolverText: String): ResolverProfile {
            return ResolverProfile(
                id = DefaultId,
                name = DefaultName,
                resolverText = resolverText,
            )
        }
    }
}

data class AdvancedSettingsProfile(
    val id: String,
    val name: String,
    val listenIp: String,
    val listenPort: String,
    val httpProxyEnabled: Boolean,
    val httpProxyPort: String,
    val socks5Authentication: Boolean,
    val socksUsername: String,
    val socksPassword: String,
    val balancingStrategy: Int,
    val uploadDuplication: String,
    val downloadDuplication: String,
    val uploadCompression: Int,
    val downloadCompression: Int,
    val baseEncodeData: Boolean,
    val minUploadMtu: String,
    val minDownloadMtu: String,
    val maxUploadMtu: String,
    val maxDownloadMtu: String,
    val mtuTestRetriesResolvers: String,
    val mtuTestTimeoutResolvers: String,
    val mtuTestParallelismResolvers: String,
    val mtuTestRetriesLogs: String,
    val mtuTestTimeoutLogs: String,
    val mtuTestParallelismLogs: String,
    val rxTxWorkers: String,
    val tunnelProcessWorkers: String,
    val tunnelPacketTimeoutSeconds: String,
    val dispatcherIdlePollIntervalSeconds: String,
    val txChannelSize: String,
    val rxChannelSize: String,
    val resolverUdpConnectionPoolSize: String,
    val streamQueueInitialCapacity: String,
    val orphanQueueInitialCapacity: String,
    val dnsResponseFragmentStoreCapacity: String,
    val maxActiveStreams: String,
    val localHandshakeTimeoutSeconds: String,
    val socksUdpAssociateReadTimeoutSeconds: String,
    val clientTerminalStreamRetentionSeconds: String,
    val clientCancelledSetupRetentionSeconds: String,
    val sessionInitRetryBaseSeconds: String,
    val sessionInitRetryStepSeconds: String,
    val sessionInitRetryLinearAfter: String,
    val sessionInitRetryMaxSeconds: String,
    val sessionInitBusyRetryIntervalSeconds: String,
    val localDnsEnabled: Boolean,
    val localDnsPort: String,
    val startupMode: String,
    val pingWatchdogSeconds: String,
    val trafficWarmupEnabled: Boolean,
    val trafficWarmupProbeCount: String,
    val trafficKeepaliveIntervalSeconds: String,
    val autoTuneEnabled: Boolean,
    val logLevel: String,
    val cottenDnsResolverTransport: String = CottenDnsResolverTransport.Auto.storedValue,
) : Serializable {
    companion object {
        const val DefaultId = "advanced-default"

        fun defaultProfile(): AdvancedSettingsProfile {
            return fromSettings(
                settings = WhiteZiaSettings(),
                id = DefaultId,
                name = "Default",
            )
        }

        fun newId(): String = "advanced-${System.currentTimeMillis()}"

        fun fromSettings(
            settings: WhiteZiaSettings,
            id: String = "",
            name: String = "Advanced Settings",
        ): AdvancedSettingsProfile {
            return AdvancedSettingsProfile(
                id = id,
                name = name,
                listenIp = settings.listenIp,
                listenPort = settings.listenPort,
                httpProxyEnabled = settings.httpProxyEnabled,
                httpProxyPort = settings.httpProxyPort,
                socks5Authentication = settings.socks5Authentication,
                socksUsername = settings.socksUsername,
                socksPassword = settings.socksPassword,
                balancingStrategy = settings.balancingStrategy,
                uploadDuplication = settings.uploadDuplication,
                downloadDuplication = settings.downloadDuplication,
                uploadCompression = settings.uploadCompression,
                downloadCompression = settings.downloadCompression,
                baseEncodeData = settings.baseEncodeData,
                minUploadMtu = settings.minUploadMtu,
                minDownloadMtu = settings.minDownloadMtu,
                maxUploadMtu = settings.maxUploadMtu,
                maxDownloadMtu = settings.maxDownloadMtu,
                mtuTestRetriesResolvers = settings.mtuTestRetriesResolvers,
                mtuTestTimeoutResolvers = settings.mtuTestTimeoutResolvers,
                mtuTestParallelismResolvers = settings.mtuTestParallelismResolvers,
                mtuTestRetriesLogs = settings.mtuTestRetriesLogs,
                mtuTestTimeoutLogs = settings.mtuTestTimeoutLogs,
                mtuTestParallelismLogs = settings.mtuTestParallelismLogs,
                rxTxWorkers = settings.rxTxWorkers,
                tunnelProcessWorkers = settings.tunnelProcessWorkers,
                tunnelPacketTimeoutSeconds = settings.tunnelPacketTimeoutSeconds,
                dispatcherIdlePollIntervalSeconds = settings.dispatcherIdlePollIntervalSeconds,
                txChannelSize = settings.txChannelSize,
                rxChannelSize = settings.rxChannelSize,
                resolverUdpConnectionPoolSize = settings.resolverUdpConnectionPoolSize,
                streamQueueInitialCapacity = settings.streamQueueInitialCapacity,
                orphanQueueInitialCapacity = settings.orphanQueueInitialCapacity,
                dnsResponseFragmentStoreCapacity = settings.dnsResponseFragmentStoreCapacity,
                maxActiveStreams = settings.maxActiveStreams,
                localHandshakeTimeoutSeconds = settings.localHandshakeTimeoutSeconds,
                socksUdpAssociateReadTimeoutSeconds = settings.socksUdpAssociateReadTimeoutSeconds,
                clientTerminalStreamRetentionSeconds = settings.clientTerminalStreamRetentionSeconds,
                clientCancelledSetupRetentionSeconds = settings.clientCancelledSetupRetentionSeconds,
                sessionInitRetryBaseSeconds = settings.sessionInitRetryBaseSeconds,
                sessionInitRetryStepSeconds = settings.sessionInitRetryStepSeconds,
                sessionInitRetryLinearAfter = settings.sessionInitRetryLinearAfter,
                sessionInitRetryMaxSeconds = settings.sessionInitRetryMaxSeconds,
                sessionInitBusyRetryIntervalSeconds = settings.sessionInitBusyRetryIntervalSeconds,
                localDnsEnabled = settings.localDnsEnabled,
                localDnsPort = settings.localDnsPort,
                startupMode = settings.startupMode,
                pingWatchdogSeconds = settings.pingWatchdogSeconds,
                trafficWarmupEnabled = settings.trafficWarmupEnabled,
                trafficWarmupProbeCount = settings.trafficWarmupProbeCount,
                trafficKeepaliveIntervalSeconds = settings.trafficKeepaliveIntervalSeconds,
                autoTuneEnabled = settings.autoTuneEnabled,
                logLevel = settings.logLevel,
                cottenDnsResolverTransport = CottenDnsResolverTransport.normalize(settings.cottenDnsResolverTransport),
            )
        }
    }
}

data class ResolverTextValidation(
    val normalizedResolvers: List<String>,
    val invalidEntries: List<String>,
) {
    val normalizedText: String
        get() = normalizedResolvers.joinToString("\n")

    val isValid: Boolean
        get() = normalizedResolvers.isNotEmpty() && invalidEntries.isEmpty()
}
