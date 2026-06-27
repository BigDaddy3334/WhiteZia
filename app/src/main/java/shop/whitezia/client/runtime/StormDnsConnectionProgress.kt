package shop.whitezia.client.runtime

import kotlin.math.roundToInt
import shop.whitezia.client.model.ConnectionProgressState

fun parseStormDnsConnectionProgressLine(line: String): ConnectionProgressState? {
    val cleanLine = line
        .replace(AnsiEscapeRegex, "")
        .trim()
    val markerIndex = cleanLine.indexOf(ProgressMarker)
    if (markerIndex < 0) {
        return null
    }

    val fields = ProgressFieldRegex.findAll(cleanLine.substring(markerIndex + ProgressMarker.length))
        .associate { match -> match.groupValues[1] to match.groupValues[2] }
    val phase = fields["phase"]?.lowercase().orEmpty()
    if (phase.isBlank()) {
        return null
    }

    val completed = fields["completed"].toIntOrZero()
    val total = fields["total"].toIntOrZero()
    val valid = fields["valid"].toIntOrZero()
    val rejected = fields["rejected"].toIntOrZero()
    val percent = fields["percent"]?.toIntOrNull()
        ?: inferProgressPercent(phase, completed, total)

    return ConnectionProgressState(
        phase = phase,
        percent = percent.coerceIn(0, 100),
        completed = completed,
        total = total,
        valid = valid,
        rejected = rejected,
    )
}

private fun inferProgressPercent(
    phase: String,
    completed: Int,
    total: Int,
): Int {
    return when {
        phase == "mtu" && total > 0 -> (10f + (completed.coerceIn(0, total).toFloat() / total) * 70f).roundToInt()
        phase == "starting" -> 5
        phase == "selecting" -> 85
        phase == "session" -> 90
        phase == "runtime" -> 98
        phase == "connected" -> 100
        else -> 0
    }
}

private fun String?.toIntOrZero(): Int {
    return this?.toIntOrNull() ?: 0
}

private const val ProgressMarker = "WD_PROGRESS"

private val ProgressFieldRegex = Regex("""(\w+)=([^\s]+)""")

private val AnsiEscapeRegex = Regex("\\u001B\\[[;\\d]*m")
