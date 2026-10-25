package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.model.AdvancedSettingsProfile
import shop.whitezia.client.model.WhiteZiaSettings

@Composable
internal fun AdvancedSettingsProfileDialog(
    profile: AdvancedSettingsProfile?,
    initialName: String,
    initialSettings: WhiteZiaSettings,
    onDismiss: () -> Unit,
    onSave: (AdvancedSettingsProfile) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(initialName) }
    var draftSettings by remember(profile?.id) { mutableStateOf(initialSettings) }
    val canSave = name.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WhiteZiaPalette.Surface)
                .border(1.5.dp, WhiteZiaPalette.Border, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateSetting else WhiteZiaL10n.profileDialogEditSetting,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldName,
                value = name,
                onValueChange = { name = it },
                placeholder = WhiteZiaL10n.settingProfileFastTunnelPlaceholder,
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AdvancedSettingsFields(
                    settings = draftSettings,
                    showProxySettings = true,
                    onSettingsChange = { draftSettings = it },
                )
            }
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnCancel,
                    emphasized = false,
                    enabled = true,
                    onClick = onDismiss,
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.btnSave,
                    emphasized = true,
                    enabled = canSave,
                    onClick = {
                        onSave(
                            AdvancedSettingsProfile.fromSettings(
                                settings = draftSettings,
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingProfileRow(
    profile: AdvancedSettingsProfile,
    selected: Boolean,
    dirty: Boolean,
    canUse: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canDrag: Boolean,
    dragging: Boolean,
    onUse: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val summarySuffix = when {
        dirty -> " - ${WhiteZiaL10n.profileStatusModified}"
        selected -> " - ${WhiteZiaL10n.profileStatusSelected}"
        else -> ""
    }
    val displayName = if (profile.id == AdvancedSettingsProfile.DefaultId) WhiteZiaL10n.setupDefaultAdvanced else profile.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) WhiteZiaPalette.AccentSurface else WhiteZiaPalette.SurfaceAlt)
            .border(
                1.5.dp,
                if (selected) WhiteZiaPalette.Accent.copy(alpha = 0.18f) else WhiteZiaPalette.Border,
                RoundedCornerShape(11.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileDragHandle(
                enabled = canDrag,
                dragging = dragging,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = WhiteZiaPalette.Ink,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.xs))
                Text(
                    text = "${advancedProfileSummary(profile)}$summarySuffix",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = when {
                            dirty -> WhiteZiaPalette.WarningText
                            selected -> WhiteZiaPalette.AccentText
                            else -> WhiteZiaPalette.Muted
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.settingProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuUse,
                        icon = Icons.Rounded.Check,
                        contentDescription = WhiteZiaL10n.useSettingProfile,
                        enabled = canUse,
                        onClick = onUse,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuExport,
                        icon = Icons.Rounded.Link,
                        contentDescription = WhiteZiaL10n.exportSettingProfileAction,
                        enabled = true,
                        onClick = onExport,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editSettingProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = WhiteZiaL10n.deleteSettingProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}
