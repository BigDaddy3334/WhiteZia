package shop.whitezia.client.runtime

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.AmneziaWgCandidate
import shop.whitezia.client.model.XrayCandidate
import shop.whitezia.client.model.StormDnsCandidate
import shop.whitezia.client.model.WhiteZiaOptions
import shop.whitezia.client.model.StormDnsServerProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaSettingsStore
import shop.whitezia.client.model.runtimeConnectionSettings
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.syncSelectedConnectionProfileFields

data class RuntimeLaunchRequest(
    val id: String,
    val serverProfile: StormDnsServerProfile?,
    val settings: WhiteZiaSettings,
)

object RuntimeLaunchRequestStore {
    private const val DirectoryName = "runtime-launch"
    private const val Extension = ".json"
    private val SafeIdRegex = Regex("[A-Za-z0-9._-]+")

    fun save(
        context: Context,
        requestId: String,
        serverProfile: StormDnsServerProfile?,
        settings: WhiteZiaSettings,
    ): RuntimeLaunchRequest {
        require(requestId.isSafeRequestId()) { "Invalid runtime launch request ID" }
        val request = RuntimeLaunchRequest(
            id = requestId,
            serverProfile = serverProfile,
            settings = settings.runtimeConnectionSettings().syncSelectedConnectionProfileFields(),
        )
        RuntimeFileLock.withLock(lockFile(context)) {
            saveRequestLocked(context, request)
        }
        return request
    }

