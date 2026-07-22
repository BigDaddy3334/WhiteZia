package shop.whitezia.client.controlplane

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

internal class ControlPlaneTransport(context: Context) {
    private val bootstrapRuntime = BootstrapXrayRuntime.get(context.applicationContext)

    fun <T> execute(
        rawUrl: String,
        shouldRetry: (Throwable) -> Boolean = ::isDefaultBootstrapRetry,
        request: (HttpURLConnection) -> T,
    ): T {
        if (shouldPreferBootstrap() && bootstrapRuntime.isConfigured) {
            val bootstrapLease = try {
                bootstrapRuntime.acquireProxy()
            } catch (bootstrapStartError: Exception) {
                clearBootstrapPreference()
                Log.w(Tag, "Bootstrap transport could not start, retrying directly", bootstrapStartError)
                return executeOnce(rawUrl, Proxy.NO_PROXY, request)
            }
            return try {
                bootstrapLease.use { lease -> executeOnce(rawUrl, lease.proxy, request) }
            } catch (bootstrapError: Exception) {
                if (!shouldRetry(bootstrapError)) throw bootstrapError
                clearBootstrapPreference()
                Log.w(Tag, "Bootstrap control-plane request failed, retrying directly", bootstrapError)
                executeOnce(rawUrl, Proxy.NO_PROXY, request)
            }
        }
        return try {
            executeOnce(rawUrl, Proxy.NO_PROXY, request).also { clearBootstrapPreference() }
        } catch (directError: Exception) {
            if (!shouldRetry(directError) || !bootstrapRuntime.isConfigured) throw directError
            preferBootstrapTemporarily()
            Log.w(Tag, "Direct control-plane request failed, retrying through bootstrap", directError)
            try {
                executeThroughBootstrap(rawUrl, request)
            } catch (bootstrapError: Exception) {
                bootstrapError.addSuppressed(directError)
                throw bootstrapError
            }
        }
    }

    fun <T> executeNonReplayable(
        rawUrl: String,
        healthCheckUrl: String,
        request: (HttpURLConnection) -> T,
    ): T {
        val bootstrapLease = selectBootstrapForNonReplayableRequest(healthCheckUrl)
        return if (bootstrapLease == null) {
            executeOnce(rawUrl, Proxy.NO_PROXY, request)
        } else {
            bootstrapLease.use { lease -> executeOnce(rawUrl, lease.proxy, request) }
        }
    }

    suspend fun <T> executeSuspend(
        rawUrl: String,
        shouldRetry: (Throwable) -> Boolean = ::isDefaultBootstrapRetry,
        request: suspend (HttpURLConnection) -> T,
    ): T {
        if (shouldPreferBootstrap() && bootstrapRuntime.isConfigured) {
            val bootstrapLease = try {
                bootstrapRuntime.acquireProxy()
            } catch (bootstrapStartError: Exception) {
                clearBootstrapPreference()
                Log.w(Tag, "Bootstrap transport could not start, retrying directly", bootstrapStartError)
                return executeOnceSuspend(rawUrl, Proxy.NO_PROXY, request)
            }
            return try {
                bootstrapLease.use { lease -> executeOnceSuspend(rawUrl, lease.proxy, request) }
            } catch (bootstrapError: Exception) {
                if (!shouldRetry(bootstrapError)) throw bootstrapError
                clearBootstrapPreference()
                Log.w(Tag, "Bootstrap control-plane request failed, retrying directly", bootstrapError)
                executeOnceSuspend(rawUrl, Proxy.NO_PROXY, request)
            }
        }
        return try {
            executeOnceSuspend(rawUrl, Proxy.NO_PROXY, request).also { clearBootstrapPreference() }
        } catch (directError: Exception) {
            if (!shouldRetry(directError) || !bootstrapRuntime.isConfigured) throw directError
            preferBootstrapTemporarily()
            Log.w(Tag, "Direct control-plane request failed, retrying through bootstrap", directError)
            try {
                executeSuspendThroughBootstrap(rawUrl, request)
            } catch (bootstrapError: Exception) {
                bootstrapError.addSuppressed(directError)
                throw bootstrapError
            }
        }
    }

