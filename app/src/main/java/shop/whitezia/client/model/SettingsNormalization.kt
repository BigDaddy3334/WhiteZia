package shop.whitezia.client.model

internal fun normalizeSplitTunnelMode(raw: String): String {
    return when (raw) {
        WhiteZiaOptions.SplitTunnelModeInclude -> raw
        WhiteZiaOptions.SplitTunnelModeExclude -> raw
        WhiteZiaOptions.SplitTunnelModeOff -> raw
        else -> WhiteZiaOptions.SplitTunnelModeOff
    }
}

internal fun normalizeConnectionMode(raw: String): String {
    return when (raw) {
        "proxy", "vpn" -> raw
        else -> "proxy"
    }
}

internal fun normalizeTransportMode(raw: String): String {
    return when (raw.trim().lowercase()) {
        WhiteZiaOptions.TransportDns -> WhiteZiaOptions.TransportDns
        WhiteZiaOptions.TransportXray -> WhiteZiaOptions.TransportXray
        else -> WhiteZiaOptions.TransportAuto
    }
}

internal fun normalizeThemeMode(raw: String): String {
    return when (raw) {
        WhiteZiaThemeMode.System,
        WhiteZiaThemeMode.Light,
        WhiteZiaThemeMode.Dark,
        -> raw
        else -> WhiteZiaThemeMode.System
    }
}

internal fun normalizeLanguageCode(raw: String): String {
    return when (raw) {
        WhiteZiaLanguage.Fa -> WhiteZiaLanguage.Fa
        else -> WhiteZiaLanguage.En
    }
}

internal fun normalizePackageNames(raw: List<String>): List<String> {
    return raw
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()
        .toList()
}
