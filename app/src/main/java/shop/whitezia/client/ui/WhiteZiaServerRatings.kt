package shop.whitezia.client.ui


import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import shop.whitezia.client.model.ServerTestResult
import shop.whitezia.client.model.ServerTestStatus

@Composable
internal fun serverTestRatingDisplay(
    result: ServerTestResult,
    score: ServerTestScore,
): ServerTestRatingDisplay {
    return when {
        result.status == ServerTestStatus.Failed ->
            ServerTestRatingDisplay(
                label = WhiteZiaL10n.serverTestScoreUnavailable,
                icon = Icons.Rounded.Close,
                color = WhiteZiaPalette.Error,
                surface = WhiteZiaPalette.ErrorSurface,
            )
        score.bucket == ServerTestScoreBucket.Poor ->
            ServerTestRatingDisplay(
                label = WhiteZiaL10n.serverTestScorePoor,
                icon = Icons.Rounded.Close,
                color = WhiteZiaPalette.Error,
                surface = WhiteZiaPalette.ErrorSurface,
            )
        score.bucket == ServerTestScoreBucket.Good -> ServerTestRatingDisplay(
            label = WhiteZiaL10n.serverTestScoreGood,
            icon = Icons.Rounded.Check,
            color = WhiteZiaPalette.Success,
            surface = WhiteZiaPalette.SuccessSurface,
        )
        score.bucket == ServerTestScoreBucket.Fair -> ServerTestRatingDisplay(
            label = WhiteZiaL10n.serverTestScoreFair,
            icon = Icons.Rounded.WarningAmber,
            color = WhiteZiaPalette.Warning,
            surface = WhiteZiaPalette.WarningSurface,
        )
        else -> ServerTestRatingDisplay(
            label = when (result.status) {
                ServerTestStatus.Starting -> WhiteZiaL10n.serverTestStarting
                ServerTestStatus.Measuring -> WhiteZiaL10n.serverTestMeasuring
                else -> WhiteZiaL10n.serverTestPending
            },
            icon = Icons.Rounded.Tune,
            color = WhiteZiaPalette.Warning,
            surface = WhiteZiaPalette.WarningSurface,
            loading = result.status == ServerTestStatus.Starting || result.status == ServerTestStatus.Measuring,
        )
    }
}

internal fun buildServerTestScores(results: List<ServerTestResult>): Map<String, ServerTestScore> {
    val measuredResults = results.filter { it.status == ServerTestStatus.Ready }
    val speeds = measuredResults
        .map { it.speedBytesPerSecond }
        .filter { it > 0L }
    val pings = measuredResults
        .mapNotNull { it.pingMillis }
        .filter { it > 0L }

    return results.associate { result ->
        val score = when (result.status) {
            ServerTestStatus.Failed -> ServerTestScore(ServerTestScoreBucket.Poor, 0f)
            ServerTestStatus.Ready -> {
                val metricScores = listOfNotNull(
                    result.speedBytesPerSecond
                        .takeIf { it > 0L }
                        ?.let { percentileScore(value = it, values = speeds, higherIsBetter = true) },
                    result.pingMillis
                        ?.takeIf { it > 0L }
                        ?.let { percentileScore(value = it, values = pings, higherIsBetter = false) },
                )
                val percentile = if (metricScores.isEmpty()) {
                    0f
                } else {
                    metricScores.average().toFloat().coerceIn(0f, 1f)
                }
                ServerTestScore(
                    bucket = serverTestScoreBucket(percentile),
                    percentile = percentile,
                )
            }
            else -> ServerTestScore(ServerTestScoreBucket.Pending, 0f)
        }
        result.serverId to score
    }
}

private fun percentileScore(
    value: Long,
    values: List<Long>,
    higherIsBetter: Boolean,
): Float {
    if (values.isEmpty()) {
        return 0f
    }
    val favorableCount = if (higherIsBetter) {
        values.count { value >= it }
    } else {
        values.count { value <= it }
    }
    return (favorableCount.toFloat() / values.size.toFloat()).coerceIn(0f, 1f)
}

private fun serverTestScoreBucket(percentile: Float): ServerTestScoreBucket {
    return when {
        percentile >= 0.67f -> ServerTestScoreBucket.Good
        percentile >= 0.34f -> ServerTestScoreBucket.Fair
        else -> ServerTestScoreBucket.Poor
    }
}

internal data class ServerTestRatingDisplay(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val surface: Color,
    val loading: Boolean = false,
)

internal data class ServerTestScore(
    val bucket: ServerTestScoreBucket,
    val percentile: Float,
)

internal enum class ServerTestScoreBucket {
    Good,
    Fair,
    Poor,
    Pending,
}
