package shop.whitezia.client

import java.util.Locale

internal fun formatMbps(bytesPerSecond: Long): String {
    return if (bytesPerSecond <= 0L) {
        "0 Мбит/с"
    } else {
        "${"%.2f".format(Locale.US, bytesPerSecond * 8.0 / 1_000_000.0)} Мбит/с"
    }
}

internal fun networkTransportLabel(transport: String): String = when (transport) {
    NetworkTransportWifi -> "Wi-Fi"
    NetworkTransportMobile -> "мобильная сеть"
    NetworkTransportOther -> "другая сеть"
    else -> "нет сети"
}

internal fun buildVisibleLog(localLog: String, runtimeLogs: List<String>): String {
    val runtimeLines = runtimeLogs
        .map(String::trim)
        .filter(String::isNotBlank)
        .filter { it != "Idle" }
    val localLines = localLog
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
    return (runtimeLines.ifEmpty { localLines })
        .fold(mutableListOf<String>()) { lines, line ->
            if (lines.lastOrNull() != line) lines += line
            lines
        }
        .joinToString(separator = "\n")
}

internal const val WhiteZiaVisibleLogTailLimit = 10
internal const val WhiteZiaFullVisibleLogLimit = 300
