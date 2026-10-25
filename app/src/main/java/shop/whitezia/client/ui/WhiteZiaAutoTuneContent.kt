package shop.whitezia.client.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shop.whitezia.client.model.AutoTuneTrialResult

@Composable
internal fun AutoTuneResultsSection(
    results: List<AutoTuneTrialResult>,
    onSaveResult: (AutoTuneTrialResult) -> Unit,
) {
    Spacer(modifier = Modifier.height(WhiteZiaSpacing.inputSpacing))
    GroupLabel(WhiteZiaL10n.groupParallelTestResults)
    results.forEachIndexed { index, result ->
        AutoTuneResultRow(
            result = result,
            onSaveResult = onSaveResult,
        )
        if (index < results.lastIndex) {
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(WhiteZiaPalette.Divider),
            )
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        }
    }
}

@Composable
private fun AutoTuneResultRow(
    result: AutoTuneTrialResult,
    onSaveResult: (AutoTuneTrialResult) -> Unit,
) {
    val inProgress = result.isAutoTuneInProgress()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (result.selected) WhiteZiaPalette.AccentSurface else Color.Transparent)
            .border(
                1.5.dp,
                if (result.selected) WhiteZiaPalette.Accent.copy(alpha = 0.18f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = autoTuneResultLabel(result),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = WhiteZiaPalette.Ink,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = WhiteZiaPalette.Accent,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AutoTuneMetricPill(
                metric = autoTuneMtuMetric(result),
                modifier = Modifier.weight(1f),
            )
            AutoTuneMetricPill(
                metric = AutoTuneDisplayMetric(
                    icon = Icons.Rounded.Speed,
                    iconContentDescription = WhiteZiaL10n.cdParallelTestSpeed,
                    label = WhiteZiaL10n.autoTuneSpeedLabel,
                    value = autoTuneSpeedValue(result),
                    color = WhiteZiaPalette.Success,
                    surface = WhiteZiaPalette.SuccessSurface,
                    loading = result.status == "measuring",
                ),
                modifier = Modifier.weight(1f),
            )
            AutoTuneMetricPill(
                metric = AutoTuneDisplayMetric(
                    icon = Icons.Rounded.NetworkPing,
                    iconContentDescription = WhiteZiaL10n.cdParallelTestPing,
                    label = WhiteZiaL10n.autoTunePingLabel,
                    value = result.pingMillis?.let { "${it}ms" } ?: "-",
                    color = WhiteZiaPalette.AccentText,
                    surface = WhiteZiaPalette.AccentSurface,
                    loading = false,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        autoTuneStatusMessage(
            result = result,
            startingLabel = WhiteZiaL10n.autoTuneStartingTest,
            measuringLabel = WhiteZiaL10n.autoTuneMeasuringSpeed,
            failedFallback = WhiteZiaL10n.autoTuneFailedFallback,
            measuredKeyword = WhiteZiaL10n.autoTuneMeasuredKeyword,
        )?.let { message ->
            Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (result.status == "failed") WhiteZiaPalette.WarningText else WhiteZiaPalette.Muted,
                    fontWeight = if (result.status == "failed") FontWeight.Medium else FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = Modifier.height(WhiteZiaSpacing.sm))
        CompactActionButton(
            modifier = Modifier.fillMaxWidth(),
            label = WhiteZiaL10n.saveSettingAs,
            emphasized = result.selected,
            enabled = true,
            onClick = { onSaveResult(result) },
        )
    }
}

@Composable
private fun AutoTuneMetricPill(
    metric: AutoTuneDisplayMetric,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(metric.surface)
            .border(1.5.dp, metric.color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (metric.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = metric.color,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = metric.icon,
                contentDescription = metric.iconContentDescription,
                tint = metric.color,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 7.sp,
                    letterSpacing = 0.5.sp,
                    color = WhiteZiaPalette.Muted,
                ),
            )
            Text(
                text = metric.value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteZiaPalette.Ink,
                ),
            )
        }
    }
}

private data class AutoTuneDisplayMetric(
    val icon: ImageVector,
    val iconContentDescription: String,
    val label: String,
    val value: String,
    val color: Color,
    val surface: Color,
    val loading: Boolean = false,
)

@Composable
private fun autoTuneMtuMetric(result: AutoTuneTrialResult): AutoTuneDisplayMetric {
    return when (result.status) {
        "failed" -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Close,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuFailed,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuFail,
            color = WhiteZiaPalette.Error,
            surface = WhiteZiaPalette.ErrorSurface,
        )
        "listening", "measuring", "ready" -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Check,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuPassed,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuPass,
            color = WhiteZiaPalette.Success,
            surface = WhiteZiaPalette.SuccessSurface,
        )
        else -> AutoTuneDisplayMetric(
            icon = Icons.Rounded.Tune,
            iconContentDescription = WhiteZiaL10n.cdAutoTuneMtuTesting,
            label = WhiteZiaL10n.groupMtu,
            value = WhiteZiaL10n.autoTuneMtuTest,
            color = WhiteZiaPalette.AccentText,
            surface = WhiteZiaPalette.AccentSurface,
            loading = true,
        )
    }
}

private fun autoTuneResultLabel(result: AutoTuneTrialResult): String {
    return if (result.selected) {
        "${result.label} - Selected"
    } else {
        result.label
    }
}

private fun autoTuneSpeedValue(result: AutoTuneTrialResult): String {
    return if (result.speedBytesPerSecond > 0L) {
        formatDataSpeed(result.speedBytesPerSecond)
    } else {
        "-"
    }
}

private fun autoTuneStatusMessage(
    result: AutoTuneTrialResult,
    startingLabel: String,
    measuringLabel: String,
    failedFallback: String,
    measuredKeyword: String,
): String? {
    return when (result.status) {
        "starting" -> startingLabel
        "listening" -> measuringLabel
        "measuring" -> measuringLabel
        "failed" -> result.message.ifBlank { failedFallback }
        else -> result.message.takeIf { it.isNotBlank() && it != measuredKeyword }
    }
}

private fun AutoTuneTrialResult.isAutoTuneInProgress(): Boolean {
    return status == "pending" || status == "starting" || status == "listening" || status == "measuring"
}
