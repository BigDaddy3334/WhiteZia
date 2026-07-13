package shop.whitezia.client.model

import java.util.Base64
import org.json.JSONObject

private const val StormDnsProfileScheme = "stormdns"
private const val StormBundleProfileScheme = "stormbundle"
private const val StormDnsProfileSchema = "whitezia.profile"
private const val StormBundleProfileSchema = "whitezia.bundle"
private const val LegacyWhiteDnsProfileSchema = "whitedns.profile"
private const val LegacyWhiteDnsBundleSchema = "whitedns.bundle"
private const val StormDnsProfileVersion = 1
private const val StormBundleProfileVersion = 2

fun WhiteZiaSettings.exportStormDnsProfileLink(profile: ConnectionProfile = selectedConnectionProfile()): String {
    val normalizedProfile = profile.copy(
        name = profile.name.ifBlank { profile.customServerDomain.ifBlank { "WhiteZia Profile" } },
        serverMode = "custom",
        customServerDomain = profile.customServerDomain.trim().trimEnd('.'),
        customServerEncryptionKey = profile.customServerEncryptionKey.trim(),
        customServerEncryptionMethod = profile.customServerEncryptionMethod.coerceIn(0, 5),
    )
    if (normalizedProfile.customServerDomain.isBlank() || normalizedProfile.customServerEncryptionKey.isBlank()) {
        throw IllegalArgumentException("Custom server domain and encryption key are required to export")
    }

    val profileJson = JSONObject()
        .put("name", normalizedProfile.name)
        .put(
            "server",
            JSONObject()
                .put("domain", normalizedProfile.customServerDomain)
                .put("encryption_key", normalizedProfile.customServerEncryptionKey)
                .put("encryption_method", normalizedProfile.customServerEncryptionMethod),
        )

    val root = JSONObject()
        .put("schema", StormDnsProfileSchema)
        .put("version", StormDnsProfileVersion)
        .put("profile", profileJson)

    return "$StormDnsProfileScheme://${encodeProfilePayload(root)}"
}

fun WhiteZiaSettings.exportAllStormDnsProfileLinks(): String {
    val links = normalizedConnectionProfiles()
        .filter { profile ->
            profile.serverMode == "custom" &&
                profile.customServerDomain.isNotBlank() &&
                profile.customServerEncryptionKey.isNotBlank()
        }
        .map { profile -> exportStormDnsProfileLink(profile) }
    if (links.isEmpty()) {
        throw IllegalArgumentException("No custom profiles are available to export")
    }
    return links.joinToString(separator = "\n")
}

fun WhiteZiaSettings.importStormDnsProfileLinks(
    rawLinks: String,
    nowMillis: Long = System.currentTimeMillis(),
): WhiteZiaSettings {
    val links = rawLinks
        .lineSequence()
        .mapIndexedNotNull { index, line ->
            line.trim().takeIf(String::isNotEmpty)?.let { trimmedLine ->
                (index + 1) to trimmedLine
            }
        }
        .toList()
    if (links.isEmpty()) {
        throw IllegalArgumentException("Enter at least one stormdns:// profile link")
    }

    var nextSettings = this
    links.forEachIndexed { index, (lineNumber, link) ->
        nextSettings = runCatching {
            nextSettings.importStormDnsProfileLink(
                rawLink = link,
                nowMillis = nowMillis + index,
            )
        }.getOrElse { error ->
            throw IllegalArgumentException("Line $lineNumber: ${error.message ?: "Unable to import profile"}", error)
        }
    }
    return nextSettings
}

