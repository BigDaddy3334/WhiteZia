package shop.whitezia.client.model

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhiteZiaModelsTest {
    @Test
    fun defaultSettingsStartWithBlankCustomConnection() {
        val settings = WhiteZiaSettings().syncSelectedConnectionProfileFields()
        val profile = settings.selectedConnectionProfile()

        assertEquals(ConnectionProfile.DefaultId, profile.id)
        assertEquals("custom", profile.serverMode)
        assertEquals("", profile.customServerDomain)
        assertEquals("", profile.customServerEncryptionKey)
        assertEquals("custom", settings.serverMode)
    }

    @Test
    fun runtimeConnectionSettingsEnableTunneledDnsForProxyMode() {
        val runtimeSettings = WhiteZiaSettings(
            connectionMode = "proxy",
            localDnsEnabled = false,
            localDnsPort = "53",
        ).runtimeConnectionSettings()
        val resolvedSettings = runtimeSettings.resolve()

        assertEquals("proxy", resolvedSettings.connectionMode)
        assertEquals(true, resolvedSettings.localDnsEnabled)
        assertEquals(WhiteZiaRuntimeProxy.LocalDnsPortInt, resolvedSettings.localDnsPort)
    }

    @Test
    fun runtimeConnectionSettingsKeepFullVpnOnVirtualTunDnsPath() {
        val runtimeSettings = WhiteZiaSettings(
            connectionMode = "vpn",
            listenIp = "0.0.0.0",
            listenPort = "12345",
            httpProxyEnabled = true,
            socks5Authentication = true,
            localDnsEnabled = true,
            localDnsPort = "53",
        ).runtimeConnectionSettings()
        val resolvedSettings = runtimeSettings.resolve()

        assertEquals("vpn", resolvedSettings.connectionMode)
        assertEquals(WhiteZiaRuntimeProxy.ListenIp, resolvedSettings.listenIp)
        assertEquals(WhiteZiaRuntimeProxy.ListenPortInt, resolvedSettings.listenPort)
        assertEquals(false, resolvedSettings.httpProxyEnabled)
        assertEquals(false, resolvedSettings.socks5Authentication)
        assertEquals(false, resolvedSettings.localDnsEnabled)
        assertEquals(WhiteZiaRuntimeProxy.LocalDnsPortInt, resolvedSettings.localDnsPort)
    }

    @Test
    fun recoverPersistedParallelTestPresetResetsUnsavedBuiltInPreset() {
        val preset = WhiteZiaAutoTunePresets.all.first()
        val settings = WhiteZiaSettings(autoTuneEnabled = true)
            .applyAutoTunePreset(preset)

        val recoveredSettings = settings.recoverPersistedParallelTestPreset()
        val defaults = WhiteZiaSettings()

        assertEquals(defaults.minUploadMtu, recoveredSettings.minUploadMtu)
        assertEquals(defaults.maxUploadMtu, recoveredSettings.maxUploadMtu)
        assertEquals(defaults.minDownloadMtu, recoveredSettings.minDownloadMtu)
        assertEquals(defaults.maxDownloadMtu, recoveredSettings.maxDownloadMtu)
        assertEquals(defaults.uploadDuplication, recoveredSettings.uploadDuplication)
        assertEquals(defaults.downloadDuplication, recoveredSettings.downloadDuplication)
        assertEquals(true, recoveredSettings.autoTuneEnabled)
    }

    @Test
    fun recoverPersistedParallelTestPresetKeepsSavedAdvancedProfile() {
        val preset = WhiteZiaAutoTunePresets.all.first()
        val presetSettings = WhiteZiaSettings()
            .applyAutoTunePreset(preset)
            .copy(autoTuneEnabled = false)
        val profile = AdvancedSettingsProfile.fromSettings(
            settings = presetSettings,
            id = "advanced-saved",
            name = "Saved preset",
        )
        val settings = WhiteZiaSettings(
            selectedAdvancedProfileId = profile.id,
            advancedProfiles = listOf(profile),
        ).applyAdvancedProfile(profile)

        val recoveredSettings = settings.recoverPersistedParallelTestPreset()

        assertEquals(profile.id, recoveredSettings.selectedAdvancedProfileId)
        assertEquals(preset.minUploadMtu, recoveredSettings.minUploadMtu)
        assertEquals(preset.maxDownloadMtu, recoveredSettings.maxDownloadMtu)
        assertEquals(preset.uploadDuplication, recoveredSettings.uploadDuplication)
        assertEquals(preset.downloadDuplication, recoveredSettings.downloadDuplication)
    }

    @Test
    fun recoverPersistedParallelTestPresetRestoresDirtySelectedAdvancedProfile() {
        val preset = WhiteZiaAutoTunePresets.all.first()
        val customSettings = WhiteZiaSettings(
            minUploadMtu = "42",
            maxUploadMtu = "240",
            minDownloadMtu = "420",
            maxDownloadMtu = "2400",
        )
        val profile = AdvancedSettingsProfile.fromSettings(
            settings = customSettings,
            id = "advanced-custom",
            name = "Custom",
        )
        val accidentallyPersistedSettings = WhiteZiaSettings(
            selectedAdvancedProfileId = profile.id,
            advancedProfiles = listOf(profile),
            autoTuneEnabled = true,
        ).applyAutoTunePreset(preset)

        val recoveredSettings = accidentallyPersistedSettings.recoverPersistedParallelTestPreset()

        assertEquals(profile.id, recoveredSettings.selectedAdvancedProfileId)
        assertEquals(customSettings.minUploadMtu, recoveredSettings.minUploadMtu)
        assertEquals(customSettings.maxUploadMtu, recoveredSettings.maxUploadMtu)
        assertEquals(customSettings.minDownloadMtu, recoveredSettings.minDownloadMtu)
        assertEquals(customSettings.maxDownloadMtu, recoveredSettings.maxDownloadMtu)
        assertEquals(true, recoveredSettings.autoTuneEnabled)
    }

    @Test
    fun syncSelectedConnectionProfileFieldsUsesSelectedResolverProfileText() {
        val resolverProfile = ResolverProfile(
            id = "resolver-main",
            name = "Main",
            resolverText = "1.1.1.1\n8.8.8.8",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = "profile-main",
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = "profile-main",
                    name = "Main",
                    resolverProfileId = resolverProfile.id,
                ),
            ),
            selectedResolverProfileId = resolverProfile.id,
            resolverProfiles = listOf(resolverProfile),
            resolverText = "",
        )

        val syncedSettings = settings.syncSelectedConnectionProfileFields()

        assertEquals(resolverProfile.resolverText, syncedSettings.resolverText)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), syncedSettings.resolve().resolverEntries)
    }

    @Test
    fun updateManualResolverTextClearsSelectedResolverProfileAndKeepsTypedResolvers() {
        val resolverProfile = ResolverProfile(
            id = "resolver-main",
            name = "Main",
            resolverText = "1.1.1.1",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = "profile-main",
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = "profile-main",
                    name = "Main",
                    resolverProfileId = resolverProfile.id,
                ),
            ),
            selectedResolverProfileId = resolverProfile.id,
            resolverProfiles = listOf(resolverProfile),
            resolverText = resolverProfile.resolverText,
        )
        val typedResolvers = "1.1.1.1\n8.8.8.8\n9.9.9.9"

        val updatedSettings = settings.updateManualResolverText(typedResolvers)

        assertEquals("", updatedSettings.selectedResolverProfileId)
        assertEquals("", updatedSettings.selectedConnectionProfile().resolverProfileId)
        assertEquals(typedResolvers, updatedSettings.resolverText)
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            updatedSettings.resolve().resolverEntries,
        )
    }

    @Test
    fun updateManualResolverTextClearsResolverProfileWhenSelectedConnectionIdIsStale() {
        val resolverProfile = ResolverProfile(
            id = "resolver-main",
            name = "Main",
            resolverText = "1.1.1.1",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = "missing-profile",
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = "profile-main",
                    name = "Main",
                    resolverProfileId = resolverProfile.id,
                ),
            ),
            selectedResolverProfileId = resolverProfile.id,
            resolverProfiles = listOf(resolverProfile),
            resolverText = resolverProfile.resolverText,
        )

        val updatedSettings = settings.updateManualResolverText("8.8.8.8\n9.9.9.9")

        assertEquals("profile-main", updatedSettings.selectedConnectionProfileId)
        assertEquals("", updatedSettings.selectedConnectionProfile().resolverProfileId)
        assertEquals("", updatedSettings.selectedResolverProfileId)
        assertEquals(listOf("8.8.8.8", "9.9.9.9"), updatedSettings.resolve().resolverEntries)
    }

    @Test
    fun syncSelectedConnectionProfileFieldsPersistsSelectedConnectionMode() {
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = "profile-main",
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = "profile-main",
                    name = "Main",
                    connectionMode = "proxy",
                ),
            ),
            connectionMode = "vpn",
        )

        val syncedSettings = settings.syncSelectedConnectionProfileFields()

        assertEquals("vpn", syncedSettings.connectionMode)
        assertEquals("vpn", syncedSettings.selectedConnectionProfile().connectionMode)
    }

    @Test
    fun syncSelectedConnectionProfileFieldsNormalizesThemeMode() {
        val settings = WhiteZiaSettings(themeMode = "invalid")

        val syncedSettings = settings.syncSelectedConnectionProfileFields()

        assertEquals(WhiteZiaThemeMode.System, syncedSettings.themeMode)
    }

    @Test
    fun moveConnectionProfileReordersCustomProfilesForSelectionLists() {
        val first = ConnectionProfile(id = "profile-first", name = "First", serverMode = "custom")
        val second = ConnectionProfile(id = "profile-second", name = "Second", serverMode = "custom")
        val third = ConnectionProfile(id = "profile-third", name = "Third", serverMode = "custom")
        val settings = WhiteZiaSettings(
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), first, second, third),
        )

        val updatedSettings = settings.moveConnectionProfile("profile-third", -1)

        assertEquals(
            listOf(ConnectionProfile.DefaultId, "profile-first", "profile-third", "profile-second"),
            updatedSettings.normalizedConnectionProfiles().map { it.id },
        )
    }

    @Test
    fun moveConnectionProfileToIndexReordersToDropTarget() {
        val first = ConnectionProfile(id = "profile-first", name = "First", serverMode = "custom")
        val second = ConnectionProfile(id = "profile-second", name = "Second", serverMode = "custom")
        val third = ConnectionProfile(id = "profile-third", name = "Third", serverMode = "custom")
        val settings = WhiteZiaSettings(
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), first, second, third),
        )

        val updatedSettings = settings.moveConnectionProfileToIndex("profile-first", 2)

        assertEquals(
            listOf(ConnectionProfile.DefaultId, "profile-second", "profile-first", "profile-third"),
            updatedSettings.normalizedConnectionProfiles().map { it.id },
        )
    }

    @Test
    fun deleteDuplicateConnectionProfilesRemovesLaterProfilesWithSameServerDomainAndKey() {
        val first = ConnectionProfile(
            id = "profile-first",
            name = "First",
            customServerDomain = "Server.Example.com.",
            customServerEncryptionKey = "secret-key",
        )
        val duplicate = ConnectionProfile(
            id = "profile-duplicate",
            name = "Duplicate",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "secret-key",
        )
        val other = ConnectionProfile(
            id = "profile-other",
            name = "Other",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "other-key",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = first.id,
            connectionProfiles = listOf(first, duplicate, other),
        )

        val updatedSettings = settings.deleteDuplicateConnectionProfiles()

        assertEquals(1, settings.duplicateConnectionProfileCount())
        assertEquals(listOf(first.id, other.id), updatedSettings.normalizedConnectionProfiles().map { it.id })
        assertEquals(first.id, updatedSettings.selectedConnectionProfileId)
    }

    @Test
    fun deleteDuplicateConnectionProfilesKeepsProtectedProfileWhenItIsDuplicate() {
        val first = ConnectionProfile(
            id = "profile-first",
            name = "First",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "secret-key",
        )
        val protected = ConnectionProfile(
            id = "profile-protected",
            name = "Protected",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "secret-key",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = first.id,
            connectionProfiles = listOf(first, protected),
        )

        val updatedSettings = settings.deleteDuplicateConnectionProfiles(protectedProfileId = protected.id)

        assertEquals(listOf(protected.id), updatedSettings.normalizedConnectionProfiles().map { it.id })
        assertEquals(protected.id, updatedSettings.selectedConnectionProfileId)
    }

    @Test
    fun duplicateConnectionProfileCountIgnoresIncompleteServerProfiles() {
        val first = ConnectionProfile(id = "profile-first", name = "First")
        val second = ConnectionProfile(id = "profile-second", name = "Second")
        val settings = WhiteZiaSettings(connectionProfiles = listOf(first, second))

        val updatedSettings = settings.deleteDuplicateConnectionProfiles()

        assertEquals(0, settings.duplicateConnectionProfileCount())
        assertEquals(listOf(first.id, second.id), updatedSettings.normalizedConnectionProfiles().map { it.id })
    }

    @Test
    fun moveResolverProfileReordersProfilesAndKeepsSelection() {
        val first = ResolverProfile(id = "resolver-first", name = "First", resolverText = "1.1.1.1")
        val second = ResolverProfile(id = "resolver-second", name = "Second", resolverText = "8.8.8.8")
        val third = ResolverProfile(id = "resolver-third", name = "Third", resolverText = "9.9.9.9")
        val settings = WhiteZiaSettings(
            selectedResolverProfileId = second.id,
            resolverProfiles = listOf(first, second, third),
        )

        val updatedSettings = settings.moveResolverProfile("resolver-second", 1)

        assertEquals(
            listOf("resolver-first", "resolver-third", "resolver-second"),
            updatedSettings.normalizedResolverProfiles().map { it.id },
        )
        assertEquals(second.id, updatedSettings.selectedResolverProfileId)
    }

    @Test
    fun moveResolverProfileToIndexReordersToDropTarget() {
        val first = ResolverProfile(id = "resolver-first", name = "First", resolverText = "1.1.1.1")
        val second = ResolverProfile(id = "resolver-second", name = "Second", resolverText = "8.8.8.8")
        val third = ResolverProfile(id = "resolver-third", name = "Third", resolverText = "9.9.9.9")
        val settings = WhiteZiaSettings(
            selectedResolverProfileId = first.id,
            resolverProfiles = listOf(first, second, third),
        )

        val updatedSettings = settings.moveResolverProfileToIndex("resolver-first", 2)

        assertEquals(
            listOf("resolver-second", "resolver-third", "resolver-first"),
            updatedSettings.normalizedResolverProfiles().map { it.id },
        )
        assertEquals(first.id, updatedSettings.selectedResolverProfileId)
    }

    @Test
    fun defaultResolverProfileStaysFirstAndCannotBeDeletedOrMoved() {
        val defaultProfile = ResolverProfile.defaultProfile("1.1.1.1")
        val first = ResolverProfile(id = "resolver-first", name = "First", resolverText = "8.8.8.8")
        val second = ResolverProfile(id = "resolver-second", name = "Second", resolverText = "9.9.9.9")
        val settings = WhiteZiaSettings(
            resolverProfiles = listOf(first, defaultProfile, second),
            selectedResolverProfileId = ResolverProfile.DefaultId,
        )

        val normalizedSettings = settings.syncSelectedConnectionProfileFields()
        val deletedSettings = normalizedSettings.deleteResolverProfile(ResolverProfile.DefaultId)
        val movedSettings = normalizedSettings.moveResolverProfileToIndex(ResolverProfile.DefaultId, 2)
        val customMovedSettings = normalizedSettings.moveResolverProfileToIndex(second.id, 0)

        assertEquals(
            listOf(ResolverProfile.DefaultId, first.id, second.id),
            normalizedSettings.normalizedResolverProfiles().map { it.id },
        )
        assertEquals(
            normalizedSettings.normalizedResolverProfiles(),
            deletedSettings.normalizedResolverProfiles(),
        )
        assertEquals(
            normalizedSettings.normalizedResolverProfiles(),
            movedSettings.normalizedResolverProfiles(),
        )
        assertEquals(
            listOf(ResolverProfile.DefaultId, second.id, first.id),
            customMovedSettings.normalizedResolverProfiles().map { it.id },
        )
        assertEquals(defaultProfile.resolverText, normalizedSettings.resolverText)
    }

    @Test
    fun upsertResolverProfileNormalizesCommaSeparatedTextAndDefaultPorts() {
        val settings = WhiteZiaSettings()

        val updatedSettings = settings.upsertResolverProfile(
            ResolverProfile(
                id = "resolver-main",
                name = "Main",
                resolverText = "1.1.1.1:53, 8.8.8.8:53, 9.9.9.9:5353",
            ),
        )

        assertEquals("1.1.1.1\n8.8.8.8\n9.9.9.9:5353", updatedSettings.resolverText)
        assertEquals(updatedSettings.resolverText, updatedSettings.resolverProfiles.single().resolverText)
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9:5353"),
            updatedSettings.resolve().resolverEntries,
        )
    }

    @Test
    fun saveResolverProfileAsAddsProfileWithoutChangingSelectedResolver() {
        val selectedProfile = ResolverProfile(
            id = "resolver-main",
            name = "Main",
            resolverText = "1.1.1.1",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = "profile-main",
            connectionProfiles = listOf(
                ConnectionProfile(
                    id = "profile-main",
                    name = "Main",
                    resolverProfileId = selectedProfile.id,
                ),
            ),
            selectedResolverProfileId = selectedProfile.id,
            resolverProfiles = listOf(selectedProfile),
            resolverText = selectedProfile.resolverText,
        )

        val updatedSettings = settings.saveResolverProfileAs(
            name = "Scan Results",
            resolverText = "8.8.8.8:53, 9.9.9.9",
        )

        assertEquals(selectedProfile.id, updatedSettings.selectedResolverProfileId)
        assertEquals(selectedProfile.id, updatedSettings.selectedConnectionProfile().resolverProfileId)
        assertEquals(selectedProfile.resolverText, updatedSettings.resolverText)
        assertEquals(2, updatedSettings.normalizedResolverProfiles().size)
        assertEquals(
            "8.8.8.8\n9.9.9.9",
            updatedSettings.normalizedResolverProfiles().last().resolverText,
        )
    }

    @Test
    fun saveSelectedAdvancedProfileDoesNotPersistDefaultProfile() {
        val settings = WhiteZiaSettings(uploadDuplication = "9")

        val updatedSettings = settings.saveSelectedAdvancedProfile()

        assertEquals(AdvancedSettingsProfile.DefaultId, updatedSettings.selectedAdvancedProfileId)
        assertEquals("9", updatedSettings.uploadDuplication)
        assertTrue(updatedSettings.advancedProfiles.isEmpty())
    }

    @Test
    fun saveCurrentAdvancedProfileAsPersistsCustomProfileAndCanReturnToDefault() {
        val savedSettings = WhiteZiaSettings(
            uploadDuplication = "9",
            logLevel = "DEBUG",
        ).saveCurrentAdvancedProfileAs("Fast Tunnel")
        val customProfileId = savedSettings.selectedAdvancedProfileId

        assertEquals(1, savedSettings.advancedProfiles.size)
        assertEquals("Fast Tunnel", savedSettings.selectedAdvancedProfile().name)
        assertEquals("9", savedSettings.selectedAdvancedProfile().uploadDuplication)
        assertEquals("DEBUG", savedSettings.selectedAdvancedProfile().logLevel)

        val defaultSettings = savedSettings.selectAdvancedProfile(AdvancedSettingsProfile.DefaultId)

        assertEquals(AdvancedSettingsProfile.DefaultId, defaultSettings.selectedAdvancedProfileId)
        assertEquals("3", defaultSettings.uploadDuplication)
        assertEquals("WARN", defaultSettings.logLevel)
        assertEquals(customProfileId, defaultSettings.advancedProfiles.single().id)
    }

    @Test
    fun saveSelectedAdvancedProfileUpdatesCustomProfileInPlace() {
        val savedSettings = WhiteZiaSettings(
            uploadDuplication = "4",
        ).saveCurrentAdvancedProfileAs("Fast Tunnel")

        val updatedSettings = savedSettings
            .copy(uploadDuplication = "6")
            .saveSelectedAdvancedProfile()

        assertEquals(savedSettings.selectedAdvancedProfileId, updatedSettings.selectedAdvancedProfileId)
        assertEquals("Fast Tunnel", updatedSettings.selectedAdvancedProfile().name)
        assertEquals("6", updatedSettings.selectedAdvancedProfile().uploadDuplication)
    }

    @Test
    fun upsertAdvancedProfilePersistsSelectsAndAppliesProfile() {
        val profile = AdvancedSettingsProfile.fromSettings(
            settings = WhiteZiaSettings(uploadDuplication = "5", logLevel = "INFO"),
            id = "advanced-fast",
            name = "Fast",
        )

        val updatedSettings = WhiteZiaSettings().upsertAdvancedProfile(profile)

        assertEquals("advanced-fast", updatedSettings.selectedAdvancedProfileId)
        assertEquals("Fast", updatedSettings.selectedAdvancedProfile().name)
        assertEquals("5", updatedSettings.uploadDuplication)
        assertEquals("INFO", updatedSettings.logLevel)
    }

    @Test
    fun moveAdvancedProfileToIndexReordersCustomProfilesAfterDefault() {
        val first = AdvancedSettingsProfile.fromSettings(
            settings = WhiteZiaSettings(uploadDuplication = "4"),
            id = "advanced-first",
            name = "First",
        )
        val second = AdvancedSettingsProfile.fromSettings(
            settings = WhiteZiaSettings(uploadDuplication = "5"),
            id = "advanced-second",
            name = "Second",
        )
        val third = AdvancedSettingsProfile.fromSettings(
            settings = WhiteZiaSettings(uploadDuplication = "6"),
            id = "advanced-third",
            name = "Third",
        )
        val settings = WhiteZiaSettings(
            selectedAdvancedProfileId = first.id,
            advancedProfiles = listOf(first, second, third),
        )

        val updatedSettings = settings.moveAdvancedProfileToIndex("advanced-first", 2)

        assertEquals(
            listOf(AdvancedSettingsProfile.DefaultId, "advanced-second", "advanced-third", "advanced-first"),
            updatedSettings.normalizedAdvancedProfiles().map { it.id },
        )
        assertEquals(first.id, updatedSettings.selectedAdvancedProfileId)
    }

    @Test
    fun deleteSelectedAdvancedProfileReturnsToDefaultSettings() {
        val savedSettings = WhiteZiaSettings(
            uploadDuplication = "5",
            logLevel = "INFO",
        ).saveCurrentAdvancedProfileAs("Fast")
        val selectedProfileId = savedSettings.selectedAdvancedProfileId

        val updatedSettings = savedSettings.deleteAdvancedProfile(selectedProfileId)

        assertEquals(AdvancedSettingsProfile.DefaultId, updatedSettings.selectedAdvancedProfileId)
        assertEquals("3", updatedSettings.uploadDuplication)
        assertEquals("WARN", updatedSettings.logLevel)
        assertTrue(updatedSettings.advancedProfiles.none { it.id == selectedProfileId })
    }

    @Test
    fun importAdvancedSettingsProfileFromTomlIgnoresServerFieldsAndSavesProfile() {
        val connectionProfile = ConnectionProfile(
            id = "profile-main",
            name = "Main",
            customServerDomain = "existing.example.com",
            customServerEncryptionKey = "existing-key",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = connectionProfile.id,
            connectionProfiles = listOf(connectionProfile),
        )

        val importedSettings = settings.importAdvancedSettingsProfileFromToml(
            name = "Imported",
            toml = """
                DOMAINS = ["ignored.example.com"]
                DATA_ENCRYPTION_METHOD = 5
                ENCRYPTION_KEY = "ignored-key"
                LISTEN_PORT = 12345
                SOCKS5_AUTH = true
                SOCKS5_USER = "alice"
                UPLOAD_PACKET_DUPLICATION_COUNT = 4
                LOG_LEVEL = "INFO"
            """.trimIndent(),
        )

        assertEquals("Imported", importedSettings.selectedAdvancedProfile().name)
        assertEquals("12345", importedSettings.listenPort)
        assertEquals(true, importedSettings.socks5Authentication)
        assertEquals("alice", importedSettings.socksUsername)
        assertEquals("4", importedSettings.uploadDuplication)
        assertEquals("INFO", importedSettings.logLevel)
        assertEquals("existing.example.com", importedSettings.selectedConnectionProfile().customServerDomain)
        assertEquals("existing-key", importedSettings.selectedConnectionProfile().customServerEncryptionKey)
    }

    @Test
    fun importAdvancedSettingsProfileFromTomlRejectsInvalidValues() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WhiteZiaSettings().importAdvancedSettingsProfileFromToml(
                name = "Bad",
                toml = "LISTEN_PORT = 70000",
            )
        }

        assertTrue(error.message.orEmpty().contains("LISTEN_PORT"))
    }

    @Test
    fun importAdvancedSettingsProfileFromTomlRejectsFilesWithoutSettings() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WhiteZiaSettings().importAdvancedSettingsProfileFromToml(
                name = "Server only",
                toml = """
                    DOMAINS = ["server.example.com"]
                    ENCRYPTION_KEY = "secret-key"
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("No supported advanced settings"))
    }

    @Test
    fun resolveBoundsTrafficWarmupSettings() {
        val settings = WhiteZiaSettings(
            trafficWarmupProbeCount = "99",
            trafficKeepaliveIntervalSeconds = "1",
        )

        val resolvedSettings = settings.resolve()

        assertEquals(false, resolvedSettings.trafficWarmupEnabled)
        assertEquals(10, resolvedSettings.trafficWarmupProbeCount)
        assertEquals(2, resolvedSettings.trafficKeepaliveIntervalSeconds)
    }

    @Test
    fun resolveUsesAppMtuDefaults() {
        val resolvedSettings = WhiteZiaSettings().resolve()

        assertEquals(40, resolvedSettings.minUploadMtu)
        assertEquals(300, resolvedSettings.minDownloadMtu)
        assertEquals(140, resolvedSettings.maxUploadMtu)
        assertEquals(3000, resolvedSettings.maxDownloadMtu)
    }

    @Test
    fun autoTunePresetsExposeTenIranBucketsForParallelTesting() {
        assertEquals(10, WhiteZiaAutoTunePresets.all.size)
        assertEquals(7, WhiteZiaParallelTest.stableConfigIds.size)
        assertEquals(3, WhiteZiaParallelTest.aggressiveConfigIds.size)
    }

    @Test
    fun applyAutoTunePresetAppliesIranDefaultValues() {
        val settings = WhiteZiaSettings(autoTuneEnabled = true)
            .applyAutoTunePreset(WhiteZiaAutoTunePresets.all.first())
        val resolvedSettings = settings.resolve()

        assertEquals(true, settings.autoTuneEnabled)
        assertEquals(40, resolvedSettings.minUploadMtu)
        assertEquals(140, resolvedSettings.maxUploadMtu)
        assertEquals(300, resolvedSettings.minDownloadMtu)
        assertEquals(3000, resolvedSettings.maxDownloadMtu)
        assertEquals(2.5, resolvedSettings.mtuTestTimeoutResolvers, 0.0)
        assertEquals(256, resolvedSettings.dnsResponseFragmentStoreCapacity)
        assertEquals(3, resolvedSettings.uploadDuplication)
        assertEquals(7, resolvedSettings.downloadDuplication)
        assertEquals(2, resolvedSettings.uploadCompression)
        assertEquals(2, resolvedSettings.downloadCompression)
    }

    @Test
    fun parallelTestIsDisabledByDefault() {
        val settings = WhiteZiaSettings()

        assertEquals(false, settings.autoTuneEnabled)
        assertEquals(false, settings.resolve().autoTuneEnabled)
    }

    @Test
    fun defaultTunnelPacketTimeoutMatchesStormDnsDefault() {
        val settings = WhiteZiaSettings()

        assertEquals("10.0", settings.tunnelPacketTimeoutSeconds)
        assertEquals(10.0, settings.resolve().tunnelPacketTimeoutSeconds, 0.0)
    }

    @Test
    fun normalizeParallelTestConfigIdsDefaultsAndCapsSelection() {
        val profiles = (1..5).map { index ->
            AdvancedSettingsProfile.fromSettings(
                settings = WhiteZiaSettings(),
                id = "profile-$index",
                name = "Profile $index",
            )
        }
        val requestedIds = WhiteZiaParallelTest.allConfigIds +
            profiles.map { WhiteZiaParallelTest.settingConfigId(it.id) }

        val defaultIds = WhiteZiaParallelTest.normalizeConfigIds(
            configIds = emptyList(),
            advancedProfiles = profiles,
        )
        val cappedIds = WhiteZiaParallelTest.normalizeConfigIds(
            configIds = requestedIds,
            advancedProfiles = profiles,
            includeAggressive = true,
        )
        val stableOnlyIds = WhiteZiaParallelTest.normalizeConfigIds(
            configIds = WhiteZiaParallelTest.allConfigIds,
            advancedProfiles = profiles,
        )

        assertEquals(WhiteZiaParallelTest.defaultConfigIds, defaultIds)
        assertEquals(WhiteZiaParallelTest.stableConfigIds, stableOnlyIds)
        assertEquals(WhiteZiaParallelTest.MaxSelectedConfigs, cappedIds.size)
        assertEquals(WhiteZiaParallelTest.allConfigIds, cappedIds)
    }

    @Test
    fun resolveUsesFullScanStartupAndBoundsNativeReliabilitySettings() {
        val settings = WhiteZiaSettings(
            startupMode = "invalid",
            maxActiveStreams = "999999",
            localHandshakeTimeoutSeconds = "0",
            minUploadMtu = "300",
            maxUploadMtu = "100",
            minDownloadMtu = "5000",
            maxDownloadMtu = "1000",
        )

        val resolvedSettings = settings.resolve()

        assertEquals("resolvers", resolvedSettings.startupMode)
        assertEquals(65535, resolvedSettings.maxActiveStreams)
        assertEquals(5.0, resolvedSettings.localHandshakeTimeoutSeconds, 0.0)
        assertEquals(300, resolvedSettings.maxUploadMtu)
        assertEquals(5000, resolvedSettings.maxDownloadMtu)
    }

    @Test
    fun importStormDnsProfileLinkAcceptsRequiredPayloadOnly() {
        val payload = """
            {
              "schema": "whitezia.profile",
              "version": 1,
              "profile": {
                "name": "Imported Profile",
                "server": {
                  "domain": "server.example.com",
                  "encryption_key": "secret-key",
                  "encryption_method": 2
                }
              }
            }
        """.trimIndent()
        val link = "stormdns://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"

        val importedSettings = WhiteZiaSettings().importStormDnsProfileLink(link, nowMillis = 42L)
        val importedProfile = importedSettings.selectedConnectionProfile()

        assertEquals("profile-imported-42", importedProfile.id)
        assertEquals("Imported Profile", importedProfile.name)
        assertEquals("custom", importedProfile.serverMode)
        assertEquals("server.example.com", importedProfile.customServerDomain)
        assertEquals("secret-key", importedProfile.customServerEncryptionKey)
        assertEquals(2, importedProfile.customServerEncryptionMethod)
        assertEquals("proxy", importedSettings.connectionMode)
    }

    @Test
    fun exportAndImportStormDnsProfileLinkUsesOnlyRequiredProfileFields() {
        val resolverProfile = ResolverProfile(
            id = "resolver-main",
            name = "Main Resolvers",
            resolverText = "1.1.1.1\n8.8.8.8",
        )
        val connectionProfile = ConnectionProfile(
            id = "profile-main",
            name = "Main Profile",
            serverMode = "custom",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "secret-key",
            customServerEncryptionMethod = 5,
            resolverProfileId = resolverProfile.id,
            connectionMode = "vpn",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = connectionProfile.id,
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), connectionProfile),
            selectedResolverProfileId = resolverProfile.id,
            resolverProfiles = listOf(resolverProfile),
            resolverText = resolverProfile.resolverText,
            listenPort = "12345",
            httpProxyEnabled = false,
            balancingStrategy = 4,
            uploadDuplication = "2",
            downloadDuplication = "6",
            rxTxWorkers = "8",
            startupMode = "logs",
            trafficWarmupEnabled = false,
            trafficKeepaliveIntervalSeconds = "15",
            splitTunnelMode = WhiteZiaOptions.SplitTunnelModeExclude,
            splitTunnelPackages = listOf("org.telegram.messenger"),
            logLevel = "INFO",
        )

        val link = settings.exportStormDnsProfileLink(connectionProfile)
        val exportedProfileJson = JSONObject(decodeStormDnsProfilePayload(link)).getJSONObject("profile")
        val exportedServerJson = exportedProfileJson.getJSONObject("server")
        val importedSettings = WhiteZiaSettings().importStormDnsProfileLink(link, nowMillis = 100L)
        val importedProfile = importedSettings.selectedConnectionProfile()

        assertTrue(link.startsWith("stormdns://"))
        assertEquals(setOf("name", "server"), exportedProfileJson.keys().asSequence().toSet())
        assertEquals(
            setOf("domain", "encryption_key", "encryption_method"),
            exportedServerJson.keys().asSequence().toSet(),
        )
        assertEquals("Main Profile", importedProfile.name)
        assertEquals("server.example.com", importedProfile.customServerDomain)
        assertEquals("secret-key", importedProfile.customServerEncryptionKey)
        assertEquals(5, importedProfile.customServerEncryptionMethod)
        assertEquals("", importedProfile.resolverProfileId)
        assertEquals("proxy", importedSettings.connectionMode)
        assertEquals(emptyList<String>(), importedSettings.resolve().resolverEntries)
        assertEquals("10886", importedSettings.listenPort)
        assertEquals(true, importedSettings.httpProxyEnabled)
        assertEquals(3, importedSettings.balancingStrategy)
        assertEquals("3", importedSettings.uploadDuplication)
        assertEquals("7", importedSettings.downloadDuplication)
        assertEquals("4", importedSettings.rxTxWorkers)
        assertEquals("resolvers", importedSettings.startupMode)
        assertEquals(false, importedSettings.trafficWarmupEnabled)
        assertEquals("5", importedSettings.trafficKeepaliveIntervalSeconds)
        assertEquals(WhiteZiaOptions.SplitTunnelModeOff, importedSettings.splitTunnelMode)
        assertEquals(emptyList<String>(), importedSettings.splitTunnelPackages)
        assertEquals("WARN", importedSettings.logLevel)
    }

    @Test
    fun exportStormDnsProfileLinkAlwaysWritesRequiredPayloadOnly() {
        val connectionProfile = ConnectionProfile(
            id = "profile-main",
            name = "Main Profile",
            serverMode = "custom",
            customServerDomain = "server.example.com",
            customServerEncryptionKey = "secret-key",
            customServerEncryptionMethod = 5,
            connectionMode = "vpn",
        )
        val settings = WhiteZiaSettings(
            selectedConnectionProfileId = connectionProfile.id,
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), connectionProfile),
            listenPort = "12345",
            httpProxyEnabled = false,
            trafficWarmupEnabled = false,
            logLevel = "INFO",
        )

        val link = settings.exportStormDnsProfileLink(profile = connectionProfile)
        val profileJson = JSONObject(decodeStormDnsProfilePayload(link)).getJSONObject("profile")
        val importedSettings = WhiteZiaSettings().importStormDnsProfileLink(link, nowMillis = 300L)
        val importedProfile = importedSettings.selectedConnectionProfile()

        assertEquals(setOf("name", "server"), profileJson.keys().asSequence().toSet())
        assertEquals("Main Profile", importedProfile.name)
        assertEquals("server.example.com", importedProfile.customServerDomain)
        assertEquals("secret-key", importedProfile.customServerEncryptionKey)
        assertEquals(5, importedProfile.customServerEncryptionMethod)
        assertEquals("proxy", importedSettings.connectionMode)
        assertEquals("10886", importedSettings.listenPort)
        assertEquals(true, importedSettings.httpProxyEnabled)
        assertEquals(false, importedSettings.trafficWarmupEnabled)
        assertEquals("WARN", importedSettings.logLevel)
    }

    @Test
    fun importStormBundleProfileLinkAcceptsLegacyWhiteDnsBundleSchema() {
        val payload = """
            {
              "schema": "whitedns.bundle",
              "version": 1,
              "profile": {
                "name": "Legacy Bundle",
                "stormdns": {
                  "domain": "legacy.example.com",
                  "encryption_key": "legacy-key",
                  "encryption_method": 1
                },
                "amneziawg": {
                  "config": "[Interface]\nPrivateKey = test"
                }
              }
            }
        """.trimIndent()
        val link = "stormbundle://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"

        val importedSettings = WhiteZiaSettings().importStormDnsProfileLink(link, nowMillis = 101L)
        val importedProfile = importedSettings.selectedConnectionProfile()

        assertEquals("profile-imported-101", importedProfile.id)
        assertEquals("Legacy Bundle", importedProfile.name)
        assertEquals("legacy.example.com", importedProfile.customServerDomain)
        assertEquals("legacy-key", importedProfile.customServerEncryptionKey)
        assertEquals(1, importedProfile.customServerEncryptionMethod)
        assertEquals(WhiteZiaOptions.TransportAuto, importedSettings.transportMode)
        assertTrue(importedSettings.amneziaWgConfig.contains("PrivateKey"))
    }

    @Test
    fun importStormDnsProfileLinkIgnoresResolverPayload() {
        val existingResolverProfile = ResolverProfile(
            id = "resolver-existing",
            name = "Existing",
            resolverText = "9.9.9.9",
        )
        val existingSettings = WhiteZiaSettings(
            selectedResolverProfileId = existingResolverProfile.id,
            resolverProfiles = listOf(existingResolverProfile),
            resolverText = existingResolverProfile.resolverText,
        )
        val payload = """
            {
              "schema": "whitezia.profile",
              "version": 1,
              "profile": {
                "name": "Imported",
                "server": {
                  "domain": "server.example.com",
                  "encryption_key": "secret-key",
                  "encryption_method": 2
                },
                "connection": {
                  "mode": "vpn"
                },
                "local_proxy": {
                  "listen_port": "12345"
                },
                "resolvers": {
                  "name": "Imported Resolvers",
                  "entries": ["1.1.1.1", "8.8.8.8"]
                }
              }
            }
        """.trimIndent()
        val link = "stormdns://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"

        val importedSettings = existingSettings.importStormDnsProfileLink(link, nowMillis = 200L)
        val importedProfile = importedSettings.selectedConnectionProfile()

        assertEquals("Imported", importedProfile.name)
        assertEquals("", importedProfile.resolverProfileId)
        assertEquals(listOf(existingResolverProfile), importedSettings.resolverProfiles)
        assertEquals(existingResolverProfile.id, importedSettings.selectedResolverProfileId)
        assertEquals("proxy", importedSettings.connectionMode)
        assertEquals("10886", importedSettings.listenPort)
        assertEquals(listOf("9.9.9.9"), importedSettings.resolve().resolverEntries)
    }

    @Test
    fun exportAllStormDnsProfileLinksWritesOneLinkPerCustomProfile() {
        val first = ConnectionProfile(
            id = "profile-first",
            name = "First",
            serverMode = "custom",
            customServerDomain = "first.example.com",
            customServerEncryptionKey = "first-key",
            customServerEncryptionMethod = 1,
        )
        val second = ConnectionProfile(
            id = "profile-second",
            name = "Second",
            serverMode = "custom",
            customServerDomain = "second.example.com",
            customServerEncryptionKey = "second-key",
            customServerEncryptionMethod = 2,
        )
        val settings = WhiteZiaSettings(
            connectionProfiles = listOf(ConnectionProfile.defaultProfile(), first, second),
        )

        val exportedLinks = settings.exportAllStormDnsProfileLinks().lineSequence().toList()

        assertEquals(2, exportedLinks.size)
        assertTrue(exportedLinks.all { it.startsWith("stormdns://") })
        assertEquals("first.example.com", WhiteZiaSettings().importStormDnsProfileLink(exportedLinks[0]).customServerDomain)
        assertEquals("second.example.com", WhiteZiaSettings().importStormDnsProfileLink(exportedLinks[1]).customServerDomain)
    }

    @Test
    fun importStormDnsProfileLinksImportsManyLinksLineByLine() {
        fun linkFor(domain: String, key: String): String {
            val payload = """
                {
                  "schema": "whitezia.profile",
                  "version": 1,
                  "profile": {
                    "name": "$domain",
                    "server": {
                      "domain": "$domain",
                      "encryption_key": "$key",
                      "encryption_method": 1
                    }
                  }
                }
            """.trimIndent()
            return "stormdns://${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}"
        }
        val firstLink = linkFor("first.example.com", "first-key")
        val secondLink = linkFor("second.example.com", "second-key")

        val importedSettings = WhiteZiaSettings().importStormDnsProfileLinks(
            rawLinks = "$firstLink\n\n$secondLink",
            nowMillis = 500L,
        )
        val importedProfiles = importedSettings.normalizedConnectionProfiles()
            .filter { it.customServerDomain.isNotBlank() }

        assertEquals(listOf("first.example.com", "second.example.com"), importedProfiles.map { it.customServerDomain })
        assertEquals(listOf("profile-imported-500", "profile-imported-501"), importedProfiles.map { it.id })
        assertEquals("second.example.com", importedSettings.selectedConnectionProfile().customServerDomain)
    }

    @Test
    fun exportAllResolverProfilesTextWritesOneDeduplicatedResolverFile() {
        val first = ResolverProfile(
            id = "resolver-first",
            name = "First",
            resolverText = "1.1.1.1\n8.8.8.8:53",
        )
        val second = ResolverProfile(
            id = "resolver-second",
            name = "Second",
            resolverText = "8.8.8.8\n9.9.9.9:5353",
        )
        val settings = WhiteZiaSettings(resolverProfiles = listOf(first, second))

        assertEquals(
            "1.1.1.1\n8.8.8.8\n9.9.9.9:5353",
            settings.exportAllResolverProfilesText(),
        )
    }

    @Test
    fun exportAllResolverProfilesTextRejectsEmptyResolverProfiles() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WhiteZiaSettings().exportAllResolverProfilesText()
        }

        assertEquals("No resolver profiles are available to export", error.message)
    }

    @Test
    fun validateResolverTextAcceptsSupportedResolverIpFormats() {
        val validation = validateResolverText(
            """
            # comment
            1.1.1.1, 8.8.8.8:5353
            [2001:4860:4860::8888]:53
            192.168.10.0/30:5300
            """.trimIndent(),
        )

        assertEquals(emptyList<String>(), validation.invalidEntries)
        assertEquals(
            listOf(
                "1.1.1.1",
                "8.8.8.8:5353",
                "2001:4860:4860:0:0:0:0:8888",
                "192.168.10.0/30:5300",
            ),
            validation.normalizedResolvers,
        )
    }

    @Test
    fun resolveNormalizesCommaSeparatedResolverTextAndDefaultPorts() {
        val settings = WhiteZiaSettings(
            resolverText = "1.1.1.1:53, 8.8.8.8:53\n9.9.9.9",
        )

        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            settings.resolve().resolverEntries,
        )
    }

    @Test
    fun validateResolverTextAcceptsQuotedCommaSeparatedExportRows() {
        val validation = validateResolverText(
            """
            "resolver","port"
            "1.1.1.1:53","valid"
            "8.8.8.8",53
            "999.1.1.1","bad"
            """.trimIndent(),
        )

        assertEquals(listOf("1.1.1.1", "8.8.8.8"), validation.normalizedResolvers)
        assertEquals(listOf("resolver", "port", "valid", "53", "999.1.1.1", "bad"), validation.invalidEntries)
    }

    @Test
    fun validateResolverTextRejectsInvalidResolverEntries() {
        val validation = validateResolverText(
            """
            1.1.1.1
            google.com
            999.1.1.1
            8.8.8.8:70000
            10.0.0.0/8
            """.trimIndent(),
        )

        assertEquals(listOf("1.1.1.1"), validation.normalizedResolvers)
        assertEquals(
            listOf("google.com", "999.1.1.1", "8.8.8.8:70000", "10.0.0.0/8"),
            validation.invalidEntries,
        )
    }

    @Test
    fun recoverIfStaleStopsOldRunningScanState() {
        val recovered = WhiteZiaScanState(
            sessionId = "scan-1",
            status = WhiteZiaScanStatus.Running,
            updatedAtMillis = 10_000L,
            message = "Scanning",
        ).recoverIfStale(
            nowMillis = 30_000L,
            staleAfterMillis = 15_000L,
        )

        assertEquals(WhiteZiaScanStatus.Stopped, recovered.status)
        assertEquals("Previous scan is no longer active", recovered.message)
        assertEquals(30_000L, recovered.updatedAtMillis)
    }

    @Test
    fun recoverIfStaleKeepsFreshRunningScanState() {
        val freshState = WhiteZiaScanState(
            sessionId = "scan-2",
            status = WhiteZiaScanStatus.Running,
            updatedAtMillis = 20_000L,
            message = "Scanning",
        )
        val recovered = freshState.recoverIfStale(
            nowMillis = 30_000L,
            staleAfterMillis = 15_000L,
        )

        assertEquals(freshState, recovered)
    }

    private fun decodeStormDnsProfilePayload(link: String): String {
        val payload = link.removePrefix("stormdns://")
        val paddedPayload = payload.padEnd(payload.length + ((4 - payload.length % 4) % 4), '=')
        return Base64.getUrlDecoder().decode(paddedPayload).toString(Charsets.UTF_8)
    }
}
