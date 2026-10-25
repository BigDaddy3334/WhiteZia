package shop.whitezia.client.runtime

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class ProbeHttpRequest(
    val url: String,
    val socksProxyPort: Int? = null,
    val connectTimeoutMillis: Int = 4_000,
    val readTimeoutMillis: Int = 5_000,
    val totalTimeoutMillis: Long = 9_000,
    val maxBodyBytes: Long = 4_096,
    val captureBodyBytes: Int = 4_096,
    val headers: Map<String, String> = emptyMap(),
    val socksDestinationHost: String? = null,
)

internal data class ProbeHttpResponse(
    val status: Int,
    val body: ByteArray,
    val bytesRead: Long,
    val elapsedMillis: Long,
    val complete: Boolean,
    val failure: String? = null,
)

/** Small GET-only probe transport. Owns the raw socket before SOCKS/TLS can block. */
internal class OwnedHttpTransport(
    private val sslSocketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
    private val executor: Executor = HttpWorkers,
) {
    fun newCall(request: ProbeHttpRequest): Call = Call(request)

    inner class Call internal constructor(private val request: ProbeHttpRequest) {
        private val startedNanos = System.nanoTime()
        private val deadlineNanos = startedNanos + TimeUnit.MILLISECONDS.toNanos(request.totalTimeoutMillis)
        private val cancelled = AtomicBoolean()
        private val expired = AtomicBoolean()
        private val executed = AtomicBoolean()
        private val rawSocket = AtomicReference<Socket?>()
        private val dnsFuture = AtomicReference<Future<*>?>()
        private var tlsSocket: SSLSocket? = null

        init {
            require(request.connectTimeoutMillis > 0 && request.readTimeoutMillis > 0)
            require(request.totalTimeoutMillis in 1..180_000)
            require(request.maxBodyBytes in 0..32L * 1024 * 1024)
            require(request.captureBodyBytes in 0..65_536)
            require(request.socksProxyPort == null || request.socksProxyPort in 1..65535)
            request.socksDestinationHost?.let { destination ->
                require(request.socksProxyPort != null) { "CottenMax health marker requires a SOCKS listener" }
                CottenMaxHealthDestination.validateInitial(request.url, destination)
            }
        }

        fun cancel() {
            cancelled.set(true)
            closeRawSocket()
            dnsFuture.getAndSet(null)?.cancel(true)
        }

        suspend fun await(): ProbeHttpResponse = suspendCancellableCoroutine { continuation ->
            // Lease exists before submitting work, including cancellation before worker dispatch.
            continuation.invokeOnCancellation { cancel() }
            try {
                executor.execute {
                    try {
                        val response = execute()
                        continuation.resume(response)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            } catch (_: RejectedExecutionException) {
                cancel()
                continuation.resumeWithException(IOException("HTTP probe workers are busy"))
            }
        }

        fun execute(): ProbeHttpResponse {
            check(executed.compareAndSet(false, true)) { "HTTP probe call already executed" }
            checkActive()
            val timer = Deadlines.schedule({
                expired.set(true)
                // Never close the TLS wrapper from the scheduler: its monitor may be held by a read.
                closeRawSocket()
                dnsFuture.getAndSet(null)?.cancel(true)
            }, remainingMillis().toLong(), TimeUnit.MILLISECONDS)
            try {
                var url = URL(request.url)
                repeat(MaxRedirects + 1) { redirect ->
                    validateUrl(url)
                    try {
                        val input = connect(url)
                        val headers = readHeaders(input)
                        if (headers.status in RedirectStatuses && headers.values["location"] != null) {
                            if (redirect == MaxRedirects) throw IOException("HTTP probe redirect limit exceeded")
                            val locations = headers.values.getValue("location")
                            if (locations.size != 1) throw IOException("Ambiguous HTTP redirect")
                            val next = URL(url, locations.single())
                            validateUrl(next)
                            if (url.protocol == "https" && next.protocol != "https") throw IOException("HTTPS probe redirect downgrade")
                            url = next
                        } else {
                            return readBody(input, headers)
                        }
                    } finally {
                        closeHop()
                    }
                }
                throw IOException("HTTP probe redirect limit exceeded")
            } finally {
                timer.cancel(false)
                closeHop()
                dnsFuture.getAndSet(null)?.cancel(true)
            }
        }

        private fun validateUrl(url: URL) {
            if (url.protocol !in setOf("http", "https") || url.host.isBlank() || url.userInfo != null) {
                throw IOException("Unsupported HTTP probe URL")
            }
            if (url.file.any { it == '\r' || it == '\n' }) throw IOException("Invalid HTTP probe path")
        }

        private fun connect(url: URL): InputStream {
            checkActive()
            val host = url.host.removeSurrounding("[", "]")
            val asciiHost = if (':' in host) host else IDN.toASCII(host)
            val port = if (url.port >= 0) url.port else if (url.protocol == "https") 443 else 80
            if (port !in 1..65535) throw IOException("Invalid HTTP probe port")
            // Only the private local SOCKS destination changes. The real URL host
            // below still owns TLS verification, SNI and the HTTP Host header.
            // Redirects can retain the reserve only for the same exact allowlist.
            val destinationHost = if (request.socksDestinationHost != null) CottenMaxHealthDestination.forUrl(url) else asciiHost
            val socket = Socket()
            rawSocket.set(socket)
            checkActive() // A cancellation racing socket creation must close this new socket too.
            socket.soTimeout = phaseTimeout(request.readTimeoutMillis)
            val proxyPort = request.socksProxyPort
            if (proxyPort != null) {
                socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), proxyPort),
                    phaseTimeout(request.connectTimeoutMillis))
                connectSocks(socket, destinationHost, port)
            } else {
                val addresses = resolve(asciiHost)
                socket.connect(InetSocketAddress(addresses.firstOrNull() ?: throw IOException("No HTTP probe address"), port),
                    phaseTimeout(request.connectTimeoutMillis))
            }
            val connection = if (url.protocol == "https") {
                val tls = sslSocketFactory.createSocket(socket, asciiHost, port, true) as SSLSocket
                tlsSocket = tls
                checkActive()
                tls.sslParameters = tls.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    if (':' !in asciiHost && !asciiHost.matches(Regex("[0-9.]+"))) serverNames = listOf(SNIHostName(asciiHost))
                }
                tls.soTimeout = phaseTimeout(request.readTimeoutMillis)
                tls.startHandshake()
                tls
            } else socket
            checkActive()
            val authority = (if (':' in asciiHost) "[$asciiHost]" else asciiHost) +
                if (port != if (url.protocol == "https") 443 else 80) ":$port" else ""
            val path = url.file.ifBlank { "/" }.let { if (it.startsWith('?')) "/$it" else it }
            val message = buildString {
                append("GET $path HTTP/1.1\r\nHost: $authority\r\nConnection: close\r\nAccept-Encoding: identity\r\n")
                request.headers.forEach { (name, value) ->
                    if (!name.matches(Regex("[A-Za-z0-9-]+")) || value.any { it == '\r' || it == '\n' }) {
                        throw IOException("Invalid HTTP probe header")
                    }
                    if (name.lowercase(Locale.US) in setOf("host", "connection", "accept-encoding", "content-length", "transfer-encoding")) {
                        throw IOException("Reserved HTTP probe header")
                    }
                    append("$name: $value\r\n")
                }
                append("\r\n")
            }
            connection.getOutputStream().apply { write(message.toByteArray(StandardCharsets.US_ASCII)); flush() }
            connection.soTimeout = phaseTimeout(request.readTimeoutMillis)
            return BufferedInputStream(connection.getInputStream(), 8 * 1024)
        }

        private fun resolve(host: String): Array<InetAddress> {
            // Native DNS may ignore interrupt. Its isolated pool and queue stay strictly bounded.
            val future = try { DnsWorkers.submit<Array<InetAddress>> { InetAddress.getAllByName(host) } }
            catch (_: RejectedExecutionException) { throw IOException("HTTP probe DNS workers are busy") }
            dnsFuture.set(future)
            try {
                checkActive()
                return future.get(remainingMillis().toLong(), TimeUnit.MILLISECONDS)
            } catch (error: Exception) {
                checkActive()
                throw IOException("HTTP probe DNS failed", error)
            } finally {
                dnsFuture.compareAndSet(future, null)
                future.cancel(true)
            }
        }

        private fun connectSocks(socket: Socket, host: String, port: Int) {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            // SO_TIMEOUT is already armed before the first greeting byte or reply read.
            output.write(byteArrayOf(5, 1, 0)); output.flush()
            val greeting = readExact(input, 2)
            if (greeting[0].toInt() != 5 || greeting[1].toInt() != 0) throw IOException("SOCKS5 probe authentication rejected")
            val hostname = host.toByteArray(StandardCharsets.US_ASCII)
            if (hostname.size !in 1..255) throw IOException("SOCKS5 probe hostname is too long")
            output.write(byteArrayOf(5, 1, 0, 3, hostname.size.toByte()))
            output.write(hostname)
            output.write(byteArrayOf((port ushr 8).toByte(), port.toByte())); output.flush()
            val reply = readExact(input, 4)
            if (reply[0].toInt() != 5 || reply[1].toInt() != 0 || reply[2].toInt() != 0) throw IOException("SOCKS5 probe connect rejected")
            val addressSize = when (reply[3].toInt() and 255) {
                1 -> 4
                4 -> 16
                3 -> readExact(input, 1)[0].toInt() and 255
                else -> throw IOException("Invalid SOCKS5 probe address type")
            }
            readExact(input, addressSize + 2)
        }

        private fun readHeaders(input: InputStream): ResponseHeaders {
            val budget = HeaderBudget()
            repeat(6) {
                val statusLine = readLine(input, budget)
                val match = Regex("HTTP/1\\.[01] ([0-9]{3})(?: .*)?").matchEntire(statusLine)
                    ?: throw IOException("Invalid HTTP probe status")
                val status = match.groupValues[1].toInt()
                val values = linkedMapOf<String, MutableList<String>>()
                while (true) {
                    val line = readLine(input, budget)
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator <= 0 || !line.substring(0, separator).matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) {
                        throw IOException("Invalid HTTP probe header")
                    }
                    val name = line.substring(0, separator).lowercase(Locale.US)
                    values.getOrPut(name) { mutableListOf() }.add(line.substring(separator + 1).trim())
                }
                if (status !in 100..199) return ResponseHeaders(status, values)
                if (status == 101) throw IOException("HTTP probe protocol switch unsupported")
            }
            throw IOException("Too many interim HTTP probe responses")
        }

        private fun readBody(input: InputStream, headers: ResponseHeaders): ProbeHttpResponse {
            val captured = ByteArrayOutputStream(minOf(request.captureBodyBytes, 4096))
            var bytes = 0L
            fun response(complete: Boolean, failure: String? = null) = ProbeHttpResponse(headers.status,
                captured.toByteArray(), bytes, elapsedMillis(), complete, failure)
            fun readPayload(count: Long): Boolean {
                val buffer = ByteArray(16 * 1024)
                var left = count
                while (left > 0 && bytes < request.maxBodyBytes) {
                    checkActive()
                    val wanted = minOf(buffer.size.toLong(), left, request.maxBodyBytes - bytes).toInt()
                    val read = input.read(buffer, 0, wanted)
                    if (read < 0) return false
                    if (read == 0) continue
                    val capture = minOf(read, (request.captureBodyBytes - captured.size()).coerceAtLeast(0))
                    captured.write(buffer, 0, capture)
                    bytes += read
                    left -= read
                }
                return left == 0L
            }
            try {
                if (headers.status == 204 || headers.status == 304) return response(true)
                val encoding = headers.values["content-encoding"].orEmpty().joinToString(",").lowercase(Locale.US)
                if (encoding.isNotEmpty() && encoding != "identity") throw IOException("Encoded HTTP probe body unsupported")
                val transfer = headers.values["transfer-encoding"].orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase(Locale.US) }
                val lengths = headers.values["content-length"].orEmpty().flatMap { it.split(',') }.map { it.trim() }
                if (transfer.isNotEmpty() && lengths.isNotEmpty()) throw IOException("Ambiguous HTTP probe body framing")
                if (transfer.isNotEmpty() && transfer != listOf("chunked")) throw IOException("Unsupported HTTP probe transfer encoding")
                val length = if (lengths.isEmpty()) null else {
                    if (lengths.distinct().size != 1 || !lengths[0].matches(Regex("[0-9]+"))) throw IOException("Invalid HTTP probe content length")
                    lengths[0].toLongOrNull() ?: throw IOException("HTTP probe content length overflow")
                }
                if (request.maxBodyBytes == 0L) return response(length == 0L)
                if (transfer.isNotEmpty()) {
                    while (bytes < request.maxBodyBytes) {
                        val sizeText = readLine(input, HeaderBudget()).substringBefore(';').trim()
                        if (!sizeText.matches(Regex("[0-9A-Fa-f]{1,16}"))) throw IOException("Invalid HTTP probe chunk size")
                        val chunk = sizeText.toLongOrNull(16) ?: throw IOException("HTTP probe chunk size overflow")
                        if (chunk == 0L) {
                            val trailers = HeaderBudget()
                            while (true) {
                                val line = readLine(input, trailers)
                                if (line.isEmpty()) break
                                val colon = line.indexOf(':')
                                if (colon <= 0 || !line.substring(0, colon).matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) {
                                    throw IOException("Invalid HTTP probe trailer")
                                }
                            }
                            return response(true)
                        }
                        if (!readPayload(chunk)) {
                            if (bytes == request.maxBodyBytes) return response(false)
                            throw EOFException("Truncated HTTP probe chunk")
                        }
                        if (!readExact(input, 2).contentEquals(byteArrayOf(13, 10))) throw IOException("Invalid HTTP probe chunk terminator")
                    }
                    return response(false)
                }
                if (length != null) {
                    if (readPayload(length)) return response(true)
                    if (bytes == request.maxBodyBytes) return response(false)
                    throw EOFException("Truncated HTTP probe body")
                }
                while (bytes < request.maxBodyBytes) {
                    if (!readPayload(minOf(16 * 1024L, request.maxBodyBytes - bytes))) return response(true)
                }
                return response(false)
            } catch (error: IOException) {
                if (cancelled.get()) throw IOException("HTTP probe cancelled", error)
                val failure = if (expired.get() || System.nanoTime() >= deadlineNanos) "HTTP probe deadline exceeded"
                    else error.message ?: error.javaClass.simpleName
                return response(false, failure)
            }
        }

        private fun readLine(input: InputStream, budget: HeaderBudget): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                checkActive()
                val byte = input.read()
                if (byte < 0) throw EOFException("Truncated HTTP probe headers")
                budget.bytes += 1
                if (budget.bytes > MaxHeaderBytes || bytes.size() >= MaxHeaderLine) throw IOException("HTTP probe headers too large")
                if (byte == 10) {
                    val line = bytes.toByteArray()
                    if (line.isEmpty() || line.last() != 13.toByte()) throw IOException("Invalid HTTP probe line ending")
                    return String(line, 0, line.size - 1, StandardCharsets.ISO_8859_1)
                }
                bytes.write(byte)
            }
        }

        private fun readExact(input: InputStream, count: Int): ByteArray {
            val result = ByteArray(count)
            var position = 0
            while (position < count) {
                checkActive()
                val read = input.read(result, position, count - position)
                if (read < 0) throw EOFException("Truncated SOCKS/HTTP probe response")
                position += read
            }
            return result
        }

        private fun elapsedMillis() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceAtLeast(1)
        private fun remainingMillis(): Int {
            checkActive()
            return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
        }
        private fun phaseTimeout(timeout: Int) = minOf(timeout, remainingMillis())
        private fun checkActive() {
            if (cancelled.get()) { closeRawSocket(); throw IOException("HTTP probe cancelled") }
            if (expired.get() || System.nanoTime() >= deadlineNanos) {
                closeRawSocket()
                throw SocketTimeoutException("HTTP probe deadline exceeded")
            }
        }
        private fun closeRawSocket() { runCatching { rawSocket.getAndSet(null)?.close() } }
        private fun closeHop() {
            closeRawSocket()
            runCatching { tlsSocket?.close() }
            tlsSocket = null
        }
    }

    private data class ResponseHeaders(val status: Int, val values: Map<String, List<String>>)
    private class HeaderBudget(var bytes: Int = 0)

    private companion object {
        const val MaxHeaderBytes = 65_536
        const val MaxHeaderLine = 8_192
        const val MaxRedirects = 5
        val RedirectStatuses = setOf(301, 302, 303, 307, 308)
        fun daemonFactory(prefix: String): ThreadFactory {
            val sequence = AtomicInteger()
            return ThreadFactory { task -> Thread(task, "$prefix-${sequence.incrementAndGet()}").apply { isDaemon = true } }
        }
        val HttpWorkers = ThreadPoolExecutor(0, 8, 30, TimeUnit.SECONDS, SynchronousQueue(), daemonFactory("http-probe"),
            ThreadPoolExecutor.AbortPolicy())
        val DnsWorkers = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(), daemonFactory("http-probe-dns"),
            ThreadPoolExecutor.AbortPolicy())
        val Deadlines = ScheduledThreadPoolExecutor(1, daemonFactory("http-probe-deadline")).apply { removeOnCancelPolicy = true }
    }
}
