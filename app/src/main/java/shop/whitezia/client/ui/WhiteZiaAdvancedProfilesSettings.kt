package shop.whitezia.client.ui


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.applyAdvancedProfile
import shop.whitezia.client.model.deleteAdvancedProfile
import shop.whitezia.client.model.importAdvancedSettingsProfileFromToml
import shop.whitezia.client.model.matchesAdvancedProfile
import shop.whitezia.client.model.moveAdvancedProfileToIndex
import shop.whitezia.client.model.normalizedAdvancedProfiles
import shop.whitezia.client.model.selectAdvancedProfile
import shop.whitezia.client.model.selectedAdvancedProfile
import shop.whitezia.client.model.upsertAdvancedProfile
import shop.whitezia.client.storm.StormDnsConfigRenderer

@Composable
internal fun SettingProfilesSettings(
    settings: WhiteZiaSettings,
    connectionStatus: ConnectionStatus,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedAdvancedProfiles()
    val defaultProfile = profiles.first()
    val customProfiles = profiles.filter { it.id != AdvancedSettingsProfile.DefaultId }
    val selectedProfile = settings.selectedAdvancedProfile()
    val context = LocalContext.current
    val shareChooserAdvancedSettingsLabel = WhiteZiaL10n.shareChooserAdvancedSettings
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var exportProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<AdvancedSettingsProfile?>(null) }
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeightPx by remember { mutableIntStateOf(0) }
    val canManageProfiles = connectionStatus == ConnectionStatus.DISCONNECTED
    val draggedIndex = draggedProfileId?.let { profileId ->
        customProfiles.indexOfFirst { it.id == profileId }.takeIf { it >= 0 }
    }
    val dragTargetIndex = draggedIndex?.let {
        val indexOffset = dragOffsetToProfileIndexOffset(
            offsetY = dragOffsetY,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
    }

    fun clearDragState() {
        draggedProfileId = null
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    fun finishDrag(commit: Boolean) {
        val profileId = draggedProfileId
        val targetIndex = if (profileId != null && customProfiles.isNotEmpty()) {
            val indexOffset = dragOffsetToProfileIndexOffset(
                offsetY = dragOffsetY,
                itemHeightPx = measuredItemHeightPx.toFloat(),
            )
            (dragStartIndex + indexOffset).coerceIn(0, customProfiles.lastIndex)
        } else {
            null
        }
        clearDragState()
        if (commit && profileId != null && targetIndex != null && canManageProfiles) {
            onSettingsChange(settings.moveAdvancedProfileToIndex(profileId, targetIndex))
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canManageProfiles,
                onClick = { showCreateDialog = true },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnImport,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = { showImportDialog = true },
            ),
        )
    )

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupDefault)
    SettingProfileRow(
        profile = defaultProfile,
        selected = defaultProfile.id == selectedProfile.id,
        dirty = defaultProfile.id == selectedProfile.id && !settings.matchesAdvancedProfile(defaultProfile),
        canUse = canManageProfiles,
        canEdit = false,
        canDelete = false,
        canDrag = false,
        dragging = false,
        onUse = {
            if (canManageProfiles) {
                onSettingsChange(settings.selectAdvancedProfile(defaultProfile.id))
            }
        },
        onDragStart = {},
        onDrag = {},
        onDragEnd = {},
        onDragCancel = {},
        onExport = { exportProfile = defaultProfile },
        onEdit = {},
        onDelete = {},
    )
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))

    GroupLabel(WhiteZiaL10n.groupCustomSettings)
    if (customProfiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.profileNoSettingProfiles,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    customProfiles.forEachIndexed { index, profile ->
        val isDragging = profile.id == draggedProfileId
        val targetTranslationY = profileDragTranslationY(
            itemIndex = index,
            draggedIndex = draggedIndex,
            targetIndex = dragTargetIndex,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        val animatedTranslationY by animateFloatAsState(
            targetValue = if (isDragging) 0f else targetTranslationY,
            animationSpec = spring(),
            label = "advancedProfileDragTranslation",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetY else animatedTranslationY
                    shadowElevation = if (isDragging) 8f else 0f
                    alpha = if (isDragging) 0.96f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    measuredItemHeightPx = coordinates.size.height.takeIf { it > 0 } ?: measuredItemHeightPx
                },
        ) {
            SettingProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile.id,
                dirty = profile.id == selectedProfile.id && !settings.matchesAdvancedProfile(profile),
                canUse = canManageProfiles,
                canEdit = canManageProfiles,
                canDelete = canManageProfiles,
                canDrag = canManageProfiles && customProfiles.size > 1,
                dragging = isDragging,
                onUse = {
                    if (canManageProfiles) {
                        onSettingsChange(settings.selectAdvancedProfile(profile.id))
                    }
                },
                onDragStart = {
                    if (canManageProfiles && customProfiles.size > 1) {
                        draggedProfileId = profile.id
                        dragStartIndex = index
                        dragOffsetY = 0f
                    }
                },
                onDrag = { deltaY ->
                    if (draggedProfileId == profile.id) {
                        dragOffsetY += deltaY
                    }
                },
                onDragEnd = {
                    finishDrag(commit = true)
                },
                onDragCancel = {
                    finishDrag(commit = false)
                },
                onExport = { exportProfile = profile },
                onEdit = { editProfile = profile },
                onDelete = {
                    if (canManageProfiles) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        AdvancedSettingsProfileDialog(
            profile = null,
            initialName = advancedSaveAsInitialName(selectedProfile, WhiteZiaL10n.homeSelectorCustomAdvanced, WhiteZiaL10n.profileNameCopySuffix),
            initialSettings = settings,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                onSettingsChange(settings.upsertAdvancedProfile(profile))
                showCreateDialog = false
            },
        )
    }

    if (showImportDialog) {
        AdvancedSettingsImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { profileName, toml ->
                runCatching {
                    settings.importAdvancedSettingsProfileFromToml(profileName, toml)
                }.onSuccess { importedSettings ->
                    onSettingsChange(importedSettings)
                    showImportDialog = false
                }
            },
        )
    }

    editProfile?.let { profile ->
        AdvancedSettingsProfileDialog(
            profile = profile,
            initialName = profile.name,
            initialSettings = settings.applyAdvancedProfile(profile),
            onDismiss = { editProfile = null },
            onSave = { updatedProfile ->
                onSettingsChange(settings.upsertAdvancedProfile(updatedProfile.copy(id = profile.id)))
                editProfile = null
            },
        )
    }

    exportProfile?.let { profile ->
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportSettings,
            fieldLabel = "advanced_settings.toml",
            placeholder = "advanced_settings.toml",
            showQr = false,
            linkResult = remember(settings, profile) {
                runCatching {
                    StormDnsConfigRenderer.renderAdvancedSettingsToml(settings.applyAdvancedProfile(profile))
                }
            },
            onDismiss = { exportProfile = null },
            onShare = { toml ->
                shareAdvancedSettingsToml(context, toml, shareChooserAdvancedSettingsLabel)
            },
        )
    }

    deleteProfile?.let { profile ->
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteSettingTitle,
            message = WhiteZiaL10n.deleteSettingMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canManageProfiles) {
                    onSettingsChange(settings.deleteAdvancedProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}
