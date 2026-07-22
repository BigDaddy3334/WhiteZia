package shop.whitezia.client.controlplane

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal fun InputStream.readUtf8Limited(maxBytes: Int): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, InitialBufferBytes))
    val buffer = ByteArray(ReadBufferBytes)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > maxBytes) {
            throw IOException("Control-plane response exceeds $maxBytes bytes")
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val InitialBufferBytes = 8 * 1024
private const val ReadBufferBytes = 8 * 1024
