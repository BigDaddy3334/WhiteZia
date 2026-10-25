package shop.whitezia.client.vpn

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import shop.whitezia.client.runtime.VpnDesiredSessionStore

/** Keeps the started VPN registered when Android unbinds a disappearing TUN. */
internal object VpnRuntimeBinding {
    private const val Tag = "VpnRuntimeBinding"
    private var appContext: Context? = null
    private var connection: ServiceConnection? = null

    @Synchronized
    fun hold(context: Context, sessionId: String) {
        if (!isClientProcess(context)) return
        val app = context.applicationContext
        if (VpnDesiredSessionStore(app.noBackupFilesDir).read()?.sessionId != sessionId) return
        if (connection != null) return
        val listener = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                releaseWhenNotRequested(app)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // BIND_AUTO_CREATE lets Android recreate the started sticky service.
                releaseWhenNotRequested(app)
            }

            override fun onBindingDied(name: ComponentName) = bindingEnded(this, true)
            override fun onNullBinding(name: ComponentName) = bindingEnded(this, false)
        }
        appContext = app
        connection = listener
        try {
            // Match the system VPN binding's filter exactly. A different action
            // would not prevent its last-client unbind after a process death.
            val bound = app.bindService(
                Intent(app, WhiteZiaVpnService::class.java).setAction(VpnService.SERVICE_INTERFACE),
                listener,
                Context.BIND_AUTO_CREATE,
            )
            if (!bound) {
                appContext = null
                connection = null
                Log.w(Tag, "VPN runtime binding was not accepted")
            }
        } catch (error: Exception) {
            appContext = null
            connection = null
            Log.w(Tag, "Could not bind requested VPN runtime", error)
        }
    }

    @Synchronized
    fun releaseWhenNotRequested(context: Context) {
        if (!isClientProcess(context)) return
        if (VpnDesiredSessionStore(context.applicationContext.noBackupFilesDir).read() != null) return
        releaseLocked()
    }

    fun notifyCancellation(context: Context) {
        releaseWhenNotRequested(context)
        runCatching {
            context.sendBroadcast(Intent(context, VpnBindingReleaseReceiver::class.java))
        }.onFailure { Log.w(Tag, "Could not notify VPN binding cancellation", it) }
    }

    @Synchronized
    private fun bindingEnded(ended: ServiceConnection, reconnect: Boolean) {
        if (connection !== ended) return
        val app = appContext ?: return
        releaseLocked()
        if (reconnect) {
            VpnDesiredSessionStore(app.noBackupFilesDir).read()?.let { hold(app, it.sessionId) }
        }
    }

    private fun releaseLocked() {
        val app = appContext
        val bound = connection
        appContext = null
        connection = null
        if (app != null && bound != null) {
            runCatching { app.unbindService(bound) }
                .onFailure { Log.w(Tag, "Could not release VPN runtime binding", it) }
        }
    }

    private fun isClientProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        return processName == context.applicationInfo.processName
    }
}
