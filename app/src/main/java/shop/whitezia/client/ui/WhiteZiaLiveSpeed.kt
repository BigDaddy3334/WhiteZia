package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import shop.whitezia.client.R
import shop.whitezia.client.model.ConnectionStats
import shop.whitezia.client.model.ServerTestState
import shop.whitezia.client.model.ServerTestStatus

@Composable
internal fun LiveSpeedStrip(
    stats: ConnectionStats,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WhiteZiaPalette.Surface)
            .border(2.dp, WhiteZiaPalette.Border, RoundedCornerShape(18.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeedIndicator(
            icon = Icons.Filled.Download,
            iconContentDescription = stringResource(R.string.cd_icon_download),
            label = WhiteZiaL10n.speedDown,
            value = formatDataSpeed(stats.downloadSpeedBytesPerSecond),
            modifier = Modifier.weight(1f),
        )
        SpeedIndicator(
            icon = Icons.Filled.Upload,
            iconContentDescription = stringResource(R.string.cd_icon_upload),
            label = WhiteZiaL10n.speedUp,
            value = formatDataSpeed(stats.uploadSpeedBytesPerSecond),
            modifier = Modifier.weight(1f),
        )
        SpeedIndicator(
            icon = Icons.Filled.DataUsage,
            iconContentDescription = stringResource(R.string.cd_icon_data_usage),
            label = WhiteZiaL10n.speedTotalUsage,
            value = formatDataSize(stats.totalDataUsageBytes),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun serverTestDisplayMessage(state: ServerTestState): String {
    val completed = state.results.count {
        it.status == ServerTestStatus.Ready || it.status == ServerTestStatus.Failed
    }
    val ready = state.results.count { it.status == ServerTestStatus.Ready }
    val failed = state.results.count { it.status == ServerTestStatus.Failed }
    val total = state.results.size

    return when {
        state.isRunning && total > 0 -> WhiteZiaL10n.serverTestProgressTemplate.format(completed, total)
        total > 0 && completed == total -> WhiteZiaL10n.serverTestSummaryTemplate.format(ready, failed)
        state.message == "Server Test: no saved server profiles are configured" ->
            WhiteZiaL10n.serverTestNoSavedServers
        state.message == "Server Test: no connected resolvers are available" ->
            WhiteZiaL10n.serverTestNoConnectedResolvers
        state.message.startsWith("Server Test failed:") ->
            WhiteZiaL10n.serverTestFailedTemplate.format(state.message.substringAfter(':').trim())
        else -> state.message.ifBlank { WhiteZiaL10n.serverTestIdle }
    }
}
