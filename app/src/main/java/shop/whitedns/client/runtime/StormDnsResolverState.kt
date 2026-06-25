package shop.whitedns.client.runtime

import shop.whitedns.client.model.ResolverRuntimeState

fun parseStormDnsResolverStateLine(line: String): ResolverRuntimeState? {
    val cleanLine = line
        .replace(AnsiEscapeRegex, "")
        .trim()
    val match = StormDnsResolverStateRegex.find(cleanLine) ?: return null
    return ResolverRuntimeState(
        activeResolvers = parseResolverRuntimeList(match.groupValues[1]),
        standbyResolvers = parseResolverRuntimeList(match.groupValues[2]),
        validResolvers = parseResolverRuntimeList(match.groupValues[3]),
    )
}

private fun parseResolverRuntimeList(raw: String): List<String> {
    return raw
        .takeUnless { it == "-" }
        .orEmpty()
        .split(',')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}

private val StormDnsResolverStateRegex = Regex(
    """WD_RESOLVERS\s+active=([^\s]+)\s+standby=([^\s]+)\s+valid=([^\s]+)""",
)

private val AnsiEscapeRegex = Regex("\\u001B\\[[;\\d]*m")
