package shop.whitezia.client.model

import shop.whitezia.client.xray.XrayClientConfigParser

internal class SubscriptionProfileManager {
    fun importProfile(
        settings: WhiteZiaSettings,
        rawLink: String,
        rememberSubscriptionLink: Boolean,
    ): WhiteZiaSettings {
        val trimmedLink = rawLink.trim()
        require(isSupportedLink(trimmedLink)) { "Ссылка не содержит профиль WhiteZia" }
        val imported = settings
            .importStormDnsProfileLink(trimmedLink)
            .let { parsed ->
                if (rememberSubscriptionLink) parsed.copy(subscriptionLink = trimmedLink) else parsed
            }
            .let { parsed ->
                if (settings.manualMode) {
                    parsed.copy(
                        forceDnsTunnel = settings.forceDnsTunnel,
                        transportMode = settings.transportMode,
                    )
                } else {
                    parsed
                }
            }
            .syncSelectedConnectionProfileFields()
        validateTransports(imported)
        return imported
    }

    fun applySubscriptionIfNeeded(
        settings: WhiteZiaSettings,
        previousSettings: WhiteZiaSettings,
        force: Boolean = false,
    ): WhiteZiaSettings {
        val trimmedLink = settings.subscriptionLink.trim()
        val linkChanged = trimmedLink != previousSettings.subscriptionLink.trim()
        val alreadyApplied = settings.customServerDomain.isNotBlank() ||
            settings.amneziaWgConfig.isNotBlank() ||
            settings.xrayUri.isNotBlank()
        if (
            trimmedLink.isBlank() ||
            !isSupportedLink(trimmedLink) ||
            (!force && !linkChanged && alreadyApplied)
        ) {
            return settings.copy(subscriptionLink = trimmedLink)
        }
        return importProfile(
            settings = settings,
            rawLink = trimmedLink,
            rememberSubscriptionLink = true,
        )
    }

    fun isSupportedLink(link: String): Boolean {
        return link.startsWith("stormbundle://", ignoreCase = true) ||
            link.startsWith("stormdns://", ignoreCase = true)
    }

    private fun validateTransports(settings: WhiteZiaSettings) {
        buildList {
            settings.xrayUri.takeIf(String::isNotBlank)?.let(::add)
            addAll(settings.xrayCandidates.map(XrayCandidate::uri))
        }
            .distinct()
            .forEach(XrayClientConfigParser::parseVlessUri)

        settings.stormDnsCandidates.forEach { candidate ->
            require(candidate.encryptionMethod in 0..5) {
                "StormDNS encryption method must be between 0 and 5"
            }
        }
    }
}
