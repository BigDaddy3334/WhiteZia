package shop.whitezia.client.ui


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

internal fun formatDataSpeed(bytesPerSecond: Long): String {
    return "${formatDataSize(bytesPerSecond)}/s"
}

internal fun formatDataSize(bytes: Long): String {
    if (bytes <= 0) {
        return "0 B"
    }

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    if (unitIndex == 0) {
        return "$bytes B"
    }

    val pattern = if (value >= 10.0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unitIndex])
}
