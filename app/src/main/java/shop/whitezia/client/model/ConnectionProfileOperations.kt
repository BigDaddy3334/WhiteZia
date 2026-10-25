package shop.whitezia.client.model

fun WhiteZiaSettings.selectConnectionProfile(profileId: String): WhiteZiaSettings {
    val profiles = normalizedConnectionProfiles()
    val resolverProfiles = normalizedResolverProfiles()
    val selected = profiles.firstOrNull { it.id == profileId } ?: profiles.first()
    val resolverProfile = resolverProfiles.firstOrNull { it.id == selected.resolverProfileId }
    return copy(
        selectedConnectionProfileId = selected.id,
        connectionProfiles = profiles,
        selectedResolverProfileId = resolverProfile?.id.orEmpty(),
        resolverProfiles = resolverProfiles,
        resolverText = resolverProfile?.resolverText ?: resolverText,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.upsertConnectionProfile(profile: ConnectionProfile): WhiteZiaSettings {
    val resolverIds = normalizedResolverProfiles().map { it.id }.toSet()
    val normalizedProfile = profile.copy(
        id = profile.id.ifBlank { "profile-${System.currentTimeMillis()}" },
        name = profile.name.ifBlank { "Connection" },
        serverMode = "custom",
        customServerEncryptionMethod = profile.customServerEncryptionMethod.coerceIn(0, 5),
        resolverProfileId = profile.resolverProfileId.takeIf { it in resolverIds }.orEmpty(),
        connectionMode = when (profile.connectionMode) {
            "proxy", "vpn" -> profile.connectionMode
            else -> "proxy"
        },
    )
    val profiles = normalizedConnectionProfiles()
    val updatedProfiles = if (profiles.any { it.id == normalizedProfile.id }) {
        profiles.map { existing ->
            if (existing.id == normalizedProfile.id) normalizedProfile else existing
        }
    } else {
        profiles + normalizedProfile
    }
    return copy(
        connectionProfiles = updatedProfiles,
        selectedConnectionProfileId = if (selectedConnectionProfileId.isBlank()) {
            normalizedProfile.id
        } else {
            selectedConnectionProfileId
        },
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.moveConnectionProfile(profileId: String, direction: Int): WhiteZiaSettings {
    if (direction == 0) {
        return syncSelectedConnectionProfileFields()
    }
    val profiles = normalizedConnectionProfiles()
    val customProfiles = profiles.filter { it.serverMode == "custom" }
    val fromIndex = customProfiles.indexOfFirst { it.id == profileId }
    if (fromIndex == -1) {
        return copy(connectionProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    return moveConnectionProfileToIndex(profileId, fromIndex + direction)
}

fun WhiteZiaSettings.moveConnectionProfileToIndex(profileId: String, targetIndex: Int): WhiteZiaSettings {
    val profiles = normalizedConnectionProfiles()
    val customProfiles = profiles.filter { it.serverMode == "custom" }
    val fromIndex = customProfiles.indexOfFirst { it.id == profileId }
    if (fromIndex == -1) {
        return copy(connectionProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    val toIndex = targetIndex.coerceIn(0, customProfiles.lastIndex)
    if (fromIndex == toIndex) {
        return copy(connectionProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    return copy(
        connectionProfiles = customProfiles.moved(fromIndex, toIndex),
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.deleteConnectionProfile(profileId: String): WhiteZiaSettings {
    val profiles = normalizedConnectionProfiles()
    if (profiles.size <= 1 || profiles.none { it.id == profileId }) {
        return syncSelectedConnectionProfileFields()
    }
    val remainingProfiles = profiles.filterNot { it.id == profileId }
    val nextSelectedId = if (selectedConnectionProfileId == profileId) {
        remainingProfiles.first().id
    } else {
        selectedConnectionProfileId
    }
    return copy(
        connectionProfiles = remainingProfiles,
        selectedConnectionProfileId = nextSelectedId,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.duplicateConnectionProfileCount(): Int {
    return normalizedConnectionProfiles()
        .mapNotNull { it.duplicateServerKey() }
        .groupingBy { it }
        .eachCount()
        .values
        .sumOf { count -> (count - 1).coerceAtLeast(0) }
}

fun WhiteZiaSettings.deleteDuplicateConnectionProfiles(protectedProfileId: String? = null): WhiteZiaSettings {
    val profiles = normalizedConnectionProfiles()
    if (profiles.size <= 1) {
        return syncSelectedConnectionProfileFields()
    }

    val duplicateGroups = profiles
        .withIndex()
        .mapNotNull { indexedProfile ->
            indexedProfile.value.duplicateServerKey()?.let { key -> key to indexedProfile }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .values
        .filter { it.size > 1 }
    if (duplicateGroups.isEmpty()) {
        return copy(connectionProfiles = profiles).syncSelectedConnectionProfileFields()
    }

    val removedToSurvivorId = mutableMapOf<String, String>()
    duplicateGroups.forEach { group ->
        val survivor = group.firstOrNull { it.value.id == protectedProfileId }
            ?: group.firstOrNull { it.value.id == selectedConnectionProfileId }
            ?: group.minBy { it.index }
        group
            .filterNot { it.value.id == survivor.value.id }
            .forEach { removedToSurvivorId[it.value.id] = survivor.value.id }
    }

    val remainingProfiles = profiles.filterNot { it.id in removedToSurvivorId.keys }
    val nextSelectedId = removedToSurvivorId[selectedConnectionProfileId] ?: selectedConnectionProfileId
    return copy(
        connectionProfiles = remainingProfiles,
        selectedConnectionProfileId = nextSelectedId,
    ).syncSelectedConnectionProfileFields()
}

private data class ConnectionServerKey(
    val domain: String,
    val encryptionKey: String,
)

private fun ConnectionProfile.duplicateServerKey(): ConnectionServerKey? {
    val domain = customServerDomain.trim().trimEnd('.').lowercase()
    val encryptionKey = customServerEncryptionKey.trim()
    return if (domain.isNotBlank() && encryptionKey.isNotBlank()) {
        ConnectionServerKey(domain = domain, encryptionKey = encryptionKey)
    } else {
        null
    }
}
