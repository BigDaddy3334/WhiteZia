package shop.whitedns.client.model

import java.util.Locale

fun WhiteDnsSettings.importAdvancedSettingsProfileFromToml(
    name: String,
    toml: String,
): WhiteDnsSettings {
    val profile = parseAdvancedSettingsProfileFromToml(name, toml)
    val profiles = normalizedAdvancedProfiles()
        .filter { it.id != AdvancedSettingsProfile.DefaultId && it.id != profile.id } + profile
    return copy(
        selectedAdvancedProfileId = profile.id,
        advancedProfiles = profiles,
    ).applyAdvancedProfile(profile)
        .copy(autoTuneEnabled = profile.autoTuneEnabled)
}

private fun parseAdvancedSettingsProfileFromToml(
    name: String,
    toml: String,
): AdvancedSettingsProfile {
    val profileName = name.trim()
    if (profileName.isBlank()) {
        throw IllegalArgumentException("Enter a settings profile name")
    }
    val assignments = parseTomlAssignments(toml)
    var settings = WhiteDnsSettings()
    var importedSettingCount = 0

    fun applyIfPresent(key: String, update: (TomlValue) -> WhiteDnsSettings) {
        assignments[key]?.let { value ->
            settings = update(value)
            importedSettingCount += 1
        }
    }

    applyIfPresent("LISTEN_IP") { value ->
        val listenIp = value.stringValue("LISTEN_IP").trim()
        if (listenIp.isBlank()) {
            value.fail("LISTEN_IP must not be blank")
        }
        settings.copy(listenIp = listenIp)
    }
    applyIfPresent("LISTEN_PORT") { value ->
        settings.copy(listenPort = value.intValue("LISTEN_PORT", 1, 65535).toString())
    }
    applyIfPresent("HTTP_PROXY_ENABLED") { value ->
        settings.copy(httpProxyEnabled = value.booleanValue("HTTP_PROXY_ENABLED"))
    }
    applyIfPresent("HTTP_PROXY_PORT") { value ->
        settings.copy(httpProxyPort = value.intValue("HTTP_PROXY_PORT", 1, 65535).toString())
    }
    applyIfPresent("SOCKS5_AUTH") { value ->
        settings.copy(socks5Authentication = value.booleanValue("SOCKS5_AUTH"))
    }
    applyIfPresent("SOCKS5_USER") { value ->
        val socksUsername = value.stringValue("SOCKS5_USER")
        if (socksUsername.length > 255) {
            value.fail("SOCKS5_USER must be 255 characters or fewer")
        }
        settings.copy(socksUsername = socksUsername)
    }
    applyIfPresent("SOCKS5_PASS") { value ->
        val socksPassword = value.stringValue("SOCKS5_PASS")
        if (socksPassword.length > 255) {
            value.fail("SOCKS5_PASS must be 255 characters or fewer")
        }
        settings.copy(socksPassword = socksPassword)
    }
    applyIfPresent("LOCAL_DNS_ENABLED") { value ->
        settings.copy(localDnsEnabled = value.booleanValue("LOCAL_DNS_ENABLED"))
    }
    applyIfPresent("LOCAL_DNS_PORT") { value ->
        settings.copy(localDnsPort = value.intValue("LOCAL_DNS_PORT", 1, 65535).toString())
    }
    applyIfPresent("RESOLVER_BALANCING_STRATEGY") { value ->
        settings.copy(balancingStrategy = value.enumIntValue("RESOLVER_BALANCING_STRATEGY", setOf(1, 2, 3, 4)))
    }
    applyIfPresent("UPLOAD_PACKET_DUPLICATION_COUNT") { value ->
        settings.copy(uploadDuplication = value.intValue("UPLOAD_PACKET_DUPLICATION_COUNT", 1, 30).toString())
    }
    applyIfPresent("DOWNLOAD_PACKET_DUPLICATION_COUNT") { value ->
        settings.copy(downloadDuplication = value.intValue("DOWNLOAD_PACKET_DUPLICATION_COUNT", 1, 30).toString())
    }
    applyIfPresent("UPLOAD_COMPRESSION_TYPE") { value ->
        settings.copy(uploadCompression = value.intValue("UPLOAD_COMPRESSION_TYPE", 0, 3))
    }
    applyIfPresent("DOWNLOAD_COMPRESSION_TYPE") { value ->
        settings.copy(downloadCompression = value.intValue("DOWNLOAD_COMPRESSION_TYPE", 0, 3))
    }
    applyIfPresent("BASE_ENCODE_DATA") { value ->
        settings.copy(baseEncodeData = value.booleanValue("BASE_ENCODE_DATA"))
    }
    applyIfPresent("MIN_UPLOAD_MTU") { value ->
        settings.copy(minUploadMtu = value.intValue("MIN_UPLOAD_MTU", 1, 65535).toString())
    }
    applyIfPresent("MIN_DOWNLOAD_MTU") { value ->
        settings.copy(minDownloadMtu = value.intValue("MIN_DOWNLOAD_MTU", 1, 65535).toString())
    }
    applyIfPresent("MAX_UPLOAD_MTU") { value ->
        settings.copy(maxUploadMtu = value.intValue("MAX_UPLOAD_MTU", 1, 65535).toString())
    }
    applyIfPresent("MAX_DOWNLOAD_MTU") { value ->
        settings.copy(maxDownloadMtu = value.intValue("MAX_DOWNLOAD_MTU", 1, 65535).toString())
    }
    applyIfPresent("MTU_TEST_RETRIES_RESOLVERS") { value ->
        settings.copy(mtuTestRetriesResolvers = value.intValue("MTU_TEST_RETRIES_RESOLVERS", 1, 100).toString())
    }
    applyIfPresent("MTU_TEST_TIMEOUT_RESOLVERS") { value ->
        settings.copy(mtuTestTimeoutResolvers = value.positiveDoubleValue("MTU_TEST_TIMEOUT_RESOLVERS").toNormalizedString())
    }
    applyIfPresent("MTU_TEST_PARALLELISM_RESOLVERS") { value ->
        settings.copy(mtuTestParallelismResolvers = value.intValue("MTU_TEST_PARALLELISM_RESOLVERS", 1, 1024).toString())
    }
    applyIfPresent("MTU_TEST_RETRIES_LOGS") { value ->
        settings.copy(mtuTestRetriesLogs = value.intValue("MTU_TEST_RETRIES_LOGS", 1, 100).toString())
    }
    applyIfPresent("MTU_TEST_TIMEOUT_LOGS") { value ->
        settings.copy(mtuTestTimeoutLogs = value.positiveDoubleValue("MTU_TEST_TIMEOUT_LOGS").toNormalizedString())
    }
    applyIfPresent("MTU_TEST_PARALLELISM_LOGS") { value ->
        settings.copy(mtuTestParallelismLogs = value.intValue("MTU_TEST_PARALLELISM_LOGS", 1, 1024).toString())
    }
    applyIfPresent("RX_TX_WORKERS") { value ->
        settings.copy(rxTxWorkers = value.intValue("RX_TX_WORKERS", 1, 64).toString())
    }
    applyIfPresent("TUNNEL_PROCESS_WORKERS") { value ->
        settings.copy(tunnelProcessWorkers = value.intValue("TUNNEL_PROCESS_WORKERS", 1, 64).toString())
    }
    applyIfPresent("TUNNEL_PACKET_TIMEOUT_SECONDS") { value ->
        settings.copy(
            tunnelPacketTimeoutSeconds = value.doubleValue("TUNNEL_PACKET_TIMEOUT_SECONDS", 0.5, 120.0).toNormalizedString(),
        )
    }
    applyIfPresent("DISPATCHER_IDLE_POLL_INTERVAL_SECONDS") { value ->
        settings.copy(
            dispatcherIdlePollIntervalSeconds = value.doubleValue(
                "DISPATCHER_IDLE_POLL_INTERVAL_SECONDS",
                0.001,
                1.0,
            ).toNormalizedString(),
        )
    }
    applyIfPresent("TX_CHANNEL_SIZE") { value ->
        settings.copy(txChannelSize = value.intValue("TX_CHANNEL_SIZE", 64, 65536).toString())
    }
    applyIfPresent("RX_CHANNEL_SIZE") { value ->
        settings.copy(rxChannelSize = value.intValue("RX_CHANNEL_SIZE", 64, 65536).toString())
    }
    applyIfPresent("RESOLVER_UDP_CONNECTION_POOL_SIZE") { value ->
        settings.copy(resolverUdpConnectionPoolSize = value.intValue("RESOLVER_UDP_CONNECTION_POOL_SIZE", 1, 1024).toString())
    }
    applyIfPresent("STREAM_QUEUE_INITIAL_CAPACITY") { value ->
        settings.copy(streamQueueInitialCapacity = value.intValue("STREAM_QUEUE_INITIAL_CAPACITY", 8, 65536).toString())
    }
    applyIfPresent("ORPHAN_QUEUE_INITIAL_CAPACITY") { value ->
        settings.copy(orphanQueueInitialCapacity = value.intValue("ORPHAN_QUEUE_INITIAL_CAPACITY", 4, 4096).toString())
    }
    applyIfPresent("DNS_RESPONSE_FRAGMENT_STORE_CAPACITY") { value ->
        settings.copy(
            dnsResponseFragmentStoreCapacity = value.intValue("DNS_RESPONSE_FRAGMENT_STORE_CAPACITY", 16, 16384).toString(),
        )
    }
    applyIfPresent("MAX_ACTIVE_STREAMS") { value ->
        settings.copy(maxActiveStreams = value.intValue("MAX_ACTIVE_STREAMS", 1, 65535).toString())
    }
    applyIfPresent("LOCAL_HANDSHAKE_TIMEOUT_SECONDS") { value ->
        settings.copy(
            localHandshakeTimeoutSeconds = value.doubleValue("LOCAL_HANDSHAKE_TIMEOUT_SECONDS", 0.5, 60.0)
                .toNormalizedString(),
        )
    }
    applyIfPresent("SOCKS_UDP_ASSOCIATE_READ_TIMEOUT_SECONDS") { value ->
        settings.copy(
            socksUdpAssociateReadTimeoutSeconds = value.doubleValue(
                "SOCKS_UDP_ASSOCIATE_READ_TIMEOUT_SECONDS",
                1.0,
                3600.0,
            ).toNormalizedString(),
        )
    }
    applyIfPresent("CLIENT_TERMINAL_STREAM_RETENTION_SECONDS") { value ->
        settings.copy(
            clientTerminalStreamRetentionSeconds = value.doubleValue(
                "CLIENT_TERMINAL_STREAM_RETENTION_SECONDS",
                1.0,
                3600.0,
            ).toNormalizedString(),
        )
    }
    applyIfPresent("CLIENT_CANCELLED_SETUP_RETENTION_SECONDS") { value ->
        settings.copy(
            clientCancelledSetupRetentionSeconds = value.doubleValue(
                "CLIENT_CANCELLED_SETUP_RETENTION_SECONDS",
                1.0,
                3600.0,
            ).toNormalizedString(),
        )
    }
    applyIfPresent("SESSION_INIT_RETRY_BASE_SECONDS") { value ->
        settings.copy(
            sessionInitRetryBaseSeconds = value.doubleValue("SESSION_INIT_RETRY_BASE_SECONDS", 0.1, 60.0)
                .toNormalizedString(),
        )
    }
    applyIfPresent("SESSION_INIT_RETRY_STEP_SECONDS") { value ->
        settings.copy(
            sessionInitRetryStepSeconds = value.doubleValue("SESSION_INIT_RETRY_STEP_SECONDS", 0.0, 60.0)
                .toNormalizedString(),
        )
    }
    applyIfPresent("SESSION_INIT_RETRY_LINEAR_AFTER") { value ->
        settings.copy(sessionInitRetryLinearAfter = value.intValue("SESSION_INIT_RETRY_LINEAR_AFTER", 0, 1000).toString())
    }
    applyIfPresent("SESSION_INIT_RETRY_MAX_SECONDS") { value ->
        settings.copy(
            sessionInitRetryMaxSeconds = value.doubleValue("SESSION_INIT_RETRY_MAX_SECONDS", 0.1, 3600.0)
                .toNormalizedString(),
        )
    }
    applyIfPresent("SESSION_INIT_BUSY_RETRY_INTERVAL_SECONDS") { value ->
        settings.copy(
            sessionInitBusyRetryIntervalSeconds = value.doubleValue(
                "SESSION_INIT_BUSY_RETRY_INTERVAL_SECONDS",
                1.0,
                3600.0,
            ).toNormalizedString(),
        )
    }
    applyIfPresent("STARTUP_MODE") { value ->
        val startupMode = value.stringValue("STARTUP_MODE").lowercase(Locale.US)
        if (startupMode !in setOf("ask", "resolvers", "logs")) {
            value.fail("STARTUP_MODE must be one of ask, resolvers, logs")
        }
        settings.copy(startupMode = startupMode)
    }
    applyIfPresent("PING_WATCHDOG_TIMEOUT_SECONDS") { value ->
        settings.copy(pingWatchdogSeconds = value.intValue("PING_WATCHDOG_TIMEOUT_SECONDS", 0, 3600).toString())
    }
    applyIfPresent("TRAFFIC_WARMUP_ENABLED") { value ->
        settings.copy(trafficWarmupEnabled = value.booleanValue("TRAFFIC_WARMUP_ENABLED"))
    }
    applyIfPresent("TRAFFIC_WARMUP_PROBE_COUNT") { value ->
        settings.copy(trafficWarmupProbeCount = value.intValue("TRAFFIC_WARMUP_PROBE_COUNT", 0, 10).toString())
    }
    applyIfPresent("TRAFFIC_KEEPALIVE_INTERVAL_SECONDS") { value ->
        settings.copy(trafficKeepaliveIntervalSeconds = value.intValue("TRAFFIC_KEEPALIVE_INTERVAL_SECONDS", 2, 300).toString())
    }
    applyIfPresent("AUTO_TUNE_ENABLED") { value ->
        settings.copy(autoTuneEnabled = value.booleanValue("AUTO_TUNE_ENABLED"))
    }
    applyIfPresent("LOG_LEVEL") { value ->
        settings.copy(logLevel = value.enumStringValue("LOG_LEVEL", setOf("DEBUG", "INFO", "WARN", "ERROR")))
    }

    if (importedSettingCount == 0) {
        throw IllegalArgumentException("No supported advanced settings found in TOML")
    }
    validateAdvancedSettingsRelations(settings)

    return AdvancedSettingsProfile.fromSettings(
        settings = settings,
        id = AdvancedSettingsProfile.newId(),
        name = profileName,
    )
}