fun WhiteZiaSettings.importStormDnsProfileLink(
    rawLink: String,
    nowMillis: Long = System.currentTimeMillis(),
): WhiteZiaSettings {
    val root = decodeProfilePayload(rawLink)
    val schema = root.requiredString("schema")
    if (!isSupportedProfileSchema(schema)) {
        throw IllegalArgumentException("Unsupported profile schema")
    }
    val version = root.optionalInt("version") ?: StormDnsProfileVersion
    val supportedVersion = if (isBundleProfileSchema(schema)) {
        version in StormDnsProfileVersion..StormBundleProfileVersion
    } else {
        version == StormDnsProfileVersion
    }
    if (!supportedVersion) {
        throw IllegalArgumentException("Unsupported profile version")
    }

    val profileJson = root.optJSONObject("profile")
        ?: throw IllegalArgumentException("Missing profile")
    val serverJson = profileJson.optJSONObject("server")
        ?: profileJson.optJSONObject("stormdns")
    val amneziaWgConfig = profileJson.optJSONObject("amneziawg")
        ?.optionalString("config")
        ?.trim()
        .orEmpty()
    val xrayJson = profileJson.optJSONObject("xray")
    val xrayUri = xrayJson
        ?.optionalString("uri")
        ?.trim()
        .orEmpty()
    val xrayDailyLimitBytes = xrayJson
        ?.optionalLong("daily_limit_bytes")
        ?.coerceAtLeast(0L)
        ?: 0L
    if (serverJson == null) {
        if (isBundleProfileSchema(schema) && (amneziaWgConfig.isNotBlank() || xrayUri.isNotBlank())) {
            return copy(
                transportMode = WhiteZiaOptions.TransportAuto,
                amneziaWgConfig = amneziaWgConfig,
                xrayUri = xrayUri,
                xrayDailyLimitBytes = xrayDailyLimitBytes,
            ).syncSelectedConnectionProfileFields()
        }
        throw IllegalArgumentException("Missing server")
    }
    val domain = serverJson.requiredString("domain").trim().trimEnd('.')
    val encryptionKey = serverJson.requiredString("encryption_key").trim()
    if (domain.isBlank()) {
        throw IllegalArgumentException("Server domain is required")
    }
    if (encryptionKey.isBlank()) {
        throw IllegalArgumentException("Server encryption key is required")
    }

    val profileName = profileJson.requiredString("name").trim()
    val normalizedProfiles = normalizedConnectionProfiles()
    val existingImportedProfile = normalizedProfiles.firstOrNull { profile ->
        profile.id.startsWith("profile-imported-") &&
            profile.name == profileName &&
            profile.customServerDomain.equals(domain, ignoreCase = true) &&
            profile.customServerEncryptionKey == encryptionKey
    }
    val profileId = existingImportedProfile?.id
        ?: uniqueImportedProfileId(normalizedProfiles, nowMillis)
    val encryptionMethod = serverJson.requiredInt("encryption_method")
    if (encryptionMethod !in 0..5) {
        throw IllegalArgumentException("Server encryption method must be between 0 and 5")
    }
    val importedProfile = ConnectionProfile(
        id = profileId,
        name = profileName,
        serverMode = "custom",
        customServerDomain = domain,
        customServerEncryptionKey = encryptionKey,
        customServerEncryptionMethod = encryptionMethod,
        resolverProfileId = "",
        connectionMode = connectionMode,
    )

    return copy(
        selectedConnectionProfileId = profileId,
        connectionProfiles = if (existingImportedProfile == null) {
            normalizedProfiles + importedProfile
        } else {
            normalizedProfiles.map { profile ->
                if (profile.id == existingImportedProfile.id) importedProfile else profile
            }
        },
        serverMode = "custom",
        customServerDomain = domain,
        customServerEncryptionKey = encryptionKey,
        customServerEncryptionMethod = importedProfile.customServerEncryptionMethod,
        transportMode = if (isBundleProfileSchema(schema) && (amneziaWgConfig.isNotBlank() || xrayUri.isNotBlank())) {
            WhiteZiaOptions.TransportAuto
        } else {
            WhiteZiaOptions.TransportDns
        },
        amneziaWgConfig = amneziaWgConfig,
        xrayUri = xrayUri,
        xrayDailyLimitBytes = xrayDailyLimitBytes,
    ).syncSelectedConnectionProfileFields()
}

private fun isSupportedProfileSchema(schema: String): Boolean {
    return schema == StormDnsProfileSchema ||
        schema == StormBundleProfileSchema ||
        schema == LegacyWhiteDnsProfileSchema ||
        schema == LegacyWhiteDnsBundleSchema
}

private fun isBundleProfileSchema(schema: String): Boolean {
    return schema == StormBundleProfileSchema || schema == LegacyWhiteDnsBundleSchema
}

private fun encodeProfilePayload(root: JSONObject): String {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(root.toString().toByteArray(Charsets.UTF_8))
}

private fun decodeProfilePayload(rawLink: String): JSONObject {
    val link = rawLink.trim()
    val prefix = when {
        link.startsWith("$StormDnsProfileScheme://") -> "$StormDnsProfileScheme://"
        link.startsWith("$StormBundleProfileScheme://") -> "$StormBundleProfileScheme://"
        else -> throw IllegalArgumentException("Profile link must start with stormdns:// or stormbundle://")
    }
    val payload = link.removePrefix(prefix).trim()
    if (payload.isBlank()) {
        throw IllegalArgumentException("Profile link is empty")
    }
    val decoded = decodeBase64Payload(payload.substringBefore('#').substringBefore('?'))
    return JSONObject(decoded)
}

private fun decodeBase64Payload(payload: String): String {
    val normalizedPayload = payload.filterNot(Char::isWhitespace)
    val paddedPayload = normalizedPayload.padEnd(
        normalizedPayload.length + ((4 - normalizedPayload.length % 4) % 4),
        '=',
    )
    val bytes = runCatching {
        Base64.getUrlDecoder().decode(paddedPayload)
    }.recoverCatching {
        Base64.getDecoder().decode(paddedPayload)
    }.getOrElse {
        throw IllegalArgumentException("Profile link payload is not valid base64")
    }
    return bytes.toString(Charsets.UTF_8)
}

private fun uniqueImportedProfileId(
    profiles: List<ConnectionProfile>,
    nowMillis: Long,
): String {
    val existingIds = profiles.map { it.id }.toSet()
    val baseId = "profile-imported-$nowMillis"
    if (baseId !in existingIds) {
        return baseId
    }
    var suffix = 2
    while ("$baseId-$suffix" in existingIds) {
        suffix += 1
    }
    return "$baseId-$suffix"
}

private fun JSONObject.requiredString(name: String): String {
    return optionalString(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Missing $name")
}

private fun JSONObject.requiredInt(name: String): Int {
    return optionalInt(name) ?: throw IllegalArgumentException("Missing $name")
}

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return opt(name)?.toString()
}

private fun JSONObject.optionalInt(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}


private fun JSONObject.optionalLong(name: String): Long? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
}
