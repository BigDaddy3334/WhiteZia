package shop.whitezia.client.model

fun WhiteZiaSettings.upsertResolverProfile(profile: ResolverProfile): WhiteZiaSettings {
    if (profile.id == ResolverProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val normalizedProfile = profile.copy(
        id = profile.id.ifBlank { ResolverProfile.newId() },
        name = profile.name.ifBlank { "Resolvers" },
        resolverText = normalizeResolverText(profile.resolverText),
    )
    if (normalizedProfile.resolverText.isBlank()) {
        return syncSelectedConnectionProfileFields()
    }
    val profiles = normalizedResolverProfiles()
    val updatedProfiles = if (profiles.any { it.id == normalizedProfile.id }) {
        profiles.map { existing ->
            if (existing.id == normalizedProfile.id) normalizedProfile else existing
        }
    } else {
        profiles + normalizedProfile
    }
    return copy(
        resolverProfiles = updatedProfiles,
        selectedResolverProfileId = normalizedProfile.id,
        resolverText = normalizedProfile.resolverText,
    ).applyResolverProfileToSelectedConnection(normalizedProfile.id)
}

fun WhiteZiaSettings.exportAllResolverProfilesText(): String {
    val resolverEntries = normalizedResolverProfiles()
        .flatMap { profile -> validateResolverText(profile.resolverText).normalizedResolvers }
        .distinct()
    if (resolverEntries.isEmpty()) {
        throw IllegalArgumentException("No resolver profiles are available to export")
    }
    return resolverEntries.joinToString(separator = "\n")
}

fun WhiteZiaSettings.saveResolverProfileAs(
    name: String,
    resolverText: String,
): WhiteZiaSettings {
    val normalizedResolverText = normalizeResolverText(resolverText)
    if (normalizedResolverText.isBlank()) {
        return syncSelectedConnectionProfileFields()
    }
    val profile = ResolverProfile(
        id = ResolverProfile.newId(),
        name = name.trim().ifBlank { "Resolvers" },
        resolverText = normalizedResolverText,
    )
    return copy(
        resolverProfiles = normalizedResolverProfiles() + profile,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.moveResolverProfile(profileId: String, direction: Int): WhiteZiaSettings {
    if (direction == 0) {
        return syncSelectedConnectionProfileFields()
    }
    if (profileId == ResolverProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val profiles = normalizedResolverProfiles()
    val customProfiles = profiles.filter { it.id != ResolverProfile.DefaultId }
    val fromIndex = customProfiles.indexOfFirst { it.id == profileId }
    if (fromIndex == -1) {
        return copy(resolverProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    return moveResolverProfileToIndex(profileId, fromIndex + direction)
}

fun WhiteZiaSettings.moveResolverProfileToIndex(profileId: String, targetIndex: Int): WhiteZiaSettings {
    if (profileId == ResolverProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val profiles = normalizedResolverProfiles()
    val defaultProfile = profiles.firstOrNull { it.id == ResolverProfile.DefaultId }
    val customProfiles = profiles.filter { it.id != ResolverProfile.DefaultId }
    val fromIndex = customProfiles.indexOfFirst { it.id == profileId }
    if (fromIndex == -1) {
        return copy(resolverProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    val toIndex = targetIndex.coerceIn(0, customProfiles.lastIndex)
    if (fromIndex == toIndex) {
        return copy(resolverProfiles = profiles).syncSelectedConnectionProfileFields()
    }
    return copy(
        resolverProfiles = listOfNotNull(defaultProfile) + customProfiles.moved(fromIndex, toIndex),
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.applyResolverProfileToSelectedConnection(profileId: String): WhiteZiaSettings {
    val resolverProfiles = normalizedResolverProfiles()
    val resolverProfile = resolverProfiles.firstOrNull { it.id == profileId }
        ?: return copy(selectedResolverProfileId = "").syncSelectedConnectionProfileFields()
    val connectionProfiles = normalizedConnectionProfiles()
    val selectedConnection = connectionProfiles.firstOrNull { it.id == selectedConnectionProfileId }
        ?: connectionProfiles.first()
    val updatedConnectionProfiles = connectionProfiles.map { profile ->
        if (profile.id == selectedConnection.id) {
            profile.copy(resolverProfileId = resolverProfile.id)
        } else {
            profile
        }
    }
    return copy(
        connectionProfiles = updatedConnectionProfiles,
        resolverProfiles = resolverProfiles,
        selectedResolverProfileId = resolverProfile.id,
        resolverText = resolverProfile.resolverText,
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.clearSelectedResolverProfile(): WhiteZiaSettings {
    val connectionProfiles = normalizedConnectionProfiles()
    val selectedConnection = connectionProfiles.firstOrNull { it.id == selectedConnectionProfileId }
        ?: connectionProfiles.first()
    val updatedConnectionProfiles = connectionProfiles.map { profile ->
        if (profile.id == selectedConnection.id) {
            profile.copy(resolverProfileId = "")
        } else {
            profile
        }
    }
    return copy(
        connectionProfiles = updatedConnectionProfiles,
        selectedConnectionProfileId = selectedConnection.id,
        selectedResolverProfileId = "",
    ).syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.updateManualResolverText(resolverText: String): WhiteZiaSettings {
    return clearSelectedResolverProfile()
        .copy(resolverText = resolverText)
        .syncSelectedConnectionProfileFields()
}

fun WhiteZiaSettings.deleteResolverProfile(profileId: String): WhiteZiaSettings {
    if (profileId == ResolverProfile.DefaultId) {
        return syncSelectedConnectionProfileFields()
    }
    val profiles = normalizedResolverProfiles()
    if (profiles.none { it.id == profileId }) {
        return syncSelectedConnectionProfileFields()
    }
    val remainingProfiles = profiles.filterNot { it.id == profileId }
    val updatedConnectionProfiles = normalizedConnectionProfiles().map { profile ->
        if (profile.resolverProfileId == profileId) {
            profile.copy(resolverProfileId = "")
        } else {
            profile
        }
    }
    return copy(
        resolverProfiles = remainingProfiles,
        connectionProfiles = updatedConnectionProfiles,
        selectedResolverProfileId = if (selectedResolverProfileId == profileId) "" else selectedResolverProfileId,
    ).syncSelectedConnectionProfileFields()
}
