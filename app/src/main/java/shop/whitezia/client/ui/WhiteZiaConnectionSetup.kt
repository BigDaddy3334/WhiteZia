package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import shop.whitezia.client.R
import shop.whitezia.client.model.ConnectionProfile
import shop.whitezia.client.model.ResolverProfile
import shop.whitezia.client.model.selectedConnectionProfile
import shop.whitezia.client.model.selectedResolverProfile

@Composable
internal fun ConnectionSetupCard(
    selectedConnectionProfile: ConnectionProfile,
    selectedResolverProfile: ResolverProfile?,
    resolverCount: Int,
    resolverIssue: String?,
    actionsEnabled: Boolean,
    onAddConnectionClick: () -> Unit,
    onAddResolverProfileClick: () -> Unit,
) {
    val serverRouteMissing = WhiteZiaL10n.serverRouteMissing
    val serverRouteAndKeyMissing = WhiteZiaL10n.setupServerRouteAndKey
    val encryptionKeyMissing = WhiteZiaL10n.setupEncryptionKeyMissing
    val serverRoute = selectedConnectionProfile.customServerDomain.ifBlank { serverRouteMissing }
    val connectionIssue = when {
        selectedConnectionProfile.customServerDomain.isBlank() &&
            selectedConnectionProfile.customServerEncryptionKey.isBlank() -> serverRouteAndKeyMissing
        selectedConnectionProfile.customServerDomain.isBlank() -> serverRouteMissing
        selectedConnectionProfile.customServerEncryptionKey.isBlank() -> encryptionKeyMissing
        else -> null
    }
    val resolverSource = when {
        selectedResolverProfile == null -> WhiteZiaL10n.setupManualResolvers
        selectedResolverProfile.id == ResolverProfile.DefaultId -> WhiteZiaL10n.setupDefaultResolver
        else -> selectedResolverProfile.name
    }
    val resolverDetail = resolverIssue ?: resolverCountLabel(
        resolverCount,
        WhiteZiaL10n.resolverCountOneTemplate,
        WhiteZiaL10n.resolverCountTemplate,
    )

    InfoCard(title = WhiteZiaL10n.setupSectionSetup, compact = true) {
        SetupInfoRow(
            icon = if (connectionIssue == null) Icons.Rounded.Link else Icons.Rounded.WarningAmber,
            iconContentDescription = stringResource(
                if (connectionIssue == null) R.string.cd_icon_link else R.string.cd_icon_warning
            ),
            label = WhiteZiaL10n.sectionConnection,
            value = when {
                selectedConnectionProfile.name.isBlank() -> WhiteZiaL10n.sectionConnection
                selectedConnectionProfile.id == ConnectionProfile.DefaultId -> WhiteZiaL10n.setupDefaultConnection
                else -> selectedConnectionProfile.name
            },
            detail = connectionIssue ?: serverRoute,
            color = if (connectionIssue == null) WhiteZiaPalette.AccentText else WhiteZiaPalette.WarningText,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(WhiteZiaPalette.Divider),
        )
        SetupInfoRow(
            icon = if (resolverIssue == null) Icons.Rounded.Check else Icons.Rounded.WarningAmber,
            iconContentDescription = stringResource(
                if (resolverIssue == null) R.string.cd_icon_check else R.string.cd_icon_warning
            ),
            label = WhiteZiaL10n.setupResolversLabel,
            value = resolverSource,
            detail = resolverDetail,
            color = if (resolverIssue == null) WhiteZiaPalette.Success else WhiteZiaPalette.WarningText,
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.md))
        SetupActionButton(
            label = WhiteZiaL10n.setupAddConnection,
            supportingText = WhiteZiaL10n.setupAddConnectionSupportingText,
            icon = Icons.Rounded.Add,
            iconContentDescription = stringResource(R.string.cd_icon_add),
            emphasized = true,
            enabled = actionsEnabled,
            onClick = onAddConnectionClick,
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        SetupActionButton(
            label = WhiteZiaL10n.setupAddResolver,
            supportingText = WhiteZiaL10n.setupAddResolverSupportingText,
            icon = Icons.Rounded.Add,
            iconContentDescription = stringResource(R.string.cd_icon_add),
            emphasized = false,
            enabled = actionsEnabled,
            onClick = onAddResolverProfileClick,
        )
    }
}
