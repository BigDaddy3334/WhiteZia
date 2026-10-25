package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ServerTestResult

@Composable
internal fun ConnectionProfileDialog(
    profile: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var domain by remember(profile?.id) { mutableStateOf(profile?.customServerDomain.orEmpty()) }
    var encryptionKey by remember(profile?.id) { mutableStateOf(profile?.customServerEncryptionKey.orEmpty()) }
    var encryptionMethod by remember(profile?.id) {
        mutableIntStateOf(profile?.customServerEncryptionMethod ?: 1)
    }
    val canSave = name.isNotBlank() && domain.isNotBlank() && encryptionKey.isNotBlank()

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
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateConnection else WhiteZiaL10n.profileDialogEditConnection,
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
                placeholder = WhiteZiaL10n.profileMyStormDnsPlaceholder,
            )
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldDomain,
                value = domain,
                onValueChange = { domain = it.trim() },
                placeholder = WhiteZiaL10n.profileDomainPlaceholder,
            )
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldEncryptionKey,
                value = encryptionKey,
                onValueChange = { encryptionKey = it.trim() },
                placeholder = WhiteZiaL10n.profileEncryptionKeyPlaceholder,
            )
            WhiteZiaDropdownField(
                label = WhiteZiaL10n.profileFieldEncryptionMethod,
                value = encryptionMethod,
                options = localizedEncryptionMethods(),
                onValueChange = { encryptionMethod = it },
            )
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
                            ConnectionProfile(
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                                serverMode = "custom",
                                customServerDomain = domain.trim().trimEnd('.'),
                                customServerEncryptionKey = encryptionKey.trim(),
                                customServerEncryptionMethod = encryptionMethod,
                                resolverProfileId = profile?.resolverProfileId.orEmpty(),
                                connectionMode = profile?.connectionMode ?: "proxy",
                            ),
                        )
                    },
                )
            }
        }
    }
}

internal fun dragOffsetToProfileIndexOffset(
    offsetY: Float,
    itemHeightPx: Float,
): Int {
    if (itemHeightPx <= 0f) {
        return 0
    }
    return when {
        offsetY > 0f -> ((offsetY + itemHeightPx / 2f) / itemHeightPx).toInt()
        offsetY < 0f -> ((offsetY - itemHeightPx / 2f) / itemHeightPx).toInt()
        else -> 0
    }
}

internal fun profileDragTranslationY(
    itemIndex: Int,
    draggedIndex: Int?,
    targetIndex: Int?,
    itemHeightPx: Float,
): Float {
    if (draggedIndex == null || targetIndex == null || itemHeightPx <= 0f) {
        return 0f
    }
    return when {
        draggedIndex < targetIndex && itemIndex in (draggedIndex + 1)..targetIndex -> -itemHeightPx
        draggedIndex > targetIndex && itemIndex in targetIndex until draggedIndex -> itemHeightPx
        else -> 0f
    }
}

@Composable
internal fun ConnectionProfileRow(
    profile: ConnectionProfile,
    selected: Boolean,
    active: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canTest: Boolean,
    canDrag: Boolean,
    dragging: Boolean,
    serverTestResult: ServerTestResult?,
    serverTestScore: ServerTestScore?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onExport: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val domain = profile.customServerDomain.ifBlank { WhiteZiaL10n.profileDomainFallback }
    val activeStatus = WhiteZiaL10n.profileStatusActive
    val selectedStatus = WhiteZiaL10n.profileStatusSelected
    val connectionSummary = when {
        active -> "$domain - $activeStatus"
        selected -> "$domain - $selectedStatus"
        else -> domain
    }
    val displayName = if (profile.id == ConnectionProfile.DefaultId) WhiteZiaL10n.setupDefaultConnection else profile.name
    val testRating = serverTestResult?.let { result ->
        serverTestRatingDisplay(
            result = result,
            score = serverTestScore ?: ServerTestScore(ServerTestScoreBucket.Pending, 0f),
        )
    }
    val rowSurface = when {
        testRating != null -> testRating.surface
        selected -> WhiteZiaPalette.AccentSurface
        else -> WhiteZiaPalette.SurfaceAlt
    }
    val rowBorder = when {
        testRating != null -> testRating.color.copy(alpha = 0.24f)
        selected -> WhiteZiaPalette.Accent.copy(alpha = 0.18f)
        else -> WhiteZiaPalette.Border
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(rowSurface)
            .border(
                1.5.dp,
                rowBorder,
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
                    text = connectionSummary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = when {
                            active -> WhiteZiaPalette.Success
                            selected -> WhiteZiaPalette.AccentText
                            else -> WhiteZiaPalette.Muted
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.connectionProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = WhiteZiaL10n.serverTestSingleButton,
                        icon = Icons.Rounded.Speed,
                        contentDescription = WhiteZiaL10n.serverTestSingleButton,
                        enabled = canTest,
                        onClick = onTest,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuExport,
                        icon = Icons.Rounded.Link,
                        contentDescription = WhiteZiaL10n.exportConnectionProfileAction,
                        enabled = profile.customServerDomain.isNotBlank() && profile.customServerEncryptionKey.isNotBlank(),
                        onClick = onExport,
                    ),
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editConnectionProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = if (active) WhiteZiaL10n.profileMenuDelete else WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = if (active) WhiteZiaL10n.deleteConnectionProfileBlockedAction else WhiteZiaL10n.deleteConnectionProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
        if (serverTestResult != null && testRating != null) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            ServerTestInlineResult(rating = testRating)
        }
    }
}

@Composable
private fun ServerTestInlineResult(rating: ServerTestRatingDisplay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(rating.color.copy(alpha = 0.12f))
            .border(1.5.dp, rating.color.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (rating.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = rating.color,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = rating.icon,
                contentDescription = rating.label,
                tint = rating.color,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = WhiteZiaL10n.serverTestTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 9.sp,
                color = WhiteZiaPalette.Ink,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(rating.color.copy(alpha = 0.13f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = rating.icon,
                contentDescription = rating.label,
                tint = rating.color,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = rating.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 8.sp,
                    color = rating.color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                ),
            )
        }
    }
}
