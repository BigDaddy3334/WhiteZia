package shop.whitezia.client.ui


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.WhiteZiaUiState

@Composable
internal fun ProfilesTabContent(
    uiState: WhiteZiaUiState,
    createRequest: ProfileCreateRequest?,
    onCreateRequestConsumed: () -> Unit,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    var selectedProfileTab by rememberSaveable { mutableStateOf(ProfileTab.CONNECTION) }
    var connectionCreateRequestId by rememberSaveable { mutableIntStateOf(0) }
    var resolverCreateRequestId by rememberSaveable { mutableIntStateOf(0) }
    var showSettingGuide by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createRequest) {
        when (createRequest) {
            ProfileCreateRequest.CONNECTION -> {
                selectedProfileTab = ProfileTab.CONNECTION
                connectionCreateRequestId += 1
                onCreateRequestConsumed()
            }
            ProfileCreateRequest.RESOLVER -> {
                selectedProfileTab = ProfileTab.RESOLVER
                resolverCreateRequestId += 1
                onCreateRequestConsumed()
            }
            null -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderCard(
            themeMode = uiState.settings.themeMode,
            onThemeModeChange = { onSettingsChange(uiState.settings.copy(themeMode = it)) },
            languageCode = uiState.settings.languageCode,
            onLanguageCodeChange = { onSettingsChange(uiState.settings.copy(languageCode = it)) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 20.dp),
        ) {
            ProfileTabSwitch(
                selectedTab = selectedProfileTab,
                onTabSelected = { selectedProfileTab = it },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            InfoCard(
                title = when (selectedProfileTab) {
                    ProfileTab.CONNECTION -> WhiteZiaL10n.selectorConnectionProfiles.uppercase()
                    ProfileTab.RESOLVER -> WhiteZiaL10n.selectorResolverProfiles.uppercase()
                    ProfileTab.SETTING -> WhiteZiaL10n.selectorSettingProfiles.uppercase()
                },
                titleAction = if (selectedProfileTab == ProfileTab.SETTING) {
                    {
                        SettingProfileGuideButton(
                            onClick = { showSettingGuide = true },
                        )
                    }
                } else {
                    null
                },
            ) {
                when (selectedProfileTab) {
                    ProfileTab.CONNECTION -> ConnectionProfilesSettings(
                        settings = uiState.settings,
                        activeConnectionProfileId = uiState.activeConnectionProfileId,
                        connectionStatus = uiState.connectionStatus,
                        serverTestState = uiState.serverTestState,
                        openCreateRequestId = connectionCreateRequestId,
                        onServerTestClick = onServerTestClick,
                        onSettingsChange = onSettingsChange,
                    )
                    ProfileTab.RESOLVER -> ResolverProfilesSettings(
                        settings = uiState.settings,
                        connectionStatus = uiState.connectionStatus,
                        openCreateRequestId = resolverCreateRequestId,
                        onSettingsChange = onSettingsChange,
                    )
                    ProfileTab.SETTING -> SettingProfilesSettings(
                        settings = uiState.settings,
                        connectionStatus = uiState.connectionStatus,
                        onSettingsChange = onSettingsChange,
                    )
                }
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sectionSpacing))
            FooterLink()
        }
    }

    if (showSettingGuide) {
        SettingProfileGuideDialog(
            onDismiss = { showSettingGuide = false },
        )
    }
}

internal enum class ProfileTab(val label: String) {
    CONNECTION("Connection"),
    RESOLVER("Resolver"),
    SETTING("Setting"),
}

@Composable
internal fun tabLabel(tab: WhiteZiaTab): String = when (tab) {
    WhiteZiaTab.PROFILES -> WhiteZiaL10n.tabProfiles
    WhiteZiaTab.CONNECT -> WhiteZiaL10n.tabConnect
    WhiteZiaTab.SCAN -> WhiteZiaL10n.tabScan
    WhiteZiaTab.LOGS -> WhiteZiaL10n.tabLogs
}

@Composable
internal fun profileTabLabel(tab: ProfileTab): String = when (tab) {
    ProfileTab.CONNECTION -> WhiteZiaL10n.profileTabConnection
    ProfileTab.RESOLVER -> WhiteZiaL10n.profileTabResolver
    ProfileTab.SETTING -> WhiteZiaL10n.profileTabSetting
}

@Composable
internal fun AdvancedProfileControls(
    selectedProfile: AdvancedSettingsProfile,
    dirty: Boolean,
    enabled: Boolean,
    onSelectClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    HomeSelectorCard(
        label = WhiteZiaL10n.homeSelectorSettingLabel,
        value = if (selectedProfile.id == AdvancedSettingsProfile.DefaultId) WhiteZiaL10n.setupDefaultAdvanced else selectedProfile.name,
        detail = if (dirty) WhiteZiaL10n.homeSelectorUnsavedChanges else advancedProfileSummary(selectedProfile),
        selected = !dirty,
        enabled = enabled,
        onClick = onSelectClick,
        onEditClick = onEditClick,
    )
}

internal fun advancedProfileSummary(profile: AdvancedSettingsProfile): String {
    return "MTU ${profile.minUploadMtu}-${profile.maxUploadMtu}/${profile.minDownloadMtu}-${profile.maxDownloadMtu}, ${profile.logLevel}"
}

internal fun advancedSaveAsInitialName(profile: AdvancedSettingsProfile, customAdvancedLabel: String, copySuffix: String): String {
    return if (profile.id == AdvancedSettingsProfile.DefaultId) {
        customAdvancedLabel
    } else {
        "${profile.name} $copySuffix"
    }
}

internal fun AdvancedSettingsProfile.displayName(dirty: Boolean, modifiedSuffix: String): String {
    return if (dirty) {
        "$name $modifiedSuffix"
    } else {
        name
    }
}
