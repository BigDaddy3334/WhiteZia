package shop.whitezia.client.proxy

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.concurrent.thread

private data class HostPort(
    val host: String,
    val port: Int,
)

class HttpProxyBridge {
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false

    fun start(
        listenHost: String,
        listenPort: Int,
        socksHost: String,
        socksPort: Int,
        socksUsername: String? = null,
        socksPassword: String? = null,
        onOutput: (String) -> Unit = {},
    ) {
        stop()
        if (listenPort !in 1..65535 || socksPort !in 1..65535) {
            throw IllegalArgumentException("Invalid HTTP proxy or SOCKS port")
        }

        val bindHost = listenHost.trim().ifEmpty { "127.0.0.1" }
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(bindHost), listenPort))
        }
        serverSocket = socket
        running = true
        onOutput("HTTP proxy is listening on $bindHost:$listenPort")

        thread(name = "whitezia-http-proxy", isDaemon = true) {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                thread(name = "whitezia-http-proxy-client", isDaemon = true) {
                    handleClient(
                        client = client,
                        socksHost = socksHost,
                        socksPort = socksPort,
                        socksUsername = socksUsername,
                        socksPassword = socksPassword,
                    )
                }
            }
        }
    }

    fun stop() {
        running = false
        val socket = serverSocket
        serverSocket = null
        runCatching { socket?.close() }
    }

    private fun handleClient(
        client: Socket,
        socksHost: String,
        socksPort: Int,
        socksUsername: String?,
        socksPassword: String?,
    ) {
        client.use { clientSocket ->
            try {
                clientSocket.soTimeout = ClientReadTimeoutMillis
                val input = clientSocket.getInputStream()
                val output = clientSocket.getOutputStream()

                val requestLine = readHttpLine(input) ?: return
                val headers = readHeaders(input) ?: return
                if (!isAuthorized(headers, socksUsername, socksPassword)) {
                    output.write(
                        "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"WhiteZia\"\r\nConnection: close\r\n\r\n"
                            .toByteArray(StandardCharsets.ISO_8859_1),
                    )
                    output.flush()
                    return
                }

                val parts = requestLine.split(' ', limit = 3)
                if (parts.size < 3) {
                    writeHttpError(output, 400, "Bad Request")
                    return
                }

                val method = parts[0].uppercase(Locale.US)
                if (method == "CONNECT") {
                    val target = parseHostPort(parts[1], defaultPort = 443)
                    if (target == null) {
                        writeHttpError(output, 400, "Bad Request")
                        return
                    }
                    val upstream = connectViaSocks(socksHost, socksPort, socksUsername, socksPassword, target.host, target.port)
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                    output.flush()
                    tunnel(clientSocket, upstream)
                    return
                }

                val rewrittenRequest = rewriteHttpRequest(parts, headers) ?: run {
                    writeHttpError(output, 400, "Bad Request")
                    return
                }
                val upstream = connectViaSocks(
                    socksHost = socksHost,
                    socksPort = socksPort,
                    socksUsername = socksUsername,
                    socksPassword = socksPassword,
                    host = rewrittenRequest.host,
                    port = rewrittenRequest.port,
                )
                upstream.getOutputStream().write(rewrittenRequest.headerBytes)
                upstream.getOutputStream().flush()
                tunnel(clientSocket, upstream)
            } catch (_: Exception) {
                runCatching {
                    writeHttpError(clientSocket.getOutputStream(), 502, "Bad Gateway")
                }
            }
        }
    }

    private fun tunnel(client: Socket, upstream: Socket) {
        upstream.use { upstreamSocket ->
            client.soTimeout = 0
            upstreamSocket.soTimeout = 0
            val clientToUpstream = thread(name = "whitezia-http-c2u", isDaemon = true) {
                copyAndCloseOutput(client.getInputStream(), upstreamSocket)
            }
            val upstreamToClient = thread(name = "whitezia-http-u2c", isDaemon = true) {
                copyAndCloseOutput(upstreamSocket.getInputStream(), client)
            }
            clientToUpstream.join()
            upstreamToClient.join()
        }
    }

    private fun copyAndCloseOutput(input: InputStream, outputSocket: Socket) {
        runCatching {
            input.copyTo(outputSocket.getOutputStream())
        }
        runCatching { outputSocket.shutdownOutput() }
        runCatching { outputSocket.close() }
    }

    private fun connectViaSocks(
        socksHost: String,
        socksPort: Int,
        socksUsername: String?,
        socksPassword: String?,
        host: String,
        port: Int,
    ): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(socksHost, socksPort), SocksConnectTimeoutMillis)
        socket.soTimeout = SocksReadTimeoutMillis
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        val useAuth = !socksUsername.isNullOrEmpty()
        output.write(byteArrayOf(0x05, 0x01, if (useAuth) 0x02 else 0x00))
        output.flush()
        val methodReply = input.readExactly(2)
        if (methodReply[0] != 0x05.toByte() || methodReply[1] == 0xFF.toByte()) {
            socket.close()
            throw IOException("SOCKS authentication method rejected")
        }

        if (methodReply[1] == 0x02.toByte()) {
            val user = socksUsername.orEmpty().toByteArray(StandardCharsets.UTF_8)
            val pass = socksPassword.orEmpty().toByteArray(StandardCharsets.UTF_8)
            if (user.size > 255 || pass.size > 255) {
                socket.close()
                throw IOException("SOCKS credentials are too long")
            }
            output.write(byteArrayOf(0x01, user.size.toByte()))
            output.write(user)
            output.write(pass.size)
            output.write(pass)
            output.flush()
            val authReply = input.readExactly(2)
            if (authReply[1] != 0x00.toByte()) {
                socket.close()
                throw IOException("SOCKS authentication failed")
            }
        }

        val request = ByteArrayOutputStream()
        request.write(byteArrayOf(0x05, 0x01, 0x00))
        val ipv4 = parseIpv4(host)
        if (ipv4 != null) {
            request.write(0x01)
            request.write(ipv4)
        } else {
            val hostBytes = host.removeSurrounding("[", "]").toByteArray(StandardCharsets.UTF_8)
            if (hostBytes.isEmpty() || hostBytes.size > 255) {
                socket.close()
                throw IOException("Target host is invalid")
            }
            request.write(0x03)
            request.write(hostBytes.size)
            request.write(hostBytes)
        }
        request.write((port ushr 8) and 0xFF)
        request.write(port and 0xFF)
        output.write(request.toByteArray())
        output.flush()

        val reply = input.readExactly(4)
        if (reply[0] != 0x05.toByte() || reply[1] != 0x00.toByte()) {
            socket.close()
            throw IOException("SOCKS connect failed: ${reply[1].toInt() and 0xFF}")
        }
        when (reply[3].toInt() and 0xFF) {
            0x01 -> input.readExactly(4)
            0x03 -> input.readExactly(input.read())
            0x04 -> input.readExactly(16)
            else -> Unit
        }
        input.readExactly(2)
        socket.soTimeout = 0
        return socket
    }

    private fun rewriteHttpRequest(parts: List<String>, headers: List<String>): ProxiedRequest? {
        val uri = runCatching { URI(parts[1]) }.getOrNull()
        val host: String
        val port: Int
        val path: String

        if (uri?.scheme.equals("http", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            host = uri.host
            port = if (uri.port > 0) uri.port else 80
            path = buildString {
                append(uri.rawPath.takeIf { !it.isNullOrEmpty() } ?: "/")
                if (!uri.rawQuery.isNullOrEmpty()) {
                    append('?')
                    append(uri.rawQuery)
                }
            }
        } else {
            val hostHeader = headers.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?: return null
            val target = parseHostPort(hostHeader, defaultPort = 80) ?: return null
            host = target.host
            port = target.port
            path = parts[1].takeIf { it.startsWith("/") } ?: return null
        }

        val headerBytes = buildString {
            append(parts[0])
            append(' ')
            append(path)
            append(' ')
            append(parts[2])
            append("\r\n")
            for (header in headers) {
                val name = header.substringBefore(':').trim().lowercase(Locale.US)
                if (name == "proxy-connection" || name == "proxy-authorization") {
                    continue
                }
                append(header)
                append("\r\n")
            }
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)

        return ProxiedRequest(host = host, port = port, headerBytes = headerBytes)
    }

    private fun isAuthorized(headers: List<String>, username: String?, password: String?): Boolean {
        if (username.isNullOrEmpty()) {
            return true
        }
        val header = headers.firstOrNull { it.startsWith("Proxy-Authorization:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?: return false
        if (!header.startsWith("Basic ", ignoreCase = true)) {
            return false
        }
        val decoded = runCatching {
            String(Base64.decode(header.substringAfter(' ').trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
        }.getOrNull() ?: return false
        return decoded == "$username:${password.orEmpty()}"
    }

    private fun readHeaders(input: InputStream): List<String>? {
        val headers = mutableListOf<String>()
        while (true) {
            val line = readHttpLine(input) ?: return null
            if (line.isEmpty()) {
                return headers
            }
            headers += line
            if (headers.size > MaxHeaderCount) {
                return null
            }
        }
    }

    private fun readHttpLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() <= MaxHeaderLineBytes) {
            val value = input.read()
            if (value == -1) {
                return if (buffer.size() == 0) null else buffer.toString(StandardCharsets.ISO_8859_1.name())
            }
            if (value == '\n'.code) {
                val bytes = buffer.toByteArray()
                val length = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, length, StandardCharsets.ISO_8859_1)
            }
            buffer.write(value)
        }
        return null
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        if (length < 0) {
            throw IOException("Invalid read length")
        }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = read(bytes, offset, length - offset)
            if (count == -1) {
                throw IOException("Unexpected end of stream")
            }
            offset += count
        }
        return bytes
    }

    private fun parseIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) {
            return null
        }
        val bytes = ByteArray(4)
        for (idx in parts.indices) {
            val value = parts[idx].toIntOrNull() ?: return null
            if (value !in 0..255) {
                return null
            }
            bytes[idx] = value.toByte()
        }
        return bytes
    }

    private fun writeHttpError(output: java.io.OutputStream, code: Int, reason: String) {
        output.write("HTTP/1.1 $code $reason\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
    }

    private data class ProxiedRequest(
        val host: String,
        val port: Int,
        val headerBytes: ByteArray,
    )

    private companion object {
        const val ClientReadTimeoutMillis = 30_000
        const val SocksConnectTimeoutMillis = 3_000
        const val SocksReadTimeoutMillis = 10_000
        const val MaxHeaderLineBytes = 8_192
        const val MaxHeaderCount = 128
    }
}

internal fun parseHttpProxyHostPort(authority: String, defaultPort: Int): Pair<String, Int>? {
    val trimmed = authority.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end <= 1) {
            return null
        }
        val host = trimmed.substring(1, end)
        val port = if (trimmed.length > end + 1) {
            if (trimmed[end + 1] != ':') return null
            trimmed.substring(end + 2).toIntOrNull() ?: return null
        } else {
            defaultPort
        }
        return if (host.isNotBlank() && port in 1..65535) host to port else null
    }

    val colonIndex = trimmed.lastIndexOf(':')
    val host = if (colonIndex > 0 && trimmed.indexOf(':') == colonIndex) {
        trimmed.substring(0, colonIndex)
    } else {
        trimmed
    }
    val port = if (colonIndex > 0 && trimmed.indexOf(':') == colonIndex) {
        trimmed.substring(colonIndex + 1).toIntOrNull() ?: return null
    } else {
        defaultPort
    }
    return if (host.isNotBlank() && port in 1..65535) host to port else null
}

private fun parseHostPort(authority: String, defaultPort: Int): HostPort? {
    return parseHttpProxyHostPort(authority, defaultPort)?.let { (host, port) ->
        HostPort(host, port)
    }
}
