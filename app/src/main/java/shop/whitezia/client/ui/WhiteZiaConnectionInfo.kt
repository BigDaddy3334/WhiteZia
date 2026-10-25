package shop.whitezia.client.ui


import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import shop.whitezia.client.R
import shop.whitezia.client.model.AutoTuneTrialResult
import shop.whitezia.client.model.ConnectionStats

@Composable
internal fun ConnectionInfoCard(
    connectionProfileName: String,
    resolverProfileName: String,
    settingProfileName: String,
    listenAddress: String,
    httpProxyAddress: String,
    connectionMode: String,
    httpProxyEnabled: Boolean,
    protocol: String,
    socksAuthEnabled: Boolean,
    username: String,
    password: String,
    stats: ConnectionStats,
    autoTuneResults: List<AutoTuneTrialResult>,
    onSaveAutoTuneResult: (AutoTuneTrialResult) -> Unit,
    showProxyDetails: Boolean,
    splitTunnelMode: String,
    splitTunnelPackages: List<String>,
    splitTunnelAppLabels: Map<String, String>,
    canDownloadToml: Boolean,
    onDownloadToml: () -> Unit,
) {
    InfoCard(title = WhiteZiaL10n.infoCardConnection) {
        InfoRow(label = WhiteZiaL10n.infoLabelMode, value = connectionMode)
        if (showProxyDetails) {
            InfoRow(label = WhiteZiaL10n.infoLabelSocks5Proxy, value = listenAddress)
            if (httpProxyEnabled) {
                InfoRow(label = WhiteZiaL10n.infoLabelHttpProxy, value = httpProxyAddress)
            }
            ProtocolRow(protocol = protocol, showDivider = true)
            InfoRow(label = WhiteZiaL10n.infoLabelAuth, value = if (socksAuthEnabled) WhiteZiaL10n.infoLabelAuthOn else WhiteZiaL10n.infoLabelAuthOff)
            if (socksAuthEnabled) {
                InfoRow(label = WhiteZiaL10n.infoLabelUser, value = username)
                InfoRow(label = WhiteZiaL10n.infoLabelPass, value = password)
            }
        } else {
            ProtocolRow(protocol = protocol, showDivider = true)
            InfoRow(
                label = WhiteZiaL10n.infoLabelSplitTunnel,
                value = splitTunnelConnectionSummary(
                    mode = splitTunnelMode,
                    packageNames = splitTunnelPackages,
                    labelsByPackage = splitTunnelAppLabels,
                    allAppsLabel = WhiteZiaL10n.splitTunnelAllApps,
                    noAppsLabel = WhiteZiaL10n.splitTunnelNoApps,
                    onlyPrefix = WhiteZiaL10n.splitTunnelOnlyPrefix,
                    bypassPrefix = WhiteZiaL10n.splitTunnelBypassPrefix,
                ),
            )
        }
        if (autoTuneResults.isNotEmpty()) {
            AutoTuneResultsSection(
                results = autoTuneResults,
                onSaveResult = onSaveAutoTuneResult,
            )
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        CompactMetricRow(
            metrics = listOf(
                CompactMetric(
                    icon = Icons.Filled.Apps,
                    iconContentDescription = stringResource(R.string.cd_icon_apps),
                    label = WhiteZiaL10n.infoLabelApps,
                    value = stats.connectedApps.toString(),
                ),
            ),
        )
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        InfoRow(label = WhiteZiaL10n.infoLabelConnectionProfile, value = connectionProfileName)
        InfoRow(label = WhiteZiaL10n.infoLabelResolverProfile, value = resolverProfileName)
        InfoRow(label = WhiteZiaL10n.infoLabelSettingProfile, value = settingProfileName)
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
        ResolverActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = WhiteZiaL10n.downloadTomlBtn,
            emphasized = false,
            enabled = canDownloadToml,
            onClick = onDownloadToml,
        )
    }
}
