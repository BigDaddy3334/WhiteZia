package shop.whitezia.client.model

import java.net.InetAddress

fun validateResolverText(raw: String): ResolverTextValidation {
    val normalizedResolvers = mutableListOf<String>()
    val invalidEntries = mutableListOf<String>()
    val seen = mutableSetOf<String>()

    resolverTextTokens(raw).forEach { entry ->
        val normalized = normalizeResolverEntry(entry)
        if (normalized == null) {
            invalidEntries += entry
            return@forEach
        }
        if (seen.add(normalized)) {
            normalizedResolvers += normalized
        }
    }

    return ResolverTextValidation(
        normalizedResolvers = normalizedResolvers,
        invalidEntries = invalidEntries.distinct(),
    )
}

internal fun normalizeResolverText(raw: String): String {
    return validateResolverText(raw).normalizedText
}

private fun resolverTextTokens(raw: String): Sequence<String> {
    val lines = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

    val separators = if (detectResolverTextEntryType(lines) == ResolverTextEntryType.CommaSeparated) {
        charArrayOf(',', ';')
    } else {
        charArrayOf()
    }

    return lines
        .asSequence()
        .flatMap { line ->
            if (separators.isEmpty()) {
                sequenceOf(line)
            } else {
                line.split(*separators).asSequence()
            }
        }
        .map(::cleanResolverToken)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
}

private fun cleanResolverToken(token: String): String {
    return token.trim().trim('"', '\'').trim()
}

private fun normalizeResolverEntry(entry: String): String? {
    normalizeResolverTarget(entry)?.let { return it }

    val hostPort = splitResolverHostPort(entry) ?: return null
    val target = normalizeResolverTarget(hostPort.first) ?: return null
    val port = hostPort.second.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    if (port == DefaultResolverPort) {
        return target
    }
    return if (resolverTargetNeedsBrackets(target)) {
        "[$target]:$port"
    } else {
        "$target:$port"
    }
}

private fun splitResolverHostPort(entry: String): Pair<String, String>? {
    val text = entry.trim()
    if (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end <= 1) {
            return null
        }
        val hostPart = text.substring(1, end).trim()
        val remainder = text.substring(end + 1).trim()
        if (!remainder.startsWith(":")) {
            return null
        }
        val portPart = remainder.substring(1).trim()
        return if (hostPart.isNotEmpty() && portPart.isNotEmpty()) hostPart to portPart else null
    }

    if (text.count { it == ':' } != 1) {
        return null
    }
    val separator = text.indexOf(':')
    val hostPart = text.substring(0, separator).trim()
    val portPart = text.substring(separator + 1).trim()
    return if (hostPart.isNotEmpty() && portPart.isNotEmpty()) hostPart to portPart else null
}

private fun normalizeResolverTarget(target: String): String? {
    val text = target.trim()
    if (text.isEmpty()) {
        return null
    }

    val slashIndex = text.indexOf('/')
    if (slashIndex == -1) {
        return normalizeIpAddress(text)
    }
    if (slashIndex != text.lastIndexOf('/')) {
        return null
    }

    val ip = normalizeIpAddress(text.substring(0, slashIndex).trim()) ?: return null
    val prefixBits = text.substring(slashIndex + 1).trim().toIntOrNull() ?: return null
    val maxBits = if (ip.contains(':')) 128 else 32
    if (prefixBits !in 0..maxBits) {
        return null
    }
    val hostBits = maxBits - prefixBits
    if (hostBits > 16) {
        return null
    }
    return "$ip/$prefixBits"
}

private fun normalizeIpAddress(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) {
        return null
    }

    if (!text.contains(':')) {
        return normalizeIpv4Address(text)
    }

    if (!ResolverIpv6Chars.matches(text)) {
        return null
    }
    return runCatching {
        InetAddress.getByName(text)
    }.getOrNull()?.hostAddress?.takeIf { it.contains(':') }
}

private fun normalizeIpv4Address(raw: String): String? {
    val parts = raw.split('.')
    if (parts.size != 4) {
        return null
    }
    return parts
        .map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) {
                return null
            }
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        .joinToString(".")
}

private fun resolverTargetNeedsBrackets(target: String): Boolean {
    return target.substringBefore('/').contains(':')
}

private fun detectResolverTextEntryType(lines: List<String>): ResolverTextEntryType {
    return if (lines.any { it.contains(',') || it.contains(';') }) {
        ResolverTextEntryType.CommaSeparated
    } else {
        ResolverTextEntryType.LineSeparated
    }
}

private enum class ResolverTextEntryType {
    LineSeparated,
    CommaSeparated,
}

private const val DefaultResolverPort = 53

private val ResolverIpv6Chars = Regex("^[0-9A-Fa-f:.]+$")
