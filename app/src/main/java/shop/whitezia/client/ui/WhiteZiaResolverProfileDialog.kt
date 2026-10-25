package shop.whitezia.client.ui


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.validateResolverText

@Composable
internal fun ResolverProfileDialog(
    profile: ResolverProfile?,
    initialResolverText: String,
    onDismiss: () -> Unit,
    onSave: (ResolverProfile) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var resolverText by remember(profile?.id) { mutableStateOf(profile?.resolverText ?: initialResolverText) }
    var importError by remember(profile?.id) { mutableStateOf<String?>(null) }
    val resolverValidation = remember(resolverText) { validateResolverText(resolverText) }
    val invalidResolverIpTemplate = WhiteZiaL10n.errorInvalidResolverIpTemplate
    val enterValidResolverIpLabel = WhiteZiaL10n.errorEnterValidResolverIp
    val enterProfileNameToSaveLabel = WhiteZiaL10n.errorEnterProfileNameToSave
    val resolverValidSingularTemplate = WhiteZiaL10n.resolverValidSingularTemplate
    val resolverValidPluralTemplate = WhiteZiaL10n.resolverValidPluralTemplate
    val unableToOpenResolverLabel = WhiteZiaL10n.errorUnableToOpenResolverFile
    val noResolverEntriesLabel = WhiteZiaL10n.errorNoResolverEntries
    val validationMessage = resolverValidationMessage(
        name = name,
        resolverText = resolverText,
        invalidEntries = resolverValidation.invalidEntries,
        validResolverCount = resolverValidation.normalizedResolvers.size,
        invalidResolverIpTemplate = invalidResolverIpTemplate,
        enterValidResolverIpLabel = enterValidResolverIpLabel,
        enterProfileNameToSaveLabel = enterProfileNameToSaveLabel,
        validResolverSingularTemplate = resolverValidSingularTemplate,
        validResolverPluralTemplate = resolverValidPluralTemplate,
    )
    val validationMessageIsError = validationMessage != null && (!resolverValidation.isValid || name.isBlank())
    val errorImportResolverLabel = WhiteZiaL10n.errorImportResolver
    val importResolverFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        readResolverTextFromUri(
            context = context,
            uri = uri,
            unableToOpenLabel = unableToOpenResolverLabel,
            invalidResolverIpTemplate = invalidResolverIpTemplate,
            noResolverEntriesLabel = noResolverEntriesLabel,
        )
            .onSuccess { importedResolverText ->
                resolverText = importedResolverText
                importError = null
            }
            .onFailure { error ->
                importError = error.message ?: errorImportResolverLabel
            }
    }
    val canSave = name.trim().isNotEmpty() && resolverValidation.isValid

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
                text = if (profile == null) WhiteZiaL10n.profileDialogCreateResolver else WhiteZiaL10n.profileDialogEditResolver,
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
                placeholder = WhiteZiaL10n.resolverProfileHomeResolversPlaceholder,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnImportFile,
                    emphasized = false,
                    enabled = true,
                    onClick = {
                        importResolverFileLauncher.launch(ResolverImportMimeTypes)
                    },
                )
                CompactActionButton(
                    modifier = Modifier.weight(1f),
                    label = WhiteZiaL10n.profileBtnClear,
                    emphasized = false,
                    enabled = resolverText.isNotBlank(),
                    onClick = {
                        resolverText = ""
                        importError = null
                    },
                )
            }
            importError?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = WhiteZiaPalette.Error,
                    ),
                )
            }
            WhiteZiaTextField(
                label = WhiteZiaL10n.profileFieldResolvers,
                value = resolverText,
                onValueChange = {
                    resolverText = it
                    importError = null
                },
                placeholder = WhiteZiaL10n.resolverFieldPlaceholder,
                singleLine = false,
                minLines = 6,
                maxLines = 10,
            )
            validationMessage?.let { message ->
                Spacer(modifier = Modifier.height(WhiteZiaSpacing.iconSpacing))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = if (validationMessageIsError) WhiteZiaPalette.Error else WhiteZiaPalette.Muted,
                    ),
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
                            ResolverProfile(
                                id = profile?.id.orEmpty(),
                                name = name.trim(),
                                resolverText = resolverValidation.normalizedText,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun ResolverProfileRow(
    profile: ResolverProfile,
    selected: Boolean,
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val resolverCount = profile.resolverText
        .let { validateResolverText(it).normalizedResolvers.size }
    val resolverSummary = WhiteZiaL10n.resolverProfileSummary(resolverCount) +
        if (selected) " - ${WhiteZiaL10n.profileStatusSelected}" else ""
    val displayName = if (profile.id == ResolverProfile.DefaultId) WhiteZiaL10n.setupDefaultResolver else profile.name
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
                    text = resolverSummary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = if (selected) WhiteZiaPalette.AccentText else WhiteZiaPalette.Muted,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProfileActionsMenu(
                contentDescription = WhiteZiaL10n.resolverProfileMenuActions,
                actions = listOf(
                    ProfileMenuAction(
                        label = if (selected) WhiteZiaL10n.profileMenuUseSelected else WhiteZiaL10n.profileMenuUse,
                        icon = Icons.Rounded.Check,
                        contentDescription = WhiteZiaL10n.useResolverProfile,
                        enabled = canUse,
                        onClick = onUse,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuEdit,
                        icon = Icons.Rounded.Edit,
                        contentDescription = WhiteZiaL10n.editResolverProfileAction,
                        enabled = canEdit,
                        onClick = onEdit,
                    ),
                    ProfileMenuAction(
                        label = WhiteZiaL10n.profileMenuDelete,
                        icon = Icons.Rounded.Delete,
                        contentDescription = WhiteZiaL10n.deleteResolverProfileAction,
                        enabled = canDelete,
                        onClick = onDelete,
                    ),
                ),
            )
        }
    }
}
