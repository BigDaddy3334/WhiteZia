package shop.whitezia.client.model

data class WhiteZiaAutoTunePreset(
    val id: String,
    val label: String,
    val minUploadMtu: String,
    val maxUploadMtu: String,
    val minDownloadMtu: String,
    val maxDownloadMtu: String,
    val resolverTimeoutSeconds: String,
    val dnsResponseFragmentStoreCapacity: String,
    val uploadDuplication: String,
    val downloadDuplication: String,
    val uploadCompression: Int,
    val downloadCompression: Int,
    val stability: WhiteZiaAutoTunePresetStability = WhiteZiaAutoTunePresetStability.Stable,
)

enum class WhiteZiaAutoTunePresetStability {
    Stable,
    Aggressive,
}

object WhiteZiaAutoTunePresets {
    val all: List<WhiteZiaAutoTunePreset> = listOf(
        WhiteZiaAutoTunePreset(
            id = "iran-average",
            label = "Iran Default",
            minUploadMtu = "40",
            maxUploadMtu = "140",
            minDownloadMtu = "300",
            maxDownloadMtu = "3000",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "3",
            downloadDuplication = "7",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-low-mtu-scan",
            label = "Iran Low MTU Scan",
            minUploadMtu = "20",
            maxUploadMtu = "120",
            minDownloadMtu = "160",
            maxDownloadMtu = "768",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "3",
            downloadDuplication = "7",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-fast-low-mtu",
            label = "Iran Fast Low MTU",
            minUploadMtu = "20",
            maxUploadMtu = "325",
            minDownloadMtu = "100",
            maxDownloadMtu = "1270",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "100",
            uploadDuplication = "5",
            downloadDuplication = "10",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-compact-fixed",
            label = "Iran Compact Fixed",
            minUploadMtu = "62",
            maxUploadMtu = "62",
            minDownloadMtu = "414",
            maxDownloadMtu = "414",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "384",
            uploadDuplication = "6",
            downloadDuplication = "8",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-fixed-64-balanced",
            label = "Iran Fixed 64 Balanced",
            minUploadMtu = "64",
            maxUploadMtu = "64",
            minDownloadMtu = "756",
            maxDownloadMtu = "756",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "8",
            downloadDuplication = "8",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-mid-reliable",
            label = "Iran Mid Reliable",
            minUploadMtu = "120",
            maxUploadMtu = "160",
            minDownloadMtu = "652",
            maxDownloadMtu = "1110",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "5",
            downloadDuplication = "11",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-download-heavy",
            label = "Iran Download Heavy",
            minUploadMtu = "104",
            maxUploadMtu = "139",
            minDownloadMtu = "394",
            maxDownloadMtu = "1000",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "8",
            downloadDuplication = "30",
            uploadCompression = 2,
            downloadCompression = 2,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-fixed-64-aggressive",
            label = "Iran Fixed 64 Wide",
            minUploadMtu = "64",
            maxUploadMtu = "64",
            minDownloadMtu = "756",
            maxDownloadMtu = "1317",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "230",
            uploadDuplication = "14",
            downloadDuplication = "30",
            uploadCompression = 2,
            downloadCompression = 2,
            stability = WhiteZiaAutoTunePresetStability.Aggressive,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-large-download-aggressive",
            label = "Iran No Compression Max",
            minUploadMtu = "100",
            maxUploadMtu = "600",
            minDownloadMtu = "800",
            maxDownloadMtu = "6500",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "640",
            uploadDuplication = "23",
            downloadDuplication = "30",
            uploadCompression = 0,
            downloadCompression = 0,
            stability = WhiteZiaAutoTunePresetStability.Aggressive,
        ),
        WhiteZiaAutoTunePreset(
            id = "iran-wide-range-aggressive",
            label = "Iran Wide Range Max",
            minUploadMtu = "100",
            maxUploadMtu = "1000",
            minDownloadMtu = "200",
            maxDownloadMtu = "2667",
            resolverTimeoutSeconds = "2.5",
            dnsResponseFragmentStoreCapacity = "256",
            uploadDuplication = "15",
            downloadDuplication = "30",
            uploadCompression = 2,
            downloadCompression = 2,
            stability = WhiteZiaAutoTunePresetStability.Aggressive,
        ),
    )
}

object WhiteZiaParallelTest {
    const val EnabledByDefault = false
    const val MaxSelectedConfigs = 10
    private const val WhiteZiaConfigPrefix = "whitezia:"
    private const val SettingConfigPrefix = "setting:"

    val defaultConfigIds: List<String>
        get() = stableConfigIds

    val stableConfigIds: List<String>
        get() = WhiteZiaAutoTunePresets.all
            .filter { it.stability == WhiteZiaAutoTunePresetStability.Stable }
            .map { whiteZiaConfigId(it.id) }

    val aggressiveConfigIds: List<String>
        get() = WhiteZiaAutoTunePresets.all
            .filter { it.stability == WhiteZiaAutoTunePresetStability.Aggressive }
            .map { whiteZiaConfigId(it.id) }

    val allConfigIds: List<String>
        get() = WhiteZiaAutoTunePresets.all.map { whiteZiaConfigId(it.id) }

    fun whiteZiaConfigIds(includeAggressive: Boolean): List<String> {
        return if (includeAggressive) {
            allConfigIds
        } else {
            stableConfigIds
        }
    }

    fun whiteZiaConfigId(presetId: String): String = "$WhiteZiaConfigPrefix$presetId"

