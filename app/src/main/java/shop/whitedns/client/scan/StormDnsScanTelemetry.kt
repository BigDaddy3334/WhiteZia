package shop.whitedns.client.scan

sealed class StormDnsScanTelemetry {
    data class Valid(val resolver: String) : StormDnsScanTelemetry()
    data class Rejected(val resolver: String) : StormDnsScanTelemetry()
    data class Complete(
        val total: Int,
        val valid: Int,
        val rejected: Int,
    ) : StormDnsScanTelemetry()
}

fun parseStormDnsScanLine(line: String): StormDnsScanTelemetry? {
    val cleanLine = line
        .replace(AnsiEscapeRegex, "")
        .trim()
    val markerIndex = cleanLine.indexOf(ScanMarker)
    if (markerIndex < 0) {
        return null
    }

    val fields = ScanFieldRegex.findAll(cleanLine.substring(markerIndex + ScanMarker.length))
        .associate { match -> match.groupValues[1] to match.groupValues[2] }
    return when (fields["event"]) {
        "valid" -> fields["resolver"]
            ?.takeIf(String::isNotBlank)
            ?.let(StormDnsScanTelemetry::Valid)
        "rejected" -> fields["resolver"]
            ?.takeIf(String::isNotBlank)
            ?.let(StormDnsScanTelemetry::Rejected)
        "complete" -> StormDnsScanTelemetry.Complete(
            total = fields["total"].toIntOrZero(),
            valid = fields["valid"].toIntOrZero(),
            rejected = fields["rejected"].toIntOrZero(),
        )
        else -> null
    }
}

private fun String?.toIntOrZero(): Int {
    return this?.toIntOrNull() ?: 0
}

private const val ScanMarker = "WD_SCAN"
private val ScanFieldRegex = Regex("""(\w+)=([^\s]+)""")
private val AnsiEscapeRegex = Regex("${27.toChar()}\\[[;?0-9]*[ -/]*[@-~]")
