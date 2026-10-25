package shop.whitezia.client.diagnostics

import shop.whitezia.client.model.WhiteZiaSettings

/** Applies only to operational diagnostics. Explicit profile exports retain their credentials. */
internal object DiagnosticPrivacy {
    private const val Hidden = "[redacted]"
    private val ansi = Regex("\\u001B\\[[;\\d]*m")
    private val profileLink = Regex("(?i)\\b(?:vless|stormdns|stormbundle)://[^\\s<>\\\"']+")
    private val uuid = Regex("(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])")
    private val jwt = Regex("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])")
    private val authorization = Regex("(?i)\\b(Bearer|Basic)\\s+[^\\s,;\\\"']+")
    private val labelledSecret = Regex(
        """(?i)(["']?(?:access[_ -]?token|refresh[_ -]?token|device[_ -]?token|token|password|passwd|secret|encryption[_ -]?key|customServerEncryptionKey|socksPassword|private[_ -]?key|preshared[_ -]?key|api[_ -]?key)["']?\s*[:=]\s*)(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s&,;]+)""",
    )

    fun redact(text: String, knownSecrets: Iterable<String> = emptyList()): String {
        var safe = ansi.replace(text, "")
        // Known values catch unlabelled native errors. Longer values must be removed first.
        knownSecrets.flatMap { listOf(it, it.trim()) }.filter(String::isNotEmpty)
            .distinct().sortedByDescending(String::length).forEach { secret ->
                safe = if (secret.length >= 4) safe.replace(secret, Hidden)
                else Regex("(?<![A-Za-z0-9_])${Regex.escape(secret)}(?![A-Za-z0-9_])").replace(safe, Hidden)
            }
        safe = profileLink.replace(safe, Hidden)
        safe = uuid.replace(safe, Hidden)
        safe = authorization.replace(safe) { "${it.groupValues[1]} $Hidden" }
        safe = jwt.replace(safe, Hidden)
        return labelledSecret.replace(safe) { "${it.groupValues[1]}$Hidden" }
    }
}

internal fun WhiteZiaSettings.diagnosticSecrets(): List<String> = buildList {
    add(subscriptionLink)
    add(xrayUri)
    add(amneziaWgConfig)
    amneziaWgCandidates.forEach { add(it.config) }
    add(customServerEncryptionKey)
    add(socksPassword)
    connectionProfiles.forEach { add(it.customServerEncryptionKey) }
    advancedProfiles.forEach { add(it.socksPassword) }
    xrayCandidates.forEach { add(it.uri); add(it.directUri) }
    stormDnsCandidates.forEach { add(it.encryptionKey) }
}

internal fun WhiteZiaSettings.redactDiagnostics(text: String): String =
    DiagnosticPrivacy.redact(text, diagnosticSecrets())