private fun parseTomlAssignments(toml: String): Map<String, TomlValue> {
    if (toml.isBlank()) {
        throw IllegalArgumentException("Paste TOML or import a TOML file")
    }
    val assignments = linkedMapOf<String, TomlValue>()
    toml.replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .forEachIndexed { index, line ->
            val lineNumber = index + 1
            val content = stripTomlComment(line).trim()
            if (content.isEmpty()) {
                return@forEachIndexed
            }
            if (content.startsWith("[") && content.endsWith("]")) {
                throw IllegalArgumentException("Unsupported TOML section on line $lineNumber")
            }
            val equalsIndex = content.indexOfUnquotedEquals()
            if (equalsIndex <= 0) {
                throw IllegalArgumentException("Invalid TOML setting on line $lineNumber")
            }
            val key = content.substring(0, equalsIndex).trim().uppercase(Locale.US)
            if (!TomlKeyRegex.matches(key)) {
                throw IllegalArgumentException("Invalid TOML key on line $lineNumber")
            }
            if (assignments.containsKey(key)) {
                throw IllegalArgumentException("Duplicate TOML setting $key on line $lineNumber")
            }
            val value = content.substring(equalsIndex + 1).trim()
            if (value.isEmpty()) {
                throw IllegalArgumentException("Missing value for $key on line $lineNumber")
            }
            assignments[key] = TomlValue(raw = value, lineNumber = lineNumber)
        }
    if (assignments.isEmpty()) {
        throw IllegalArgumentException("No TOML settings found")
    }
    return assignments
}

