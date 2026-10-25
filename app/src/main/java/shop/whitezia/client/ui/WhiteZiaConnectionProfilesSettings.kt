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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ConnectionStatus
import shop.whitezia.client.model.ServerTestState
import shop.whitezia.client.model.WhiteZiaSettings
import shop.whitezia.client.model.deleteConnectionProfile
import shop.whitezia.client.model.deleteDuplicateConnectionProfiles
import shop.whitezia.client.model.duplicateConnectionProfileCount
import shop.whitezia.client.model.exportAllStormDnsProfileLinks
import shop.whitezia.client.model.exportStormDnsProfileLink
import shop.whitezia.client.model.importStormDnsProfileLinks
import shop.whitezia.client.model.moveConnectionProfileToIndex
import shop.whitezia.client.model.normalizedConnectionProfiles
import shop.whitezia.client.model.selectConnectionProfile
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.upsertConnectionProfile

@Composable
internal fun ConnectionProfilesSettings(
    settings: WhiteZiaSettings,
    activeConnectionProfileId: String?,
    connectionStatus: ConnectionStatus,
    serverTestState: ServerTestState,
    openCreateRequestId: Int,
    onServerTestClick: (String?) -> Unit,
    onSettingsChange: (WhiteZiaSettings) -> Unit,
) {
    val profiles = settings.normalizedConnectionProfiles()
    val selectedProfile = settings.selectedConnectionProfile()
    val customProfiles = profiles.filter { it.serverMode == "custom" }
    val serverTestScores = remember(serverTestState.results) { buildServerTestScores(serverTestState.results) }
    val serverTestResultsById = remember(serverTestState.results) {
        serverTestState.results.associateBy { it.serverId }
    }
    val context = LocalContext.current
    var dialogProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var showDeleteDuplicatesDialog by remember { mutableStateOf(false) }
    var deleteProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var draggedProfileId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredItemHeightPx by remember { mutableIntStateOf(0) }
    val canManageProfiles = connectionStatus != ConnectionStatus.CONNECTING
    val duplicateProfileCount = settings.duplicateConnectionProfileCount()
    val shareSubjectProfileLabel = WhiteZiaL10n.shareSubjectProfile
    val shareChooserProfileLabel = WhiteZiaL10n.shareChooserProfile
    val importSuccessLabel = WhiteZiaL10n.profileImportSuccess
    val importErrorProfileLabel = WhiteZiaL10n.errorImportProfile
    var importNotice by remember { mutableStateOf<String?>(null) }
    var importNoticeIsError by remember { mutableStateOf(false) }
    val qrScanner = rememberQrProfileImportLauncher(
        onDecoded = { decodedLink ->
            runCatching {
                settings.importStormDnsProfileLinks(decodedLink)
            }.onSuccess { importedSettings ->
                importNoticeIsError = false
                importNotice = importSuccessLabel
                onSettingsChange(importedSettings)
            }.onFailure { error ->
                importNoticeIsError = true
                importNotice = error.message ?: importErrorProfileLabel
            }
        },
        onError = { message ->
            importNoticeIsError = true
            importNotice = message
        },
    )
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
            onSettingsChange(settings.moveConnectionProfileToIndex(profileId, targetIndex))
        }
    }

    LaunchedEffect(openCreateRequestId) {
        if (openCreateRequestId > 0 && canManageProfiles) {
            showCreateDialog = true
        }
    }

    ProfileTopActionGrid(
        actions = listOf(
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnCreate,
                emphasized = true,
                enabled = canManageProfiles,
                onClick = {
                    showCreateDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnImport,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = {
                    showImportDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnScanQr,
                emphasized = false,
                enabled = canManageProfiles,
                onClick = qrScanner,
            ),
            ProfileTopAction(
                label = if (serverTestState.isRunning) {
                    WhiteZiaL10n.serverTestRunning
                } else {
                    WhiteZiaL10n.serverTestButton
                },
                emphasized = connectionStatus == ConnectionStatus.CONNECTED && !serverTestState.isRunning,
                enabled = connectionStatus == ConnectionStatus.CONNECTED && !serverTestState.isRunning,
                onClick = { onServerTestClick(null) },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnDeleteDups,
                emphasized = false,
                enabled = canManageProfiles && duplicateProfileCount > 0,
                onClick = {
                    showDeleteDuplicatesDialog = true
                },
            ),
            ProfileTopAction(
                label = WhiteZiaL10n.profileBtnExportAll,
                emphasized = false,
                enabled = customProfiles.any {
                    it.customServerDomain.isNotBlank() && it.customServerEncryptionKey.isNotBlank()
                },
                onClick = {
                    showExportAllDialog = true
                },
            ),
        )
    )
    importNotice?.let { message ->
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = if (importNoticeIsError) WhiteZiaPalette.Error else WhiteZiaPalette.Success,
                fontWeight = FontWeight.Medium,
            ),
        )
    }

    SectionDivider()
    GroupLabel(WhiteZiaL10n.groupCustomConnections)
    if (serverTestState.results.isEmpty() && serverTestState.message.isNotBlank()) {
        Text(
            text = serverTestDisplayMessage(serverTestState),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    if (customProfiles.isEmpty()) {
        Text(
            text = WhiteZiaL10n.customConnectionsEmpty,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 10.sp,
                color = WhiteZiaPalette.Muted,
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
    }
    customProfiles.forEachIndexed { index, profile ->
        val isActive = profile.id == activeConnectionProfileId &&
            connectionStatus != ConnectionStatus.DISCONNECTED
        val canEdit = canManageProfiles
        val canDelete = canManageProfiles && !isActive
        val canTest = connectionStatus == ConnectionStatus.CONNECTED &&
            !serverTestState.isRunning &&
            profile.customServerDomain.isNotBlank() &&
            profile.customServerEncryptionKey.isNotBlank()
        val isDragging = profile.id == draggedProfileId
        val serverTestResult = serverTestResultsById[profile.id]
        val targetTranslationY = profileDragTranslationY(
            itemIndex = index,
            draggedIndex = draggedIndex,
            targetIndex = dragTargetIndex,
            itemHeightPx = measuredItemHeightPx.toFloat(),
        )
        val animatedTranslationY by animateFloatAsState(
            targetValue = if (isDragging) 0f else targetTranslationY,
            animationSpec = spring(),
            label = "connectionProfileDragTranslation",
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
            ConnectionProfileRow(
                profile = profile,
                selected = profile.id == selectedProfile.id,
                active = isActive,
                canEdit = canEdit,
                canDelete = canDelete,
                canTest = canTest,
                canDrag = canManageProfiles && customProfiles.size > 1,
                dragging = isDragging,
                serverTestResult = serverTestResult,
                serverTestScore = serverTestResult?.let { serverTestScores[it.serverId] },
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
                onExport = {
                    exportProfile = profile
                },
                onTest = {
                    onServerTestClick(profile.id)
                },
                onEdit = {
                    dialogProfile = profile
                },
                onDelete = {
                    if (canDelete) {
                        deleteProfile = profile
                    }
                },
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }

    if (showCreateDialog) {
        ConnectionProfileDialog(
            profile = null,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                val profileId = "profile-${System.currentTimeMillis()}"
                val nextProfile = profile.copy(id = profileId, serverMode = "custom")
                onSettingsChange(
                    settings
                        .upsertConnectionProfile(nextProfile)
                        .selectConnectionProfile(profileId),
                )
                showCreateDialog = false
            },
        )
    }

    if (showImportDialog) {
        ConnectionProfileImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { links ->
                runCatching {
                    settings.importStormDnsProfileLinks(links)
                }.onSuccess { importedSettings ->
                    onSettingsChange(importedSettings)
                    showImportDialog = false
                }
            },
        )
    }

    exportProfile?.let { profile ->
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportConnection,
            fieldLabel = WhiteZiaL10n.profileFieldProfileLinkSingle,
            linkResult = remember(settings, profile) {
                runCatching { settings.exportStormDnsProfileLink(profile) }
            },
            onDismiss = { exportProfile = null },
            onShare = { link ->
                shareProfileLink(context, link, shareSubjectProfileLabel, shareChooserProfileLabel)
            },
        )
    }

    if (showExportAllDialog) {
        ConnectionProfileExportDialog(
            title = WhiteZiaL10n.profileDialogExportAllConnections,
            fieldLabel = WhiteZiaL10n.profileFieldProfileLinksLabel,
            linkResult = remember(settings, showExportAllDialog) {
                runCatching { settings.exportAllStormDnsProfileLinks() }
            },
            onDismiss = { showExportAllDialog = false },
            onShare = { links ->
                shareProfileLink(context, links, shareSubjectProfileLabel, shareChooserProfileLabel)
            },
        )
    }

    if (showDeleteDuplicatesDialog) {
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteDupsTitle,
            message = if (duplicateProfileCount == 1) {
                WhiteZiaL10n.deleteDupsMessageSingleConnection
            } else {
                WhiteZiaL10n.deleteDupsMessageManyConnection
            },
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { showDeleteDuplicatesDialog = false },
            onConfirm = {
                if (canManageProfiles && duplicateProfileCount > 0) {
                    onSettingsChange(settings.deleteDuplicateConnectionProfiles(activeConnectionProfileId))
                }
                showDeleteDuplicatesDialog = false
            },
        )
    }

    dialogProfile?.let { profile ->
        ConnectionProfileDialog(
            profile = profile,
            onDismiss = { dialogProfile = null },
            onSave = { updatedProfile ->
                val nextProfile = updatedProfile.copy(id = profile.id, serverMode = "custom")
                onSettingsChange(
                    settings
                        .upsertConnectionProfile(nextProfile)
                        .selectConnectionProfile(profile.id),
                )
                dialogProfile = null
            },
        )
    }

    deleteProfile?.let { profile ->
        val profileIsActive = profile.id == activeConnectionProfileId &&
            connectionStatus != ConnectionStatus.DISCONNECTED
        DeleteProfileConfirmationDialog(
            title = WhiteZiaL10n.deleteConnectionTitle,
            message = WhiteZiaL10n.deleteConnectionMessageTemplate,
            confirmLabel = WhiteZiaL10n.dialogDeleteConfirm,
            onDismiss = { deleteProfile = null },
            onConfirm = {
                if (canManageProfiles && !profileIsActive) {
                    onSettingsChange(settings.deleteConnectionProfile(profile.id))
                }
                deleteProfile = null
            },
        )
    }
}