    fun settingConfigId(profileId: String): String = "$SettingConfigPrefix$profileId"

    fun presetIdFromConfigId(configId: String): String? {
        val normalized = normalizeLegacyConfigId(configId)
        return normalized.removePrefix(WhiteZiaConfigPrefix)
            .takeIf { normalized.startsWith(WhiteZiaConfigPrefix) && it.isNotBlank() }
    }

    fun settingProfileIdFromConfigId(configId: String): String? {
        return configId.removePrefix(SettingConfigPrefix)
            .takeIf { configId.startsWith(SettingConfigPrefix) && it.isNotBlank() }
    }

    fun normalizeConfigIds(
        configIds: List<String>,
        advancedProfiles: List<AdvancedSettingsProfile>,
        defaultIfEmpty: Boolean = true,
        includeAggressive: Boolean = false,
    ): List<String> {
        val whiteZiaIds = whiteZiaConfigIds(includeAggressive)
        val settingIds = advancedProfiles
            .filter { it.id.isNotBlank() && it.id != AdvancedSettingsProfile.DefaultId }
            .map { settingConfigId(it.id) }
        val availableIds = (whiteZiaIds + settingIds).toSet()
        val requestedIds = if (configIds.isEmpty() && defaultIfEmpty) {
            whiteZiaIds
        } else {
            configIds
        }
        return requestedIds
            .map(::normalizeLegacyConfigId)
            .distinct()
            .filter { it in availableIds }
            .take(MaxSelectedConfigs)
            .ifEmpty {
                if (defaultIfEmpty) {
                    whiteZiaIds.take(MaxSelectedConfigs)
                } else {
                    emptyList()
                }
            }
    }

    private fun normalizeLegacyConfigId(configId: String): String {
        return if (WhiteZiaAutoTunePresets.all.any { it.id == configId }) {
            whiteZiaConfigId(configId)
        } else {
            configId
        }
    }
}

fun WhiteZiaSettings.applyAutoTunePreset(preset: WhiteZiaAutoTunePreset): WhiteZiaSettings {
    return copy(
        minUploadMtu = preset.minUploadMtu,
        maxUploadMtu = preset.maxUploadMtu,
        minDownloadMtu = preset.minDownloadMtu,
        maxDownloadMtu = preset.maxDownloadMtu,
        mtuTestTimeoutResolvers = preset.resolverTimeoutSeconds,
        mtuTestTimeoutLogs = preset.resolverTimeoutSeconds,
        dnsResponseFragmentStoreCapacity = preset.dnsResponseFragmentStoreCapacity,
        uploadDuplication = preset.uploadDuplication,
        downloadDuplication = preset.downloadDuplication,
        uploadCompression = preset.uploadCompression,
        downloadCompression = preset.downloadCompression,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.recoverPersistedParallelTestPreset(): WhiteZiaSettings {
    val settings = syncSelectedConnectionProfileFields()
    if (!settings.matchesAutoTunePresetFields()) {
        return settings
    }

    val selectedProfile = settings.selectedAdvancedProfile()
    return when {
        selectedProfile.id != AdvancedSettingsProfile.DefaultId &&
            settings.matchesAdvancedProfile(selectedProfile) -> settings

        selectedProfile.id != AdvancedSettingsProfile.DefaultId -> settings
            .applyAdvancedProfile(selectedProfile)
            .copy(
                autoTuneEnabled = settings.autoTuneEnabled,
                parallelTestSelectedConfigIds = settings.parallelTestSelectedConfigIds,
            )
            .syncSelectedConnectionProfileFields()

        else -> settings
            .copyAutoTunePresetFieldsFrom(WhiteZiaSettings())
            .syncSelectedConnectionProfileFields()
    }
}

private fun WhiteZiaSettings.matchesAutoTunePresetFields(): Boolean {
    return WhiteZiaAutoTunePresets.all.any { preset ->
        minUploadMtu == preset.minUploadMtu &&
            maxUploadMtu == preset.maxUploadMtu &&
            minDownloadMtu == preset.minDownloadMtu &&
            maxDownloadMtu == preset.maxDownloadMtu &&
            mtuTestTimeoutResolvers == preset.resolverTimeoutSeconds &&
            mtuTestTimeoutLogs == preset.resolverTimeoutSeconds &&
            dnsResponseFragmentStoreCapacity == preset.dnsResponseFragmentStoreCapacity &&
            uploadDuplication == preset.uploadDuplication &&
            downloadDuplication == preset.downloadDuplication &&
            uploadCompression == preset.uploadCompression &&
            downloadCompression == preset.downloadCompression
    }
}

private fun WhiteZiaSettings.copyAutoTunePresetFieldsFrom(source: WhiteZiaSettings): WhiteZiaSettings {
    return copy(
        minUploadMtu = source.minUploadMtu,
        maxUploadMtu = source.maxUploadMtu,
        minDownloadMtu = source.minDownloadMtu,
        maxDownloadMtu = source.maxDownloadMtu,
        mtuTestTimeoutResolvers = source.mtuTestTimeoutResolvers,
        mtuTestTimeoutLogs = source.mtuTestTimeoutLogs,
        dnsResponseFragmentStoreCapacity = source.dnsResponseFragmentStoreCapacity,
        uploadDuplication = source.uploadDuplication,
        downloadDuplication = source.downloadDuplication,
        uploadCompression = source.uploadCompression,
        downloadCompression = source.downloadCompression,
    )
}