private fun validateAdvancedSettingsRelations(settings: WhiteDnsSettings) {
    val minUploadMtu = settings.minUploadMtu.toInt()
    val maxUploadMtu = settings.maxUploadMtu.toInt()
    if (maxUploadMtu < minUploadMtu) {
        throw IllegalArgumentException("MAX_UPLOAD_MTU must be greater than or equal to MIN_UPLOAD_MTU")
    }
    val minDownloadMtu = settings.minDownloadMtu.toInt()
    val maxDownloadMtu = settings.maxDownloadMtu.toInt()
    if (maxDownloadMtu < minDownloadMtu) {
        throw IllegalArgumentException("MAX_DOWNLOAD_MTU must be greater than or equal to MIN_DOWNLOAD_MTU")
    }
    val rxTxWorkers = settings.rxTxWorkers.toInt()
    val tunnelProcessWorkers = settings.tunnelProcessWorkers.toInt()
    if (tunnelProcessWorkers < rxTxWorkers) {
        throw IllegalArgumentException("TUNNEL_PROCESS_WORKERS must be greater than or equal to RX_TX_WORKERS")
    }
    val sessionRetryBase = settings.sessionInitRetryBaseSeconds.toDouble()
    val sessionRetryMax = settings.sessionInitRetryMaxSeconds.toDouble()
    if (sessionRetryMax < sessionRetryBase) {
        throw IllegalArgumentException("SESSION_INIT_RETRY_MAX_SECONDS must be greater than or equal to SESSION_INIT_RETRY_BASE_SECONDS")
    }
}

