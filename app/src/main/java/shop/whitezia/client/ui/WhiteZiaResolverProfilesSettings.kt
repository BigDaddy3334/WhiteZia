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
import androidx.compose.runtime.LaunchedEffect
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
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.applyResolverProfileToSelectedConnection
import shop.whitezia.client.model.deleteResolverProfile
import shop.whitezia.client.model.moveResolverProfileToIndex
import shop.whitezia.client.model.normalizedResolverProfiles
import shop.whitezia.client.model.selectedResolverProfile
import shop.whitezia.client.model.upsertResolverProfile

@Composable
internal fun ResolverProfilesSettings(
    settings: WhiteZiaSettings,
    connectionStatus: ConnectionStatus,
    openCreateRequestId: Int,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedResolverProfiles()
    val selectedProfile = settings.selectedResolverProfile()
    val context = LocalContext.current
    var dialogProfile by remember { mutableStateOf<ResolverProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ResolverProfile?>(null) }
    val canChangeProfiles = connectionStatus != ConnectionStatus.CONNECTING
    val shareChooserResolversLabel = WhiteZiaL10n.shareChooserResolvers
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeightPx by remember { mutableIntStateOf(0) }
    val draggedIndex = draggedProfileId?.let { profileId ->
        profiles.indexOfFirst { it.id == profileId }.takeIf { it >= 0 }
    }
    val dragTargetIndex = draggedIndex?.let {
        val indexOffset = dragOffsetToProfileIndexOffset(
            offsetY = dragOffsetY,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        (dragStartIndex + indexOffset).coerceIn(0, profiles.lastIndex)
    }

    fun clearDragState() {
        draggedProfileId = null
        dragStartIndex = 0
        dragOffsetY = 0f
    }

    fun finishDrag(commit: Boolean) {
        val profileId = draggedProfileId
        val targetIndex = if (profileId != null && profiles.isNotEmpty()) {
            val indexOffset = dragOffsetToProfileIndexOffset(
                offsetY = dragOffsetY,
                itemHeightPx = measuredItemHeightPx.toFloat(),
            )
            (dragStartIndex + indexOffset).coerceIn(0, profiles.lastIndex)
        } else {
            null
        }
        clearDragState()
        if (commit && profileId != null && targetIndex != null && canChangeProfiles) {
            onSettingsChange(settings.moveResolverProfileToIndex(profileId, targetIndex))
        }
    }

    LaunchedEffect(openCreateRequestId) {
        if (openCreateRequestId > 0 && canChangeProfiles) {
            showCreateDialog = true
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canChangeProfiles,
                onClick = { showCreateDialog = true },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnSaveCurrent,
                emphasized = false,
                enabled = canChangeProfiles && settings.resolverText.isNotBlank(),
                onClick = {
                    showCreateDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnExportAll,
                emphasized = false,
                enabled = profiles.isNotEmpty(),
                onClick = {
                    showExportAllDialog = true
                },
            ),
        )
    )

    SectionDivider()
    if (profiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.profileNoResolverLists,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    profiles.forEachIndexed { index, profile ->
        val isDefaultProfile = profile.id == ResolverProfile.DefaultId
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
            label = "resolverProfileDragTranslation",
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
            ResolverProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile?.id,
                canUse = canChangeProfiles,
                canEdit = canChangeProfiles && !isDefaultProfile,
                canDelete = canChangeProfiles && !isDefaultProfile,
                canDrag = canChangeProfiles &&
                    !isDefaultProfile &&
                    profiles.count { it.id != ResolverProfile.DefaultId } > 1,
                dragging = isDragging,
                onUse = {
                    if (canChangeProfiles) {
                        onSettingsChange(settings.applyResolverProfileToSelectedConnection(profile.id))
                    }
                },
                onDragStart = {
                    if (canChangeProfiles && !isDefaultProfile && profiles.size > 1) {
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
                onEdit = { dialogProfile = profile },
                onDelete = {
                    if (canChangeProfiles && !isDefaultProfile) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        ResolverProfileDialog(
            profile = null,
            initialResolverText = settings.resolverText,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                onSettingsChange(settings.upsertResolverProfile(profile))
                showCreateDialog = false
            },
        )
    }

    if (showExportAllDialog) {
        ResolverProfilesExportDialog(
            settings = settings,
            onDismiss = { showExportAllDialog = false },
            onShare = { exportFile ->
                shareCachedExportFile(
                    context = context,
                    exportFile = exportFile,
                    subject = ResolverProfilesExportFileName,
                    chooserTitle = shareChooserResolversLabel,
                )
            },
        )
    }

    dialogProfile?.let { profile ->
        ResolverProfileDialog(
            profile = profile,
            initialResolverText = profile.resolverText,
            onDismiss = { dialogProfile = null },
            onSave = { updatedProfile ->
                onSettingsChange(settings.upsertResolverProfile(updatedProfile.copy(id = profile.id)))
                dialogProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteResolverTitle,
            message = WhiteZiaL10n.deleteResolverMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canChangeProfiles) {
                    onSettingsChange(settings.deleteResolverProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}