    private fun selectBootstrapForNonReplayableRequest(healthCheckUrl: String): BootstrapProxyLease? {
        if (shouldPreferBootstrap() && bootstrapRuntime.isConfigured) {
            try {
                return bootstrapRuntime.acquireProxy()
            } catch (bootstrapStartError: Exception) {
                clearBootstrapPreference()
                Log.w(Tag, "Bootstrap transport could not start, probing direct route", bootstrapStartError)
            }
        }
        return try {
            executeOnce(healthCheckUrl, Proxy.NO_PROXY) { connection ->
                connection.requestMethod = "GET"
                connection.connectTimeout = RouteProbeTimeoutMillis
                connection.readTimeout = RouteProbeTimeoutMillis
                connection.instanceFollowRedirects = false
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    throw ControlPlaneHttpStatusException(
                        statusCode,
                        "Control-plane health check returned HTTP $statusCode",
                    )
                }
            }
            clearBootstrapPreference()
            null
        } catch (directError: Exception) {
            if (!isDefaultBootstrapRetry(directError) || !bootstrapRuntime.isConfigured) throw directError
            preferBootstrapTemporarily()
            Log.w(Tag, "Direct control-plane health check failed, selecting bootstrap", directError)
            bootstrapRuntime.acquireProxy()
        }
    }

    private fun <T> executeThroughBootstrap(
        rawUrl: String,
        request: (HttpURLConnection) -> T,
    ): T = bootstrapRuntime.acquireProxy().use { lease ->
        executeOnce(rawUrl, lease.proxy, request)
    }

    private suspend fun <T> executeSuspendThroughBootstrap(
        rawUrl: String,
        request: suspend (HttpURLConnection) -> T,
    ): T {
        val lease = bootstrapRuntime.acquireProxy()
        return try {
            executeOnceSuspend(rawUrl, lease.proxy, request)
        } finally {
            lease.close()
        }
    }

    private fun <T> executeOnce(
        rawUrl: String,
        proxy: Proxy,
        request: (HttpURLConnection) -> T,
    ): T {
        val connection = openConnection(rawUrl, proxy)
        return try {
            request(connection)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> executeOnceSuspend(
        rawUrl: String,
        proxy: Proxy,
        request: suspend (HttpURLConnection) -> T,
    ): T {
        val connection = openConnection(rawUrl, proxy)
        return try {
            request(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(rawUrl: String, proxy: Proxy): HttpURLConnection {
        val url = URL(rawUrl)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Control-plane requests must use HTTPS"
        }
        return url.openConnection(proxy) as HttpURLConnection
    }

    companion object {
        private const val Tag = "WhiteZiaControlPlane"
        private const val BootstrapPreferenceMillis = 2L * 60L * 1_000L
        private const val RouteProbeTimeoutMillis = 2_500

        @Volatile
        private var preferBootstrapUntilElapsedMillis = 0L

        private fun shouldPreferBootstrap(): Boolean =
            SystemClock.elapsedRealtime() < preferBootstrapUntilElapsedMillis

        private fun preferBootstrapTemporarily() {
            preferBootstrapUntilElapsedMillis = SystemClock.elapsedRealtime() + BootstrapPreferenceMillis
        }

        private fun clearBootstrapPreference() {
            preferBootstrapUntilElapsedMillis = 0L
        }
    }
}

internal class ControlPlaneHttpStatusException(
    val statusCode: Int,
    message: String,
) : IOException(message)

internal fun isDefaultBootstrapRetry(error: Throwable): Boolean = when (error) {
    is ControlPlaneHttpStatusException -> isBootstrapRetryStatus(error.statusCode)
    is IOException -> true
    else -> false
}

internal fun isBootstrapRetryStatus(statusCode: Int): Boolean =
    statusCode in 500..599 || statusCode in setOf(403, 408, 451)
