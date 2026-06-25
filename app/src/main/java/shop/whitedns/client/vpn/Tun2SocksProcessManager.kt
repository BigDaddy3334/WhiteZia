package shop.whitedns.client.vpn

import android.content.Context
import android.util.Log
import com.github.shadowsocks.bg.Tun2proxy

class Tun2SocksProcessManager(
    context: Context,
    private val binaryInstaller: Tun2SocksBinaryInstaller = Tun2SocksBinaryInstaller(context),
) {

    private val ownerToken = Any()

    fun requireBinary() {
        binaryInstaller.requireLibrary()
    }

    fun start(
        tunFileDescriptor: Int,
        closeTunFileDescriptorOnDrop: Boolean = true,
        socksHost: String,
        socksPort: Int,
        socksUsername: String? = null,
        socksPassword: String? = null,
        onOutput: (String) -> Unit = {},
        onExit: (Int) -> Unit = {},
    ) {
        if (!stop(StopBeforeStartGracePeriodMillis, force = true, signalNative = false)) {
            throw IllegalStateException("Previous tun2proxy runner is still stopping")
        }
        binaryInstaller.requireLibrary()
        val proxyUrl = buildSocksProxyUrl(
            host = socksHost,
            port = socksPort,
            username = socksUsername,
            password = socksPassword,
        )
        val activeThread = Thread {
            val exitCode = try {
                Tun2proxy.run(
                    proxyUrl,
                    tunFileDescriptor,
                    closeTunFileDescriptorOnDrop,
                    TunMtu.toChar(),
                    Tun2proxy.VERBOSITY_WARN,
                    Tun2proxy.DNS_VIRTUAL,
                )
            } catch (error: Throwable) {
                runCatching {
                    onOutput("tun2proxy native runner failed: ${error.message ?: error::class.java.simpleName}")
                }
                NativeRunnerFailureExitCode
            }
            val shouldReportExit = synchronized(NativeStateLock) {
                if (runnerThread === Thread.currentThread()) {
                    runnerThread = null
                    runnerOwnerToken = null
                    stopSignalSentThread = null
                    true
                } else {
                    false
                }
            }
            if (shouldReportExit) {
                runCatching { onExit(exitCode) }
            }
        }.apply {
            name = "tun2proxy-runner"
            isDaemon = true
        }
        synchronized(NativeOperationLock) {
            val existingThread = synchronized(NativeStateLock) { runnerThread }
            if (existingThread?.isAlive == true) {
                throw IllegalStateException("tun2proxy runner is already active")
            }
            synchronized(NativeStateLock) {
                runnerThread = activeThread
                runnerOwnerToken = ownerToken
                stopSignalSentThread = null
            }
            activeThread.start()
        }
        onOutput("tun2proxy native runner started")
    }

    fun stop(
        gracePeriodMillis: Long = 3_000,
        force: Boolean = false,
        signalNative: Boolean = true,
    ): Boolean {
        return synchronized(NativeOperationLock) {
            stopLocked(gracePeriodMillis, force, signalNative)
        }
    }

    private fun stopLocked(
        gracePeriodMillis: Long,
        force: Boolean,
        signalNative: Boolean,
    ): Boolean {
        val activeThread = synchronized(NativeStateLock) {
            if (!force && runnerOwnerToken !== ownerToken) {
                return true
            }
            runnerThread
        }
        if (activeThread == null) {
            return true
        }
        val shouldSignalNative = signalNative && synchronized(NativeStateLock) {
            if (stopSignalSentThread === activeThread) {
                false
            } else {
                stopSignalSentThread = activeThread
                true
            }
        }
        if (shouldSignalNative) {
            runCatching {
                Tun2proxy.stop()
            }.onFailure { error ->
                Log.w(Tag, "Failed to stop tun2proxy native runner", error)
            }
        }
        try {
            activeThread.join(gracePeriodMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        val stopped = !activeThread.isAlive
        if (stopped) {
            synchronized(NativeStateLock) {
                if (runnerThread === activeThread) {
                    runnerThread = null
                    runnerOwnerToken = null
                    stopSignalSentThread = null
                }
            }
        } else {
            Log.w(Tag, "tun2proxy native runner did not stop within ${gracePeriodMillis}ms")
        }
        return stopped
    }

    private fun buildSocksProxyUrl(
        host: String,
        port: Int,
        username: String?,
        password: String?,
    ): String {
        val authorityHost = if (host.contains(":") && !host.startsWith("[")) {
            "[$host]"
        } else {
            host
        }
        val userInfo = if (!username.isNullOrEmpty()) {
            "${percentEncode(username)}:${percentEncode(password.orEmpty())}@"
        } else {
            ""
        }
        return "socks5://$userInfo$authorityHost:$port"
    }

    private fun percentEncode(value: String): String {
        val hex = "0123456789ABCDEF"
        return buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val code = byte.toInt() and 0xff
                val isUnreserved =
                    code in 'A'.code..'Z'.code ||
                        code in 'a'.code..'z'.code ||
                        code in '0'.code..'9'.code ||
                        code == '-'.code ||
                        code == '.'.code ||
                        code == '_'.code ||
                        code == '~'.code
                if (isUnreserved) {
                    append(code.toChar())
                } else {
                    append('%')
                    append(hex[code shr 4])
                    append(hex[code and 0x0f])
                }
            }
        }
    }

    private companion object {
        const val Tag = "Tun2SocksProcessManager"
        const val TunMtu = 1500
        const val NativeRunnerFailureExitCode = -1
        const val StopBeforeStartGracePeriodMillis = 5_000L
        val NativeOperationLock = Any()
        val NativeStateLock = Any()

        @Volatile
        var runnerThread: Thread? = null

        @Volatile
        var runnerOwnerToken: Any? = null

        @Volatile
        var stopSignalSentThread: Thread? = null
    }
}
