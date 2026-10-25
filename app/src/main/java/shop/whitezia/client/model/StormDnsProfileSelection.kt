package shop.whitezia.client.model

internal fun selectServerProfile(settings: WhiteZiaSettings): StormDnsServerProfile? {
    return serverProfileFromConnectionProfile(settings.selectedConnectionProfile())
}

internal fun serverProfileFromConnectionProfile(connectionProfile: ConnectionProfile): StormDnsServerProfile? {
    val domain = connectionProfile.customServerDomain
        .trim()
        .trimEnd('.')
    val encryptionKey = connectionProfile.customServerEncryptionKey.trim()
    if (domain.isBlank() || encryptionKey.isBlank()) {
        return null
    }
    return StormDnsServerProfile(
        id = connectionProfile.id.ifBlank { domain },
        label = connectionProfile.name.ifBlank { domain },
        domain = domain,
        encryptionKey = encryptionKey,
        encryptionMethod = connectionProfile.customServerEncryptionMethod.coerceIn(0, 5),
    )
}

