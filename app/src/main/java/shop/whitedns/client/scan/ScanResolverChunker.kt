package shop.whitedns.client.scan

fun chunkResolversRoundRobin(
    resolvers: List<String>,
    requestedWorkerCount: Int,
): List<List<String>> {
    val cleanResolvers = resolvers
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (cleanResolvers.isEmpty()) {
        return emptyList()
    }

    val workerCount = requestedWorkerCount
        .coerceAtLeast(1)
        .coerceAtMost(cleanResolvers.size)
    val chunks = List(workerCount) { mutableListOf<String>() }
    cleanResolvers.forEachIndexed { index, resolver ->
        chunks[index % workerCount] += resolver
    }
    return chunks.map { it.toList() }
}
