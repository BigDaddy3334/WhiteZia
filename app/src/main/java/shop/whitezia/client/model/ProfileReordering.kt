package shop.whitezia.client.model

internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    val reordered = toMutableList()
    val item = reordered.removeAt(fromIndex)
    reordered.add(toIndex, item)
    return reordered
}