    private fun saveRequestLocked(context: Context, request: RuntimeLaunchRequest) {
        pruneLaunchRequestsLocked(context)
        val atomicFile = AtomicFile(requestFile(context, request.id))
        val output = atomicFile.startWrite()
        try {
            output.write(encode(request).toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun load(context: Context, requestId: String): RuntimeLaunchRequest? {
        if (!requestId.isSafeRequestId()) {
            return null
        }
        return runCatching {
            RuntimeFileLock.withLock(lockFile(context)) {
                loadRequestLocked(context, requestId)
            }
        }.getOrNull()
    }

    private fun loadRequestLocked(context: Context, requestId: String): RuntimeLaunchRequest? {
        return runCatching {
            val text = AtomicFile(requestFile(context, requestId))
                .openRead()
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            decode(JSONObject(text))
        }.getOrNull()
    }

    suspend fun loadOrRecover(
        context: Context,
        requestId: String,
        attempts: Int = 8,
        retryDelayMillis: Long = 125L,
    ): RuntimeLaunchRequest? {
        if (!requestId.isSafeRequestId()) return null
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            load(context, requestId)?.let { return it }
            if (attempt + 1 < attempts) {
                delay(retryDelayMillis)
            }
        }

        return runCatching {
            val settings = WhiteZiaSettingsStore(context)
                .load()
                .runtimeConnectionSettings()
                .syncSelectedConnectionProfileFields()
            val profile = settings.selectedConnectionProfile()
            val serverProfile = if (
                profile.customServerDomain.isNotBlank() &&
                profile.customServerEncryptionKey.isNotBlank()
            ) {
                StormDnsServerProfile(
                    id = "custom",
                    label = "Custom StormDNS Server",
                    domain = profile.customServerDomain.trim().trimEnd('.'),
                    encryptionKey = profile.customServerEncryptionKey.trim(),
                    encryptionMethod = profile.customServerEncryptionMethod.coerceIn(0, 5),
                )
            } else {
                null
            }
            RuntimeFileLock.withLock(lockFile(context)) {
                // A producer may have saved the request while recovery loaded settings.
                loadRequestLocked(context, requestId) ?: RuntimeLaunchRequest(requestId, serverProfile, settings)
                    .also { recovered ->
                        saveRequestLocked(context, recovered)
                        Log.w(Tag, "Recovered missing runtime launch request $requestId")
                    }
            }
        }.onFailure { error ->
            Log.e(Tag, "Failed to recover runtime launch request $requestId", error)
        }.getOrNull()
    }

    fun delete(context: Context, requestId: String) {
        if (requestId.isSafeRequestId()) {
            RuntimeFileLock.withLock(lockFile(context)) {
                AtomicFile(requestFile(context, requestId)).delete()
            }
        }
    }

    private fun launchDirectory(context: Context): File {
        return File(context.noBackupFilesDir, DirectoryName)
    }

    private fun requestFile(context: Context, requestId: String): File {
        return File(launchDirectory(context), "$requestId$Extension")
    }

    private fun lockFile(context: Context): File {
        return File(launchDirectory(context), "requests.lock")
    }

    private fun pruneLaunchRequestsLocked(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        launchDirectory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(Extension) }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .forEachIndexed { index, file ->
                if (index >= MaxRetainedRequests || nowMillis - file.lastModified() > MaxRequestAgeMillis) {
                    runCatching { AtomicFile(file).delete() }
                }
            }
    }

    internal fun encode(request: RuntimeLaunchRequest): JSONObject {
        return JSONObject()
            .put("id", request.id)
            .put("serverProfile", request.serverProfile?.let(::encodeServerProfile) ?: JSONObject.NULL)
            .put("settings", encodeSettings(request.settings))
    }

    internal fun decode(json: JSONObject): RuntimeLaunchRequest {
        return RuntimeLaunchRequest(
            id = json.optString("id"),
            serverProfile = json.optJSONObject("serverProfile")?.let(::decodeServerProfile),
            settings = decodeSettings(json.getJSONObject("settings")),
        )
    }

    private fun encodeServerProfile(profile: StormDnsServerProfile): JSONObject {
        return JSONObject()
            .put("id", profile.id)
            .put("label", profile.label)
            .put("domain", profile.domain)
            .put("encryptionKey", profile.encryptionKey)
            .put("encryptionMethod", profile.encryptionMethod)
    }

    private fun decodeServerProfile(json: JSONObject): StormDnsServerProfile {
        return StormDnsServerProfile(
            id = json.optString("id"),
            label = json.optString("label"),
            domain = json.optString("domain"),
            encryptionKey = json.optString("encryptionKey"),
            encryptionMethod = json.optInt("encryptionMethod", 1),
        )
    }

    private fun encodeSettings(settings: WhiteZiaSettings): JSONObject {
        val splitTunnelPackages = JSONArray()
        settings.splitTunnelPackages.forEach { packageName ->
            splitTunnelPackages.put(packageName)
        }
        return JSONObject()
            .put("selectedConnectionProfileId", settings.selectedConnectionProfileId)
            .put("serverMode", settings.serverMode)
            .put("customServerDomain", settings.customServerDomain)
            .put("customServerEncryptionKey", settings.customServerEncryptionKey)
            .put("customServerEncryptionMethod", settings.customServerEncryptionMethod)
            .put("connectionMode", settings.connectionMode)
            .put("protocolType", settings.protocolType)
            .put("resolverText", settings.resolverText)
            .put("listenIp", settings.listenIp)
            .put("listenPort", settings.listenPort)
            .put("httpProxyEnabled", settings.httpProxyEnabled)
            .put("httpProxyPort", settings.httpProxyPort)
            .put("socks5Authentication", settings.socks5Authentication)
            .put("socksUsername", settings.socksUsername)
            .put("socksPassword", settings.socksPassword)
            .put("balancingStrategy", settings.balancingStrategy)
            .put("uploadDuplication", settings.uploadDuplication)
            .put("downloadDuplication", settings.downloadDuplication)
            .put("uploadCompression", settings.uploadCompression)
            .put("downloadCompression", settings.downloadCompression)
            .put("baseEncodeData", settings.baseEncodeData)
            .put("minUploadMtu", settings.minUploadMtu)
            .put("minDownloadMtu", settings.minDownloadMtu)
            .put("maxUploadMtu", settings.maxUploadMtu)
            .put("maxDownloadMtu", settings.maxDownloadMtu)
            .put("mtuTestRetriesResolvers", settings.mtuTestRetriesResolvers)
            .put("mtuTestTimeoutResolvers", settings.mtuTestTimeoutResolvers)
            .put("mtuTestParallelismResolvers", settings.mtuTestParallelismResolvers)
            .put("mtuTestRetriesLogs", settings.mtuTestRetriesLogs)
            .put("mtuTestTimeoutLogs", settings.mtuTestTimeoutLogs)
            .put("mtuTestParallelismLogs", settings.mtuTestParallelismLogs)
            .put("rxTxWorkers", settings.rxTxWorkers)
            .put("tunnelProcessWorkers", settings.tunnelProcessWorkers)
            .put("tunnelPacketTimeoutSeconds", settings.tunnelPacketTimeoutSeconds)
            .put("dispatcherIdlePollIntervalSeconds", settings.dispatcherIdlePollIntervalSeconds)
            .put("txChannelSize", settings.txChannelSize)
            .put("rxChannelSize", settings.rxChannelSize)
            .put("resolverUdpConnectionPoolSize", settings.resolverUdpConnectionPoolSize)
            .put("streamQueueInitialCapacity", settings.streamQueueInitialCapacity)
            .put("orphanQueueInitialCapacity", settings.orphanQueueInitialCapacity)
            .put("dnsResponseFragmentStoreCapacity", settings.dnsResponseFragmentStoreCapacity)
            .put("maxActiveStreams", settings.maxActiveStreams)
            .put("localHandshakeTimeoutSeconds", settings.localHandshakeTimeoutSeconds)
            .put("socksUdpAssociateReadTimeoutSeconds", settings.socksUdpAssociateReadTimeoutSeconds)
            .put("clientTerminalStreamRetentionSeconds", settings.clientTerminalStreamRetentionSeconds)
            .put("clientCancelledSetupRetentionSeconds", settings.clientCancelledSetupRetentionSeconds)
            .put("sessionInitRetryBaseSeconds", settings.sessionInitRetryBaseSeconds)
            .put("sessionInitRetryStepSeconds", settings.sessionInitRetryStepSeconds)
            .put("sessionInitRetryLinearAfter", settings.sessionInitRetryLinearAfter)
            .put("sessionInitRetryMaxSeconds", settings.sessionInitRetryMaxSeconds)
            .put("sessionInitBusyRetryIntervalSeconds", settings.sessionInitBusyRetryIntervalSeconds)
            .put("localDnsEnabled", settings.localDnsEnabled)
            .put("localDnsPort", settings.localDnsPort)
            .put("startupMode", settings.startupMode)
            .put("pingWatchdogSeconds", settings.pingWatchdogSeconds)
            .put("trafficWarmupEnabled", settings.trafficWarmupEnabled)
            .put("trafficWarmupProbeCount", settings.trafficWarmupProbeCount)
            .put("trafficKeepaliveIntervalSeconds", settings.trafficKeepaliveIntervalSeconds)
            .put("autoTuneEnabled", settings.autoTuneEnabled)
            .put("fullVpnPerformanceWarningDismissed", settings.fullVpnPerformanceWarningDismissed)
            .put("splitTunnelMode", settings.splitTunnelMode)
            .put("splitTunnelPackages", splitTunnelPackages)
            .put("transportMode", settings.transportMode)
            .put("manualMode", settings.manualMode)
            .put("forceDnsTunnel", settings.forceDnsTunnel)
            .put("operatorCode", settings.operatorCode)
            .put("customResolversEnabled", settings.customResolversEnabled)
            .put("customResolverText", settings.customResolverText)
            .put("amneziaWgConfig", settings.amneziaWgConfig)
            .put("amneziaWgCandidates", JSONArray().apply {
                settings.amneziaWgCandidates.forEach { candidate ->
                    put(JSONObject()
                        .put("nodeId", candidate.nodeId)
                        .put("role", candidate.role)
                        .put("config", candidate.config))
                }
            })
            .put("activeAmneziaWgNodeId", settings.activeAmneziaWgNodeId)
            .put("xrayUri", settings.xrayUri)
            .put("xrayDailyLimitBytes", settings.xrayDailyLimitBytes)
            .put("xrayCandidates", JSONArray().apply {
                settings.xrayCandidates.forEach { candidate ->
                    put(JSONObject()
                        .put("nodeId", candidate.nodeId)
                        .put("role", candidate.role)
                        .put("uri", candidate.uri)
                        .put("directUri", candidate.directUri)
                        .put("dailyLimitBytes", candidate.dailyLimitBytes))
                }
            })
            .put("activeXrayNodeId", settings.activeXrayNodeId)
            .put("stormDnsCandidates", JSONArray().apply {
                settings.stormDnsCandidates.forEach { candidate ->
                    put(JSONObject()
                        .put("nodeId", candidate.nodeId)
                        .put("role", candidate.role)
                        .put("domain", candidate.domain)
                        .put("encryptionKey", candidate.encryptionKey)
                        .put("encryptionMethod", candidate.encryptionMethod))
                }
            })
            .put("activeStormDnsNodeId", settings.activeStormDnsNodeId)
            .put("logLevel", settings.logLevel)
    }

    private fun decodeSettings(json: JSONObject): WhiteZiaSettings {
        val selectedConnectionProfileId = json.optString("selectedConnectionProfileId", ConnectionProfile.DefaultId)
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = selectedConnectionProfileId,
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = selectedConnectionProfileId.ifBlank { ConnectionProfile.DefaultId },
                    name = "Connection",
                    serverMode = json.optString("serverMode", "custom"),
                    customServerDomain = json.optString("customServerDomain"),
                    customServerEncryptionKey = json.optString("customServerEncryptionKey"),
                    customServerEncryptionMethod = json.optInt("customServerEncryptionMethod", 1),
                    connectionMode = json.optString("connectionMode", "proxy"),
                ),
            ),
            serverMode = json.optString("serverMode", "custom"),
            customServerDomain = json.optString("customServerDomain"),
            customServerEncryptionKey = json.optString("customServerEncryptionKey"),
            customServerEncryptionMethod = json.optInt("customServerEncryptionMethod", 1),
            connectionMode = json.optString("connectionMode", "proxy"),
            protocolType = json.optString("protocolType", "SOCKS5"),
            resolverText = json.optString("resolverText"),
            listenIp = json.optString("listenIp", "127.0.0.1"),
            listenPort = json.optString("listenPort", "10886"),
            httpProxyEnabled = json.optBoolean("httpProxyEnabled", true),
            httpProxyPort = json.optString("httpProxyPort", "10887"),
            socks5Authentication = json.optBoolean("socks5Authentication", false),
            socksUsername = json.optString("socksUsername"),
            socksPassword = json.optString("socksPassword"),
            balancingStrategy = json.optInt("balancingStrategy", 3),
            uploadDuplication = json.optString("uploadDuplication", "3"),
            downloadDuplication = json.optString("downloadDuplication", "7"),
            uploadCompression = json.optInt("uploadCompression", 2),
            downloadCompression = json.optInt("downloadCompression", 2),
            baseEncodeData = json.optBoolean("baseEncodeData", false),
            minUploadMtu = json.optString("minUploadMtu", "40"),
            minDownloadMtu = json.optString("minDownloadMtu", "300"),
            maxUploadMtu = json.optString("maxUploadMtu", "140"),
            maxDownloadMtu = json.optString("maxDownloadMtu", "3000"),
            mtuTestRetriesResolvers = json.optString("mtuTestRetriesResolvers", "3"),
            mtuTestTimeoutResolvers = json.optString("mtuTestTimeoutResolvers", "2.0"),
            mtuTestParallelismResolvers = json.optString("mtuTestParallelismResolvers", "100"),
            mtuTestRetriesLogs = json.optString("mtuTestRetriesLogs", "5"),
            mtuTestTimeoutLogs = json.optString("mtuTestTimeoutLogs", "2.0"),
            mtuTestParallelismLogs = json.optString("mtuTestParallelismLogs", "32"),
            rxTxWorkers = json.optString("rxTxWorkers", "4"),
            tunnelProcessWorkers = json.optString("tunnelProcessWorkers", "4"),
            tunnelPacketTimeoutSeconds = json.optString("tunnelPacketTimeoutSeconds", "10.0"),
            dispatcherIdlePollIntervalSeconds = json.optString("dispatcherIdlePollIntervalSeconds", "0.020"),
            txChannelSize = json.optString("txChannelSize", "2048"),
            rxChannelSize = json.optString("rxChannelSize", "2048"),
            resolverUdpConnectionPoolSize = json.optString("resolverUdpConnectionPoolSize", "64"),
            streamQueueInitialCapacity = json.optString("streamQueueInitialCapacity", "128"),
            orphanQueueInitialCapacity = json.optString("orphanQueueInitialCapacity", "32"),
            dnsResponseFragmentStoreCapacity = json.optString("dnsResponseFragmentStoreCapacity", "256"),
            maxActiveStreams = json.optString("maxActiveStreams", "2048"),
            localHandshakeTimeoutSeconds = json.optString("localHandshakeTimeoutSeconds", "5.0"),
            socksUdpAssociateReadTimeoutSeconds = json.optString("socksUdpAssociateReadTimeoutSeconds", "30.0"),
            clientTerminalStreamRetentionSeconds = json.optString("clientTerminalStreamRetentionSeconds", "45.0"),
            clientCancelledSetupRetentionSeconds = json.optString("clientCancelledSetupRetentionSeconds", "120.0"),
            sessionInitRetryBaseSeconds = json.optString("sessionInitRetryBaseSeconds", "1.0"),
            sessionInitRetryStepSeconds = json.optString("sessionInitRetryStepSeconds", "1.0"),
            sessionInitRetryLinearAfter = json.optString("sessionInitRetryLinearAfter", "5"),
            sessionInitRetryMaxSeconds = json.optString("sessionInitRetryMaxSeconds", "60.0"),
            sessionInitBusyRetryIntervalSeconds = json.optString("sessionInitBusyRetryIntervalSeconds", "60.0"),
            localDnsEnabled = json.optBoolean("localDnsEnabled", false),
            localDnsPort = json.optString("localDnsPort", "10888"),
            startupMode = json.optString("startupMode", "resolvers"),
            pingWatchdogSeconds = json.optString("pingWatchdogSeconds", "300"),
            trafficWarmupEnabled = json.optBoolean("trafficWarmupEnabled", false),
            trafficWarmupProbeCount = json.optString("trafficWarmupProbeCount", "4"),
            trafficKeepaliveIntervalSeconds = json.optString("trafficKeepaliveIntervalSeconds", "5"),
            autoTuneEnabled = json.optBoolean("autoTuneEnabled", false),
            fullVpnPerformanceWarningDismissed = json.optBoolean("fullVpnPerformanceWarningDismissed", false),
            splitTunnelMode = json.optString("splitTunnelMode", "off"),
            splitTunnelPackages = decodeStringArray(json.optJSONArray("splitTunnelPackages")),
            transportMode = json.optString("transportMode", "auto"),
            manualMode = json.optBoolean("manualMode", false),
            forceDnsTunnel = json.optBoolean("forceDnsTunnel", false),
            operatorCode = json.optString("operatorCode", WhiteZiaOptions.OperatorMegafonYota),
            customResolversEnabled = json.optBoolean("customResolversEnabled", false),
            customResolverText = json.optString("customResolverText"),
            amneziaWgConfig = json.optString("amneziaWgConfig"),
            amneziaWgCandidates = decodeCandidates(json.optJSONArray("amneziaWgCandidates")) { candidate ->
                AmneziaWgCandidate(
                    nodeId = candidate.getString("nodeId"),
                    role = candidate.optString("role"),
                    config = candidate.getString("config"),
                )
            },
            activeAmneziaWgNodeId = json.optString("activeAmneziaWgNodeId"),
            xrayUri = json.optString("xrayUri"),
            xrayDailyLimitBytes = json.optLong("xrayDailyLimitBytes", 0L),
            xrayCandidates = decodeCandidates(json.optJSONArray("xrayCandidates")) { candidate ->
                XrayCandidate(
                    nodeId = candidate.getString("nodeId"),
                    role = candidate.optString("role"),
                    uri = candidate.getString("uri"),
                    directUri = candidate.optString("directUri"),
                    dailyLimitBytes = candidate.optLong("dailyLimitBytes", 0L),
                )
            },
            activeXrayNodeId = json.optString("activeXrayNodeId"),
            stormDnsCandidates = decodeCandidates(json.optJSONArray("stormDnsCandidates")) { candidate ->
                StormDnsCandidate(
                    nodeId = candidate.getString("nodeId"),
                    role = candidate.optString("role"),
                    domain = candidate.getString("domain"),
                    encryptionKey = candidate.getString("encryptionKey"),
                    encryptionMethod = candidate.optInt("encryptionMethod", 1),
                )
            },
            activeStormDnsNodeId = json.optString("activeStormDnsNodeId"),
            logLevel = json.optString("logLevel", "WARN"),
        )
        return settings.syncSelectedConnectionProfileFields()
    }

    private fun decodeStringArray(array: JSONArray?): List<String> {
        if (array == null) {
            return emptyList()
        }
        return List(array.length()) { index ->
            array.optString(index)
        }.filter(String::isNotBlank)
    }

    private fun <T> decodeCandidates(array: JSONArray?, decode: (JSONObject) -> T): List<T> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { candidate -> runCatching { decode(candidate) }.getOrNull() }
        }
    }

    private fun String.isSafeRequestId(): Boolean {
        return isNotBlank() && length <= 128 && SafeIdRegex.matches(this)
    }

    private const val MaxRetainedRequests = 64
    private const val MaxRequestAgeMillis = 7L * 24L * 60L * 60L * 1_000L
    private const val Tag = "RuntimeLaunchRequest"
}