private data class TomlValue(
    val raw: String,
    val lineNumber: Int,
) {
    fun stringValue(key: String): String {
        val value = raw.trim()
        if (value.startsWith("\"") || value.startsWith("'")) {
            return parseTomlString(key)
        }
        if (value.startsWith("[")) {
            fail("$key must be a scalar value")
        }
        return value
    }

    fun booleanValue(key: String): Boolean {
        return when (val value = stringValue(key).lowercase(Locale.US)) {
            "true" -> true
            "false" -> false
            else -> fail("$key must be true or false")
        }
    }

    fun intValue(key: String, minValue: Int, maxValue: Int): Int {
        val text = stringValue(key)
        val value = text.toIntOrNull()
            ?: text.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }
                ?.toInt()
            ?: fail("$key must be an integer")
        if (value !in minValue..maxValue) {
            fail("$key must be between $minValue and $maxValue")
        }
        return value
    }

    fun enumIntValue(key: String, allowedValues: Set<Int>): Int {
        val value = intValue(key, allowedValues.minOrNull() ?: 0, allowedValues.maxOrNull() ?: 0)
        if (value !in allowedValues) {
            fail("$key must be one of ${allowedValues.sorted().joinToString(", ")}")
        }
        return value
    }

    fun positiveDoubleValue(key: String): Double {
        val value = stringValue(key).toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: fail("$key must be a number")
        if (value <= 0.0) {
            fail("$key must be greater than 0")
        }
        return value
    }

    fun doubleValue(key: String, minValue: Double, maxValue: Double): Double {
        val value = stringValue(key).toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: fail("$key must be a number")
        if (value < minValue || value > maxValue) {
            fail("$key must be between ${minValue.toNormalizedString()} and ${maxValue.toNormalizedString()}")
        }
        return value
    }

    fun enumStringValue(key: String, allowedValues: Set<String>): String {
        val value = stringValue(key).uppercase(Locale.US)
        if (value !in allowedValues) {
            fail("$key must be one of ${allowedValues.sorted().joinToString(", ")}")
        }
        return value
    }

    fun fail(message: String): Nothing {
        throw IllegalArgumentException("$message (line $lineNumber)")
    }

    private fun parseTomlString(key: String): String {
        val value = raw.trim()
        val quote = value.first()
        if ((quote != '"' && quote != '\'') || value.length < 2 || value.last() != quote) {
            fail("$key has an invalid string value")
        }
        val body = value.substring(1, value.lastIndex)
        if (quote == '\'') {
            return body
        }
        val output = StringBuilder()
        var index = 0
        while (index < body.length) {
            val char = body[index]
            if (char != '\\') {
                output.append(char)
                index += 1
                continue
            }
            if (index == body.lastIndex) {
                fail("$key has an invalid escape sequence")
            }
            val escaped = body[index + 1]
            output.append(
                when (escaped) {
                    'b' -> '\b'
                    't' -> '\t'
                    'n' -> '\n'
                    'f' -> '\u000C'
                    'r' -> '\r'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> fail("$key has an unsupported escape sequence")
                },
            )
            index += 2
        }
        return output.toString()
    }
}

private fun stripTomlComment(line: String): String {
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    line.forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' && inDoubleQuote -> escaped = true
            char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
            char == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            char == '#' && !inSingleQuote && !inDoubleQuote -> return line.substring(0, index)
        }
    }
    return line
}

private fun String.indexOfUnquotedEquals(): Int {
    var inSingleQuote = false
    var inDoubleQuote = false
    var escaped = false
    forEachIndexed { index, char ->
        when {
            escaped -> escaped = false
            char == '\\' && inDoubleQuote -> escaped = true
            char == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
            char == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
            char == '=' && !inSingleQuote && !inDoubleQuote -> return index
        }
    }
    return -1
}

private fun Double.toNormalizedString(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}

private val TomlKeyRegex = Regex("""[A-Z0-9_]+""")
